package org.jds.edgar4j.dto.request;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DownloadRequest {

    public enum DownloadType {
        TICKERS_ALL,
        TICKERS_NYSE,
        TICKERS_NASDAQ,
        TICKERS_MF,
        SUBMISSIONS,
        REMOTE_FILINGS_SYNC,
        BULK_SUBMISSIONS,
        BULK_COMPANY_FACTS
    }

    @NotNull(message = "Download type is required")
    private DownloadType type;

    @Pattern(regexp = "^[0-9]{1,10}$", message = "CIK must be 1-10 digits")
    private String cik;

    private String formType;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateFrom;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateTo;

    private String userAgent;
}
