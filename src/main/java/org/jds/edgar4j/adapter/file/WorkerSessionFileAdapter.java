package org.jds.edgar4j.adapter.file;

import java.time.Instant;
import java.util.Optional;

import org.jds.edgar4j.model.WorkerSession;
import org.jds.edgar4j.port.WorkerSessionDataPort;
import org.jds.edgar4j.storage.file.FileCollection;
import org.jds.edgar4j.storage.file.FileFormat;
import org.jds.edgar4j.storage.file.FileStorageEngine;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("resource-low")
public class WorkerSessionFileAdapter implements WorkerSessionDataPort {

    private final FileCollection<WorkerSession> collection;
    private final Object mutationLock = new Object();

    public WorkerSessionFileAdapter(FileStorageEngine storageEngine) {
        this.collection = storageEngine.registerCollection(
                "worker_sessions",
                WorkerSession.class,
                FileFormat.JSON,
                WorkerSession::getId,
                WorkerSession::setId);
    }

    @Override
    public WorkerSession save(WorkerSession session) {
        synchronized (mutationLock) {
            return collection.save(session);
        }
    }

    @Override
    public Optional<WorkerSession> findActive(String sessionId, String tokenHash, Instant now) {
        return collection.findById(sessionId)
                .filter(session -> tokenHash != null && tokenHash.equals(session.getTokenHash()))
                .filter(session -> session.isActive(now));
    }

    @Override
    public Optional<WorkerSession> extend(String sessionId, String tokenHash, Instant now, Instant expiresAt) {
        synchronized (mutationLock) {
            Optional<WorkerSession> session = findActive(sessionId, tokenHash, now);
            if (session.isEmpty()) {
                return Optional.empty();
            }

            WorkerSession value = session.get();
            value.setLastSeenAt(now);
            value.setExpiresAt(expiresAt);
            collection.save(value);
            return Optional.of(value);
        }
    }

    @Override
    public boolean revoke(String sessionId, String tokenHash, Instant now) {
        synchronized (mutationLock) {
            Optional<WorkerSession> session = findActive(sessionId, tokenHash, now);
            if (session.isEmpty()) {
                return false;
            }

            WorkerSession value = session.get();
            value.setRevokedAt(now);
            value.setLastSeenAt(now);
            collection.save(value);
            return true;
        }
    }

    @Override
    public int deleteExpired(Instant now) {
        synchronized (mutationLock) {
            var expired = collection.findAllMatching(session -> !session.isActive(now));
            collection.deleteAll(expired);
            return expired.size();
        }
    }
}
