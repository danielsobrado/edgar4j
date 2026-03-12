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

import org.jds.edgar4j.integration.model.form13dg.Schedule13Document;
import org.jds.edgar4j.model.Form13DG;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import lombok.extern.slf4j.Slf4j;

/**
 * Parses SEC Schedule 13D/13G XML documents into domain model objects.
 * Uses JAXB as primary parser with DOM fallback for malformed/legacy XML.
 *
 * Supports both new XML format (December 2024+) and legacy HTML-based filings.
 */
@Slf4j
@Component
public class Form13DGParser {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_FORMATTER_ALT = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    private JAXBContext jaxbContext;
    private DocumentBuilderFactory documentBuilderFactory;

    public Form13DGParser() {
        try {
            this.jaxbContext = JAXBContext.newInstance(Schedule13Document.class);
            this.documentBuilderFactory = DocumentBuilderFactory.newInstance();
            this.documentBuilderFactory.setNamespaceAware(false);
            // Security: disable external entities
            this.documentBuilderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        } catch (Exception e) {
            log.error("Failed to initialize Form13DG parser", e);
            throw new RuntimeException("Failed to initialize Form13DG parser", e);
        }
    }

    /**
     * Parses raw XML string into Form13DG domain object.
     *
     * @param xml             Raw XML content of the 13D/13G filing
     * @param accessionNumber SEC accession number for the filing
     * @return Form13DG with parsed data, or null if parsing fails
     */
    public Form13DG parse(String xml, String accessionNumber) {
        log.debug("Parsing Schedule 13D/G XML for accession: {}", accessionNumber);

        String cleanedXml = cleanXml(xml);
        if (cleanedXml == null || cleanedXml.isBlank()) {
            log.warn("Empty XML content after cleaning for accession: {}", accessionNumber);
            return null;
        }

        // Try JAXB parsing first for new XML format
        Schedule13Document doc = parseXmlWithJaxb(cleanedXml);

        if (doc != null) {
            return mapToForm13DG(doc, accessionNumber);
        }

        // Fall back to DOM parsing for legacy or malformed XML
        log.debug("JAXB parsing failed, trying DOM fallback for: {}", accessionNumber);
        return parseWithDom(cleanedXml, accessionNumber);
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
            startIdx = xml.indexOf("<edgarSubmission");
        }
        if (startIdx == -1) {
            startIdx = xml.indexOf("<SEC-DOCUMENT");
        }

        if (startIdx == -1) {
            log.warn("No XML content found in document");
            return xml;
        }

        return xml.substring(startIdx);
    }

    /**
     * Parses cleaned XML into Schedule13Document using JAXB.
     */
    private Schedule13Document parseXmlWithJaxb(String xml) {
        try {
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            StringReader reader = new StringReader(xml);
            return (Schedule13Document) unmarshaller.unmarshal(reader);
        } catch (JAXBException e) {
            log.debug("JAXB parsing failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Maps parsed XML document to Form13DG domain model.
     */
    private Form13DG mapToForm13DG(Schedule13Document doc, String accessionNumber) {
        Form13DG form = Form13DG.builder()
                .accessionNumber(accessionNumber)
                .build();

        // Parse header data
        if (doc.getHeaderData() != null) {
            String submissionType = doc.getHeaderData().getSubmissionType();
            form.setFormType(submissionType);
            form.setScheduleType(determineScheduleType(submissionType));

            if (doc.getHeaderData().getFilerInfo() != null &&
                doc.getHeaderData().getFilerInfo().getFiler() != null &&
                doc.getHeaderData().getFilerInfo().getFiler().getCredentials() != null) {
                form.setFilingPersonCik(doc.getHeaderData().getFilerInfo().getFiler().getCredentials().getCik());
            }
        }

        // Parse form data
        if (doc.getFormData() != null) {
            parseCoverPage(form, doc.getFormData().getCoverPage());
            parseReportingPersons(form, doc.getFormData().getReportingPersons());
            parseSignature(form, doc.getFormData().getSignatureBlock());
        }

        log.debug("Successfully parsed Schedule 13D/G for accession: {}", accessionNumber);
        return form;
    }

    private void parseCoverPage(Form13DG form, Schedule13Document.CoverPage coverPage) {
        if (coverPage == null) return;

        form.setIssuerName(coverPage.getIssuerName());
        form.setIssuerCik(coverPage.getIssuerCik());
        form.setCusip(normalizeCusip(coverPage.getCusipNumber()));
        form.setSecurityTitle(coverPage.getSecurityTitle());
        form.setEventDate(parseDate(coverPage.getDateOfEvent()));
        form.setFiledDate(parseDate(coverPage.getFiledDate()));

        // Handle amendment
        if ("true".equalsIgnoreCase(coverPage.getIsAmendment()) || "1".equals(coverPage.getIsAmendment())) {
            form.setAmendmentType("AMENDMENT");
            form.setAmendmentNumber(parseInteger(coverPage.getAmendmentNo()));
        } else {
            form.setAmendmentType("INITIAL");
        }
    }

    private void parseReportingPersons(Form13DG form, List<Schedule13Document.ReportingPersonInfo> reportingPersons) {
        if (reportingPersons == null || reportingPersons.isEmpty()) return;

        // First reporting person is the primary filer
        Schedule13Document.ReportingPersonInfo primary = reportingPersons.get(0);
        form.setFilingPersonName(primary.getNameOfReportingPerson());

        if (primary.getReportingPersonCik() != null) {
            form.setFilingPersonCik(primary.getReportingPersonCik());
        }

        if (primary.getIdentificationOrResidence() != null) {
            form.setCitizenshipOrOrganization(primary.getIdentificationOrResidence().getCitizenshipOrPlaceOfOrganization());
        }

        if (primary.getAddress() != null) {
            form.setFilingPersonAddress(mapAddress(primary.getAddress()));
        }

        if (primary.getTypeOfReportingPerson() != null) {
            form.setReportingPersonTypes(parseReportingPersonTypes(primary.getTypeOfReportingPerson()));
            form.setTypeOfReportingPerson(primary.getTypeOfReportingPerson());
        }

        // Parse ownership info from primary
        if (primary.getOwnershipInfo() != null) {
            parseOwnershipInfo(form, primary.getOwnershipInfo());
        }

        // Parse additional reporting persons
        if (reportingPersons.size() > 1) {
            List<Form13DG.ReportingPerson> additionalPersons = new ArrayList<>();
            for (int i = 1; i < reportingPersons.size(); i++) {
                Schedule13Document.ReportingPersonInfo person = reportingPersons.get(i);
                additionalPersons.add(mapReportingPerson(person));
            }
            form.setAdditionalReportingPersons(additionalPersons);
        }
    }

    private void parseOwnershipInfo(Form13DG form, Schedule13Document.OwnershipInfo ownershipInfo) {
        form.setVotingPowerSole(parseLong(ownershipInfo.getSoleVotingPower()));
        form.setVotingPowerShared(parseLong(ownershipInfo.getSharedVotingPower()));
        form.setDispositivePowerSole(parseLong(ownershipInfo.getSoleDispositivePower()));
        form.setDispositivePowerShared(parseLong(ownershipInfo.getSharedDispositivePower()));
        form.setAggregateAmountBeneficiallyOwned(parseLong(ownershipInfo.getAggregateAmountBeneficiallyOwned()));
        form.setSharesBeneficiallyOwned(parseLong(ownershipInfo.getAggregateAmountBeneficiallyOwned()));
        form.setExcludesCertainShares("true".equalsIgnoreCase(ownershipInfo.getCheckIfExcludesCertainShares()) ||
                                       "1".equals(ownershipInfo.getCheckIfExcludesCertainShares()));
        form.setPercentOfClass(parseDouble(ownershipInfo.getPercentOfClass()));
        form.setPercentOfClassRow11(parseDouble(ownershipInfo.getPercentOfClass()));
    }

    private Form13DG.ReportingPerson mapReportingPerson(Schedule13Document.ReportingPersonInfo person) {
        Form13DG.ReportingPerson.ReportingPersonBuilder builder = Form13DG.ReportingPerson.builder()
                .name(person.getNameOfReportingPerson())
                .cik(person.getReportingPersonCik());

        if (person.getIdentificationOrResidence() != null) {
            builder.citizenshipOrOrganization(person.getIdentificationOrResidence().getCitizenshipOrPlaceOfOrganization());
        }

        if (person.getAddress() != null) {
            builder.address(mapAddress(person.getAddress()));
        }

        if (person.getTypeOfReportingPerson() != null) {
            builder.reportingPersonTypes(parseReportingPersonTypes(person.getTypeOfReportingPerson()));
        }

        if (person.getOwnershipInfo() != null) {
            builder.sharesBeneficiallyOwned(parseLong(person.getOwnershipInfo().getAggregateAmountBeneficiallyOwned()))
                   .percentOfClass(parseDouble(person.getOwnershipInfo().getPercentOfClass()))
                   .votingPowerSole(parseLong(person.getOwnershipInfo().getSoleVotingPower()))
                   .votingPowerShared(parseLong(person.getOwnershipInfo().getSharedVotingPower()))
                   .dispositivePowerSole(parseLong(person.getOwnershipInfo().getSoleDispositivePower()))
                   .dispositivePowerShared(parseLong(person.getOwnershipInfo().getSharedDispositivePower()));
        }

        return builder.build();
    }

    private Form13DG.Address mapAddress(Schedule13Document.Address address) {
        return Form13DG.Address.builder()
                .street1(address.getStreet1())
                .street2(address.getStreet2())
                .city(address.getCity())
                .stateOrCountry(address.getStateOrCountry())
                .zipCode(address.getZipCode())
                .build();
    }

    private void parseSignature(Form13DG form, Schedule13Document.SignatureBlock signatureBlock) {
        if (signatureBlock == null) return;

        form.setSignatureName(signatureBlock.getSignatureName());
        form.setSignatureTitle(signatureBlock.getSignatureTitle());
        form.setSignatureDate(parseDate(signatureBlock.getSignatureDate()));
    }

    /**
     * DOM-based fallback parser for legacy or malformed XML.
     */
    private Form13DG parseWithDom(String xml, String accessionNumber) {
        try {
            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xml)));
            doc.getDocumentElement().normalize();

            Form13DG form = new Form13DG();
            form.setAccessionNumber(accessionNumber);

            Element root = doc.getDocumentElement();

            // Try to determine form type from document
            String docType = getElementText(root, "TYPE");
            if (docType == null) {
                docType = getElementText(root, "submissionType");
            }
            if (docType != null) {
                form.setFormType(docType.trim());
                form.setScheduleType(determineScheduleType(docType));
            }

            // Parse header/filer info
            parseHeaderFromDom(form, root);

            // Parse cover page
            Element coverPage = getFirstElement(root, "coverPage");
            if (coverPage != null) {
                parseCoverPageFromDom(form, coverPage);
            }

            // Parse reporting person info
            parseReportingPersonFromDom(form, root);

            // Parse signature
            Element signature = getFirstElement(root, "signatureBlock");
            if (signature != null) {
                parseSignatureFromDom(form, signature);
            }

            log.debug("DOM parsing succeeded for: {}", accessionNumber);
            return form;

        } catch (Exception e) {
            log.error("DOM parsing failed for accession: {}", accessionNumber, e);
            return null;
        }
    }

    private void parseHeaderFromDom(Form13DG form, Element root) {
        Element headerData = getFirstElement(root, "headerData");
        if (headerData != null) {
            Element filerInfo = getFirstElement(headerData, "filerInfo");
            if (filerInfo != null) {
                Element filer = getFirstElement(filerInfo, "filer");
                if (filer != null) {
                    Element credentials = getFirstElement(filer, "credentials");
                    if (credentials != null) {
                        form.setFilingPersonCik(getElementText(credentials, "cik"));
                    }
                }
            }
        }
    }

    private void parseCoverPageFromDom(Form13DG form, Element coverPage) {
        form.setIssuerName(getElementText(coverPage, "issuerName"));
        form.setIssuerCik(getElementText(coverPage, "issuerCik"));
        form.setCusip(normalizeCusip(getElementText(coverPage, "cusipNumber")));
        form.setSecurityTitle(getElementText(coverPage, "securityTitle"));
        form.setEventDate(parseDate(getElementText(coverPage, "dateOfEvent")));
        form.setFiledDate(parseDate(getElementText(coverPage, "filedDate")));

        String isAmendment = getElementText(coverPage, "isAmendment");
        if ("true".equalsIgnoreCase(isAmendment) || "1".equals(isAmendment)) {
            form.setAmendmentType("AMENDMENT");
            form.setAmendmentNumber(parseInteger(getElementText(coverPage, "amendmentNo")));
        } else {
            form.setAmendmentType("INITIAL");
        }
    }

    private void parseReportingPersonFromDom(Form13DG form, Element root) {
        // Try different element names
        Element reportingPerson = getFirstElement(root, "reportingPersonInfo");
        if (reportingPerson == null) {
            reportingPerson = getFirstElement(root, "reportingPerson");
        }
        if (reportingPerson == null) return;

        form.setFilingPersonName(getElementText(reportingPerson, "nameOfReportingPerson"));

        String reportingPersonCik = getElementText(reportingPerson, "reportingPersonCik");
        if (reportingPersonCik != null) {
            form.setFilingPersonCik(reportingPersonCik);
        }

        Element identification = getFirstElement(reportingPerson, "identificationOrResidence");
        if (identification != null) {
            form.setCitizenshipOrOrganization(getElementText(identification, "citizenshipOrPlaceOfOrganization"));
        }

        Element address = getFirstElement(reportingPerson, "address");
        if (address != null) {
            form.setFilingPersonAddress(Form13DG.Address.builder()
                    .street1(getElementText(address, "street1"))
                    .street2(getElementText(address, "street2"))
                    .city(getElementText(address, "city"))
                    .stateOrCountry(getElementText(address, "stateOrCountry"))
                    .zipCode(getElementText(address, "zipCode"))
                    .build());
        }

        String typeOfReportingPerson = getElementText(reportingPerson, "typeOfReportingPerson");
        if (typeOfReportingPerson != null) {
            form.setReportingPersonTypes(parseReportingPersonTypes(typeOfReportingPerson));
            form.setTypeOfReportingPerson(typeOfReportingPerson);
        }

        // Parse ownership info
        Element ownershipInfo = getFirstElement(reportingPerson, "ownershipInfo");
        if (ownershipInfo != null) {
            form.setVotingPowerSole(parseLong(getElementText(ownershipInfo, "soleVotingPower")));
            form.setVotingPowerShared(parseLong(getElementText(ownershipInfo, "sharedVotingPower")));
            form.setDispositivePowerSole(parseLong(getElementText(ownershipInfo, "soleDispositivePower")));
            form.setDispositivePowerShared(parseLong(getElementText(ownershipInfo, "sharedDispositivePower")));
            form.setAggregateAmountBeneficiallyOwned(parseLong(getElementText(ownershipInfo, "aggregateAmountBeneficiallyOwned")));
            form.setSharesBeneficiallyOwned(parseLong(getElementText(ownershipInfo, "aggregateAmountBeneficiallyOwned")));
            form.setPercentOfClass(parseDouble(getElementText(ownershipInfo, "percentOfClass")));
            form.setPercentOfClassRow11(parseDouble(getElementText(ownershipInfo, "percentOfClass")));

            String excludesCertainShares = getElementText(ownershipInfo, "checkIfExcludesCertainShares");
            form.setExcludesCertainShares("true".equalsIgnoreCase(excludesCertainShares) || "1".equals(excludesCertainShares));
        }
    }

    private void parseSignatureFromDom(Form13DG form, Element signature) {
        form.setSignatureName(getElementText(signature, "signatureName"));
        form.setSignatureTitle(getElementText(signature, "signatureTitle"));
        form.setSignatureDate(parseDate(getElementText(signature, "signatureDate")));
    }

    // Helper methods
    private String determineScheduleType(String formType) {
        if (formType == null) return null;
        String upper = formType.toUpperCase();
        if (upper.contains("13D")) return "13D";
        if (upper.contains("13G")) return "13G";
        return null;
    }

    private String normalizeCusip(String cusip) {
        if (cusip == null) return null;
        // Remove any non-alphanumeric characters and convert to uppercase
        return cusip.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    }

    private List<String> parseReportingPersonTypes(String types) {
        if (types == null || types.isBlank()) return new ArrayList<>();
        List<String> result = new ArrayList<>();
        // Types can be comma-separated or space-separated
        String[] parts = types.split("[,\\s]+");
        for (String part : parts) {
            String trimmed = part.trim().toUpperCase();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private Element getFirstElement(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        return nodes.getLength() > 0 ? (Element) nodes.item(0) : null;
    }

    private String getElementText(Element parent, String tagName) {
        Element element = getFirstElement(parent, tagName);
        return element != null ? element.getTextContent().trim() : null;
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        try {
            String cleaned = dateStr.trim();

            // Try standard format first: yyyy-MM-dd
            if (cleaned.length() == 10 && cleaned.contains("-")) {
                return LocalDate.parse(cleaned, DATE_FORMATTER);
            }

            // Try alternate format: MM/dd/yyyy
            if (cleaned.contains("/")) {
                return LocalDate.parse(cleaned, DATE_FORMATTER_ALT);
            }

            // Try format: MMDDYYYY
            if (cleaned.length() == 8) {
                return LocalDate.of(
                    Integer.parseInt(cleaned.substring(4, 8)),
                    Integer.parseInt(cleaned.substring(0, 2)),
                    Integer.parseInt(cleaned.substring(2, 4))
                );
            }

            // Try format: YYYYMMDD
            if (cleaned.length() == 8) {
                return LocalDate.of(
                    Integer.parseInt(cleaned.substring(0, 4)),
                    Integer.parseInt(cleaned.substring(4, 6)),
                    Integer.parseInt(cleaned.substring(6, 8))
                );
            }

            return LocalDate.parse(cleaned, DATE_FORMATTER);
        } catch (DateTimeParseException | NumberFormatException e) {
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
            if (cleaned.isEmpty()) return null;
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
            String cleaned = value.replaceAll("[^0-9\\-]", "");
            if (cleaned.isEmpty()) return null;
            return Integer.parseInt(cleaned);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse integer: {}", value);
            return null;
        }
    }

    private Double parseDouble(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            // Handle percentage values
            String cleaned = value.replaceAll("[^0-9.\\-]", "");
            if (cleaned.isEmpty()) return null;
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse double: {}", value);
            return null;
        }
    }
}
