export const PROTOCOL_VERSION = 1;
export const CLIENT_VERSION = 'mobile-web-1';
export const CAPABILITIES = ['DOWNLOAD', 'SHA256'];

export const SESSION_ID_HEADER = 'X-Worker-Session-Id';
export const SESSION_TOKEN_HEADER = 'X-Worker-Session-Token';
export const LEASE_TOKEN_HEADER = 'X-Worker-Lease-Token';
export const SHA256_HEADER = 'X-Artifact-Sha256';

export const SESSION_STORAGE_KEY = 'edgar4j.worker.session.v1';
export const POLICY_STORAGE_KEY = 'edgar4j.worker.policy.v1';
export const CACHE_DB_NAME = 'edgar4j-worker-cache-v1';
export const CACHE_STORE_NAME = 'artifacts';
export const CACHE_MAX_BYTES = 100 * 1024 * 1024;

export const FETCH_TIMEOUT_MS = 60_000;
export const HEARTBEAT_INTERVAL_MS = 60_000;
export const DEFAULT_RETRY_SECONDS = 10;
export const MAX_RETRY_SECONDS = 60;

export const ALLOWED_SEC_HOSTS = new Set([
  'www.sec.gov',
  'data.sec.gov',
  'efts.sec.gov',
]);
