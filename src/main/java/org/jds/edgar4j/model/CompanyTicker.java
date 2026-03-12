package org.jds.edgar4j.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * Maps one document from the {@code company_tickers} MongoDB collection.
 *
 * <p>The collection is sourced from the SEC's
 * {@code https://www.sec.gov/files/company_tickers.json} feed.
 * Raw JSON fields are:
 * <pre>
 *   { "cik_str": 320193, "ticker": "AAPL", "title": "Apple Inc." }
 * </pre>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Document(collection = "company_tickers")
public class CompanyTicker {

    @Id
    private String id;

    /** Numeric CIK as stored in the SEC feed (e.g. 320193). */
    @Field("cik_str")
    @Indexed
    private Long cikStr;

    /** Stock ticker symbol (e.g. "AAPL"). */
    @Indexed(unique = true)
    private String ticker;

    /** Company name (e.g. "Apple Inc."). */
    private String title;

    /**
     * Returns the CIK zero-padded to 10 digits, matching the format used
     * throughout the rest of the application (e.g. "0000320193").
     */
    public String getCikPadded() {
        if (cikStr == null) return null;
        return String.format("%010d", cikStr);
    }
}
