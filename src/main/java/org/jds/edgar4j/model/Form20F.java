package org.jds.edgar4j.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

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
 * Form 20-F - Annual Report for Foreign Private Issuers.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "form20f")
@CompoundIndexes({
    @CompoundIndex(name = "cik_fiscalYear_idx", def = "{'cik': 1, 'fiscalYear': -1}"),
    @CompoundIndex(name = "symbol_fiscalYear_idx", def = "{'tradingSymbol': 1, 'fiscalYear': -1}")
})
public class Form20F {

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
     * Issuer name (from DEI if available).
     */
    private String companyName;

    /**
     * Trading symbol (best-effort extracted from XBRL).
     */
    @Indexed
    private String tradingSymbol;

    /**
     * Security exchange (best-effort).
     */
    private String securityExchange;

    /**
     * Form type (20-F / 20-F/A).
     */
    @Indexed
    private String formType;

    /**
     * Filing date (best-effort; often sourced from submissions metadata).
     */
    @Indexed
    private LocalDate filedDate;

    /**
     * Report date (usually document period end date).
     */
    private LocalDate reportDate;

    /**
     * Document period end date from XBRL DEI.
     */
    private LocalDate documentPeriodEndDate;

    private Integer fiscalYear;
    private String fiscalPeriod;
    private String fiscalYearEndDate;
    private Long sharesOutstanding;
    private Boolean isAmendment;

    /**
     * EDGAR primary document filename (e.g. "d123456d20f.htm").
     */
    private String primaryDocument;

    /**
     * Selected key financials from XBRL.
     */
    private Map<String, BigDecimal> keyFinancials;

    /**
     * All extracted DEI facts (for reference).
     */
    private Map<String, String> deiData;

    private Instant createdAt;
    private Instant updatedAt;
}

