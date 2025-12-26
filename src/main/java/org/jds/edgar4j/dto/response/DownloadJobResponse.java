package org.jds.edgar4j.dto.response;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DownloadJobResponse {

    public enum JobStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    private String id;
    private String type;
    private String description;
    private JobStatus status;
    private int progress;
    private String error;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private long filesDownloaded;
    private long totalFiles;
    private String estimatedSize;
}
