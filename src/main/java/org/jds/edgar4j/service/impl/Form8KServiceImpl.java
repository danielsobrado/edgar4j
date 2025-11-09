package org.jds.edgar4j.service.impl;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jds.edgar4j.model.Form8K;
import org.jds.edgar4j.service.Form8KService;
import org.jds.edgar4j.service.IndustryLookupService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Service for downloading and parsing SEC Form 8-K filings
 *
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-11-07
 */
@Slf4j
@Service
public class Form8KServiceImpl implements Form8KService {

    @Value("${edgar4j.urls.edgarDataArchivesUrl}")
    private String edgarDataArchivesUrl;

    @Autowired
    private IndustryLookupService industryLookupService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final DateTimeFormatter DATE_FORMATTER_ISO = DateTimeFormatter.ISO_DATE;
    private static final Pattern ITEM_PATTERN = Pattern.compile("(?i)item\\s+(\\d+\\.\\d+)");

    @Override
    public CompletableFuture<HttpResponse<String>> downloadForm8K(String cik, String accessionNumber, String primaryDocument) {
        log.info("Download Form 8-K for CIK: {}, Accession: {}", cik, accessionNumber);

        final String formURL = edgarDataArchivesUrl + "/" + cik + "/" + accessionNumber.replace("-", "") + "/" + primaryDocument;

        log.debug("Form 8-K URL: {}", formURL);

        final HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(formURL))
                .timeout(Duration.ofSeconds(30))
                .build();
        HttpResponse.BodyHandler<String> bodyHandler = HttpResponse.BodyHandlers.ofString();
        return httpClient.sendAsync(httpRequest, bodyHandler);
    }

    @Override
    public Form8K parseForm8K(String html) {
        log.info("Parsing Form 8-K HTML");

        try {
            Document doc = Jsoup.parse(html);

            Form8K form8K = Form8K.builder()
                    .filingDate(LocalDateTime.now())
                    .items(new ArrayList<>())
                    .eventItems(new ArrayList<>())
                    .isAmendment(false)
                    .build();

            // Parse header information
            parseHeaderInformation(doc, form8K);

            // Parse company information
            parseCompanyInformation(doc, form8K);

            // Parse item numbers and content
            parseItems(doc, form8K);

            // Extract full text content for search
            extractTextContent(doc, form8K);

            // Lookup industry
            if (form8K.getCompanyCik() != null) {
                try {
                    String industry = industryLookupService.getIndustryByCik(form8K.getCompanyCik());
                    form8K.setIndustry(industry);
                } catch (Exception e) {
                    log.debug("Could not lookup industry for CIK {}", form8K.getCompanyCik());
                }
            }

            log.info("Successfully parsed Form 8-K with {} items: {}",
                    form8K.getItems().size(), form8K.getItems());

            return form8K;
        } catch (Exception e) {
            log.error("Error parsing Form 8-K: {}", e.getMessage(), e);
            return Form8K.builder().build();
        }
    }

    private void parseHeaderInformation(Document doc, Form8K form8K) {
        // Parse accession number
        Elements accessionElements = doc.select("accession-number");
        if (!accessionElements.isEmpty()) {
            form8K.setAccessionNumber(accessionElements.first().text().trim());
        }

        // Parse filing date
        Elements filingDateElements = doc.select("filing-date");
        if (!filingDateElements.isEmpty()) {
            String filingDateText = filingDateElements.first().text().trim();
            LocalDate filingDate = parseDate(filingDateText);
            if (filingDate != null) {
                form8K.setFilingDate(filingDate.atStartOfDay());
            }
        }

        // Parse event date (period of report)
        Elements periodElements = doc.select("period-of-report");
        if (!periodElements.isEmpty()) {
            String eventDateText = periodElements.first().text().trim();
            form8K.setEventDate(parseDate(eventDateText));
        }

        // Check for amendment
        Elements typeElements = doc.select("type");
        if (!typeElements.isEmpty()) {
            String type = typeElements.first().text().trim();
            if (type.contains("/A")) {
                form8K.setIsAmendment(true);
            }
        }
    }

    private void parseCompanyInformation(Document doc, Form8K form8K) {
        // Parse company CIK
        Elements cikElements = doc.select("filer cik");
        if (cikElements.isEmpty()) {
            cikElements = doc.select("issuer cik");
        }
        if (!cikElements.isEmpty()) {
            form8K.setCompanyCik(cikElements.first().text().trim());
        }

        // Parse company name
        Elements nameElements = doc.select("filer name");
        if (nameElements.isEmpty()) {
            nameElements = doc.select("issuer name");
        }
        if (!nameElements.isEmpty()) {
            form8K.setCompanyName(nameElements.first().text().trim());
        }

        // Parse trading symbol
        Elements symbolElements = doc.select("trading-symbol");
        if (!symbolElements.isEmpty()) {
            form8K.setTradingSymbol(symbolElements.first().text().trim());
        }

        // Parse IRS number
        Elements irsElements = doc.select("irs-number");
        if (!irsElements.isEmpty()) {
            form8K.setIrsNumber(irsElements.first().text().trim());
        }

        // Parse state of incorporation
        Elements stateElements = doc.select("state-of-incorporation");
        if (!stateElements.isEmpty()) {
            form8K.setStateOfIncorporation(stateElements.first().text().trim());
        }

        // Parse fiscal year end
        Elements fiscalElements = doc.select("fiscal-year-end");
        if (!fiscalElements.isEmpty()) {
            form8K.setFiscalYearEnd(fiscalElements.first().text().trim());
        }
    }

    private void parseItems(Document doc, Form8K form8K) {
        Set<String> foundItems = new HashSet<>();

        // Look for item numbers in various places

        // 1. Check for XML item elements
        Elements itemElements = doc.select("item");
        for (Element item : itemElements) {
            String itemNumber = item.attr("item-number");
            if (itemNumber != null && !itemNumber.isEmpty()) {
                foundItems.add(itemNumber);

                // Create event item
                Form8K.EventItem eventItem = Form8K.EventItem.builder()
                    .itemNumber(itemNumber)
                    .title(Form8K.getEventTypeForItem(itemNumber))
                    .category(Form8K.getCategoryForItem(itemNumber))
                    .content(item.text())
                    .build();

                form8K.getEventItems().add(eventItem);
            }
        }

        // 2. Parse text content for "Item X.XX" patterns
        String bodyText = doc.text();
        Matcher itemMatcher = ITEM_PATTERN.matcher(bodyText);
        while (itemMatcher.find()) {
            String itemNumber = itemMatcher.group(1);
            foundItems.add(itemNumber);

            // Only add event item if not already added from XML
            boolean alreadyExists = form8K.getEventItems().stream()
                .anyMatch(ei -> itemNumber.equals(ei.getItemNumber()));

            if (!alreadyExists) {
                // Try to extract content around this item
                String content = extractItemContent(bodyText, itemNumber);

                Form8K.EventItem eventItem = Form8K.EventItem.builder()
                    .itemNumber(itemNumber)
                    .title(Form8K.getEventTypeForItem(itemNumber))
                    .category(Form8K.getCategoryForItem(itemNumber))
                    .content(content)
                    .build();

                form8K.getEventItems().add(eventItem);
            }
        }

        // 3. Look for common section headers
        Elements headers = doc.select("h1, h2, h3, h4, b, strong");
        for (Element header : headers) {
            String headerText = header.text();
            Matcher matcher = ITEM_PATTERN.matcher(headerText);
            if (matcher.find()) {
                String itemNumber = matcher.group(1);
                foundItems.add(itemNumber);

                boolean alreadyExists = form8K.getEventItems().stream()
                    .anyMatch(ei -> itemNumber.equals(ei.getItemNumber()));

                if (!alreadyExists) {
                    // Get content from following siblings
                    StringBuilder content = new StringBuilder();
                    Element next = header.nextElementSibling();
                    int count = 0;
                    while (next != null && count < 5) {
                        String text = next.text().trim();
                        if (!text.isEmpty() && !ITEM_PATTERN.matcher(text).find()) {
                            content.append(text).append("\n");
                        } else {
                            break;
                        }
                        next = next.nextElementSibling();
                        count++;
                    }

                    Form8K.EventItem eventItem = Form8K.EventItem.builder()
                        .itemNumber(itemNumber)
                        .title(Form8K.getEventTypeForItem(itemNumber))
                        .category(Form8K.getCategoryForItem(itemNumber))
                        .content(content.toString().trim())
                        .build();

                    form8K.getEventItems().add(eventItem);
                }
            }
        }

        // Convert set to sorted list
        List<String> itemsList = new ArrayList<>(foundItems);
        itemsList.sort(String::compareTo);
        form8K.setItems(itemsList);
    }

    private String extractItemContent(String text, String itemNumber) {
        // Find the position of this item
        Pattern pattern = Pattern.compile("(?i)item\\s+" + Pattern.quote(itemNumber) + "\\b");
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            int start = matcher.end();

            // Find the next item or end
            Pattern nextItemPattern = Pattern.compile("(?i)item\\s+\\d+\\.\\d+");
            Matcher nextMatcher = nextItemPattern.matcher(text);

            int end = text.length();
            if (nextMatcher.find(start)) {
                end = nextMatcher.start();
            }

            // Extract content (limit to reasonable length)
            int maxLength = Math.min(end - start, 5000);
            return text.substring(start, start + maxLength).trim();
        }

        return "";
    }

    private void extractTextContent(Document doc, Form8K form8K) {
        // Extract text from document body, excluding scripts and styles
        Elements body = doc.select("body");
        if (!body.isEmpty()) {
            // Remove script and style elements
            body.select("script, style").remove();

            String text = body.text();

            // Limit text length for storage
            if (text.length() > 50000) {
                text = text.substring(0, 50000);
            }

            form8K.setTextContent(text);
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
}
