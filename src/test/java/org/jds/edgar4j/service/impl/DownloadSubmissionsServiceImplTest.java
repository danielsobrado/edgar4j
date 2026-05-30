package org.jds.edgar4j.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.integration.SecApiClient;
import org.jds.edgar4j.integration.SecResponseParser;
import org.jds.edgar4j.integration.model.SecSubmissionResponse;
import org.jds.edgar4j.model.Filling;
import org.jds.edgar4j.model.FormType;
import org.jds.edgar4j.model.Submissions;
import org.jds.edgar4j.port.FillingDataPort;
import org.jds.edgar4j.port.SubmissionsDataPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DownloadSubmissionsServiceImplTest {

    @Mock
    private SecApiClient secApiClient;

    @Mock
    private SecResponseParser responseParser;

    @Mock
    private SubmissionsDataPort submissionsRepository;

    @Mock
    private FillingDataPort fillingRepository;

    @InjectMocks
    private DownloadSubmissionsServiceImpl service;

    @Test
    void downloadSubmissionsByDateShouldFetchMatchingArchiveFilesAndFilterByDateAndForm() {
        String cik = "0000789019";
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 3, 31);
        String responseJson = "{\"cik\":\"0000789019\"}";
        String archiveJson = "{\"filings\":{\"recent\":{\"accessionNumber\":[\"ARCHIVE_MATCH_1\",\"ARCHIVE_MATCH_2\",\"ARCHIVE_DUP_1\",\"ARCHIVE_DUP_1\"],\"filingDate\":[\"2026-03-05\",\"2026-03-06\",\"2026-03-06\",\"2026-03-06\"],\"form\":[\"13F\",\"13F\",\"4\",\"13F\"]}}}";

        SecSubmissionResponse response = responseFromFiles(
                filingFile("a-2026-03.json", "2026-02-01", "2026-03-31"),
                filingFile("a-2026-04.json", "2026-04-01", "2026-04-30"));

        when(secApiClient.fetchSubmissions(cik)).thenReturn(responseJson);
        when(responseParser.parseSubmissionResponse(responseJson)).thenReturn(response);
        when(responseParser.toFillings(response)).thenReturn(List.of(
                filing("RECENT_13F_OUT_OF_RANGE", "10-K", LocalDate.of(2025, 12, 15)),
                filing("RECENT_MATCH", "13F", LocalDate.of(2026, 3, 3))
        ));
        when(responseParser.toFillings(archiveJson, cik)).thenReturn(List.of(
                filing("ARCHIVE_MATCH_1", "13F", LocalDate.of(2026, 3, 5)),
                filing("ARCHIVE_MATCH_2", "13F", LocalDate.of(2026, 3, 6)),
                filing("ARCHIVE_DUP_1", "13F", LocalDate.of(2026, 3, 6)),
                filing("ARCHIVE_DUP_1", "13F", LocalDate.of(2026, 3, 6))
        ));
        when(secApiClient.fetchSubmissionFile("a-2026-03.json")).thenReturn(archiveJson);

        when(responseParser.toSubmissions(response)).thenReturn(new Submissions());
        when(submissionsRepository.save(any(Submissions.class))).thenReturn(new Submissions());
        when(fillingRepository.findByAccessionNumber(any(String.class))).thenReturn(Optional.empty());
        when(fillingRepository.saveAll(any(List.class))).thenAnswer(invocation -> invocation.getArgument(0));

        long saved = service.downloadSubmissions(cik, "13F", from, to);

        assertEquals(4L, saved);
        verify(secApiClient).fetchSubmissionFile("a-2026-03.json");
        verify(secApiClient, never()).fetchSubmissionFile("a-2026-04.json");

        ArgumentCaptor<List<Filling>> savedFillingsCaptor = ArgumentCaptor.forClass(List.class);
        verify(fillingRepository).saveAll(savedFillingsCaptor.capture());
        List<Filling> savedFillings = savedFillingsCaptor.getValue();
        assertEquals(4, savedFillings.size());
        assertEquals(
                List.of("RECENT_MATCH", "ARCHIVE_MATCH_1", "ARCHIVE_MATCH_2", "ARCHIVE_DUP_1"),
                savedFillings.stream().map(Filling::getAccessionNumber).toList());
    }

    @Test
    void downloadSubmissionsByDateShouldSkipArchiveFilesOutsideRequestedRange() {
        String cik = "0000789019";
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 3, 31);
        String responseJson = "{\"name\":\"No Archive Match\"}";
        SecSubmissionResponse response = responseFromFiles(
                filingFile("a-2026-02.json", "2026-02-01", "2026-02-28"),
                filingFile("a-2026-01.json", "2026-01-01", "2026-01-31"));

        when(secApiClient.fetchSubmissions(cik)).thenReturn(responseJson);
        when(responseParser.parseSubmissionResponse(responseJson)).thenReturn(response);
        when(responseParser.toFillings(response)).thenReturn(List.of(
                filing("RECENT_13F", "13F", LocalDate.of(2026, 3, 15))
        ));
        when(responseParser.toSubmissions(response)).thenReturn(new Submissions());
        when(submissionsRepository.save(any(Submissions.class))).thenReturn(new Submissions());
        when(fillingRepository.findByAccessionNumber(any(String.class))).thenReturn(Optional.empty());
        when(fillingRepository.saveAll(any(List.class))).thenAnswer(invocation -> invocation.getArgument(0));

        long saved = service.downloadSubmissions(cik, "13F", from, to);

        assertEquals(1L, saved);
        verify(secApiClient, never()).fetchSubmissionFile(any());
    }

    private Filling filing(String accessionNumber, String formType, LocalDate filingDate) {
        return Filling.builder()
                .accessionNumber(accessionNumber)
                .formType(FormType.builder().number(formType).build())
                .fillingDate(Date.from(filingDate.atStartOfDay().toInstant(java.time.ZoneOffset.UTC)))
                .build();
    }

    private SecSubmissionResponse responseFromFiles(SecSubmissionResponse.FilesEntry... fileEntries) {
        SecSubmissionResponse response = new SecSubmissionResponse();
        response.setName("Test Company");
        SecSubmissionResponse.Filings filings = new SecSubmissionResponse.Filings();
        filings.setFiles(List.of(fileEntries));
        response.setFilings(filings);
        return response;
    }

    private SecSubmissionResponse.FilesEntry filingFile(String name, String filingFrom, String filingTo) {
        SecSubmissionResponse.FilesEntry entry = new SecSubmissionResponse.FilesEntry();
        entry.setName(name);
        entry.setFilingFrom(filingFrom);
        entry.setFilingTo(filingTo);
        return entry;
    }
}
