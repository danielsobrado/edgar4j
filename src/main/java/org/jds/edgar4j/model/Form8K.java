package org.jds.edgar4j.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Form 8-K - Current Report.
 * Captures the most commonly-used structured data (items and exhibits) from the primary filing document.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "form8k")
@CompoundIndexes({
    @CompoundIndex(name = "cik_filedDate_idx", def = "{'cik': 1, 'filedDate': -1}"),
    @CompoundIndex(name = "symbol_filedDate_idx", def = "{'tradingSymbol': 1, 'filedDate': -1}")
})
public class Form8K {

    @Id
    private String id;

    /**
     * Unique SEC accession number for the filing.
     */
    @Indexed(unique = true)
    private String accessionNumber;

    /**
     * Issuer CIK.
     */
    @Indexed
    private String cik;

    /**
     * Issuer name (best-effort; often sourced from submissions metadata).
     */
    private String companyName;

    /**
     * Trading symbol (best-effort extracted from cover page).
     */
    @Indexed
    private String tradingSymbol;

    /**
     * Form type, e.g. "8-K" or "8-K/A".
     */
    @Indexed
    private String formType;

    /**
     * Filing date (best-effort; often sourced from submissions metadata).
     */
    @Indexed
    private LocalDate filedDate;

    /**
     * Report date, if present (best-effort; often sourced from submissions metadata).
     */
    private LocalDate reportDate;

    /**
     * EDGAR primary document filename (e.g. "d123456d8k.htm").
     */
    private String primaryDocument;

    /**
     * Items string from SEC submissions (e.g. "2.02, 9.01") when available.
     */
    private String items;

    /**
     * Parsed item sections from the primary document.
     */
    private List<ItemSection> itemSections;

    /**
     * Parsed exhibits list (when present in the primary document).
     */
    private List<Exhibit> exhibits;

    private Instant createdAt;
    private Instant updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemSection {
        private String itemNumber;   // e.g. "2.02"
        private String title;        // e.g. "Results of Operations and Financial Condition"
        private String content;      // best-effort extracted text
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Exhibit {
        private String exhibitNumber; // e.g. "99.1"
        private String description;
        private String document;      // filename/href when available
    }
}

