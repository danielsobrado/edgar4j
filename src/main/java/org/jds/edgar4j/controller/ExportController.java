package org.jds.edgar4j.controller;

import org.jds.edgar4j.dto.request.ExportRequest;
import org.jds.edgar4j.service.ExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;

    @PostMapping("/csv")
    public ResponseEntity<byte[]> exportToCsv(@RequestBody ExportRequest request) {
        log.info("POST /api/export/csv: {}", request);

        byte[] csvData = exportService.exportToCsv(request);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment", "filings-export.csv");
        headers.setContentLength(csvData.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(csvData);
    }

    @PostMapping("/json")
    public ResponseEntity<byte[]> exportToJson(@RequestBody ExportRequest request) {
        log.info("POST /api/export/json: {}", request);

        byte[] jsonData = exportService.exportToJson(request);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setContentDispositionFormData("attachment", "filings-export.json");
        headers.setContentLength(jsonData.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(jsonData);
    }
}
