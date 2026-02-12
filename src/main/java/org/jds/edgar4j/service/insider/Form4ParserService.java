package org.jds.edgar4j.service.insider;

import org.jds.edgar4j.model.insider.InsiderTransaction;

import java.util.List;

/**
 * Service interface for Form 4 XML parsing
 * 
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-01-01
 */
public interface Form4ParserService {

    /**
     * Parse Form 4 XML content
     */
    List<InsiderTransaction> parseForm4Xml(String xmlContent, String accessionNumber);

    /**
     * Extract issuer information from Form 4
     */
    IssuerInfo extractIssuerInfo(String xmlContent);

    /**
     * Extract reporting owner information from Form 4
     */
    ReportingOwnerInfo extractReportingOwnerInfo(String xmlContent);

    /**
     * Extract non-derivative transactions from Form 4
     */
    List<NonDerivativeTransaction> extractNonDerivativeTransactions(String xmlContent);

    /**
     * Extract derivative transactions from Form 4
     */
    List<DerivativeTransaction> extractDerivativeTransactions(String xmlContent);

    /**
     * Validate Form 4 XML structure
     */
    boolean validateForm4Xml(String xmlContent);

    /**
     * Inner class for issuer information
     */
    class IssuerInfo {
        private String cik;
        private String name;
        private String tradingSymbol;

        public IssuerInfo() {}

        public IssuerInfo(String cik, String name, String tradingSymbol) {
            this.cik = cik;
            this.name = name;
            this.tradingSymbol = tradingSymbol;
        }

        // Getters and setters
        public String getCik() { return cik; }
        public void setCik(String cik) { this.cik = cik; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getTradingSymbol() { return tradingSymbol; }
        public void setTradingSymbol(String tradingSymbol) { this.tradingSymbol = tradingSymbol; }
    }

    /**
     * Inner class for reporting owner information
     */
    class ReportingOwnerInfo {
        private String cik;
        private String name;
        private String address;
        private boolean isDirector;
        private boolean isOfficer;
        private boolean isTenPercentOwner;
        private boolean isOther;
        private String officerTitle;
        private String otherText;

        public ReportingOwnerInfo() {}

        // Getters and setters
        public String getCik() { return cik; }
        public void setCik(String cik) { this.cik = cik; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }

        public boolean isDirector() { return isDirector; }
        public void setDirector(boolean director) { isDirector = director; }

        public boolean isOfficer() { return isOfficer; }
        public void setOfficer(boolean officer) { isOfficer = officer; }

        public boolean isTenPercentOwner() { return isTenPercentOwner; }
        public void setTenPercentOwner(boolean tenPercentOwner) { isTenPercentOwner = tenPercentOwner; }

        public boolean isOther() { return isOther; }
        public void setOther(boolean other) { isOther = other; }

        public String getOfficerTitle() { return officerTitle; }
        public void setOfficerTitle(String officerTitle) { this.officerTitle = officerTitle; }

        public String getOtherText() { return otherText; }
        public void setOtherText(String otherText) { this.otherText = otherText; }
    }

    /**
     * Inner class for non-derivative transactions
     */
    class NonDerivativeTransaction {
        private String securityTitle;
        private String transactionDate;
        private String transactionCode;
        private String sharesTransacted;
        private String pricePerShare;
        private String acquiredDisposed;
        private String sharesOwnedAfter;
        private String ownershipNature;

        public NonDerivativeTransaction() {}

        // Getters and setters
        public String getSecurityTitle() { return securityTitle; }
        public void setSecurityTitle(String securityTitle) { this.securityTitle = securityTitle; }

        public String getTransactionDate() { return transactionDate; }
        public void setTransactionDate(String transactionDate) { this.transactionDate = transactionDate; }

        public String getTransactionCode() { return transactionCode; }
        public void setTransactionCode(String transactionCode) { this.transactionCode = transactionCode; }

        public String getSharesTransacted() { return sharesTransacted; }
        public void setSharesTransacted(String sharesTransacted) { this.sharesTransacted = sharesTransacted; }

        public String getPricePerShare() { return pricePerShare; }
        public void setPricePerShare(String pricePerShare) { this.pricePerShare = pricePerShare; }

        public String getAcquiredDisposed() { return acquiredDisposed; }
        public void setAcquiredDisposed(String acquiredDisposed) { this.acquiredDisposed = acquiredDisposed; }

        public String getSharesOwnedAfter() { return sharesOwnedAfter; }
        public void setSharesOwnedAfter(String sharesOwnedAfter) { this.sharesOwnedAfter = sharesOwnedAfter; }

        public String getOwnershipNature() { return ownershipNature; }
        public void setOwnershipNature(String ownershipNature) { this.ownershipNature = ownershipNature; }
    }

    /**
     * Inner class for derivative transactions
     */
    class DerivativeTransaction {
        private String securityTitle;
        private String transactionDate;
        private String transactionCode;
        private String sharesTransacted;
        private String pricePerShare;
        private String acquiredDisposed;
        private String exerciseDate;
        private String expirationDate;
        private String underlyingSecurityTitle;
        private String underlyingShares;
        private String sharesOwnedAfter;
        private String ownershipNature;

        public DerivativeTransaction() {}

        // Getters and setters
        public String getSecurityTitle() { return securityTitle; }
        public void setSecurityTitle(String securityTitle) { this.securityTitle = securityTitle; }

        public String getTransactionDate() { return transactionDate; }
        public void setTransactionDate(String transactionDate) { this.transactionDate = transactionDate; }

        public String getTransactionCode() { return transactionCode; }
        public void setTransactionCode(String transactionCode) { this.transactionCode = transactionCode; }

        public String getSharesTransacted() { return sharesTransacted; }
        public void setSharesTransacted(String sharesTransacted) { this.sharesTransacted = sharesTransacted; }

        public String getPricePerShare() { return pricePerShare; }
        public void setPricePerShare(String pricePerShare) { this.pricePerShare = pricePerShare; }

        public String getAcquiredDisposed() { return acquiredDisposed; }
        public void setAcquiredDisposed(String acquiredDisposed) { this.acquiredDisposed = acquiredDisposed; }

        public String getExerciseDate() { return exerciseDate; }
        public void setExerciseDate(String exerciseDate) { this.exerciseDate = exerciseDate; }

        public String getExpirationDate() { return expirationDate; }
        public void setExpirationDate(String expirationDate) { this.expirationDate = expirationDate; }

        public String getUnderlyingSecurityTitle() { return underlyingSecurityTitle; }
        public void setUnderlyingSecurityTitle(String underlyingSecurityTitle) { this.underlyingSecurityTitle = underlyingSecurityTitle; }

        public String getUnderlyingShares() { return underlyingShares; }
        public void setUnderlyingShares(String underlyingShares) { this.underlyingShares = underlyingShares; }

        public String getSharesOwnedAfter() { return sharesOwnedAfter; }
        public void setSharesOwnedAfter(String sharesOwnedAfter) { this.sharesOwnedAfter = sharesOwnedAfter; }

        public String getOwnershipNature() { return ownershipNature; }
        public void setOwnershipNature(String ownershipNature) { this.ownershipNature = ownershipNature; }
    }
}
