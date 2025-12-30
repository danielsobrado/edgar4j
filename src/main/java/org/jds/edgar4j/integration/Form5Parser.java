package org.jds.edgar4j.integration;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.time.Instant;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.jds.edgar4j.model.Form4Transaction;
import org.jds.edgar4j.model.Form5;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import lombok.extern.slf4j.Slf4j;

/**
 * Parses SEC Form 5 XML documents into domain model objects.
 */
@Slf4j
@Component
public class Form5Parser {

    private final DocumentBuilderFactory documentBuilderFactory;

    public Form5Parser() {
        this.documentBuilderFactory = OwnershipDomParserSupport.buildDocumentBuilderFactory();
    }

    public Form5 parse(String xml, String accessionNumber) {
        String cleanedXml = OwnershipDomParserSupport.cleanXml(xml);
        if (cleanedXml == null || cleanedXml.isBlank()) {
            return null;
        }

        try {
            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(cleanedXml)));
            doc.getDocumentElement().normalize();

            Element root = doc.getDocumentElement();

            Form5 form5 = new Form5();
            form5.setAccessionNumber(accessionNumber);

            form5.setDocumentType(OwnershipDomParserSupport.getElementText(root, "documentType"));
            form5.setPeriodOfReport(OwnershipDomParserSupport.parseDate(
                    OwnershipDomParserSupport.getElementText(root, "periodOfReport")));

            Element issuer = OwnershipDomParserSupport.getFirstElement(root, "issuer");
            if (issuer != null) {
                form5.setCik(OwnershipDomParserSupport.getElementText(issuer, "issuerCik"));
                form5.setIssuerName(OwnershipDomParserSupport.getElementText(issuer, "issuerName"));
                form5.setTradingSymbol(OwnershipDomParserSupport.getElementText(issuer, "issuerTradingSymbol"));
            }

            Element reportingOwner = OwnershipDomParserSupport.getFirstElement(root, "reportingOwner");
            if (reportingOwner != null) {
                mapReportingOwner(form5, reportingOwner);
            }

            List<Form4Transaction> transactions = new ArrayList<>();
            transactions.addAll(OwnershipDomParserSupport.parseNonDerivativeEntries(root, accessionNumber, "nonDerivativeTransaction"));
            transactions.addAll(OwnershipDomParserSupport.parseDerivativeEntries(root, accessionNumber, "derivativeTransaction"));
            if (!transactions.isEmpty()) {
                form5.setTransactions(transactions);
            }

            List<Form4Transaction> holdings = new ArrayList<>();
            holdings.addAll(OwnershipDomParserSupport.parseNonDerivativeEntries(root, accessionNumber, "nonDerivativeHolding"));
            holdings.addAll(OwnershipDomParserSupport.parseDerivativeEntries(root, accessionNumber, "derivativeHolding"));
            if (!holdings.isEmpty()) {
                form5.setHoldings(holdings);
            }

            Instant now = Instant.now();
            form5.setCreatedAt(now);
            form5.setUpdatedAt(now);

            return form5;
        } catch (Exception e) {
            log.error("Failed to parse Form 5 for accession: {}", accessionNumber, e);
            return null;
        }
    }

    private void mapReportingOwner(Form5 form5, Element reportingOwner) {
        Element ownerId = OwnershipDomParserSupport.getFirstElement(reportingOwner, "reportingOwnerId");
        if (ownerId != null) {
            form5.setRptOwnerCik(OwnershipDomParserSupport.getElementText(ownerId, "rptOwnerCik"));
            form5.setRptOwnerName(OwnershipDomParserSupport.getElementText(ownerId, "rptOwnerName"));
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

            form5.setDirector(isDirector);
            form5.setOfficer(isOfficer);
            form5.setTenPercentOwner(isTenPercentOwner);
            form5.setOther(isOther);
            form5.setOfficerTitle(OwnershipDomParserSupport.getElementText(relationship, "officerTitle"));
            form5.setOwnerType(deriveOwnerTypeFromFlags(isDirector, isOfficer, isTenPercentOwner, isOther));
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
