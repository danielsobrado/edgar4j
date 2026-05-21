package org.jds.edgar4j.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.jds.edgar4j.model.PoliticalTrade;
import org.jds.edgar4j.port.PoliticalTradeDataPort;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

abstract class PoliticalTradeDataPortContractTest {

    protected abstract PoliticalTradeDataPort port();

    @Test
    void saveAndFindBySourceTradeIdRoundTrip() {
        PoliticalTrade trade = createTrade("CAPITOL_TRADES:1", "T");

        port().save(trade);

        PoliticalTrade found = port().findBySourceTradeId("CAPITOL_TRADES:1").orElseThrow();
        assertEquals("Tim Moore", found.getPoliticianName());
        assertEquals("T", found.getTicker());
        assertTrue(port().existsBySourceTradeId("CAPITOL_TRADES:1"));
        assertFalse(port().existsBySourceTradeId("missing"));
    }

    @Test
    void findAllSupportsPagination() {
        port().saveAll(List.of(
                createTrade("CAPITOL_TRADES:1", "T"),
                createTrade("CAPITOL_TRADES:2", "MSFT"),
                createTrade("CAPITOL_TRADES:3", "MLM")));

        Page<PoliticalTrade> page = port().findAll(PageRequest.of(0, 2));

        assertEquals(3, page.getTotalElements());
        assertEquals(2, page.getContent().size());
    }

    @Test
    void deleteByIdRemovesRecord() {
        PoliticalTrade saved = port().save(createTrade("CAPITOL_TRADES:1", "T"));

        port().deleteById(saved.getId());

        assertTrue(port().findBySourceTradeId("CAPITOL_TRADES:1").isEmpty());
    }

    protected PoliticalTrade createTrade(String sourceTradeId, String ticker) {
        return PoliticalTrade.builder()
                .id(sourceTradeId.replace(':', '-'))
                .sourceTradeId(sourceTradeId)
                .politicianName("Tim Moore")
                .party("Republican")
                .chamber("House")
                .state("NC")
                .issuerName(ticker + " Inc")
                .ticker(ticker)
                .disclosureDate(LocalDate.of(2026, 5, 20))
                .tradedDate(LocalDate.of(2026, 5, 18))
                .transactionType("BUY")
                .assetType("stock")
                .source("CAPITOL_TRADES")
                .build();
    }
}
