package org.jds.edgar4j.model.insider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Entity representing an insider (person or entity) in insider trading data
 * 
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-01-01
 */
@Entity
@Table(name = "insiders", indexes = {
    @Index(name = "idx_insider_cik", columnList = "cik"),
    @Index(name = "idx_insider_name", columnList = "full_name"),
    @Index(name = "idx_insider_active", columnList = "is_active")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class Insider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cik", nullable = false, unique = true, length = 10)
    private String cik;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "middle_name", length = 100)
    private String middleName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "suffix", length = 20)
    private String suffix;

    @Enumerated(EnumType.STRING)
    @Column(name = "insider_type", nullable = false)
    private InsiderType insiderType;

    @Column(name = "street_address_1", length = 200)
    private String streetAddress1;

    @Column(name = "street_address_2", length = 200)
    private String streetAddress2;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state", length = 20)
    private String state;

    @Column(name = "zip_code", length = 20)
    private String zipCode;

    @Column(name = "country", length = 50)
    private String country;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "last_transaction_date")
    private LocalDateTime lastTransactionDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "insider", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<InsiderTransaction> insiderTransactions;

    @OneToMany(mappedBy = "insider", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<InsiderCompanyRelationship> companyRelationships;

    public enum InsiderType {
        INDIVIDUAL("Individual Person"),
        ENTITY("Corporate Entity"),
        TRUST("Trust or Estate"),
        PARTNERSHIP("Partnership"),
        FUND("Investment Fund"),
        OTHER("Other");

        private final String description;

        InsiderType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

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
     * Constructs full name from individual name components
     */
    public void constructFullName() {
        StringBuilder nameBuilder = new StringBuilder();
        
        if (firstName != null && !firstName.isEmpty()) {
            nameBuilder.append(firstName);
        }
        
        if (middleName != null && !middleName.isEmpty()) {
            if (nameBuilder.length() > 0) nameBuilder.append(" ");
            nameBuilder.append(middleName);
        }
        
        if (lastName != null && !lastName.isEmpty()) {
            if (nameBuilder.length() > 0) nameBuilder.append(" ");
            nameBuilder.append(lastName);
        }
        
        if (suffix != null && !suffix.isEmpty()) {
            if (nameBuilder.length() > 0) nameBuilder.append(" ");
            nameBuilder.append(suffix);
        }
        
        this.fullName = nameBuilder.toString();
    }

    /**
     * Formats address as single string
     */
    public String getFormattedAddress() {
        StringBuilder addressBuilder = new StringBuilder();
        
        if (streetAddress1 != null && !streetAddress1.isEmpty()) {
            addressBuilder.append(streetAddress1);
        }
        
        if (streetAddress2 != null && !streetAddress2.isEmpty()) {
            if (addressBuilder.length() > 0) addressBuilder.append(", ");
            addressBuilder.append(streetAddress2);
        }
        
        if (city != null && !city.isEmpty()) {
            if (addressBuilder.length() > 0) addressBuilder.append(", ");
            addressBuilder.append(city);
        }
        
        if (state != null && !state.isEmpty()) {
            if (addressBuilder.length() > 0) addressBuilder.append(", ");
            addressBuilder.append(state);
        }
        
        if (zipCode != null && !zipCode.isEmpty()) {
            if (addressBuilder.length() > 0) addressBuilder.append(" ");
            addressBuilder.append(zipCode);
        }
        
        return addressBuilder.toString();
    }
}
