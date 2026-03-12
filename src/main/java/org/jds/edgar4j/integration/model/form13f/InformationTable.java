package org.jds.edgar4j.integration.model.form13f;

import java.util.List;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

import lombok.Data;

/**
 * Root element for SEC Form 13F Information Table XML document.
 * Contains the list of holdings reported by an institutional investment manager.
 */
@Data
@XmlRootElement(name = "informationTable", namespace = "http://www.sec.gov/edgar/document/thirteenf/informationtable")
@XmlAccessorType(XmlAccessType.FIELD)
public class InformationTable {

    @XmlElement(name = "infoTable", namespace = "http://www.sec.gov/edgar/document/thirteenf/informationtable")
    private List<InfoTableEntry> infoTables;
}
