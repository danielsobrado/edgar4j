package org.jds.edgar4j.dto.response;

import java.time.Instant;
import java.util.List;

import org.jds.edgar4j.service.DataMigrationService;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MigrationStatusResponse {

    private String jobId;
    private String action;
    private String status;
    private String message;
    private String path;
    private List<String> collections;
    private Instant startedAt;
    private Instant completedAt;
    private DataMigrationService.MigrationReport report;
}
