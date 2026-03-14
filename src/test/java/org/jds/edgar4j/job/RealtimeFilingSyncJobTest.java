package org.jds.edgar4j.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.jds.edgar4j.integration.SecAccessDiagnostics;
import org.jds.edgar4j.integration.SecApiClient;
import org.jds.edgar4j.model.AppSettings;
import org.jds.edgar4j.model.Filling;
import org.jds.edgar4j.model.Form13DG;
import org.jds.edgar4j.model.Form13F;
import org.jds.edgar4j.model.Form20F;
import org.jds.edgar4j.model.Form3;
import org.jds.edgar4j.model.Form4;
import org.jds.edgar4j.model.Form5;
import org.jds.edgar4j.model.Form6K;
import org.jds.edgar4j.model.Form8K;
import org.jds.edgar4j.model.FormType;
import org.jds.edgar4j.port.AppSettingsDataPort;
import org.jds.edgar4j.port.FillingDataPort;
import org.jds.edgar4j.service.DownloadSubmissionsService;
import org.jds.edgar4j.service.Form13DGService;
import org.jds.edgar4j.service.Form13FService;
import org.jds.edgar4j.service.Form20FService;
import org.jds.edgar4j.service.Form3Service;
import org.jds.edgar4j.service.Form4Service;
import org.jds.edgar4j.service.Form5Service;
import org.jds.edgar4j.service.Form6KService;
import org.jds.edgar4j.service.Form8KService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class RealtimeFilingSyncJobTest {

    @Mock
    private SecApiClient secApiClient;

    @Mock
    private DownloadSubmissionsService downloadSubmissionsService;

    @Mock
    private FillingDataPort fillingRepository;

    @Mock
    private AppSettingsDataPort appSettingsRepository;

    @Mock
    private Form3Service form3Service;

    @Mock
    private Form4Service form4Service;

    @Mock
    private Form5Service form5Service;

    @Mock
    private Form6KService form6KService;

    @Mock
    private Form8KService form8KService;

    @Mock
    private Form13DGService form13DGService;

    @Mock
    private Form13FService form13FService;

    @Mock
    private Form20FService form20FService;

    @Test
    @DisplayName("syncRecentFilings should skip execution when realtime sync is disabled")
    void syncRecentFilingsShouldSkipWhenDisabled() {
        when(appSettingsRepository.findById("default")).thenReturn(Optional.of(
                AppSettings.builder().realtimeSyncEnabled(Boolean.FALSE).build()));

        RealtimeFilingSyncJob job = createJob();

        job.syncRecentFilings();

        verifyNoInteractions(secApiClient, fillingRepository, downloadSubmissionsService, form3Service, form4Service,
                form5Service, form6KService, form8KService, form13DGService, form13FService, form20FService);
        assertFalse(job.isRunning());
    }

    @Test
    @DisplayName("syncRecentFilings should route supported form types to their services")
    void syncRecentFilingsShouldRouteSupportedFormTypes() {
        String form4Accession = "0000320193-24-000001";
        String form8KAccession = "0000789019-24-000002";
        when(appSettingsRepository.findById("default")).thenReturn(Optional.of(
                AppSettings.builder()
                        .realtimeSyncEnabled(Boolean.TRUE)
                        .realtimeSyncForms("4,8-K")
                        .realtimeSyncMaxPages(1)
                        .realtimeSyncPageSize(50)
                        .build()));
        when(secApiClient.fetchEftsSearch(eq("4,8-K"), any(LocalDate.class), any(LocalDate.class), eq(0), eq(50)))
                .thenReturn("""
                        {"hits":{"total":{"value":2,"relation":"eq"},"hits":[
                          {"_id":"000032019324000001","_source":{"form_type":"4","entity_id":["320193"],"file_num":["001-12345"]}},
                          {"_id":"000078901924000002","_source":{"form_type":"8-K","entity_id":["789019"]}}
                        ]}}
                        """);
        when(fillingRepository.findByAccessionNumber(form4Accession)).thenReturn(Optional.of(
                Filling.builder()
                        .accessionNumber(form4Accession)
                        .cik("0000320193")
                        .formType(FormType.builder().number("4").build())
                        .primaryDocument("xslF345X05/doc4.xml")
                        .build()));
        when(fillingRepository.findByAccessionNumber(form8KAccession)).thenReturn(Optional.of(
                Filling.builder()
                        .accessionNumber(form8KAccession)
                        .cik("0000789019")
                        .formType(FormType.builder().number("8-K").build())
                        .primaryDocument("msft-8k.htm")
                        .build()));
        when(form4Service.existsByAccessionNumber(form4Accession)).thenReturn(false);
        when(form8KService.existsByAccessionNumber(form8KAccession)).thenReturn(false);
        when(form4Service.downloadAndParseForm4("0000320193", form4Accession, "doc4.xml"))
                .thenReturn(CompletableFuture.completedFuture(Form4.builder().accessionNumber(form4Accession).build()));
        when(form8KService.downloadAndParse("0000789019", form8KAccession, "msft-8k.htm"))
                .thenReturn(CompletableFuture.completedFuture(Form8K.builder().accessionNumber(form8KAccession).build()));
        when(form4Service.save(any(Form4.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(form8KService.save(any(Form8K.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RealtimeFilingSyncJob job = createJob();

        job.syncRecentFilings();

        verify(form4Service).downloadAndParseForm4("0000320193", form4Accession, "doc4.xml");
        verify(form8KService).downloadAndParse("0000789019", form8KAccession, "msft-8k.htm");
        verify(form4Service).save(any(Form4.class));
        verify(form8KService).save(any(Form8K.class));
        assertEquals(2, job.getLastSyncNewCount());
        assertEquals(2, job.getLastSyncTotalScanned());
        assertFalse(job.isRunning());
    }

    @Test
    @DisplayName("syncRecentFilings should resolve 13F attachments from filing index when submissions metadata is not present yet")
    void syncRecentFilingsShouldResolve13FAttachmentsFromFilingIndex() {
        String accessionNumber = "0001166559-25-000001";
        when(appSettingsRepository.findById("default")).thenReturn(Optional.of(
                AppSettings.builder()
                        .realtimeSyncEnabled(Boolean.TRUE)
                        .realtimeSyncForms("13F-HR")
                        .realtimeSyncMaxPages(1)
                        .realtimeSyncPageSize(10)
                        .build()));
        when(secApiClient.fetchEftsSearch(eq("13F-HR"), any(LocalDate.class), any(LocalDate.class), eq(0), eq(10)))
                .thenReturn("""
                        {"hits":{"total":{"value":1,"relation":"eq"},"hits":[
                          {"_id":"000116655925000001","_source":{"form_type":"13F-HR","entity_id":["1166559"]}}
                        ]}}
                        """);
        when(fillingRepository.findByAccessionNumber(accessionNumber))
                .thenReturn(Optional.empty(), Optional.empty());
        when(secApiClient.fetchFiling("0001166559", accessionNumber, "index.json"))
                .thenReturn("""
                        {"directory":{"item":[
                          {"name":"primary_doc.xml"},
                          {"name":"infotable.xml"}
                        ]}}
                        """);
        when(form13FService.existsByAccessionNumber(accessionNumber)).thenReturn(false);
        when(form13FService.downloadAndParseForm13F(
                "0001166559",
                accessionNumber,
                "primary_doc.xml",
                "infotable.xml"))
                .thenReturn(CompletableFuture.completedFuture(Form13F.builder().accessionNumber(accessionNumber).build()));
        when(form13FService.save(any(Form13F.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RealtimeFilingSyncJob job = createJob();

        job.syncRecentFilings();

        verify(downloadSubmissionsService).downloadSubmissions("0001166559");
        verify(secApiClient).fetchFiling("0001166559", accessionNumber, "index.json");
        verify(form13FService).downloadAndParseForm13F(
                "0001166559",
                accessionNumber,
                "primary_doc.xml",
                "infotable.xml");
        assertEquals(1, job.getLastSyncNewCount());
        assertEquals(1, job.getLastSyncTotalScanned());
    }

    @Test
    @DisplayName("syncRecentFilings should skip duplicate or already-ingested accessions")
    void syncRecentFilingsShouldSkipDuplicateOrExistingAccessions() {
        String accessionNumber = "0000320193-24-000001";
        when(appSettingsRepository.findById("default")).thenReturn(Optional.of(
                AppSettings.builder()
                        .realtimeSyncEnabled(Boolean.TRUE)
                        .realtimeSyncForms("4")
                        .realtimeSyncMaxPages(1)
                        .realtimeSyncPageSize(10)
                        .build()));
        when(secApiClient.fetchEftsSearch(eq("4"), any(LocalDate.class), any(LocalDate.class), eq(0), eq(10)))
                .thenReturn("""
                        {"hits":{"total":{"value":2,"relation":"eq"},"hits":[
                          {"_id":"000032019324000001","_source":{"form_type":"4","entity_id":["320193"]}},
                          {"_id":"000032019324000001","_source":{"form_type":"4","entity_id":["320193"]}}
                        ]}}
                        """);
        when(fillingRepository.findByAccessionNumber(accessionNumber)).thenReturn(Optional.of(
                Filling.builder()
                        .accessionNumber(accessionNumber)
                        .cik("0000320193")
                        .formType(FormType.builder().number("4").build())
                        .primaryDocument("doc4.xml")
                        .build()));
        when(form4Service.existsByAccessionNumber(accessionNumber)).thenReturn(true);

        RealtimeFilingSyncJob job = createJob();

        job.syncRecentFilings();

        verify(form4Service, never()).downloadAndParseForm4(any(), any(), any());
        assertEquals(0, job.getLastSyncNewCount());
        assertEquals(2, job.getLastSyncTotalScanned());
    }

    @Test
    @DisplayName("syncRecentFilings should use an hour-based start date instead of rounding the lookback to full days")
    void syncRecentFilingsShouldUseHourBasedStartDate() {
        RealtimeFilingSyncJob job = spy(createJob());
        doReturn(LocalDateTime.of(2026, 3, 12, 15, 30)).when(job).currentDateTime();
        when(appSettingsRepository.findById("default")).thenReturn(Optional.of(
                AppSettings.builder()
                        .realtimeSyncEnabled(Boolean.TRUE)
                        .realtimeSyncForms("4")
                        .realtimeSyncLookbackHours(1)
                        .realtimeSyncMaxPages(1)
                        .realtimeSyncPageSize(10)
                        .build()));
        when(secApiClient.fetchEftsSearch(
                eq("4"),
                eq(LocalDate.of(2026, 3, 12)),
                eq(LocalDate.of(2026, 3, 12)),
                eq(0),
                eq(10)))
                .thenReturn("{\"hits\":{\"total\":{\"value\":0,\"relation\":\"eq\"},\"hits\":[]}}");

        job.syncRecentFilings();

        verify(secApiClient).fetchEftsSearch(
                "4",
                LocalDate.of(2026, 3, 12),
                LocalDate.of(2026, 3, 12),
                0,
                10);
        assertEquals(0, job.getLastSyncTotalScanned());
    }

    @Test
    @DisplayName("syncRecentFilings should enter cooldown after an SEC automation block")
    void syncRecentFilingsShouldEnterCooldownAfterSecAutomationBlock() {
        when(appSettingsRepository.findById("default")).thenReturn(Optional.of(
                AppSettings.builder()
                        .realtimeSyncEnabled(Boolean.TRUE)
                        .realtimeSyncForms("4")
                        .realtimeSyncMaxPages(1)
                        .realtimeSyncPageSize(10)
                        .build()));
        when(secApiClient.fetchEftsSearch(eq("4"), any(LocalDate.class), any(LocalDate.class), eq(0), eq(10)))
                .thenThrow(new RuntimeException(SecAccessDiagnostics.buildUndeclaredAutomationBlockMessage(
                        "https://efts.sec.gov/search",
                        "ref-123")));

        RealtimeFilingSyncJob job = createJob();

        job.syncRecentFilings();
        job.syncRecentFilings();

        verify(secApiClient, times(1)).fetchEftsSearch(eq("4"), any(LocalDate.class), any(LocalDate.class), eq(0), eq(10));
        assertFalse(job.isRunning());
    }

    private RealtimeFilingSyncJob createJob() {
                return new RealtimeFilingSyncJob(
                secApiClient,
                downloadSubmissionsService,
                fillingRepository,
                appSettingsRepository,
                form3Service,
                form4Service,
                form5Service,
                form6KService,
                form8KService,
                form13DGService,
                form13FService,
                form20FService,
                                new ObjectMapper(),
                                true,
                                "4",
                                1,
                                10,
                                100,
                                10);
    }
}
