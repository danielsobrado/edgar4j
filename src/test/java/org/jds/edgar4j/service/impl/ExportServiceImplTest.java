package org.jds.edgar4j.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jds.edgar4j.dto.request.ExportRequest;
import org.jds.edgar4j.dto.request.FilingSearchRequest;
import org.jds.edgar4j.dto.response.FilingResponse;
import org.jds.edgar4j.dto.response.PaginatedResponse;
import org.jds.edgar4j.model.Filling;
import org.jds.edgar4j.repository.FillingRepository;
import org.jds.edgar4j.service.FilingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

@ExtendWith(MockitoExtension.class)
class ExportServiceImplTest {

    @Mock
    private FillingRepository fillingRepository;

    @Mock
    private FilingService filingService;

    private ExportServiceImpl exportService;

    @BeforeEach
    void setUp() {
        exportService = new ExportServiceImpl(fillingRepository, filingService, new ObjectMapper());
        ReflectionTestUtils.setField(exportService, "maxExportRecords", 250);
    }

    @Test
    @DisplayName("exportToJson should collect all search result pages before loading filings")
    void exportToJsonShouldCollectAllSearchResultPages() throws IOException {
        FilingSearchRequest searchCriteria = FilingSearchRequest.builder()
                .companyName("Apple")
                .build();
        ExportRequest request = ExportRequest.builder()
                .format(ExportRequest.ExportFormat.JSON)
                .searchCriteria(searchCriteria)
                .build();
        List<FilingResponse> firstPageContent = new ArrayList<>();
        for (int index = 1; index <= 100; index++) {
            firstPageContent.add(filingResponse("id-" + index));
        }

        when(filingService.searchFilings(any(FilingSearchRequest.class)))
                .thenAnswer(invocation -> {
                    FilingSearchRequest pageRequest = invocation.getArgument(0);
                    if (pageRequest.getPage() == 0) {
                        return PaginatedResponse.of(firstPageContent, 0, pageRequest.getSize(), 101);
                    }
                    if (pageRequest.getPage() == 1) {
                        return PaginatedResponse.of(List.of(filingResponse("id-101")), 1, pageRequest.getSize(), 101);
                    }
                    throw new AssertionError("Unexpected export page: " + pageRequest.getPage());
                });
        when(fillingRepository.findAllById(any(List.class)))
                .thenAnswer(invocation -> ((List<String>) invocation.getArgument(0)).stream()
                        .map(this::filling)
                        .toList());

        byte[] payload = exportService.exportToJson(request);
        JsonNode root = new ObjectMapper().readTree(payload);

        assertEquals(101, root.size());
        assertEquals("id-1", root.get(0).get("id").asText());
        assertEquals("id-101", root.get(100).get("id").asText());
    }

    @Test
    @DisplayName("exportToJson should reject oversized search exports before loading filings")
    void exportToJsonShouldRejectOversizedSearchExports() {
        FilingSearchRequest searchCriteria = FilingSearchRequest.builder()
                .companyName("Apple")
                .build();
        ExportRequest request = ExportRequest.builder()
                .format(ExportRequest.ExportFormat.JSON)
                .searchCriteria(searchCriteria)
                .build();

        when(filingService.searchFilings(any(FilingSearchRequest.class)))
                .thenReturn(PaginatedResponse.of(List.of(filingResponse("id-1")), 0, 100, 251));

        assertThrows(IllegalArgumentException.class, () -> exportService.exportToJson(request));
    }

    private FilingResponse filingResponse(String id) {
        return FilingResponse.builder()
                .id(id)
                .build();
    }

    private Filling filling(String id) {
        return Filling.builder()
                .id(id)
                .build();
    }
}