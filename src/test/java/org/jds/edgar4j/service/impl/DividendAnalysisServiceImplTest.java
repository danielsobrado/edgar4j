package org.jds.edgar4j.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jds.edgar4j.dto.response.CompanyResponse;
import org.jds.edgar4j.dto.response.DividendAlertsResponse;
import org.jds.edgar4j.dto.response.DividendComparisonResponse;
import org.jds.edgar4j.dto.response.DividendEvidenceResponse;
import org.jds.edgar4j.dto.response.DividendEventsResponse;
import org.jds.edgar4j.dto.response.DividendHistoryResponse;
import org.jds.edgar4j.dto.response.DividendMetricDefinitionResponse;
import org.jds.edgar4j.dto.response.DividendOverviewResponse;
import org.jds.edgar4j.dto.request.DividendScreenRequest;
import org.jds.edgar4j.dto.response.DividendScreenResponse;
import org.jds.edgar4j.integration.SecApiClient;
import org.jds.edgar4j.integration.SecApiConfig;
import org.jds.edgar4j.integration.SecResponseParser;
import org.jds.edgar4j.integration.model.SecCompanyFactsResponse;
import org.jds.edgar4j.model.CompanyMarketData;
import org.jds.edgar4j.model.Filling;
import org.jds.edgar4j.model.FormType;
import org.jds.edgar4j.port.FillingDataPort;
import org.jds.edgar4j.service.CompanyMarketDataService;
import org.jds.edgar4j.service.CompanyService;
import org.jds.edgar4j.service.dividend.DividendAlertsService;
import org.jds.edgar4j.service.dividend.DividendEventExtractor;
import org.jds.edgar4j.service.dividend.DividendEvidenceService;
import org.jds.edgar4j.service.dividend.DividendMetricsService;
import org.jds.edgar4j.service.dividend.DividendScreeningService;
import org.jds.edgar4j.validation.UrlAllowlistValidator;
import org.jds.edgar4j.xbrl.XbrlService;
import org.jds.edgar4j.xbrl.model.XbrlInstance;
import org.jds.edgar4j.xbrl.sec.SecFilingExtractor;
import org.jds.edgar4j.xbrl.standardization.ConceptStandardizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class DividendAnalysisServiceImplTest {

    @Mock
    private CompanyService companyService;

    @Mock
    private FillingDataPort fillingRepository;

    @Mock
    private CompanyMarketDataService companyMarketDataService;

    @Mock
    private SecApiClient secApiClient;

    @Mock
    private SecResponseParser secResponseParser;

    @Mock
    private SecApiConfig secApiConfig;

    @Mock
    private XbrlService xbrlService;

    @Mock
    private UrlAllowlistValidator urlAllowlistValidator;

    private DividendAnalysisServiceImpl dividendAnalysisService;
    private DividendEvidenceService dividendEvidenceService;
    private DividendScreeningService dividendScreeningService;

    @BeforeEach
    void setUp() {
        DividendMetricsService dividendMetricsService = new DividendMetricsService();
        DividendEventExtractor dividendEventExtractor = new DividendEventExtractor(new org.jds.edgar4j.integration.Form8KParser());
        dividendEvidenceService = new DividendEvidenceService(
                secApiClient,
                secApiConfig,
                urlAllowlistValidator,
                dividendEventExtractor);
        dividendScreeningService = new DividendScreeningService();
        DividendFilingAnalysisService dividendFilingAnalysisService = new DividendFilingAnalysisService(
                fillingRepository,
                secApiClient,
                secResponseParser,
                secApiConfig,
                xbrlService,
                urlAllowlistValidator,
                dividendMetricsService);
        dividendAnalysisService = new DividendAnalysisServiceImpl(
                companyService,
                companyMarketDataService,
                dividendFilingAnalysisService,
                dividendMetricsService,
                new DividendAlertsService(),
                dividendScreeningService,
                dividendEvidenceService);
    }

    @Test
    @DisplayName("getOverview should build a SAFE overview from company facts and XBRL filings")
    void getOverviewShouldBuildSafeOverview() {
        CompanyResponse company = CompanyResponse.builder()
                .name("Acme Corp")
                .ticker("ACME")
                .cik("0000123456")
                .sicDescription("Industrials")
                .fiscalYearEnd(1231L)
                .build();
        Filling annual = filing("0000123456", "10-K", "0000123456-26-000001", LocalDate.of(2026, 1, 30), "https://www.sec.gov/acme-10k.htm", true);
        Filling quarterly = filing("0000123456", "10-Q", "0000123456-26-000010", LocalDate.of(2026, 5, 10), "https://www.sec.gov/acme-10q.htm", true);
        Filling currentReport = filing("0000123456", "8-K", "0000123456-26-000015", LocalDate.of(2026, 2, 20), "https://www.sec.gov/acme-8k.htm", false);

        XbrlInstance annualInstance = XbrlInstance.builder().documentUri(annual.getUrl()).build();
        XbrlInstance quarterlyInstance = XbrlInstance.builder().documentUri(quarterly.getUrl()).build();

        when(companyService.getCompanyByTicker("ACME")).thenReturn(Optional.of(company));
        when(fillingRepository.findByCik(eq("0000123456"), any()))
                .thenReturn(new PageImpl<>(List.of(quarterly, currentReport, annual)));
        when(secApiClient.fetchSubmissions("0000123456")).thenThrow(new RuntimeException("skip remote"));
        when(secApiClient.fetchCompanyFacts("0000123456")).thenReturn("{\"ok\":true}");
        when(secResponseParser.parseCompanyFactsResponse("{\"ok\":true}")).thenReturn(companyFacts(
                Map.of(
                        LocalDate.of(2020, 12, 31), 1.00d,
                        LocalDate.of(2021, 12, 31), 1.04d,
                        LocalDate.of(2022, 12, 31), 1.08d,
                        LocalDate.of(2023, 12, 31), 1.13d,
                        LocalDate.of(2024, 12, 31), 1.19d,
                        LocalDate.of(2025, 12, 31), 1.26d)));
        when(companyMarketDataService.getStoredMarketData("ACME")).thenReturn(Optional.of(CompanyMarketData.builder()
                .ticker("ACME")
                .currentPrice(42d)
                .lastUpdated(Instant.parse("2026-03-14T00:00:00Z"))
                .build()));

        when(xbrlService.parseFromUrl(annual.getUrl())).thenReturn(Mono.just(annualInstance));
        when(xbrlService.parseFromUrl(quarterly.getUrl())).thenReturn(Mono.just(quarterlyInstance));
        when(xbrlService.standardize(annualInstance)).thenReturn(standardizedData(Map.of(
                "Revenue", 50_000_000_000d,
                "OperatingCashFlow", 12_000_000_000d,
                "CapitalExpenditures", 2_000_000_000d,
                "DividendsPaid", 3_000_000_000d,
                "OperatingIncome", 9_000_000_000d,
                "DepreciationAmortization", 1_000_000_000d,
                "InterestExpense", 500_000_000d,
                "DividendsPerShare", 1.26d)));
        when(xbrlService.getKeyFinancials(annualInstance)).thenReturn(bigDecimalMap(Map.of(
                "EarningsPerShareDiluted", 5.50d)));
        when(xbrlService.extractSecMetadata(annualInstance)).thenReturn(SecFilingExtractor.SecFilingMetadata.builder()
                .documentPeriodEndDate(LocalDate.of(2025, 12, 31))
                .sharesOutstanding(1_000_000_000L)
                .build());

        when(xbrlService.standardize(quarterlyInstance)).thenReturn(standardizedData(Map.of(
                "Cash", 8_000_000_000d,
                "LongTermDebt", 10_000_000_000d,
                "DebtCurrent", 2_000_000_000d,
                "TotalCurrentAssets", 15_000_000_000d,
                "TotalCurrentLiabilities", 10_000_000_000d)));
        when(xbrlService.getKeyFinancials(quarterlyInstance)).thenReturn(Map.of());
        when(xbrlService.extractSecMetadata(quarterlyInstance)).thenReturn(SecFilingExtractor.SecFilingMetadata.builder()
                .documentPeriodEndDate(LocalDate.of(2026, 3, 31))
                .sharesOutstanding(1_000_000_000L)
                .build());

        DividendOverviewResponse response = dividendAnalysisService.getOverview("ACME");

        assertNotNull(response);
        assertEquals("ACME", response.getCompany().getTicker());
        assertEquals("Industrials", response.getCompany().getSector());
        assertEquals(LocalDate.of(2026, 5, 10), response.getCompany().getLastFilingDate());
        assertEquals(DividendOverviewResponse.DividendRating.SAFE, response.getViability().getRating());
        assertEquals(6, response.getTrend().size());
        assertEquals(1.26d, response.getSnapshot().getDpsLatest(), 0.000001d);
        assertEquals(0.300000d, response.getSnapshot().getFcfPayoutRatio(), 0.000001d);
        assertEquals(6, response.getSnapshot().getUninterruptedYears());
        assertEquals(5, response.getSnapshot().getConsecutiveRaises());
        assertEquals(0.400000d, response.getSnapshot().getNetDebtToEbitda(), 0.000001d);
        assertEquals(18.000000d, response.getSnapshot().getInterestCoverage(), 0.000001d);
        assertEquals(1.500000d, response.getSnapshot().getCurrentRatio(), 0.000001d);
        assertEquals(0.200000d, response.getSnapshot().getFcfMargin(), 0.000001d);
        assertEquals(0.030000d, response.getSnapshot().getDividendYield(), 0.000001d);
        assertEquals(42d, response.getReferencePrice(), 0.000001d);
        assertEquals(DividendOverviewResponse.MetricConfidence.HIGH, response.getConfidence().get("dpsLatest"));
        assertEquals("10-K", response.getEvidence().getLatestAnnualReport().getFormType());
        assertEquals("8-K", response.getEvidence().getLatestCurrentReport().getFormType());
        assertTrue(response.getWarnings().stream().anyMatch(warning -> warning.contains("Fewer than six annual")));
    }

    @Test
    @DisplayName("getOverview should fall back to filing-derived dividend metrics when company facts are unavailable")
    void getOverviewShouldFallBackWhenCompanyFactsUnavailable() {
        CompanyResponse company = CompanyResponse.builder()
                .name("Fallback Co")
                .ticker("FBCK")
                .cik("0000654321")
                .fiscalYearEnd(1231L)
                .build();
        Filling annual = filing("0000654321", "10-K", "0000654321-26-000001", LocalDate.of(2026, 2, 14), "https://www.sec.gov/fbck-10k.htm", true);
        XbrlInstance annualInstance = XbrlInstance.builder().documentUri(annual.getUrl()).build();

        when(companyService.getCompanyByTicker("FBCK")).thenReturn(Optional.of(company));
        when(fillingRepository.findByCik(eq("0000654321"), any()))
                .thenReturn(new PageImpl<>(List.of(annual)));
        when(secApiClient.fetchSubmissions("0000654321")).thenThrow(new RuntimeException("skip remote"));
        when(secApiClient.fetchCompanyFacts("0000654321")).thenThrow(new RuntimeException("no company facts"));
        when(companyMarketDataService.getStoredMarketData("FBCK")).thenReturn(Optional.empty());

        when(xbrlService.parseFromUrl(annual.getUrl())).thenReturn(Mono.just(annualInstance));
        when(xbrlService.standardize(annualInstance)).thenReturn(standardizedData(Map.of(
                "DividendsPaid", 500d,
                "Revenue", 2_000d,
                "OperatingCashFlow", 900d,
                "CapitalExpenditures", 100d)));
        when(xbrlService.getKeyFinancials(annualInstance)).thenReturn(Map.of());
        when(xbrlService.extractSecMetadata(annualInstance)).thenReturn(SecFilingExtractor.SecFilingMetadata.builder()
                .documentPeriodEndDate(LocalDate.of(2025, 12, 31))
                .sharesOutstanding(100L)
                .build());

        DividendOverviewResponse response = dividendAnalysisService.getOverview("FBCK");

        assertNotNull(response);
        assertEquals(5.000000d, response.getSnapshot().getDpsLatest(), 0.000001d);
        assertEquals(1, response.getSnapshot().getUninterruptedYears());
        assertEquals(DividendOverviewResponse.MetricConfidence.LOW_MEDIUM, response.getConfidence().get("dpsLatest"));
        assertTrue(response.getWarnings().stream().anyMatch(warning -> warning.contains("companyfacts dividend history was unavailable")));
        assertTrue(response.getWarnings().stream().anyMatch(warning -> warning.contains("market-price data is unavailable")));
    }

    @Test
    @DisplayName("getHistory should return requested annual metric series and rows")
    void getHistoryShouldReturnRequestedMetricSeriesAndRows() {
        CompanyResponse company = CompanyResponse.builder()
                .name("History Co")
                .ticker("HIST")
                .cik("0000123456")
                .fiscalYearEnd(1231L)
                .build();
        Filling annual2025 = filing("0000123456", "10-K", "0000123456-26-000101", LocalDate.of(2026, 2, 14), "https://www.sec.gov/hist-2025-10k.htm", true);
        Filling annual2024 = filing("0000123456", "10-K", "0000123456-25-000101", LocalDate.of(2025, 2, 14), "https://www.sec.gov/hist-2024-10k.htm", true);
        Filling annual2023 = filing("0000123456", "10-K", "0000123456-24-000101", LocalDate.of(2024, 2, 14), "https://www.sec.gov/hist-2023-10k.htm", true);

        XbrlInstance annual2025Instance = XbrlInstance.builder().documentUri(annual2025.getUrl()).build();
        XbrlInstance annual2024Instance = XbrlInstance.builder().documentUri(annual2024.getUrl()).build();
        XbrlInstance annual2023Instance = XbrlInstance.builder().documentUri(annual2023.getUrl()).build();

        when(companyService.getCompanyByTicker("HIST")).thenReturn(Optional.of(company));
        when(fillingRepository.findByCik(eq("0000123456"), any()))
                .thenReturn(new PageImpl<>(List.of(annual2025, annual2024, annual2023)));
        when(secApiClient.fetchSubmissions("0000123456")).thenThrow(new RuntimeException("skip remote"));
        when(secApiClient.fetchCompanyFacts("0000123456")).thenReturn("{\"ok\":true}");
        when(secResponseParser.parseCompanyFactsResponse("{\"ok\":true}")).thenReturn(companyFacts(
                Map.of(
                        LocalDate.of(2023, 12, 31), 0.80d,
                        LocalDate.of(2024, 12, 31), 0.88d,
                        LocalDate.of(2025, 12, 31), 0.95d)));
        when(companyMarketDataService.getStoredMarketData("HIST")).thenReturn(Optional.empty());

        when(xbrlService.parseFromUrl(annual2025.getUrl())).thenReturn(Mono.just(annual2025Instance));
        when(xbrlService.parseFromUrl(annual2024.getUrl())).thenReturn(Mono.just(annual2024Instance));
        when(xbrlService.parseFromUrl(annual2023.getUrl())).thenReturn(Mono.just(annual2023Instance));

        when(xbrlService.standardize(annual2025Instance)).thenReturn(standardizedData(doubles(
                "Revenue", 1_500d,
                "OperatingCashFlow", 600d,
                "CapitalExpenditures", 100d,
                "DividendsPaid", 240d,
                "OperatingIncome", 450d,
                "DepreciationAmortization", 50d,
                "InterestExpense", 30d,
                "Cash", 400d,
                "LongTermDebt", 500d,
                "DebtCurrent", 100d,
                "TotalCurrentAssets", 700d,
                "TotalCurrentLiabilities", 350d)));
        when(xbrlService.standardize(annual2024Instance)).thenReturn(standardizedData(doubles(
                "Revenue", 1_350d,
                "OperatingCashFlow", 500d,
                "CapitalExpenditures", 90d,
                "DividendsPaid", 220d,
                "OperatingIncome", 390d,
                "DepreciationAmortization", 45d,
                "InterestExpense", 32d,
                "Cash", 350d,
                "LongTermDebt", 540d,
                "DebtCurrent", 120d,
                "TotalCurrentAssets", 640d,
                "TotalCurrentLiabilities", 340d)));
        when(xbrlService.standardize(annual2023Instance)).thenReturn(standardizedData(doubles(
                "Revenue", 1_200d,
                "OperatingCashFlow", 450d,
                "CapitalExpenditures", 80d,
                "DividendsPaid", 200d,
                "OperatingIncome", 340d,
                "DepreciationAmortization", 40d,
                "InterestExpense", 35d,
                "Cash", 300d,
                "LongTermDebt", 580d,
                "DebtCurrent", 140d,
                "TotalCurrentAssets", 600d,
                "TotalCurrentLiabilities", 330d)));

        when(xbrlService.getKeyFinancials(annual2025Instance)).thenReturn(bigDecimalMap(Map.of("EarningsPerShareDiluted", 2.10d)));
        when(xbrlService.getKeyFinancials(annual2024Instance)).thenReturn(bigDecimalMap(Map.of("EarningsPerShareDiluted", 1.95d)));
        when(xbrlService.getKeyFinancials(annual2023Instance)).thenReturn(bigDecimalMap(Map.of("EarningsPerShareDiluted", 1.80d)));
        when(xbrlService.extractSecMetadata(annual2025Instance)).thenReturn(SecFilingExtractor.SecFilingMetadata.builder()
                .documentPeriodEndDate(LocalDate.of(2025, 12, 31))
                .sharesOutstanding(250L)
                .build());
        when(xbrlService.extractSecMetadata(annual2024Instance)).thenReturn(SecFilingExtractor.SecFilingMetadata.builder()
                .documentPeriodEndDate(LocalDate.of(2024, 12, 31))
                .sharesOutstanding(250L)
                .build());
        when(xbrlService.extractSecMetadata(annual2023Instance)).thenReturn(SecFilingExtractor.SecFilingMetadata.builder()
                .documentPeriodEndDate(LocalDate.of(2023, 12, 31))
                .sharesOutstanding(250L)
                .build());

        DividendHistoryResponse response = dividendAnalysisService.getHistory(
                "HIST",
                List.of("dps_declared", "fcf_payout", "interest_coverage"),
                "FY",
                2);

        assertNotNull(response);
        assertEquals("FY", response.getPeriod());
        assertEquals(2, response.getYearsRequested());
        assertEquals(List.of("dps_declared", "fcf_payout", "interest_coverage"), response.getMetrics());
        assertEquals(2, response.getRows().size());
        assertEquals(LocalDate.of(2024, 12, 31), response.getRows().get(0).getPeriodEnd());
        assertEquals(LocalDate.of(2025, 12, 31), response.getRows().get(1).getPeriodEnd());
        assertEquals(0.95d, response.getSeries().get(0).getLatestValue(), 0.000001d);
        assertEquals(0.480000d, response.getRows().get(1).getMetrics().get("fcf_payout"), 0.000001d);
        assertEquals(15.000000d, response.getRows().get(1).getMetrics().get("interest_coverage"), 0.000001d);
        assertEquals(DividendHistoryResponse.TrendDirection.UP, response.getSeries().get(0).getTrend());
    }

    @Test
    @DisplayName("getAlerts should return active alerts and historical alert events")
    void getAlertsShouldReturnActiveAndHistoricalAlerts() {
        CompanyResponse company = CompanyResponse.builder()
                .name("Alerts Co")
                .ticker("ALRT")
                .cik("0000123456")
                .fiscalYearEnd(1231L)
                .build();
        Filling annual2025 = filing("0000123456", "10-K", "0000123456-26-000201", LocalDate.of(2026, 2, 20), "https://www.sec.gov/alrt-2025-10k.htm", true);
        Filling annual2024 = filing("0000123456", "10-K", "0000123456-25-000201", LocalDate.of(2025, 2, 20), "https://www.sec.gov/alrt-2024-10k.htm", true);

        XbrlInstance annual2025Instance = XbrlInstance.builder().documentUri(annual2025.getUrl()).build();
        XbrlInstance annual2024Instance = XbrlInstance.builder().documentUri(annual2024.getUrl()).build();

        when(companyService.getCompanyByTicker("ALRT")).thenReturn(Optional.of(company));
        when(fillingRepository.findByCik(eq("0000123456"), any()))
                .thenReturn(new PageImpl<>(List.of(annual2025, annual2024)));
        when(secApiClient.fetchSubmissions("0000123456")).thenThrow(new RuntimeException("skip remote"));
        when(secApiClient.fetchCompanyFacts("0000123456")).thenReturn("{\"ok\":true}");
        when(secResponseParser.parseCompanyFactsResponse("{\"ok\":true}")).thenReturn(companyFacts(
                Map.of(
                        LocalDate.of(2024, 12, 31), 1.20d,
                        LocalDate.of(2025, 12, 31), 1.00d)));
        when(companyMarketDataService.getStoredMarketData("ALRT")).thenReturn(Optional.empty());

        when(xbrlService.parseFromUrl(annual2025.getUrl())).thenReturn(Mono.just(annual2025Instance));
        when(xbrlService.parseFromUrl(annual2024.getUrl())).thenReturn(Mono.just(annual2024Instance));

        when(xbrlService.standardize(annual2025Instance)).thenReturn(standardizedData(doubles(
                "Revenue", 1_000d,
                "OperatingCashFlow", 1_000d,
                "CapitalExpenditures", 100d,
                "DividendsPaid", 1_200d,
                "OperatingIncome", 300d,
                "DepreciationAmortization", 200d,
                "InterestExpense", 200d,
                "Cash", 100d,
                "LongTermDebt", 6_000d,
                "DebtCurrent", 1_000d,
                "TotalCurrentAssets", 700d,
                "TotalCurrentLiabilities", 1_000d)));
        when(xbrlService.standardize(annual2024Instance)).thenReturn(standardizedData(doubles(
                "Revenue", 1_000d,
                "OperatingCashFlow", 900d,
                "CapitalExpenditures", 100d,
                "DividendsPaid", 800d,
                "OperatingIncome", 500d,
                "DepreciationAmortization", 100d,
                "InterestExpense", 40d,
                "Cash", 500d,
                "LongTermDebt", 1_000d,
                "DebtCurrent", 100d,
                "TotalCurrentAssets", 1_200d,
                "TotalCurrentLiabilities", 700d)));

        when(xbrlService.getKeyFinancials(annual2025Instance)).thenReturn(bigDecimalMap(Map.of("EarningsPerShareDiluted", 1.20d)));
        when(xbrlService.getKeyFinancials(annual2024Instance)).thenReturn(bigDecimalMap(Map.of("EarningsPerShareDiluted", 1.50d)));
        when(xbrlService.extractSecMetadata(annual2025Instance)).thenReturn(SecFilingExtractor.SecFilingMetadata.builder()
                .documentPeriodEndDate(LocalDate.of(2025, 12, 31))
                .sharesOutstanding(1_000L)
                .build());
        when(xbrlService.extractSecMetadata(annual2024Instance)).thenReturn(SecFilingExtractor.SecFilingMetadata.builder()
                .documentPeriodEndDate(LocalDate.of(2024, 12, 31))
                .sharesOutstanding(1_000L)
                .build());

        DividendAlertsResponse response = dividendAnalysisService.getAlerts("ALRT", false);
        DividendAlertsResponse activeOnlyResponse = dividendAnalysisService.getAlerts("ALRT", true);

        assertNotNull(response);
        assertEquals(5, response.getActiveAlerts().size());
        assertTrue(response.getHistoricalAlerts().stream().anyMatch(event -> "dividend-cut".equals(event.getId()) && event.isActive()));
        assertTrue(response.getHistoricalAlerts().stream().anyMatch(event -> "fcf-payout".equals(event.getId()) && event.isActive()));
        assertTrue(response.getHistoricalAlerts().stream().anyMatch(event -> "current-ratio".equals(event.getId()) && event.isActive()));
        assertTrue(response.getHistoricalAlerts().stream().anyMatch(event -> "net-debt-to-ebitda".equals(event.getId()) && event.isActive()));
        assertTrue(response.getHistoricalAlerts().stream().anyMatch(event -> "interest-coverage".equals(event.getId()) && event.isActive()));
        assertEquals(response.getActiveAlerts().size(), activeOnlyResponse.getHistoricalAlerts().size());
        assertTrue(activeOnlyResponse.getHistoricalAlerts().stream().allMatch(DividendAlertsResponse.AlertEvent::isActive));
    }

    @Test
    @DisplayName("getEvents should extract dividend declarations from 8-K filing text")
    void getEventsShouldExtractDividendDeclarationsFromFilingText() {
        CompanyResponse company = CompanyResponse.builder()
                .name("Events Co")
                .ticker("EVNT")
                .cik("0000123456")
                .fiscalYearEnd(1231L)
                .build();
        Filling currentReport = filing("0000123456", "8-K", "0000123456-26-000301", LocalDate.of(2026, 1, 31), "https://www.sec.gov/Archives/edgar/data/123456/000012345626000301/d8k.htm", false);
        currentReport.setPrimaryDocument("d8k.htm");
        Filling annual = filing("0000123456", "10-K", "0000123456-26-000201", LocalDate.of(2026, 2, 15), "https://www.sec.gov/Archives/edgar/data/123456/000012345626000201/d10k.htm", true);
        annual.setPrimaryDocument("d10k.htm");

        when(companyService.getCompanyByTicker("EVNT")).thenReturn(Optional.of(company));
        when(fillingRepository.findByCik(eq("0000123456"), any()))
                .thenReturn(new PageImpl<>(List.of(annual, currentReport)));
        when(secApiClient.fetchSubmissions("0000123456")).thenThrow(new RuntimeException("skip remote"));
        when(secApiClient.fetchFiling("0000123456", "0000123456-26-000301", "d8k.htm")).thenReturn("""
                <html><body>
                <h2>Item 8.01 Other Events</h2>
                <p>On January 30, 2026, the Board of Directors declared a quarterly cash dividend of $0.42 per share.
                The dividend is payable on February 28, 2026 to stockholders of record on February 14, 2026.</p>
                </body></html>
                """);
        when(secApiClient.fetchFiling("0000123456", "0000123456-26-000201", "d10k.htm")).thenReturn("""
                <html><body>
                <p>Future dividends remain at the discretion of the board and depend on earnings, capital requirements, and debt covenants.</p>
                </body></html>
                """);

        DividendEventsResponse response = dividendAnalysisService.getEvents("EVNT", null);

        assertNotNull(response);
        assertEquals("EVNT", response.getCompany().getTicker());
        assertTrue(response.getEvents().stream().anyMatch(event ->
                event.getEventType() == DividendEventsResponse.EventType.DECLARATION
                        && event.getAmountPerShare() != null
                        && Math.abs(event.getAmountPerShare() - 0.42d) < 0.000001d
                        && event.getRecordDate().equals(LocalDate.of(2026, 2, 14))
                        && event.getPayableDate().equals(LocalDate.of(2026, 2, 28))));
        assertTrue(response.getEvents().stream().anyMatch(event ->
                event.getEventType() == DividendEventsResponse.EventType.POLICY_CHANGE
                        && event.getPolicyLanguage() != null
                        && event.getPolicyLanguage().contains("discretion of the board")));
    }

    @Test
    @DisplayName("compare should return requested peer metrics")
    void compareShouldReturnRequestedPeerMetrics() {
        CompanyResponse apple = CompanyResponse.builder()
                .name("Apple Inc.")
                .ticker("AAPL")
                .cik("0000320193")
                .sicDescription("Technology")
                .fiscalYearEnd(930L)
                .build();
        CompanyResponse microsoft = CompanyResponse.builder()
                .name("Microsoft Corp.")
                .ticker("MSFT")
                .cik("0000789019")
                .sicDescription("Technology")
                .fiscalYearEnd(630L)
                .build();

        Filling appleAnnual = filing("0000320193", "10-K", "0000320193-25-000106", LocalDate.of(2025, 11, 1), "https://www.sec.gov/aapl-10k.htm", true);
        Filling microsoftAnnual = filing("0000789019", "10-K", "0000789019-25-000088", LocalDate.of(2025, 10, 20), "https://www.sec.gov/msft-10k.htm", true);
        XbrlInstance appleInstance = XbrlInstance.builder().documentUri(appleAnnual.getUrl()).build();
        XbrlInstance microsoftInstance = XbrlInstance.builder().documentUri(microsoftAnnual.getUrl()).build();

        when(companyService.getCompanyByTicker("AAPL")).thenReturn(Optional.of(apple));
        when(companyService.getCompanyByTicker("MSFT")).thenReturn(Optional.of(microsoft));
        when(fillingRepository.findByCik(eq("0000320193"), any()))
                .thenReturn(new PageImpl<>(List.of(appleAnnual)));
        when(fillingRepository.findByCik(eq("0000789019"), any()))
                .thenReturn(new PageImpl<>(List.of(microsoftAnnual)));
        when(secApiClient.fetchSubmissions("0000320193")).thenThrow(new RuntimeException("skip remote"));
        when(secApiClient.fetchSubmissions("0000789019")).thenThrow(new RuntimeException("skip remote"));
        when(secApiClient.fetchCompanyFacts("0000320193")).thenThrow(new RuntimeException("skip company facts"));
        when(secApiClient.fetchCompanyFacts("0000789019")).thenThrow(new RuntimeException("skip company facts"));
        when(companyMarketDataService.getStoredMarketData("AAPL")).thenReturn(Optional.empty());
        when(companyMarketDataService.getStoredMarketData("MSFT")).thenReturn(Optional.empty());

        when(xbrlService.parseFromUrl(appleAnnual.getUrl())).thenReturn(Mono.just(appleInstance));
        when(xbrlService.parseFromUrl(microsoftAnnual.getUrl())).thenReturn(Mono.just(microsoftInstance));
        when(xbrlService.standardize(appleInstance)).thenReturn(standardizedData(doubles(
                "Revenue", 1_000d,
                "OperatingCashFlow", 400d,
                "CapitalExpenditures", 100d,
                "DividendsPaid", 60d,
                "OperatingIncome", 200d,
                "DepreciationAmortization", 50d,
                "InterestExpense", 20d,
                "Cash", 300d,
                "LongTermDebt", 200d,
                "DebtCurrent", 50d,
                "TotalCurrentAssets", 600d,
                "TotalCurrentLiabilities", 300d)));
        when(xbrlService.standardize(microsoftInstance)).thenReturn(standardizedData(doubles(
                "Revenue", 1_000d,
                "OperatingCashFlow", 300d,
                "CapitalExpenditures", 100d,
                "DividendsPaid", 140d,
                "OperatingIncome", 120d,
                "DepreciationAmortization", 30d,
                "InterestExpense", 30d,
                "Cash", 90d,
                "LongTermDebt", 500d,
                "DebtCurrent", 100d,
                "TotalCurrentAssets", 180d,
                "TotalCurrentLiabilities", 200d)));
        when(xbrlService.getKeyFinancials(appleInstance)).thenReturn(bigDecimalMap(Map.of("EarningsPerShareDiluted", 6.42d)));
        when(xbrlService.getKeyFinancials(microsoftInstance)).thenReturn(bigDecimalMap(Map.of("EarningsPerShareDiluted", 4.10d)));
        when(xbrlService.extractSecMetadata(appleInstance)).thenReturn(SecFilingExtractor.SecFilingMetadata.builder()
                .documentPeriodEndDate(LocalDate.of(2025, 9, 27))
                .sharesOutstanding(1_000L)
                .build());
        when(xbrlService.extractSecMetadata(microsoftInstance)).thenReturn(SecFilingExtractor.SecFilingMetadata.builder()
                .documentPeriodEndDate(LocalDate.of(2025, 6, 30))
                .sharesOutstanding(1_000L)
                .build());

        DividendComparisonResponse response = dividendAnalysisService.compare(
                List.of("AAPL", "MSFT"),
                List.of("fcf_payout", "current_ratio", "score"));

        assertNotNull(response);
        assertEquals(3, response.getMetrics().size());
        assertEquals(2, response.getCompanies().size());
        assertEquals("AAPL", response.getCompanies().get(0).getCompany().getTicker());
        assertEquals(0.200000d, response.getCompanies().get(0).getValues().get("fcf_payout"), 0.000001d);
        assertEquals(2.000000d, response.getCompanies().get(0).getValues().get("current_ratio"), 0.000001d);
        assertNotNull(response.getCompanies().get(0).getValues().get("score"));
        assertEquals("MSFT", response.getCompanies().get(1).getCompany().getTicker());
        assertEquals(0.700000d, response.getCompanies().get(1).getValues().get("fcf_payout"), 0.000001d);
        assertEquals(0.900000d, response.getCompanies().get(1).getValues().get("current_ratio"), 0.000001d);
        assertTrue(response.getWarnings().isEmpty());
    }

    @Test
    @DisplayName("getMetricDefinitions should expose overview and history metrics")
    void getMetricDefinitionsShouldExposeOverviewAndHistoryMetrics() {
        List<DividendMetricDefinitionResponse> response = dividendAnalysisService.getMetricDefinitions();

        assertNotNull(response);
        assertTrue(response.stream().anyMatch(metric ->
                "dps_cagr_5y".equals(metric.getId())
                        && "overview".equals(metric.getGroup())
                        && "percent".equals(metric.getFormatHint())));
        assertTrue(response.stream().anyMatch(metric ->
                "dps_declared".equals(metric.getId())
                        && "history".equals(metric.getGroup())
                        && "currency".equals(metric.getFormatHint())));
    }

    @Test
    @DisplayName("screen should filter and sort results using the requested dividend criteria")
    void screenShouldFilterAndSortResults() {
        CompanyResponse apple = CompanyResponse.builder()
                .name("Apple Inc.")
                .ticker("AAPL")
                .cik("0000320193")
                .sicDescription("Technology")
                .fiscalYearEnd(930L)
                .build();
        CompanyResponse microsoft = CompanyResponse.builder()
                .name("Microsoft Corp.")
                .ticker("MSFT")
                .cik("0000789019")
                .sicDescription("Technology")
                .fiscalYearEnd(630L)
                .build();

        Filling appleAnnual = filing("0000320193", "10-K", "0000320193-25-000106", LocalDate.of(2025, 11, 1), "https://www.sec.gov/aapl-10k.htm", true);
        Filling microsoftAnnual = filing("0000789019", "10-K", "0000789019-25-000088", LocalDate.of(2025, 10, 20), "https://www.sec.gov/msft-10k.htm", true);
        XbrlInstance appleInstance = XbrlInstance.builder().documentUri(appleAnnual.getUrl()).build();
        XbrlInstance microsoftInstance = XbrlInstance.builder().documentUri(microsoftAnnual.getUrl()).build();

        when(companyService.getCompanyByTicker("AAPL")).thenReturn(Optional.of(apple));
        when(companyService.getCompanyByTicker("MSFT")).thenReturn(Optional.of(microsoft));
        when(fillingRepository.findByCik(eq("0000320193"), any()))
                .thenReturn(new PageImpl<>(List.of(appleAnnual)));
        when(fillingRepository.findByCik(eq("0000789019"), any()))
                .thenReturn(new PageImpl<>(List.of(microsoftAnnual)));
        when(secApiClient.fetchSubmissions("0000320193")).thenThrow(new RuntimeException("skip remote"));
        when(secApiClient.fetchSubmissions("0000789019")).thenThrow(new RuntimeException("skip remote"));
        when(secApiClient.fetchCompanyFacts("0000320193")).thenThrow(new RuntimeException("skip company facts"));
        when(secApiClient.fetchCompanyFacts("0000789019")).thenThrow(new RuntimeException("skip company facts"));
        when(companyMarketDataService.getStoredMarketData("AAPL")).thenReturn(Optional.empty());
        when(companyMarketDataService.getStoredMarketData("MSFT")).thenReturn(Optional.empty());

        when(xbrlService.parseFromUrl(appleAnnual.getUrl())).thenReturn(Mono.just(appleInstance));
        when(xbrlService.parseFromUrl(microsoftAnnual.getUrl())).thenReturn(Mono.just(microsoftInstance));
        when(xbrlService.standardize(appleInstance)).thenReturn(standardizedData(doubles(
                "Revenue", 1_000d,
                "OperatingCashFlow", 400d,
                "CapitalExpenditures", 100d,
                "DividendsPaid", 60d,
                "OperatingIncome", 200d,
                "DepreciationAmortization", 50d,
                "InterestExpense", 20d,
                "Cash", 300d,
                "LongTermDebt", 200d,
                "DebtCurrent", 50d,
                "TotalCurrentAssets", 600d,
                "TotalCurrentLiabilities", 300d)));
        when(xbrlService.standardize(microsoftInstance)).thenReturn(standardizedData(doubles(
                "Revenue", 1_000d,
                "OperatingCashFlow", 300d,
                "CapitalExpenditures", 100d,
                "DividendsPaid", 140d,
                "OperatingIncome", 120d,
                "DepreciationAmortization", 30d,
                "InterestExpense", 30d,
                "Cash", 90d,
                "LongTermDebt", 500d,
                "DebtCurrent", 100d,
                "TotalCurrentAssets", 180d,
                "TotalCurrentLiabilities", 200d)));
        when(xbrlService.getKeyFinancials(appleInstance)).thenReturn(bigDecimalMap(Map.of("EarningsPerShareDiluted", 6.42d)));
        when(xbrlService.getKeyFinancials(microsoftInstance)).thenReturn(bigDecimalMap(Map.of("EarningsPerShareDiluted", 4.10d)));
        when(xbrlService.extractSecMetadata(appleInstance)).thenReturn(SecFilingExtractor.SecFilingMetadata.builder()
                .documentPeriodEndDate(LocalDate.of(2025, 9, 27))
                .sharesOutstanding(1_000L)
                .build());
        when(xbrlService.extractSecMetadata(microsoftInstance)).thenReturn(SecFilingExtractor.SecFilingMetadata.builder()
                .documentPeriodEndDate(LocalDate.of(2025, 6, 30))
                .sharesOutstanding(1_000L)
                .build());

        DividendScreenRequest request = DividendScreenRequest.builder()
                .tickersOrCiks(List.of("AAPL", "MSFT"))
                .filters(DividendScreenRequest.DividendScreenFilters.builder()
                        .metrics(Map.of(
                                "fcf_payout", DividendScreenRequest.MetricRange.builder().max(0.50d).build(),
                                "current_ratio", DividendScreenRequest.MetricRange.builder().min(1.0d).build()))
                        .viabilityRatings(List.of(DividendOverviewResponse.DividendRating.SAFE))
                        .sectors(List.of("Technology"))
                        .build())
                .metrics(List.of("fcf_payout", "current_ratio"))
                .sort("fcf_payout")
                .direction("ASC")
                .page(0)
                .size(10)
                .candidateLimit(10)
                .build();

        DividendScreenResponse response = dividendAnalysisService.screen(request);

        assertNotNull(response);
        assertEquals(2, response.getCandidatesEvaluated());
        assertEquals(2, response.getMetrics().size());
        assertEquals(1, response.getResults().getContent().size());
        assertEquals("AAPL", response.getResults().getContent().get(0).getCompany().getTicker());
        assertEquals(0.200000d, response.getResults().getContent().get(0).getValues().get("fcf_payout"), 0.000001d);
        assertEquals(2.000000d, response.getResults().getContent().get(0).getValues().get("current_ratio"), 0.000001d);
        assertTrue(response.getWarnings().isEmpty());
    }

    @Test
    @DisplayName("getEvidence should return filing highlights and a cleaned text preview")
    void getEvidenceShouldReturnFilingHighlightsAndCleanedText() {
        CompanyResponse company = CompanyResponse.builder()
                .name("Evidence Co")
                .ticker("EVDC")
                .cik("0000123456")
                .fiscalYearEnd(1231L)
                .build();
        Filling currentReport = filing("0000123456", "8-K", "0000123456-26-000401", LocalDate.of(2026, 2, 2), "https://www.sec.gov/Archives/edgar/data/123456/000012345626000401/d8k.htm", false);
        currentReport.setPrimaryDocument("d8k.htm");

        when(companyService.getCompanyByTicker("EVDC")).thenReturn(Optional.of(company));
        when(fillingRepository.findByCik(eq("0000123456"), any()))
                .thenReturn(new PageImpl<>(List.of(currentReport)));
        when(secApiClient.fetchSubmissions("0000123456")).thenThrow(new RuntimeException("skip remote"));
        when(secApiClient.fetchFiling("0000123456", "0000123456-26-000401", "d8k.htm")).thenReturn("""
                <html><body>
                <h2>Item 8.01 Other Events</h2>
                <p>On January 30, 2026, the Board of Directors declared a quarterly cash dividend of $0.42 per share.
                The dividend is payable on February 28, 2026 to stockholders of record on February 14, 2026.</p>
                </body></html>
                """);

        DividendEvidenceResponse response = dividendAnalysisService.getEvidence("EVDC", "0000123456-26-000401");

        assertNotNull(response);
        assertEquals("EVDC", response.getCompany().getTicker());
        assertEquals("8-K", response.getFiling().getFormType());
        assertEquals("0000123456-26-000401", response.getFiling().getAccessionNumber());
        assertFalse(response.isTruncated());
        assertTrue(response.getHighlights().stream().anyMatch(highlight ->
                highlight.getEventType() == DividendEventsResponse.EventType.DECLARATION
                        && highlight.getSnippet() != null
                        && highlight.getSnippet().contains("$0.42 per share")));
        assertTrue(response.getCleanedText().contains("Board of Directors declared a quarterly cash dividend"));
        assertTrue(response.getWarnings().isEmpty());
    }

    private Filling filing(
            String cik,
            String formType,
            String accessionNumber,
            LocalDate filingDate,
            String url,
            boolean xbrl) {
        return Filling.builder()
                .cik(cik)
                .accessionNumber(accessionNumber)
                .formType(FormType.builder().number(formType).build())
                .fillingDate(date(filingDate))
                .reportDate(date(filingDate.minusDays(30)))
                .url(url)
                .isXBRL(xbrl)
                .isInlineXBRL(xbrl)
                .build();
    }

    private Date date(LocalDate value) {
        return Date.from(value.atStartOfDay().toInstant(ZoneOffset.UTC));
    }

    private SecCompanyFactsResponse companyFacts(Map<LocalDate, Double> annualDividendsPerShare) {
        List<SecCompanyFactsResponse.FactEntry> entries = annualDividendsPerShare.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> SecCompanyFactsResponse.FactEntry.builder()
                        .end(entry.getKey().toString())
                        .filed(entry.getKey().plusMonths(2).toString())
                        .val(BigDecimal.valueOf(entry.getValue()))
                        .accn("ACCN-" + entry.getKey())
                        .fp("FY")
                        .form("10-K")
                        .build())
                .toList();

        return SecCompanyFactsResponse.builder()
                .cik("0000123456")
                .entityName("Acme Corp")
                .facts(Map.of(
                        "us-gaap", Map.of(
                                "CommonStockDividendsPerShareDeclared", SecCompanyFactsResponse.ConceptFacts.builder()
                                        .units(Map.of("USD/shares", entries))
                                        .build())))
                .build();
    }

    private ConceptStandardizer.StandardizedData standardizedData(Map<String, Double> values) {
        List<ConceptStandardizer.StandardizedFact> facts = values.entrySet().stream()
                .map(entry -> ConceptStandardizer.StandardizedFact.builder()
                        .standardConcept(entry.getKey())
                        .value(BigDecimal.valueOf(entry.getValue()))
                        .build())
                .toList();

        return ConceptStandardizer.StandardizedData.builder()
                .facts(facts)
                .unmappedConcepts(List.of())
                .build();
    }

    private Map<String, BigDecimal> bigDecimalMap(Map<String, Double> values) {
        return values.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> BigDecimal.valueOf(entry.getValue())));
    }

    private Map<String, Double> doubles(Object... entries) {
        java.util.LinkedHashMap<String, Double> values = new java.util.LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            values.put((String) entries[index], (Double) entries[index + 1]);
        }
        return values;
    }
}
