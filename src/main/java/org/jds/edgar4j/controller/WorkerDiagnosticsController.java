package org.jds.edgar4j.controller;

import static org.jds.edgar4j.constants.WorkerHttpConstants.BASE_PATH;

import org.jds.edgar4j.dto.response.ApiResponse;
import org.jds.edgar4j.dto.worker.WorkerDiagnosticsResponse;
import org.jds.edgar4j.service.WorkerDiagnosticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping(BASE_PATH)
@RequiredArgsConstructor
public class WorkerDiagnosticsController {

    private final WorkerDiagnosticsService diagnosticsService;

    @GetMapping("/status")
    public Mono<ResponseEntity<ApiResponse<WorkerDiagnosticsResponse>>> getStatus() {
        return Mono.fromCallable(diagnosticsService::getDiagnostics)
                .subscribeOn(Schedulers.boundedElastic())
                .map(status -> ResponseEntity.ok(ApiResponse.success(status)));
    }
}
