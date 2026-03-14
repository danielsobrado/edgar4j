package org.jds.edgar4j.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.jds.edgar4j.config.AppConstants;
import org.jds.edgar4j.dto.request.ExportRequest;
import org.jds.edgar4j.dto.request.FilingSearchRequest;
import org.jds.edgar4j.dto.response.FilingResponse;
import org.jds.edgar4j.dto.response.PaginatedResponse;
import org.jds.edgar4j.model.Filling;
import org.jds.edgar4j.port.FillingDataPort;
import org.jds.edgar4j.service.ExportService;
import org.jds.edgar4j.service.FilingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExportServiceImpl implements ExportService {

    private final FillingDataPort fillingRepository;
    private final FilingService filingService;
    private final ObjectMapper objectMapper;

    @Value("${edgar4j.export.max-records:10000}")
    private int maxExportRecords;

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    @Override
    public byte[] exportToCsv(ExportRequest request) {
        log.info("Exporting to CSV: {}", request);

        List<Filling> fillings = getFillings(request);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8))) {

            writer.println("ID,Company,CIK,Form Type,Filing Date,Report Date,Accession Number,Primary Document,URL,XBRL,Inline XBRL");

            for (Filling f : fillings) {
                writer.println(String.join(",",
                        escapeCSV(f.getId()),
                        escapeCSV(f.getCompany()),
                        escapeCSV(f.getCik()),
                        escapeCSV(f.getFormType() != null ? f.getFormType().getNumber() : ""),
                        escapeCSV(f.getFillingDate() != null ? DATE_FORMAT.format(f.getFillingDate()) : ""),
                        escapeCSV(f.getReportDate() != null ? DATE_FORMAT.format(f.getReportDate()) : ""),
                        escapeCSV(f.getAccessionNumber()),
                        escapeCSV(f.getPrimaryDocument()),
                        escapeCSV(f.getUrl()),
                        String.valueOf(f.isXBRL()),
                        String.valueOf(f.isInlineXBRL())
                ));
            }

            writer.flush();
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Error exporting to CSV", e);
            throw new RuntimeException("Failed to export to CSV", e);
        }
    }

    @Override
    public byte[] exportToJson(ExportRequest request) {
        log.info("Exporting to JSON: {}", request);

        List<Filling> fillings = getFillings(request);

        try {
            ObjectMapper mapper = objectMapper.copy();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            return mapper.writeValueAsBytes(fillings);
        } catch (Exception e) {
            log.error("Error exporting to JSON", e);
            throw new RuntimeException("Failed to export to JSON", e);
        }
    }

    private List<Filling> getFillings(ExportRequest request) {
        if (request.getFilingIds() != null && !request.getFilingIds().isEmpty()) {
            enforceExportLimit(request.getFilingIds().size());
            return StreamSupport.stream(
                    fillingRepository.findAllById(request.getFilingIds()).spliterator(),
                    false
            ).collect(Collectors.toList());
        } else if (request.getSearchCriteria() != null) {
            return loadSearchResultFillings(request.getSearchCriteria());
        }
        return fillingRepository.findTop10ByOrderByFillingDateDesc();
    }

    private List<Filling> loadSearchResultFillings(FilingSearchRequest searchCriteria) {
        int exportPageSize = Math.min(maxExportRecords, AppConstants.MAX_PAGE_SIZE);
        PaginatedResponse<FilingResponse> firstPage = filingService.searchFilings(
                buildExportSearchRequest(searchCriteria, 0, exportPageSize));

        enforceExportLimit(Math.toIntExact(Math.min(firstPage.getTotalElements(), Integer.MAX_VALUE)));

        LinkedHashSet<String> filingIds = new LinkedHashSet<>();
        collectFilingIds(firstPage, filingIds);

        for (int page = 1; page < firstPage.getTotalPages(); page++) {
            PaginatedResponse<FilingResponse> nextPage = filingService.searchFilings(
                    buildExportSearchRequest(searchCriteria, page, exportPageSize));
            collectFilingIds(nextPage, filingIds);
        }

        return loadFillingsByIds(new ArrayList<>(filingIds));
    }

    private FilingSearchRequest buildExportSearchRequest(FilingSearchRequest searchCriteria, int page, int size) {
        return FilingSearchRequest.builder()
                .companyName(searchCriteria.getCompanyName())
                .ticker(searchCriteria.getTicker())
                .cik(searchCriteria.getCik())
                .formTypes(searchCriteria.getFormTypes())
                .dateFrom(searchCriteria.getDateFrom())
                .dateTo(searchCriteria.getDateTo())
                .keywords(searchCriteria.getKeywords())
                .page(page)
                .size(size)
                .sortBy(searchCriteria.getSortBy())
                .sortDir(searchCriteria.getSortDir())
                .build();
    }

    private void collectFilingIds(PaginatedResponse<FilingResponse> searchResult, LinkedHashSet<String> filingIds) {
        searchResult.getContent().stream()
                .map(FilingResponse::getId)
                .forEach(filingIds::add);
    }

    private List<Filling> loadFillingsByIds(List<String> filingIds) {
        Map<String, Filling> fillingsById = new LinkedHashMap<>();
        fillingRepository.findAllById(filingIds).forEach(filling -> fillingsById.put(filling.getId(), filling));
        List<Filling> fillings = new ArrayList<>();
        for (String filingId : filingIds) {
            Filling filling = fillingsById.get(filingId);
            if (filling != null) {
                fillings.add(filling);
            }
        }
        return fillings;
    }

    private void enforceExportLimit(int resultCount) {
        if (resultCount > maxExportRecords) {
            throw new IllegalArgumentException(
                    "Export exceeds configured maximum of " + maxExportRecords + " records");
        }
    }

    private String escapeCSV(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}

