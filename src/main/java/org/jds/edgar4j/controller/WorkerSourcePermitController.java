package org.jds.edgar4j.controller;

import static org.jds.edgar4j.constants.WorkerHttpConstants.BASE_PATH;
import static org.jds.edgar4j.constants.WorkerHttpConstants.SESSION_ID_HEADER;
import static org.jds.edgar4j.constants.WorkerHttpConstants.SESSION_TOKEN_HEADER;

import org.jds.edgar4j.dto.response.ApiResponse;
import org.jds.edgar4j.dto.worker.WorkerSourcePermitRequest;
import org.jds.edgar4j.service.WorkerSourcePermitService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping(BASE_PATH)
@RequiredArgsConstructor
public class WorkerSourcePermitController {

    private final WorkerSourcePermitService sourcePermitService;

    @PostMapping("/tasks/{taskId}/source-permit")
    public Mono<ResponseEntity<ApiResponse<Void>>> reserveSource(
            @RequestHeader(SESSION_ID_HEADER) String sessionId,
            @RequestHeader(SESSION_TOKEN_HEADER) String sessionToken,
            @PathVariable String taskId,
            @RequestBody @Valid WorkerSourcePermitRequest request) {
        return Mono.fromRunnable(() -> sourcePermitService.reserve(
                        sessionId,
                        sessionToken,
                        taskId,
                        request.leaseToken()))
                .subscribeOn(Schedulers.boundedElastic())
                .thenReturn(ResponseEntity.ok(ApiResponse.success(null, "Source request permitted")));
    }
}
