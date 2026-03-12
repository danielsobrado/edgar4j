package org.jds.edgar4j.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.jds.edgar4j.model.Sp500Constituent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(OrderAnnotation.class)
class Sp500ConstituentRepositoryTest {

    @Autowired
    private Sp500ConstituentRepository sp500ConstituentRepository;

    @BeforeEach
    void seedData() {
        sp500ConstituentRepository.deleteAll();

        Instant now = Instant.now();
        sp500ConstituentRepository.saveAll(List.of(
                Sp500Constituent.builder()
                        .ticker("AAPL")
                        .companyName("Apple Inc.")
                        .cik("0000320193")
                        .sector("Information Technology")
                        .subIndustry("Technology Hardware, Storage & Peripherals")
                        .dateAdded(LocalDate.of(1982, 11, 30))
                        .lastUpdated(now)
                        .build(),
                Sp500Constituent.builder()
                        .ticker("BRK-B")
                        .companyName("Berkshire Hathaway Inc.")
                        .cik("0001067983")
                        .sector("Financials")
                        .subIndustry("Multi-Sector Holdings")
                        .dateAdded(LocalDate.of(2010, 2, 16))
                        .lastUpdated(now)
                        .build(),
                Sp500Constituent.builder()
                        .ticker("MSFT")
                        .companyName("Microsoft Corporation")
                        .cik("0000789019")
                        .sector("Information Technology")
                        .subIndustry("Systems Software")
                        .dateAdded(LocalDate.of(1994, 6, 1))
                        .lastUpdated(now)
                        .build()));
    }

    @AfterEach
    void tearDown() {
        sp500ConstituentRepository.deleteAll();
    }

    @Test
    @Order(1)
    @DisplayName("Should save and count S&P 500 constituents")
    void shouldSaveAndCount() {
        assertEquals(3, sp500ConstituentRepository.count());
    }

    @Test
    @Order(2)
    @DisplayName("Should find a constituent by ticker ignoring case")
    void shouldFindByTickerIgnoreCase() {
        var result = sp500ConstituentRepository.findByTickerIgnoreCase("brk-b");

        assertTrue(result.isPresent());
        assertEquals("Berkshire Hathaway Inc.", result.get().getCompanyName());
        assertEquals("0001067983", result.get().getCik());
    }

    @Test
    @Order(3)
    @DisplayName("Should report S&P 500 membership by ticker ignoring case")
    void shouldCheckExistsByTickerIgnoreCase() {
        assertTrue(sp500ConstituentRepository.existsByTickerIgnoreCase("msft"));
        assertFalse(sp500ConstituentRepository.existsByTickerIgnoreCase("pltr"));
    }

    @Test
    @Order(4)
    @DisplayName("Should find a constituent by CIK")
    void shouldFindByCik() {
        var result = sp500ConstituentRepository.findByCik("0000320193");

        assertTrue(result.isPresent());
        assertEquals("AAPL", result.get().getTicker());
    }

    @Test
    @Order(5)
    @DisplayName("Should return all constituents ordered by ticker")
    void shouldFindAllOrderedByTicker() {
        List<Sp500Constituent> constituents = sp500ConstituentRepository.findAllByOrderByTickerAsc();

        assertEquals(List.of("AAPL", "BRK-B", "MSFT"),
                constituents.stream().map(Sp500Constituent::getTicker).toList());
    }

    @Test
    @Order(6)
    @DisplayName("Should find constituents by sector ordered by ticker")
    void shouldFindBySectorOrderedByTicker() {
        List<Sp500Constituent> constituents =
                sp500ConstituentRepository.findBySectorOrderByTickerAsc("Information Technology");

        assertEquals(List.of("AAPL", "MSFT"),
                constituents.stream().map(Sp500Constituent::getTicker).toList());
    }

    @Test
    @Order(7)
    @DisplayName("Should reject duplicate ticker symbols")
    void shouldRejectDuplicateTicker() {
        Sp500Constituent duplicate = Sp500Constituent.builder()
                .ticker("AAPL")
                .companyName("Fake Apple")
                .cik("0000000001")
                .sector("Information Technology")
                .subIndustry("Fake")
                .lastUpdated(Instant.now())
                .build();

        assertThrows(Exception.class, () -> sp500ConstituentRepository.save(duplicate));
    }
}
