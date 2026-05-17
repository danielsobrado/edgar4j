package org.jds.edgar4j.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.jds.edgar4j.integration.SecApiClient;
import org.jds.edgar4j.integration.SecApiConfig;
import org.jds.edgar4j.integration.SecResponseParser;
import org.jds.edgar4j.integration.model.SecCompanyFactsResponse;
import org.jds.edgar4j.model.NormalizedXbrlFact;
import org.jds.edgar4j.port.FillingDataPort;
import org.jds.edgar4j.port.NormalizedXbrlFactDataPort;
import org.jds.edgar4j.service.dividend.DividendMetricsService;
import org.jds.edgar4j.validation.UrlAllowlistValidator;
import org.jds.edgar4j.xbrl.XbrlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DividendFilingAnalysisServiceTest {

    @Mock
    private FillingDataPort fillingRepository;

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

    @Mock
    private NormalizedXbrlFactDataPort normalizedXbrlFactDataPort;

    private DividendFilingAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new DividendFilingAnalysisService(
                fillingRepository,
                secApiClient,
                secResponseParser,
                secApiConfig,
                xbrlService,
                urlAllowlistValidator,
                new DividendMetricsService(),
                normalizedXbrlFactDataPort);
    }

    @Test
    @DisplayName("loadDividendFactSeries should prefer stored normalized annual facts")
    void loadDividendFactSeriesShouldPreferStoredFacts() {
        String cik = "0000320193";
        when(normalizedXbrlFactDataPort.findByCikAndStandardConceptAndCurrentBestTrueOrderByPeriodEndDesc(
                cik,
                "DividendsPerShare"))
                .thenReturn(List.of(
                        normalizedFact("DividendsPerShare", "10-K", "FY", LocalDate.of(2025, 9, 27), 1.04d),
                        normalizedFact("DividendsPerShare", "10-Q", "Q3", LocalDate.of(2025, 6, 28), 0.26d)));
        when(normalizedXbrlFactDataPort.findByCikAndStandardConceptAndCurrentBestTrueOrderByPeriodEndDesc(
                cik,
                "DividendsPerShareCashPaid"))
                .thenReturn(List.of(normalizedFact(
                        "DividendsPerShareCashPaid",
                        "10-K",
                        "FY",
                        LocalDate.of(2025, 9, 27),
                        1.00d)));

        List<DividendFilingAnalysisService.DividendFactPoint> result = service.loadDividendFactSeries(cik);

        assertEquals(1, result.size());
        assertEquals(LocalDate.of(2025, 9, 27), result.get(0).periodEnd());
        assertEquals(1.04d, result.get(0).dividendsPerShare(), 0.000001d);
        assertEquals("0000320193-25-000001", result.get(0).accessionNumber());
        verify(secApiClient, never()).fetchCompanyFacts(cik);
    }

    @Test
    @DisplayName("loadDividendFactSeries should fall back to SEC companyfacts when stored facts are empty")
    void loadDividendFactSeriesShouldFallbackToCompanyFacts() {
        String cik = "0000789019";
        when(normalizedXbrlFactDataPort.findByCikAndStandardConceptAndCurrentBestTrueOrderByPeriodEndDesc(
                cik,
                "DividendsPerShare"))
                .thenReturn(List.of());
        when(normalizedXbrlFactDataPort.findByCikAndStandardConceptAndCurrentBestTrueOrderByPeriodEndDesc(
                cik,
                "DividendsPerShareCashPaid"))
                .thenReturn(List.of());
        when(secApiClient.fetchCompanyFacts(cik)).thenReturn("{\"facts\":true}");
        when(secResponseParser.parseCompanyFactsResponse("{\"facts\":true}")).thenReturn(companyFacts());

        List<DividendFilingAnalysisService.DividendFactPoint> result = service.loadDividendFactSeries(cik);

        assertEquals(1, result.size());
        assertEquals(LocalDate.of(2025, 6, 30), result.get(0).periodEnd());
        assertEquals(3.32d, result.get(0).dividendsPerShare(), 0.000001d);
        verify(secApiClient).fetchCompanyFacts(cik);
    }

    private NormalizedXbrlFact normalizedFact(
            String standardConcept,
            String form,
            String fiscalPeriod,
            LocalDate periodEnd,
            double value) {
        return NormalizedXbrlFact.builder()
                .cik("0000320193")
                .standardConcept(standardConcept)
                .form(form)
                .fiscalPeriod(fiscalPeriod)
                .periodStart(periodEnd.minusYears(1).plusDays(1))
                .periodEnd(periodEnd)
                .filedDate(periodEnd.plusMonths(1))
                .accession("0000320193-25-000001")
                .value(BigDecimal.valueOf(value))
                .currentBest(true)
                .build();
    }

    private SecCompanyFactsResponse companyFacts() {
        SecCompanyFactsResponse.FactEntry entry = SecCompanyFactsResponse.FactEntry.builder()
                .start("2024-07-01")
                .end("2025-06-30")
                .filed("2025-08-01")
                .val(BigDecimal.valueOf(3.32d))
                .accn("0000789019-25-000088")
                .fp("FY")
                .form("10-K")
                .build();

        return SecCompanyFactsResponse.builder()
                .cik("0000789019")
                .entityName("Microsoft Corp.")
                .facts(Map.of(
                        "us-gaap", Map.of(
                                "CommonStockDividendsPerShareDeclared",
                                SecCompanyFactsResponse.ConceptFacts.builder()
                                        .units(Map.of("USD/shares", List.of(entry)))
                                        .build())))
                .build();
    }
}
