package org.jds.edgar4j.service;

import org.jds.edgar4j.integration.SecApiClient;
import org.jds.edgar4j.integration.SecResponseParser;
import org.jds.edgar4j.integration.model.SecSubmissionResponse;
import org.jds.edgar4j.model.Filling;
import org.jds.edgar4j.model.Submissions;
import org.jds.edgar4j.repository.FillingRepository;
import org.jds.edgar4j.repository.SubmissionsRepository;
import org.jds.edgar4j.service.impl.DownloadSubmissionsServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author J. Daniel Sobrado
 * @version 1.1
 * @since 2022-09-18
 */
@ExtendWith(MockitoExtension.class)
class DownloadSubmissionsServiceTests {

    @Mock
    private SecApiClient secApiClient;

    @Mock
    private SecResponseParser responseParser;

    @Mock
    private SubmissionsRepository submissionsRepository;

    @Mock
    private FillingRepository fillingRepository;

    @InjectMocks
    private DownloadSubmissionsServiceImpl downloadSubmissionsService;

    @DisplayName("Should download submissions for valid CIK")
    @Test
    void testDownloadSubmissions_ValidCIK_Success() {
        // Given
        String cik = "789019";
        String jsonResponse = "{\"name\":\"Microsoft Corporation\"}";
        SecSubmissionResponse response = new SecSubmissionResponse();
        response.setCik(cik);
        response.setName("Microsoft Corporation");
        Submissions submissions = Submissions.builder().cik(cik).companyName("Microsoft Corporation").build();
        Filling filing = Filling.builder().accessionNumber("0000000000-24-000001").build();

        when(secApiClient.fetchSubmissions(cik)).thenReturn(jsonResponse);
        when(responseParser.parseSubmissionResponse(jsonResponse)).thenReturn(response);
        when(responseParser.toSubmissions(response)).thenReturn(submissions);
        when(responseParser.toFillings(response)).thenReturn(List.of(filing));
        when(submissionsRepository.findByCik(cik)).thenReturn(Optional.empty());
        when(fillingRepository.findByAccessionNumber(filing.getAccessionNumber())).thenReturn(Optional.empty());
        
        // When
        downloadSubmissionsService.downloadSubmissions(cik);
        
        // Then
        verify(secApiClient).fetchSubmissions(cik);
        verify(responseParser).parseSubmissionResponse(jsonResponse);
        verify(submissionsRepository).save(submissions);
        verify(fillingRepository).saveAll(List.of(filing));
    }

    @DisplayName("Should reject invalid CIK format")
    @Test
    void testDownloadSubmissions_InvalidCIK_ThrowsException() {
        // Given
        String invalidCik = "invalid";
        
        // When / Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> downloadSubmissionsService.downloadSubmissions(invalidCik));

        assertEquals("Invalid CIK format: invalid", exception.getMessage());
        verifyNoInteractions(secApiClient, responseParser, submissionsRepository, fillingRepository);
    }

    @DisplayName("Should reuse existing ids when submissions already exist")
    @Test
    void testDownloadSubmissions_UpdatesExistingRecords() {
        // Given
        String cik = "789019";
        String jsonResponse = "{\"name\":\"Microsoft Corporation\"}";
        SecSubmissionResponse response = new SecSubmissionResponse();
        response.setCik(cik);
        response.setName("Microsoft Corporation");

        Submissions mappedSubmissions = Submissions.builder().cik(cik).companyName("Microsoft Corporation").build();
        Submissions existingSubmissions = Submissions.builder().id("existing-submissions").cik(cik).build();

        Filling mappedFiling = Filling.builder().accessionNumber("0000000000-24-000001").build();
        Filling existingFiling = Filling.builder().id("existing-filing").accessionNumber(mappedFiling.getAccessionNumber()).build();

        when(secApiClient.fetchSubmissions(cik)).thenReturn(jsonResponse);
        when(responseParser.parseSubmissionResponse(jsonResponse)).thenReturn(response);
        when(responseParser.toSubmissions(response)).thenReturn(mappedSubmissions);
        when(responseParser.toFillings(response)).thenReturn(List.of(mappedFiling));
        when(submissionsRepository.findByCik(cik)).thenReturn(Optional.of(existingSubmissions));
        when(fillingRepository.findByAccessionNumber(mappedFiling.getAccessionNumber())).thenReturn(Optional.of(existingFiling));
        
        // When
        downloadSubmissionsService.downloadSubmissions(cik);
        
        // Then
        ArgumentCaptor<Submissions> submissionsCaptor = ArgumentCaptor.forClass(Submissions.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Filling>> fillingsCaptor = ArgumentCaptor.forClass(List.class);

        verify(submissionsRepository).save(submissionsCaptor.capture());
        verify(fillingRepository).saveAll(fillingsCaptor.capture());

        assertEquals("existing-submissions", submissionsCaptor.getValue().getId());
        assertEquals("existing-filing", fillingsCaptor.getValue().get(0).getId());
    }
}
