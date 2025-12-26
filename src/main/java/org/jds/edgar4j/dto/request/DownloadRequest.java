package org.jds.edgar4j.dto.request;

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

    private DownloadType type;
    private String cik;
    private String userAgent;
}
