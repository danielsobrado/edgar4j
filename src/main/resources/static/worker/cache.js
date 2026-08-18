import { CACHE_DB_NAME, CACHE_MAX_BYTES, CACHE_STORE_NAME } from './constants.js';

export async function getCachedArtifact(task) {
  try {
    const db = await openDatabase();
    const record = await readRecord(db, task.resourceId);
    if (!record) return undefined;
    if (record.sizeBytes > task.maxBytes) {
      await deleteRecord(db, task.resourceId);
      return undefined;
    }

    const sha256 = await sha256Hex(record.bytes);
    if (sha256 !== record.sha256) {
      await deleteRecord(db, task.resourceId);
      return undefined;
    }
    if (task.expectedSha256 && sha256 !== task.expectedSha256.toLowerCase()) {
      await deleteRecord(db, task.resourceId);
      return undefined;
    }

    record.lastAccessAt = Date.now();
    await writeRecord(db, record);
    return {
      bytes: record.bytes,
      sha256,
      contentType: record.contentType,
    };
  } catch {
    return undefined;
  }
}

export async function putCachedArtifact(task, bytes, sha256, contentType) {
  if (bytes.byteLength <= 0 || bytes.byteLength > Math.min(task.maxBytes, CACHE_MAX_BYTES)) return;
  try {
    const db = await openDatabase();
    await writeRecord(db, {
      resourceId: task.resourceId,
      sha256,
      contentType,
      bytes,
      sizeBytes: bytes.byteLength,
      lastAccessAt: Date.now(),
    });
    await evictIfNeeded(db);
  } catch {
    // Cache failure never affects task correctness.
  }
}

async function evictIfNeeded(db) {
  const records = await readAllMetadata(db);
  let total = records.reduce((sum, record) => sum + record.sizeBytes, 0);
  if (total <= CACHE_MAX_BYTES) return;

  records.sort((a, b) => a.lastAccessAt - b.lastAccessAt);
  for (const record of records) {
    if (total <= CACHE_MAX_BYTES) break;
    await deleteRecord(db, record.resourceId);
    total -= record.sizeBytes;
  }
}

function openDatabase() {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(CACHE_DB_NAME, 1);
    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains(CACHE_STORE_NAME)) {
        db.createObjectStore(CACHE_STORE_NAME, { keyPath: 'resourceId' });
      }
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

function readRecord(db, resourceId) {
  return transactionRequest(db, 'readonly', (store) => store.get(resourceId));
}

function writeRecord(db, record) {
  return transactionRequest(db, 'readwrite', (store) => store.put(record));
}

function deleteRecord(db, resourceId) {
  return transactionRequest(db, 'readwrite', (store) => store.delete(resourceId));
}

function readAllMetadata(db) {
  return new Promise((resolve, reject) => {
    const transaction = db.transaction(CACHE_STORE_NAME, 'readonly');
    const store = transaction.objectStore(CACHE_STORE_NAME);
    const records = [];
    const request = store.openCursor();
    request.onsuccess = () => {
      const cursor = request.result;
      if (!cursor) return;
      const value = cursor.value;
      records.push({
        resourceId: value.resourceId,
        sizeBytes: value.sizeBytes,
        lastAccessAt: value.lastAccessAt || 0,
      });
      cursor.continue();
    };
    transaction.oncomplete = () => resolve(records);
    transaction.onerror = () => reject(transaction.error);
    transaction.onabort = () => reject(transaction.error);
  });
}

function transactionRequest(db, mode, operation) {
  return new Promise((resolve, reject) => {
    const transaction = db.transaction(CACHE_STORE_NAME, mode);
    const request = operation(transaction.objectStore(CACHE_STORE_NAME));
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
    transaction.onerror = () => reject(transaction.error);
    transaction.onabort = () => reject(transaction.error);
  });
}

async function sha256Hex(bytes) {
  const digest = await crypto.subtle.digest('SHA-256', bytes);
  return Array.from(new Uint8Array(digest), (value) => value.toString(16).padStart(2, '0')).join('');
}
