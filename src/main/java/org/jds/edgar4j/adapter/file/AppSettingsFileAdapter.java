package org.jds.edgar4j.adapter.file;

import org.jds.edgar4j.model.AppSettings;
import org.jds.edgar4j.port.AppSettingsDataPort;
import org.jds.edgar4j.storage.file.FileFormat;
import org.jds.edgar4j.storage.file.FileStorageEngine;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("resource-low")
public class AppSettingsFileAdapter extends AbstractFileDataPort<AppSettings> implements AppSettingsDataPort {

    public AppSettingsFileAdapter(FileStorageEngine storageEngine) {
        super(storageEngine.registerCollection(
                "app_settings",
                AppSettings.class,
                FileFormat.JSON,
                AppSettings::getId,
                AppSettings::setId));
    }
}
