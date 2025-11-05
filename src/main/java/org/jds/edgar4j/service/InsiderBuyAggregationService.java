package org.jds.edgar4j.service;

import java.time.LocalDate;
import java.util.List;

import org.jds.edgar4j.model.report.ClusterBuy;
import org.jds.edgar4j.model.report.InsiderBuy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service for aggregating and analyzing insider buy transactions from Form 4 filings
 * Generates cluster buy reports and individual insider buy reports
 *
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-11-05
 */
public interface InsiderBuyAggregationService {

    /**
     * Get latest cluster buys across all stocks
     * Groups insider buys by ticker and trade date, showing stocks where multiple insiders bought
     *
     * @param days number of days to look back from today
     * @param minInsiders minimum number of insiders required to form a cluster (typically 2+)
     * @param pageable pagination parameters
     * @return page of cluster buys ordered by filing date (most recent first)
     */
    Page<ClusterBuy> getLatestClusterBuys(int days, int minInsiders, Pageable pageable);

    /**
     * Get latest individual insider buys across all stocks
     *
     * @param days number of days to look back from today
     * @param pageable pagination parameters
     * @return page of insider buys ordered by filing date (most recent first)
     */
    Page<InsiderBuy> getLatestInsiderBuys(int days, Pageable pageable);

    /**
     * Get cluster buys for a specific ticker
     *
     * @param ticker stock ticker symbol
     * @param days number of days to look back from today
     * @param minInsiders minimum number of insiders required to form a cluster
     * @return list of cluster buys for the ticker
     */
    List<ClusterBuy> getClusterBuysByTicker(String ticker, int days, int minInsiders);

    /**
     * Get individual insider buys for a specific ticker
     *
     * @param ticker stock ticker symbol
     * @param days number of days to look back from today
     * @return list of insider buys for the ticker
     */
    List<InsiderBuy> getInsiderBuysByTicker(String ticker, int days);

    /**
     * Get insider buys by a specific insider (reporting owner)
     *
     * @param insiderCik insider's CIK
     * @param days number of days to look back from today
     * @return list of insider buys by this insider
     */
    List<InsiderBuy> getInsiderBuysByInsider(String insiderCik, int days);

    /**
     * Get cluster buys within a specific date range
     *
     * @param startDate start date (inclusive)
     * @param endDate end date (inclusive)
     * @param minInsiders minimum number of insiders required to form a cluster
     * @param pageable pagination parameters
     * @return page of cluster buys
     */
    Page<ClusterBuy> getClusterBuysByDateRange(LocalDate startDate, LocalDate endDate, int minInsiders, Pageable pageable);

    /**
     * Get individual insider buys within a specific date range
     *
     * @param startDate start date (inclusive)
     * @param endDate end date (inclusive)
     * @param pageable pagination parameters
     * @return page of insider buys
     */
    Page<InsiderBuy> getInsiderBuysByDateRange(LocalDate startDate, LocalDate endDate, Pageable pageable);

    /**
     * Get top cluster buys by total value
     *
     * @param days number of days to look back
     * @param limit number of top clusters to return
     * @return list of top cluster buys by value
     */
    List<ClusterBuy> getTopClusterBuysByValue(int days, int limit);

    /**
     * Get top insider buys by transaction value
     *
     * @param days number of days to look back
     * @param limit number of top buys to return
     * @return list of top insider buys by value
     */
    List<InsiderBuy> getTopInsiderBuysByValue(int days, int limit);

    /**
     * Get cluster buys with high significance scores
     * Significance based on insider count, transaction value, and insider roles
     *
     * @param days number of days to look back
     * @param minSignificanceScore minimum significance score (0-100)
     * @param limit number of results to return
     * @return list of high-significance cluster buys
     */
    List<ClusterBuy> getHighSignificanceClusterBuys(int days, int minSignificanceScore, int limit);
}
