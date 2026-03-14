package org.jds.edgar4j.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import org.jds.edgar4j.dto.request.FilingSearchRequest;
import org.jds.edgar4j.dto.response.PaginatedResponse;
import org.jds.edgar4j.model.Filling;
import org.jds.edgar4j.model.FormType;
import org.jds.edgar4j.port.FilingSearchPort;
import org.jds.edgar4j.port.FillingDataPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

@ExtendWith(MockitoExtension.class)
class FilingServiceImplTest {

    @Mock
    private FillingDataPort fillingDataPort;

    @Mock
    private FilingSearchPort filingSearchPort;

    private FilingServiceImpl filingService;

    @BeforeEach
    void setUp() {
        filingService = new FilingServiceImpl(fillingDataPort, filingSearchPort);
    }

    @Test
    @DisplayName("searchFilings should preserve search hit order when hydrating filings")
    void searchFilingsShouldPreserveSearchHitOrder() throws ParseException {
        when(filingSearchPort.search(any(FilingSearchPort.SearchCriteria.class), any()))
                .thenReturn(new PageImpl<>(List.of(
                        new FilingSearchPort.SearchResult("b", "10-k", "Second", "snippet", 3.0d, LocalDate.of(2024, 2, 1)),
                        new FilingSearchPort.SearchResult("a", "8-k", "First", "snippet", 2.0d, LocalDate.of(2024, 1, 1)))));
        when(fillingDataPort.findAllById(List.of("b", "a")))
                .thenReturn(List.of(
                        filing("a", "Alpha Co", "8-K", "2024-01-01"),
                        filing("b", "Bravo Co", "10-K", "2024-02-01")));

        PaginatedResponse<?> response = filingService.searchFilings(FilingSearchRequest.builder()
                .companyName("alpha")
                .build());

        assertThat(response.getContent()).extracting("id").containsExactly("b", "a");
        assertThat(response.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("searchFilings should translate request fields into search criteria")
    void searchFilingsShouldBuildSearchCriteriaFromRequest() {
        when(filingSearchPort.search(any(FilingSearchPort.SearchCriteria.class), any()))
                .thenAnswer(invocation -> {
                    FilingSearchPort.SearchCriteria criteria = invocation.getArgument(0);
                    assertThat(criteria.query()).isEqualTo("earnings growth");
                    assertThat(criteria.formTypes()).containsExactly("10-K");
                    assertThat(criteria.cik()).isEqualTo("1234567");
                    assertThat(criteria.symbol()).isEqualTo("ACME");
                    assertThat(criteria.startDate()).isEqualTo(LocalDate.of(2024, 1, 1));
                    assertThat(criteria.endDate()).isEqualTo(LocalDate.of(2024, 1, 31));
                    return new PageImpl<FilingSearchPort.SearchResult>(List.of());
                });
        when(fillingDataPort.findAllById(List.of())).thenReturn(List.of());

        filingService.searchFilings(FilingSearchRequest.builder()
                .keywords(List.of("earnings", "growth"))
                .formTypes(List.of("10-K"))
                .cik("1234567")
                .ticker("ACME")
                .dateFrom(java.sql.Date.valueOf(LocalDate.of(2024, 1, 1)))
                .dateTo(java.sql.Date.valueOf(LocalDate.of(2024, 1, 31)))
                .build());
    }

    private Filling filing(String id, String company, String formType, String filingDate) throws ParseException {
        return Filling.builder()
                .id(id)
                .company(company)
                .formType(FormType.builder().number(formType).build())
                .fillingDate(new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(filingDate))
                .build();
    }
}