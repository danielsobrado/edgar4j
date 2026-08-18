import { ALLOWED_SEC_HOSTS, FETCH_TIMEOUT_MS } from './constants.js';

export class WorkerTaskFailure extends Error {
  constructor(code, message) {
    super(message);
    this.code = code;
  }
}

export function validateTask(task, policy) {
  if (task.type !== 'DOWNLOAD' || task.source !== 'SEC_EDGAR') {
    throw new WorkerTaskFailure('POLICY_CHANGED', 'Unsupported worker task type or source');
  }

  let url;
  try {
    url = new URL(task.sourceUrl);
  } catch {
    throw new WorkerTaskFailure('SOURCE_REJECTED', 'Invalid source URL');
  }

  if (url.protocol !== 'https:' || !ALLOWED_SEC_HOSTS.has(url.hostname.toLowerCase())) {
    throw new WorkerTaskFailure('SOURCE_REJECTED', 'Source URL is outside the SEC allowlist');
  }
  if (task.maxBytes <= 0 || task.maxBytes > policy.maxArtifactBytes) {
    throw new WorkerTaskFailure('INSUFFICIENT_STORAGE', 'Task exceeds the local artifact policy');
  }
}

export async function downloadTask(task, policy, controller) {
  const timeout = window.setTimeout(() => controller.abort(), FETCH_TIMEOUT_MS);
  const maxBytes = Math.min(task.maxBytes, policy.maxArtifactBytes);
  try {
    const response = await fetch(task.sourceUrl, {
      method: 'GET',
      cache: 'no-store',
      credentials: 'omit',
      redirect: 'error',
      signal: controller.signal,
    });

    if (response.status === 404) {
      throw new WorkerTaskFailure('SOURCE_NOT_FOUND', 'Source artifact was not found');
    }
    if (response.status === 429) {
      throw new WorkerTaskFailure('SOURCE_RATE_LIMITED', 'Source rate limited the worker');
    }
    if (!response.ok) {
      throw new WorkerTaskFailure('SOURCE_REJECTED', `Source returned HTTP ${response.status}`);
    }

    const declaredLength = Number(response.headers.get('content-length') || 0);
    if (declaredLength > maxBytes) {
      throw new WorkerTaskFailure('INSUFFICIENT_STORAGE', 'Source artifact exceeds the task limit');
    }

    const bytes = response.body
      ? await readBoundedStream(response.body, maxBytes)
      : await readBoundedBuffer(response, maxBytes);
    const sha256 = await sha256Hex(bytes);
    if (task.expectedSha256 && sha256 !== task.expectedSha256.toLowerCase()) {
      throw new WorkerTaskFailure('CHECKSUM_MISMATCH', 'Downloaded checksum does not match task metadata');
    }

    return {
      bytes,
      sha256,
      contentType: task.contentType || response.headers.get('content-type') || 'application/octet-stream',
    };
  } catch (error) {
    if (error instanceof WorkerTaskFailure) throw error;
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw new WorkerTaskFailure('SOURCE_TIMEOUT', 'Source download timed out or the lease expired');
    }
    if (error instanceof TypeError) {
      throw new WorkerTaskFailure('NETWORK_UNAVAILABLE', 'Source download failed');
    }
    throw error;
  } finally {
    window.clearTimeout(timeout);
  }
}

export async function sha256Hex(bytes) {
  const digest = await crypto.subtle.digest('SHA-256', bytes);
  return Array.from(new Uint8Array(digest), (value) => value.toString(16).padStart(2, '0')).join('');
}

async function readBoundedBuffer(response, maxBytes) {
  const bytes = await response.arrayBuffer();
  if (bytes.byteLength > maxBytes) {
    throw new WorkerTaskFailure('INSUFFICIENT_STORAGE', 'Source artifact exceeds the task limit');
  }
  return bytes;
}

async function readBoundedStream(stream, maxBytes) {
  const reader = stream.getReader();
  const chunks = [];
  let totalBytes = 0;
  try {
    while (true) {
      const result = await reader.read();
      if (result.done) break;
      totalBytes += result.value.byteLength;
      if (totalBytes > maxBytes) {
        await reader.cancel();
        throw new WorkerTaskFailure('INSUFFICIENT_STORAGE', 'Source artifact exceeds the task limit');
      }
      chunks.push(result.value);
    }
  } finally {
    reader.releaseLock();
  }

  const combined = new Uint8Array(totalBytes);
  let offset = 0;
  for (const chunk of chunks) {
    combined.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return combined.buffer;
}
