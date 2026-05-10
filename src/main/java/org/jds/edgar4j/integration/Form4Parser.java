package org.jds.edgar4j.integration;

import java.io.StringReader;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
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

    private static final String TRANSACTION_TYPE_NON_DERIVATIVE = "NON_DERIVATIVE";
    private static final String TRANSACTION_TYPE_DERIVATIVE = "DERIVATIVE";

    private final JAXBContext jaxbContext;
    private final DocumentBuilderFactory documentBuilderFactory;

    public Form4Parser() {
        try {
            this.jaxbContext = JAXBContext.newInstance(OwnershipDocument.class);
            this.documentBuilderFactory = OwnershipDomParserSupport.buildDocumentBuilderFactory();
        } catch (Exception e) {
            log.error("Failed to initialize Form 4 parser", e);
            throw new RuntimeException("Failed to initialize Form 4 parser", e);
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
     * Cleans XML by removing problematic content (HTML wrappers, DOCTYPE declarations, etc.).
     * Always starts from the root <ownershipDocument> element to skip <?xml?> and <!DOCTYPE>
     * declarations which cause "disallow-doctype-decl" security errors in the XML parser.
     */
    private String cleanXml(String xml) {
        String cleanedXml = OwnershipDomParserSupport.cleanXml(xml);
        if ((cleanedXml == null || cleanedXml.isBlank()) && xml != null && !xml.isBlank()) {
            log.warn("No XML content found in document");
        }
        return cleanedXml;
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

            form4.setDocumentType(OwnershipDomParserSupport.getElementText(root, "documentType"));
            form4.setPeriodOfReport(parseDate(OwnershipDomParserSupport.getElementText(root, "periodOfReport")));

            Element issuer = OwnershipDomParserSupport.getFirstElement(root, "issuer");
            if (issuer != null) {
                form4.setCik(OwnershipDomParserSupport.getElementText(issuer, "issuerCik"));
                form4.setIssuerName(OwnershipDomParserSupport.getElementText(issuer, "issuerName"));
                form4.setTradingSymbol(OwnershipDomParserSupport.getElementText(issuer, "issuerTradingSymbol"));
            }

            Element reportingOwner = OwnershipDomParserSupport.getFirstElement(root, "reportingOwner");
            if (reportingOwner != null) {
                Element ownerId = OwnershipDomParserSupport.getFirstElement(reportingOwner, "reportingOwnerId");
                if (ownerId != null) {
                    form4.setRptOwnerCik(OwnershipDomParserSupport.getElementText(ownerId, "rptOwnerCik"));
                    form4.setRptOwnerName(OwnershipDomParserSupport.getElementText(ownerId, "rptOwnerName"));
                }

                Element relationship = OwnershipDomParserSupport.getFirstElement(reportingOwner, "reportingOwnerRelationship");
                if (relationship != null) {
                    form4.setDirector(OwnershipDomParserSupport.isTrue(OwnershipDomParserSupport.getElementText(relationship, "isDirector")));
                    form4.setOfficer(OwnershipDomParserSupport.isTrue(OwnershipDomParserSupport.getElementText(relationship, "isOfficer")));
                    form4.setTenPercentOwner(OwnershipDomParserSupport.isTrue(OwnershipDomParserSupport.getElementText(relationship, "isTenPercentOwner")));
                    form4.setOther(OwnershipDomParserSupport.isTrue(OwnershipDomParserSupport.getElementText(relationship, "isOther")));
                    form4.setOfficerTitle(OwnershipDomParserSupport.getElementText(relationship, "officerTitle"));
                    form4.setOwnerType(deriveOwnerTypeFromFlags(
                            form4.isDirector(), form4.isOfficer(),
                            form4.isTenPercentOwner(), form4.isOther()));
                }
            }

            List<Form4Transaction> transactions = new ArrayList<>();
            transactions.addAll(OwnershipDomParserSupport.parseNonDerivativeEntries(root, accessionNumber, "nonDerivativeTransaction"));
            transactions.addAll(OwnershipDomParserSupport.parseDerivativeEntries(root, accessionNumber, "derivativeTransaction"));
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

    private LocalDate parseDate(String dateStr) {
        LocalDate parsed = ParserDateUtils.parseDate(dateStr);
        if (parsed == null && dateStr != null && !dateStr.trim().isEmpty()) {
            log.warn("Failed to parse date: {}", dateStr);
        }
        return parsed;
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
