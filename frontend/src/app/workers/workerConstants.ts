import type { WorkerCapability, WorkerPolicy } from '../api/workerTypes';

export const WORKER_PROTOCOL_VERSION = 1;
export const WORKER_CAPABILITIES: WorkerCapability[] = ['DOWNLOAD', 'SHA256'];
export const WORKER_POLICY_STORAGE_KEY = 'edgar4j.distributed-worker.policy.v1';
export const WORKER_SESSION_STORAGE_KEY = 'edgar4j.distributed-worker.session.v1';
export const WORKER_CLIENT_VERSION = 'web-1';
export const WORKER_IDLE_RETRY_SECONDS = 10;
export const WORKER_MAX_RETRY_SECONDS = 60;
export const WORKER_FETCH_TIMEOUT_MS = 60_000;
export const WORKER_HEARTBEAT_INTERVAL_MS = 60_000;

export const WORKER_ALLOWED_SEC_HOSTS = new Set([
  'www.sec.gov',
  'data.sec.gov',
  'efts.sec.gov',
]);

export const DEFAULT_WORKER_POLICY: WorkerPolicy = {
  enabled: false,
  wifiOnly: true,
  chargingOnly: true,
  minimumBatteryPercent: 40,
  maxConcurrentTasks: 1,
  maxArtifactBytes: 50 * 1024 * 1024,
};
