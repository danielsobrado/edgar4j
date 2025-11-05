package org.jds.edgar4j.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.jds.edgar4j.model.Form4;
import org.jds.edgar4j.model.NonDerivativeTransaction;
import org.jds.edgar4j.model.ReportingOwner;
import org.jds.edgar4j.model.report.ClusterBuy;
import org.jds.edgar4j.model.report.InsiderBuy;
import org.jds.edgar4j.repository.Form4Repository;
import org.jds.edgar4j.service.IndustryLookupService;
import org.jds.edgar4j.service.impl.InsiderBuyAggregationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Test cases for InsiderBuyAggregationService
 * Tests aggregation logic, clustering, and query methods
 *
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-11-05
 */
@ExtendWith(MockitoExtension.class)
public class InsiderBuyAggregationServiceTests {

    @Mock
    private Form4Repository form4Repository;

    @Mock
    private IndustryLookupService industryLookupService;

    @InjectMocks
    private InsiderBuyAggregationServiceImpl aggregationService;

    private List<Form4> testForm4s;

    @BeforeEach
    public void setUp() {
        testForm4s = createTestForm4Data();

        // Setup industry lookup mock to return test industries
        when(industryLookupService.getIndustryByCik(anyString()))
            .thenAnswer(invocation -> {
                String cik = invocation.getArgument(0);
                if (cik != null && cik.contains("AAPL")) {
                    return "Electronic Computers";
                } else if (cik != null && cik.contains("MSFT")) {
                    return "Prepackaged Software";
                }
                return "Unknown Industry";
            });
    }

    @DisplayName("Test get latest cluster buys")
    @Test
    public void testGetLatestClusterBuys() {
        // Setup mock
        Page<Form4> form4Page = new PageImpl<>(testForm4s);
        when(form4Repository.findByFilingDateBetween(any(), any(), any()))
            .thenReturn(form4Page);

        // Execute
        Pageable pageable = PageRequest.of(0, 10);
        Page<ClusterBuy> result = aggregationService.getLatestClusterBuys(30, 2, pageable);

        // Verify
        assertNotNull(result);
        assertFalse(result.isEmpty());

        ClusterBuy cluster = result.getContent().get(0);
        assertEquals("AAPL", cluster.getTicker());
        assertEquals(2, cluster.getInsiderCount());  // 2 insiders bought AAPL on same day
    }

    @DisplayName("Test get latest insider buys")
    @Test
    public void testGetLatestInsiderBuys() {
        // Setup mock
        Page<Form4> form4Page = new PageImpl<>(testForm4s);
        when(form4Repository.findByFilingDateBetween(any(), any(), any()))
            .thenReturn(form4Page);

        // Execute
        Pageable pageable = PageRequest.of(0, 10);
        Page<InsiderBuy> result = aggregationService.getLatestInsiderBuys(30, pageable);

        // Verify
        assertNotNull(result);
        assertFalse(result.isEmpty());

        // Should have 3 total insider buys (2 AAPL + 1 MSFT)
        assertEquals(3, result.getTotalElements());
    }

    @DisplayName("Test get cluster buys by ticker")
    @Test
    public void testGetClusterBuysByTicker() {
        // Setup mock
        List<Form4> appleForm4s = testForm4s.stream()
            .filter(f -> "AAPL".equals(f.getTradingSymbol()))
            .toList();
        Page<Form4> form4Page = new PageImpl<>(appleForm4s);

        when(form4Repository.findByTradingSymbolAndFilingDateBetween(
            anyString(), any(), any(), any()))
            .thenReturn(form4Page);

        // Execute
        List<ClusterBuy> result = aggregationService.getClusterBuysByTicker("AAPL", 90, 2);

        // Verify
        assertNotNull(result);
        assertFalse(result.isEmpty());

        ClusterBuy cluster = result.get(0);
        assertEquals("AAPL", cluster.getTicker());
        assertEquals(2, cluster.getInsiderCount());
    }

    @DisplayName("Test get insider buys by ticker")
    @Test
    public void testGetInsiderBuysByTicker() {
        // Setup mock
        List<Form4> appleForm4s = testForm4s.stream()
            .filter(f -> "AAPL".equals(f.getTradingSymbol()))
            .toList();
        Page<Form4> form4Page = new PageImpl<>(appleForm4s);

        when(form4Repository.findByTradingSymbolAndFilingDateBetween(
            anyString(), any(), any(), any()))
            .thenReturn(form4Page);

        // Execute
        List<InsiderBuy> result = aggregationService.getInsiderBuysByTicker("AAPL", 90);

        // Verify
        assertNotNull(result);
        assertEquals(2, result.size());  // 2 AAPL buys
        assertTrue(result.stream().allMatch(b -> "AAPL".equals(b.getTicker())));
    }

    @DisplayName("Test get insider buys by insider CIK")
    @Test
    public void testGetInsiderBuysByInsider() {
        // Setup mock
        Page<Form4> form4Page = new PageImpl<>(testForm4s);
        when(form4Repository.findByFilingDateBetween(any(), any(), any()))
            .thenReturn(form4Page);

        // Execute
        List<InsiderBuy> result = aggregationService.getInsiderBuysByInsider("0001111111", 180);

        // Verify
        assertNotNull(result);
        assertEquals(1, result.size());  // Only 1 buy by this insider
        assertEquals("0001111111", result.get(0).getInsiderCik());
    }

    @DisplayName("Test get top cluster buys by value")
    @Test
    public void testGetTopClusterBuysByValue() {
        // Setup mock
        Page<Form4> form4Page = new PageImpl<>(testForm4s);
        when(form4Repository.findByFilingDateBetween(any(), any(), any()))
            .thenReturn(form4Page);

        // Execute
        List<ClusterBuy> result = aggregationService.getTopClusterBuysByValue(30, 5);

        // Verify
        assertNotNull(result);
        assertFalse(result.isEmpty());

        // Verify sorted by value (descending)
        if (result.size() > 1) {
            BigDecimal firstValue = result.get(0).getTotalValue();
            BigDecimal secondValue = result.get(1).getTotalValue();
            assertTrue(firstValue.compareTo(secondValue) >= 0);
        }
    }

    @DisplayName("Test get top insider buys by value")
    @Test
    public void testGetTopInsiderBuysByValue() {
        // Setup mock
        Page<Form4> form4Page = new PageImpl<>(testForm4s);
        when(form4Repository.findByFilingDateBetween(any(), any(), any()))
            .thenReturn(form4Page);

        // Execute
        List<InsiderBuy> result = aggregationService.getTopInsiderBuysByValue(30, 5);

        // Verify
        assertNotNull(result);
        assertFalse(result.isEmpty());

        // Verify sorted by value (descending)
        if (result.size() > 1) {
            BigDecimal firstValue = result.get(0).getTransactionValue();
            BigDecimal secondValue = result.get(1).getTransactionValue();
            assertTrue(firstValue.compareTo(secondValue) >= 0);
        }
    }

    @DisplayName("Test get high significance cluster buys")
    @Test
    public void testGetHighSignificanceClusterBuys() {
        // Setup mock
        Page<Form4> form4Page = new PageImpl<>(testForm4s);
        when(form4Repository.findByFilingDateBetween(any(), any(), any()))
            .thenReturn(form4Page);

        // Execute
        List<ClusterBuy> result = aggregationService.getHighSignificanceClusterBuys(30, 50, 10);

        // Verify
        assertNotNull(result);

        // All results should have significance >= 50
        for (ClusterBuy cluster : result) {
            assertTrue(cluster.getSignificanceScore() >= 50);
        }

        // Verify sorted by significance (descending)
        if (result.size() > 1) {
            int firstScore = result.get(0).getSignificanceScore();
            int secondScore = result.get(1).getSignificanceScore();
            assertTrue(firstScore >= secondScore);
        }
    }

    @DisplayName("Test clustering - same ticker and date")
    @Test
    public void testClustering_SameTickerAndDate() {
        // Setup mock
        Page<Form4> form4Page = new PageImpl<>(testForm4s);
        when(form4Repository.findByFilingDateBetween(any(), any(), any()))
            .thenReturn(form4Page);

        // Execute
        Page<ClusterBuy> result = aggregationService.getLatestClusterBuys(30, 2, PageRequest.of(0, 10));

        // Verify - should cluster the 2 AAPL buys on same day
        ClusterBuy appleCluster = result.getContent().stream()
            .filter(c -> "AAPL".equals(c.getTicker()))
            .findFirst()
            .orElse(null);

        assertNotNull(appleCluster);
        assertEquals(2, appleCluster.getInsiderCount());
        assertEquals(LocalDate.of(2025, 11, 4), appleCluster.getTradeDate());
    }

    @DisplayName("Test clustering - different dates not clustered")
    @Test
    public void testClustering_DifferentDatesNotClustered() {
        // Create Form4s with same ticker but different dates
        List<Form4> differentDateForm4s = new ArrayList<>();

        // Day 1
        differentDateForm4s.add(createForm4("AAPL", "Apple Inc.", "0001111111",
            "John Doe", "Director", LocalDate.of(2025, 11, 1),
            LocalDateTime.of(2025, 11, 2, 10, 0), "100", "150.00"));

        // Day 2
        differentDateForm4s.add(createForm4("AAPL", "Apple Inc.", "0002222222",
            "Jane Smith", "CEO", LocalDate.of(2025, 11, 2),
            LocalDateTime.of(2025, 11, 3, 10, 0), "200", "151.00"));

        Page<Form4> form4Page = new PageImpl<>(differentDateForm4s);
        when(form4Repository.findByFilingDateBetween(any(), any(), any()))
            .thenReturn(form4Page);

        // Execute
        Page<ClusterBuy> result = aggregationService.getLatestClusterBuys(30, 2, PageRequest.of(0, 10));

        // Verify - should NOT cluster because different dates
        assertEquals(0, result.getTotalElements());  // No clusters with minInsiders=2
    }

    @DisplayName("Test pagination")
    @Test
    public void testPagination() {
        // Setup mock
        Page<Form4> form4Page = new PageImpl<>(testForm4s);
        when(form4Repository.findByFilingDateBetween(any(), any(), any()))
            .thenReturn(form4Page);

        // Execute - page 0, size 1
        Pageable pageable = PageRequest.of(0, 1);
        Page<InsiderBuy> result = aggregationService.getLatestInsiderBuys(30, pageable);

        // Verify
        assertEquals(1, result.getContent().size());  // Only 1 per page
        assertEquals(3, result.getTotalElements());  // Total 3 buys
        assertEquals(3, result.getTotalPages());  // 3 pages
    }

    @DisplayName("Test empty results")
    @Test
    public void testEmptyResults() {
        // Setup mock - empty list
        Page<Form4> emptyPage = new PageImpl<>(new ArrayList<>());
        when(form4Repository.findByFilingDateBetween(any(), any(), any()))
            .thenReturn(emptyPage);

        // Execute
        Page<InsiderBuy> result = aggregationService.getLatestInsiderBuys(30, PageRequest.of(0, 10));

        // Verify
        assertNotNull(result);
        assertTrue(result.isEmpty());
        assertEquals(0, result.getTotalElements());
    }

    @DisplayName("Test get cluster buys by date range")
    @Test
    public void testGetClusterBuysByDateRange() {
        // Setup mock
        Page<Form4> form4Page = new PageImpl<>(testForm4s);
        when(form4Repository.findByPeriodOfReportBetween(any(), any(), any()))
            .thenReturn(form4Page);

        // Execute
        LocalDate startDate = LocalDate.of(2025, 11, 1);
        LocalDate endDate = LocalDate.of(2025, 11, 30);
        Page<ClusterBuy> result = aggregationService.getClusterBuysByDateRange(
            startDate, endDate, 2, PageRequest.of(0, 10)
        );

        // Verify
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @DisplayName("Test get insider buys by date range")
    @Test
    public void testGetInsiderBuysByDateRange() {
        // Setup mock
        Page<Form4> form4Page = new PageImpl<>(testForm4s);
        when(form4Repository.findByPeriodOfReportBetween(any(), any(), any()))
            .thenReturn(form4Page);

        // Execute
        LocalDate startDate = LocalDate.of(2025, 11, 1);
        LocalDate endDate = LocalDate.of(2025, 11, 30);
        Page<InsiderBuy> result = aggregationService.getInsiderBuysByDateRange(
            startDate, endDate, PageRequest.of(0, 10)
        );

        // Verify
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @DisplayName("Test industry classification populated in cluster buys")
    @Test
    public void testIndustryClassificationInClusterBuys() {
        // Setup mock
        Page<Form4> form4Page = new PageImpl<>(testForm4s);
        when(form4Repository.findByFilingDateBetween(any(), any(), any()))
            .thenReturn(form4Page);

        // Execute
        Pageable pageable = PageRequest.of(0, 10);
        Page<ClusterBuy> result = aggregationService.getLatestClusterBuys(30, 2, pageable);

        // Verify
        assertNotNull(result);
        assertFalse(result.isEmpty());

        ClusterBuy cluster = result.getContent().get(0);
        assertNotNull(cluster.getIndustry());
        assertEquals("Electronic Computers", cluster.getIndustry());
    }

    @DisplayName("Test industry classification populated in insider buys")
    @Test
    public void testIndustryClassificationInInsiderBuys() {
        // Setup mock
        Page<Form4> form4Page = new PageImpl<>(testForm4s);
        when(form4Repository.findByFilingDateBetween(any(), any(), any()))
            .thenReturn(form4Page);

        // Execute
        Pageable pageable = PageRequest.of(0, 10);
        Page<InsiderBuy> result = aggregationService.getLatestInsiderBuys(30, pageable);

        // Verify
        assertNotNull(result);
        assertFalse(result.isEmpty());

        // Check AAPL buys have correct industry
        long appleCount = result.getContent().stream()
            .filter(buy -> "AAPL".equals(buy.getTicker()))
            .filter(buy -> "Electronic Computers".equals(buy.getIndustry()))
            .count();
        assertEquals(2, appleCount);

        // Check MSFT buy has correct industry
        long msftCount = result.getContent().stream()
            .filter(buy -> "MSFT".equals(buy.getTicker()))
            .filter(buy -> "Prepackaged Software".equals(buy.getIndustry()))
            .count();
        assertEquals(1, msftCount);
    }

    /**
     * Create test Form4 data for testing
     */
    private List<Form4> createTestForm4Data() {
        List<Form4> form4s = new ArrayList<>();

        // AAPL - 2 insiders buying on same day (should cluster)
        form4s.add(createForm4("AAPL", "Apple Inc.", "0001111111",
            "John Doe", "Director", LocalDate.of(2025, 11, 4),
            LocalDateTime.of(2025, 11, 4, 17, 0), "1000", "150.00"));

        form4s.add(createForm4("AAPL", "Apple Inc.", "0002222222",
            "Jane Smith", "CEO", LocalDate.of(2025, 11, 4),
            LocalDateTime.of(2025, 11, 4, 18, 0), "2000", "151.00"));

        // MSFT - 1 insider (won't cluster with minInsiders=2)
        form4s.add(createForm4("MSFT", "Microsoft Corp", "0003333333",
            "Bob Johnson", "Director", LocalDate.of(2025, 11, 3),
            LocalDateTime.of(2025, 11, 3, 16, 0), "500", "380.00"));

        return form4s;
    }

    /**
     * Helper method to create a Form4 object for testing
     */
    private Form4 createForm4(String ticker, String issuerName, String insiderCik,
                              String insiderName, String insiderTitle,
                              LocalDate tradeDate, LocalDateTime filingDate,
                              String shares, String price) {
        ReportingOwner owner = ReportingOwner.builder()
            .cik(insiderCik)
            .name(insiderName)
            .isDirector(insiderTitle.contains("Director"))
            .isOfficer(insiderTitle.contains("CEO") || insiderTitle.contains("CFO"))
            .officerTitle(insiderTitle.contains("CEO") || insiderTitle.contains("CFO") ? insiderTitle : null)
            .build();

        NonDerivativeTransaction transaction = NonDerivativeTransaction.builder()
            .securityTitle("Common Stock")
            .transactionDate(tradeDate)
            .transactionCode("P")
            .transactionShares(new BigDecimal(shares))
            .acquiredDisposedCode("A")
            .transactionPricePerShare(new BigDecimal(price))
            .sharesOwnedFollowingTransaction(new BigDecimal(shares).multiply(new BigDecimal("10")))
            .directOrIndirectOwnership("D")
            .build();

        List<NonDerivativeTransaction> transactions = new ArrayList<>();
        transactions.add(transaction);

        List<ReportingOwner> owners = new ArrayList<>();
        owners.add(owner);

        return Form4.builder()
            .accessionNumber("0001234567-25-00000" + Math.random())
            .tradingSymbol(ticker)
            .issuerName(issuerName)
            .issuerCik("000" + ticker.hashCode())
            .filingDate(filingDate)
            .periodOfReport(tradeDate)
            .reportingOwners(owners)
            .nonDerivativeTransactions(transactions)
            .derivativeTransactions(new ArrayList<>())
            .build();
    }
}
