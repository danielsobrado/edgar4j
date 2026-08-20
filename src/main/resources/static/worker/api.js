import {
  LEASE_TOKEN_HEADER,
  SESSION_ID_HEADER,
  SESSION_TOKEN_HEADER,
  SHA256_HEADER,
} from './constants.js';

export class WorkerApiError extends Error {
  constructor(message, status) {
    super(message);
    this.status = status;
  }
}

export async function openSession(request) {
  return requestJson('/api/workers/session', {
    method: 'POST',
    headers: jsonHeaders(),
    body: JSON.stringify(request),
  });
}

export async function leaseTasks(credentials, request) {
  return requestJson('/api/workers/tasks/lease', {
    method: 'POST',
    headers: authJsonHeaders(credentials),
    body: JSON.stringify(request),
  });
}

export async function heartbeat(credentials, taskId, leaseToken, runtime) {
  return requestJson(`/api/workers/tasks/${encodeURIComponent(taskId)}/heartbeat`, {
    method: 'POST',
    headers: authJsonHeaders(credentials),
    body: JSON.stringify({ leaseToken, runtime }),
  });
}

export async function reserveSource(credentials, task) {
  return requestJson(`/api/workers/tasks/${encodeURIComponent(task.id)}/source-permit`, {
    method: 'POST',
    headers: authJsonHeaders(credentials),
    body: JSON.stringify({ leaseToken: task.leaseToken }),
  });
}

export async function uploadArtifact(credentials, task, sha256, contentType, bytes) {
  return requestJson(`/api/workers/tasks/${encodeURIComponent(task.id)}/artifact`, {
    method: 'PUT',
    headers: {
      ...authHeaders(credentials),
      [LEASE_TOKEN_HEADER]: task.leaseToken,
      [SHA256_HEADER]: sha256,
      'Content-Type': contentType || 'application/octet-stream',
    },
    body: bytes,
  });
}

export async function reportFailure(credentials, task, code, message) {
  return requestJson(`/api/workers/tasks/${encodeURIComponent(task.id)}/failure`, {
    method: 'POST',
    headers: authJsonHeaders(credentials),
    body: JSON.stringify({
      leaseToken: task.leaseToken,
      code,
      message: sanitizeMessage(message),
    }),
  });
}

export async function abandon(credentials, task) {
  return requestJson(`/api/workers/tasks/${encodeURIComponent(task.id)}/abandon`, {
    method: 'POST',
    headers: authJsonHeaders(credentials),
    body: JSON.stringify({ leaseToken: task.leaseToken }),
  });
}

export async function revokeSession(credentials) {
  return requestJson('/api/workers/session', {
    method: 'DELETE',
    headers: authHeaders(credentials),
  });
}

function authHeaders(credentials) {
  return {
    [SESSION_ID_HEADER]: credentials.sessionId,
    [SESSION_TOKEN_HEADER]: credentials.sessionToken,
  };
}

function authJsonHeaders(credentials) {
  return {
    ...authHeaders(credentials),
    ...jsonHeaders(),
  };
}

function jsonHeaders() {
  return { 'Content-Type': 'application/json' };
}

async function requestJson(url, options) {
  const response = await fetch(url, {
    credentials: 'same-origin',
    cache: 'no-store',
    ...options,
  });
  const payload = await readPayload(response);
  if (!response.ok) {
    throw new WorkerApiError(payload?.message || `Worker API returned HTTP ${response.status}`, response.status);
  }
  return payload?.data;
}

async function readPayload(response) {
  const text = await response.text();
  if (!text) return undefined;
  try {
    return JSON.parse(text);
  } catch {
    return undefined;
  }
}

function sanitizeMessage(message) {
  if (!message) return undefined;
  return String(message).replace(/[\r\n]+/g, ' ').trim().slice(0, 512);
}
