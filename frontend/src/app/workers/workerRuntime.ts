import type { WorkerPolicy, WorkerRuntimeState } from '../api/workerTypes';

interface BrowserConnection {
  type?: string;
  effectiveType?: string;
  saveData?: boolean;
}

interface BrowserBattery {
  charging: boolean;
  level: number;
}

interface WorkerNavigator extends Navigator {
  connection?: BrowserConnection;
  mozConnection?: BrowserConnection;
  webkitConnection?: BrowserConnection;
  getBattery?: () => Promise<BrowserBattery>;
}

export interface WorkerEligibility {
  eligible: boolean;
  reason?: string;
  runtime: WorkerRuntimeState;
}

export async function evaluateWorkerEligibility(policy: WorkerPolicy): Promise<WorkerEligibility> {
  const workerNavigator = navigator as WorkerNavigator;
  const connection =
    workerNavigator.connection ?? workerNavigator.mozConnection ?? workerNavigator.webkitConnection;
  const networkType = resolveNetworkType(connection);
  const metered = connection?.saveData === true || networkType === 'CELLULAR';
  const battery = await readBattery(workerNavigator);
  const availableStorageBytes = await readFreeStorage(policy.maxArtifactBytes);
  const contributedStorageBytes = Math.min(availableStorageBytes, policy.maxArtifactBytes);

  const runtime: WorkerRuntimeState = {
    networkType,
    metered,
    charging: battery?.charging ?? false,
    batteryPercent: battery ? Math.round(battery.level * 100) : undefined,
    freeStorageBytes: contributedStorageBytes,
  };

  if (!navigator.onLine) {
    return { eligible: false, reason: 'Offline', runtime };
  }
  if (policy.wifiOnly && networkType !== 'WIFI' && networkType !== 'ETHERNET') {
    return { eligible: false, reason: 'Waiting for Wi-Fi', runtime };
  }
  if (policy.chargingOnly && battery?.charging !== true) {
    return { eligible: false, reason: 'Waiting for charging', runtime };
  }
  if (policy.minimumBatteryPercent > 0 && !battery) {
    return { eligible: false, reason: 'Battery state unavailable', runtime };
  }
  if (battery && runtime.batteryPercent! < policy.minimumBatteryPercent) {
    return { eligible: false, reason: 'Battery below worker threshold', runtime };
  }
  if (availableStorageBytes < policy.maxArtifactBytes) {
    return { eligible: false, reason: 'Insufficient browser storage budget', runtime };
  }

  return { eligible: true, runtime };
}

function resolveNetworkType(connection?: BrowserConnection): WorkerRuntimeState['networkType'] {
  const type = connection?.type?.toLowerCase();
  if (type === 'wifi') return 'WIFI';
  if (type === 'cellular') return 'CELLULAR';
  if (type === 'ethernet') return 'ETHERNET';
  return 'OTHER';
}

async function readBattery(workerNavigator: WorkerNavigator): Promise<BrowserBattery | undefined> {
  if (!workerNavigator.getBattery) return undefined;
  try {
    return await workerNavigator.getBattery();
  } catch {
    return undefined;
  }
}

async function readFreeStorage(fallbackBytes: number): Promise<number> {
  try {
    const estimate = await navigator.storage?.estimate();
    if (estimate?.quota == null) return fallbackBytes;
    return Math.max(0, estimate.quota - (estimate.usage ?? 0));
  } catch {
    return fallbackBytes;
  }
}
