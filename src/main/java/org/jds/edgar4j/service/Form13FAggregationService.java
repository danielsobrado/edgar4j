package org.jds.edgar4j.service;

import java.time.LocalDate;
import java.util.List;

import org.jds.edgar4j.model.Form13F;
import org.jds.edgar4j.model.report.InstitutionalHoldingReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service for aggregating and analyzing Form 13F institutional holdings
 * Tracks "smart money" movements and position changes
 *
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-11-07
 */
public interface Form13FAggregationService {

    /**
     * Get latest filings for a specific institution
     *
     * @param filerCik institution's CIK
     * @param quarters number of quarters to look back
     * @param pageable pagination parameters
     * @return page of Form 13F filings
     */
    Page<Form13F> getInstitutionFilings(String filerCik, int quarters, Pageable pageable);

    /**
     * Get all institutions that hold a specific security (by CUSIP)
     *
     * @param cusip security CUSIP
     * @param quarterEnd quarter end date
     * @return list of institutions holding this security
     */
    List<InstitutionalHoldingReport> getInstitutionsHoldingSecurity(String cusip, LocalDate quarterEnd);

    /**
     * Get top holdings for an institution in a specific quarter
     *
     * @param filerCik institution's CIK
     * @param quarterEnd quarter end date
     * @param limit number of top holdings to return
     * @return list of top holdings
     */
    List<Form13F.Holding> getTopHoldings(String filerCik, LocalDate quarterEnd, int limit);

    /**
     * Get new positions for an institution (securities added in latest quarter)
     *
     * @param filerCik institution's CIK
     * @param quarterEnd quarter end date
     * @return list of new positions
     */
    List<Form13F.Holding> getNewPositions(String filerCik, LocalDate quarterEnd);

    /**
     * Get closed positions for an institution (securities removed from portfolio)
     *
     * @param filerCik institution's CIK
     * @param quarterEnd quarter end date
     * @return list of closed positions from previous quarter
     */
    List<Form13F.Holding> getClosedPositions(String filerCik, LocalDate quarterEnd);

    /**
     * Get position changes for an institution (increased or decreased positions)
     *
     * @param filerCik institution's CIK
     * @param quarterEnd quarter end date
     * @param minChangePercent minimum change percentage to include
     * @return list of positions with significant changes
     */
    List<PositionChange> getPositionChanges(String filerCik, LocalDate quarterEnd, int minChangePercent);

    /**
     * Get most popular securities (held by most institutions)
     *
     * @param quarterEnd quarter end date
     * @param limit number of securities to return
     * @return list of popular securities with holder counts
     */
    List<PopularSecurity> getMostPopularSecurities(LocalDate quarterEnd, int limit);

    /**
     * Get institutions with largest AUM (assets under management)
     *
     * @param quarterEnd quarter end date
     * @param limit number of institutions to return
     * @return list of largest institutions
     */
    List<InstitutionRanking> getLargestInstitutions(LocalDate quarterEnd, int limit);

    /**
     * Compare two quarters for an institution
     *
     * @param filerCik institution's CIK
     * @param currentQuarter current quarter end date
     * @param previousQuarter previous quarter end date
     * @return quarterly comparison report
     */
    QuarterlyComparison compareQuarters(String filerCik, LocalDate currentQuarter, LocalDate previousQuarter);

    /**
     * Position change information
     */
    class PositionChange {
        private String cusip;
        private String nameOfIssuer;
        private Long previousShares;
        private Long currentShares;
        private Long sharesChange;
        private Double percentChange;
        private String changeType; // "NEW", "INCREASED", "DECREASED", "CLOSED"

        // Getters and setters
        public String getCusip() { return cusip; }
        public void setCusip(String cusip) { this.cusip = cusip; }

        public String getNameOfIssuer() { return nameOfIssuer; }
        public void setNameOfIssuer(String nameOfIssuer) { this.nameOfIssuer = nameOfIssuer; }

        public Long getPreviousShares() { return previousShares; }
        public void setPreviousShares(Long previousShares) { this.previousShares = previousShares; }

        public Long getCurrentShares() { return currentShares; }
        public void setCurrentShares(Long currentShares) { this.currentShares = currentShares; }

        public Long getSharesChange() { return sharesChange; }
        public void setSharesChange(Long sharesChange) { this.sharesChange = sharesChange; }

        public Double getPercentChange() { return percentChange; }
        public void setPercentChange(Double percentChange) { this.percentChange = percentChange; }

        public String getChangeType() { return changeType; }
        public void setChangeType(String changeType) { this.changeType = changeType; }
    }

    /**
     * Popular security information
     */
    class PopularSecurity {
        private String cusip;
        private String nameOfIssuer;
        private int institutionCount;
        private Long totalShares;

        public PopularSecurity(String cusip, String nameOfIssuer, int institutionCount, Long totalShares) {
            this.cusip = cusip;
            this.nameOfIssuer = nameOfIssuer;
            this.institutionCount = institutionCount;
            this.totalShares = totalShares;
        }

        // Getters
        public String getCusip() { return cusip; }
        public String getNameOfIssuer() { return nameOfIssuer; }
        public int getInstitutionCount() { return institutionCount; }
        public Long getTotalShares() { return totalShares; }
    }

    /**
     * Institution ranking information
     */
    class InstitutionRanking {
        private String filerCik;
        private String filerName;
        private Long totalValue;
        private int holdingsCount;

        public InstitutionRanking(String filerCik, String filerName, Long totalValue, int holdingsCount) {
            this.filerCik = filerCik;
            this.filerName = filerName;
            this.totalValue = totalValue;
            this.holdingsCount = holdingsCount;
        }

        // Getters
        public String getFilerCik() { return filerCik; }
        public String getFilerName() { return filerName; }
        public Long getTotalValue() { return totalValue; }
        public int getHoldingsCount() { return holdingsCount; }
    }

    /**
     * Quarterly comparison report
     */
    class QuarterlyComparison {
        private String filerCik;
        private String filerName;
        private LocalDate currentQuarter;
        private LocalDate previousQuarter;
        private Long currentValue;
        private Long previousValue;
        private Long valueChange;
        private Double percentChange;
        private int newPositions;
        private int closedPositions;
        private int increasedPositions;
        private int decreasedPositions;
        private int unchangedPositions;

        // Getters and setters
        public String getFilerCik() { return filerCik; }
        public void setFilerCik(String filerCik) { this.filerCik = filerCik; }

        public String getFilerName() { return filerName; }
        public void setFilerName(String filerName) { this.filerName = filerName; }

        public LocalDate getCurrentQuarter() { return currentQuarter; }
        public void setCurrentQuarter(LocalDate currentQuarter) { this.currentQuarter = currentQuarter; }

        public LocalDate getPreviousQuarter() { return previousQuarter; }
        public void setPreviousQuarter(LocalDate previousQuarter) { this.previousQuarter = previousQuarter; }

        public Long getCurrentValue() { return currentValue; }
        public void setCurrentValue(Long currentValue) { this.currentValue = currentValue; }

        public Long getPreviousValue() { return previousValue; }
        public void setPreviousValue(Long previousValue) { this.previousValue = previousValue; }

        public Long getValueChange() { return valueChange; }
        public void setValueChange(Long valueChange) { this.valueChange = valueChange; }

        public Double getPercentChange() { return percentChange; }
        public void setPercentChange(Double percentChange) { this.percentChange = percentChange; }

        public int getNewPositions() { return newPositions; }
        public void setNewPositions(int newPositions) { this.newPositions = newPositions; }

        public int getClosedPositions() { return closedPositions; }
        public void setClosedPositions(int closedPositions) { this.closedPositions = closedPositions; }

        public int getIncreasedPositions() { return increasedPositions; }
        public void setIncreasedPositions(int increasedPositions) { this.increasedPositions = increasedPositions; }

        public int getDecreasedPositions() { return decreasedPositions; }
        public void setDecreasedPositions(int decreasedPositions) { this.decreasedPositions = decreasedPositions; }

        public int getUnchangedPositions() { return unchangedPositions; }
        public void setUnchangedPositions(int unchangedPositions) { this.unchangedPositions = unchangedPositions; }
    }
}
