package org.jds.edgar4j.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jds.edgar4j.model.CompanyTicker;
import org.jds.edgar4j.model.Sp500Constituent;
import org.jds.edgar4j.repository.CompanyTickerRepository;
import org.jds.edgar4j.repository.Sp500ConstituentRepository;
import org.jds.edgar4j.service.SettingsService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class Sp500ServiceImplTest {

    private static final String SAMPLE_WIKIPEDIA_HTML = """
            <html>
              <body>
                <table id="constituents" class="wikitable sortable">
                  <tbody>
                    <tr>
                      <th>Symbol</th>
                      <th>Security</th>
                      <th>GICS Sector</th>
                      <th>GICS Sub-Industry</th>
                      <th>Headquarters Location</th>
                      <th>Date added</th>
                      <th>CIK</th>
                      <th>Founded</th>
                    </tr>
                    <tr>
                      <td>BRK.B</td>
                      <td>Berkshire Hathaway Inc.</td>
                      <td>Financials</td>
                      <td>Multi-Sector Holdings</td>
                      <td>Omaha, Nebraska</td>
                      <td>2010-02-16</td>
                      <td>1067983</td>
                      <td>1839</td>
                    </tr>
                    <tr>
                      <td>AAPL</td>
                      <td>Apple Inc.</td>
                      <td>Information Technology</td>
                      <td>Technology Hardware, Storage &amp; Peripherals</td>
                      <td>Cupertino, California</td>
                      <td>1982-11-30</td>
                      <td></td>
                      <td>1977</td>
                    </tr>
                  </tbody>
                </table>
              </body>
            </html>
            """;

    @Mock
    private Sp500ConstituentRepository sp500Repository;

    @Mock
    private CompanyTickerRepository companyTickerRepository;

    @Mock
    private SettingsService settingsService;

    @InjectMocks
    private Sp500ServiceImpl sp500Service;

    @Test
    @DisplayName("syncFromWikipedia should parse the current Wikipedia table layout and clean up stale entries")
    void syncFromWikipediaShouldParseAndCleanup() throws Exception {
        Sp500Constituent existingApple = Sp500Constituent.builder()
                .id("existing-aapl")
                .ticker("AAPL")
                .companyName("Old Apple Name")
                .cik("0000320193")
                .sector("Legacy Sector")
                .subIndustry("Legacy Sub-Industry")
                .dateAdded(LocalDate.of(1982, 11, 30))
                .lastUpdated(Instant.parse("2025-01-01T00:00:00Z"))
                .build();
        Sp500Constituent staleConstituent = Sp500Constituent.builder()
                .id("stale")
                .ticker("ZZZZ")
                .companyName("Removed Company")
                .cik("0000009999")
                .sector("Utilities")
                .subIndustry("Legacy")
                .lastUpdated(Instant.parse("2025-01-01T00:00:00Z"))
                .build();

        Document wikipediaDocument = Jsoup.parse(SAMPLE_WIKIPEDIA_HTML, "https://en.wikipedia.org/");
        Sp500ServiceImpl spyService = org.mockito.Mockito.spy(sp500Service);

        when(sp500Repository.findAll()).thenReturn(List.of(existingApple, staleConstituent));
        when(sp500Repository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(companyTickerRepository.findByTickerIgnoreCase("AAPL")).thenReturn(Optional.of(
                CompanyTicker.builder()
                        .ticker("AAPL")
                        .title("Apple Inc.")
                        .cikStr(320193L)
                        .build()));
        doReturn(wikipediaDocument).when(spyService).fetchWikipediaDocument();

        List<Sp500Constituent> syncedConstituents = spyService.syncFromWikipedia();

        ArgumentCaptor<List<Sp500Constituent>> savedCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<Sp500Constituent>> deletedCaptor = ArgumentCaptor.forClass(List.class);
        verify(sp500Repository).saveAll(savedCaptor.capture());
        verify(sp500Repository).deleteAll(deletedCaptor.capture());

        assertEquals(List.of("AAPL", "BRK-B"),
                syncedConstituents.stream().map(Sp500Constituent::getTicker).toList());

        List<Sp500Constituent> savedConstituents = savedCaptor.getValue();
        assertEquals(2, savedConstituents.size());

        Sp500Constituent savedApple = savedConstituents.stream()
                .filter(constituent -> "AAPL".equals(constituent.getTicker()))
                .findFirst()
                .orElseThrow();
        assertEquals("existing-aapl", savedApple.getId());
        assertEquals("Apple Inc.", savedApple.getCompanyName());
        assertEquals("0000320193", savedApple.getCik());
        assertEquals("Information Technology", savedApple.getSector());
        assertEquals("Technology Hardware, Storage & Peripherals", savedApple.getSubIndustry());
        assertEquals(LocalDate.of(1982, 11, 30), savedApple.getDateAdded());
        assertNotNull(savedApple.getLastUpdated());

        Sp500Constituent savedBerkshire = savedConstituents.stream()
                .filter(constituent -> "BRK-B".equals(constituent.getTicker()))
                .findFirst()
                .orElseThrow();
        assertEquals("Berkshire Hathaway Inc.", savedBerkshire.getCompanyName());
        assertEquals("0001067983", savedBerkshire.getCik());
        assertEquals("Financials", savedBerkshire.getSector());
        assertEquals("Multi-Sector Holdings", savedBerkshire.getSubIndustry());
        assertEquals(LocalDate.of(2010, 2, 16), savedBerkshire.getDateAdded());

        assertEquals(List.of("ZZZZ"),
                deletedCaptor.getValue().stream().map(Sp500Constituent::getTicker).toList());
    }

    @Test
    @DisplayName("Lookup methods should normalize ticker case")
    void queryMethodsShouldNormalizeTickerCase() {
        Sp500Constituent microsoft = Sp500Constituent.builder()
                .ticker("MSFT")
                .companyName("Microsoft Corporation")
                .cik("0000789019")
                .sector("Information Technology")
                .subIndustry("Systems Software")
                .build();

        when(sp500Repository.findAllByOrderByTickerAsc()).thenReturn(List.of(microsoft));
        when(sp500Repository.existsByTickerIgnoreCase("MSFT")).thenReturn(true);
        when(sp500Repository.findByTickerIgnoreCase("MSFT")).thenReturn(Optional.of(microsoft));
        when(sp500Repository.count()).thenReturn(1L);

        assertEquals(List.of(microsoft), sp500Service.getAll());
        assertEquals(Set.of("MSFT"), sp500Service.getAllTickers());
        assertTrue(sp500Service.isSp500("msft"));
        assertEquals(Optional.of(microsoft), sp500Service.findByTicker("msft"));
        assertEquals(1L, sp500Service.count());
        assertFalse(sp500Service.isSp500(null));
        assertEquals(Optional.empty(), sp500Service.findByTicker(null));
    }

    @Test
    @DisplayName("parseConstituents should fail fast when the Wikipedia table is missing")
    void parseConstituentsShouldFailWhenTableMissing() {
        Document documentWithoutTable = Jsoup.parse("<html><body><p>Missing table</p></body></html>");

        Assertions.assertThrows(IllegalStateException.class,
                () -> sp500Service.parseConstituents(documentWithoutTable, Map.of(), Instant.now()));
    }
}
