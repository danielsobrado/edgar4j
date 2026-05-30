package org.jds.edgar4j.controller;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

import org.jds.edgar4j.dto.request.DownloadRequest;
import org.jds.edgar4j.dto.response.ApiResponse;
import org.jds.edgar4j.dto.response.DownloadJobResponse;
import org.jds.edgar4j.dto.response.DownloadSummaryResponse;
import org.jds.edgar4j.dto.response.UsaSpendingCoverageResponse;
import org.jds.edgar4j.dto.response.UsaSpendingCsvPageResponse;
import org.jds.edgar4j.service.DownloadJobService;
import org.jds.edgar4j.util.PaginationUtils;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/downloads")
@RequiredArgsConstructor
@Validated
public class DownloadController {

    private final DownloadJobService downloadJobService;

    @PostMapping("/tickers")
    public ResponseEntity<ApiResponse<DownloadJobResponse>> downloadTickers(
            @RequestParam(defaultValue = "TICKERS_ALL") String type,
            @RequestBody(required = false) @Valid DownloadRequest request) {
        log.info("POST /api/downloads/tickers?type={}", type);

        DownloadRequest.DownloadType requestedType = resolveDownloadType(type);
        DownloadRequest downloadRequest = request != null ? request : DownloadRequest.builder()
                .type(requestedType)
                .build();
        if (downloadRequest.getType() == null) {
            downloadRequest.setType(requestedType);
        }

        DownloadJobResponse job = downloadJobService.startDownload(downloadRequest);
        return ResponseEntity.ok(ApiResponse.success(job, "Download job started"));
    }

    @PostMapping("/submissions")
    public ResponseEntity<ApiResponse<DownloadJobResponse>> downloadSubmissions(@RequestBody @Valid DownloadRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Download request is required"));
        }
        log.info("POST /api/downloads/submissions: cik={}", request.getCik());

        if (request.getCik() == null || request.getCik().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("CIK is required for submissions download"));
        }

        request.setType(DownloadRequest.DownloadType.SUBMISSIONS);
        DownloadJobResponse job = downloadJobService.startDownload(request);
        return ResponseEntity.ok(ApiResponse.success(job, "Download job started"));
    }

    @PostMapping("/remote-filings")
    public ResponseEntity<ApiResponse<DownloadJobResponse>> downloadRemoteFilings(@RequestBody @Valid DownloadRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Download request is required"));
        }
        log.info("POST /api/downloads/remote-filings: formType={}, dateFrom={}, dateTo={}",
                request.getFormType(), request.getDateFrom(), request.getDateTo());

        if (request.getFormType() == null || request.getFormType().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("formType is required for remote filing sync"));
        }
        if (request.getDateFrom() == null || request.getDateTo() == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("dateFrom and dateTo are required for remote filing sync"));
        }

        request.setType(DownloadRequest.DownloadType.REMOTE_FILINGS_SYNC);
        DownloadJobResponse job = downloadJobService.startDownload(request);
        return ResponseEntity.ok(ApiResponse.success(job, "Download job started"));
    }

    @PostMapping("/usaspending/awards")
    public ResponseEntity<ApiResponse<DownloadJobResponse>> downloadUsaSpendingAwards(@RequestBody @Valid DownloadRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Download request is required"));
        }
        log.info("POST /api/downloads/usaspending/awards: dateFrom={}, dateTo={}",
                request.getDateFrom(), request.getDateTo());

        if (request.getDateFrom() == null || request.getDateTo() == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("dateFrom and dateTo are required for USAspending award downloads"));
        }
        if (request.getDateTo().isBefore(request.getDateFrom())) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("dateTo must be on or after dateFrom"));
        }
        if (ChronoUnit.DAYS.between(request.getDateFrom(), request.getDateTo()) + 1 > 366) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("USAspending award downloads may span at most one year"));
        }

        request.setType(DownloadRequest.DownloadType.USA_SPENDING_AWARDS);
        DownloadJobResponse job = downloadJobService.startDownload(request);
        return ResponseEntity.ok(ApiResponse.success(job, "USAspending award download job started"));
    }

    @PostMapping("/bulk")
    public ResponseEntity<ApiResponse<DownloadJobResponse>> downloadBulk(@RequestBody @Valid DownloadRequest request) {
        if (request == null || request.getType() == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Download type is required for bulk downloads"));
        }
        log.info("POST /api/downloads/bulk: type={}", request.getType());
        DownloadJobResponse job = downloadJobService.startDownload(request);
        return ResponseEntity.ok(ApiResponse.success(job, "Bulk download job started"));
    }

    @GetMapping("/jobs")
    public ResponseEntity<ApiResponse<List<DownloadJobResponse>>> getJobs(
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
        int safeLimit = PaginationUtils.normalizeSize(limit);
        log.info("GET /api/downloads/jobs?limit={}", safeLimit);
        List<DownloadJobResponse> jobs = downloadJobService.getRecentJobs(safeLimit);
        return ResponseEntity.ok(ApiResponse.success(jobs));
    }

    @GetMapping("/jobs/active")
    public ResponseEntity<ApiResponse<List<DownloadJobResponse>>> getActiveJobs() {
        log.info("GET /api/downloads/jobs/active");
        List<DownloadJobResponse> jobs = downloadJobService.getActiveJobs();
        return ResponseEntity.ok(ApiResponse.success(jobs));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<DownloadSummaryResponse>> getSummary() {
        log.info("GET /api/downloads/summary");
        return ResponseEntity.ok(ApiResponse.success(downloadJobService.getSummary()));
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<ApiResponse<DownloadJobResponse>> getJobById(@PathVariable String id) {
        log.info("GET /api/downloads/jobs/{}", id);
        return downloadJobService.getJobById(id)
                .map(job -> ResponseEntity.ok(ApiResponse.success(job)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Download job not found")));
    }

    @GetMapping("/usaspending/coverage")
    public ResponseEntity<ApiResponse<UsaSpendingCoverageResponse>> getUsaSpendingCoverage(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        log.info("GET /api/downloads/usaspending/coverage?from={}&to={}", from, to);
        if (to.isBefore(from)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("to must be on or after from"));
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > 366) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Coverage window may span at most one year"));
        }
        return ResponseEntity.ok(ApiResponse.success(downloadJobService.getUsaSpendingCoverage(from, to)));
    }

    @GetMapping("/jobs/{id}/usaspending-csv")
    public ResponseEntity<ApiResponse<UsaSpendingCsvPageResponse>> getUsaSpendingCsvPage(
            @PathVariable String id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        log.info("GET /api/downloads/jobs/{}/usaspending-csv?page={}&size={}", id, page, size);
        return downloadJobService.getUsaSpendingCsvPage(id, page, size)
                .map(csvPage -> ResponseEntity.ok(ApiResponse.success(csvPage)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Completed USAspending CSV download not found")));
    }

    @DeleteMapping("/jobs/{id}")
    public ResponseEntity<ApiResponse<Void>> cancelJob(@PathVariable String id) {
        log.info("DELETE /api/downloads/jobs/{}", id);
        boolean cancelled = downloadJobService.cancelJob(id);
        if (!cancelled) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Unable to cancel job: not found or already in terminal state"));
        }
        return ResponseEntity.ok(ApiResponse.success(null, "Job cancelled"));
    }

    private DownloadRequest.DownloadType resolveDownloadType(String type) {
        try {
            String normalizedType = type == null || type.isBlank()
                    ? DownloadRequest.DownloadType.TICKERS_ALL.name()
                    : type.trim().toUpperCase(Locale.ROOT);
            return DownloadRequest.DownloadType.valueOf(normalizedType);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported download type: " + type);
        }
    }
}
