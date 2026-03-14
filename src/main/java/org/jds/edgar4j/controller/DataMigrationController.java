package org.jds.edgar4j.controller;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.jds.edgar4j.dto.request.MigrationRequest;
import org.jds.edgar4j.dto.response.MigrationStatusResponse;
import org.jds.edgar4j.service.DataMigrationService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@Profile("resource-high & !resource-low")
@RequestMapping("/api/admin/migration")
@RequiredArgsConstructor
public class DataMigrationController {

    private final DataMigrationService dataMigrationService;
    private final Map<String, MigrationStatusResponse> jobs = new ConcurrentHashMap<>();

    @PostMapping("/export")
    public ResponseEntity<MigrationStatusResponse> exportData(@RequestBody MigrationRequest request) {
        return ResponseEntity.accepted().body(startJob("export", request));
    }

    @PostMapping("/import")
    public ResponseEntity<MigrationStatusResponse> importData(@RequestBody MigrationRequest request) {
        return ResponseEntity.accepted().body(startJob("import", request));
    }

    @GetMapping("/status/{jobId}")
    public ResponseEntity<MigrationStatusResponse> getStatus(@PathVariable String jobId) {
        MigrationStatusResponse status = jobs.get(jobId);
        if (status == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(status);
    }

    private MigrationStatusResponse startJob(String action, MigrationRequest request) {
        String path = validatePath(request);
        String jobId = UUID.randomUUID().toString();
        List<String> collections = request.getCollections() == null ? List.of() : List.copyOf(request.getCollections());

        MigrationStatusResponse initialStatus = MigrationStatusResponse.builder()
                .jobId(jobId)
                .action(action)
                .status("RUNNING")
                .message(action + " started")
                .path(path)
                .collections(collections)
                .startedAt(Instant.now())
                .build();
        jobs.put(jobId, initialStatus);

        CompletableFuture.runAsync(() -> executeJob(jobId, action, path, collections));
        return initialStatus;
    }

    private void executeJob(String jobId, String action, String path, List<String> requestedCollections) {
        Instant startedAt = jobs.get(jobId).getStartedAt();
        try {
            DataMigrationService.MigrationReport report = resolveReport(action, Path.of(path), requestedCollections);
            jobs.put(jobId, MigrationStatusResponse.builder()
                    .jobId(jobId)
                    .action(action)
                    .status(report.success() ? "COMPLETED" : "FAILED")
                    .message(report.messages().isEmpty() ? action + " completed" : String.join("; ", report.messages()))
                    .path(path)
                    .collections(requestedCollections)
                    .startedAt(startedAt)
                    .completedAt(Instant.now())
                    .report(report)
                    .build());
        } catch (Exception e) {
            log.error("Migration job {} failed", jobId, e);
            jobs.put(jobId, MigrationStatusResponse.builder()
                    .jobId(jobId)
                    .action(action)
                    .status("FAILED")
                    .message(e.getMessage())
                    .path(path)
                    .collections(requestedCollections)
                    .startedAt(startedAt)
                    .completedAt(Instant.now())
                    .build());
        }
    }

    private DataMigrationService.MigrationReport resolveReport(String action, Path path, List<String> requestedCollections) {
        List<String> collectionSelection = requestedCollections == null ? List.of() : requestedCollections.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
        if ("export".equalsIgnoreCase(action)) {
            if (collectionSelection.isEmpty()) {
                return dataMigrationService.exportAll(path);
            }
            if (collectionSelection.size() == 1) {
                return dataMigrationService.exportCollection(collectionSelection.get(0), path);
            }
            return dataMigrationService.exportCollections(path, collectionSelection);
        }

        if (collectionSelection.isEmpty()) {
            return dataMigrationService.importAll(path);
        }
        if (collectionSelection.size() == 1) {
            return dataMigrationService.importCollection(collectionSelection.get(0), path);
        }
        return dataMigrationService.importCollections(path, collectionSelection);
    }

    private String validatePath(MigrationRequest request) {
        if (request == null || !StringUtils.hasText(request.getPath())) {
            throw new IllegalArgumentException("Migration request path is required");
        }
        return request.getPath().trim();
    }
}
