package org.jds.edgar4j.model;

import java.time.Instant;
import java.time.LocalDate;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
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
@Document(collection = "dividend_alert_resolutions")
public class DividendAlertResolution {

    @Id
    private String id;

    @Indexed(unique = true)
    private String resolutionKey;

    @Indexed
    private String cik;

    @Indexed
    private String ticker;

    @Indexed
    private String alertId;

    private LocalDate periodEnd;
    private String accessionNumber;

    @Builder.Default
    private ResolutionStatus status = ResolutionStatus.RESOLVED;

    private String note;
    private String resolvedBy;
    private Instant resolvedAt;
    private Instant snoozedUntil;
    private Instant createdAt;
    private Instant updatedAt;

    public enum ResolutionStatus {
        RESOLVED,
        SNOOZED
    }
}
