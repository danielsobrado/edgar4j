package org.jds.edgar4j.integration.model.form4;

import java.util.List;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

import lombok.Data;

/**
 * Table I - Non-Derivative Securities from Form 4 XML.
 */
@Data
@XmlAccessorType(XmlAccessType.FIELD)
public class NonDerivativeTable {

    @XmlElement(name = "nonDerivativeTransaction")
    private List<NonDerivativeTransaction> transactions;

    @XmlElement(name = "nonDerivativeHolding")
    private List<NonDerivativeHolding> holdings;

    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class NonDerivativeTransaction {

        @XmlElement(name = "securityTitle")
        private ValueWithFootnote securityTitle;

        @XmlElement(name = "transactionDate")
        private ValueWithFootnote transactionDate;

        @XmlElement(name = "deemedExecutionDate")
        private ValueWithFootnote deemedExecutionDate;

        @XmlElement(name = "transactionCoding")
        private TransactionCoding transactionCoding;

        @XmlElement(name = "transactionTimeliness")
        private ValueWithFootnote transactionTimeliness;

        @XmlElement(name = "transactionAmounts")
        private TransactionAmounts transactionAmounts;

        @XmlElement(name = "postTransactionAmounts")
        private PostTransactionAmounts postTransactionAmounts;

        @XmlElement(name = "ownershipNature")
        private OwnershipNature ownershipNature;
    }

    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class NonDerivativeHolding {

        @XmlElement(name = "securityTitle")
        private ValueWithFootnote securityTitle;

        @XmlElement(name = "postTransactionAmounts")
        private PostTransactionAmounts postTransactionAmounts;

        @XmlElement(name = "ownershipNature")
        private OwnershipNature ownershipNature;
    }

    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "nonDerivativeTransactionCoding")
    public static class TransactionCoding {

        @XmlElement(name = "transactionFormType")
        private String transactionFormType;

        @XmlElement(name = "transactionCode")
        private String transactionCode;

        @XmlElement(name = "equitySwapInvolved")
        private String equitySwapInvolved;

        @XmlElement(name = "footnoteId")
        private FootnoteId footnoteId;

        public boolean isEquitySwapInvolved() {
            return "1".equals(equitySwapInvolved) || "true".equalsIgnoreCase(equitySwapInvolved);
        }
    }

    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class TransactionAmounts {

        @XmlElement(name = "transactionShares")
        private ValueWithFootnote transactionShares;

        @XmlElement(name = "transactionPricePerShare")
        private ValueWithFootnote transactionPricePerShare;

        @XmlElement(name = "transactionAcquiredDisposedCode")
        private ValueWithFootnote transactionAcquiredDisposedCode;
    }

    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class PostTransactionAmounts {

        @XmlElement(name = "sharesOwnedFollowingTransaction")
        private ValueWithFootnote sharesOwnedFollowingTransaction;

        @XmlElement(name = "valueOwnedFollowingTransaction")
        private ValueWithFootnote valueOwnedFollowingTransaction;
    }

    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class OwnershipNature {

        @XmlElement(name = "directOrIndirectOwnership")
        private ValueWithFootnote directOrIndirectOwnership;

        @XmlElement(name = "natureOfOwnership")
        private ValueWithFootnote natureOfOwnership;
    }
}
