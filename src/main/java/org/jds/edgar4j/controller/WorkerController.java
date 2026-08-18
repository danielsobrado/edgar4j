package org.jds.edgar4j.controller;

import static org.jds.edgar4j.constants.WorkerHttpConstants.BASE_PATH;
import static org.jds.edgar4j.constants.WorkerHttpConstants.LEASE_TOKEN_HEADER;
import static org.jds.edgar4j.constants.WorkerHttpConstants.SESSION_ID_HEADER;
import static org.jds.edgar4j.constants.WorkerHttpConstants.SESSION_TOKEN_HEADER;
import static org.jds.edgar4j.constants.WorkerHttpConstants.SHA256_HEADER;

import java.security.Principal;
import java.util.function.Supplier;

import org.jds.edgar4j.dto.response.ApiResponse;
import org.jds.edgar4j.dto.worker.WorkerAbandonRequest;
import org.jds.edgar4j.dto.worker.WorkerArtifactResponse;
import org.jds.edgar4j.dto.worker.WorkerFailureRequest;
import org.jds.edgar4j.dto.worker.WorkerHeartbeatRequest;
import org.jds.edgar4j.dto.worker.WorkerHeartbeatResponse;
import org.jds.edgar4j.dto.worker.WorkerLeaseRequest;
import org.jds.edgar4j.dto.worker.WorkerLeaseResponse;
import org.jds.edgar4j.dto.worker.WorkerSessionRequest;
import org.jds.edgar4j.dto.worker.WorkerSessionResponse;
import org.jds.edgar4j.service.WorkerArtifactIngressService;
import org.jds.edgar4j.service.WorkerCoordinatorService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping(BASE_PATH)
@RequiredArgsConstructor
@Validated
public class WorkerController {

    private static final String LOCAL_PRINCIPAL = "local-anonymous";

    private final WorkerCoordinatorService coordinatorService;
    private final WorkerArtifactIngressService artifactIngressService;

    @PostMapping("/session")
    public Mono<ResponseEntity<ApiResponse<WorkerSessionResponse>>> openSession(
            @RequestBody @Valid WorkerSessionRequest request,
            ServerWebExchange exchange) {
        return exchange.getPrincipal()
                .map(Principal::getName)
                .defaultIfEmpty(LOCAL_PRINCIPAL)
                .flatMap(principal -> blocking(() -> coordinatorService.openSession(principal, request)))
                .map(session -> ResponseEntity.ok(ApiResponse.success(session)));
    }

    @PostMapping("/tasks/lease")
    public Mono<ResponseEntity<ApiResponse<WorkerLeaseResponse>>> lease(
            @RequestHeader(SESSION_ID_HEADER) String sessionId,
            @RequestHeader(SESSION_TOKEN_HEADER) String sessionToken,
            @RequestBody @Valid WorkerLeaseRequest request) {
        return blocking(() -> coordinatorService.lease(sessionId, sessionToken, request))
                .map(result -> ResponseEntity.ok(ApiResponse.success(result)));
    }

    @PostMapping("/tasks/{taskId}/heartbeat")
    public Mono<ResponseEntity<ApiResponse<WorkerHeartbeatResponse>>> heartbeat(
            @RequestHeader(SESSION_ID_HEADER) String sessionId,
            @RequestHeader(SESSION_TOKEN_HEADER) String sessionToken,
            @PathVariable String taskId,
            @RequestBody @Valid WorkerHeartbeatRequest request) {
        return blocking(() -> coordinatorService.heartbeat(sessionId, sessionToken, taskId, request))
                .map(result -> ResponseEntity.ok(ApiResponse.success(result)));
    }

    @PutMapping("/tasks/{taskId}/artifact")
    public Mono<ResponseEntity<ApiResponse<WorkerArtifactResponse>>> uploadArtifact(
            @RequestHeader(SESSION_ID_HEADER) String sessionId,
            @RequestHeader(SESSION_TOKEN_HEADER) String sessionToken,
            @RequestHeader(LEASE_TOKEN_HEADER) String leaseToken,
            @RequestHeader(name = SHA256_HEADER, required = false) String claimedSha256,
            @PathVariable String taskId,
            ServerWebExchange exchange) {
        MediaType contentType = exchange.getRequest().getHeaders().getContentType();
        String contentTypeValue = contentType == null ? null : contentType.toString();
        return artifactIngressService.accept(
                        sessionId,
                        sessionToken,
                        taskId,
                        leaseToken,
                        claimedSha256,
                        contentTypeValue,
                        exchange.getRequest().getBody())
                .map(WorkerArtifactResponse::from)
                .map(result -> ResponseEntity.ok(ApiResponse.success(result)));
    }

    @PostMapping("/tasks/{taskId}/failure")
    public Mono<ResponseEntity<ApiResponse<Void>>> reportFailure(
            @RequestHeader(SESSION_ID_HEADER) String sessionId,
            @RequestHeader(SESSION_TOKEN_HEADER) String sessionToken,
            @PathVariable String taskId,
            @RequestBody @Valid WorkerFailureRequest request) {
        return blockingVoid(() -> coordinatorService.reportFailure(sessionId, sessionToken, taskId, request))
                .thenReturn(ResponseEntity.ok(ApiResponse.success(null, "Failure recorded")));
    }

    @PostMapping("/tasks/{taskId}/abandon")
    public Mono<ResponseEntity<ApiResponse<Void>>> abandon(
            @RequestHeader(SESSION_ID_HEADER) String sessionId,
            @RequestHeader(SESSION_TOKEN_HEADER) String sessionToken,
            @PathVariable String taskId,
            @RequestBody @Valid WorkerAbandonRequest request) {
        return blockingVoid(() -> coordinatorService.abandon(
                        sessionId,
                        sessionToken,
                        taskId,
                        request.leaseToken()))
                .thenReturn(ResponseEntity.ok(ApiResponse.success(null, "Task abandoned")));
    }

    @DeleteMapping("/session")
    public Mono<ResponseEntity<ApiResponse<Boolean>>> revokeSession(
            @RequestHeader(SESSION_ID_HEADER) String sessionId,
            @RequestHeader(SESSION_TOKEN_HEADER) String sessionToken) {
        return blocking(() -> coordinatorService.revokeSession(sessionId, sessionToken))
                .map(revoked -> ResponseEntity.ok(ApiResponse.success(revoked)));
    }

    private static <T> Mono<T> blocking(Supplier<T> supplier) {
        return Mono.fromCallable(supplier::get).subscribeOn(Schedulers.boundedElastic());
    }

    private static Mono<Void> blockingVoid(Runnable operation) {
        return Mono.fromRunnable(operation).subscribeOn(Schedulers.boundedElastic()).then();
    }
}
