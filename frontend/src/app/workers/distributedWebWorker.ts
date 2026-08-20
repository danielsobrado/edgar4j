import { isAxiosError } from 'axios';
import { workersApi, type WorkerCredentials } from '../api/endpoints/workers';
import type {
  WorkerFailureCode,
  WorkerPolicy,
  WorkerRuntimeState,
  WorkerSessionResponse,
  WorkerStatus,
  WorkerTaskResponse,
} from '../api/workerTypes';
import {
  DEFAULT_WORKER_POLICY,
  WORKER_ALLOWED_SEC_HOSTS,
  WORKER_CAPABILITIES,
  WORKER_CLIENT_VERSION,
  WORKER_FETCH_TIMEOUT_MS,
  WORKER_IDLE_RETRY_SECONDS,
  WORKER_MAX_RETRY_SECONDS,
  WORKER_PROTOCOL_VERSION,
  WORKER_SESSION_STORAGE_KEY,
} from './workerConstants';
import { evaluateWorkerEligibility } from './workerRuntime';

interface StoredSession extends WorkerCredentials {
  expiresAt: string;
}

interface ActiveTask {
  controller: AbortController;
  task: WorkerTaskResponse;
}

class WorkerTaskFailure extends Error {
  constructor(
    readonly code: WorkerFailureCode,
    message: string,
  ) {
    super(message);
  }
}

type StatusListener = (status: WorkerStatus) => void;

class DistributedWebWorker {
  private running = false;
  private policy: WorkerPolicy = DEFAULT_WORKER_POLICY;
  private session?: StoredSession;
  private activeTasks = new Map<string, ActiveTask>();
  private listeners = new Set<StatusListener>();
  private status: WorkerStatus = {
    running: false,
    eligible: false,
    activeTasks: 0,
    completedTasks: 0,
    failedTasks: 0,
  };

  subscribe(listener: StatusListener): () => void {
    this.listeners.add(listener);
    listener(this.status);
    return () => this.listeners.delete(listener);
  }

  getStatus(): WorkerStatus {
    return this.status;
  }

  start(policy: WorkerPolicy): void {
    this.policy = policy;
    if (this.running || !policy.enabled) return;
    this.running = true;
    this.updateStatus({ running: true, lastError: undefined });
    void this.runLoop();
  }

  async stop(): Promise<void> {
    this.running = false;
    const active = Array.from(this.activeTasks.values());
    active.forEach(({ controller }) => controller.abort());
    this.activeTasks.clear();

    const session = this.session ?? this.loadSession();
    this.session = undefined;
    sessionStorage.removeItem(WORKER_SESSION_STORAGE_KEY);
    if (session) {
      await Promise.allSettled(
        active.map(({ task }) => workersApi.abandon(session, task.id, task.leaseToken)),
      );
      try {
        await workersApi.revokeSession(session);
      } catch {
        // Expired sessions require no cleanup from the browser.
      }
    }
    this.updateStatus({ running: false, eligible: false, activeTasks: 0 });
  }

  updatePolicy(policy: WorkerPolicy): void {
    this.policy = policy;
    if (!policy.enabled) {
      void this.stop();
    } else if (!this.running) {
      this.start(policy);
    }
  }

  private async runLoop(): Promise<void> {
    let retrySeconds = WORKER_IDLE_RETRY_SECONDS;
    while (this.running) {
      try {
        const eligibility = await evaluateWorkerEligibility(this.policy);
        this.updateStatus({ eligible: eligibility.eligible, lastError: eligibility.reason });
        if (!eligibility.eligible) {
          await sleep(WORKER_IDLE_RETRY_SECONDS * 1_000);
          continue;
        }

        const credentials = await this.ensureSession();
        const lease = await workersApi.lease(credentials, {
          protocolVersion: WORKER_PROTOCOL_VERSION,
          capabilities: WORKER_CAPABILITIES,
          maxTasks: this.policy.maxConcurrentTasks,
          runtime: eligibility.runtime,
        });
        retrySeconds = WORKER_IDLE_RETRY_SECONDS;

        if (lease.tasks.length === 0) {
          await sleep(Math.max(WORKER_IDLE_RETRY_SECONDS, lease.retryAfterSeconds) * 1_000);
          continue;
        }

        await Promise.all(
          lease.tasks.map((task) => this.processTask(credentials, task, eligibility.runtime)),
        );
      } catch (error) {
        if (!this.running) break;
        if (isAxiosError(error) && error.response?.status === 401) {
          this.clearSession();
        }
        this.updateStatus({ lastError: errorMessage(error) });
        await sleep(retrySeconds * 1_000);
        retrySeconds = Math.min(WORKER_MAX_RETRY_SECONDS, retrySeconds * 2);
      }
    }
  }

  private async processTask(
    credentials: WorkerCredentials,
    task: WorkerTaskResponse,
    runtime: WorkerRuntimeState,
  ): Promise<void> {
    const controller = new AbortController();
    this.activeTasks.set(task.id, { task, controller });
    this.updateStatus({ activeTasks: this.activeTasks.size });

    let heartbeatTimer: number | undefined;
    try {
      validateTask(task, this.policy);
      heartbeatTimer = window.setInterval(() => {
        void workersApi
          .heartbeat(credentials, task.id, task.leaseToken, runtime)
          .catch(() => controller.abort());
      }, 60_000);

      await workersApi.reserveSource(credentials, task.id, task.leaseToken);
      const bytes = await fetchBounded(task, this.policy.maxArtifactBytes, controller);
      const sha256 = await sha256Hex(bytes);
      if (task.expectedSha256 && sha256 !== task.expectedSha256.toLowerCase()) {
        throw new WorkerTaskFailure('CHECKSUM_MISMATCH', 'Downloaded checksum does not match task metadata');
      }

      await workersApi.heartbeat(credentials, task.id, task.leaseToken, runtime);
      await workersApi.uploadArtifact(
        credentials,
        task.id,
        task.leaseToken,
        sha256,
        task.contentType ?? 'application/octet-stream',
        bytes,
      );
      this.updateStatus({ completedTasks: this.status.completedTasks + 1, lastError: undefined });
    } catch (error) {
      if (this.running) {
        await this.reportTaskFailure(credentials, task, error);
      }
      this.updateStatus({
        failedTasks: this.status.failedTasks + 1,
        lastError: errorMessage(error),
      });
    } finally {
      if (heartbeatTimer != null) window.clearInterval(heartbeatTimer);
      this.activeTasks.delete(task.id);
      this.updateStatus({ activeTasks: this.activeTasks.size });
    }
  }

  private async reportTaskFailure(
    credentials: WorkerCredentials,
    task: WorkerTaskResponse,
    error: unknown,
  ): Promise<void> {
    const status = isAxiosError(error) ? error.response?.status : undefined;
    if (status === 401) {
      this.clearSession();
      return;
    }
    if (status === 409 || status === 422) return;

    let failure = classifyFailure(error);
    if (status === 413) {
      failure = new WorkerTaskFailure('INSUFFICIENT_STORAGE', 'Artifact exceeds worker upload policy');
    } else if (status === 429) {
      failure = new WorkerTaskFailure('SOURCE_RATE_LIMITED', 'Source request permit unavailable');
    } else if (status != null && status >= 500) {
      failure = new WorkerTaskFailure('UPLOAD_FAILED', 'Worker API request failed');
    }

    try {
      await workersApi.reportFailure(
        credentials,
        task.id,
        task.leaseToken,
        failure.code,
        failure.message,
      );
    } catch (reportError) {
      if (isAxiosError(reportError) && reportError.response?.status === 401) {
        this.clearSession();
      }
    }
  }

  private async ensureSession(): Promise<StoredSession> {
    const existing = this.session ?? this.loadSession();
    if (existing && Date.parse(existing.expiresAt) > Date.now() + 30_000) {
      this.session = existing;
      return existing;
    }

    const created: WorkerSessionResponse = await workersApi.openSession({
      protocolVersion: WORKER_PROTOCOL_VERSION,
      platform: 'WEB',
      clientVersion: WORKER_CLIENT_VERSION,
      capabilities: WORKER_CAPABILITIES,
      maxConcurrentTasks: this.policy.maxConcurrentTasks,
    });
    const session: StoredSession = {
      sessionId: created.sessionId,
      sessionToken: created.sessionToken,
      expiresAt: created.expiresAt,
    };
    this.session = session;
    sessionStorage.setItem(WORKER_SESSION_STORAGE_KEY, JSON.stringify(session));
    return session;
  }

  private loadSession(): StoredSession | undefined {
    try {
      const value = sessionStorage.getItem(WORKER_SESSION_STORAGE_KEY);
      if (!value) return undefined;
      const session = JSON.parse(value) as StoredSession;
      if (!session.sessionId || !session.sessionToken || !session.expiresAt) return undefined;
      return session;
    } catch {
      return undefined;
    }
  }

  private clearSession(): void {
    this.session = undefined;
    sessionStorage.removeItem(WORKER_SESSION_STORAGE_KEY);
  }

  private updateStatus(patch: Partial<WorkerStatus>): void {
    this.status = { ...this.status, ...patch };
    this.listeners.forEach((listener) => listener(this.status));
  }
}

function validateTask(task: WorkerTaskResponse, policy: WorkerPolicy): void {
  if (task.type !== 'DOWNLOAD' || task.source !== 'SEC_EDGAR') {
    throw new WorkerTaskFailure('POLICY_CHANGED', 'Unsupported worker task type or source');
  }
  let url: URL;
  try {
    url = new URL(task.sourceUrl);
  } catch {
    throw new WorkerTaskFailure('SOURCE_REJECTED', 'Invalid source URL');
  }
  if (url.protocol !== 'https:' || !WORKER_ALLOWED_SEC_HOSTS.has(url.hostname.toLowerCase())) {
    throw new WorkerTaskFailure('SOURCE_REJECTED', 'Source URL is outside the SEC allowlist');
  }
  if (url.port && url.port !== '443') {
    throw new WorkerTaskFailure('SOURCE_REJECTED', 'Source URL must use the standard HTTPS port');
  }
  if (url.username || url.password || url.hash) {
    throw new WorkerTaskFailure('SOURCE_REJECTED', 'Source URL contains forbidden components');
  }
  if (task.maxBytes <= 0 || task.maxBytes > policy.maxArtifactBytes) {
    throw new WorkerTaskFailure('INSUFFICIENT_STORAGE', 'Task exceeds the local artifact policy');
  }
}

async function fetchBounded(
  task: WorkerTaskResponse,
  policyMaxBytes: number,
  controller: AbortController,
): Promise<ArrayBuffer> {
  const timeout = window.setTimeout(() => controller.abort(), WORKER_FETCH_TIMEOUT_MS);
  const maxBytes = Math.min(task.maxBytes, policyMaxBytes);
  try {
    const response = await fetch(task.sourceUrl, {
      method: 'GET',
      cache: 'no-store',
      credentials: 'omit',
      redirect: 'error',
      signal: controller.signal,
    });
    if (response.status === 404 || response.status === 410) {
      throw new WorkerTaskFailure('SOURCE_NOT_FOUND', 'Source artifact was not found');
    }
    if (response.status === 408 || response.status === 504) {
      throw new WorkerTaskFailure('SOURCE_TIMEOUT', `Source returned HTTP ${response.status}`);
    }
    if (response.status === 403 || response.status === 429) {
      throw new WorkerTaskFailure('SOURCE_RATE_LIMITED', `Source returned HTTP ${response.status}`);
    }
    if (response.status >= 500) {
      throw new WorkerTaskFailure('NETWORK_UNAVAILABLE', `Source returned HTTP ${response.status}`);
    }
    if (!response.ok) {
      throw new WorkerTaskFailure('SOURCE_REJECTED', `Source returned HTTP ${response.status}`);
    }

    const declaredLength = Number(response.headers.get('content-length') ?? 0);
    if (declaredLength > maxBytes) {
      throw new WorkerTaskFailure('INSUFFICIENT_STORAGE', 'Source artifact exceeds the task limit');
    }
    if (!response.body) {
      const bytes = await response.arrayBuffer();
      if (bytes.byteLength > maxBytes) {
        throw new WorkerTaskFailure('INSUFFICIENT_STORAGE', 'Source artifact exceeds the task limit');
      }
      return bytes;
    }

    const reader = response.body.getReader();
    const chunks: Uint8Array[] = [];
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

async function sha256Hex(bytes: ArrayBuffer): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', bytes);
  return Array.from(new Uint8Array(digest), (value) => value.toString(16).padStart(2, '0')).join('');
}

function classifyFailure(error: unknown): WorkerTaskFailure {
  if (error instanceof WorkerTaskFailure) return error;
  return new WorkerTaskFailure('INTERNAL_ERROR', errorMessage(error));
}

function errorMessage(error: unknown): string {
  if (error instanceof Error) return error.message;
  return 'Worker operation failed';
}

function sleep(milliseconds: number): Promise<void> {
  return new Promise((resolve) => window.setTimeout(resolve, milliseconds));
}

export const distributedWebWorker = new DistributedWebWorker();
