package org.jds.edgar4j.integration.model.form13f;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

import lombok.Data;

/**
 * Represents the shares or principal amount for a Form 13F holding.
 */
@Data
@XmlAccessorType(XmlAccessType.FIELD)
public class SharesOrPrincipalAmount {

    private static final String NS = "http://www.sec.gov/edgar/document/thirteenf/informationtable";

    /**
     * Number of shares or principal amount.
     */
    @XmlElement(name = "sshPrnamt", namespace = NS)
    private Long sshPrnamt;

    /**
     * Type: "SH" for shares or "PRN" for principal amount.
     */
    @XmlElement(name = "sshPrnamtType", namespace = NS)
    private String sshPrnamtType;
}
