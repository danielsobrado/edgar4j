package org.jds.edgar4j.controller;

import java.time.LocalDate;
import java.util.Map;

import org.jds.edgar4j.dto.request.PoliticalTradeScreenRequest;
import org.jds.edgar4j.dto.request.PoliticalTradeSyncRequest;
import org.jds.edgar4j.dto.response.ApiResponse;
import org.jds.edgar4j.dto.response.PaginatedResponse;
import org.jds.edgar4j.dto.response.PoliticalTradeResponse;
import org.jds.edgar4j.dto.response.PoliticalTradeSyncResponse;
import org.jds.edgar4j.service.PoliticalTradeService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@RestController
@RequestMapping("/api/political-trades")
@RequiredArgsConstructor
@Validated
@Tag(name = "Political Trades", description = "Congressional trade screeners backed by cached public source data")
public class PoliticalTradeController {

    private final PoliticalTradeService politicalTradeService;

    @Operation(summary = "Screen political trades", description = "Returns cached political trade rows.")
    @GetMapping("/screen")
    public ResponseEntity<ApiResponse<PaginatedResponse<PoliticalTradeResponse>>> screen(
            @RequestParam(required = false) String politician,
            @RequestParam(required = false) String ticker,
            @RequestParam(required = false) String issuer,
            @RequestParam(required = false) String party,
            @RequestParam(required = false) String chamber,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String assetType,
            @RequestParam(required = false) String transactionType,
            @RequestParam(required = false) String owner,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradedDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradedDateTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate disclosureDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate disclosureDateTo,
            @RequestParam(required = false) Double minAmount,
            @RequestParam(required = false) Double maxAmount,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @Parameter(description = "Page number (0-based)")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        PoliticalTradeScreenRequest request = buildScreenRequest(
                politician,
                ticker,
                issuer,
                party,
                chamber,
                state,
                assetType,
                transactionType,
                owner,
                tradedDateFrom,
                tradedDateTo,
                disclosureDateFrom,
                disclosureDateTo,
                minAmount,
                maxAmount,
                sortBy,
                sortDir,
                page,
                size);
        log.info("GET /api/political-trades/screen request={}", request);
        return ResponseEntity.ok(ApiResponse.success(politicalTradeService.screen(request)));
    }

    @Operation(summary = "Export political trades", description = "Exports the active political trade result set as CSV or JSON.")
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String politician,
            @RequestParam(required = false) String ticker,
            @RequestParam(required = false) String issuer,
            @RequestParam(required = false) String party,
            @RequestParam(required = false) String chamber,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String assetType,
            @RequestParam(required = false) String transactionType,
            @RequestParam(required = false) String owner,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradedDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradedDateTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate disclosureDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate disclosureDateTo,
            @RequestParam(required = false) Double minAmount,
            @RequestParam(required = false) Double maxAmount,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(defaultValue = "CSV") String format) {
        PoliticalTradeScreenRequest request = buildScreenRequest(
                politician,
                ticker,
                issuer,
                party,
                chamber,
                state,
                assetType,
                transactionType,
                owner,
                tradedDateFrom,
                tradedDateTo,
                disclosureDateFrom,
                disclosureDateTo,
                minAmount,
                maxAmount,
                sortBy,
                sortDir,
                0,
                100);
        String normalizedFormat = "JSON".equalsIgnoreCase(format) ? "JSON" : "CSV";
        byte[] body = politicalTradeService.export(request, normalizedFormat);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType("JSON".equals(normalizedFormat) ? MediaType.APPLICATION_JSON : MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment", "political-trades." + normalizedFormat.toLowerCase());
        headers.setContentLength(body.length);

        return ResponseEntity.ok().headers(headers).body(body);
    }

    @Operation(summary = "Sync political trades", description = "Fetches public Capitol Trades rows into the local cache.")
    @PostMapping(value = "/sync", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<ApiResponse<PoliticalTradeSyncResponse>>> sync(
            @RequestParam(required = false) String assetType,
            @RequestParam(required = false) @Min(1) @Max(250) Integer maxPages,
            @RequestParam(required = false) Boolean force,
            @RequestBody(required = false) Mono<Map<String, Object>> requestBody) {
        Mono<Map<String, Object>> body = requestBody == null
                ? Mono.just(Map.of())
                : requestBody.defaultIfEmpty(Map.of());
        return body
                .map(values -> buildSyncRequest(values, assetType, maxPages, force))
                .flatMap(this::syncAsync);
    }

    @Operation(summary = "Sync political trades", description = "Fetches public Capitol Trades rows into the local cache.")
    @PostMapping(value = "/sync", consumes = "!application/json")
    public Mono<ResponseEntity<ApiResponse<PoliticalTradeSyncResponse>>> sync(
            @RequestParam(required = false) String assetType,
            @RequestParam(required = false) @Min(1) @Max(250) Integer maxPages,
            @RequestParam(required = false) Boolean force) {
        PoliticalTradeSyncRequest request = buildSyncRequest(Map.of(), assetType, maxPages, force);
        return syncAsync(request);
    }

    private PoliticalTradeScreenRequest buildScreenRequest(
            String politician,
            String ticker,
            String issuer,
            String party,
            String chamber,
            String state,
            String assetType,
            String transactionType,
            String owner,
            LocalDate tradedDateFrom,
            LocalDate tradedDateTo,
            LocalDate disclosureDateFrom,
            LocalDate disclosureDateTo,
            Double minAmount,
            Double maxAmount,
            String sortBy,
            String sortDir,
            int page,
            int size) {
        return PoliticalTradeScreenRequest.builder()
                .politician(politician)
                .ticker(ticker)
                .issuer(issuer)
                .party(party)
                .chamber(chamber)
                .state(state)
                .assetType(assetType)
                .transactionType(transactionType)
                .owner(owner)
                .tradedDateFrom(tradedDateFrom)
                .tradedDateTo(tradedDateTo)
                .disclosureDateFrom(disclosureDateFrom)
                .disclosureDateTo(disclosureDateTo)
                .minAmount(minAmount)
                .maxAmount(maxAmount)
                .sortBy(sortBy)
                .sortDir(sortDir)
                .page(page)
                .size(size)
                .build();
    }

    private PoliticalTradeSyncRequest buildSyncRequest(
            Map<String, Object> body,
            String assetType,
            Integer maxPages,
            Boolean force) {
        return PoliticalTradeSyncRequest.builder()
                .assetType(StringUtils.hasText(assetType) ? assetType : stringValue(body.get("assetType")))
                .maxPages(maxPages != null ? maxPages : integerValue(body.get("maxPages")))
                .force(force != null ? force : booleanValue(body.get("force")))
                .build();
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = stringValue(value);
        if (text == null) {
            return null;
        }
        return Integer.valueOf(text);
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = stringValue(value);
        return text != null && Boolean.parseBoolean(text);
    }

    private ResponseEntity<ApiResponse<PoliticalTradeSyncResponse>> syncResponse(PoliticalTradeSyncResponse response) {
        return ResponseEntity.ok(ApiResponse.success(response, "Political trade sync completed"));
    }

    private Mono<ResponseEntity<ApiResponse<PoliticalTradeSyncResponse>>> syncAsync(PoliticalTradeSyncRequest request) {
        return Mono.fromCallable(() -> politicalTradeService.sync(request))
                .subscribeOn(Schedulers.boundedElastic())
                .map(this::syncResponse);
    }
}
