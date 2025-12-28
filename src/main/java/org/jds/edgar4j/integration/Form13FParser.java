package org.jds.edgar4j.integration;

import java.io.StringReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.jds.edgar4j.integration.model.form13f.InfoTableEntry;
import org.jds.edgar4j.integration.model.form13f.InformationTable;
import org.jds.edgar4j.model.Form13F;
import org.jds.edgar4j.model.Form13FHolding;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import lombok.extern.slf4j.Slf4j;

/**
 * Parses SEC Form 13F XML documents into domain model objects.
 * Uses JAXB as primary parser with DOM fallback for malformed XML.
 * Handles the Information Table which contains all holdings data.
 */
@Slf4j
@Component
public class Form13FParser {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String INFO_TABLE_NS = "http://www.sec.gov/edgar/document/thirteenf/informationtable";

    private JAXBContext jaxbContext;
    private DocumentBuilderFactory documentBuilderFactory;

    public Form13FParser() {
        try {
            this.jaxbContext = JAXBContext.newInstance(InformationTable.class);
            this.documentBuilderFactory = DocumentBuilderFactory.newInstance();
            this.documentBuilderFactory.setNamespaceAware(true);
            // Security: disable external entities
            this.documentBuilderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        } catch (Exception e) {
            log.error("Failed to initialize Form13F parser", e);
            throw new RuntimeException("Failed to initialize Form13F parser", e);
        }
    }

    /**
     * Parses the Form 13F Information Table XML into a list of holdings.
     * This parses only the information table portion (the XML attachment).
     *
     * @param xml             Raw XML content of the information table
     * @param accessionNumber SEC accession number for the filing
     * @return Form13F with parsed holdings, or null if parsing fails
     */
    public Form13F parseInformationTable(String xml, String accessionNumber) {
        log.debug("Parsing Form 13F Information Table for accession: {}", accessionNumber);

        String cleanedXml = cleanXml(xml);
        if (cleanedXml == null || cleanedXml.isBlank()) {
            log.warn("Empty XML content after cleaning for accession: {}", accessionNumber);
            return null;
        }

        // Try JAXB parsing first
        InformationTable table = parseXmlWithJaxb(cleanedXml);

        List<Form13FHolding> holdings;
        if (table != null && table.getInfoTables() != null) {
            holdings = mapToHoldings(table);
        } else {
            // Fall back to DOM parsing if JAXB fails
            log.debug("JAXB parsing failed, trying DOM fallback for: {}", accessionNumber);
            holdings = parseWithDom(cleanedXml);
        }

        if (holdings == null || holdings.isEmpty()) {
            log.warn("No holdings parsed for accession: {}", accessionNumber);
            return null;
        }

        Form13F form13F = new Form13F();
        form13F.setAccessionNumber(accessionNumber);
        form13F.setHoldings(holdings);
        form13F.setHoldingsCount(holdings.size());
        form13F.setTotalValue(holdings.stream()
                .mapToLong(h -> h.getValue() != null ? h.getValue() : 0L)
                .sum());

        log.debug("Successfully parsed {} holdings for accession: {}", holdings.size(), accessionNumber);
        return form13F;
    }

    /**
     * Parses Form 13F with metadata from the primary document.
     * Call this after parseInformationTable to add filer info.
     */
    public void parseMetadata(Form13F form13F, String primaryDocXml) {
        if (form13F == null || primaryDocXml == null) {
            return;
        }

        try {
            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
            // Temporarily disable namespace awareness for primary doc
            documentBuilderFactory.setNamespaceAware(false);
            DocumentBuilder builder2 = documentBuilderFactory.newDocumentBuilder();
            Document doc = builder2.parse(new InputSource(new StringReader(cleanPrimaryDoc(primaryDocXml))));
            doc.getDocumentElement().normalize();

            Element root = doc.getDocumentElement();

            // Parse header info
            Element headerData = getFirstElement(root, "headerData");
            if (headerData != null) {
                Element filerInfo = getFirstElement(headerData, "filerInfo");
                if (filerInfo != null) {
                    form13F.setFormType(getElementText(filerInfo, "formType"));

                    Element filer = getFirstElement(filerInfo, "filer");
                    if (filer != null) {
                        Element credentials = getFirstElement(filer, "credentials");
                        if (credentials != null) {
                            form13F.setCik(getElementText(credentials, "cik"));
                        }
                    }
                }
            }

            // Parse form data
            Element formData = getFirstElement(root, "formData");
            if (formData != null) {
                Element coverPage = getFirstElement(formData, "coverPage");
                if (coverPage != null) {
                    form13F.setReportPeriod(parseDate(getElementText(coverPage, "reportCalendarOrQuarter")));
                    form13F.setFiledDate(parseDate(getElementText(coverPage, "filedDate")));

                    Element filingManager = getFirstElement(coverPage, "filingManager");
                    if (filingManager != null) {
                        form13F.setFilerName(getElementText(filingManager, "name"));

                        Element address = getFirstElement(filingManager, "address");
                        if (address != null) {
                            String street1 = getElementText(address, "street1");
                            String street2 = getElementText(address, "street2");
                            String city = getElementText(address, "city");
                            String stateOrCountry = getElementText(address, "stateOrCountry");
                            String zipCode = getElementText(address, "zipCode");

                            StringBuilder addr = new StringBuilder();
                            if (street1 != null) addr.append(street1);
                            if (street2 != null) addr.append(", ").append(street2);
                            if (city != null) addr.append(", ").append(city);
                            if (stateOrCountry != null) addr.append(", ").append(stateOrCountry);
                            if (zipCode != null) addr.append(" ").append(zipCode);

                            form13F.setBusinessAddress(addr.toString());
                        }
                    }

                    String reportType = getElementText(coverPage, "reportType");
                    form13F.setReportType(reportType);

                    String isAmendment = getElementText(coverPage, "isAmendment");
                    if ("true".equalsIgnoreCase(isAmendment) || "1".equals(isAmendment)) {
                        form13F.setAmendmentType(getElementText(coverPage, "amendmentType"));
                        String amendNo = getElementText(coverPage, "amendmentNo");
                        if (amendNo != null) {
                            try {
                                form13F.setAmendmentNumber(Integer.parseInt(amendNo));
                            } catch (NumberFormatException ignored) {}
                        }
                    }

                    String confTreatment = getElementText(coverPage, "isConfidentialOmitted");
                    form13F.setConfidentialTreatment("true".equalsIgnoreCase(confTreatment) || "1".equals(confTreatment));
                }

                // Parse signature block
                Element signatureBlock = getFirstElement(formData, "signatureBlock");
                if (signatureBlock != null) {
                    form13F.setSignatureName(getElementText(signatureBlock, "name"));
                    form13F.setSignatureTitle(getElementText(signatureBlock, "title"));
                    form13F.setSignaturePhone(getElementText(signatureBlock, "phone"));
                    form13F.setSignatureCity(getElementText(signatureBlock, "city"));
                    form13F.setSignatureState(getElementText(signatureBlock, "stateOrCountry"));
                    form13F.setSignatureDate(parseDate(getElementText(signatureBlock, "signatureDate")));
                }

                // Parse other managers
                Element summaryPage = getFirstElement(formData, "summaryPage");
                if (summaryPage != null) {
                    List<Form13F.OtherManager> otherManagers = parseOtherManagers(summaryPage);
                    if (!otherManagers.isEmpty()) {
                        form13F.setOtherManagers(otherManagers);
                    }
                }
            }

            // Restore namespace awareness
            documentBuilderFactory.setNamespaceAware(true);

        } catch (Exception e) {
            log.warn("Failed to parse metadata for accession: {}", form13F.getAccessionNumber(), e);
        }
    }

    private List<Form13F.OtherManager> parseOtherManagers(Element summaryPage) {
        List<Form13F.OtherManager> managers = new ArrayList<>();

        NodeList managerNodes = summaryPage.getElementsByTagName("otherIncludedManagersInfo");
        for (int i = 0; i < managerNodes.getLength(); i++) {
            Element managerEl = (Element) managerNodes.item(i);
            Form13F.OtherManager manager = Form13F.OtherManager.builder()
                    .sequenceNumber(parseInteger(getElementText(managerEl, "sequenceNumber")))
                    .cik(getElementText(managerEl, "cik"))
                    .name(getElementText(managerEl, "name"))
                    .form13FFileNumber(getElementText(managerEl, "form13FFileNumber"))
                    .build();
            managers.add(manager);
        }

        return managers;
    }

    /**
     * Cleans XML by extracting the informationTable content.
     */
    private String cleanXml(String xml) {
        if (xml == null) {
            return null;
        }

        // Try to find the informationTable element
        int startIdx = xml.indexOf("<informationTable");
        if (startIdx == -1) {
            // Try with namespace prefix
            startIdx = xml.indexOf("<ns1:informationTable");
            if (startIdx == -1) {
                startIdx = xml.indexOf("<?xml");
            }
        }

        if (startIdx == -1) {
            log.warn("No XML content found in document");
            return xml;
        }

        int endIdx = xml.lastIndexOf("</informationTable>");
        if (endIdx == -1) {
            endIdx = xml.lastIndexOf("</ns1:informationTable>");
        }

        if (endIdx == -1) {
            return xml.substring(startIdx);
        }

        String endTag = xml.contains("</ns1:informationTable>")
                ? "</ns1:informationTable>"
                : "</informationTable>";

        return xml.substring(startIdx, endIdx + endTag.length());
    }

    private String cleanPrimaryDoc(String xml) {
        if (xml == null) return null;

        int startIdx = xml.indexOf("<?xml");
        if (startIdx == -1) {
            startIdx = xml.indexOf("<edgarSubmission");
        }
        if (startIdx == -1) {
            return xml;
        }

        return xml.substring(startIdx);
    }

    /**
     * Parses cleaned XML into InformationTable using JAXB.
     */
    private InformationTable parseXmlWithJaxb(String xml) {
        try {
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            StringReader reader = new StringReader(xml);
            return (InformationTable) unmarshaller.unmarshal(reader);
        } catch (JAXBException e) {
            log.debug("JAXB parsing failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * DOM-based fallback parser for malformed XML.
     */
    private List<Form13FHolding> parseWithDom(String xml) {
        List<Form13FHolding> holdings = new ArrayList<>();

        try {
            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xml)));
            doc.getDocumentElement().normalize();

            // Try different element names for holdings
            NodeList infoTableNodes = doc.getElementsByTagNameNS(INFO_TABLE_NS, "infoTable");
            if (infoTableNodes.getLength() == 0) {
                infoTableNodes = doc.getElementsByTagName("infoTable");
            }
            if (infoTableNodes.getLength() == 0) {
                infoTableNodes = doc.getElementsByTagNameNS("*", "infoTable");
            }

            for (int i = 0; i < infoTableNodes.getLength(); i++) {
                Element entry = (Element) infoTableNodes.item(i);
                Form13FHolding holding = parseHoldingFromDom(entry);
                if (holding != null) {
                    holdings.add(holding);
                }
            }

            log.debug("DOM parsing succeeded, found {} holdings", holdings.size());

        } catch (Exception e) {
            log.error("DOM parsing failed", e);
        }

        return holdings;
    }

    private Form13FHolding parseHoldingFromDom(Element entry) {
        Form13FHolding holding = new Form13FHolding();

        holding.setNameOfIssuer(getElementTextNs(entry, "nameOfIssuer"));
        holding.setTitleOfClass(getElementTextNs(entry, "titleOfClass"));
        holding.setCusip(getElementTextNs(entry, "cusip"));
        holding.setFigi(getElementTextNs(entry, "figi"));
        holding.setValue(parseLong(getElementTextNs(entry, "value")));
        holding.setPutCall(getElementTextNs(entry, "putCall"));
        holding.setInvestmentDiscretion(getElementTextNs(entry, "investmentDiscretion"));
        holding.setOtherManager(getElementTextNs(entry, "otherManager"));

        // Parse shares or principal amount
        Element shrsOrPrnAmt = getFirstElementNs(entry, "shrsOrPrnAmt");
        if (shrsOrPrnAmt != null) {
            holding.setSharesOrPrincipalAmount(parseLong(getElementTextNs(shrsOrPrnAmt, "sshPrnamt")));
            holding.setSharesOrPrincipalAmountType(getElementTextNs(shrsOrPrnAmt, "sshPrnamtType"));
        }

        // Parse voting authority
        Element votingAuth = getFirstElementNs(entry, "votingAuthority");
        if (votingAuth != null) {
            holding.setVotingAuthoritySole(parseLong(getElementTextNs(votingAuth, "Sole")));
            holding.setVotingAuthorityShared(parseLong(getElementTextNs(votingAuth, "Shared")));
            holding.setVotingAuthorityNone(parseLong(getElementTextNs(votingAuth, "None")));
        }

        return holding;
    }

    /**
     * Maps parsed XML InformationTable to list of Form13FHolding.
     */
    private List<Form13FHolding> mapToHoldings(InformationTable table) {
        List<Form13FHolding> holdings = new ArrayList<>();

        for (InfoTableEntry entry : table.getInfoTables()) {
            Form13FHolding holding = new Form13FHolding();

            holding.setNameOfIssuer(entry.getNameOfIssuer());
            holding.setTitleOfClass(entry.getTitleOfClass());
            holding.setCusip(entry.getCusip());
            holding.setFigi(entry.getFigi());
            holding.setValue(entry.getValue());
            holding.setPutCall(entry.getPutCall());
            holding.setInvestmentDiscretion(entry.getInvestmentDiscretion());
            holding.setOtherManager(entry.getOtherManager());

            if (entry.getShrsOrPrnAmt() != null) {
                holding.setSharesOrPrincipalAmount(entry.getShrsOrPrnAmt().getSshPrnamt());
                holding.setSharesOrPrincipalAmountType(entry.getShrsOrPrnAmt().getSshPrnamtType());
            }

            if (entry.getVotingAuthority() != null) {
                holding.setVotingAuthoritySole(entry.getVotingAuthority().getSole());
                holding.setVotingAuthorityShared(entry.getVotingAuthority().getShared());
                holding.setVotingAuthorityNone(entry.getVotingAuthority().getNone());
            }

            holdings.add(holding);
        }

        return holdings;
    }

    // Helper methods for DOM parsing
    private Element getFirstElement(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        return nodes.getLength() > 0 ? (Element) nodes.item(0) : null;
    }

    private Element getFirstElementNs(Element parent, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS(INFO_TABLE_NS, localName);
        if (nodes.getLength() == 0) {
            nodes = parent.getElementsByTagName(localName);
        }
        if (nodes.getLength() == 0) {
            nodes = parent.getElementsByTagNameNS("*", localName);
        }
        return nodes.getLength() > 0 ? (Element) nodes.item(0) : null;
    }

    private String getElementText(Element parent, String tagName) {
        Element element = getFirstElement(parent, tagName);
        return element != null ? element.getTextContent().trim() : null;
    }

    private String getElementTextNs(Element parent, String localName) {
        Element element = getFirstElementNs(parent, localName);
        return element != null ? element.getTextContent().trim() : null;
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        try {
            // Handle different date formats
            String cleaned = dateStr.trim();
            if (cleaned.length() == 10) {
                return LocalDate.parse(cleaned, DATE_FORMATTER);
            } else if (cleaned.length() == 8) {
                // Format: MMDDYYYY
                return LocalDate.of(
                    Integer.parseInt(cleaned.substring(4, 8)),
                    Integer.parseInt(cleaned.substring(0, 2)),
                    Integer.parseInt(cleaned.substring(2, 4))
                );
            }
            return LocalDate.parse(cleaned, DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            log.warn("Failed to parse date: {}", dateStr);
            return null;
        }
    }

    private Long parseLong(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            String cleaned = value.replaceAll("[^0-9\\-]", "");
            return Long.parseLong(cleaned);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse long: {}", value);
            return null;
        }
    }

    private Integer parseInteger(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
