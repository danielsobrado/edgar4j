package org.jds.edgar4j.model.insider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entity representing the relationship between an insider and a company
 * 
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-01-01
 */
@Entity
@Table(name = "insider_company_relationships", indexes = {
    @Index(name = "idx_relationship_insider", columnList = "insider_id"),
    @Index(name = "idx_relationship_company", columnList = "company_id"),
    @Index(name = "idx_relationship_active", columnList = "is_active"),
    @Index(name = "idx_relationship_dates", columnList = "start_date, end_date")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class InsiderCompanyRelationship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "insider_id", nullable = false)
    private Insider insider;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "is_director", nullable = false)
    @Builder.Default
    private Boolean isDirector = false;

    @Column(name = "is_officer", nullable = false)
    @Builder.Default
    private Boolean isOfficer = false;

    @Column(name = "is_ten_percent_owner", nullable = false)
    @Builder.Default
    private Boolean isTenPercentOwner = false;

    @Column(name = "is_other", nullable = false)
    @Builder.Default
    private Boolean isOther = false;

    @Column(name = "officer_title", length = 200)
    private String officerTitle;

    @Column(name = "other_text", length = 500)
    private String otherText;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        
        // Set start date to current date if not provided
        if (startDate == null) {
            startDate = LocalDate.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Determines the primary relationship type
     */
    public String getPrimaryRelationshipType() {
        if (isDirector && isOfficer) {
            return "Director and Officer";
        } else if (isDirector) {
            return "Director";
        } else if (isOfficer) {
            return "Officer";
        } else if (isTenPercentOwner) {
            return "10% Owner";
        } else if (isOther) {
            return "Other";
        } else {
            return "Unknown";
        }
    }

    /**
     * Gets the display title for this relationship
     */
    public String getDisplayTitle() {
        StringBuilder title = new StringBuilder();
        
        if (isDirector) {
            title.append("Director");
        }
        
        if (isOfficer) {
            if (title.length() > 0) {
                title.append(" and ");
            }
            
            if (officerTitle != null && !officerTitle.isEmpty()) {
                title.append(officerTitle);
            } else {
                title.append("Officer");
            }
        }
        
        if (isTenPercentOwner) {
            if (title.length() > 0) {
                title.append(", ");
            }
            title.append("10% Owner");
        }
        
        if (isOther && otherText != null && !otherText.isEmpty()) {
            if (title.length() > 0) {
                title.append(", ");
            }
            title.append(otherText);
        }
        
        return title.toString();
    }

    /**
     * Checks if this relationship is currently active
     */
    public boolean isCurrentlyActive() {
        if (!isActive) {
            return false;
        }
        
        LocalDate today = LocalDate.now();
        
        // Check if start date is in the future
        if (startDate != null && startDate.isAfter(today)) {
            return false;
        }
        
        // Check if end date is in the past
        if (endDate != null && endDate.isBefore(today)) {
            return false;
        }
        
        return true;
    }

    /**
     * Terminates this relationship
     */
    public void terminate() {
        this.endDate = LocalDate.now();
        this.isActive = false;
    }

    /**
     * Checks if this person has insider status
     */
    public boolean hasInsiderStatus() {
        return isDirector || isOfficer || isTenPercentOwner;
    }

    /**
     * Gets the SEC reporting category for this relationship
     */
    public String getSecReportingCategory() {
        if (isDirector && isOfficer) {
            return "DO"; // Director and Officer
        } else if (isDirector) {
            return "D";  // Director
        } else if (isOfficer) {
            return "O";  // Officer
        } else if (isTenPercentOwner) {
            return "S";  // Shareholder (10% owner)
        } else if (isOther) {
            return "X";  // Other
        } else {
            return "U";  // Unknown
        }
    }

    /**
     * Creates a relationship for a director
     */
    public static InsiderCompanyRelationship createDirectorRelationship(Insider insider, Company company) {
        return InsiderCompanyRelationship.builder()
            .insider(insider)
            .company(company)
            .isDirector(true)
            .startDate(LocalDate.now())
            .isActive(true)
            .build();
    }

    /**
     * Creates a relationship for an officer
     */
    public static InsiderCompanyRelationship createOfficerRelationship(Insider insider, Company company, String title) {
        return InsiderCompanyRelationship.builder()
            .insider(insider)
            .company(company)
            .isOfficer(true)
            .officerTitle(title)
            .startDate(LocalDate.now())
            .isActive(true)
            .build();
    }

    /**
     * Creates a relationship for a 10% owner
     */
    public static InsiderCompanyRelationship createTenPercentOwnerRelationship(Insider insider, Company company) {
        return InsiderCompanyRelationship.builder()
            .insider(insider)
            .company(company)
            .isTenPercentOwner(true)
            .startDate(LocalDate.now())
            .isActive(true)
            .build();
    }
}
