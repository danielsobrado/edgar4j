package org.jds.edgar4j.integration;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.time.Instant;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.jds.edgar4j.model.Form3;
import org.jds.edgar4j.model.Form4Transaction;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import lombok.extern.slf4j.Slf4j;

/**
 * Parses SEC Form 3 XML documents into domain model objects.
 */
@Slf4j
@Component
public class Form3Parser {

    private final DocumentBuilderFactory documentBuilderFactory;

    public Form3Parser() {
        this.documentBuilderFactory = OwnershipDomParserSupport.buildDocumentBuilderFactory();
    }

    public Form3 parse(String xml, String accessionNumber) {
        String cleanedXml = OwnershipDomParserSupport.cleanXml(xml);
        if (cleanedXml == null || cleanedXml.isBlank()) {
            return null;
        }

        try {
            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(cleanedXml)));
            doc.getDocumentElement().normalize();

            Element root = doc.getDocumentElement();

            Form3 form3 = new Form3();
            form3.setAccessionNumber(accessionNumber);

            form3.setDocumentType(OwnershipDomParserSupport.getElementText(root, "documentType"));
            form3.setPeriodOfReport(OwnershipDomParserSupport.parseDate(
                    OwnershipDomParserSupport.getElementText(root, "periodOfReport")));

            Element issuer = OwnershipDomParserSupport.getFirstElement(root, "issuer");
            if (issuer != null) {
                form3.setCik(OwnershipDomParserSupport.getElementText(issuer, "issuerCik"));
                form3.setIssuerName(OwnershipDomParserSupport.getElementText(issuer, "issuerName"));
                form3.setTradingSymbol(OwnershipDomParserSupport.getElementText(issuer, "issuerTradingSymbol"));
            }

            Element reportingOwner = OwnershipDomParserSupport.getFirstElement(root, "reportingOwner");
            if (reportingOwner != null) {
                mapReportingOwner(form3, reportingOwner);
            }

            List<Form4Transaction> holdings = new ArrayList<>();
            holdings.addAll(OwnershipDomParserSupport.parseNonDerivativeEntries(root, accessionNumber, "nonDerivativeHolding"));
            holdings.addAll(OwnershipDomParserSupport.parseNonDerivativeEntries(root, accessionNumber, "nonDerivativeTransaction"));
            holdings.addAll(OwnershipDomParserSupport.parseDerivativeEntries(root, accessionNumber, "derivativeHolding"));
            holdings.addAll(OwnershipDomParserSupport.parseDerivativeEntries(root, accessionNumber, "derivativeTransaction"));

            if (!holdings.isEmpty()) {
                form3.setHoldings(holdings);
            }

            Instant now = Instant.now();
            form3.setCreatedAt(now);
            form3.setUpdatedAt(now);

            return form3;
        } catch (Exception e) {
            log.error("Failed to parse Form 3 for accession: {}", accessionNumber, e);
            return null;
        }
    }

    private void mapReportingOwner(Form3 form3, Element reportingOwner) {
        Element ownerId = OwnershipDomParserSupport.getFirstElement(reportingOwner, "reportingOwnerId");
        if (ownerId != null) {
            form3.setRptOwnerCik(OwnershipDomParserSupport.getElementText(ownerId, "rptOwnerCik"));
            form3.setRptOwnerName(OwnershipDomParserSupport.getElementText(ownerId, "rptOwnerName"));
        }

        Element relationship = OwnershipDomParserSupport.getFirstElement(reportingOwner, "reportingOwnerRelationship");
        if (relationship != null) {
            boolean isDirector = OwnershipDomParserSupport.isTrue(
                    OwnershipDomParserSupport.getElementText(relationship, "isDirector"));
            boolean isOfficer = OwnershipDomParserSupport.isTrue(
                    OwnershipDomParserSupport.getElementText(relationship, "isOfficer"));
            boolean isTenPercentOwner = OwnershipDomParserSupport.isTrue(
                    OwnershipDomParserSupport.getElementText(relationship, "isTenPercentOwner"));
            boolean isOther = OwnershipDomParserSupport.isTrue(
                    OwnershipDomParserSupport.getElementText(relationship, "isOther"));

            form3.setDirector(isDirector);
            form3.setOfficer(isOfficer);
            form3.setTenPercentOwner(isTenPercentOwner);
            form3.setOther(isOther);
            form3.setOfficerTitle(OwnershipDomParserSupport.getElementText(relationship, "officerTitle"));
            form3.setOwnerType(deriveOwnerTypeFromFlags(isDirector, isOfficer, isTenPercentOwner, isOther));
        }
    }

    private String deriveOwnerTypeFromFlags(boolean isDirector, boolean isOfficer,
                                            boolean isTenPercentOwner, boolean isOther) {
        if (isDirector) return "Director";
        if (isOfficer) return "Officer";
        if (isTenPercentOwner) return "10% Owner";
        if (isOther) return "Other";
        return "Unknown";
    }
}
