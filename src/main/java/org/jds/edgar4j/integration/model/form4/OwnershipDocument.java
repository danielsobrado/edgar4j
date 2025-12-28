package org.jds.edgar4j.integration.model.form4;

import java.util.List;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;

import lombok.Data;

/**
 * Root element for SEC Form 4 XML document.
 * Maps to ownershipDocument element per SEC EDGAR XML spec.
 */
@Data
@XmlRootElement(name = "ownershipDocument")
@XmlAccessorType(XmlAccessType.FIELD)
public class OwnershipDocument {

    @XmlElement(name = "schemaVersion")
    private String schemaVersion;

    @XmlElement(name = "documentType")
    private String documentType;

    @XmlElement(name = "periodOfReport")
    private String periodOfReport;

    @XmlElement(name = "dateOfOriginalSubmission")
    private String dateOfOriginalSubmission;

    @XmlElement(name = "noSecuritiesOwned")
    private Boolean noSecuritiesOwned;

    @XmlElement(name = "notSubjectToSection16")
    private Boolean notSubjectToSection16;

    @XmlElement(name = "form3HoldingsReported")
    private Boolean form3HoldingsReported;

    @XmlElement(name = "form4TransactionsReported")
    private Boolean form4TransactionsReported;

    @XmlElement(name = "issuer")
    private Issuer issuer;

    @XmlElementWrapper(name = "reportingOwner")
    @XmlElement(name = "reportingOwnerId")
    private List<ReportingOwner> reportingOwners;

    @XmlElement(name = "reportingOwner")
    private ReportingOwner reportingOwner;

    @XmlElement(name = "nonDerivativeTable")
    private NonDerivativeTable nonDerivativeTable;

    @XmlElement(name = "derivativeTable")
    private DerivativeTable derivativeTable;

    @XmlElementWrapper(name = "footnotes")
    @XmlElement(name = "footnote")
    private List<Footnote> footnotes;

    @XmlElement(name = "remarks")
    private String remarks;

    @XmlElementWrapper(name = "ownerSignature")
    @XmlElement(name = "signatureName")
    private List<OwnerSignature> ownerSignatures;

    @XmlElement(name = "ownerSignature")
    private OwnerSignature ownerSignature;
}
