package org.jds.edgar4j.model;

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
 * Form 13F - Institutional Holdings Report.
 * Filed quarterly by institutional investment managers with over $100M in qualifying assets.
 * Contains a list of all equity holdings as of the reporting period end date.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "form13f")
@CompoundIndexes({
    @CompoundIndex(name = "cik_period_idx", def = "{'cik': 1, 'reportPeriod': -1}"),
    @CompoundIndex(name = "filer_period_idx", def = "{'filerName': 1, 'reportPeriod': -1}")
})
public class Form13F {

    @Id
    private String id;

    /**
     * Unique SEC accession number for the filing.
     */
    @Indexed(unique = true)
    private String accessionNumber;

    /**
     * CIK (Central Index Key) of the filing institution.
     */
    @Indexed
    private String cik;

    /**
     * Name of the institutional investment manager.
     */
    @Indexed
    private String filerName;

    /**
     * Business address of the filer.
     */
    private String businessAddress;

    /**
     * Form type (13F-HR, 13F-HR/A, 13F-NT, etc.).
     */
    private String formType;

    /**
     * Date the form was filed with the SEC.
     */
    @Indexed
    private LocalDate filedDate;

    /**
     * End date of the reporting period (quarter end).
     */
    @Indexed
    private LocalDate reportPeriod;

    /**
     * Amendment type: NEW, RESTATEMENT, or null for original filings.
     */
    private String amendmentType;

    /**
     * Amendment number (for amended filings).
     */
    private Integer amendmentNumber;

    /**
     * Confidential treatment requested for some holdings.
     */
    private Boolean confidentialTreatment;

    /**
     * Report type: 13F HOLDINGS REPORT, 13F NOTICE, 13F COMBINATION REPORT.
     */
    private String reportType;

    /**
     * Total number of holdings in the filing.
     */
    private Integer holdingsCount;

    /**
     * Total market value of all holdings in thousands of dollars.
     */
    private Long totalValue;

    /**
     * List of individual security holdings.
     */
    private List<Form13FHolding> holdings;

    /**
     * Other managers included in the report.
     */
    private List<OtherManager> otherManagers;

    /**
     * Signature information.
     */
    private String signatureName;
    private String signatureTitle;
    private String signaturePhone;
    private String signatureCity;
    private String signatureState;
    private LocalDate signatureDate;

    /**
     * Represents other managers listed in a 13F filing.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OtherManager {
        private Integer sequenceNumber;
        private String cik;
        private String name;
        private String form13FFileNumber;
    }
}
