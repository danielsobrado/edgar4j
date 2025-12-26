package org.jds.edgar4j.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = false)
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Document(collection = "download_jobs")
public class DownloadJob {

    public enum JobType {
        TICKERS_ALL,
        TICKERS_NYSE,
        TICKERS_NASDAQ,
        TICKERS_MF,
        SUBMISSIONS,
        BULK_SUBMISSIONS,
        BULK_COMPANY_FACTS
    }

    public enum JobStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    @Id
    private String id;

    private JobType type;
    private String description;
    private JobStatus status;
    private int progress;
    private String error;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private long filesDownloaded;
    private long totalFiles;
    private String cik;
    private String userAgent;
}
