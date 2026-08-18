export type WorkerCapability = 'DOWNLOAD' | 'SHA256';
export type WorkerPlatform = 'WEB';
export type WorkerSource = 'SEC_EDGAR';
export type WorkerNetworkType = 'WIFI' | 'CELLULAR' | 'ETHERNET' | 'OTHER';

export type WorkerFailureCode =
  | 'SOURCE_TIMEOUT'
  | 'SOURCE_RATE_LIMITED'
  | 'SOURCE_NOT_FOUND'
  | 'SOURCE_REJECTED'
  | 'NETWORK_UNAVAILABLE'
  | 'POLICY_CHANGED'
  | 'INSUFFICIENT_STORAGE'
  | 'CHECKSUM_MISMATCH'
  | 'CONTENT_INVALID'
  | 'UPLOAD_FAILED'
  | 'LEASE_EXPIRED'
  | 'WORKER_CANCELLED'
  | 'INTERNAL_ERROR';

export interface WorkerRuntimeState {
  networkType: WorkerNetworkType;
  metered: boolean;
  charging: boolean;
  batteryPercent?: number;
  freeStorageBytes: number;
}

export interface WorkerSessionRequest {
  protocolVersion: number;
  platform: WorkerPlatform;
  clientVersion: string;
  capabilities: WorkerCapability[];
  maxConcurrentTasks: number;
}

export interface WorkerSessionResponse {
  sessionId: string;
  sessionToken: string;
  protocolVersion: number;
  expiresAt: string;
}

export interface WorkerLeaseRequest {
  protocolVersion: number;
  capabilities: WorkerCapability[];
  maxTasks: number;
  runtime: WorkerRuntimeState;
}

export interface WorkerTaskResponse {
  id: string;
  type: 'DOWNLOAD';
  resourceId: string;
  source: WorkerSource;
  sourceUrl: string;
  leaseToken: string;
  leaseExpiresAt: string;
  notBefore?: string;
  maxBytes: number;
  expectedSha256?: string;
  contentType?: string;
}

export interface WorkerLeaseResponse {
  tasks: WorkerTaskResponse[];
  retryAfterSeconds: number;
}

export interface WorkerHeartbeatResponse {
  leaseExpiresAt: string;
}

export interface WorkerArtifactResponse {
  artifactId: string;
  sha256: string;
  sizeBytes: number;
  contentType?: string;
  verifiedAt: string;
}

export interface WorkerPolicy {
  enabled: boolean;
  wifiOnly: boolean;
  chargingOnly: boolean;
  minimumBatteryPercent: number;
  maxConcurrentTasks: number;
  maxArtifactBytes: number;
}

export interface WorkerStatus {
  running: boolean;
  eligible: boolean;
  activeTasks: number;
  completedTasks: number;
  failedTasks: number;
  lastError?: string;
}
