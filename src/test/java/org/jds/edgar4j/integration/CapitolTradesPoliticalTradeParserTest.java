package org.jds.edgar4j.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.jds.edgar4j.model.PoliticalTrade;
import org.junit.jupiter.api.Test;

class CapitolTradesPoliticalTradeParserTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            LocalDate.of(2026, 5, 21).atStartOfDay(ZoneId.systemDefault()).toInstant(),
            ZoneId.systemDefault());

    private final CapitolTradesPoliticalTradeParser parser = new CapitolTradesPoliticalTradeParser(FIXED_CLOCK);

    @Test
    void parsesCapitolTradesRowsWithoutImages() {
        List<PoliticalTrade> trades = parser.parse(htmlFixture(), URI.create("https://www.capitoltrades.com/trades"), "stock");

        assertEquals(2, trades.size());

        PoliticalTrade first = trades.get(0);
        assertEquals("CAPITOL_TRADES:20003798315", first.getSourceTradeId());
        assertEquals("Tim Moore", first.getPoliticianName());
        assertEquals("Republican", first.getParty());
        assertEquals("House", first.getChamber());
        assertEquals("NC", first.getState());
        assertEquals("AT&T Inc", first.getIssuerName());
        assertEquals("T", first.getTicker());
        assertEquals(LocalDate.of(2026, 5, 20), first.getDisclosureDate());
        assertEquals(LocalDate.of(2026, 5, 18), first.getTradedDate());
        assertEquals(1, first.getFiledAfterDays());
        assertEquals("Undisclosed", first.getOwner());
        assertEquals("BUY", first.getTransactionType());
        assertEquals("15K-50K", first.getAmountLabel());
        assertEquals(15_000d, first.getAmountMin());
        assertEquals(50_000d, first.getAmountMax());
        assertEquals(24.43d, first.getPrice());
        assertEquals("stock", first.getAssetType());
        assertEquals("https://www.capitoltrades.com/trades/20003798315", first.getSourceTradeUrl());

        PoliticalTrade second = trades.get(1);
        assertEquals(LocalDate.of(2026, 5, 21), second.getDisclosureDate());
        assertEquals("MSFT", second.getTicker());
        assertNull(second.getPrice());
        assertNull(second.getAmountMin());
        assertNull(second.getAmountMax());
    }

    private String htmlFixture() {
        return """
                <html><body><table><tbody>
                <tr>
                  <td><div class="q-cell cell--politician has-avatar">
                    <img src="/ignored.jpg"><h2 class="politician-name"><a href="/politicians/M001236">Tim Moore</a></h2>
                    <div class="politician-info"><span class="q-field party party--republican">Republican</span><span class="q-field chamber chamber--house">House</span><span class="q-field us-state-compact us-state-compact--nc">NC</span></div>
                  </div></td>
                  <td><div class="q-cell cell--traded-issuer has-avatar"><figure></figure><h3 class="q-fieldset issuer-name"><a href="/issuers/429914">AT&amp;T Inc</a></h3><span class="q-field issuer-ticker">T:US</span></div></td>
                  <td><div class="text-center"><div>13:05</div><div>Yesterday</div></div></td>
                  <td><div class="text-center"><div>18 May</div><div>2026</div></div></td>
                  <td><div class="q-cell cell--reporting-gap"><div class="q-label">days</div><div class="q-value"><span>1</span></div></div></td>
                  <td><span class="q-field owner-with-icon"><span class="q-label">Undisclosed</span></span></td>
                  <td><span class="q-field tx-type tx-type--buy">buy</span></td>
                  <td><span class="q-field trade-size"><span class="text-size-2">15K\u201350K</span></span></td>
                  <td><span>$24.43</span></td>
                  <td><a href="/trades/20003798315"><span class="sr-only">Goto trade detail page.</span></a></td>
                </tr>
                <tr>
                  <td><div class="q-cell cell--politician has-avatar">
                    <h2 class="politician-name"><a href="/politicians/G000583">Josh Gottheimer</a></h2>
                    <div class="politician-info"><span class="q-field party party--democrat">Democrat</span><span class="q-field chamber chamber--house">House</span><span class="q-field us-state-compact us-state-compact--nj">NJ</span></div>
                  </div></td>
                  <td><div class="q-cell cell--traded-issuer has-avatar"><h3 class="q-fieldset issuer-name"><a href="/issuers/433382">Microsoft Corp</a></h3><span class="q-field issuer-ticker">MSFT:US</span></div></td>
                  <td><div class="text-center"><div>08:15</div><div>Today</div></div></td>
                  <td><div class="text-center"><div>9 Apr</div><div>2026</div></div></td>
                  <td><div class="q-cell cell--reporting-gap"><div class="q-label">days</div><div class="q-value"><span>41</span></div></div></td>
                  <td><span class="q-field owner-with-icon"><span class="q-label">Spouse</span></span></td>
                  <td><span class="q-field tx-type tx-type--sell">sell</span></td>
                  <td><span class="q-field trade-size"><span class="text-size-2">N/A</span></span></td>
                  <td><span>N/A</span></td>
                  <td><a href="/trades/20003798316"><span class="sr-only">Goto trade detail page.</span></a></td>
                </tr>
                </tbody></table></body></html>
                """;
    }
}
