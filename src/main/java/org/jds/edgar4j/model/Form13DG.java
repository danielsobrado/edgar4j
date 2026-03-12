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
 * Form 13D/13G - Beneficial Ownership Reports.
 *
 * Schedule 13D: Filed when acquiring >5% ownership with intent to influence the company.
 * Schedule 13G: Filed when acquiring >5% ownership with passive intent.
 *
 * As of December 18, 2024, all Schedule 13D and 13G filings must be submitted in XML format.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "form13dg")
@CompoundIndexes({
    @CompoundIndex(name = "issuer_date_idx", def = "{'issuerCik': 1, 'eventDate': -1}"),
    @CompoundIndex(name = "filer_date_idx", def = "{'filingPersonCik': 1, 'eventDate': -1}"),
    @CompoundIndex(name = "cusip_date_idx", def = "{'cusip': 1, 'eventDate': -1}")
})
public class Form13DG {

    @Id
    private String id;

    /**
     * Unique SEC accession number for the filing.
     */
    @Indexed(unique = true)
    private String accessionNumber;

    /**
     * Form type: SC 13D, SC 13D/A, SC 13G, SC 13G/A, SCHEDULE 13D, SCHEDULE 13G.
     */
    @Indexed
    private String formType;

    /**
     * Whether this is a 13D (activist) or 13G (passive) filing.
     * Values: "13D" or "13G"
     */
    private String scheduleType;

    /**
     * Date the form was filed with the SEC.
     */
    @Indexed
    private LocalDate filedDate;

    /**
     * Date of event requiring this filing (crossing 5% threshold, etc.).
     */
    @Indexed
    private LocalDate eventDate;

    /**
     * Amendment number (null for original, 1+ for amendments).
     */
    private Integer amendmentNumber;

    /**
     * Type of amendment: INITIAL, AMENDMENT.
     */
    private String amendmentType;

    // ========== COVER PAGE: SUBJECT COMPANY ==========

    /**
     * CUSIP number of the subject security.
     */
    @Indexed
    private String cusip;

    /**
     * Name of the issuer (subject company).
     */
    @Indexed
    private String issuerName;

    /**
     * CIK of the issuer company.
     */
    @Indexed
    private String issuerCik;

    /**
     * Title/class of securities (e.g., "Common Stock", "Class A Common Stock").
     */
    private String securityTitle;

    // ========== COVER PAGE: FILING PERSON ==========

    /**
     * Name of the beneficial owner filing this report.
     */
    @Indexed
    private String filingPersonName;

    /**
     * CIK of the filing person (if available).
     */
    @Indexed
    private String filingPersonCik;

    /**
     * Filing person's address.
     */
    private Address filingPersonAddress;

    /**
     * Citizenship or place of organization.
     */
    private String citizenshipOrOrganization;

    /**
     * Type of reporting person code(s): BD, BK, IC, IV, IA, EP, HC, SA, CP, CO, PN, IN, OO.
     * BD = Broker Dealer, BK = Bank, IC = Insurance Company, IV = Investment Company,
     * IA = Investment Adviser, EP = Employee Benefit Plan, HC = Holding Company,
     * SA = State, CP = Corporation, CO = Partnership, PN = Pension Fund,
     * IN = Individual, OO = Other
     */
    private List<String> reportingPersonTypes;

    // ========== OWNERSHIP INFORMATION ==========

    /**
     * Percent of class beneficially owned at time of filing.
     */
    private Double percentOfClass;

    /**
     * Total number of shares beneficially owned.
     */
    private Long sharesBeneficiallyOwned;

    /**
     * Sole voting power - number of shares.
     */
    private Long votingPowerSole;

    /**
     * Shared voting power - number of shares.
     */
    private Long votingPowerShared;

    /**
     * No voting power (dispositive only) - number of shares.
     */
    private Long votingPowerNone;

    /**
     * Sole dispositive power - number of shares.
     */
    private Long dispositivePowerSole;

    /**
     * Shared dispositive power - number of shares.
     */
    private Long dispositivePowerShared;

    /**
     * Aggregate amount beneficially owned by each reporting person.
     */
    private Long aggregateAmountBeneficiallyOwned;

    /**
     * Check if the aggregate amount in Row 11 excludes certain shares.
     */
    private Boolean excludesCertainShares;

    /**
     * Percent of class represented by amount in Row 11.
     */
    private Double percentOfClassRow11;

    /**
     * Type of reporting person for Row 12.
     */
    private String typeOfReportingPerson;

    // ========== 13D SPECIFIC: ITEM 4 - PURPOSE ==========

    /**
     * Purpose of transaction (Item 4 for 13D).
     * Most important field for 13D - describes activist intent.
     */
    private String purposeOfTransaction;

    // ========== 13D SPECIFIC: ITEM 3 - SOURCE OF FUNDS ==========

    /**
     * Source of funds code: PF (Personal Funds), WC (Working Capital),
     * BK (Bank Loan), AF (Affiliate), OO (Other).
     */
    private List<String> sourceOfFunds;

    /**
     * Total amount of funds used.
     */
    private Long amountOfFunds;

    // ========== 13G SPECIFIC: FILER CATEGORY ==========

    /**
     * For 13G: Type of filing person category.
     * QII = Qualified Institutional Investor (Rule 13d-1(b))
     * PASSIVE = Passive Investor (Rule 13d-1(c))
     * EXEMPT = Exempt Investor (Rule 13d-1(d))
     */
    private String filerCategory;

    // ========== ADDITIONAL REPORTING PERSONS ==========

    /**
     * List of additional reporting persons (for group filings).
     */
    private List<ReportingPerson> additionalReportingPersons;

    // ========== SIGNATURE ==========

    /**
     * Signature name.
     */
    private String signatureName;

    /**
     * Signature title.
     */
    private String signatureTitle;

    /**
     * Signature date.
     */
    private LocalDate signatureDate;

    // ========== NESTED CLASSES ==========

    /**
     * Address structure for filing persons.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Address {
        private String street1;
        private String street2;
        private String city;
        private String stateOrCountry;
        private String zipCode;
    }

    /**
     * Additional reporting person in group filings.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportingPerson {
        private String name;
        private String cik;
        private Address address;
        private String citizenshipOrOrganization;
        private List<String> reportingPersonTypes;
        private Long sharesBeneficiallyOwned;
        private Double percentOfClass;
        private Long votingPowerSole;
        private Long votingPowerShared;
        private Long dispositivePowerSole;
        private Long dispositivePowerShared;
    }

    // ========== HELPER METHODS ==========

    /**
     * Returns true if this is a 13D filing (activist).
     */
    public boolean is13D() {
        return "13D".equals(scheduleType) ||
               (formType != null && formType.toUpperCase().contains("13D"));
    }

    /**
     * Returns true if this is a 13G filing (passive).
     */
    public boolean is13G() {
        return "13G".equals(scheduleType) ||
               (formType != null && formType.toUpperCase().contains("13G"));
    }

    /**
     * Returns true if this is an amendment.
     */
    public boolean isAmendment() {
        return amendmentNumber != null && amendmentNumber > 0;
    }

    /**
     * Returns true if ownership exceeds 10%.
     */
    public boolean isTenPercentOwner() {
        return percentOfClass != null && percentOfClass >= 10.0;
    }

    /**
     * Returns the total voting power (sole + shared).
     */
    public Long getTotalVotingPower() {
        long sole = votingPowerSole != null ? votingPowerSole : 0;
        long shared = votingPowerShared != null ? votingPowerShared : 0;
        return sole + shared;
    }

    /**
     * Returns the total dispositive power (sole + shared).
     */
    public Long getTotalDispositivePower() {
        long sole = dispositivePowerSole != null ? dispositivePowerSole : 0;
        long shared = dispositivePowerShared != null ? dispositivePowerShared : 0;
        return sole + shared;
    }
}
