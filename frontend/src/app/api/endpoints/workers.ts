import { apiClient } from '../client';
import type {
  WorkerArtifactResponse,
  WorkerFailureCode,
  WorkerHeartbeatResponse,
  WorkerLeaseRequest,
  WorkerLeaseResponse,
  WorkerSessionRequest,
  WorkerSessionResponse,
} from '../workerTypes';

const SESSION_ID_HEADER = 'X-Worker-Session-Id';
const SESSION_TOKEN_HEADER = 'X-Worker-Session-Token';
const LEASE_TOKEN_HEADER = 'X-Worker-Lease-Token';
const SHA256_HEADER = 'X-Artifact-Sha256';

export interface WorkerCredentials {
  sessionId: string;
  sessionToken: string;
}

function authHeaders(credentials: WorkerCredentials) {
  return {
    [SESSION_ID_HEADER]: credentials.sessionId,
    [SESSION_TOKEN_HEADER]: credentials.sessionToken,
  };
}

export const workersApi = {
  openSession: (request: WorkerSessionRequest): Promise<WorkerSessionResponse> => {
    return apiClient.post<WorkerSessionResponse>('/workers/session', request);
  },

  lease: (
    credentials: WorkerCredentials,
    request: WorkerLeaseRequest,
  ): Promise<WorkerLeaseResponse> => {
    return apiClient.post<WorkerLeaseResponse>('/workers/tasks/lease', request, {
      headers: authHeaders(credentials),
    });
  },

  heartbeat: (
    credentials: WorkerCredentials,
    taskId: string,
    leaseToken: string,
    runtime: WorkerLeaseRequest['runtime'],
  ): Promise<WorkerHeartbeatResponse> => {
    return apiClient.post<WorkerHeartbeatResponse>(
      `/workers/tasks/${encodeURIComponent(taskId)}/heartbeat`,
      { leaseToken, runtime },
      { headers: authHeaders(credentials) },
    );
  },

  reserveSource: (
    credentials: WorkerCredentials,
    taskId: string,
    leaseToken: string,
  ): Promise<void> => {
    return apiClient.post<void>(
      `/workers/tasks/${encodeURIComponent(taskId)}/source-permit`,
      { leaseToken },
      { headers: authHeaders(credentials) },
    );
  },

  uploadArtifact: (
    credentials: WorkerCredentials,
    taskId: string,
    leaseToken: string,
    sha256: string,
    contentType: string,
    bytes: ArrayBuffer,
  ): Promise<WorkerArtifactResponse> => {
    return apiClient.put<WorkerArtifactResponse>(
      `/workers/tasks/${encodeURIComponent(taskId)}/artifact`,
      bytes,
      {
        headers: {
          ...authHeaders(credentials),
          [LEASE_TOKEN_HEADER]: leaseToken,
          [SHA256_HEADER]: sha256,
          'Content-Type': contentType,
        },
        timeout: 120_000,
      },
    );
  },

  reportFailure: async (
    credentials: WorkerCredentials,
    taskId: string,
    leaseToken: string,
    code: WorkerFailureCode,
    message?: string,
  ): Promise<void> => {
    await apiClient.post<void>(
      `/workers/tasks/${encodeURIComponent(taskId)}/failure`,
      { leaseToken, code, message },
      { headers: authHeaders(credentials) },
    );
  },

  abandon: async (
    credentials: WorkerCredentials,
    taskId: string,
    leaseToken: string,
  ): Promise<void> => {
    await apiClient.post<void>(
      `/workers/tasks/${encodeURIComponent(taskId)}/abandon`,
      { leaseToken },
      { headers: authHeaders(credentials) },
    );
  },

  revokeSession: async (credentials: WorkerCredentials): Promise<void> => {
    await apiClient.delete<void>('/workers/session', { headers: authHeaders(credentials) });
  },
};
