package org.jds.edgar4j.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.jds.edgar4j.dto.request.ExportRequest;
import org.jds.edgar4j.dto.response.FilingResponse;
import org.jds.edgar4j.model.Filling;
import org.jds.edgar4j.repository.FillingRepository;
import org.jds.edgar4j.service.ExportService;
import org.jds.edgar4j.service.FilingService;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExportServiceImpl implements ExportService {

    private final FillingRepository fillingRepository;
    private final FilingService filingService;
    private final ObjectMapper objectMapper;

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
            return StreamSupport.stream(
                    fillingRepository.findAllById(request.getFilingIds()).spliterator(),
                    false
            ).collect(Collectors.toList());
        } else if (request.getSearchCriteria() != null) {
            var searchResult = filingService.searchFilings(request.getSearchCriteria());
            return searchResult.getContent().stream()
                    .map(FilingResponse::getId)
                    .map(fillingRepository::findById)
                    .filter(java.util.Optional::isPresent)
                    .map(java.util.Optional::get)
                    .collect(Collectors.toList());
        }
        return fillingRepository.findTop10ByOrderByFillingDateDesc();
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

