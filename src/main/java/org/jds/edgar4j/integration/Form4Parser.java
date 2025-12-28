package org.jds.edgar4j.integration;

import java.io.StringReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.jds.edgar4j.integration.model.form4.DerivativeTable;
import org.jds.edgar4j.integration.model.form4.NonDerivativeTable;
import org.jds.edgar4j.integration.model.form4.OwnershipDocument;
import org.jds.edgar4j.integration.model.form4.ReportingOwner;
import org.jds.edgar4j.integration.model.form4.ValueWithFootnote;
import org.jds.edgar4j.model.Form4;
import org.jds.edgar4j.model.Form4Transaction;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import lombok.extern.slf4j.Slf4j;

/**
 * Parses SEC Form 4 XML documents into domain model objects.
 * Uses JAXB as primary parser with DOM fallback for malformed XML.
 * Handles both non-derivative and derivative transactions.
 */
@Slf4j
@Component
public class Form4Parser {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    private static final String TRANSACTION_TYPE_NON_DERIVATIVE = "NON_DERIVATIVE";
    private static final String TRANSACTION_TYPE_DERIVATIVE = "DERIVATIVE";

    private JAXBContext jaxbContext;
    private DocumentBuilderFactory documentBuilderFactory;

    public Form4Parser() {
        try {
            this.jaxbContext = JAXBContext.newInstance(OwnershipDocument.class);
            this.documentBuilderFactory = DocumentBuilderFactory.newInstance();
            this.documentBuilderFactory.setNamespaceAware(false);
            // Security: disable external entities
            this.documentBuilderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        } catch (Exception e) {
            log.error("Failed to initialize Form4 parser", e);
            throw new RuntimeException("Failed to initialize Form4 parser", e);
        }
    }

    /**
     * Parses raw XML string into Form4 domain object with all transactions.
     */
    public Form4 parse(String xml, String accessionNumber) {
        log.debug("Parsing Form 4 XML for accession: {}", accessionNumber);

        String cleanedXml = cleanXml(xml);
        if (cleanedXml == null || cleanedXml.isBlank()) {
            log.warn("Empty XML content after cleaning for accession: {}", accessionNumber);
            return null;
        }

        // Try JAXB parsing first
        OwnershipDocument doc = parseXmlWithJaxb(cleanedXml);

        // Fall back to DOM parsing if JAXB fails
        if (doc == null) {
            log.debug("JAXB parsing failed, trying DOM fallback for: {}", accessionNumber);
            return parseWithDom(cleanedXml, accessionNumber);
        }

        return mapToForm4(doc, accessionNumber);
    }

    /**
     * Cleans XML by removing problematic content (HTML wrappers, etc.).
     */
    private String cleanXml(String xml) {
        if (xml == null) {
            return null;
        }

        int startIdx = xml.indexOf("<?xml");
        if (startIdx == -1) {
            startIdx = xml.indexOf("<ownershipDocument");
        }

        if (startIdx == -1) {
            log.warn("No XML content found in document");
            return xml;
        }

        int endIdx = xml.lastIndexOf("</ownershipDocument>");
        if (endIdx == -1) {
            return xml.substring(startIdx);
        }

        return xml.substring(startIdx, endIdx + "</ownershipDocument>".length());
    }

    /**
     * Parses cleaned XML into OwnershipDocument using JAXB.
     */
    private OwnershipDocument parseXmlWithJaxb(String xml) {
        try {
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            StringReader reader = new StringReader(xml);
            return (OwnershipDocument) unmarshaller.unmarshal(reader);
        } catch (JAXBException e) {
            log.debug("JAXB parsing failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * DOM-based fallback parser for malformed XML.
     */
    private Form4 parseWithDom(String xml, String accessionNumber) {
        try {
            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xml)));
            doc.getDocumentElement().normalize();

            Form4 form4 = new Form4();
            form4.setAccessionNumber(accessionNumber);

            Element root = doc.getDocumentElement();

            form4.setDocumentType(getElementText(root, "documentType"));
            form4.setPeriodOfReport(parseDate(getElementText(root, "periodOfReport")));

            Element issuer = getFirstElement(root, "issuer");
            if (issuer != null) {
                form4.setCik(getElementText(issuer, "issuerCik"));
                form4.setIssuerName(getElementText(issuer, "issuerName"));
                form4.setTradingSymbol(getElementText(issuer, "issuerTradingSymbol"));
            }

            Element reportingOwner = getFirstElement(root, "reportingOwner");
            if (reportingOwner != null) {
                Element ownerId = getFirstElement(reportingOwner, "reportingOwnerId");
                if (ownerId != null) {
                    form4.setRptOwnerCik(getElementText(ownerId, "rptOwnerCik"));
                    form4.setRptOwnerName(getElementText(ownerId, "rptOwnerName"));
                }

                Element relationship = getFirstElement(reportingOwner, "reportingOwnerRelationship");
                if (relationship != null) {
                    form4.setDirector(isTrue(getElementText(relationship, "isDirector")));
                    form4.setOfficer(isTrue(getElementText(relationship, "isOfficer")));
                    form4.setTenPercentOwner(isTrue(getElementText(relationship, "isTenPercentOwner")));
                    form4.setOther(isTrue(getElementText(relationship, "isOther")));
                    form4.setOfficerTitle(getElementText(relationship, "officerTitle"));
                    form4.setOwnerType(deriveOwnerTypeFromFlags(
                            form4.isDirector(), form4.isOfficer(),
                            form4.isTenPercentOwner(), form4.isOther()));
                }
            }

            List<Form4Transaction> transactions = new ArrayList<>();
            transactions.addAll(parseNonDerivativeTransactionsFromDom(root, accessionNumber));
            transactions.addAll(parseDerivativeTransactionsFromDom(root, accessionNumber));
            form4.setTransactions(transactions);

            if (!transactions.isEmpty()) {
                Form4Transaction firstTx = transactions.get(0);
                form4.setSecurityTitle(firstTx.getSecurityTitle());
                form4.setTransactionDate(firstTx.getTransactionDate());
                form4.setTransactionShares(firstTx.getTransactionShares());
                form4.setTransactionPricePerShare(firstTx.getTransactionPricePerShare());
                form4.setAcquiredDisposedCode(firstTx.getAcquiredDisposedCode());
            }

            log.debug("DOM parsing succeeded for: {}", accessionNumber);
            return form4;

        } catch (Exception e) {
            log.error("DOM parsing failed for accession: {}", accessionNumber, e);
            return null;
        }
    }

    private List<Form4Transaction> parseNonDerivativeTransactionsFromDom(Element root, String accessionNumber) {
        List<Form4Transaction> transactions = new ArrayList<>();

        Element table = getFirstElement(root, "nonDerivativeTable");
        if (table == null) return transactions;

        NodeList txNodes = table.getElementsByTagName("nonDerivativeTransaction");
        for (int i = 0; i < txNodes.getLength(); i++) {
            Element tx = (Element) txNodes.item(i);
            Form4Transaction transaction = new Form4Transaction();
            transaction.setAccessionNumber(accessionNumber);
            transaction.setTransactionType(TRANSACTION_TYPE_NON_DERIVATIVE);

            transaction.setSecurityTitle(getNestedValue(tx, "securityTitle"));
            transaction.setTransactionDate(parseDate(getNestedValue(tx, "transactionDate")));

            Element coding = getFirstElement(tx, "transactionCoding");
            if (coding != null) {
                transaction.setTransactionCode(getElementText(coding, "transactionCode"));
                transaction.setTransactionFormType(getElementText(coding, "transactionFormType"));
                transaction.setEquitySwapInvolved(isTrue(getElementText(coding, "equitySwapInvolved")));
            }

            Element amounts = getFirstElement(tx, "transactionAmounts");
            if (amounts != null) {
                transaction.setTransactionShares(parseFloat(getNestedValue(amounts, "transactionShares")));
                transaction.setTransactionPricePerShare(parseFloat(getNestedValue(amounts, "transactionPricePerShare")));
                transaction.setAcquiredDisposedCode(getNestedValue(amounts, "transactionAcquiredDisposedCode"));
            }

            Element postAmounts = getFirstElement(tx, "postTransactionAmounts");
            if (postAmounts != null) {
                transaction.setSharesOwnedFollowingTransaction(
                        parseFloat(getNestedValue(postAmounts, "sharesOwnedFollowingTransaction")));
            }

            Element ownership = getFirstElement(tx, "ownershipNature");
            if (ownership != null) {
                transaction.setDirectOrIndirectOwnership(getNestedValue(ownership, "directOrIndirectOwnership"));
                transaction.setNatureOfOwnership(getNestedValue(ownership, "natureOfOwnership"));
            }

            if (transaction.getTransactionShares() != null && transaction.getTransactionPricePerShare() != null) {
                transaction.setTransactionValue(
                        transaction.getTransactionShares() * transaction.getTransactionPricePerShare());
            }

            transactions.add(transaction);
        }

        return transactions;
    }

    private List<Form4Transaction> parseDerivativeTransactionsFromDom(Element root, String accessionNumber) {
        List<Form4Transaction> transactions = new ArrayList<>();

        Element table = getFirstElement(root, "derivativeTable");
        if (table == null) return transactions;

        NodeList txNodes = table.getElementsByTagName("derivativeTransaction");
        for (int i = 0; i < txNodes.getLength(); i++) {
            Element tx = (Element) txNodes.item(i);
            Form4Transaction transaction = new Form4Transaction();
            transaction.setAccessionNumber(accessionNumber);
            transaction.setTransactionType(TRANSACTION_TYPE_DERIVATIVE);

            transaction.setSecurityTitle(getNestedValue(tx, "securityTitle"));
            transaction.setTransactionDate(parseDate(getNestedValue(tx, "transactionDate")));
            transaction.setExercisePrice(parseFloat(getNestedValue(tx, "conversionOrExercisePrice")));
            transaction.setExpirationDate(parseDate(getNestedValue(tx, "expirationDate")));

            Element coding = getFirstElement(tx, "transactionCoding");
            if (coding != null) {
                transaction.setTransactionCode(getElementText(coding, "transactionCode"));
                transaction.setTransactionFormType(getElementText(coding, "transactionFormType"));
            }

            Element amounts = getFirstElement(tx, "transactionAmounts");
            if (amounts != null) {
                transaction.setTransactionShares(parseFloat(getNestedValue(amounts, "transactionShares")));
                transaction.setTransactionPricePerShare(parseFloat(getNestedValue(amounts, "transactionPricePerShare")));
                transaction.setAcquiredDisposedCode(getNestedValue(amounts, "transactionAcquiredDisposedCode"));
            }

            Element underlying = getFirstElement(tx, "underlyingSecurity");
            if (underlying != null) {
                transaction.setUnderlyingSecurityTitle(getNestedValue(underlying, "underlyingSecurityTitle"));
                transaction.setUnderlyingSecurityShares(parseFloat(getNestedValue(underlying, "underlyingSecurityShares")));
            }

            transactions.add(transaction);
        }

        return transactions;
    }

    private Element getFirstElement(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        return nodes.getLength() > 0 ? (Element) nodes.item(0) : null;
    }

    private String getElementText(Element parent, String tagName) {
        Element element = getFirstElement(parent, tagName);
        return element != null ? element.getTextContent().trim() : null;
    }

    private String getNestedValue(Element parent, String tagName) {
        Element element = getFirstElement(parent, tagName);
        if (element == null) return null;

        Element valueElement = getFirstElement(element, "value");
        if (valueElement != null) {
            return valueElement.getTextContent().trim();
        }
        return element.getTextContent().trim();
    }

    private boolean isTrue(String value) {
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }

    private String deriveOwnerTypeFromFlags(boolean isDirector, boolean isOfficer,
                                             boolean isTenPercentOwner, boolean isOther) {
        if (isDirector) return "Director";
        if (isOfficer) return "Officer";
        if (isTenPercentOwner) return "10% Owner";
        if (isOther) return "Other";
        return "Unknown";
    }

    /**
     * Maps parsed XML document to Form4 domain model.
     */
    private Form4 mapToForm4(OwnershipDocument doc, String accessionNumber) {
        Form4 form4 = new Form4();
        form4.setAccessionNumber(accessionNumber);
        form4.setDocumentType(doc.getDocumentType());
        form4.setPeriodOfReport(parseDate(doc.getPeriodOfReport()));

        if (doc.getIssuer() != null) {
            form4.setCik(doc.getIssuer().getCik());
            form4.setIssuerName(doc.getIssuer().getName());
            form4.setTradingSymbol(doc.getIssuer().getTradingSymbol());
        }

        ReportingOwner owner = doc.getReportingOwner();
        if (owner != null) {
            mapReportingOwner(form4, owner);
        }

        List<Form4Transaction> transactions = new ArrayList<>();
        transactions.addAll(parseNonDerivativeTransactions(doc, accessionNumber));
        transactions.addAll(parseDerivativeTransactions(doc, accessionNumber));
        form4.setTransactions(transactions);

        if (!transactions.isEmpty()) {
            Form4Transaction firstTx = transactions.get(0);
            form4.setSecurityTitle(firstTx.getSecurityTitle());
            form4.setTransactionDate(firstTx.getTransactionDate());
            form4.setTransactionShares(firstTx.getTransactionShares());
            form4.setTransactionPricePerShare(firstTx.getTransactionPricePerShare());
            form4.setAcquiredDisposedCode(firstTx.getAcquiredDisposedCode());
        }

        return form4;
    }

    private void mapReportingOwner(Form4 form4, ReportingOwner owner) {
        if (owner.getReportingOwnerId() != null) {
            form4.setRptOwnerCik(owner.getReportingOwnerId().getCik());
            form4.setRptOwnerName(owner.getReportingOwnerId().getName());
        }

        if (owner.getReportingOwnerRelationship() != null) {
            var rel = owner.getReportingOwnerRelationship();
            form4.setDirector(rel.isDirectorFlag());
            form4.setOfficer(rel.isOfficerFlag());
            form4.setTenPercentOwner(rel.isTenPercentOwnerFlag());
            form4.setOther(rel.isOtherFlag());
            form4.setOfficerTitle(rel.getOfficerTitle());
            form4.setOwnerType(deriveOwnerType(rel));
        }
    }

    private String deriveOwnerType(ReportingOwner.ReportingOwnerRelationship rel) {
        if (rel.isDirectorFlag()) return "Director";
        if (rel.isOfficerFlag()) return "Officer";
        if (rel.isTenPercentOwnerFlag()) return "10% Owner";
        if (rel.isOtherFlag()) return "Other";
        return "Unknown";
    }

    private List<Form4Transaction> parseNonDerivativeTransactions(OwnershipDocument doc, String accessionNumber) {
        List<Form4Transaction> transactions = new ArrayList<>();

        if (doc.getNonDerivativeTable() == null || doc.getNonDerivativeTable().getTransactions() == null) {
            return transactions;
        }

        for (NonDerivativeTable.NonDerivativeTransaction tx : doc.getNonDerivativeTable().getTransactions()) {
            Form4Transaction transaction = new Form4Transaction();
            transaction.setAccessionNumber(accessionNumber);
            transaction.setTransactionType(TRANSACTION_TYPE_NON_DERIVATIVE);

            transaction.setSecurityTitle(extractValue(tx.getSecurityTitle()));
            transaction.setTransactionDate(parseDate(extractValue(tx.getTransactionDate())));

            if (tx.getTransactionCoding() != null) {
                transaction.setTransactionCode(tx.getTransactionCoding().getTransactionCode());
                transaction.setTransactionFormType(tx.getTransactionCoding().getTransactionFormType());
                transaction.setEquitySwapInvolved(tx.getTransactionCoding().isEquitySwapInvolved());
            }

            if (tx.getTransactionAmounts() != null) {
                transaction.setTransactionShares(parseFloat(extractValue(tx.getTransactionAmounts().getTransactionShares())));
                transaction.setTransactionPricePerShare(parseFloat(extractValue(tx.getTransactionAmounts().getTransactionPricePerShare())));
                transaction.setAcquiredDisposedCode(extractValue(tx.getTransactionAmounts().getTransactionAcquiredDisposedCode()));
            }

            if (tx.getPostTransactionAmounts() != null) {
                transaction.setSharesOwnedFollowingTransaction(
                    parseFloat(extractValue(tx.getPostTransactionAmounts().getSharesOwnedFollowingTransaction())));
            }

            if (tx.getOwnershipNature() != null) {
                transaction.setDirectOrIndirectOwnership(extractValue(tx.getOwnershipNature().getDirectOrIndirectOwnership()));
                transaction.setNatureOfOwnership(extractValue(tx.getOwnershipNature().getNatureOfOwnership()));
            }

            if (transaction.getTransactionShares() != null && transaction.getTransactionPricePerShare() != null) {
                transaction.setTransactionValue(
                    transaction.getTransactionShares() * transaction.getTransactionPricePerShare());
            }

            transactions.add(transaction);
        }

        return transactions;
    }

    private List<Form4Transaction> parseDerivativeTransactions(OwnershipDocument doc, String accessionNumber) {
        List<Form4Transaction> transactions = new ArrayList<>();

        if (doc.getDerivativeTable() == null || doc.getDerivativeTable().getTransactions() == null) {
            return transactions;
        }

        for (DerivativeTable.DerivativeTransaction tx : doc.getDerivativeTable().getTransactions()) {
            Form4Transaction transaction = new Form4Transaction();
            transaction.setAccessionNumber(accessionNumber);
            transaction.setTransactionType(TRANSACTION_TYPE_DERIVATIVE);

            transaction.setSecurityTitle(extractValue(tx.getSecurityTitle()));
            transaction.setTransactionDate(parseDate(extractValue(tx.getTransactionDate())));
            transaction.setExercisePrice(parseFloat(extractValue(tx.getConversionOrExercisePrice())));
            transaction.setExpirationDate(parseDate(extractValue(tx.getExpirationDate())));

            if (tx.getTransactionCoding() != null) {
                transaction.setTransactionCode(tx.getTransactionCoding().getTransactionCode());
                transaction.setTransactionFormType(tx.getTransactionCoding().getTransactionFormType());
                transaction.setEquitySwapInvolved(tx.getTransactionCoding().isEquitySwapInvolved());
            }

            if (tx.getTransactionAmounts() != null) {
                transaction.setTransactionShares(parseFloat(extractValue(tx.getTransactionAmounts().getTransactionShares())));
                transaction.setTransactionPricePerShare(parseFloat(extractValue(tx.getTransactionAmounts().getTransactionPricePerShare())));
                transaction.setAcquiredDisposedCode(extractValue(tx.getTransactionAmounts().getTransactionAcquiredDisposedCode()));
            }

            if (tx.getUnderlyingSecurity() != null) {
                transaction.setUnderlyingSecurityTitle(extractValue(tx.getUnderlyingSecurity().getUnderlyingSecurityTitle()));
                transaction.setUnderlyingSecurityShares(parseFloat(extractValue(tx.getUnderlyingSecurity().getUnderlyingSecurityShares())));
            }

            if (tx.getPostTransactionAmounts() != null) {
                transaction.setSharesOwnedFollowingTransaction(
                    parseFloat(extractValue(tx.getPostTransactionAmounts().getSharesOwnedFollowingTransaction())));
            }

            if (tx.getOwnershipNature() != null) {
                transaction.setDirectOrIndirectOwnership(extractValue(tx.getOwnershipNature().getDirectOrIndirectOwnership()));
                transaction.setNatureOfOwnership(extractValue(tx.getOwnershipNature().getNatureOfOwnership()));
            }

            transactions.add(transaction);
        }

        return transactions;
    }

    private String extractValue(ValueWithFootnote vwf) {
        if (vwf == null) return null;
        return vwf.getValue();
    }

    private Date parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        try {
            synchronized (DATE_FORMAT) {
                return DATE_FORMAT.parse(dateStr.trim());
            }
        } catch (ParseException e) {
            log.warn("Failed to parse date: {}", dateStr);
            return null;
        }
    }

    private Float parseFloat(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            String cleaned = value.replaceAll("[^0-9.\\-]", "");
            return Float.parseFloat(cleaned);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse float: {}", value);
            return null;
        }
    }
}
