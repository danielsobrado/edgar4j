package org.jds.edgar4j.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single holding in a Form 13F filing.
 * Each holding represents a security position held by an institutional investment manager.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Form13FHolding {

    /**
     * Name of the issuer (company name).
     */
    private String nameOfIssuer;

    /**
     * Title/class of the security (e.g., "COM", "CL A", "CL B").
     */
    private String titleOfClass;

    /**
     * CUSIP number - 9 character security identifier.
     */
    private String cusip;

    /**
     * FIGI - 12 character Financial Instrument Global Identifier (optional).
     */
    private String figi;

    /**
     * Market value of the holding in thousands of dollars.
     */
    private Long value;

    /**
     * Number of shares or principal amount.
     */
    private Long sharesOrPrincipalAmount;

    /**
     * Type: "SH" for shares or "PRN" for principal amount.
     */
    private String sharesOrPrincipalAmountType;

    /**
     * PUT or CALL indicator for options (optional).
     */
    private String putCall;

    /**
     * Investment discretion: SOLE, DFND (defined), or OTR (other).
     */
    private String investmentDiscretion;

    /**
     * Other manager sequence number (optional).
     */
    private String otherManager;

    /**
     * Voting authority - sole voting power.
     */
    private Long votingAuthoritySole;

    /**
     * Voting authority - shared voting power.
     */
    private Long votingAuthorityShared;

    /**
     * Voting authority - no voting power.
     */
    private Long votingAuthorityNone;
}
