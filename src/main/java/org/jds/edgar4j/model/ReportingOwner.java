package org.jds.edgar4j.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents the reporting person (insider) from SEC Form 4
 * Contains information about the insider's identity and relationship to the issuer
 *
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-11-05
 */
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ReportingOwner {

    /** CIK (Central Index Key) of the reporting person */
    private String cik;

    /** Full name of the reporting person */
    private String name;

    /** Last name */
    private String lastName;

    /** First name */
    private String firstName;

    /** Middle name */
    private String middleName;

    /** Street address line 1 */
    private String street1;

    /** Street address line 2 */
    private String street2;

    /** City */
    private String city;

    /** State or country */
    private String stateOrCountry;

    /** ZIP or postal code */
    private String zipCode;

    /** Phone number */
    private String phoneNumber;

    /** Is the reporting person a Director? */
    private boolean isDirector;

    /** Is the reporting person an Officer? */
    private boolean isOfficer;

    /** Is the reporting person a 10% Owner? */
    private boolean isTenPercentOwner;

    /** Is the reporting person classified as "Other"? */
    private boolean isOther;

    /** Officer title (if isOfficer = true) */
    private String officerTitle;

    /** Other text description (if isOther = true) */
    private String otherText;

    /**
     * Get a human-readable relationship description
     * @return relationship description (e.g., "Director", "CEO", "10% Owner")
     */
    public String getRelationshipDescription() {
        if (isDirector && isOfficer && officerTitle != null) {
            return officerTitle + ", Dir";
        }
        if (isDirector) {
            return "Director";
        }
        if (isOfficer && officerTitle != null) {
            return officerTitle;
        }
        if (isOfficer) {
            return "Officer";
        }
        if (isTenPercentOwner) {
            return "10% Owner";
        }
        if (isOther && otherText != null) {
            return otherText;
        }
        if (isOther) {
            return "Other";
        }
        return "Unknown";
    }

    /**
     * Get abbreviated relationship for display
     * @return abbreviated relationship (e.g., "Dir", "CEO", "10%")
     */
    public String getRelationshipAbbreviation() {
        if (isDirector) {
            return "Dir";
        }
        if (isOfficer && officerTitle != null) {
            return officerTitle;
        }
        if (isTenPercentOwner) {
            return "10%";
        }
        return "Other";
    }
}
