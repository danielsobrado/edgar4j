package org.jds.edgar4j.service.impl;

import static org.jds.edgar4j.constants.WorkerStorageConstants.ARTIFACT_ROOT_DIRECTORY;
import static org.jds.edgar4j.constants.WorkerStorageConstants.INGRESS_DIRECTORY;
import static org.jds.edgar4j.constants.WorkerStorageConstants.STAGING_SUFFIX;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.jds.edgar4j.dto.worker.WorkerHeartbeatRequest;
import org.jds.edgar4j.exception.WorkerHttpException;
import org.jds.edgar4j.port.ArtifactStorePort.VerifiedArtifact;
import org.jds.edgar4j.properties.DistributedWorkerProperties;
import org.jds.edgar4j.service.WorkerArtifactIngressService;
import org.jds.edgar4j.service.WorkerCoordinatorService;
import org.jds.edgar4j.storage.file.FileStorageProperties;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class WorkerArtifactIngressServiceImpl implements WorkerArtifactIngressService {

    private static final String PAYLOAD_TOO_LARGE = "WORKER_ARTIFACT_TOO_LARGE";
    private static final String INGRESS_FAILED = "WORKER_ARTIFACT_INGRESS_FAILED";

    private final WorkerCoordinatorService coordinatorService;
    private final DistributedWorkerProperties properties;
    private final Path ingressDirectory;

    public WorkerArtifactIngressServiceImpl(
            WorkerCoordinatorService coordinatorService,
            DistributedWorkerProperties properties,
            FileStorageProperties storageProperties) {
        this.coordinatorService = coordinatorService;
        this.properties = properties;
        this.ingressDirectory = storageProperties.resolveBaseDirectory()
                .resolve(ARTIFACT_ROOT_DIRECTORY)
                .resolve(INGRESS_DIRECTORY)
                .normalize();
    }

    @Override
    public Mono<VerifiedArtifact> accept(
            String sessionId,
            String sessionToken,
            String taskId,
            String leaseToken,
            String claimedSha256,
            String contentType,
            Flux<DataBuffer> body) {
        long maxBytes = properties.getArtifact().getMaxMobileBytes().toBytes();

        Mono<Path> ingressResource = Mono.fromCallable(() -> {
            coordinatorService.heartbeat(
                    sessionId,
                    sessionToken,
                    taskId,
                    new WorkerHeartbeatRequest(leaseToken, null));
            Files.createDirectories(ingressDirectory);
            return ingressDirectory.resolve(UUID.randomUUID() + STAGING_SUFFIX).normalize();
        }).subscribeOn(Schedulers.boundedElastic());

        return Mono.usingWhen(
                ingressResource,
                path -> writeBounded(body, path, maxBytes)
                        .then(Mono.fromCallable(() -> verify(
                                sessionId,
                                sessionToken,
                                taskId,
                                leaseToken,
                                claimedSha256,
                                contentType,
                                path))
                                .subscribeOn(Schedulers.boundedElastic())),
                this::deleteIngress,
                (path, failure) -> deleteIngress(path),
                this::deleteIngress);
    }

    private Mono<Void> writeBounded(Flux<DataBuffer> body, Path path, long maxBytes) {
        AtomicLong totalBytes = new AtomicLong();
        Flux<DataBuffer> bounded = body.handle((buffer, sink) -> {
            long total = totalBytes.addAndGet(buffer.readableByteCount());
            if (total > maxBytes) {
                DataBufferUtils.release(buffer);
                sink.error(new WorkerHttpException(
                        "Worker artifact exceeds the configured upload limit",
                        PAYLOAD_TOO_LARGE,
                        HttpStatus.PAYLOAD_TOO_LARGE));
                return;
            }
            sink.next(buffer);
        });

        return DataBufferUtils.write(
                        bounded,
                        path,
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE)
                .doOnDiscard(DataBuffer.class, DataBufferUtils::release)
                .onErrorMap(IOException.class, failure -> new WorkerHttpException(
                        "Failed to write worker artifact ingress",
                        INGRESS_FAILED,
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        failure));
    }

    private VerifiedArtifact verify(
            String sessionId,
            String sessionToken,
            String taskId,
            String leaseToken,
            String claimedSha256,
            String contentType,
            Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            return coordinatorService.acceptArtifact(
                    sessionId,
                    sessionToken,
                    taskId,
                    leaseToken,
                    claimedSha256,
                    contentType,
                    input);
        }
    }

    private Mono<Void> deleteIngress(Path path) {
        return Mono.fromRunnable(() -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                        // Maintenance cleanup handles any ingress file that cannot be deleted immediately.
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }
}
