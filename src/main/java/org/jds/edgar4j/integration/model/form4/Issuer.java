package org.jds.edgar4j.integration.model.form4;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

import lombok.Data;

/**
 * Issuer information from Form 4 XML.
 */
@Data
@XmlAccessorType(XmlAccessType.FIELD)
public class Issuer {

    @XmlElement(name = "issuerCik")
    private String cik;

    @XmlElement(name = "issuerName")
    private String name;

    @XmlElement(name = "issuerTradingSymbol")
    private String tradingSymbol;
}
