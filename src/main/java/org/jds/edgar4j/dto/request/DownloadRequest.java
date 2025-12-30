package org.jds.edgar4j.dto.request;

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
        BULK_SUBMISSIONS,
        BULK_COMPANY_FACTS
    }

    @NotNull(message = "Download type is required")
    private DownloadType type;

    @Pattern(regexp = "^[0-9]{1,10}$", message = "CIK must be 1-10 digits")
    private String cik;

    private String userAgent;
}
