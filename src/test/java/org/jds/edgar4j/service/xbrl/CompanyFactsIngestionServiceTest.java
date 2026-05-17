package org.jds.edgar4j.service.xbrl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jds.edgar4j.integration.SecApiClient;
import org.jds.edgar4j.integration.SecResponseParser;
import org.jds.edgar4j.integration.model.SecCompanyFactsResponse;
import org.jds.edgar4j.model.NormalizedXbrlFact;
import org.jds.edgar4j.port.NormalizedXbrlFactDataPort;
import org.jds.edgar4j.xbrl.standardization.ConceptStandardizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompanyFactsIngestionServiceTest {

    @Mock
    private SecApiClient secApiClient;

    @Mock
    private SecResponseParser secResponseParser;

    @Mock
    private NormalizedXbrlFactDataPort factDataPort;

    private final ConceptStandardizer conceptStandardizer = new ConceptStandardizer();

    @Test
    @DisplayName("ingest should flatten companyfacts, standardize concepts, and mark latest amendment current-best")
    void ingestShouldFlattenAndMarkCurrentBest() {
        List<NormalizedXbrlFact> storedFacts = new ArrayList<>();
        CompanyFactsIngestionService service = createService(storedFacts);
        SecCompanyFactsResponse response = companyFactsResponse();

        when(secApiClient.fetchCompanyFacts("0000320193")).thenReturn("{\"ok\":true}");
        when(secResponseParser.parseCompanyFactsResponse("{\"ok\":true}")).thenReturn(response);

        CompanyFactsIngestionService.IngestionResult result = service.ingest("320193");

        assertEquals("0000320193", result.cik());
        assertEquals(2, result.inserted());
        assertEquals(0, result.updated());
        assertEquals(2, storedFacts.size());

        NormalizedXbrlFact amendment = storedFacts.stream()
                .filter(fact -> "10-K/A".equals(fact.getForm()))
                .findFirst()
                .orElseThrow();
        NormalizedXbrlFact original = storedFacts.stream()
                .filter(fact -> "10-K".equals(fact.getForm()))
                .findFirst()
                .orElseThrow();

        assertEquals("DividendsPerShare", amendment.getStandardConcept());
        assertEquals("USD-per-shares", amendment.getUnit());
        assertEquals(LocalDate.of(2025, 9, 27), amendment.getPeriodEnd());
        assertTrue(amendment.isCurrentBest(), "latest amendment should be current best");
        assertTrue(!original.isCurrentBest(), "original filing should be superseded by amendment");
    }

    @Test
    @DisplayName("ingest should update existing deterministic fact ids on repeat runs")
    void ingestShouldUpdateExistingFactsOnRepeatRuns() {
        List<NormalizedXbrlFact> storedFacts = new ArrayList<>();
        CompanyFactsIngestionService service = createService(storedFacts);
        SecCompanyFactsResponse response = companyFactsResponse();

        when(secApiClient.fetchCompanyFacts("0000320193")).thenReturn("{\"ok\":true}");
        when(secResponseParser.parseCompanyFactsResponse("{\"ok\":true}")).thenReturn(response);

        service.ingest("320193");
        CompanyFactsIngestionService.IngestionResult secondRun = service.ingest("320193");

        assertEquals(0, secondRun.inserted());
        assertEquals(2, secondRun.updated());
        assertEquals(2, storedFacts.size());
    }

    @Test
    @DisplayName("ingest should preserve dimensional facts as separate natural keys")
    void ingestShouldPreserveDimensionalFacts() {
        List<NormalizedXbrlFact> storedFacts = new ArrayList<>();
        CompanyFactsIngestionService service = createService(storedFacts);
        SecCompanyFactsResponse response = dimensionalCompanyFactsResponse();

        when(secApiClient.fetchCompanyFacts("0000320193")).thenReturn("{\"ok\":true}");
        when(secResponseParser.parseCompanyFactsResponse("{\"ok\":true}")).thenReturn(response);

        CompanyFactsIngestionService.IngestionResult result = service.ingest("320193");

        assertEquals(2, result.inserted());
        assertEquals(2, storedFacts.size());

        NormalizedXbrlFact consolidated = storedFacts.stream()
                .filter(fact -> fact.getDimensions().isEmpty())
                .findFirst()
                .orElseThrow();
        NormalizedXbrlFact dimensional = storedFacts.stream()
                .filter(fact -> !fact.getDimensions().isEmpty())
                .findFirst()
                .orElseThrow();

        assertEquals("", consolidated.getDimensionsHash());
        assertEquals("ProductsAxis.IPhoneMember", dimensional.getDimensions().get("segment"));
        assertNotEquals("", dimensional.getDimensionsHash());
        assertNotEquals(consolidated.getId(), dimensional.getId());
        assertTrue(consolidated.isCurrentBest());
        assertTrue(dimensional.isCurrentBest());
    }

    @Test
    @DisplayName("ingestAll should deduplicate CIKs, cap the batch, and continue after failures")
    void ingestAllShouldDeduplicateCapAndContinueAfterFailures() {
        List<NormalizedXbrlFact> storedFacts = new ArrayList<>();
        CompanyFactsIngestionService service = createService(storedFacts);

        when(secApiClient.fetchCompanyFacts("0000320193")).thenReturn("{\"apple\":true}");
        when(secResponseParser.parseCompanyFactsResponse("{\"apple\":true}")).thenReturn(companyFactsResponse());
        when(secApiClient.fetchCompanyFacts("0000789019")).thenThrow(new RuntimeException("rate limited"));

        CompanyFactsIngestionService.BulkIngestionResult result = service.ingestAll(
                List.of("320193", "0000320193", "bad-cik", "789019"),
                2);

        assertEquals(4, result.requested());
        assertEquals(3, result.processed());
        assertEquals(1, result.succeeded());
        assertEquals(2, result.failed());
        assertEquals("0000320193", result.results().get(0).cik());
        assertTrue(result.failures().containsKey("bad-cik"));
        assertTrue(result.failures().containsKey("0000789019"));
        verify(secApiClient, times(1)).fetchCompanyFacts("0000320193");
        verify(secApiClient, times(1)).fetchCompanyFacts("0000789019");
    }

    private CompanyFactsIngestionService createService(List<NormalizedXbrlFact> storedFacts) {
        when(factDataPort.findById(anyString())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            return storedFacts.stream()
                    .filter(fact -> fact.getId().equals(id))
                    .findFirst();
        });
        when(factDataPort.findByCik("0000320193")).thenReturn(storedFacts);
        when(factDataPort.saveAll(any())).thenAnswer(invocation -> {
            Iterable<NormalizedXbrlFact> facts = invocation.getArgument(0);
            List<NormalizedXbrlFact> savedFacts = new ArrayList<>();
            for (NormalizedXbrlFact fact : facts) {
                savedFacts.add(fact);
            }
            storedFacts.clear();
            storedFacts.addAll(savedFacts);
            return List.copyOf(storedFacts);
        });

        return new CompanyFactsIngestionService(
                secApiClient,
                secResponseParser,
                conceptStandardizer,
                factDataPort);
    }

    private SecCompanyFactsResponse companyFactsResponse() {
        return SecCompanyFactsResponse.builder()
                .cik("0000320193")
                .entityName("Apple Inc.")
                .facts(Map.of(
                        "us-gaap", Map.of(
                                "CommonStockDividendsPerShareDeclared",
                                SecCompanyFactsResponse.ConceptFacts.builder()
                                        .label("Common stock dividends per share declared")
                                        .units(Map.of(
                                                "USD/shares", List.of(
                                                        fact("0000320193-25-000081", "10-K", "2025-11-01"),
                                                        fact("0000320193-25-000082", "10-K/A", "2025-11-15"))))
                                        .build())))
                .build();
    }

    private SecCompanyFactsResponse dimensionalCompanyFactsResponse() {
        SecCompanyFactsResponse.FactEntry dimensionalFact =
                fact("0000320193-25-000081", "10-K", "2025-11-01");
        dimensionalFact.setExtensionField("segment", "ProductsAxis.IPhoneMember");

        return SecCompanyFactsResponse.builder()
                .cik("0000320193")
                .entityName("Apple Inc.")
                .facts(Map.of(
                        "us-gaap", Map.of(
                                "Revenues",
                                SecCompanyFactsResponse.ConceptFacts.builder()
                                        .label("Revenues")
                                        .units(Map.of(
                                                "USD", List.of(
                                                        fact("0000320193-25-000081", "10-K", "2025-11-01"),
                                                        dimensionalFact)))
                                        .build())))
                .build();
    }

    private SecCompanyFactsResponse.FactEntry fact(String accession, String form, String filed) {
        return SecCompanyFactsResponse.FactEntry.builder()
                .end("2025-09-27")
                .start("2024-09-29")
                .val(new BigDecimal("1.04"))
                .accn(accession)
                .fy(2025)
                .fp("FY")
                .form(form)
                .filed(filed)
                .frame("CY2025")
                .build();
    }
}
