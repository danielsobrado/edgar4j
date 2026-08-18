import apiClient from '../client';
import type { ApiResponse } from '../types';
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
  openSession: async (request: WorkerSessionRequest): Promise<WorkerSessionResponse> => {
    const response = await apiClient.post<ApiResponse<WorkerSessionResponse>>('/workers/session', request);
    return response.data.data!;
  },

  lease: async (
    credentials: WorkerCredentials,
    request: WorkerLeaseRequest,
  ): Promise<WorkerLeaseResponse> => {
    const response = await apiClient.post<ApiResponse<WorkerLeaseResponse>>('/workers/tasks/lease', request, {
      headers: authHeaders(credentials),
    });
    return response.data.data!;
  },

  heartbeat: async (
    credentials: WorkerCredentials,
    taskId: string,
    leaseToken: string,
    runtime: WorkerLeaseRequest['runtime'],
  ): Promise<WorkerHeartbeatResponse> => {
    const response = await apiClient.post<ApiResponse<WorkerHeartbeatResponse>>(
      `/workers/tasks/${encodeURIComponent(taskId)}/heartbeat`,
      { leaseToken, runtime },
      { headers: authHeaders(credentials) },
    );
    return response.data.data!;
  },

  uploadArtifact: async (
    credentials: WorkerCredentials,
    taskId: string,
    leaseToken: string,
    sha256: string,
    contentType: string,
    bytes: ArrayBuffer,
  ): Promise<WorkerArtifactResponse> => {
    const response = await apiClient.put<ApiResponse<WorkerArtifactResponse>>(
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
    return response.data.data!;
  },

  reportFailure: async (
    credentials: WorkerCredentials,
    taskId: string,
    leaseToken: string,
    code: WorkerFailureCode,
    message?: string,
  ): Promise<void> => {
    await apiClient.post(
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
    await apiClient.post(
      `/workers/tasks/${encodeURIComponent(taskId)}/abandon`,
      { leaseToken },
      { headers: authHeaders(credentials) },
    );
  },

  revokeSession: async (credentials: WorkerCredentials): Promise<void> => {
    await apiClient.delete('/workers/session', { headers: authHeaders(credentials) });
  },
};
