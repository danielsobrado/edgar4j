package org.jds.edgar4j.controller;

import java.util.List;

import org.jds.edgar4j.dto.request.DownloadRequest;
import org.jds.edgar4j.dto.response.ApiResponse;
import org.jds.edgar4j.dto.response.DownloadJobResponse;
import org.jds.edgar4j.service.DownloadJobService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/downloads")
@RequiredArgsConstructor
public class DownloadController {

    private final DownloadJobService downloadJobService;

    @PostMapping("/tickers")
    public ResponseEntity<ApiResponse<DownloadJobResponse>> downloadTickers(
            @RequestParam(defaultValue = "TICKERS_ALL") String type,
            @RequestBody(required = false) DownloadRequest request) {
        log.info("POST /api/downloads/tickers?type={}", type);

        DownloadRequest downloadRequest = request != null ? request : DownloadRequest.builder()
                .type(DownloadRequest.DownloadType.valueOf(type))
                .build();

        DownloadJobResponse job = downloadJobService.startDownload(downloadRequest);
        return ResponseEntity.ok(ApiResponse.success(job, "Download job started"));
    }

    @PostMapping("/submissions")
    public ResponseEntity<ApiResponse<DownloadJobResponse>> downloadSubmissions(
            @RequestBody DownloadRequest request) {
        log.info("POST /api/downloads/submissions: cik={}", request.getCik());

        if (request.getCik() == null || request.getCik().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("CIK is required for submissions download"));
        }

        request.setType(DownloadRequest.DownloadType.SUBMISSIONS);
        DownloadJobResponse job = downloadJobService.startDownload(request);
        return ResponseEntity.ok(ApiResponse.success(job, "Download job started"));
    }

    @PostMapping("/bulk")
    public ResponseEntity<ApiResponse<DownloadJobResponse>> downloadBulk(
            @RequestBody DownloadRequest request) {
        log.info("POST /api/downloads/bulk: type={}", request.getType());
        DownloadJobResponse job = downloadJobService.startDownload(request);
        return ResponseEntity.ok(ApiResponse.success(job, "Bulk download job started"));
    }

    @GetMapping("/jobs")
    public ResponseEntity<ApiResponse<List<DownloadJobResponse>>> getJobs(
            @RequestParam(defaultValue = "10") int limit) {
        log.info("GET /api/downloads/jobs?limit={}", limit);
        List<DownloadJobResponse> jobs = downloadJobService.getRecentJobs(limit);
        return ResponseEntity.ok(ApiResponse.success(jobs));
    }

    @GetMapping("/jobs/active")
    public ResponseEntity<ApiResponse<List<DownloadJobResponse>>> getActiveJobs() {
        log.info("GET /api/downloads/jobs/active");
        List<DownloadJobResponse> jobs = downloadJobService.getActiveJobs();
        return ResponseEntity.ok(ApiResponse.success(jobs));
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<ApiResponse<DownloadJobResponse>> getJobById(@PathVariable String id) {
        log.info("GET /api/downloads/jobs/{}", id);
        return downloadJobService.getJobById(id)
                .map(job -> ResponseEntity.ok(ApiResponse.success(job)))
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/jobs/{id}")
    public ResponseEntity<ApiResponse<Void>> cancelJob(@PathVariable String id) {
        log.info("DELETE /api/downloads/jobs/{}", id);
        downloadJobService.cancelJob(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Job cancelled"));
    }
}
