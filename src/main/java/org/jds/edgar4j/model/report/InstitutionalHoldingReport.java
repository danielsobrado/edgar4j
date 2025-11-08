package org.jds.edgar4j.model.report;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Report showing which institutions hold a specific security
 * Generated from Form 13F aggregations
 *
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-11-07
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InstitutionalHoldingReport {

    /**
     * Institution CIK
     */
    private String filerCik;

    /**
     * Institution name
     */
    private String filerName;

    /**
     * Quarter end date
     */
    private LocalDate quarterEnd;

    /**
     * Security CUSIP
     */
    private String cusip;

    /**
     * Security name
     */
    private String securityName;

    /**
     * Number of shares held
     */
    private Long shares;

    /**
     * Value of holding (in dollars)
     */
    private BigDecimal value;

    /**
     * Percentage of institution's portfolio
     */
    private Double portfolioPercent;

    /**
     * Investment discretion ("SOLE", "SHARED", "NONE")
     */
    private String investmentDiscretion;

    /**
     * Voting authority - sole
     */
    private Long votingAuthoritySole;

    /**
     * Voting authority - shared
     */
    private Long votingAuthorityShared;

    /**
     * Voting authority - none
     */
    private Long votingAuthorityNone;
}
