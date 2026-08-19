package org.jds.edgar4j.worker

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : Activity() {
    private val scope = MainScope()
    private lateinit var preferences: WorkerPreferences
    private lateinit var scheduler: WorkerScheduler

    private lateinit var serverUrl: EditText
    private lateinit var secUserAgent: EditText
    private lateinit var username: EditText
    private lateinit var password: EditText
    private lateinit var enabled: Switch
    private lateinit var wifiOnly: Switch
    private lateinit var chargingOnly: Switch
    private lateinit var minimumBattery: EditText
    private lateinit var maxArtifactMb: EditText
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        preferences = WorkerPreferences(this)
        scheduler = WorkerScheduler(this)
        bindViews()

        findViewById<Button>(R.id.save).setOnClickListener { save(runNow = false) }
        findViewById<Button>(R.id.runNow).setOnClickListener { save(runNow = true) }

        scope.launch { populate(preferences.current()) }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun bindViews() {
        serverUrl = findViewById(R.id.serverUrl)
        secUserAgent = findViewById(R.id.secUserAgent)
        username = findViewById(R.id.username)
        password = findViewById(R.id.password)
        enabled = findViewById(R.id.enabled)
        wifiOnly = findViewById(R.id.wifiOnly)
        chargingOnly = findViewById(R.id.chargingOnly)
        minimumBattery = findViewById(R.id.minimumBattery)
        maxArtifactMb = findViewById(R.id.maxArtifactMb)
        status = findViewById(R.id.status)
        password.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    }

    private fun populate(settings: WorkerSettings) {
        serverUrl.setText(settings.serverUrl)
        secUserAgent.setText(settings.secUserAgent)
        username.setText(settings.username)
        enabled.isChecked = settings.enabled
        wifiOnly.isChecked = settings.wifiOnly
        chargingOnly.isChecked = settings.chargingOnly
        minimumBattery.setText(settings.minimumBatteryPercent.toString())
        maxArtifactMb.setText(settings.maxArtifactMb.toString())
    }

    private fun save(runNow: Boolean) {
        scope.launch {
            try {
                val settings = readSettings()
                val newPassword = password.text.toString().takeIf { it.isNotBlank() }
                preferences.save(settings, newPassword)
                password.text.clear()
                scheduler.apply(settings)

                if (runNow) {
                    require(settings.enabled) { "Enable the worker before running it" }
                    scheduler.runNow(settings)
                    status.text = "Worker queued"
                } else {
                    status.text = getString(R.string.status_saved)
                }
            } catch (e: IllegalArgumentException) {
                status.text = e.message ?: "Invalid settings"
            }
        }
    }

    private fun readSettings(): WorkerSettings = WorkerSettings(
        serverUrl = serverUrl.text.toString().trim(),
        secUserAgent = secUserAgent.text.toString().trim(),
        username = username.text.toString().trim(),
        enabled = enabled.isChecked,
        wifiOnly = wifiOnly.isChecked,
        chargingOnly = chargingOnly.isChecked,
        minimumBatteryPercent = minimumBattery.text.toString().toIntOrNull()
            ?: WorkerConstants.DEFAULT_MINIMUM_BATTERY,
        maxArtifactMb = maxArtifactMb.text.toString().toIntOrNull()
            ?: WorkerConstants.DEFAULT_MAX_ARTIFACT_MB,
    )
}
