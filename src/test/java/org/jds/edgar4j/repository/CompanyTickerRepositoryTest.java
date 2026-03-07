package org.jds.edgar4j.repository;

import org.jds.edgar4j.model.CompanyTicker;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link CompanyTickerRepository}.
 *
 * <p>Uses the embedded MongoDB started by {@code EmbeddedMongoConfig} — no mocks.
 * The unique index on {@code ticker} is created at startup via
 * {@code @Indexed(unique=true)} + {@code auto-index-creation: true}, so the
 * uniqueness-constraint test (order 15) is reliable without any manual index setup.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CompanyTickerRepositoryTest {

    @Autowired
    private CompanyTickerRepository companyTickerRepository;

    // ── fixtures ──────────────────────────────────────────────────────────────

    private static final long   AAPL_CIK   = 320193L;
    private static final String AAPL_TICKER = "AAPL";
    private static final String AAPL_TITLE  = "Apple Inc.";

    private static final long   MSFT_CIK   = 789019L;
    private static final String MSFT_TICKER = "MSFT";
    private static final String MSFT_TITLE  = "Microsoft Corp";

    private static final long   PLTR_CIK   = 1560327L;
    private static final String PLTR_TICKER = "PLTR";
    private static final String PLTR_TITLE  = "Palantir Technologies Inc.";

    @BeforeEach
    void seedData() {
        companyTickerRepository.deleteAll();

        companyTickerRepository.save(CompanyTicker.builder()
                .cikStr(AAPL_CIK).ticker(AAPL_TICKER).title(AAPL_TITLE).build());
        companyTickerRepository.save(CompanyTicker.builder()
                .cikStr(MSFT_CIK).ticker(MSFT_TICKER).title(MSFT_TITLE).build());
        companyTickerRepository.save(CompanyTicker.builder()
                .cikStr(PLTR_CIK).ticker(PLTR_TICKER).title(PLTR_TITLE).build());
    }

    @AfterEach
    void tearDown() {
        companyTickerRepository.deleteAll();
    }

    // ── basic CRUD ────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("Should save and count company tickers")
    void shouldSaveAndCount() {
        assertEquals(3, companyTickerRepository.count());
    }

    // ── findByTicker (exact) ─────────────────────────────────────────────────

    @Test
    @Order(2)
    @DisplayName("Should find by exact ticker (case-sensitive)")
    void shouldFindByExactTicker() {
        Optional<CompanyTicker> result = companyTickerRepository.findByTicker("AAPL");

        assertTrue(result.isPresent());
        assertEquals(AAPL_CIK, result.get().getCikStr());
        assertEquals(AAPL_TITLE, result.get().getTitle());
    }

    @Test
    @Order(3)
    @DisplayName("Should return empty for non-existent exact ticker")
    void shouldReturnEmptyForMissingExactTicker() {
        Optional<CompanyTicker> result = companyTickerRepository.findByTicker("XXXXXX");
        assertFalse(result.isPresent());
    }

    // ── findByTickerIgnoreCase ────────────────────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("Should find by ticker ignoring case (lowercase input)")
    void shouldFindByTickerIgnoreCase_lowercase() {
        Optional<CompanyTicker> result = companyTickerRepository.findByTickerIgnoreCase("aapl");

        assertTrue(result.isPresent());
        assertEquals(AAPL_TICKER, result.get().getTicker());
        assertEquals(AAPL_CIK, result.get().getCikStr());
    }

    @Test
    @Order(5)
    @DisplayName("Should find by ticker ignoring case (mixed case input)")
    void shouldFindByTickerIgnoreCase_mixedCase() {
        Optional<CompanyTicker> result = companyTickerRepository.findByTickerIgnoreCase("MsFt");

        assertTrue(result.isPresent());
        assertEquals(MSFT_TICKER, result.get().getTicker());
    }

    @Test
    @Order(6)
    @DisplayName("Should return empty for unknown ticker (case insensitive)")
    void shouldReturnEmptyForUnknownTickerIgnoreCase() {
        Optional<CompanyTicker> result = companyTickerRepository.findByTickerIgnoreCase("XXXXXX");
        assertFalse(result.isPresent());
    }

    // ── findByCikStr ─────────────────────────────────────────────────────────

    @Test
    @Order(7)
    @DisplayName("Should find list by numeric CIK")
    void shouldFindByCikStr() {
        List<CompanyTicker> results = companyTickerRepository.findByCikStr(MSFT_CIK);

        assertEquals(1, results.size());
        assertEquals(MSFT_TICKER, results.get(0).getTicker());
    }

    @Test
    @Order(8)
    @DisplayName("Should return empty list for unknown CIK")
    void shouldReturnEmptyListForUnknownCik() {
        List<CompanyTicker> results = companyTickerRepository.findByCikStr(9999999999L);
        assertTrue(results.isEmpty());
    }

    // ── findFirstByCikStr ─────────────────────────────────────────────────────

    @Test
    @Order(9)
    @DisplayName("Should find first entry by numeric CIK")
    void shouldFindFirstByCikStr() {
        Optional<CompanyTicker> result = companyTickerRepository.findFirstByCikStr(PLTR_CIK);

        assertTrue(result.isPresent());
        assertEquals(PLTR_TICKER, result.get().getTicker());
        assertEquals(PLTR_TITLE, result.get().getTitle());
    }

    @Test
    @Order(10)
    @DisplayName("Should return empty for unknown CIK (findFirst)")
    void shouldReturnEmptyForUnknownCikFindFirst() {
        Optional<CompanyTicker> result = companyTickerRepository.findFirstByCikStr(9999999999L);
        assertFalse(result.isPresent());
    }

    // ── getCikPadded helper ───────────────────────────────────────────────────

    @Test
    @Order(11)
    @DisplayName("getCikPadded should return 10-digit zero-padded CIK string")
    void shouldReturnPaddedCik() {
        CompanyTicker ct = companyTickerRepository.findByTicker("AAPL").orElseThrow();
        assertEquals("0000320193", ct.getCikPadded());
    }

    @Test
    @Order(12)
    @DisplayName("getCikPadded should return null when cikStr is null")
    void shouldReturnNullPaddedCikWhenCikStrIsNull() {
        CompanyTicker ct = CompanyTicker.builder().ticker("TEST").title("Test Co").build();
        assertNull(ct.getCikPadded());
    }

    // ── prefix search ─────────────────────────────────────────────────────────

    @Test
    @Order(13)
    @DisplayName("Should find tickers by prefix (case insensitive)")
    void shouldFindByTickerPrefix() {
        companyTickerRepository.save(CompanyTicker.builder()
                .cikStr(12345L).ticker("AAL").title("American Airlines Group Inc.").build());
        companyTickerRepository.save(CompanyTicker.builder()
                .cikStr(23456L).ticker("AABB").title("AABB Corp").build());

        List<CompanyTicker> results = companyTickerRepository.findByTickerStartingWithIgnoreCase("aa");

        // Should find AAPL, AAL, AABB (3 out of 5 total)
        assertEquals(3, results.size());
        assertTrue(results.stream().allMatch(ct -> ct.getTicker().toUpperCase().startsWith("AA")));
    }

    // ── title search ──────────────────────────────────────────────────────────

    @Test
    @Order(14)
    @DisplayName("Should find tickers by title substring (case insensitive)")
    void shouldFindByTitleContaining() {
        List<CompanyTicker> results = companyTickerRepository.findByTitleContainingIgnoreCase("corp");
        assertTrue(results.stream().anyMatch(ct -> MSFT_TICKER.equals(ct.getTicker())));
    }

    // ── uniqueness constraint ─────────────────────────────────────────────────

    @Test
    @Order(15)
    @DisplayName("Should reject duplicate ticker symbols (unique index enforced by auto-index-creation)")
    void shouldRejectDuplicateTicker() {
        CompanyTicker duplicate = CompanyTicker.builder()
                .cikStr(99999L)
                .ticker(AAPL_TICKER)   // AAPL already in collection from @BeforeEach
                .title("Fake Apple")
                .build();

        assertThrows(Exception.class, () -> companyTickerRepository.save(duplicate));
    }

    // ── full-round-trip ───────────────────────────────────────────────────────

    @Test
    @Order(16)
    @DisplayName("Full round-trip: save → findByTicker → getCikPadded → findFirstByCikStr")
    void shouldRoundTripTickerCik() {
        companyTickerRepository.save(CompanyTicker.builder()
                .cikStr(1318605L).ticker("TSLA").title("Tesla Inc.").build());

        // Look up by ticker (case insensitive)
        CompanyTicker byTicker = companyTickerRepository
                .findByTickerIgnoreCase("tsla")
                .orElseThrow(() -> new AssertionError("TSLA not found by ticker"));
        assertEquals("0001318605", byTicker.getCikPadded());

        // Look up by CIK
        CompanyTicker byCik = companyTickerRepository
                .findFirstByCikStr(1318605L)
                .orElseThrow(() -> new AssertionError("TSLA not found by CIK"));
        assertEquals("TSLA", byCik.getTicker());
    }
}
