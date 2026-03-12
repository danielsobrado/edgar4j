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
 * Form 6-K - Report of Foreign Private Issuer.
 * Captures core metadata plus a best-effort text extract and exhibit index.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "form6k")
@CompoundIndexes({
    @CompoundIndex(name = "cik_filedDate_idx", def = "{'cik': 1, 'filedDate': -1}"),
    @CompoundIndex(name = "symbol_filedDate_idx", def = "{'tradingSymbol': 1, 'filedDate': -1}")
})
public class Form6K {

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
     * Form type, e.g. "6-K" or "6-K/A".
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
     * EDGAR primary document filename (e.g. "d123456d6k.htm").
     */
    private String primaryDocument;

    /**
     * Best-effort text extract from the primary document (truncated).
     */
    private String reportText;

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
    public static class Exhibit {
        private String exhibitNumber; // e.g. "99.1"
        private String description;
        private String document;      // filename/href when available
    }
}

