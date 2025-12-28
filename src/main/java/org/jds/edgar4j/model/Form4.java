package org.jds.edgar4j.model;

import java.util.Date;
import java.util.List;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = false)
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Document(collection = "form4")
@CompoundIndexes({
    @CompoundIndex(name = "cik_date_idx", def = "{'cik': 1, 'transactionDate': -1}"),
    @CompoundIndex(name = "symbol_date_idx", def = "{'tradingSymbol': 1, 'transactionDate': -1}"),
    @CompoundIndex(name = "owner_date_idx", def = "{'rptOwnerName': 1, 'transactionDate': -1}")
})
public class Form4 {

    @Id
    private String id;

    @Indexed(unique = true)
    private String accessionNumber;

    private String documentType;

    private Date periodOfReport;

    // Issuer information
    @Indexed
    private String cik;

    private String issuerName;

    @Indexed
    private String tradingSymbol;

    // Reporting owner information
    private String rptOwnerCik;

    @Indexed
    private String rptOwnerName;

    private String officerTitle;

    private boolean isDirector;

    private boolean isOfficer;

    private boolean isTenPercentOwner;

    private boolean isOther;

    /**
     * Derived owner type for backward compatibility.
     * Values: Director, Officer, 10% Owner, Other, Unknown
     */
    private String ownerType;

    // Primary transaction fields (for backward compatibility with single-transaction model)
    private String securityTitle;

    @Indexed
    private Date transactionDate;

    private Float transactionShares;

    private Float transactionPricePerShare;

    private Float transactionValue;

    private String acquiredDisposedCode;

    // All transactions (non-derivative and derivative)
    private List<Form4Transaction> transactions;

    // Metadata
    private Date createdAt;

    private Date updatedAt;

    /**
     * Determines if transaction was a purchase (A) or sale (D).
     */
    public boolean isBuy() {
        return "A".equalsIgnoreCase(acquiredDisposedCode);
    }

    /**
     * Determines if transaction was a sale.
     */
    public boolean isSell() {
        return "D".equalsIgnoreCase(acquiredDisposedCode);
    }

    /**
     * Gets total buy value across all transactions.
     */
    public Float getTotalBuyValue() {
        if (transactions == null) return null;
        return (float) transactions.stream()
            .filter(t -> "A".equalsIgnoreCase(t.getAcquiredDisposedCode()))
            .filter(t -> t.getTransactionValue() != null)
            .mapToDouble(Form4Transaction::getTransactionValue)
            .sum();
    }

    /**
     * Gets total sell value across all transactions.
     */
    public Float getTotalSellValue() {
        if (transactions == null) return null;
        return (float) transactions.stream()
            .filter(t -> "D".equalsIgnoreCase(t.getAcquiredDisposedCode()))
            .filter(t -> t.getTransactionValue() != null)
            .mapToDouble(Form4Transaction::getTransactionValue)
            .sum();
    }
}

