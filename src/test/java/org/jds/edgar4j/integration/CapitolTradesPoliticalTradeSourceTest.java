package org.jds.edgar4j.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import org.jds.edgar4j.exception.PoliticalTradeSyncException;
import org.jds.edgar4j.model.PoliticalTrade;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class CapitolTradesPoliticalTradeSourceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-05-21T10:00:00Z"), ZoneId.of("UTC"));

    @Test
    void fetchUsesPublicTradePagesAndStopsAtFirstEmptyPage() {
        StubSource source = new StubSource(htmlWithOneTrade(), emptyPage());

        List<PoliticalTrade> trades = source.fetch(new PoliticalTradeSourceRequest("stock", 3));

        assertEquals(1, trades.size());
        assertEquals(2, source.uris.size());
        assertTrue(source.uris.get(0).toString().startsWith("https://www.capitoltrades.com/trades?"));
        assertTrue(source.uris.get(0).getQuery().contains("page=1"));
        assertTrue(source.uris.get(0).getQuery().contains("assetType=stock"));
        assertTrue(source.uris.get(1).getQuery().contains("page=2"));
    }

    @Test
    void fetchWrapsSourceIoFailuresAsBadGatewaySyncFailure() {
        CapitolTradesPoliticalTradeSource source = new CapitolTradesPoliticalTradeSource(FIXED_CLOCK) {
            @Override
            String fetchPage(URI pageUri) throws IOException {
                throw new IOException("timeout");
            }
        };

        PoliticalTradeSyncException ex = assertThrows(PoliticalTradeSyncException.class,
                () -> source.fetch(new PoliticalTradeSourceRequest("stock", 1)));

        assertEquals("POLITICAL_TRADE_SOURCE_FETCH_FAILED", ex.getErrorCode());
        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatus());
    }

    private static String htmlWithOneTrade() {
        return """
                <html><body><table><tbody>
                <tr>
                  <td><div class="q-cell cell--politician"><h2 class="politician-name"><a>Tim Moore</a></h2><span class="party">Republican</span><span class="chamber">House</span><span class="us-state-compact">NC</span></div></td>
                  <td><div class="q-cell cell--traded-issuer"><h3 class="q-fieldset issuer-name"><a>AT&T Inc.</a></h3><span class="q-field issuer-ticker">$T:US</span></div></td>
                  <td><div class="text-center"><div>20 May</div><div>2026</div></div></td>
                  <td><div class="text-center"><div>18 May</div><div>2026</div></div></td>
                  <td>2 days</td>
                  <td><span class="q-label">Self</span></td>
                  <td><span class="q-field tx-type">buy</span></td>
                  <td><span class="q-field trade-size"><span class="text-size-2">15K-50K</span></span></td>
                  <td>$24.43</td>
                  <td><a href="/trades/20003798317"></a></td>
                </tr>
                </tbody></table></body></html>
                """;
    }

    private static String emptyPage() {
        return "<html><body><table><tbody></tbody></table></body></html>";
    }

    private static class StubSource extends CapitolTradesPoliticalTradeSource {

        private final Queue<String> pages = new ArrayDeque<>();
        private final List<URI> uris = new ArrayList<>();

        StubSource(String... pages) {
            super(FIXED_CLOCK);
            this.pages.addAll(List.of(pages));
        }

        @Override
        String fetchPage(URI pageUri) {
            uris.add(pageUri);
            return pages.remove();
        }
    }
}
