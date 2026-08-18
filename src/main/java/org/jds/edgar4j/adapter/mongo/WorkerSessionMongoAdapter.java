package org.jds.edgar4j.adapter.mongo;

import java.time.Instant;
import java.util.Optional;

import org.jds.edgar4j.model.WorkerSession;
import org.jds.edgar4j.port.WorkerSessionDataPort;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

@Component
@Profile("resource-high")
public class WorkerSessionMongoAdapter implements WorkerSessionDataPort {

    private final MongoTemplate mongoTemplate;

    public WorkerSessionMongoAdapter(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public WorkerSession save(WorkerSession session) {
        return mongoTemplate.save(session);
    }

    @Override
    public Optional<WorkerSession> findActive(String sessionId, String tokenHash, Instant now) {
        return Optional.ofNullable(mongoTemplate.findById(sessionId, WorkerSession.class))
                .filter(session -> tokenHash != null && tokenHash.equals(session.getTokenHash()))
                .filter(session -> session.isActive(now));
    }

    @Override
    public Optional<WorkerSession> extend(String sessionId, String tokenHash, Instant now, Instant expiresAt) {
        Optional<WorkerSession> session = findActive(sessionId, tokenHash, now);
        if (session.isEmpty()) {
            return Optional.empty();
        }

        WorkerSession value = session.get();
        value.setLastSeenAt(now);
        value.setExpiresAt(expiresAt);
        try {
            return Optional.of(mongoTemplate.save(value));
        } catch (OptimisticLockingFailureException e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean revoke(String sessionId, String tokenHash, Instant now) {
        Optional<WorkerSession> session = findActive(sessionId, tokenHash, now);
        if (session.isEmpty()) {
            return false;
        }

        WorkerSession value = session.get();
        value.setRevokedAt(now);
        value.setLastSeenAt(now);
        try {
            mongoTemplate.save(value);
            return true;
        } catch (OptimisticLockingFailureException e) {
            return false;
        }
    }

    @Override
    public int deleteExpired(Instant now) {
        Criteria expired = new Criteria().orOperator(
                Criteria.where("expiresAt").lte(now),
                Criteria.where("revokedAt").ne(null));
        long deleted = mongoTemplate.remove(Query.query(expired), WorkerSession.class).getDeletedCount();
        return Math.toIntExact(deleted);
    }
}
