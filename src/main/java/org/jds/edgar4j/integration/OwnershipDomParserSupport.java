package org.jds.edgar4j.integration;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import javax.xml.parsers.DocumentBuilderFactory;

import org.jds.edgar4j.model.Form4Transaction;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

final class OwnershipDomParserSupport {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String TRANSACTION_TYPE_NON_DERIVATIVE = "NON_DERIVATIVE";
    private static final String TRANSACTION_TYPE_DERIVATIVE = "DERIVATIVE";

    private OwnershipDomParserSupport() {}

    static DocumentBuilderFactory buildDocumentBuilderFactory() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return factory;
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize XML parser", e);
        }
    }

    static String cleanXml(String xml) {
        if (xml == null) {
            return null;
        }

        int startIdx = xml.indexOf("<?xml");
        if (startIdx == -1) {
            startIdx = xml.indexOf("<ownershipDocument");
        }

        if (startIdx == -1) {
            return xml;
        }

        int endIdx = xml.lastIndexOf("</ownershipDocument>");
        if (endIdx == -1) {
            return xml.substring(startIdx);
        }

        return xml.substring(startIdx, endIdx + "</ownershipDocument>".length());
    }

    static List<Form4Transaction> parseNonDerivativeEntries(Element root, String accessionNumber, String elementTag) {
        List<Form4Transaction> entries = new ArrayList<>();

        Element table = getFirstElement(root, "nonDerivativeTable");
        if (table == null) return entries;

        NodeList nodes = table.getElementsByTagName(elementTag);
        for (int i = 0; i < nodes.getLength(); i++) {
            Element tx = (Element) nodes.item(i);
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

            entries.add(transaction);
        }

        return entries;
    }

    static List<Form4Transaction> parseDerivativeEntries(Element root, String accessionNumber, String elementTag) {
        List<Form4Transaction> entries = new ArrayList<>();

        Element table = getFirstElement(root, "derivativeTable");
        if (table == null) return entries;

        NodeList nodes = table.getElementsByTagName(elementTag);
        for (int i = 0; i < nodes.getLength(); i++) {
            Element tx = (Element) nodes.item(i);
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
                transaction.setEquitySwapInvolved(isTrue(getElementText(coding, "equitySwapInvolved")));
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

            entries.add(transaction);
        }

        return entries;
    }

    static Element getFirstElement(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        return nodes.getLength() > 0 ? (Element) nodes.item(0) : null;
    }

    static String getElementText(Element parent, String tagName) {
        Element element = getFirstElement(parent, tagName);
        return element != null ? element.getTextContent().trim() : null;
    }

    static String getNestedValue(Element parent, String tagName) {
        Element element = getFirstElement(parent, tagName);
        if (element == null) return null;

        Element valueElement = getFirstElement(element, "value");
        if (valueElement != null) {
            return valueElement.getTextContent().trim();
        }
        return element.getTextContent().trim();
    }

    static boolean isTrue(String value) {
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }

    static LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr.trim(), DATE_FORMAT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    static Float parseFloat(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            String cleaned = value.replaceAll("[^0-9.\\-]", "");
            return Float.parseFloat(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
