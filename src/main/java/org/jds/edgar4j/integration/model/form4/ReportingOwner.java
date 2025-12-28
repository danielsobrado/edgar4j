package org.jds.edgar4j.integration.model.form4;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

import lombok.Data;

/**
 * Reporting owner information from Form 4 XML.
 */
@Data
@XmlAccessorType(XmlAccessType.FIELD)
public class ReportingOwner {

    @XmlElement(name = "reportingOwnerId")
    private ReportingOwnerId reportingOwnerId;

    @XmlElement(name = "reportingOwnerAddress")
    private ReportingOwnerAddress reportingOwnerAddress;

    @XmlElement(name = "reportingOwnerRelationship")
    private ReportingOwnerRelationship reportingOwnerRelationship;

    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class ReportingOwnerId {

        @XmlElement(name = "rptOwnerCik")
        private String cik;

        @XmlElement(name = "rptOwnerName")
        private String name;
    }

    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class ReportingOwnerAddress {

        @XmlElement(name = "rptOwnerStreet1")
        private String street1;

        @XmlElement(name = "rptOwnerStreet2")
        private String street2;

        @XmlElement(name = "rptOwnerCity")
        private String city;

        @XmlElement(name = "rptOwnerState")
        private String state;

        @XmlElement(name = "rptOwnerZipCode")
        private String zipCode;

        @XmlElement(name = "rptOwnerStateDescription")
        private String stateDescription;
    }

    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class ReportingOwnerRelationship {

        @XmlElement(name = "isDirector")
        private String isDirector;

        @XmlElement(name = "isOfficer")
        private String isOfficer;

        @XmlElement(name = "isTenPercentOwner")
        private String isTenPercentOwner;

        @XmlElement(name = "isOther")
        private String isOther;

        @XmlElement(name = "officerTitle")
        private String officerTitle;

        @XmlElement(name = "otherText")
        private String otherText;

        public boolean isDirectorFlag() {
            return "1".equals(isDirector) || "true".equalsIgnoreCase(isDirector);
        }

        public boolean isOfficerFlag() {
            return "1".equals(isOfficer) || "true".equalsIgnoreCase(isOfficer);
        }

        public boolean isTenPercentOwnerFlag() {
            return "1".equals(isTenPercentOwner) || "true".equalsIgnoreCase(isTenPercentOwner);
        }

        public boolean isOtherFlag() {
            return "1".equals(isOther) || "true".equalsIgnoreCase(isOther);
        }
    }
}
