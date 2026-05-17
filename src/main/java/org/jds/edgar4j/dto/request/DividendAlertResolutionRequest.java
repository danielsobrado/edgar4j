package org.jds.edgar4j.dto.request;

import java.time.Instant;
import java.time.LocalDate;

import org.jds.edgar4j.model.DividendAlertResolution;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DividendAlertResolutionRequest {

    private LocalDate periodEnd;
    private String accessionNumber;
    private String note;
    private String resolvedBy;
    private DividendAlertResolution.ResolutionStatus status;
    private Instant snoozedUntil;
}
