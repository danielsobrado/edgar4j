package org.jds.edgar4j.model.insider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Entity representing a company/issuer for insider trading data
 * 
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-01-01
 */
@Entity
@Table(name = "companies", indexes = {
    @Index(name = "idx_company_cik", columnList = "cik"),
    @Index(name = "idx_company_ticker", columnList = "ticker_symbol"),
    @Index(name = "idx_company_name", columnList = "company_name")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cik", nullable = false, unique = true, length = 10)
    private String cik;

    @Column(name = "company_name", nullable = false, length = 200)
    private String companyName;

    @Column(name = "ticker_symbol", length = 10)
    private String tickerSymbol;

    @Column(name = "exchange", length = 20)
    private String exchange;

    @Column(name = "sector", length = 50)
    private String sector;

    @Column(name = "industry", length = 100)
    private String industry;

    @Column(name = "sic_code", length = 4)
    private String sicCode;

    @Column(name = "sic_description", length = 200)
    private String sicDescription;

    @Column(name = "total_shares_outstanding")
    private BigDecimal totalSharesOutstanding;

    @Column(name = "market_cap")
    private BigDecimal marketCap;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "last_filing_date")
    private LocalDateTime lastFilingDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<InsiderTransaction> insiderTransactions;

    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<InsiderCompanyRelationship> insiderRelationships;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Formats CIK to standard 10-digit format with leading zeros
     */
    public void setCik(String cik) {
        if (cik != null && !cik.isEmpty()) {
            this.cik = String.format("%010d", Long.parseLong(cik));
        }
    }

    /**
     * Calculates market capitalization if price data is available
     */
    public void calculateMarketCap(BigDecimal stockPrice) {
        if (totalSharesOutstanding != null && stockPrice != null) {
            this.marketCap = totalSharesOutstanding.multiply(stockPrice);
        }
    }
}
