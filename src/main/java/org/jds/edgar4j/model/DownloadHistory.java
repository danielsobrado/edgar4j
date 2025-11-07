package org.jds.edgar4j.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tracks insider form download history for pipeline management
 * Supports Forms 3, 4, and 5
 *
 * @author J. Daniel Sobrado
 * @version 2.0
 * @since 2025-11-05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Document(indexName = "download_history")
public class DownloadHistory {

    @Id
    private String id;

    /**
     * Accession number (unique filing identifier)
     */
    @Field(type = FieldType.Keyword)
    private String accessionNumber;

    /**
     * Form type (3, 4, or 5)
     */
    @Field(type = FieldType.Keyword)
    private String formType;

    /**
     * Company CIK
     */
    @Field(type = FieldType.Keyword)
    private String cik;

    /**
     * Filing date
     */
    @Field(type = FieldType.Date)
    private LocalDate filingDate;

    /**
     * Download timestamp
     */
    @Field(type = FieldType.Date)
    private LocalDateTime downloadedAt;

    /**
     * Processing status
     */
    @Field(type = FieldType.Keyword)
    private ProcessingStatus status;

    /**
     * Source URL
     */
    @Field(type = FieldType.Text)
    private String sourceUrl;

    /**
     * Error message if processing failed
     */
    @Field(type = FieldType.Text)
    private String errorMessage;

    /**
     * Number of retry attempts
     */
    @Field(type = FieldType.Integer)
    private int retryCount;

    /**
     * Last processing attempt timestamp
     */
    @Field(type = FieldType.Date)
    private LocalDateTime lastAttemptAt;

    /**
     * Successfully processed timestamp
     */
    @Field(type = FieldType.Date)
    private LocalDateTime processedAt;

    /**
     * Processing duration in milliseconds
     */
    @Field(type = FieldType.Long)
    private Long processingDurationMs;

    public enum ProcessingStatus {
        PENDING,      // Queued for download
        DOWNLOADING,  // Currently downloading
        DOWNLOADED,   // Downloaded but not parsed
        PARSING,      // Currently parsing
        COMPLETED,    // Successfully processed
        FAILED,       // Processing failed
        SKIPPED       // Skipped (duplicate or invalid)
    }
}
