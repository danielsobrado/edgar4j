package org.jds.edgar4j.integration.model.form13dg;

import java.util.List;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

import lombok.Data;

/**
 * Root element for SEC Schedule 13D/13G XML document.
 * Used for parsing the new XML-based 13D/13G filings (effective December 2024).
 */
@Data
@XmlRootElement(name = "edgarSubmission")
@XmlAccessorType(XmlAccessType.FIELD)
public class Schedule13Document {

    @XmlElement(name = "schemaVersion")
    private String schemaVersion;

    @XmlElement(name = "headerData")
    private HeaderData headerData;

    @XmlElement(name = "formData")
    private FormData formData;

    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class HeaderData {

        @XmlElement(name = "submissionType")
        private String submissionType;

        @XmlElement(name = "filerInfo")
        private FilerInfo filerInfo;
    }

    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class FilerInfo {

        @XmlElement(name = "filer")
        private Filer filer;
    }

    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Filer {

        @XmlElement(name = "credentials")
        private Credentials credentials;
    }

    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Credentials {

        @XmlElement(name = "cik")
        private String cik;

        @XmlElement(name = "ccc")
        private String ccc;
    }

    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class FormData {

        @XmlElement(name = "coverPage")
        private CoverPage coverPage;

        @XmlElement(name = "reportingPersonInfo")
        private List<ReportingPersonInfo> reportingPersons;

        @XmlElement(name = "signatureBlock")
        private SignatureBlock signatureBlock;
    }

    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class CoverPage {

        @XmlElement(name = "issuerName")
        private String issuerName;

        @XmlElement(name = "issuerCik")
        private String issuerCik;

        @XmlElement(name = "cusipNumber")
        private String cusipNumber;

        @XmlElement(name = "securityTitle")
        private String securityTitle;

        @XmlElement(name = "dateOfEvent")
        private String dateOfEvent;

        @XmlElement(name = "filedDate")
        private String filedDate;

        @XmlElement(name = "amendmentNo")
        private String amendmentNo;

        @XmlElement(name = "isAmendment")
        private String isAmendment;
    }

    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class ReportingPersonInfo {

        @XmlElement(name = "nameOfReportingPerson")
        private String nameOfReportingPerson;

        @XmlElement(name = "reportingPersonCik")
        private String reportingPersonCik;

        @XmlElement(name = "identificationOrResidence")
        private IdentificationOrResidence identificationOrResidence;

        @XmlElement(name = "address")
        private Address address;

        @XmlElement(name = "typeOfReportingPerson")
        private String typeOfReportingPerson;

        @XmlElement(name = "ownershipInfo")
        private OwnershipInfo ownershipInfo;
    }

    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class IdentificationOrResidence {

        @XmlElement(name = "citizenshipOrPlaceOfOrganization")
        private String citizenshipOrPlaceOfOrganization;
    }

    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Address {

        @XmlElement(name = "street1")
        private String street1;

        @XmlElement(name = "street2")
        private String street2;

        @XmlElement(name = "city")
        private String city;

        @XmlElement(name = "stateOrCountry")
        private String stateOrCountry;

        @XmlElement(name = "zipCode")
        private String zipCode;
    }

    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class OwnershipInfo {

        @XmlElement(name = "soleVotingPower")
        private String soleVotingPower;

        @XmlElement(name = "sharedVotingPower")
        private String sharedVotingPower;

        @XmlElement(name = "soleDispositivePower")
        private String soleDispositivePower;

        @XmlElement(name = "sharedDispositivePower")
        private String sharedDispositivePower;

        @XmlElement(name = "aggregateAmountBeneficiallyOwned")
        private String aggregateAmountBeneficiallyOwned;

        @XmlElement(name = "checkIfExcludesCertainShares")
        private String checkIfExcludesCertainShares;

        @XmlElement(name = "percentOfClass")
        private String percentOfClass;
    }

    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class SignatureBlock {

        @XmlElement(name = "signatureName")
        private String signatureName;

        @XmlElement(name = "signatureTitle")
        private String signatureTitle;

        @XmlElement(name = "signatureDate")
        private String signatureDate;
    }
}
