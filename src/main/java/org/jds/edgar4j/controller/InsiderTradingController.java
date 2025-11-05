package org.jds.edgar4j.controller;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.jds.edgar4j.model.report.ClusterBuy;
import org.jds.edgar4j.model.report.InsiderBuy;
import org.jds.edgar4j.service.InsiderBuyAggregationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

/**
 * REST controller for insider trading reports
 * Provides endpoints for cluster buys and individual insider buy data
 *
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-11-05
 */
@Slf4j
@RestController
@RequestMapping("/api/insider-trading")
public class InsiderTradingController {

    @Autowired
    private InsiderBuyAggregationService aggregationService;

    /**
     * Get latest cluster buys across all stocks
     *
     * GET /api/insider-trading/cluster-buys/latest?days=30&minInsiders=2&page=0&size=50
     *
     * @param days number of days to look back (default: 30)
     * @param minInsiders minimum insiders per cluster (default: 2)
     * @param page page number (default: 0)
     * @param size page size (default: 50)
     * @return page of cluster buys
     */
    @GetMapping("/cluster-buys/latest")
    public ResponseEntity<Page<ClusterBuy>> getLatestClusterBuys(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "2") int minInsiders,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        log.info("GET /api/insider-trading/cluster-buys/latest - days={}, minInsiders={}, page={}, size={}",
            days, minInsiders, page, size);

        Pageable pageable = PageRequest.of(page, size);
        Page<ClusterBuy> clusterBuys = aggregationService.getLatestClusterBuys(days, minInsiders, pageable);

        return ResponseEntity.ok(clusterBuys);
    }

    /**
     * Get latest individual insider buys across all stocks
     *
     * GET /api/insider-trading/insider-buys/latest?days=30&page=0&size=50
     *
     * @param days number of days to look back (default: 30)
     * @param page page number (default: 0)
     * @param size page size (default: 50)
     * @return page of insider buys
     */
    @GetMapping("/insider-buys/latest")
    public ResponseEntity<Page<InsiderBuy>> getLatestInsiderBuys(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        log.info("GET /api/insider-trading/insider-buys/latest - days={}, page={}, size={}",
            days, page, size);

        Pageable pageable = PageRequest.of(page, size);
        Page<InsiderBuy> insiderBuys = aggregationService.getLatestInsiderBuys(days, pageable);

        return ResponseEntity.ok(insiderBuys);
    }

    /**
     * Get cluster buys for a specific ticker
     *
     * GET /api/insider-trading/cluster-buys/ticker/MSFT?days=90&minInsiders=2
     *
     * @param ticker stock ticker symbol
     * @param days number of days to look back (default: 90)
     * @param minInsiders minimum insiders per cluster (default: 2)
     * @return list of cluster buys for the ticker
     */
    @GetMapping("/cluster-buys/ticker/{ticker}")
    public ResponseEntity<List<ClusterBuy>> getClusterBuysByTicker(
            @PathVariable String ticker,
            @RequestParam(defaultValue = "90") int days,
            @RequestParam(defaultValue = "2") int minInsiders) {

        log.info("GET /api/insider-trading/cluster-buys/ticker/{} - days={}, minInsiders={}",
            ticker, days, minInsiders);

        List<ClusterBuy> clusterBuys = aggregationService.getClusterBuysByTicker(
            ticker.toUpperCase(), days, minInsiders
        );

        return ResponseEntity.ok(clusterBuys);
    }

    /**
     * Get individual insider buys for a specific ticker
     *
     * GET /api/insider-trading/insider-buys/ticker/MSFT?days=90
     *
     * @param ticker stock ticker symbol
     * @param days number of days to look back (default: 90)
     * @return list of insider buys for the ticker
     */
    @GetMapping("/insider-buys/ticker/{ticker}")
    public ResponseEntity<List<InsiderBuy>> getInsiderBuysByTicker(
            @PathVariable String ticker,
            @RequestParam(defaultValue = "90") int days) {

        log.info("GET /api/insider-trading/insider-buys/ticker/{} - days={}", ticker, days);

        List<InsiderBuy> insiderBuys = aggregationService.getInsiderBuysByTicker(
            ticker.toUpperCase(), days
        );

        return ResponseEntity.ok(insiderBuys);
    }

    /**
     * Get insider buys by a specific insider
     *
     * GET /api/insider-trading/insider-buys/insider/0001234567?days=180
     *
     * @param insiderCik insider's CIK
     * @param days number of days to look back (default: 180)
     * @return list of insider buys by this insider
     */
    @GetMapping("/insider-buys/insider/{insiderCik}")
    public ResponseEntity<List<InsiderBuy>> getInsiderBuysByInsider(
            @PathVariable String insiderCik,
            @RequestParam(defaultValue = "180") int days) {

        log.info("GET /api/insider-trading/insider-buys/insider/{} - days={}", insiderCik, days);

        List<InsiderBuy> insiderBuys = aggregationService.getInsiderBuysByInsider(insiderCik, days);

        return ResponseEntity.ok(insiderBuys);
    }

    /**
     * Get cluster buys within a date range
     *
     * GET /api/insider-trading/cluster-buys/date-range?startDate=2025-01-01&endDate=2025-11-05&minInsiders=2&page=0&size=50
     *
     * @param startDate start date (format: yyyy-MM-dd)
     * @param endDate end date (format: yyyy-MM-dd)
     * @param minInsiders minimum insiders per cluster (default: 2)
     * @param page page number (default: 0)
     * @param size page size (default: 50)
     * @return page of cluster buys
     */
    @GetMapping("/cluster-buys/date-range")
    public ResponseEntity<Page<ClusterBuy>> getClusterBuysByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "2") int minInsiders,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        log.info("GET /api/insider-trading/cluster-buys/date-range - startDate={}, endDate={}, minInsiders={}, page={}, size={}",
            startDate, endDate, minInsiders, page, size);

        Pageable pageable = PageRequest.of(page, size);
        Page<ClusterBuy> clusterBuys = aggregationService.getClusterBuysByDateRange(
            startDate, endDate, minInsiders, pageable
        );

        return ResponseEntity.ok(clusterBuys);
    }

    /**
     * Get individual insider buys within a date range
     *
     * GET /api/insider-trading/insider-buys/date-range?startDate=2025-01-01&endDate=2025-11-05&page=0&size=50
     *
     * @param startDate start date (format: yyyy-MM-dd)
     * @param endDate end date (format: yyyy-MM-dd)
     * @param page page number (default: 0)
     * @param size page size (default: 50)
     * @return page of insider buys
     */
    @GetMapping("/insider-buys/date-range")
    public ResponseEntity<Page<InsiderBuy>> getInsiderBuysByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        log.info("GET /api/insider-trading/insider-buys/date-range - startDate={}, endDate={}, page={}, size={}",
            startDate, endDate, page, size);

        Pageable pageable = PageRequest.of(page, size);
        Page<InsiderBuy> insiderBuys = aggregationService.getInsiderBuysByDateRange(
            startDate, endDate, pageable
        );

        return ResponseEntity.ok(insiderBuys);
    }

    /**
     * Get top cluster buys by total transaction value
     *
     * GET /api/insider-trading/cluster-buys/top-by-value?days=30&limit=10
     *
     * @param days number of days to look back (default: 30)
     * @param limit number of results to return (default: 10)
     * @return list of top cluster buys by value
     */
    @GetMapping("/cluster-buys/top-by-value")
    public ResponseEntity<List<ClusterBuy>> getTopClusterBuysByValue(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "10") int limit) {

        log.info("GET /api/insider-trading/cluster-buys/top-by-value - days={}, limit={}", days, limit);

        List<ClusterBuy> topClusters = aggregationService.getTopClusterBuysByValue(days, limit);

        return ResponseEntity.ok(topClusters);
    }

    /**
     * Get top insider buys by transaction value
     *
     * GET /api/insider-trading/insider-buys/top-by-value?days=30&limit=10
     *
     * @param days number of days to look back (default: 30)
     * @param limit number of results to return (default: 10)
     * @return list of top insider buys by value
     */
    @GetMapping("/insider-buys/top-by-value")
    public ResponseEntity<List<InsiderBuy>> getTopInsiderBuysByValue(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "10") int limit) {

        log.info("GET /api/insider-trading/insider-buys/top-by-value - days={}, limit={}", days, limit);

        List<InsiderBuy> topBuys = aggregationService.getTopInsiderBuysByValue(days, limit);

        return ResponseEntity.ok(topBuys);
    }

    /**
     * Get high-significance cluster buys
     *
     * GET /api/insider-trading/cluster-buys/high-significance?days=30&minScore=70&limit=20
     *
     * @param days number of days to look back (default: 30)
     * @param minScore minimum significance score 0-100 (default: 70)
     * @param limit number of results to return (default: 20)
     * @return list of high-significance cluster buys
     */
    @GetMapping("/cluster-buys/high-significance")
    public ResponseEntity<List<ClusterBuy>> getHighSignificanceClusterBuys(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "70") int minScore,
            @RequestParam(defaultValue = "20") int limit) {

        log.info("GET /api/insider-trading/cluster-buys/high-significance - days={}, minScore={}, limit={}",
            days, minScore, limit);

        List<ClusterBuy> highSignificanceClusters = aggregationService.getHighSignificanceClusterBuys(
            days, minScore, limit
        );

        return ResponseEntity.ok(highSignificanceClusters);
    }

    /**
     * Health check endpoint
     *
     * GET /api/insider-trading/health
     *
     * @return health status
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Insider Trading API is running");
    }
}
