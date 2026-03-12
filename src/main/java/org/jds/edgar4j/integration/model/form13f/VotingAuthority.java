package org.jds.edgar4j.integration.model.form13f;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

import lombok.Data;

/**
 * Represents the voting authority breakdown for a Form 13F holding.
 */
@Data
@XmlAccessorType(XmlAccessType.FIELD)
public class VotingAuthority {

    private static final String NS = "http://www.sec.gov/edgar/document/thirteenf/informationtable";

    /**
     * Number of shares with sole voting authority.
     */
    @XmlElement(name = "Sole", namespace = NS)
    private Long sole;

    /**
     * Number of shares with shared voting authority.
     */
    @XmlElement(name = "Shared", namespace = NS)
    private Long shared;

    /**
     * Number of shares with no voting authority.
     */
    @XmlElement(name = "None", namespace = NS)
    private Long none;
}
