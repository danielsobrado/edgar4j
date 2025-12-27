package org.jds.edgar4j.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jds.edgar4j.xbrl.XbrlService;
import org.jds.edgar4j.xbrl.model.XbrlFact;
import org.jds.edgar4j.xbrl.model.XbrlInstance;
import org.jds.edgar4j.xbrl.parser.XbrlPackageHandler;
import org.jds.edgar4j.xbrl.validation.CalculationValidator;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * REST controller for XBRL parsing and analysis.
 */
@Slf4j
@RestController
@RequestMapping("/api/xbrl")
@RequiredArgsConstructor
public class XbrlController {

    private final XbrlService xbrlService;

    /**
     * Parse XBRL from uploaded file.
     */
    @PostMapping(value = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> parseFile(
            @RequestParam("file") MultipartFile file) throws IOException {

        log.info("Parsing uploaded XBRL file: {}", file.getOriginalFilename());

        XbrlInstance instance = xbrlService.parse(
                file.getBytes(),
                file.getOriginalFilename(),
                file.getContentType()
        );

        return ResponseEntity.ok(xbrlService.getSummary(instance));
    }

    /**
     * Parse XBRL from URL.
     */
    @GetMapping("/parse")
    public Mono<ResponseEntity<Map<String, Object>>> parseUrl(@RequestParam String url) {
        log.info("Parsing XBRL from URL: {}", url);

        return xbrlService.parseFromUrl(url)
                .map(instance -> ResponseEntity.ok(xbrlService.getSummary(instance)))
                .onErrorResume(e -> {
                    log.error("Failed to parse URL: {}", url, e);
                    return Mono.just(ResponseEntity.badRequest()
                            .body(Map.of("error", e.getMessage())));
                });
    }

    /**
     * Parse XBRL package (ZIP) from URL.
     */
    @GetMapping("/parse-package")
    public Mono<ResponseEntity<Map<String, Object>>> parsePackage(@RequestParam String url) {
        log.info("Parsing XBRL package from URL: {}", url);

        return xbrlService.parsePackageFromUrl(url)
                .map(result -> {
                    Map<String, Object> response = Map.of(
                            "packageUri", result.getPackageUri(),
                            "totalFiles", result.getTotalFiles(),
                            "instanceFiles", result.getInstanceFiles(),
                            "totalFacts", result.getTotalFacts(),
                            "instances", result.getInstances().size(),
                            "errors", result.getErrors()
                    );
                    return ResponseEntity.ok(response);
                })
                .onErrorResume(e -> {
                    log.error("Failed to parse package: {}", url, e);
                    return Mono.just(ResponseEntity.badRequest()
                            .body(Map.of("error", e.getMessage())));
                });
    }

    /**
     * Get key financial metrics from uploaded XBRL.
     */
    @PostMapping(value = "/financials", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, BigDecimal>> getFinancials(
            @RequestParam("file") MultipartFile file) throws IOException {

        XbrlInstance instance = xbrlService.parse(
                file.getBytes(),
                file.getOriginalFilename(),
                file.getContentType()
        );

        return ResponseEntity.ok(xbrlService.getKeyFinancials(instance));
    }

    /**
     * Get key financial metrics from XBRL URL.
     */
    @GetMapping("/financials")
    public Mono<ResponseEntity<Map<String, BigDecimal>>> getFinancialsFromUrl(
            @RequestParam String url) {

        return xbrlService.parseFromUrl(url)
                .map(instance -> ResponseEntity.ok(xbrlService.getKeyFinancials(instance)))
                .onErrorResume(e -> Mono.just(ResponseEntity.badRequest().build()));
    }

    /**
     * Export all facts from uploaded XBRL.
     */
    @PostMapping(value = "/facts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<Map<String, Object>>> exportFacts(
            @RequestParam("file") MultipartFile file) throws IOException {

        XbrlInstance instance = xbrlService.parse(
                file.getBytes(),
                file.getOriginalFilename(),
                file.getContentType()
        );

        return ResponseEntity.ok(xbrlService.exportFacts(instance));
    }

    /**
     * Search facts by concept name.
     */
    @PostMapping(value = "/search", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<XbrlFact>> searchFacts(
            @RequestParam("file") MultipartFile file,
            @RequestParam String query) throws IOException {

        XbrlInstance instance = xbrlService.parse(
                file.getBytes(),
                file.getOriginalFilename(),
                file.getContentType()
        );

        return ResponseEntity.ok(xbrlService.searchFacts(instance, query));
    }

    /**
     * Validate calculations in uploaded XBRL.
     */
    @PostMapping(value = "/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CalculationValidator.ValidationResult> validateCalculations(
            @RequestParam("file") MultipartFile file) throws IOException {

        XbrlInstance instance = xbrlService.parse(
                file.getBytes(),
                file.getOriginalFilename(),
                file.getContentType()
        );

        return ResponseEntity.ok(xbrlService.validateCalculations(instance));
    }

    /**
     * Validate calculations from XBRL URL.
     */
    @GetMapping("/validate")
    public Mono<ResponseEntity<CalculationValidator.ValidationResult>> validateFromUrl(
            @RequestParam String url) {

        return xbrlService.parseFromUrl(url)
                .map(instance -> ResponseEntity.ok(xbrlService.validateCalculations(instance)))
                .onErrorResume(e -> Mono.just(ResponseEntity.badRequest().build()));
    }

    /**
     * Get taxonomy cache statistics.
     */
    @GetMapping("/cache/stats")
    public ResponseEntity<Map<String, Long>> getCacheStats() {
        return ResponseEntity.ok(xbrlService.getCacheStats());
    }

    /**
     * Clear taxonomy caches.
     */
    @PostMapping("/cache/clear")
    public ResponseEntity<Void> clearCache() {
        xbrlService.clearCaches();
        return ResponseEntity.ok().build();
    }

    // ========================================================================
    // NEW ENDPOINTS for frontend integration
    // ========================================================================

    /**
     * Get comprehensive XBRL analysis from URL.
     */
    @GetMapping("/analysis")
    public Mono<ResponseEntity<Map<String, Object>>> getAnalysisFromUrl(@RequestParam String url) {
        log.info("Getting comprehensive analysis from URL: {}", url);

        return xbrlService.parseFromUrl(url)
                .map(instance -> ResponseEntity.ok(xbrlService.getComprehensiveAnalysis(instance)))
                .onErrorResume(e -> {
                    log.error("Failed to analyze: {}", url, e);
                    return Mono.just(ResponseEntity.badRequest()
                            .body(Map.of("error", e.getMessage())));
                });
    }

    /**
     * Get reconstructed financial statements from URL.
     */
    @GetMapping("/statements")
    public Mono<ResponseEntity<Object>> getStatementsFromUrl(@RequestParam String url) {
        log.info("Getting financial statements from URL: {}", url);

        return xbrlService.parseFromUrl(url)
                .map(instance -> ResponseEntity.ok((Object) xbrlService.reconstructStatements(instance)))
                .onErrorResume(e -> {
                    log.error("Failed to get statements: {}", url, e);
                    return Mono.just(ResponseEntity.badRequest()
                            .body(Map.of("error", e.getMessage())));
                });
    }

    /**
     * Get SEC filing metadata from URL.
     */
    @GetMapping("/sec-metadata")
    public Mono<ResponseEntity<Object>> getSecMetadataFromUrl(@RequestParam String url) {
        log.info("Getting SEC metadata from URL: {}", url);

        return xbrlService.parseFromUrl(url)
                .map(instance -> ResponseEntity.ok((Object) xbrlService.extractSecMetadata(instance)))
                .onErrorResume(e -> {
                    log.error("Failed to get SEC metadata: {}", url, e);
                    return Mono.just(ResponseEntity.badRequest()
                            .body(Map.of("error", e.getMessage())));
                });
    }

    /**
     * Get all facts from XBRL URL.
     */
    @GetMapping("/facts")
    public Mono<ResponseEntity<List<Map<String, Object>>>> getFactsFromUrl(@RequestParam String url) {
        log.info("Exporting facts from URL: {}", url);

        return xbrlService.parseFromUrl(url)
                .map(instance -> ResponseEntity.ok(xbrlService.exportFacts(instance)))
                .onErrorResume(e -> {
                    log.error("Failed to export facts: {}", url, e);
                    return Mono.just(ResponseEntity.badRequest().build());
                });
    }

    /**
     * Search facts in XBRL from URL.
     */
    @GetMapping("/facts/search")
    public Mono<ResponseEntity<List<XbrlFact>>> searchFactsFromUrl(
            @RequestParam String url,
            @RequestParam String query) {
        log.info("Searching facts from URL: {} with query: {}", url, query);

        return xbrlService.parseFromUrl(url)
                .map(instance -> ResponseEntity.ok(xbrlService.searchFacts(instance, query)))
                .onErrorResume(e -> {
                    log.error("Failed to search facts: {}", url, e);
                    return Mono.just(ResponseEntity.badRequest().build());
                });
    }
}
