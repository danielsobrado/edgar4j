package org.jds.edgar4j.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.dto.request.RemoteFilingSearchRequest;
import org.jds.edgar4j.dto.response.RemoteFilingSearchResponse;
import org.jds.edgar4j.integration.SecApiClient;
import org.jds.edgar4j.integration.SecApiConfig;
import org.jds.edgar4j.integration.SecResponseParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class RemoteEdgarServiceImplTest {

    private RemoteEdgarServiceImpl createService(SecApiClient secApiClient) {
        SecApiConfig secApiConfig = new SecApiConfig();
        ReflectionTestUtils.setField(secApiConfig, "baseSecUrl", "https://www.sec.gov");
        return new RemoteEdgarServiceImpl(secApiClient, secApiConfig, mock(SecResponseParser.class));
    }

    @Test
    @DisplayName("searchRemoteFilings should match form families and report truncated preview metadata")
    void searchRemoteFilingsShouldMatchFormFamilies() {
        SecApiClient secApiClient = mock(SecApiClient.class);
        RemoteEdgarServiceImpl remoteEdgarService = createService(secApiClient);

        LocalDate latestDate = LocalDate.of(2026, 3, 12);
        LocalDate middleDate = LocalDate.of(2026, 3, 11);
        LocalDate earliestDate = LocalDate.of(2026, 3, 10);

        String latestIndex = """
                Description: Master Index of EDGAR Dissemination System
                CIK|Company Name|Form Type|Date Filed|Filename
                123456|ALPHA CAPITAL|13F-HR|2026-03-12|edgar/data/123456/0001234567-26-000001.txt
                999999|IGNORED COMPANY|4|2026-03-12|edgar/data/999999/0009999999-26-000001.txt
                234567|BETA CAPITAL|13F-NT|2026-03-12|edgar/data/234567/0002345678-26-000002.txt
                """;
        String earliestIndex = """
                Description: Master Index of EDGAR Dissemination System
                CIK|Company Name|Form Type|Date Filed|Filename
                345678|GAMMA CAPITAL|4|2026-03-10|edgar/data/345678/0003456789-26-000003.txt
                """;

        when(secApiClient.fetchDailyMasterIndex(latestDate)).thenReturn(Optional.of(latestIndex));
        when(secApiClient.fetchDailyMasterIndex(middleDate)).thenReturn(Optional.empty());
        when(secApiClient.fetchDailyMasterIndex(earliestDate)).thenReturn(Optional.of(earliestIndex));

        RemoteFilingSearchResponse response = remoteEdgarService.searchRemoteFilings(RemoteFilingSearchRequest.builder()
                .formType("13F")
                .dateFrom(earliestDate)
                .dateTo(latestDate)
                .limit(1)
                .build());

        assertEquals("13F", response.getFormType());
        assertEquals(2, response.getTotalMatches());
        assertEquals(1, response.getReturnedMatches());
        assertEquals(2, response.getUniqueCompanyCount());
        assertEquals(3, response.getSearchedDateCount());
        assertEquals(2, response.getAvailableDateCount());
        assertEquals(1, response.getUnavailableDateCount());
        assertTrue(response.isTruncated());
        assertEquals("0001234567-26-000001", response.getFilings().get(0).getAccessionNumber());
        assertEquals("https://www.sec.gov/Archives/edgar/data/123456/0001234567-26-000001.txt",
                response.getFilings().get(0).getFilingUrl());
    }

    @Test
    @DisplayName("findMatchingCompanyCiks should deduplicate companies across the full search range")
    void findMatchingCompanyCiksShouldDeduplicateCompanies() {
        SecApiClient secApiClient = mock(SecApiClient.class);
        RemoteEdgarServiceImpl remoteEdgarService = createService(secApiClient);

        LocalDate firstDate = LocalDate.of(2026, 3, 12);
        LocalDate secondDate = LocalDate.of(2026, 3, 11);

        String firstIndex = """
                Description: Master Index of EDGAR Dissemination System
                CIK|Company Name|Form Type|Date Filed|Filename
                123456|ALPHA CAPITAL|13F-HR|2026-03-12|edgar/data/123456/0001234567-26-000001.txt
                """;
        String secondIndex = """
                Description: Master Index of EDGAR Dissemination System
                CIK|Company Name|Form Type|Date Filed|Filename
                123456|ALPHA CAPITAL|13F-HR/A|2026-03-11|edgar/data/123456/0001234567-26-000002.txt
                234567|BETA CAPITAL|13F-HR|2026-03-11|edgar/data/234567/0002345678-26-000003.txt
                """;

        when(secApiClient.fetchDailyMasterIndex(firstDate)).thenReturn(Optional.of(firstIndex));
        when(secApiClient.fetchDailyMasterIndex(secondDate)).thenReturn(Optional.of(secondIndex));

        List<String> ciks = remoteEdgarService.findMatchingCompanyCiks(RemoteFilingSearchRequest.builder()
                .formType("13F")
                .dateFrom(secondDate)
                .dateTo(firstDate)
                .build());

        assertEquals(List.of("0000123456", "0000234567"), ciks);
    }

    @Test
    @DisplayName("searchRemoteFilings should reject inverted date ranges")
    void searchRemoteFilingsShouldRejectInvalidDateRange() {
        RemoteEdgarServiceImpl remoteEdgarService = createService(mock(SecApiClient.class));

        RemoteFilingSearchRequest request = RemoteFilingSearchRequest.builder()
                .formType("13F")
                .dateFrom(LocalDate.of(2026, 3, 12))
                .dateTo(LocalDate.of(2026, 3, 1))
                .build();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> remoteEdgarService.searchRemoteFilings(request));

        assertEquals("dateFrom must be on or before dateTo", exception.getMessage());
    }

    @Test
    @DisplayName("searchRemoteFilings should stop at the requested max forms when dates are not provided")
    void searchRemoteFilingsShouldUseMaxFormsWithoutDates() {
        SecApiClient secApiClient = mock(SecApiClient.class);
        RemoteEdgarServiceImpl remoteEdgarService = createService(secApiClient);

        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        String todayIndex = """
                Description: Master Index of EDGAR Dissemination System
                CIK|Company Name|Form Type|Date Filed|Filename
                123456|ALPHA CAPITAL|13F-HR|%s|edgar/data/123456/0001234567-26-000001.txt
                """.formatted(today);
        String yesterdayIndex = """
                Description: Master Index of EDGAR Dissemination System
                CIK|Company Name|Form Type|Date Filed|Filename
                234567|ALPHA CAPITAL PARTNERS|13F-HR|%s|edgar/data/234567/0002345678-26-000002.txt
                345678|ALPHA CAPITAL PARTNERS|13F-HR|%s|edgar/data/345678/0003456789-26-000003.txt
                """.formatted(yesterday, yesterday);

        when(secApiClient.fetchDailyMasterIndex(today)).thenReturn(Optional.of(todayIndex));
        when(secApiClient.fetchDailyMasterIndex(yesterday)).thenReturn(Optional.of(yesterdayIndex));

        RemoteFilingSearchResponse response = remoteEdgarService.searchRemoteFilings(RemoteFilingSearchRequest.builder()
                .companyName("alpha capital")
                .formType("13F")
                .limit(2)
                .build());

        assertEquals(2, response.getTotalMatches());
        assertEquals(2, response.getReturnedMatches());
        assertEquals(2, response.getUniqueCompanyCount());
        assertEquals(today.toString(), response.getDateTo());
        assertEquals(yesterday.toString(), response.getDateFrom());
        assertEquals(2, response.getSearchedDateCount());
        assertTrue(response.isTruncated());
    }
}
