package org.jds.edgar4j.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jds.edgar4j.model.Form4;
import org.jds.edgar4j.model.NonDerivativeTransaction;
import org.jds.edgar4j.model.ReportingOwner;
import org.jds.edgar4j.model.report.ClusterBuy;
import org.jds.edgar4j.model.report.InsiderBuy;
import org.jds.edgar4j.repository.Form4Repository;
import org.jds.edgar4j.service.InsiderBuyAggregationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of insider buy aggregation service
 * Analyzes Form 4 filings to identify insider buying patterns and clusters
 *
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-11-05
 */
@Slf4j
@Service
public class InsiderBuyAggregationServiceImpl implements InsiderBuyAggregationService {

    @Autowired
    private Form4Repository form4Repository;

    @Override
    public Page<ClusterBuy> getLatestClusterBuys(int days, int minInsiders, Pageable pageable) {
        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = endDate.minusDays(days);

        log.info("Fetching cluster buys from {} to {} with min {} insiders",
            startDate, endDate, minInsiders);

        // Get all Form 4s in the date range
        List<Form4> form4s = form4Repository.findByFilingDateBetween(
            startDate, endDate, PageRequest.of(0, 10000)
        ).getContent();

        // Convert to InsiderBuys (only purchases)
        List<InsiderBuy> insiderBuys = convertToInsiderBuys(form4s, true);

        // Group into clusters
        List<ClusterBuy> clusterBuys = groupIntoClusterBuys(insiderBuys, minInsiders);

        // Sort by filing date descending
        clusterBuys.sort(Comparator.comparing(ClusterBuy::getFilingDate,
            Comparator.nullsLast(Comparator.reverseOrder())));

        // Apply pagination
        return paginateList(clusterBuys, pageable);
    }

    @Override
    public Page<InsiderBuy> getLatestInsiderBuys(int days, Pageable pageable) {
        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = endDate.minusDays(days);

        log.info("Fetching insider buys from {} to {}", startDate, endDate);

        // Get all Form 4s in the date range
        List<Form4> form4s = form4Repository.findByFilingDateBetween(
            startDate, endDate, PageRequest.of(0, 10000)
        ).getContent();

        // Convert to InsiderBuys (only purchases)
        List<InsiderBuy> insiderBuys = convertToInsiderBuys(form4s, true);

        // Sort by filing date descending
        insiderBuys.sort(Comparator.comparing(InsiderBuy::getFilingDate,
            Comparator.nullsLast(Comparator.reverseOrder())));

        // Apply pagination
        return paginateList(insiderBuys, pageable);
    }

    @Override
    public List<ClusterBuy> getClusterBuysByTicker(String ticker, int days, int minInsiders) {
        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = endDate.minusDays(days);

        log.info("Fetching cluster buys for {} from {} to {}", ticker, startDate, endDate);

        // Get Form 4s for this ticker
        List<Form4> form4s = form4Repository.findByTradingSymbolAndFilingDateBetween(
            ticker, startDate, endDate, PageRequest.of(0, 1000)
        ).getContent();

        // Convert to InsiderBuys
        List<InsiderBuy> insiderBuys = convertToInsiderBuys(form4s, true);

        // Group into clusters
        List<ClusterBuy> clusterBuys = groupIntoClusterBuys(insiderBuys, minInsiders);

        // Sort by filing date descending
        clusterBuys.sort(Comparator.comparing(ClusterBuy::getFilingDate,
            Comparator.nullsLast(Comparator.reverseOrder())));

        return clusterBuys;
    }

    @Override
    public List<InsiderBuy> getInsiderBuysByTicker(String ticker, int days) {
        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = endDate.minusDays(days);

        // Get Form 4s for this ticker
        List<Form4> form4s = form4Repository.findByTradingSymbolAndFilingDateBetween(
            ticker, startDate, endDate, PageRequest.of(0, 1000)
        ).getContent();

        // Convert to InsiderBuys
        List<InsiderBuy> insiderBuys = convertToInsiderBuys(form4s, true);

        // Sort by filing date descending
        insiderBuys.sort(Comparator.comparing(InsiderBuy::getFilingDate,
            Comparator.nullsLast(Comparator.reverseOrder())));

        return insiderBuys;
    }

    @Override
    public List<InsiderBuy> getInsiderBuysByInsider(String insiderCik, int days) {
        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = endDate.minusDays(days);

        // Get all recent Form 4s
        List<Form4> form4s = form4Repository.findByFilingDateBetween(
            startDate, endDate, PageRequest.of(0, 10000)
        ).getContent();

        // Convert to InsiderBuys and filter by insider CIK
        List<InsiderBuy> insiderBuys = convertToInsiderBuys(form4s, true).stream()
            .filter(buy -> insiderCik.equals(buy.getInsiderCik()))
            .sorted(Comparator.comparing(InsiderBuy::getFilingDate,
                Comparator.nullsLast(Comparator.reverseOrder())))
            .collect(Collectors.toList());

        return insiderBuys;
    }

    @Override
    public Page<ClusterBuy> getClusterBuysByDateRange(LocalDate startDate, LocalDate endDate,
                                                       int minInsiders, Pageable pageable) {
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        List<Form4> form4s = form4Repository.findByPeriodOfReportBetween(
            startDate, endDate, PageRequest.of(0, 10000)
        ).getContent();

        List<InsiderBuy> insiderBuys = convertToInsiderBuys(form4s, true);
        List<ClusterBuy> clusterBuys = groupIntoClusterBuys(insiderBuys, minInsiders);

        clusterBuys.sort(Comparator.comparing(ClusterBuy::getFilingDate,
            Comparator.nullsLast(Comparator.reverseOrder())));

        return paginateList(clusterBuys, pageable);
    }

    @Override
    public Page<InsiderBuy> getInsiderBuysByDateRange(LocalDate startDate, LocalDate endDate,
                                                       Pageable pageable) {
        List<Form4> form4s = form4Repository.findByPeriodOfReportBetween(
            startDate, endDate, PageRequest.of(0, 10000)
        ).getContent();

        List<InsiderBuy> insiderBuys = convertToInsiderBuys(form4s, true);

        insiderBuys.sort(Comparator.comparing(InsiderBuy::getFilingDate,
            Comparator.nullsLast(Comparator.reverseOrder())));

        return paginateList(insiderBuys, pageable);
    }

    @Override
    public List<ClusterBuy> getTopClusterBuysByValue(int days, int limit) {
        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = endDate.minusDays(days);

        List<Form4> form4s = form4Repository.findByFilingDateBetween(
            startDate, endDate, PageRequest.of(0, 10000)
        ).getContent();

        List<InsiderBuy> insiderBuys = convertToInsiderBuys(form4s, true);
        List<ClusterBuy> clusterBuys = groupIntoClusterBuys(insiderBuys, 1);

        return clusterBuys.stream()
            .sorted(Comparator.comparing(ClusterBuy::getTotalValue,
                Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(limit)
            .collect(Collectors.toList());
    }

    @Override
    public List<InsiderBuy> getTopInsiderBuysByValue(int days, int limit) {
        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = endDate.minusDays(days);

        List<Form4> form4s = form4Repository.findByFilingDateBetween(
            startDate, endDate, PageRequest.of(0, 10000)
        ).getContent();

        List<InsiderBuy> insiderBuys = convertToInsiderBuys(form4s, true);

        return insiderBuys.stream()
            .sorted(Comparator.comparing(InsiderBuy::getTransactionValue,
                Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(limit)
            .collect(Collectors.toList());
    }

    @Override
    public List<ClusterBuy> getHighSignificanceClusterBuys(int days, int minSignificanceScore, int limit) {
        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = endDate.minusDays(days);

        List<Form4> form4s = form4Repository.findByFilingDateBetween(
            startDate, endDate, PageRequest.of(0, 10000)
        ).getContent();

        List<InsiderBuy> insiderBuys = convertToInsiderBuys(form4s, true);
        List<ClusterBuy> clusterBuys = groupIntoClusterBuys(insiderBuys, 2);

        return clusterBuys.stream()
            .filter(cluster -> cluster.getSignificanceScore() >= minSignificanceScore)
            .sorted(Comparator.comparing(ClusterBuy::getSignificanceScore,
                Comparator.reverseOrder()))
            .limit(limit)
            .collect(Collectors.toList());
    }

    /**
     * Convert Form 4 filings to InsiderBuy objects
     *
     * @param form4s list of Form 4 filings
     * @param purchasesOnly if true, only include purchase transactions
     * @return list of InsiderBuy objects
     */
    private List<InsiderBuy> convertToInsiderBuys(List<Form4> form4s, boolean purchasesOnly) {
        List<InsiderBuy> insiderBuys = new ArrayList<>();

        for (Form4 form4 : form4s) {
            // Skip if no ticker
            if (form4.getTradingSymbol() == null || form4.getTradingSymbol().isEmpty()) {
                continue;
            }

            // Get primary reporting owner
            ReportingOwner owner = form4.getPrimaryReportingOwner();
            if (owner == null) {
                continue;
            }

            // Process non-derivative transactions
            List<NonDerivativeTransaction> transactions = purchasesOnly
                ? form4.getPurchaseTransactions()
                : form4.getNonDerivativeTransactions();

            for (NonDerivativeTransaction transaction : transactions) {
                InsiderBuy buy = convertTransactionToInsiderBuy(form4, owner, transaction);
                if (buy != null) {
                    insiderBuys.add(buy);
                }
            }
        }

        log.info("Converted {} Form 4s to {} insider buys", form4s.size(), insiderBuys.size());
        return insiderBuys;
    }

    /**
     * Convert a single transaction to an InsiderBuy object
     */
    private InsiderBuy convertTransactionToInsiderBuy(Form4 form4, ReportingOwner owner,
                                                       NonDerivativeTransaction transaction) {
        try {
            InsiderBuy buy = InsiderBuy.builder()
                .accessionNumber(form4.getAccessionNumber())
                .filingDate(form4.getFilingDate())
                .tradeDate(transaction.getTransactionDate())
                .ticker(form4.getTradingSymbol())
                .companyName(form4.getIssuerName())
                .companyCik(form4.getIssuerCik())
                .insiderName(owner.getName())
                .insiderCik(owner.getCik())
                .insiderTitle(owner.getRelationshipDescription())
                .tradeType(transaction.getTransactionCode())
                .pricePerShare(transaction.getTransactionPricePerShare())
                .quantity(transaction.getTransactionShares())
                .sharesOwnedAfter(transaction.getSharesOwnedFollowingTransaction())
                .ownershipType(transaction.getDirectOrIndirectOwnership())
                .securityTitle(transaction.getSecurityTitle())
                .build();

            // Calculate derived fields
            buy.calculateSharesOwnedBefore();
            buy.calculateOwnershipChange();
            buy.calculateTransactionValue();

            return buy;
        } catch (Exception e) {
            log.warn("Error converting transaction to InsiderBuy: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Group InsiderBuys into ClusterBuys
     * Groups by ticker + trade date
     *
     * @param insiderBuys list of insider buys
     * @param minInsiders minimum number of distinct insiders required to form a cluster
     * @return list of cluster buys
     */
    private List<ClusterBuy> groupIntoClusterBuys(List<InsiderBuy> insiderBuys, int minInsiders) {
        // Group by ticker + trade date
        Map<String, List<InsiderBuy>> grouped = insiderBuys.stream()
            .filter(buy -> buy.getTicker() != null && buy.getTradeDate() != null)
            .collect(Collectors.groupingBy(buy ->
                buy.getTicker() + "|" + buy.getTradeDate().toString()
            ));

        List<ClusterBuy> clusterBuys = new ArrayList<>();

        for (Map.Entry<String, List<InsiderBuy>> entry : grouped.entrySet()) {
            List<InsiderBuy> buysInCluster = entry.getValue();

            // Check if we have enough distinct insiders
            long distinctInsiders = buysInCluster.stream()
                .map(InsiderBuy::getInsiderCik)
                .filter(cik -> cik != null)
                .distinct()
                .count();

            if (distinctInsiders >= minInsiders) {
                ClusterBuy cluster = ClusterBuy.builder()
                    .insiderBuys(buysInCluster)
                    .build();

                cluster.aggregateFromInsiderBuys();
                clusterBuys.add(cluster);
            }
        }

        log.info("Grouped {} insider buys into {} clusters (min {} insiders)",
            insiderBuys.size(), clusterBuys.size(), minInsiders);

        return clusterBuys;
    }

    /**
     * Paginate a list manually
     */
    private <T> Page<T> paginateList(List<T> list, Pageable pageable) {
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), list.size());

        List<T> pageContent = start >= list.size() ? new ArrayList<>() : list.subList(start, end);

        return new PageImpl<>(pageContent, pageable, list.size());
    }
}
