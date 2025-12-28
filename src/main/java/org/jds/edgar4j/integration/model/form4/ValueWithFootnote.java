package org.jds.edgar4j.integration.model.form4;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlValue;

import lombok.Data;

/**
 * Generic value element with optional footnote reference.
 */
@Data
@XmlAccessorType(XmlAccessType.FIELD)
public class ValueWithFootnote {

    @XmlElement(name = "value")
    private String value;

    @XmlElement(name = "footnoteId")
    private FootnoteId footnoteId;

    public String getValue() {
        return value;
    }
}

@Data
@XmlAccessorType(XmlAccessType.FIELD)
class FootnoteId {

    @XmlAttribute(name = "id")
    private String id;
}

@Data
@XmlAccessorType(XmlAccessType.FIELD)
class Footnote {

    @XmlAttribute(name = "id")
    private String id;

    @XmlValue
    private String text;
}

@Data
@XmlAccessorType(XmlAccessType.FIELD)
class OwnerSignature {

    @XmlElement(name = "signatureName")
    private String signatureName;

    @XmlElement(name = "signatureDate")
    private String signatureDate;
}
