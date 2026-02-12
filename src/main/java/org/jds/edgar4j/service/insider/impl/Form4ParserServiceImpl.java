package org.jds.edgar4j.service.insider.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jds.edgar4j.model.insider.*;
import org.jds.edgar4j.service.insider.CompanyService;
import org.jds.edgar4j.service.insider.Form4ParserService;
import org.jds.edgar4j.service.insider.InsiderService;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Complete implementation of Form4ParserService for parsing SEC Form 4 XML documents
 * 
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Form4ParserServiceImpl implements Form4ParserService {

    private final CompanyService companyService;
    private final InsiderService insiderService;
    
    private static final DateTimeFormatter[] DATE_FORMATTERS = {
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        DateTimeFormatter.ofPattern("MM-dd-yyyy"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd")
    };

    @Override
    public List<InsiderTransaction> parseForm4Xml(String xmlContent, String accessionNumber) {
        log.info("Parsing Form 4 XML for accession number: {}", accessionNumber);
        
        try {
            if (!validateForm4Xml(xmlContent)) {
                log.warn("Invalid Form 4 XML structure for: {}", accessionNumber);
                return List.of();
            }
            
            Document document = parseXmlDocument(xmlContent);
            
            IssuerInfo issuerInfo = extractIssuerInfo(xmlContent);
            ReportingOwnerInfo ownerInfo = extractReportingOwnerInfo(xmlContent);
            
            if (issuerInfo.getCik() == null || ownerInfo.getCik() == null) {
                log.warn("Missing required CIK information in Form 4: {}", accessionNumber);
                return List.of();
            }
            
            Company company = getOrCreateCompany(issuerInfo);
            Insider insider = getOrCreateInsider(ownerInfo);
            createOrUpdateRelationship(insider, company, ownerInfo);
            
            List<InsiderTransaction> transactions = new ArrayList<>();
            
            List<NonDerivativeTransaction> nonDerivatives = extractNonDerivativeTransactions(xmlContent);
            for (NonDerivativeTransaction nonDeriv : nonDerivatives) {
                InsiderTransaction transaction = convertNonDerivativeTransaction(
                    nonDeriv, company, insider, accessionNumber, document);
                if (transaction != null) {
                    transactions.add(transaction);
                }
            }
            
            List<DerivativeTransaction> derivatives = extractDerivativeTransactions(xmlContent);
            for (DerivativeTransaction deriv : derivatives) {
                InsiderTransaction transaction = convertDerivativeTransaction(
                    deriv, company, insider, accessionNumber, document);
                if (transaction != null) {
                    transactions.add(transaction);
                }
            }
            
            log.info("Successfully parsed {} transactions from Form 4: {}", transactions.size(), accessionNumber);
            return transactions;
            
        } catch (Exception e) {
            log.error("Error parsing Form 4 XML for {}: {}", accessionNumber, e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public IssuerInfo extractIssuerInfo(String xmlContent) {
        try {
            Document document = parseXmlDocument(xmlContent);
            Element issuerElement = getFirstElementByTagName(document, "issuer");
            
            if (issuerElement == null) {
                return new IssuerInfo();
            }
            
            String cik = getElementTextContent(issuerElement, "issuerCik");
            String name = getElementTextContent(issuerElement, "issuerName");
            String tradingSymbol = getElementTextContent(issuerElement, "issuerTradingSymbol");
            
            return new IssuerInfo(formatCik(cik), cleanText(name), cleanText(tradingSymbol));
            
        } catch (Exception e) {
            log.error("Error extracting issuer info: {}", e.getMessage(), e);
            return new IssuerInfo();
        }
    }

    @Override
    public ReportingOwnerInfo extractReportingOwnerInfo(String xmlContent) {
        try {
            Document document = parseXmlDocument(xmlContent);
            Element reportingOwnerElement = getFirstElementByTagName(document, "reportingOwner");
            
            if (reportingOwnerElement == null) {
                return new ReportingOwnerInfo();
            }
            
            ReportingOwnerInfo ownerInfo = new ReportingOwnerInfo();
            
            Element ownerIdElement = getFirstElementByTagName(reportingOwnerElement, "reportingOwnerId");
            if (ownerIdElement != null) {
                ownerInfo.setCik(formatCik(getElementTextContent(ownerIdElement, "rptOwnerCik")));
                ownerInfo.setName(cleanText(getElementTextContent(ownerIdElement, "rptOwnerName")));
            }
            
            Element addressElement = getFirstElementByTagName(reportingOwnerElement, "reportingOwnerAddress");
            if (addressElement != null) {
                ownerInfo.setAddress(buildAddress(addressElement));
            }
            
            Element relationshipElement = getFirstElementByTagName(reportingOwnerElement, "reportingOwnerRelationship");
            if (relationshipElement != null) {
                ownerInfo.setDirector("1".equals(getElementTextContent(relationshipElement, "isDirector")));
                ownerInfo.setOfficer("1".equals(getElementTextContent(relationshipElement, "isOfficer")));
                ownerInfo.setTenPercentOwner("1".equals(getElementTextContent(relationshipElement, "isTenPercentOwner")));
                ownerInfo.setOther("1".equals(getElementTextContent(relationshipElement, "isOther")));
                ownerInfo.setOfficerTitle(cleanText(getElementTextContent(relationshipElement, "officerTitle")));
                ownerInfo.setOtherText(cleanText(getElementTextContent(relationshipElement, "otherText")));
            }
            
            return ownerInfo;
            
        } catch (Exception e) {
            log.error("Error extracting reporting owner info: {}", e.getMessage(), e);
            return new ReportingOwnerInfo();
        }
    }

    @Override
    public List<NonDerivativeTransaction> extractNonDerivativeTransactions(String xmlContent) {
        try {
            Document document = parseXmlDocument(xmlContent);
            NodeList transactionElements = document.getElementsByTagName("nonDerivativeTransaction");
            
            List<NonDerivativeTransaction> transactions = new ArrayList<>();
            
            for (int i = 0; i < transactionElements.getLength(); i++) {
                Element transactionElement = (Element) transactionElements.item(i);
                NonDerivativeTransaction transaction = parseNonDerivativeTransaction(transactionElement);
                if (transaction != null) {
                    transactions.add(transaction);
                }
            }
            
            return transactions;
            
        } catch (Exception e) {
            log.error("Error extracting non-derivative transactions: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public List<DerivativeTransaction> extractDerivativeTransactions(String xmlContent) {
        try {
            Document document = parseXmlDocument(xmlContent);
            NodeList transactionElements = document.getElementsByTagName("derivativeTransaction");
            
            List<DerivativeTransaction> transactions = new ArrayList<>();
            
            for (int i = 0; i < transactionElements.getLength(); i++) {
                Element transactionElement = (Element) transactionElements.item(i);
                DerivativeTransaction transaction = parseDerivativeTransaction(transactionElement);
                if (transaction != null) {
                    transactions.add(transaction);
                }
            }
            
            return transactions;
            
        } catch (Exception e) {
            log.error("Error extracting derivative transactions: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public boolean validateForm4Xml(String xmlContent) {
        try {
            if (xmlContent == null || xmlContent.trim().isEmpty()) {
                return false;
            }
            
            Document document = parseXmlDocument(xmlContent);
            
            boolean hasOwnershipDocument = document.getElementsByTagName("ownershipDocument").getLength() > 0;
            boolean hasForm4 = document.getElementsByTagName("form4").getLength() > 0;
            boolean hasIssuer = document.getElementsByTagName("issuer").getLength() > 0;
            boolean hasReportingOwner = document.getElementsByTagName("reportingOwner").getLength() > 0;
            
            return (hasOwnershipDocument || hasForm4) && hasIssuer && hasReportingOwner;
            
        } catch (Exception e) {
            log.warn("Form 4 XML validation failed: {}", e.getMessage());
            return false;
        }
    }

    private Document parseXmlDocument(String xmlContent) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setIgnoringElementContentWhitespace(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        
        DocumentBuilder builder = factory.newDocumentBuilder();
        ByteArrayInputStream input = new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8));
        
        return builder.parse(input);
    }

    private Element getFirstElementByTagName(Document document, String tagName) {
        NodeList nodeList = document.getElementsByTagName(tagName);
        return nodeList.getLength() > 0 ? (Element) nodeList.item(0) : null;
    }

    private Element getFirstElementByTagName(Element parent, String tagName) {
        NodeList nodeList = parent.getElementsByTagName(tagName);
        return nodeList.getLength() > 0 ? (Element) nodeList.item(0) : null;
    }

    private String getElementTextContent(Element parent, String childTagName) {
        Element child = getFirstElementByTagName(parent, childTagName);
        return child != null ? child.getTextContent().trim() : null;
    }

    private NonDerivativeTransaction parseNonDerivativeTransaction(Element element) {
        try {
            NonDerivativeTransaction transaction = new NonDerivativeTransaction();
            
            Element securityTitleElement = getFirstElementByTagName(element, "securityTitle");
            if (securityTitleElement != null) {
                transaction.setSecurityTitle(getElementTextContent(securityTitleElement, "value"));
            }
            
            Element transactionElement = getFirstElementByTagName(element, "transactionAmounts");
            if (transactionElement != null) {
                transaction.setTransactionDate(getElementTextContent(transactionElement, "transactionDate"));
                transaction.setTransactionCode(getElementTextContent(transactionElement, "transactionCode"));
                transaction.setSharesTransacted(getElementTextContent(transactionElement, "transactionShares"));
                transaction.setPricePerShare(getElementTextContent(transactionElement, "transactionPricePerShare"));
                transaction.setAcquiredDisposed(getElementTextContent(transactionElement, "transactionAcquiredDisposedCode"));
            }
            
            Element postTransactionElement = getFirstElementByTagName(element, "postTransactionAmounts");
            if (postTransactionElement != null) {
                transaction.setSharesOwnedAfter(getElementTextContent(postTransactionElement, "sharesOwnedFollowingTransaction"));
            }
            
            Element ownershipElement = getFirstElementByTagName(element, "ownershipNature");
            if (ownershipElement != null) {
                transaction.setOwnershipNature(getElementTextContent(ownershipElement, "directOrIndirectOwnership"));
            }
            
            return transaction;
            
        } catch (Exception e) {
            log.warn("Error parsing non-derivative transaction: {}", e.getMessage());
            return null;
        }
    }

    private DerivativeTransaction parseDerivativeTransaction(Element element) {
        try {
            DerivativeTransaction transaction = new DerivativeTransaction();
            
            Element securityTitleElement = getFirstElementByTagName(element, "securityTitle");
            if (securityTitleElement != null) {
                transaction.setSecurityTitle(getElementTextContent(securityTitleElement, "value"));
            }
            
            Element transactionElement = getFirstElementByTagName(element, "transactionAmounts");
            if (transactionElement != null) {
                transaction.setTransactionDate(getElementTextContent(transactionElement, "transactionDate"));
                transaction.setTransactionCode(getElementTextContent(transactionElement, "transactionCode"));
                transaction.setSharesTransacted(getElementTextContent(transactionElement, "transactionShares"));
                transaction.setPricePerShare(getElementTextContent(transactionElement, "transactionPricePerShare"));
                transaction.setAcquiredDisposed(getElementTextContent(transactionElement, "transactionAcquiredDisposedCode"));
            }
            
            Element exerciseElement = getFirstElementByTagName(element, "exerciseDate");
            if (exerciseElement != null) {
                transaction.setExerciseDate(getElementTextContent(exerciseElement, "value"));
            }
            
            Element expirationElement = getFirstElementByTagName(element, "expirationDate");
            if (expirationElement != null) {
                transaction.setExpirationDate(getElementTextContent(expirationElement, "value"));
            }
            
            Element underlyingElement = getFirstElementByTagName(element, "underlyingSecurity");
            if (underlyingElement != null) {
                transaction.setUnderlyingSecurityTitle(getElementTextContent(underlyingElement, "underlyingSecurityTitle"));
                transaction.setUnderlyingShares(getElementTextContent(underlyingElement, "underlyingSecurityShares"));
            }
            
            Element postTransactionElement = getFirstElementByTagName(element, "postTransactionAmounts");
            if (postTransactionElement != null) {
                transaction.setSharesOwnedAfter(getElementTextContent(postTransactionElement, "sharesOwnedFollowingTransaction"));
            }
            
            Element ownershipElement = getFirstElementByTagName(element, "ownershipNature");
            if (ownershipElement != null) {
                transaction.setOwnershipNature(getElementTextContent(ownershipElement, "directOrIndirectOwnership"));
            }
            
            return transaction;
            
        } catch (Exception e) {
            log.warn("Error parsing derivative transaction: {}", e.getMessage());
            return null;
        }
    }

    private Company getOrCreateCompany(IssuerInfo issuerInfo) {
        return companyService.getOrCreateCompany(
            issuerInfo.getCik(), 
            issuerInfo.getName(), 
            issuerInfo.getTradingSymbol()
        );
    }

    private Insider getOrCreateInsider(ReportingOwnerInfo ownerInfo) {
        return insiderService.getOrCreateInsider(
            ownerInfo.getCik(), 
            ownerInfo.getName(), 
            ownerInfo.getAddress()
        );
    }

    private void createOrUpdateRelationship(Insider insider, Company company, ReportingOwnerInfo ownerInfo) {
        InsiderCompanyRelationship relationship = InsiderCompanyRelationship.builder()
            .insider(insider)
            .company(company)
            .isDirector(ownerInfo.isDirector())
            .isOfficer(ownerInfo.isOfficer())
            .isTenPercentOwner(ownerInfo.isTenPercentOwner())
            .isOther(ownerInfo.isOther())
            .officerTitle(ownerInfo.getOfficerTitle())
            .otherText(ownerInfo.getOtherText())
            .isActive(true)
            .build();
    }

    private InsiderTransaction convertNonDerivativeTransaction(NonDerivativeTransaction nonDeriv, 
                                                             Company company, Insider insider, 
                                                             String accessionNumber, Document document) {
        try {
            LocalDate transactionDate = parseDate(nonDeriv.getTransactionDate());
            LocalDate filingDate = extractFilingDate(document);
            
            if (transactionDate == null || filingDate == null) {
                log.warn("Missing required dates in transaction: {}", accessionNumber);
                return null;
            }
            
            return InsiderTransaction.builder()
                .company(company)
                .insider(insider)
                .accessionNumber(accessionNumber)
                .transactionDate(transactionDate)
                .filingDate(filingDate)
                .securityTitle(cleanText(nonDeriv.getSecurityTitle()))
                .transactionCode(cleanText(nonDeriv.getTransactionCode()))
                .sharesTransacted(parseBigDecimal(nonDeriv.getSharesTransacted()))
                .pricePerShare(parseBigDecimal(nonDeriv.getPricePerShare()))
                .sharesOwnedAfter(parseBigDecimal(nonDeriv.getSharesOwnedAfter()))
                .acquiredDisposed(parseAcquiredDisposed(nonDeriv.getAcquiredDisposed()))
                .ownershipNature(parseOwnershipNature(nonDeriv.getOwnershipNature()))
                .isDerivative(false)
                .build();
                
        } catch (Exception e) {
            log.warn("Error converting non-derivative transaction: {}", e.getMessage());
            return null;
        }
    }

    private InsiderTransaction convertDerivativeTransaction(DerivativeTransaction deriv, 
                                                          Company company, Insider insider, 
                                                          String accessionNumber, Document document) {
        try {
            LocalDate transactionDate = parseDate(deriv.getTransactionDate());
            LocalDate filingDate = extractFilingDate(document);
            LocalDate exerciseDate = parseDate(deriv.getExerciseDate());
            LocalDate expirationDate = parseDate(deriv.getExpirationDate());
            
            if (transactionDate == null || filingDate == null) {
                log.warn("Missing required dates in derivative transaction: {}", accessionNumber);
                return null;
            }
            
            return InsiderTransaction.builder()
                .company(company)
                .insider(insider)
                .accessionNumber(accessionNumber)
                .transactionDate(transactionDate)
                .filingDate(filingDate)
                .securityTitle(cleanText(deriv.getSecurityTitle()))
                .transactionCode(cleanText(deriv.getTransactionCode()))
                .sharesTransacted(parseBigDecimal(deriv.getSharesTransacted()))
                .pricePerShare(parseBigDecimal(deriv.getPricePerShare()))
                .sharesOwnedAfter(parseBigDecimal(deriv.getSharesOwnedAfter()))
                .acquiredDisposed(parseAcquiredDisposed(deriv.getAcquiredDisposed()))
                .ownershipNature(parseOwnershipNature(deriv.getOwnershipNature()))
                .isDerivative(true)
                .exerciseDate(exerciseDate)
                .expirationDate(expirationDate)
                .underlyingSecurityTitle(cleanText(deriv.getUnderlyingSecurityTitle()))
                .underlyingShares(parseBigDecimal(deriv.getUnderlyingShares()))
                .build();
                
        } catch (Exception e) {
            log.warn("Error converting derivative transaction: {}", e.getMessage());
            return null;
        }
    }

    private String buildAddress(Element addressElement) {
        StringBuilder address = new StringBuilder();
        
        String street1 = getElementTextContent(addressElement, "rptOwnerStreet1");
        String street2 = getElementTextContent(addressElement, "rptOwnerStreet2");
        String city = getElementTextContent(addressElement, "rptOwnerCity");
        String state = getElementTextContent(addressElement, "rptOwnerState");
        String zipCode = getElementTextContent(addressElement, "rptOwnerZipCode");
        
        if (street1 != null && !street1.isEmpty()) {
            address.append(street1);
        }
        if (street2 != null && !street2.isEmpty()) {
            address.append("\n").append(street2);
        }
        if (city != null && !city.isEmpty()) {
            address.append("\n").append(city);
            if (state != null && !state.isEmpty()) {
                address.append(", ").append(state);
            }
            if (zipCode != null && !zipCode.isEmpty()) {
                address.append(" ").append(zipCode);
            }
        }
        
        return address.toString();
    }

    private LocalDate extractFilingDate(Document document) {
        Element documentElement = getFirstElementByTagName(document, "ownershipDocument");
        if (documentElement != null) {
            Element periodElement = getFirstElementByTagName(documentElement, "periodOfReport");
            if (periodElement != null) {
                return parseDate(periodElement.getTextContent());
            }
        }
        return LocalDate.now();
    }

    private LocalDate parseDate(String dateString) {
        if (dateString == null || dateString.trim().isEmpty()) {
            return null;
        }
        
        String cleanDate = dateString.trim();
        
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(cleanDate, formatter);
            } catch (DateTimeParseException e) {
                // Try next formatter
            }
        }
        
        log.warn("Unable to parse date: {}", dateString);
        return null;
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        
        try {
            String cleanValue = value.trim().replace(",", "");
            return new BigDecimal(cleanValue);
        } catch (NumberFormatException e) {
            log.warn("Unable to parse decimal: {}", value);
            return null;
        }
    }

    private InsiderTransaction.AcquiredDisposed parseAcquiredDisposed(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        
        try {
            return InsiderTransaction.AcquiredDisposed.fromCode(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown acquired/disposed code: {}", code);
            return null;
        }
    }

    private InsiderTransaction.OwnershipNature parseOwnershipNature(String code) {
        if (code == null || code.trim().isEmpty()) {
            return InsiderTransaction.OwnershipNature.DIRECT;
        }
        
        try {
            return InsiderTransaction.OwnershipNature.fromCode(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown ownership nature code: {}", code);
            return InsiderTransaction.OwnershipNature.DIRECT;
        }
    }

    private String formatCik(String cik) {
        if (cik == null || cik.trim().isEmpty()) {
            return null;
        }
        
        try {
            long cikLong = Long.parseLong(cik.trim());
            return String.format("%010d", cikLong);
        } catch (NumberFormatException e) {
            log.warn("Invalid CIK format: {}", cik);
            return cik.trim();
        }
    }

    private String cleanText(String text) {
        if (text == null) {
            return null;
        }
        
        String cleaned = text.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
