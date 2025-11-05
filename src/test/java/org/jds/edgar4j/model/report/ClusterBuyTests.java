package org.jds.edgar4j.model.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for ClusterBuy model
 * Tests aggregation logic and significance scoring
 *
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-11-05
 */
public class ClusterBuyTests {

    @DisplayName("Test ClusterBuy - Aggregate from insider buys")
    @Test
    public void testAggregateFromInsiderBuys() {
        // Create test insider buys
        List<InsiderBuy> insiderBuys = new ArrayList<>();

        insiderBuys.add(InsiderBuy.builder()
            .insiderCik("0001111111")
            .insiderName("John Doe")
            .insiderTitle("Director")
            .ticker("AAPL")
            .companyName("Apple Inc.")
            .companyCik("0000320193")
            .filingDate(LocalDateTime.of(2025, 11, 4, 17, 0, 0))
            .tradeDate(LocalDate.of(2025, 11, 4))
            .pricePerShare(new BigDecimal("150.00"))
            .quantity(new BigDecimal("1000"))
            .sharesOwnedAfter(new BigDecimal("10000"))
            .ownershipChangePercent(new BigDecimal("10.00"))
            .transactionValue(new BigDecimal("150000.00"))
            .build());

        insiderBuys.add(InsiderBuy.builder()
            .insiderCik("0002222222")
            .insiderName("Jane Smith")
            .insiderTitle("CEO")
            .ticker("AAPL")
            .companyName("Apple Inc.")
            .companyCik("0000320193")
            .filingDate(LocalDateTime.of(2025, 11, 4, 18, 0, 0))
            .tradeDate(LocalDate.of(2025, 11, 4))
            .pricePerShare(new BigDecimal("151.00"))
            .quantity(new BigDecimal("2000"))
            .sharesOwnedAfter(new BigDecimal("50000"))
            .ownershipChangePercent(new BigDecimal("4.17"))
            .transactionValue(new BigDecimal("302000.00"))
            .build());

        // Create cluster and aggregate
        ClusterBuy cluster = ClusterBuy.builder()
            .insiderBuys(insiderBuys)
            .build();

        cluster.aggregateFromInsiderBuys();

        // Verify aggregation
        assertEquals(2, cluster.getInsiderCount());
        assertEquals(new BigDecimal("3000"), cluster.getTotalQuantity());
        assertEquals(new BigDecimal("452000.00"), cluster.getTotalValue());
        assertEquals(new BigDecimal("60000"), cluster.getTotalSharesOwned());

        // Verify average price
        assertEquals(new BigDecimal("150.50"), cluster.getAveragePrice());

        // Verify average ownership change
        assertEquals(new BigDecimal("7.09"), cluster.getAverageOwnershipChange());  // (10.00 + 4.17) / 2 = 7.085 rounded

        // Verify metadata
        assertEquals("AAPL", cluster.getTicker());
        assertEquals("Apple Inc.", cluster.getCompanyName());
        assertEquals(LocalDate.of(2025, 11, 4), cluster.getTradeDate());
        assertEquals(LocalDateTime.of(2025, 11, 4, 18, 0, 0), cluster.getFilingDate());  // Most recent
    }

    @DisplayName("Test ClusterBuy - Insider roles identification")
    @Test
    public void testInsiderRolesIdentification() {
        List<InsiderBuy> insiderBuys = new ArrayList<>();

        // Director
        insiderBuys.add(InsiderBuy.builder()
            .insiderCik("0001")
            .insiderTitle("Director")
            .ticker("TEST")
            .tradeDate(LocalDate.now())
            .quantity(new BigDecimal("100"))
            .pricePerShare(new BigDecimal("10"))
            .transactionValue(new BigDecimal("1000"))
            .build());

        // Officer (CEO)
        insiderBuys.add(InsiderBuy.builder()
            .insiderCik("0002")
            .insiderTitle("CEO")
            .ticker("TEST")
            .tradeDate(LocalDate.now())
            .quantity(new BigDecimal("200"))
            .pricePerShare(new BigDecimal("10"))
            .transactionValue(new BigDecimal("2000"))
            .build());

        // 10% Owner
        insiderBuys.add(InsiderBuy.builder()
            .insiderCik("0003")
            .insiderTitle("10%")
            .ticker("TEST")
            .tradeDate(LocalDate.now())
            .quantity(new BigDecimal("300"))
            .pricePerShare(new BigDecimal("10"))
            .transactionValue(new BigDecimal("3000"))
            .build());

        ClusterBuy cluster = ClusterBuy.builder()
            .insiderBuys(insiderBuys)
            .build();

        cluster.aggregateFromInsiderBuys();

        assertTrue(cluster.isHasDirectorBuys());
        assertTrue(cluster.isHasOfficerBuys());
        assertTrue(cluster.isHasTenPercentOwnerBuys());
        assertTrue(cluster.getInsiderRoles().contains("D"));
        assertTrue(cluster.getInsiderRoles().contains("O"));
        assertTrue(cluster.getInsiderRoles().contains("10%"));
    }

    @DisplayName("Test ClusterBuy - Significance score calculation (high)")
    @Test
    public void testSignificanceScore_High() {
        List<InsiderBuy> insiderBuys = new ArrayList<>();

        // Create cluster with 5 insiders, high value, directors and officers
        for (int i = 0; i < 5; i++) {
            insiderBuys.add(InsiderBuy.builder()
                .insiderCik("000" + i)
                .insiderTitle(i < 2 ? "Director" : "CEO")
                .ticker("TEST")
                .tradeDate(LocalDate.now())
                .quantity(new BigDecimal("10000"))
                .pricePerShare(new BigDecimal("100"))
                .transactionValue(new BigDecimal("1000000"))  // $1M each
                .build());
        }

        ClusterBuy cluster = ClusterBuy.builder()
            .insiderBuys(insiderBuys)
            .build();

        cluster.aggregateFromInsiderBuys();

        int score = cluster.getSignificanceScore();

        // 5 insiders = 30 points (maxed out)
        // $5M total value = 30 points
        // Directors + Officers = 30 points
        // Total = 90 points
        assertTrue(score >= 80);
        assertTrue(cluster.isHighSignificance());
    }

    @DisplayName("Test ClusterBuy - Significance score calculation (low)")
    @Test
    public void testSignificanceScore_Low() {
        List<InsiderBuy> insiderBuys = new ArrayList<>();

        // Create cluster with 1 insider, low value
        insiderBuys.add(InsiderBuy.builder()
            .insiderCik("0001")
            .insiderTitle("Other")
            .ticker("TEST")
            .tradeDate(LocalDate.now())
            .quantity(new BigDecimal("100"))
            .pricePerShare(new BigDecimal("10"))
            .transactionValue(new BigDecimal("1000"))  // $1K only
            .build());

        ClusterBuy cluster = ClusterBuy.builder()
            .insiderBuys(insiderBuys)
            .build();

        cluster.aggregateFromInsiderBuys();

        int score = cluster.getSignificanceScore();

        // 1 insider = 10 points
        // $1K value = 10 points
        // No directors/officers = 0 points
        // Total = 20 points
        assertTrue(score < 30);
        assertFalse(cluster.isHighSignificance());
    }

    @DisplayName("Test ClusterBuy - Get insider summary")
    @Test
    public void testGetInsiderSummary() {
        List<InsiderBuy> insiderBuys = new ArrayList<>();

        insiderBuys.add(InsiderBuy.builder()
            .insiderCik("0001")
            .insiderName("John Doe")
            .insiderTitle("CEO")
            .ticker("TEST")
            .tradeDate(LocalDate.now())
            .quantity(new BigDecimal("100"))
            .pricePerShare(new BigDecimal("10"))
            .transactionValue(new BigDecimal("1000"))
            .build());

        insiderBuys.add(InsiderBuy.builder()
            .insiderCik("0002")
            .insiderName("Jane Smith")
            .insiderTitle("Director")
            .ticker("TEST")
            .tradeDate(LocalDate.now())
            .quantity(new BigDecimal("200"))
            .pricePerShare(new BigDecimal("10"))
            .transactionValue(new BigDecimal("2000"))
            .build());

        ClusterBuy cluster = ClusterBuy.builder()
            .insiderBuys(insiderBuys)
            .build();

        cluster.aggregateFromInsiderBuys();

        String summary = cluster.getInsiderSummary();

        assertTrue(summary.contains("2 insiders"));
        assertTrue(summary.contains("John Doe"));
        assertTrue(summary.contains("Jane Smith"));
        assertTrue(summary.contains("CEO"));
        assertTrue(summary.contains("Director"));
    }

    @DisplayName("Test ClusterBuy - Get insider summary (many insiders)")
    @Test
    public void testGetInsiderSummary_ManyInsiders() {
        List<InsiderBuy> insiderBuys = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            insiderBuys.add(InsiderBuy.builder()
                .insiderCik("000" + i)
                .insiderName("Insider " + i)
                .insiderTitle("Director")
                .ticker("TEST")
                .tradeDate(LocalDate.now())
                .quantity(new BigDecimal("100"))
                .pricePerShare(new BigDecimal("10"))
                .transactionValue(new BigDecimal("1000"))
                .build());
        }

        ClusterBuy cluster = ClusterBuy.builder()
            .insiderBuys(insiderBuys)
            .build();

        cluster.aggregateFromInsiderBuys();

        String summary = cluster.getInsiderSummary();

        assertTrue(summary.contains("5 insiders"));
        assertTrue(summary.contains("and 2 more"));  // Shows first 3, then "and 2 more"
    }

    @DisplayName("Test ClusterBuy - Get formatted insider roles")
    @Test
    public void testGetFormattedInsiderRoles() {
        List<InsiderBuy> insiderBuys = new ArrayList<>();

        insiderBuys.add(InsiderBuy.builder()
            .insiderCik("0001")
            .insiderTitle("Director")
            .ticker("TEST")
            .tradeDate(LocalDate.now())
            .quantity(new BigDecimal("100"))
            .pricePerShare(new BigDecimal("10"))
            .transactionValue(new BigDecimal("1000"))
            .build());

        ClusterBuy cluster = ClusterBuy.builder()
            .insiderBuys(insiderBuys)
            .build();

        cluster.aggregateFromInsiderBuys();

        String formatted = cluster.getFormattedInsiderRoles();
        assertEquals("D", formatted);
    }

    @DisplayName("Test ClusterBuy - Get formatted ownership change")
    @Test
    public void testGetFormattedOwnershipChange() {
        List<InsiderBuy> insiderBuys = new ArrayList<>();

        insiderBuys.add(InsiderBuy.builder()
            .insiderCik("0001")
            .ticker("TEST")
            .tradeDate(LocalDate.now())
            .quantity(new BigDecimal("100"))
            .pricePerShare(new BigDecimal("10"))
            .transactionValue(new BigDecimal("1000"))
            .ownershipChangePercent(new BigDecimal("25.50"))
            .build());

        ClusterBuy cluster = ClusterBuy.builder()
            .insiderBuys(insiderBuys)
            .build();

        cluster.aggregateFromInsiderBuys();

        assertEquals("+25.50%", cluster.getFormattedOwnershipChange());
    }

    @DisplayName("Test ClusterBuy - Empty insider buys list")
    @Test
    public void testAggregateFromInsiderBuys_EmptyList() {
        ClusterBuy cluster = ClusterBuy.builder()
            .insiderBuys(new ArrayList<>())
            .build();

        cluster.aggregateFromInsiderBuys();

        // Should handle empty list gracefully
        assertNull(cluster.getInsiderCount());
    }

    @DisplayName("Test ClusterBuy - Null insider buys list")
    @Test
    public void testAggregateFromInsiderBuys_NullList() {
        ClusterBuy cluster = ClusterBuy.builder()
            .insiderBuys(null)
            .build();

        cluster.aggregateFromInsiderBuys();

        // Should handle null list gracefully
        assertNotNull(cluster);
    }

    @DisplayName("Test ClusterBuy - Builder with defaults")
    @Test
    public void testBuilderDefaults() {
        ClusterBuy cluster = ClusterBuy.builder().build();

        assertNotNull(cluster);
        assertNotNull(cluster.getInsiderBuys());  // Should have default ArrayList
        assertTrue(cluster.getInsiderBuys().isEmpty());
    }

    @DisplayName("Test ClusterBuy - Complete flow")
    @Test
    public void testCompleteFlow() {
        // Create realistic cluster buy scenario
        List<InsiderBuy> insiderBuys = new ArrayList<>();

        // 3 directors buying OBK stock
        for (int i = 0; i < 3; i++) {
            insiderBuys.add(InsiderBuy.builder()
                .insiderCik("000" + i)
                .insiderName("Director " + i)
                .insiderTitle("Director")
                .ticker("OBK")
                .companyName("Origin Bancorp, Inc.")
                .companyCik("0001234567")
                .filingDate(LocalDateTime.of(2025, 11, 4, 17, 26, 41))
                .tradeDate(LocalDate.of(2025, 11, 4))
                .pricePerShare(new BigDecimal("34.50"))
                .quantity(new BigDecimal("10000"))
                .sharesOwnedAfter(new BigDecimal("50000"))
                .ownershipChangePercent(new BigDecimal("25.00"))
                .transactionValue(new BigDecimal("345000.00"))
                .build());
        }

        ClusterBuy cluster = ClusterBuy.builder()
            .insiderBuys(insiderBuys)
            .build();

        cluster.aggregateFromInsiderBuys();

        // Verify complete aggregation
        assertEquals(3, cluster.getInsiderCount());
        assertEquals("OBK", cluster.getTicker());
        assertEquals("Origin Bancorp, Inc.", cluster.getCompanyName());
        assertEquals(new BigDecimal("30000"), cluster.getTotalQuantity());
        assertEquals(new BigDecimal("1035000.00"), cluster.getTotalValue());
        assertEquals(new BigDecimal("34.50"), cluster.getAveragePrice());
        assertEquals(new BigDecimal("25.00"), cluster.getAverageOwnershipChange());
        assertTrue(cluster.isHasDirectorBuys());
        assertTrue(cluster.getSignificanceScore() > 50);

        // Verify summary
        String summary = cluster.getInsiderSummary();
        assertTrue(summary.contains("3 insiders"));
    }
}
