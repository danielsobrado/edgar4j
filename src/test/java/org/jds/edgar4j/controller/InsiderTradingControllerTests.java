package org.jds.edgar4j.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.jds.edgar4j.model.report.ClusterBuy;
import org.jds.edgar4j.model.report.InsiderBuy;
import org.jds.edgar4j.service.InsiderBuyAggregationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.embedded.EmbeddedMongoAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

/**
 * Test cases for InsiderTradingController
 * Tests REST API endpoints using MockMvc
 *
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-11-05
 */
@WebMvcTest(InsiderTradingController.class)
@EnableAutoConfiguration(exclude = {
    EmbeddedMongoAutoConfiguration.class,
    MongoAutoConfiguration.class,
    MongoDataAutoConfiguration.class,
    SecurityAutoConfiguration.class,
    ElasticsearchDataAutoConfiguration.class,
    ElasticsearchRestClientAutoConfiguration.class
})
public class InsiderTradingControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InsiderBuyAggregationService aggregationService;

    private List<ClusterBuy> testClusterBuys;
    private List<InsiderBuy> testInsiderBuys;

    @BeforeEach
    public void setUp() {
        testClusterBuys = createTestClusterBuys();
        testInsiderBuys = createTestInsiderBuys();
    }

    @DisplayName("Test GET /api/insider-trading/cluster-buys/latest")
    @Test
    public void testGetLatestClusterBuys() throws Exception {
        // Setup mock
        Page<ClusterBuy> page = new PageImpl<>(testClusterBuys);
        when(aggregationService.getLatestClusterBuys(anyInt(), anyInt(), any(Pageable.class)))
            .thenReturn(page);

        // Execute and verify
        mockMvc.perform(get("/api/insider-trading/cluster-buys/latest")
                .param("days", "30")
                .param("minInsiders", "2")
                .param("page", "0")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content[0].ticker").value("AAPL"))
            .andExpect(jsonPath("$.content[0].insiderCount").value(3))
            .andExpect(jsonPath("$.totalElements").value(testClusterBuys.size()));
    }

    @DisplayName("Test GET /api/insider-trading/cluster-buys/latest with defaults")
    @Test
    public void testGetLatestClusterBuys_DefaultParams() throws Exception {
        // Setup mock
        Page<ClusterBuy> page = new PageImpl<>(testClusterBuys);
        when(aggregationService.getLatestClusterBuys(anyInt(), anyInt(), any(Pageable.class)))
            .thenReturn(page);

        // Execute and verify
        mockMvc.perform(get("/api/insider-trading/cluster-buys/latest"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.content").isArray());
    }

    @DisplayName("Test GET /api/insider-trading/insider-buys/latest")
    @Test
    public void testGetLatestInsiderBuys() throws Exception {
        // Setup mock
        Page<InsiderBuy> page = new PageImpl<>(testInsiderBuys);
        when(aggregationService.getLatestInsiderBuys(anyInt(), any(Pageable.class)))
            .thenReturn(page);

        // Execute and verify
        mockMvc.perform(get("/api/insider-trading/insider-buys/latest")
                .param("days", "30")
                .param("page", "0")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content[0].ticker").value("AAPL"))
            .andExpect(jsonPath("$.content[0].insiderName").value("John Doe"))
            .andExpect(jsonPath("$.totalElements").value(testInsiderBuys.size()));
    }

    @DisplayName("Test GET /api/insider-trading/cluster-buys/ticker/{ticker}")
    @Test
    public void testGetClusterBuysByTicker() throws Exception {
        // Setup mock
        List<ClusterBuy> appleCluster = testClusterBuys.stream()
            .filter(c -> "AAPL".equals(c.getTicker()))
            .toList();
        when(aggregationService.getClusterBuysByTicker(anyString(), anyInt(), anyInt()))
            .thenReturn(appleCluster);

        // Execute and verify
        mockMvc.perform(get("/api/insider-trading/cluster-buys/ticker/AAPL")
                .param("days", "90")
                .param("minInsiders", "2"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].ticker").value("AAPL"));
    }

    @DisplayName("Test GET /api/insider-trading/insider-buys/ticker/{ticker}")
    @Test
    public void testGetInsiderBuysByTicker() throws Exception {
        // Setup mock
        List<InsiderBuy> appleBuys = testInsiderBuys.stream()
            .filter(b -> "AAPL".equals(b.getTicker()))
            .toList();
        when(aggregationService.getInsiderBuysByTicker(anyString(), anyInt()))
            .thenReturn(appleBuys);

        // Execute and verify
        mockMvc.perform(get("/api/insider-trading/insider-buys/ticker/AAPL")
                .param("days", "90"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].ticker").value("AAPL"));
    }

    @DisplayName("Test GET /api/insider-trading/insider-buys/insider/{insiderCik}")
    @Test
    public void testGetInsiderBuysByInsider() throws Exception {
        // Setup mock
        List<InsiderBuy> insiderBuys = List.of(testInsiderBuys.get(0));
        when(aggregationService.getInsiderBuysByInsider(anyString(), anyInt()))
            .thenReturn(insiderBuys);

        // Execute and verify
        mockMvc.perform(get("/api/insider-trading/insider-buys/insider/0001111111")
                .param("days", "180"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].insiderCik").value("0001111111"));
    }

    @DisplayName("Test GET /api/insider-trading/cluster-buys/date-range")
    @Test
    public void testGetClusterBuysByDateRange() throws Exception {
        // Setup mock
        Page<ClusterBuy> page = new PageImpl<>(testClusterBuys);
        when(aggregationService.getClusterBuysByDateRange(
            any(LocalDate.class), any(LocalDate.class), anyInt(), any(Pageable.class)))
            .thenReturn(page);

        // Execute and verify
        mockMvc.perform(get("/api/insider-trading/cluster-buys/date-range")
                .param("startDate", "2025-01-01")
                .param("endDate", "2025-11-05")
                .param("minInsiders", "2")
                .param("page", "0")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.content").isArray());
    }

    @DisplayName("Test GET /api/insider-trading/insider-buys/date-range")
    @Test
    public void testGetInsiderBuysByDateRange() throws Exception {
        // Setup mock
        Page<InsiderBuy> page = new PageImpl<>(testInsiderBuys);
        when(aggregationService.getInsiderBuysByDateRange(
            any(LocalDate.class), any(LocalDate.class), any(Pageable.class)))
            .thenReturn(page);

        // Execute and verify
        mockMvc.perform(get("/api/insider-trading/insider-buys/date-range")
                .param("startDate", "2025-01-01")
                .param("endDate", "2025-11-05")
                .param("page", "0")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.content").isArray());
    }

    @DisplayName("Test GET /api/insider-trading/cluster-buys/top-by-value")
    @Test
    public void testGetTopClusterBuysByValue() throws Exception {
        // Setup mock
        when(aggregationService.getTopClusterBuysByValue(anyInt(), anyInt()))
            .thenReturn(testClusterBuys);

        // Execute and verify
        mockMvc.perform(get("/api/insider-trading/cluster-buys/top-by-value")
                .param("days", "30")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].totalValue").exists());
    }

    @DisplayName("Test GET /api/insider-trading/insider-buys/top-by-value")
    @Test
    public void testGetTopInsiderBuysByValue() throws Exception {
        // Setup mock
        when(aggregationService.getTopInsiderBuysByValue(anyInt(), anyInt()))
            .thenReturn(testInsiderBuys);

        // Execute and verify
        mockMvc.perform(get("/api/insider-trading/insider-buys/top-by-value")
                .param("days", "30")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].transactionValue").exists());
    }

    @DisplayName("Test GET /api/insider-trading/cluster-buys/high-significance")
    @Test
    public void testGetHighSignificanceClusterBuys() throws Exception {
        // Setup mock
        when(aggregationService.getHighSignificanceClusterBuys(anyInt(), anyInt(), anyInt()))
            .thenReturn(testClusterBuys);

        // Execute and verify
        mockMvc.perform(get("/api/insider-trading/cluster-buys/high-significance")
                .param("days", "30")
                .param("minScore", "70")
                .param("limit", "20"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray());
    }

    @DisplayName("Test GET /api/insider-trading/health")
    @Test
    public void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/api/insider-trading/health"))
            .andExpect(status().isOk())
            .andExpect(content().string("Insider Trading API is running"));
    }

    @DisplayName("Test ticker case insensitivity")
    @Test
    public void testTickerCaseInsensitivity() throws Exception {
        // Setup mock
        List<ClusterBuy> appleCluster = testClusterBuys.stream()
            .filter(c -> "AAPL".equals(c.getTicker()))
            .toList();
        when(aggregationService.getClusterBuysByTicker(anyString(), anyInt(), anyInt()))
            .thenReturn(appleCluster);

        // Test with lowercase
        mockMvc.perform(get("/api/insider-trading/cluster-buys/ticker/aapl"))
            .andExpect(status().isOk());

        // Test with mixed case
        mockMvc.perform(get("/api/insider-trading/cluster-buys/ticker/AaPl"))
            .andExpect(status().isOk());
    }

    @DisplayName("Test empty results")
    @Test
    public void testEmptyResults() throws Exception {
        // Setup mock - empty page
        Page<ClusterBuy> emptyPage = new PageImpl<>(new ArrayList<>());
        when(aggregationService.getLatestClusterBuys(anyInt(), anyInt(), any(Pageable.class)))
            .thenReturn(emptyPage);

        // Execute and verify
        mockMvc.perform(get("/api/insider-trading/cluster-buys/latest"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content").isEmpty())
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    /**
     * Create test ClusterBuy data
     */
    private List<ClusterBuy> createTestClusterBuys() {
        List<ClusterBuy> clusters = new ArrayList<>();

        ClusterBuy appleCluster = ClusterBuy.builder()
            .filingDate(LocalDateTime.of(2025, 11, 4, 17, 30, 0))
            .tradeDate(LocalDate.of(2025, 11, 4))
            .ticker("AAPL")
            .companyName("Apple Inc.")
            .companyCik("0000320193")
            .insiderCount(3)
            .tradeType("P - Purchase")
            .averagePrice(new BigDecimal("150.50"))
            .totalQuantity(new BigDecimal("5000"))
            .totalValue(new BigDecimal("752500.00"))
            .averageOwnershipChange(new BigDecimal("15.00"))
            .insiderRoles("D,O")
            .hasDirectorBuys(true)
            .hasOfficerBuys(true)
            .build();

        clusters.add(appleCluster);

        ClusterBuy msftCluster = ClusterBuy.builder()
            .filingDate(LocalDateTime.of(2025, 11, 3, 16, 0, 0))
            .tradeDate(LocalDate.of(2025, 11, 3))
            .ticker("MSFT")
            .companyName("Microsoft Corp")
            .companyCik("0000789019")
            .insiderCount(2)
            .tradeType("P - Purchase")
            .averagePrice(new BigDecimal("380.00"))
            .totalQuantity(new BigDecimal("1000"))
            .totalValue(new BigDecimal("380000.00"))
            .averageOwnershipChange(new BigDecimal("10.00"))
            .insiderRoles("D")
            .hasDirectorBuys(true)
            .build();

        clusters.add(msftCluster);

        return clusters;
    }

    /**
     * Create test InsiderBuy data
     */
    private List<InsiderBuy> createTestInsiderBuys() {
        List<InsiderBuy> buys = new ArrayList<>();

        InsiderBuy buy1 = InsiderBuy.builder()
            .accessionNumber("0001626431-25-000001")
            .filingDate(LocalDateTime.of(2025, 11, 4, 17, 30, 0))
            .tradeDate(LocalDate.of(2025, 11, 4))
            .ticker("AAPL")
            .companyName("Apple Inc.")
            .companyCik("0000320193")
            .insiderName("John Doe")
            .insiderCik("0001111111")
            .insiderTitle("Director")
            .tradeType("P")
            .pricePerShare(new BigDecimal("150.00"))
            .quantity(new BigDecimal("1000"))
            .sharesOwnedAfter(new BigDecimal("10000"))
            .ownershipChangePercent(new BigDecimal("11.11"))
            .transactionValue(new BigDecimal("150000.00"))
            .ownershipType("D")
            .securityTitle("Common Stock")
            .build();

        InsiderBuy buy2 = InsiderBuy.builder()
            .accessionNumber("0001626431-25-000002")
            .filingDate(LocalDateTime.of(2025, 11, 4, 18, 0, 0))
            .tradeDate(LocalDate.of(2025, 11, 4))
            .ticker("AAPL")
            .companyName("Apple Inc.")
            .companyCik("0000320193")
            .insiderName("Jane Smith")
            .insiderCik("0002222222")
            .insiderTitle("CEO")
            .tradeType("P")
            .pricePerShare(new BigDecimal("151.00"))
            .quantity(new BigDecimal("2000"))
            .sharesOwnedAfter(new BigDecimal("50000"))
            .ownershipChangePercent(new BigDecimal("4.17"))
            .transactionValue(new BigDecimal("302000.00"))
            .ownershipType("D")
            .securityTitle("Common Stock")
            .build();

        buys.add(buy1);
        buys.add(buy2);

        return buys;
    }
}
