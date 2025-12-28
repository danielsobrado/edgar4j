package org.jds.edgar4j.integration.model.form4;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

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
