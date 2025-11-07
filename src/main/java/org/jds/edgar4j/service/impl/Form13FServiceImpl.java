package org.jds.edgar4j.service.impl;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.jds.edgar4j.model.Form13F;
import org.jds.edgar4j.service.Form13FService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Service for downloading and parsing SEC Form 13F filings
 *
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-11-07
 */
@Slf4j
@Service
public class Form13FServiceImpl implements Form13FService {

    @Value("${edgar4j.urls.edgarDataArchivesUrl}")
    private String edgarDataArchivesUrl;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final DateTimeFormatter DATE_FORMATTER_ISO = DateTimeFormatter.ISO_DATE;

    @Override
    public CompletableFuture<HttpResponse<String>> downloadForm13F(String cik, String accessionNumber, String primaryDocument) {
        log.info("Download Form 13F for CIK: {}, Accession: {}", cik, accessionNumber);

        final String formURL = edgarDataArchivesUrl + "/" + cik + "/" + accessionNumber.replace("-", "") + "/" + primaryDocument;

        log.debug("Form 13F URL: {}", formURL);

        final HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(formURL))
                .timeout(Duration.ofSeconds(30))
                .build();
        HttpResponse.BodyHandler<String> bodyHandler = HttpResponse.BodyHandlers.ofString();
        return httpClient.sendAsync(httpRequest, bodyHandler);
    }

    @Override
    public Form13F parseForm13F(String xml) {
        log.info("Parsing Form 13F XML");

        try {
            Document doc = Jsoup.parse(xml);

            Form13F form13F = Form13F.builder()
                    .filingDate(LocalDateTime.now())
                    .holdings(new ArrayList<>())
                    .isAmendment(false)
                    .build();

            // Parse header information
            parseHeaderInformation(doc, form13F);

            // Parse filer information
            parseFilerInformation(doc, form13F);

            // Parse summary information
            parseSummaryInformation(doc, form13F);

            // Parse info table (holdings)
            parseInfoTable(doc, form13F);

            // Parse signature
            parseSignature(doc, form13F);

            // Calculate totals
            form13F.calculateHoldingsCount();
            if (form13F.getTotalValue() == null) {
                form13F.calculateTotalValue();
            }

            log.info("Successfully parsed Form 13F with {} holdings (total value: ${})",
                    form13F.getHoldingsCount(),
                    form13F.getTotalValue());

            return form13F;
        } catch (Exception e) {
            log.error("Error parsing Form 13F: {}", e.getMessage(), e);
            return Form13F.builder().build();
        }
    }

    private void parseHeaderInformation(Document doc, Form13F form13F) {
        // Parse accession number
        Elements accessionElements = doc.select("accession-number");
        if (!accessionElements.isEmpty()) {
            form13F.setAccessionNumber(accessionElements.first().text().trim());
        }

        // Parse period of report
        Elements periodElements = doc.select("period-of-report");
        if (!periodElements.isEmpty()) {
            String periodText = periodElements.first().text().trim();
            form13F.setPeriodOfReport(parseDate(periodText));
        }

        // Parse report type
        Elements typeElements = doc.select("type");
        if (!typeElements.isEmpty()) {
            String reportType = typeElements.first().text().trim();
            form13F.setReportType(reportType);

            // Check if amendment
            if (reportType.contains("/A")) {
                form13F.setIsAmendment(true);
            }
        }

        // Parse filing date
        Elements filingDateElements = doc.select("filing-date");
        if (!filingDateElements.isEmpty()) {
            String filingDateText = filingDateElements.first().text().trim();
            LocalDate filingDate = parseDate(filingDateText);
            if (filingDate != null) {
                form13F.setFilingDate(filingDate.atStartOfDay());
            }
        }
    }

    private void parseFilerInformation(Document doc, Form13F form13F) {
        // Parse filer CIK
        Elements cikElements = doc.select("filer cik");
        if (!cikElements.isEmpty()) {
            form13F.setFilerCik(cikElements.first().text().trim());
        }

        // Parse filer name
        Elements nameElements = doc.select("filer name");
        if (!nameElements.isEmpty()) {
            form13F.setFilerName(nameElements.first().text().trim());
        }

        // Parse IRS number
        Elements irsElements = doc.select("irs-number");
        if (!irsElements.isEmpty()) {
            form13F.setFilerIrsNumber(irsElements.first().text().trim());
        }

        // Parse filer address
        Elements addressElements = doc.select("filer address");
        if (!addressElements.isEmpty()) {
            Element address = addressElements.first();

            Elements street1 = address.select("street1");
            if (!street1.isEmpty()) {
                form13F.setFilerStreet1(street1.first().text().trim());
            }

            Elements street2 = address.select("street2");
            if (!street2.isEmpty()) {
                form13F.setFilerStreet2(street2.first().text().trim());
            }

            Elements city = address.select("city");
            if (!city.isEmpty()) {
                form13F.setFilerCity(city.first().text().trim());
            }

            Elements state = address.select("state-or-country");
            if (!state.isEmpty()) {
                form13F.setFilerState(state.first().text().trim());
            }

            Elements zip = address.select("zip-code");
            if (!zip.isEmpty()) {
                form13F.setFilerZipCode(zip.first().text().trim());
            }
        }
    }

    private void parseSummaryInformation(Document doc, Form13F form13F) {
        // Parse total value (table value total)
        Elements valueElements = doc.select("table-value-total");
        if (!valueElements.isEmpty()) {
            String valueText = valueElements.first().text().trim();
            BigDecimal totalValue = parseBigDecimal(valueText);
            if (totalValue != null) {
                // Value is in thousands
                form13F.setTotalValue(totalValue.multiply(BigDecimal.valueOf(1000)));
            }
        }

        // Parse number of holdings
        Elements countElements = doc.select("table-entry-total");
        if (!countElements.isEmpty()) {
            String countText = countElements.first().text().trim();
            try {
                form13F.setHoldingsCount(Integer.parseInt(countText));
            } catch (NumberFormatException e) {
                log.debug("Unable to parse holdings count: {}", countText);
            }
        }
    }

    private void parseInfoTable(Document doc, Form13F form13F) {
        // Find all infoTable entries
        Elements infoTableEntries = doc.select("infoTable");

        log.debug("Found {} infoTable entries", infoTableEntries.size());

        for (Element entry : infoTableEntries) {
            Form13F.Holding holding = parseHoldingEntry(entry);
            if (holding != null) {
                form13F.getHoldings().add(holding);
            }
        }
    }

    private Form13F.Holding parseHoldingEntry(Element entry) {
        try {
            Form13F.Holding holding = Form13F.Holding.builder().build();

            // Name of issuer
            Elements nameElements = entry.select("nameOfIssuer");
            if (!nameElements.isEmpty()) {
                holding.setNameOfIssuer(nameElements.first().text().trim());
            }

            // Title of class
            Elements titleElements = entry.select("titleOfClass");
            if (!titleElements.isEmpty()) {
                holding.setTitleOfClass(titleElements.first().text().trim());
            }

            // CUSIP
            Elements cusipElements = entry.select("cusip");
            if (!cusipElements.isEmpty()) {
                holding.setCusip(cusipElements.first().text().trim());
            }

            // Value (in thousands)
            Elements valueElements = entry.select("value");
            if (!valueElements.isEmpty()) {
                String valueText = valueElements.first().text().trim();
                BigDecimal value = parseBigDecimal(valueText);
                if (value != null) {
                    // Value is in thousands
                    holding.setValue(value.multiply(BigDecimal.valueOf(1000)));
                }
            }

            // Shares or principal amount
            Elements sharesElements = entry.select("sshPrnamt");
            if (!sharesElements.isEmpty()) {
                String sharesText = sharesElements.first().text().trim();
                try {
                    holding.setSharesOrPrincipalAmount(Long.parseLong(sharesText.replaceAll("[^0-9]", "")));
                } catch (NumberFormatException e) {
                    log.debug("Unable to parse shares: {}", sharesText);
                }
            }

            // Share/Principal indicator
            Elements shPrnElements = entry.select("sshPrnamtType");
            if (!shPrnElements.isEmpty()) {
                holding.setShPrn(shPrnElements.first().text().trim());
            }

            // Put/Call
            Elements putCallElements = entry.select("putCall");
            if (!putCallElements.isEmpty()) {
                holding.setPutCall(putCallElements.first().text().trim());
            }

            // Investment discretion
            Elements discretionElements = entry.select("investmentDiscretion");
            if (!discretionElements.isEmpty()) {
                holding.setInvestmentDiscretion(discretionElements.first().text().trim());
            }

            // Other manager
            Elements otherMgrElements = entry.select("otherManager");
            if (!otherMgrElements.isEmpty()) {
                String otherMgrText = otherMgrElements.first().text().trim();
                try {
                    holding.setOtherManager(Integer.parseInt(otherMgrText));
                } catch (NumberFormatException e) {
                    log.debug("Unable to parse other manager: {}", otherMgrText);
                }
            }

            // Voting authority
            Elements votingElements = entry.select("votingAuthority");
            if (!votingElements.isEmpty()) {
                Element votingAuth = votingElements.first();

                Elements sole = votingAuth.select("Sole");
                if (!sole.isEmpty()) {
                    holding.setVotingAuthoritySole(parseLong(sole.first().text()));
                }

                Elements shared = votingAuth.select("Shared");
                if (!shared.isEmpty()) {
                    holding.setVotingAuthorityShared(parseLong(shared.first().text()));
                }

                Elements none = votingAuth.select("None");
                if (!none.isEmpty()) {
                    holding.setVotingAuthorityNone(parseLong(none.first().text()));
                }
            }

            // Only return if we have at least a CUSIP
            if (holding.getCusip() != null && !holding.getCusip().isEmpty()) {
                return holding;
            }

            return null;
        } catch (Exception e) {
            log.warn("Error parsing holding entry: {}", e.getMessage());
            return null;
        }
    }

    private void parseSignature(Document doc, Form13F form13F) {
        // Parse signature name
        Elements signatureElements = doc.select("signature name");
        if (!signatureElements.isEmpty()) {
            form13F.setSignature(signatureElements.first().text().trim());
        }

        // Parse signature date
        Elements dateElements = doc.select("signature date");
        if (!dateElements.isEmpty()) {
            String dateText = dateElements.first().text().trim();
            form13F.setSignatureDate(parseDate(dateText));
        }
    }

    private LocalDate parseDate(String dateText) {
        if (dateText == null || dateText.trim().isEmpty()) {
            return null;
        }

        dateText = dateText.trim();

        // Try ISO format first (YYYY-MM-DD)
        try {
            return LocalDate.parse(dateText, DATE_FORMATTER_ISO);
        } catch (Exception e) {
            // Ignore, try next format
        }

        // Try MM/dd/yyyy format
        try {
            dateText = dateText.replaceAll("[^0-9/]", "");
            return LocalDate.parse(dateText, DATE_FORMATTER);
        } catch (Exception e) {
            log.debug("Unable to parse date: {}", dateText);
            return null;
        }
    }

    private BigDecimal parseBigDecimal(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }

        // Remove currency symbols, commas, and other formatting
        text = text.trim().replaceAll("[$,]", "");

        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            log.debug("Unable to parse number: {}", text);
            return null;
        }
    }

    private Long parseLong(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }

        text = text.trim().replaceAll("[^0-9]", "");

        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            log.debug("Unable to parse long: {}", text);
            return null;
        }
    }
}
