import {
  abandon,
  heartbeat,
  leaseTasks,
  openSession,
  reportFailure,
  revokeSession,
  uploadArtifact,
  WorkerApiError,
} from './api.js';
import { getCachedArtifact, putCachedArtifact } from './cache.js';
import {
  CAPABILITIES,
  CLIENT_VERSION,
  DEFAULT_RETRY_SECONDS,
  HEARTBEAT_INTERVAL_MS,
  MAX_RETRY_SECONDS,
  POLICY_STORAGE_KEY,
  PROTOCOL_VERSION,
  SESSION_STORAGE_KEY,
} from './constants.js';
import { applyPolicy, evaluateEligibility, readPolicy } from './runtime.js';
import { downloadTask, validateTask, WorkerTaskFailure } from './source.js';

const elements = {
  wifiOnly: document.querySelector('#wifiOnly'),
  chargingOnly: document.querySelector('#chargingOnly'),
  minBattery: document.querySelector('#minBattery'),
  maxMb: document.querySelector('#maxMb'),
  start: document.querySelector('#start'),
  stop: document.querySelector('#stop'),
  state: document.querySelector('#state'),
  detail: document.querySelector('#detail'),
  completed: document.querySelector('#completed'),
  failed: document.querySelector('#failed'),
  cached: document.querySelector('#cached'),
};

let running = false;
let session;
let policy;
let completedTasks = 0;
let failedTasks = 0;
let cacheHits = 0;
const activeTasks = new Map();

restorePolicy();
renderStopped();

elements.start.addEventListener('click', () => {
  if (running) return;
  policy = readPolicy(elements);
  localStorage.setItem(POLICY_STORAGE_KEY, JSON.stringify({ ...policy, enabled: false }));
  running = true;
  elements.start.hidden = true;
  elements.stop.hidden = false;
  setStatus('Starting', 'Checking device policy and server availability.');
  void runLoop();
});

elements.stop.addEventListener('click', () => {
  void stopWorker();
});

window.addEventListener('pagehide', () => {
  activeTasks.forEach(({ controller }) => controller.abort());
});

async function runLoop() {
  let retrySeconds = DEFAULT_RETRY_SECONDS;
  while (running) {
    try {
      const eligibility = await evaluateEligibility(policy);
      if (!eligibility.eligible) {
        setStatus('Paused', eligibility.reason || 'Device policy is not satisfied.');
        await sleep(DEFAULT_RETRY_SECONDS * 1_000);
        continue;
      }

      const credentials = await ensureSession();
      setStatus('Connected', 'Waiting for eligible SEC work.');
      const lease = await leaseTasks(credentials, {
        protocolVersion: PROTOCOL_VERSION,
        capabilities: CAPABILITIES,
        maxTasks: policy.maxConcurrentTasks,
        runtime: eligibility.runtime,
      });
      retrySeconds = DEFAULT_RETRY_SECONDS;

      if (!lease?.tasks?.length) {
        const delay = Math.max(DEFAULT_RETRY_SECONDS, lease?.retryAfterSeconds || 0);
        await sleep(delay * 1_000);
        continue;
      }

      await Promise.all(
        lease.tasks.map((task) => processTask(credentials, task, eligibility.runtime)),
      );
    } catch (error) {
      if (!running) break;
      if (error instanceof WorkerApiError && error.status === 401) clearSession();
      setStatus('Retrying', messageOf(error));
      await sleep(retrySeconds * 1_000);
      retrySeconds = Math.min(MAX_RETRY_SECONDS, retrySeconds * 2);
    }
  }
}

async function processTask(credentials, task, runtime) {
  const controller = new AbortController();
  activeTasks.set(task.id, { task, controller });
  setStatus('Working', `Downloading ${task.resourceId}`);

  let heartbeatTimer;
  try {
    validateTask(task, policy);
    heartbeatTimer = window.setInterval(() => {
      void heartbeat(credentials, task.id, task.leaseToken, runtime)
        .catch(() => controller.abort());
    }, HEARTBEAT_INTERVAL_MS);

    let artifact = await getCachedArtifact(task);
    if (artifact) {
      cacheHits += 1;
      renderCounters();
      setStatus('Working', `Reusing cached ${task.resourceId}`);
    } else {
      artifact = await downloadTask(task, policy, controller);
      await putCachedArtifact(task, artifact.bytes, artifact.sha256, artifact.contentType);
    }

    await heartbeat(credentials, task.id, task.leaseToken, runtime);
    await uploadArtifact(
      credentials,
      task,
      artifact.sha256,
      artifact.contentType,
      artifact.bytes,
    );
    completedTasks += 1;
    renderCounters();
    setStatus('Connected', `Completed ${task.resourceId}`);
  } catch (error) {
    failedTasks += 1;
    renderCounters();
    await handleTaskFailure(credentials, task, error);
    setStatus('Retrying', messageOf(error));
  } finally {
    if (heartbeatTimer) window.clearInterval(heartbeatTimer);
    activeTasks.delete(task.id);
  }
}

async function handleTaskFailure(credentials, task, error) {
  if (!running) return;
  if (error instanceof WorkerApiError) {
    if (error.status === 401) {
      clearSession();
      return;
    }
    if (error.status === 409 || error.status === 422) return;
    if (error.status === 413) {
      await safeReport(credentials, task, 'INSUFFICIENT_STORAGE', 'Artifact exceeds upload policy');
      return;
    }
    if (error.status >= 500) {
      await safeReport(credentials, task, 'UPLOAD_FAILED', 'Artifact upload failed');
      return;
    }
  }

  if (error instanceof WorkerTaskFailure) {
    await safeReport(credentials, task, error.code, error.message);
    return;
  }
  await safeReport(credentials, task, 'INTERNAL_ERROR', messageOf(error));
}

async function safeReport(credentials, task, code, message) {
  try {
    await reportFailure(credentials, task, code, message);
  } catch (error) {
    if (error instanceof WorkerApiError && error.status === 401) clearSession();
  }
}

async function ensureSession() {
  const existing = session || loadSession();
  if (existing && Date.parse(existing.expiresAt) > Date.now() + 30_000) {
    session = existing;
    return existing;
  }

  const created = await openSession({
    protocolVersion: PROTOCOL_VERSION,
    platform: 'WEB',
    clientVersion: CLIENT_VERSION,
    capabilities: CAPABILITIES,
    maxConcurrentTasks: policy.maxConcurrentTasks,
  });
  session = {
    sessionId: created.sessionId,
    sessionToken: created.sessionToken,
    expiresAt: created.expiresAt,
  };
  sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session));
  return session;
}

async function stopWorker() {
  if (!running && !session) {
    renderStopped();
    return;
  }
  running = false;
  const credentials = session || loadSession();
  const tasks = Array.from(activeTasks.values());
  tasks.forEach(({ controller }) => controller.abort());
  activeTasks.clear();

  if (credentials) {
    await Promise.allSettled(tasks.map(({ task }) => abandon(credentials, task)));
    try {
      await revokeSession(credentials);
    } catch {
      // Expired sessions require no browser cleanup.
    }
  }
  clearSession();
  renderStopped();
}

function loadSession() {
  try {
    const value = sessionStorage.getItem(SESSION_STORAGE_KEY);
    if (!value) return undefined;
    const parsed = JSON.parse(value);
    if (!parsed.sessionId || !parsed.sessionToken || !parsed.expiresAt) return undefined;
    return parsed;
  } catch {
    return undefined;
  }
}

function clearSession() {
  session = undefined;
  sessionStorage.removeItem(SESSION_STORAGE_KEY);
}

function restorePolicy() {
  try {
    const value = localStorage.getItem(POLICY_STORAGE_KEY);
    if (value) applyPolicy(elements, JSON.parse(value));
  } catch {
    // Defaults from HTML remain active.
  }
}

function renderStopped() {
  elements.start.hidden = false;
  elements.stop.hidden = true;
  setStatus('Stopped', 'Press Start to opt in. Work only runs while this page stays open.');
}

function setStatus(state, detail) {
  elements.state.textContent = state;
  elements.detail.textContent = detail;
}

function renderCounters() {
  elements.completed.textContent = String(completedTasks);
  elements.failed.textContent = String(failedTasks);
  elements.cached.textContent = String(cacheHits);
}

function messageOf(error) {
  if (error instanceof Error) return error.message;
  return 'Worker operation failed';
}

function sleep(milliseconds) {
  return new Promise((resolve) => window.setTimeout(resolve, milliseconds));
}
