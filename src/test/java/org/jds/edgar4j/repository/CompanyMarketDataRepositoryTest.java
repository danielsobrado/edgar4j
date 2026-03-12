package org.jds.edgar4j.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.jds.edgar4j.model.CompanyMarketData;
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
class CompanyMarketDataRepositoryTest {

    @Autowired
    private CompanyMarketDataRepository companyMarketDataRepository;

    @BeforeEach
    void seedData() {
        companyMarketDataRepository.deleteAll();

        Instant now = Instant.now();
        companyMarketDataRepository.saveAll(List.of(
                CompanyMarketData.builder()
                        .ticker("AAPL")
                        .cik("0000320193")
                        .marketCap(3_250_000_000_000d)
                        .currentPrice(181.32d)
                        .previousClose(179.55d)
                        .currency("USD")
                        .lastUpdated(now)
                        .build(),
                CompanyMarketData.builder()
                        .ticker("MSFT")
                        .cik("0000789019")
                        .marketCap(2_950_000_000_000d)
                        .currentPrice(420.15d)
                        .previousClose(417.10d)
                        .currency("USD")
                        .lastUpdated(now)
                        .build(),
                CompanyMarketData.builder()
                        .ticker("RACE")
                        .cik("0001648416")
                        .marketCap(75_000_000_000d)
                        .currentPrice(428.40d)
                        .previousClose(425.00d)
                        .currency("EUR")
                        .lastUpdated(now)
                        .build()));
    }

    @AfterEach
    void tearDown() {
        companyMarketDataRepository.deleteAll();
    }

    @Test
    @Order(1)
    @DisplayName("Should save and count company market data")
    void shouldSaveAndCount() {
        assertEquals(3, companyMarketDataRepository.count());
    }

    @Test
    @Order(2)
    @DisplayName("Should find market data by ticker ignoring case")
    void shouldFindByTickerIgnoreCase() {
        var result = companyMarketDataRepository.findByTickerIgnoreCase("msft");

        assertTrue(result.isPresent());
        assertEquals("0000789019", result.get().getCik());
        assertEquals(420.15d, result.get().getCurrentPrice());
    }

    @Test
    @Order(3)
    @DisplayName("Should find a batch of market data by ticker list")
    void shouldFindByTickerIn() {
        List<CompanyMarketData> result = companyMarketDataRepository.findByTickerIn(List.of("AAPL", "MSFT"));

        assertEquals(2, result.size());
        assertEquals(List.of("AAPL", "MSFT"),
                result.stream().map(CompanyMarketData::getTicker).sorted().toList());
    }

    @Test
    @Order(4)
    @DisplayName("Should find companies at or above a market-cap threshold")
    void shouldFindByMarketCapGreaterThanEqual() {
        List<CompanyMarketData> result = companyMarketDataRepository.findByMarketCapGreaterThanEqual(1_000_000_000_000d);

        assertEquals(List.of("AAPL", "MSFT"),
                result.stream().map(CompanyMarketData::getTicker).sorted().toList());
    }

    @Test
    @Order(5)
    @DisplayName("Should report market data existence by ticker ignoring case")
    void shouldCheckExistsByTickerIgnoreCase() {
        assertTrue(companyMarketDataRepository.existsByTickerIgnoreCase("aapl"));
        assertFalse(companyMarketDataRepository.existsByTickerIgnoreCase("tsla"));
    }

    @Test
    @Order(6)
    @DisplayName("Should reject duplicate ticker symbols")
    void shouldRejectDuplicateTicker() {
        CompanyMarketData duplicate = CompanyMarketData.builder()
                .ticker("AAPL")
                .cik("0000000001")
                .marketCap(1d)
                .currentPrice(1d)
                .previousClose(1d)
                .currency("USD")
                .lastUpdated(Instant.now())
                .build();

        assertThrows(Exception.class, () -> companyMarketDataRepository.save(duplicate));
    }
}
