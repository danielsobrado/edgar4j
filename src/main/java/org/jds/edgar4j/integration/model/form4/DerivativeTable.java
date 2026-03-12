package org.jds.edgar4j.integration.model.form4;

import java.util.List;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

import lombok.Data;

/**
 * Table II - Derivative Securities from Form 4 XML.
 * Includes puts, calls, warrants, options, convertible securities.
 */
@Data
@XmlAccessorType(XmlAccessType.FIELD)
public class DerivativeTable {

    @XmlElement(name = "derivativeTransaction")
    private List<DerivativeTransaction> transactions;

    @XmlElement(name = "derivativeHolding")
    private List<DerivativeHolding> holdings;

    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class DerivativeTransaction {

        @XmlElement(name = "securityTitle")
        private ValueWithFootnote securityTitle;

        @XmlElement(name = "conversionOrExercisePrice")
        private ValueWithFootnote conversionOrExercisePrice;

        @XmlElement(name = "transactionDate")
        private ValueWithFootnote transactionDate;

        @XmlElement(name = "deemedExecutionDate")
        private ValueWithFootnote deemedExecutionDate;

        @XmlElement(name = "transactionCoding")
        private TransactionCoding transactionCoding;

        @XmlElement(name = "transactionTimeliness")
        private ValueWithFootnote transactionTimeliness;

        @XmlElement(name = "transactionAmounts")
        private DerivativeTransactionAmounts transactionAmounts;

        @XmlElement(name = "exerciseDate")
        private ValueWithFootnote exerciseDate;

        @XmlElement(name = "expirationDate")
        private ValueWithFootnote expirationDate;

        @XmlElement(name = "underlyingSecurity")
        private UnderlyingSecurity underlyingSecurity;

        @XmlElement(name = "postTransactionAmounts")
        private DerivativePostTransactionAmounts postTransactionAmounts;

        @XmlElement(name = "ownershipNature")
        private NonDerivativeTable.OwnershipNature ownershipNature;
    }

    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class DerivativeHolding {

        @XmlElement(name = "securityTitle")
        private ValueWithFootnote securityTitle;

        @XmlElement(name = "conversionOrExercisePrice")
        private ValueWithFootnote conversionOrExercisePrice;

        @XmlElement(name = "exerciseDate")
        private ValueWithFootnote exerciseDate;

        @XmlElement(name = "expirationDate")
        private ValueWithFootnote expirationDate;

        @XmlElement(name = "underlyingSecurity")
        private UnderlyingSecurity underlyingSecurity;

        @XmlElement(name = "postTransactionAmounts")
        private DerivativePostTransactionAmounts postTransactionAmounts;

        @XmlElement(name = "ownershipNature")
        private NonDerivativeTable.OwnershipNature ownershipNature;
    }

    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "derivativeTransactionCoding")
    public static class TransactionCoding {

        @XmlElement(name = "transactionFormType")
        private String transactionFormType;

        @XmlElement(name = "transactionCode")
        private String transactionCode;

        @XmlElement(name = "equitySwapInvolved")
        private String equitySwapInvolved;

        public boolean isEquitySwapInvolved() {
            return "1".equals(equitySwapInvolved) || "true".equalsIgnoreCase(equitySwapInvolved);
        }
    }

    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class DerivativeTransactionAmounts {

        @XmlElement(name = "transactionShares")
        private ValueWithFootnote transactionShares;

        @XmlElement(name = "transactionTotalValue")
        private ValueWithFootnote transactionTotalValue;

        @XmlElement(name = "transactionPricePerShare")
        private ValueWithFootnote transactionPricePerShare;

        @XmlElement(name = "transactionAcquiredDisposedCode")
        private ValueWithFootnote transactionAcquiredDisposedCode;
    }

    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class DerivativePostTransactionAmounts {

        @XmlElement(name = "sharesOwnedFollowingTransaction")
        private ValueWithFootnote sharesOwnedFollowingTransaction;

        @XmlElement(name = "valueOwnedFollowingTransaction")
        private ValueWithFootnote valueOwnedFollowingTransaction;
    }

    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class UnderlyingSecurity {

        @XmlElement(name = "underlyingSecurityTitle")
        private ValueWithFootnote underlyingSecurityTitle;

        @XmlElement(name = "underlyingSecurityShares")
        private ValueWithFootnote underlyingSecurityShares;

        @XmlElement(name = "underlyingSecurityValue")
        private ValueWithFootnote underlyingSecurityValue;
    }
}
