package org.jds.edgar4j.controller;

import java.time.LocalDate;
import java.util.Set;

import org.jds.edgar4j.dto.request.InsiderActivityScreenRequest;
import org.jds.edgar4j.dto.response.ApiResponse;
import org.jds.edgar4j.dto.response.InsiderActivityResponse;
import org.jds.edgar4j.dto.response.PaginatedResponse;
import org.jds.edgar4j.service.InsiderActivityService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/insider-activity")
@RequiredArgsConstructor
@Validated
@Tag(name = "Insider Activity", description = "Insider transaction screeners for Form 4 activity")
public class InsiderActivityController {

    private final InsiderActivityService insiderActivityService;

    @Operation(summary = "Screen insider activity", description = "Returns transaction or aggregate insider activity rows.")
    @GetMapping("/screen")
    public ResponseEntity<ApiResponse<PaginatedResponse<InsiderActivityResponse>>> screen(
            @RequestParam(required = false) String preset,
            @RequestParam(required = false) String view,
            @RequestParam(required = false) String side,
            @RequestParam(required = false) Set<String> transactionCodes,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double minShares,
            @RequestParam(required = false) Double minTotalAmount,
            @RequestParam(required = false) Integer minInsiderCount,
            @RequestParam(required = false) String insiderTitle,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @Parameter(description = "Page number (0-based)")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        InsiderActivityScreenRequest request = buildRequest(
                preset,
                view,
                side,
                transactionCodes,
                dateFrom,
                dateTo,
                symbol,
                minPrice,
                minShares,
                minTotalAmount,
                minInsiderCount,
                insiderTitle,
                sortBy,
                sortDir,
                page,
                size);
        log.info("GET /api/insider-activity/screen request={}", request);
        return ResponseEntity.ok(ApiResponse.success(insiderActivityService.screen(request)));
    }

    @Operation(summary = "Export insider activity", description = "Exports the active insider screener result set as CSV or JSON.")
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String preset,
            @RequestParam(required = false) String view,
            @RequestParam(required = false) String side,
            @RequestParam(required = false) Set<String> transactionCodes,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double minShares,
            @RequestParam(required = false) Double minTotalAmount,
            @RequestParam(required = false) Integer minInsiderCount,
            @RequestParam(required = false) String insiderTitle,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(defaultValue = "CSV") String format) {
        InsiderActivityScreenRequest request = buildRequest(
                preset,
                view,
                side,
                transactionCodes,
                dateFrom,
                dateTo,
                symbol,
                minPrice,
                minShares,
                minTotalAmount,
                minInsiderCount,
                insiderTitle,
                sortBy,
                sortDir,
                0,
                100);
        String normalizedFormat = "JSON".equalsIgnoreCase(format) ? "JSON" : "CSV";
        byte[] body = insiderActivityService.export(request, normalizedFormat);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType("JSON".equals(normalizedFormat) ? MediaType.APPLICATION_JSON : MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment", "insider-activity." + normalizedFormat.toLowerCase());
        headers.setContentLength(body.length);

        return ResponseEntity.ok().headers(headers).body(body);
    }

    private InsiderActivityScreenRequest buildRequest(
            String preset,
            String view,
            String side,
            Set<String> transactionCodes,
            LocalDate dateFrom,
            LocalDate dateTo,
            String symbol,
            Double minPrice,
            Double minShares,
            Double minTotalAmount,
            Integer minInsiderCount,
            String insiderTitle,
            String sortBy,
            String sortDir,
            int page,
            int size) {
        return InsiderActivityScreenRequest.builder()
                .preset(preset)
                .view(view)
                .side(side)
                .transactionCodes(transactionCodes)
                .dateFrom(dateFrom)
                .dateTo(dateTo)
                .symbol(symbol)
                .minPrice(minPrice)
                .minShares(minShares)
                .minTotalAmount(minTotalAmount)
                .minInsiderCount(minInsiderCount)
                .insiderTitle(insiderTitle)
                .sortBy(sortBy)
                .sortDir(sortDir)
                .page(page)
                .size(size)
                .build();
    }
}
