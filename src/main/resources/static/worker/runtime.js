export async function evaluateEligibility(policy) {
  const connection = navigator.connection || navigator.mozConnection || navigator.webkitConnection;
  const networkType = resolveNetworkType(connection);
  const battery = await readBattery();
  const availableStorageBytes = await readFreeStorage(policy.maxArtifactBytes);
  const runtime = {
    networkType,
    metered: connection?.saveData === true || networkType === 'CELLULAR',
    charging: battery?.charging === true,
    batteryPercent: battery ? Math.round(battery.level * 100) : null,
    freeStorageBytes: Math.min(availableStorageBytes, policy.maxArtifactBytes),
  };

  if (!navigator.onLine) return ineligible('Offline', runtime);
  if (policy.wifiOnly && networkType !== 'WIFI' && networkType !== 'ETHERNET') {
    return ineligible('Waiting for a known Wi-Fi connection', runtime);
  }
  if (policy.chargingOnly && battery?.charging !== true) {
    return ineligible('Waiting for charging', runtime);
  }
  if (policy.minimumBatteryPercent > 0 && !battery) {
    return ineligible('Battery state unavailable', runtime);
  }
  if (battery && runtime.batteryPercent < policy.minimumBatteryPercent) {
    return ineligible('Battery below threshold', runtime);
  }
  if (availableStorageBytes < policy.maxArtifactBytes) {
    return ineligible('Insufficient browser storage budget', runtime);
  }
  return { eligible: true, runtime };
}

export function readPolicy(elements) {
  const maxMb = clampNumber(Number(elements.maxMb.value), 1, 50, 10);
  return {
    enabled: true,
    wifiOnly: elements.wifiOnly.checked,
    chargingOnly: elements.chargingOnly.checked,
    minimumBatteryPercent: clampNumber(Number(elements.minBattery.value), 0, 100, 40),
    maxConcurrentTasks: 1,
    maxArtifactBytes: maxMb * 1024 * 1024,
  };
}

export function applyPolicy(elements, policy) {
  elements.wifiOnly.checked = policy.wifiOnly !== false;
  elements.chargingOnly.checked = policy.chargingOnly !== false;
  elements.minBattery.value = String(clampNumber(policy.minimumBatteryPercent, 0, 100, 40));
  elements.maxMb.value = String(Math.round(clampNumber(policy.maxArtifactBytes, 1024 * 1024, 50 * 1024 * 1024, 10 * 1024 * 1024) / 1024 / 1024));
}

function resolveNetworkType(connection) {
  const type = connection?.type?.toLowerCase();
  if (type === 'wifi') return 'WIFI';
  if (type === 'cellular') return 'CELLULAR';
  if (type === 'ethernet') return 'ETHERNET';
  return 'OTHER';
}

async function readBattery() {
  if (typeof navigator.getBattery !== 'function') return undefined;
  try {
    return await navigator.getBattery();
  } catch {
    return undefined;
  }
}

async function readFreeStorage(fallbackBytes) {
  try {
    const estimate = await navigator.storage?.estimate();
    if (estimate?.quota == null) return fallbackBytes;
    return Math.max(0, estimate.quota - (estimate.usage || 0));
  } catch {
    return fallbackBytes;
  }
}

function ineligible(reason, runtime) {
  return { eligible: false, reason, runtime };
}

function clampNumber(value, min, max, fallback) {
  if (!Number.isFinite(value)) return fallback;
  return Math.min(max, Math.max(min, value));
}
