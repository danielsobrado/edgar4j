package org.jds.edgar4j.adapter.mongo;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.jds.edgar4j.constants.WorkerProtocolConstants;
import org.jds.edgar4j.model.WorkerFailureCode;
import org.jds.edgar4j.model.WorkerTask;
import org.jds.edgar4j.model.WorkerTaskStatus;
import org.jds.edgar4j.port.WorkerTaskDataPort;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
@Profile("resource-high")
public class WorkerTaskMongoAdapter implements WorkerTaskDataPort {

    private final MongoTemplate mongoTemplate;

    public WorkerTaskMongoAdapter(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @PostConstruct
    void ensureIndexes() {
        mongoTemplate.indexOps(WorkerTask.class).ensureIndex(
                new Index().on("logicalKey", Sort.Direction.ASC).unique());
        mongoTemplate.indexOps(WorkerTask.class).ensureIndex(
                new Index()
                        .on("status", Sort.Direction.ASC)
                        .on("notBefore", Sort.Direction.ASC)
                        .on("priority", Sort.Direction.DESC)
                        .on("createdAt", Sort.Direction.ASC));
    }

    @Override
    public WorkerTask createIfAbsent(WorkerTask task) {
        Objects.requireNonNull(task, "task");
        if (task.getLogicalKey() == null || task.getLogicalKey().isBlank()) {
            throw new IllegalArgumentException("Worker task logicalKey is required");
        }

        Optional<WorkerTask> existing = findByLogicalKey(task.getLogicalKey());
        if (existing.isPresent()) {
            return existing.get();
        }

        try {
            return mongoTemplate.insert(task);
        } catch (DuplicateKeyException e) {
            return findByLogicalKey(task.getLogicalKey()).orElseThrow(() -> e);
        }
    }

    @Override
    public Optional<WorkerTask> findById(String taskId) {
        return Optional.ofNullable(mongoTemplate.findById(taskId, WorkerTask.class));
    }

    @Override
    public Optional<WorkerTask> findByLogicalKey(String logicalKey) {
        if (logicalKey == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mongoTemplate.findOne(
                Query.query(Criteria.where("logicalKey").is(logicalKey)),
                WorkerTask.class));
    }

    @Override
    public Optional<WorkerTask> leaseNext(LeaseCriteria criteria) {
        Objects.requireNonNull(criteria, "criteria");
        if (criteria.allowedSources() == null || criteria.allowedSources().isEmpty()
                || criteria.capabilities() == null || criteria.maxBytes() <= 0) {
            return Optional.empty();
        }

        Criteria candidateCriteria = new Criteria().andOperator(
                Criteria.where("status").is(WorkerTaskStatus.PENDING),
                new Criteria().orOperator(
                        Criteria.where("notBefore").is(null),
                        Criteria.where("notBefore").lte(criteria.now())),
                Criteria.where("source").in(criteria.allowedSources()),
                Criteria.where("maxBytes").lte(criteria.maxBytes()),
                new Criteria().orOperator(
                        Criteria.where("expectedSizeBytes").is(null),
                        Criteria.where("expectedSizeBytes").lte(criteria.maxBytes())));
        Query candidateQuery = Query.query(candidateCriteria)
                .with(Sort.by(
                        Sort.Order.desc("priority"),
                        Sort.Order.asc("createdAt")))
                .limit(WorkerProtocolConstants.LEASE_CANDIDATE_SCAN_LIMIT);

        List<WorkerTask> candidates = mongoTemplate.find(candidateQuery, WorkerTask.class);
        for (WorkerTask candidate : candidates) {
            if (candidate.getAttemptCount() >= candidate.getMaxAttempts()) {
                continue;
            }
            if (candidate.getRequiredCapabilities() != null
                    && !criteria.capabilities().containsAll(candidate.getRequiredCapabilities())) {
                continue;
            }

            Query claimQuery = Query.query(new Criteria().andOperator(
                    Criteria.where("_id").is(candidate.getId()),
                    Criteria.where("status").is(WorkerTaskStatus.PENDING),
                    Criteria.where("attemptCount").is(candidate.getAttemptCount()),
                    new Criteria().orOperator(
                            Criteria.where("notBefore").is(null),
                            Criteria.where("notBefore").lte(criteria.now()))));
            Update claim = new Update()
                    .set("status", WorkerTaskStatus.LEASED)
                    .set("leaseOwnerSessionId", criteria.sessionId())
                    .set("leaseTokenHash", criteria.leaseTokenHash())
                    .set("leaseExpiresAt", criteria.leaseExpiresAt())
                    .set("updatedAt", criteria.now())
                    .inc("attemptCount", 1)
                    .unset("lastErrorCode")
                    .unset("lastErrorMessage");

            WorkerTask leased = mongoTemplate.findAndModify(
                    claimQuery,
                    claim,
                    FindAndModifyOptions.options().returnNew(true),
                    WorkerTask.class);
            if (leased != null) {
                return Optional.of(leased);
            }
        }

        return Optional.empty();
    }

    @Override
    public Optional<WorkerTask> extendLease(
            String taskId,
            String sessionId,
            String leaseTokenHash,
            Instant now,
            Instant leaseExpiresAt) {
        Query query = activeLeaseQuery(taskId, sessionId, leaseTokenHash, now, WorkerTaskStatus.LEASED);
        Update update = new Update()
                .set("leaseExpiresAt", leaseExpiresAt)
                .set("updatedAt", now);
        return Optional.ofNullable(mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                WorkerTask.class));
    }

    @Override
    public Optional<WorkerTask> requeueLease(
            String taskId,
            String sessionId,
            String leaseTokenHash,
            Instant now,
            Instant notBefore,
            WorkerFailureCode failureCode,
            String failureMessage) {
        Query query = activeLeaseQuery(taskId, sessionId, leaseTokenHash, now, WorkerTaskStatus.LEASED);
        WorkerTask task = mongoTemplate.findOne(query, WorkerTask.class);
        if (task == null) {
            return Optional.empty();
        }
        return transitionToRetryOrFailure(query, task, now, notBefore, failureCode, failureMessage);
    }

    @Override
    public int requeueExpiredLeases(Instant now, Instant retryNotBefore) {
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("status").in(WorkerTaskStatus.LEASED, WorkerTaskStatus.VERIFYING),
                Criteria.where("leaseExpiresAt").lte(now)));
        List<WorkerTask> expired = mongoTemplate.find(query, WorkerTask.class);
        int updated = 0;
        for (WorkerTask task : expired) {
            Query compareAndSet = Query.query(new Criteria().andOperator(
                    Criteria.where("_id").is(task.getId()),
                    Criteria.where("status").is(task.getStatus()),
                    Criteria.where("leaseTokenHash").is(task.getLeaseTokenHash()),
                    Criteria.where("leaseExpiresAt").lte(now)));
            Optional<WorkerTask> transitioned = transitionToRetryOrFailure(
                    compareAndSet,
                    task,
                    now,
                    retryNotBefore,
                    WorkerFailureCode.LEASE_EXPIRED,
                    "Worker lease expired");
            if (transitioned.isPresent()) {
                updated++;
            }
        }
        return updated;
    }

    @Override
    public Optional<WorkerTask> markVerifying(
            String taskId,
            String sessionId,
            String leaseTokenHash,
            Instant now) {
        Query query = activeLeaseQuery(taskId, sessionId, leaseTokenHash, now, WorkerTaskStatus.LEASED);
        Update update = new Update()
                .set("status", WorkerTaskStatus.VERIFYING)
                .set("updatedAt", now);
        return Optional.ofNullable(mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                WorkerTask.class));
    }

    @Override
    public Optional<WorkerTask> markCompleted(
            String taskId,
            String sessionId,
            String leaseTokenHash,
            String artifactId,
            Instant now) {
        Query query = activeLeaseQuery(taskId, sessionId, leaseTokenHash, now, WorkerTaskStatus.VERIFYING);
        Update update = new Update()
                .set("status", WorkerTaskStatus.COMPLETED)
                .set("artifactId", artifactId)
                .set("completedAt", now)
                .set("updatedAt", now)
                .unset("leaseOwnerSessionId")
                .unset("leaseTokenHash")
                .unset("leaseExpiresAt");
        return Optional.ofNullable(mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                WorkerTask.class));
    }

    @Override
    public Optional<WorkerTask> markFailed(
            String taskId,
            String sessionId,
            String leaseTokenHash,
            WorkerFailureCode failureCode,
            String failureMessage,
            Instant now) {
        Query query = activeLeaseQuery(
                taskId,
                sessionId,
                leaseTokenHash,
                now,
                WorkerTaskStatus.LEASED,
                WorkerTaskStatus.VERIFYING);
        Update update = terminalFailureUpdate(failureCode, failureMessage, now);
        return Optional.ofNullable(mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                WorkerTask.class));
    }

    @Override
    public int cancelByParentDownloadJobId(String parentDownloadJobId, Instant now) {
        if (parentDownloadJobId == null) {
            return 0;
        }
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("parentDownloadJobId").is(parentDownloadJobId),
                Criteria.where("status").in(
                        WorkerTaskStatus.PENDING,
                        WorkerTaskStatus.LEASED,
                        WorkerTaskStatus.VERIFYING)));
        Update update = new Update()
                .set("status", WorkerTaskStatus.CANCELLED)
                .set("completedAt", now)
                .set("updatedAt", now)
                .unset("leaseOwnerSessionId")
                .unset("leaseTokenHash")
                .unset("leaseExpiresAt");
        return Math.toIntExact(mongoTemplate.updateMulti(query, update, WorkerTask.class).getModifiedCount());
    }

    @Override
    public WorkerTaskCounts countByParentDownloadJobId(String parentDownloadJobId) {
        return new WorkerTaskCounts(
                count(parentDownloadJobId, WorkerTaskStatus.PENDING),
                count(parentDownloadJobId, WorkerTaskStatus.LEASED),
                count(parentDownloadJobId, WorkerTaskStatus.VERIFYING),
                count(parentDownloadJobId, WorkerTaskStatus.COMPLETED),
                count(parentDownloadJobId, WorkerTaskStatus.FAILED),
                count(parentDownloadJobId, WorkerTaskStatus.CANCELLED));
    }

    private Optional<WorkerTask> transitionToRetryOrFailure(
            Query query,
            WorkerTask task,
            Instant now,
            Instant notBefore,
            WorkerFailureCode failureCode,
            String failureMessage) {
        Update update;
        if (task.getAttemptCount() >= task.getMaxAttempts()) {
            update = terminalFailureUpdate(failureCode, failureMessage, now);
        } else {
            update = new Update()
                    .set("status", WorkerTaskStatus.PENDING)
                    .set("notBefore", notBefore)
                    .set("lastErrorCode", failureCode)
                    .set("lastErrorMessage", failureMessage)
                    .set("updatedAt", now)
                    .unset("completedAt")
                    .unset("leaseOwnerSessionId")
                    .unset("leaseTokenHash")
                    .unset("leaseExpiresAt");
        }

        return Optional.ofNullable(mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                WorkerTask.class));
    }

    private static Query activeLeaseQuery(
            String taskId,
            String sessionId,
            String leaseTokenHash,
            Instant now,
            WorkerTaskStatus... statuses) {
        return Query.query(new Criteria().andOperator(
                Criteria.where("_id").is(taskId),
                Criteria.where("status").in((Object[]) statuses),
                Criteria.where("leaseOwnerSessionId").is(sessionId),
                Criteria.where("leaseTokenHash").is(leaseTokenHash),
                Criteria.where("leaseExpiresAt").gt(now)));
    }

    private static Update terminalFailureUpdate(
            WorkerFailureCode failureCode,
            String failureMessage,
            Instant now) {
        return new Update()
                .set("status", WorkerTaskStatus.FAILED)
                .set("lastErrorCode", failureCode)
                .set("lastErrorMessage", failureMessage)
                .set("completedAt", now)
                .set("updatedAt", now)
                .unset("leaseOwnerSessionId")
                .unset("leaseTokenHash")
                .unset("leaseExpiresAt");
    }

    private long count(String parentDownloadJobId, WorkerTaskStatus status) {
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("parentDownloadJobId").is(parentDownloadJobId),
                Criteria.where("status").is(status)));
        return mongoTemplate.count(query, WorkerTask.class);
    }
}
