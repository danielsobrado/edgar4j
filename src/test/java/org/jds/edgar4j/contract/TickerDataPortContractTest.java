package org.jds.edgar4j.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.jds.edgar4j.TestFixtures;
import org.jds.edgar4j.model.Ticker;
import org.jds.edgar4j.port.TickerDataPort;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

abstract class TickerDataPortContractTest {

    protected abstract TickerDataPort port();

    @Test
    void saveAndFindByCode_roundTrip() {
        Ticker ticker = TestFixtures.createTestTicker("AAPL", "0000320193");
        port().save(ticker);

        assertEquals("0000320193", port().findByCode("aapl").orElseThrow().getCik());
    }

    @Test
    void findByCik_returnsTicker() {
        Ticker ticker = TestFixtures.createTestTicker("MSFT", "0000789019");
        port().save(ticker);

        assertEquals("MSFT", port().findByCik("0000789019").orElseThrow().getCode());
    }

    @Test
    void findByCodeIn_returnsMultipleTickers() {
        Ticker first = TestFixtures.createTestTicker("AAPL", "0000320193");
        Ticker second = TestFixtures.createTestTicker("MSFT", "0000789019");
        port().saveAll(List.of(first, second));

        List<Ticker> tickers = port().findByCodeIn(List.of("aapl", "MSFT"));

        assertEquals(2, tickers.size());
    }

    @Test
    void findAll_supportsPagination() {
        port().saveAll(List.of(
                TestFixtures.createTestTicker("AAPL", "0000320193"),
                TestFixtures.createTestTicker("MSFT", "0000789019"),
                TestFixtures.createTestTicker("GOOG", "0001652044")));

        Page<Ticker> page = port().findAll(PageRequest.of(0, 2));

        assertEquals(3, page.getTotalElements());
        assertEquals(2, page.getContent().size());
    }

    @Test
    void save_updatesExistingTicker() {
        Ticker ticker = TestFixtures.createTestTicker("AAPL", "0000320193");
        port().save(ticker);
        ticker.setName("Apple Updated");

        port().save(ticker);

        assertEquals("Apple Updated", port().findByCode("AAPL").orElseThrow().getName());
    }

    @Test
    void delete_removesTicker() {
        Ticker ticker = TestFixtures.createTestTicker("AAPL", "0000320193");
        port().save(ticker);

        port().delete(ticker);

        assertTrue(port().findByCode("AAPL").isEmpty());
    }
}
