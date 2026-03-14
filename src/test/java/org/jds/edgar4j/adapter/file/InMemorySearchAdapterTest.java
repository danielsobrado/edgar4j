package org.jds.edgar4j.adapter.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import org.jds.edgar4j.model.CompanyTicker;
import org.jds.edgar4j.model.Filling;
import org.jds.edgar4j.model.FormType;
import org.jds.edgar4j.model.Ticker;
import org.jds.edgar4j.port.CompanyTickerDataPort;
import org.jds.edgar4j.port.FilingSearchPort;
import org.jds.edgar4j.port.FillingDataPort;
import org.jds.edgar4j.port.TickerDataPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class InMemorySearchAdapterTest {

    @Mock
    private FillingDataPort fillingDataPort;

    @Mock
    private TickerDataPort tickerDataPort;

    @Mock
    private CompanyTickerDataPort companyTickerDataPort;

    private InMemorySearchAdapter inMemorySearchAdapter;

    @BeforeEach
    void setUp() {
        inMemorySearchAdapter = new InMemorySearchAdapter(fillingDataPort, tickerDataPort, companyTickerDataPort);
    }

    @Test
    @DisplayName("search should resolve ticker symbols to CIKs and preserve filing sort order")
    void searchShouldResolveTickerSymbolsToCiks() throws ParseException {
        when(tickerDataPort.findByCode("ACME")).thenReturn(java.util.Optional.of(Ticker.builder().cik("0001234567").build()));
        when(companyTickerDataPort.findByTickerIgnoreCase("acme")).thenReturn(java.util.Optional.empty());
        when(fillingDataPort.findAll()).thenReturn(List.of(
                filing("1", "Older Filing", "0001234567", "8-K", "2024-01-01"),
                filing("2", "Newest Filing", "0001234567", "10-K", "2024-02-01"),
                filing("3", "Different Company", "0009999999", "10-K", "2024-03-01")));

        Page<FilingSearchPort.SearchResult> results = inMemorySearchAdapter.search(
                new FilingSearchPort.SearchCriteria(null, List.of(), null, "acme", null, null),
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "fillingDate")));

        assertThat(results.getContent()).extracting(FilingSearchPort.SearchResult::id)
                .containsExactly("2", "1");
    }

    @Test
    @DisplayName("search should combine free text, form type, and date filters")
    void searchShouldApplyStructuredFilters() throws ParseException {
        when(fillingDataPort.findAll()).thenReturn(List.of(
                filing("1", "Acme Corporation", "0001234567", "10-K", "2024-01-15", "annual report"),
                filing("2", "Acme Corporation", "0001234567", "8-K", "2024-01-20", "current report"),
                filing("3", "Other Co", "0005555555", "10-K", "2024-01-15", "annual report")));

        Page<FilingSearchPort.SearchResult> results = inMemorySearchAdapter.search(
                new FilingSearchPort.SearchCriteria(
                        "acme annual",
                        List.of("10-K"),
                        null,
                        null,
                        LocalDate.of(2024, 1, 1),
                        LocalDate.of(2024, 1, 31)),
                PageRequest.of(0, 10));

        assertThat(results.getContent()).extracting(FilingSearchPort.SearchResult::id)
                .containsExactly("1");
        assertThat(results.getContent().get(0).snippet()).isEqualTo("annual report");
    }

    @Test
    @DisplayName("suggest should return prefix-matching values without duplicates")
    void suggestShouldReturnDistinctPrefixMatches() throws ParseException {
        when(fillingDataPort.findAll()).thenReturn(List.of(
                filing("1", "Acme Corporation", "0001234567", "10-K", "2024-01-15"),
                filing("2", "Acme Corporation", "0001234567", "8-K", "2024-01-20"),
                filing("3", "Acuity Holdings", "0005555555", "10-Q", "2024-01-25")));

        List<String> suggestions = inMemorySearchAdapter.suggest("Ac", 5);

        assertThat(suggestions).containsExactly("Acme Corporation", "Acuity Holdings");
    }

    private Filling filing(String id, String company, String cik, String formType, String date) throws ParseException {
        return filing(id, company, cik, formType, date, null);
    }

    private Filling filing(String id, String company, String cik, String formType, String date, String primaryDocDescription)
            throws ParseException {
        return Filling.builder()
                .id(id)
                .company(company)
                .cik(cik)
                .formType(FormType.builder().number(formType).description(formType).build())
                .primaryDocDescription(primaryDocDescription)
                .fillingDate(new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(date))
                .build();
    }
}