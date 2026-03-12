package org.jds.edgar4j.integration.model.form13f;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

import lombok.Data;

/**
 * Represents a single entry in the Form 13F Information Table.
 * Each entry represents one security holding.
 */
@Data
@XmlAccessorType(XmlAccessType.FIELD)
public class InfoTableEntry {

    private static final String NS = "http://www.sec.gov/edgar/document/thirteenf/informationtable";

    @XmlElement(name = "nameOfIssuer", namespace = NS)
    private String nameOfIssuer;

    @XmlElement(name = "titleOfClass", namespace = NS)
    private String titleOfClass;

    @XmlElement(name = "cusip", namespace = NS)
    private String cusip;

    @XmlElement(name = "figi", namespace = NS)
    private String figi;

    @XmlElement(name = "value", namespace = NS)
    private Long value;

    @XmlElement(name = "shrsOrPrnAmt", namespace = NS)
    private SharesOrPrincipalAmount shrsOrPrnAmt;

    @XmlElement(name = "putCall", namespace = NS)
    private String putCall;

    @XmlElement(name = "investmentDiscretion", namespace = NS)
    private String investmentDiscretion;

    @XmlElement(name = "otherManager", namespace = NS)
    private String otherManager;

    @XmlElement(name = "votingAuthority", namespace = NS)
    private VotingAuthority votingAuthority;
}
