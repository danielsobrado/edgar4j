package org.jds.edgar4j.service.impl;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jds.edgar4j.model.DerivativeTransaction;
import org.jds.edgar4j.model.InsiderForm;
import org.jds.edgar4j.model.NonDerivativeTransaction;
import org.jds.edgar4j.model.ReportingOwner;
import org.jds.edgar4j.service.InsiderFormService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Service for downloading and parsing SEC Insider Forms (Forms 3, 4, and 5)
 *
 * All three forms share identical XML structure, allowing 95% code reuse.
 *
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-11-07
 */
@Slf4j
@Service
public class InsiderFormServiceImpl implements InsiderFormService {

        @Value("${edgar4j.urls.edgarDataArchivesUrl}")
        private String edgarDataArchivesUrl;

        private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        private static final Pattern NAME_PATTERN = Pattern.compile("(.+?)\\s+(\\S+)\\s*(\\S*)");

        @Override
        public CompletableFuture<HttpResponse<String>> downloadInsiderForm(String cik, String accessionNumber, String primaryDocument) {
                log.info("Download insider form for CIK: {}, Accession: {}", cik, accessionNumber);

                final String formURL = edgarDataArchivesUrl + "/" + cik + "/" + accessionNumber.replace("-", "") + "/" + primaryDocument;

                log.debug("Form URL: {}", formURL);

                final HttpClient httpClient = HttpClient.newHttpClient();
                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create(formURL))
                        .build();
                HttpResponse.BodyHandler<String> bodyHandler = HttpResponse.BodyHandlers.ofString();
                return httpClient.sendAsync(httpRequest, bodyHandler);
        }

        @Override
        public InsiderForm parseInsiderForm(String html, String formType) {
                log.info("Parsing Insider Form {} HTML", formType);

                try {
                        Document doc = Jsoup.parse(html);

                        InsiderForm form = InsiderForm.builder()
                                .formType(formType)
                                .filingDate(LocalDateTime.now())
                                .reportingOwners(new ArrayList<>())
                                .nonDerivativeTransactions(new ArrayList<>())
                                .derivativeTransactions(new ArrayList<>())
                                .build();

                        // Parse reporting owner information
                        ReportingOwner reportingOwner = parseReportingOwner(doc);
                        if (reportingOwner != null) {
                                form.getReportingOwners().add(reportingOwner);
                        }

                        // Parse issuer information
                        parseIssuerInfo(doc, form);

                        // Parse transaction date (period of report)
                        parsePeriodOfReport(doc, form);

                        // Parse Table I - Non-Derivative Transactions
                        parseNonDerivativeTransactions(doc, form);

                        // Parse Table II - Derivative Transactions
                        parseDerivativeTransactions(doc, form);

                        // Parse footnotes and remarks
                        parseFootnotesAndRemarks(doc, form);

                        // Parse signature
                        parseSignature(doc, form);

                        log.info("Successfully parsed Form {} with {} non-derivative and {} derivative transactions",
                                formType,
                                form.getNonDerivativeTransactions().size(),
                                form.getDerivativeTransactions().size());

                        return form;
                } catch (Exception e) {
                        log.error("Error parsing Form {}: {}", formType, e.getMessage(), e);
                        return InsiderForm.builder().formType(formType).build();
                }
        }

        private ReportingOwner parseReportingOwner(Document doc) {
                try {
                        ReportingOwner owner = ReportingOwner.builder().build();

                        // Extract name - look for link with CIK
                        Elements nameLinks = doc.select("a[href*=browse-edgar]");
                        for (Element link : nameLinks) {
                                String text = link.text().trim();
                                if (text.length() > 0 && !text.startsWith("See")) {
                                        // Parse name (format: "Last First Middle" or just full name)
                                        owner.setName(text);
                                        parseName(text, owner);

                                        // Extract CIK from URL
                                        String href = link.attr("href");
                                        if (href.contains("CIK=")) {
                                                String cik = href.substring(href.indexOf("CIK=") + 4);
                                                owner.setCik(cik.replaceAll("[^0-9]", ""));
                                        }
                                        break;
                                }
                        }

                        // Parse address
                        Elements tables = doc.select("table");
                        for (Element table : tables) {
                                String tableText = table.text();
                                if (tableText.contains("(Street)")) {
                                        Elements rows = table.select("tr");
                                        for (Element row : rows) {
                                                Elements dataCells = row.select("span.FormData");
                                                if (!dataCells.isEmpty()) {
                                                        String text = dataCells.text().trim();
                                                        if (owner.getStreet1() == null && text.length() > 0) {
                                                                owner.setStreet1(text);
                                                        } else if (owner.getCity() == null && text.contains(" ")) {
                                                                // City, State, Zip row
                                                                String[] parts = text.split("\\s+");
                                                                if (parts.length >= 3) {
                                                                        owner.setCity(parts[0]);
                                                                        owner.setStateOrCountry(parts[1]);
                                                                        owner.setZipCode(parts[2]);
                                                                }
                                                        }
                                                }
                                        }
                                }
                        }

                        // Parse relationship to issuer
                        parseRelationship(doc, owner);

                        return owner;
                } catch (Exception e) {
                        log.warn("Error parsing reporting owner: {}", e.getMessage());
                        return null;
                }
        }

        private void parseName(String fullName, ReportingOwner owner) {
                Matcher matcher = NAME_PATTERN.matcher(fullName);
                if (matcher.matches()) {
                        owner.setFirstName(matcher.group(1).trim());
                        owner.setLastName(matcher.group(2).trim());
                        if (matcher.groupCount() > 2) {
                                owner.setMiddleName(matcher.group(3).trim());
                        }
                } else {
                        // Simple split by space
                        String[] parts = fullName.split("\\s+");
                        if (parts.length >= 2) {
                                owner.setFirstName(parts[0]);
                                owner.setLastName(parts[parts.length - 1]);
                                if (parts.length > 2) {
                                        owner.setMiddleName(parts[1]);
                                }
                        }
                }
        }

        private void parseRelationship(Document doc, ReportingOwner owner) {
                // Look for the relationship section (section 5)
                Elements cells = doc.select("td");
                for (Element cell : cells) {
                        String text = cell.text();
                        if (text.contains("Relationship of Reporting Person")) {
                                // Find the X markers
                                Elements allCells = cell.parent().parent().select("td");
                                for (int i = 0; i < allCells.size(); i++) {
                                        Element c = allCells.get(i);
                                        String cellText = c.text().trim();

                                        if ("X".equals(cellText) || "x".equals(cellText)) {
                                                // Check adjacent cell for relationship type
                                                if (i + 1 < allCells.size()) {
                                                        String nextText = allCells.get(i + 1).text();
                                                        if (nextText.contains("Director")) {
                                                                owner.setDirector(true);
                                                        } else if (nextText.contains("10%")) {
                                                                owner.setTenPercentOwner(true);
                                                        }
                                                }

                                                // Check previous cell
                                                if (i - 1 >= 0) {
                                                        String prevText = allCells.get(i - 1).text();
                                                        if (prevText.contains("Director")) {
                                                                owner.setDirector(true);
                                                        } else if (prevText.contains("10%")) {
                                                                owner.setTenPercentOwner(true);
                                                        } else if (prevText.contains("Officer")) {
                                                                owner.setOfficer(true);
                                                                // Look for officer title
                                                                parseOfficerTitle(doc, owner);
                                                        } else if (prevText.contains("Other")) {
                                                                owner.setOther(true);
                                                        }
                                                }
                                        }
                                }
                                break;
                        }
                }
        }

        private void parseOfficerTitle(Document doc, ReportingOwner owner) {
                // Officer title is usually in a separate field
                Elements titleElements = doc.select("span.FormData");
                for (Element el : titleElements) {
                        String text = el.text().trim();
                        if (text.length() > 0 &&
                            (text.toLowerCase().contains("ceo") ||
                             text.toLowerCase().contains("cfo") ||
                             text.toLowerCase().contains("president") ||
                             text.toLowerCase().contains("officer"))) {
                                owner.setOfficerTitle(text);
                                break;
                        }
                }
        }

        private void parseIssuerInfo(Document doc, InsiderForm form) {
                // Look for issuer name and ticker
                Elements links = doc.select("a[href*=browse-edgar]");
                for (Element link : links) {
                        String href = link.attr("href");
                        String text = link.text().trim();

                        if (href.contains("CIK=") && text.length() > 0) {
                                // Check if next element contains ticker
                                Element parent = link.parent();
                                if (parent != null) {
                                        String parentText = parent.text();
                                        if (parentText.contains("[") && parentText.contains("]")) {
                                                // Extract ticker
                                                int start = parentText.indexOf("[") + 1;
                                                int end = parentText.indexOf("]");
                                                if (start > 0 && end > start) {
                                                        String ticker = parentText.substring(start, end).trim();
                                                        form.setTradingSymbol(ticker);
                                                        form.setIssuerName(text);

                                                        // Extract CIK
                                                        String cik = href.substring(href.indexOf("CIK=") + 4);
                                                        form.setIssuerCik(cik.replaceAll("[^0-9]", ""));
                                                        break;
                                                }
                                        }
                                }
                        }
                }
        }

        private void parsePeriodOfReport(Document doc, InsiderForm form) {
                // Look for "Date of Earliest Transaction"
                Elements cells = doc.select("td");
                for (Element cell : cells) {
                        if (cell.text().contains("Date of Earliest Transaction")) {
                                // Next sibling or child should contain the date
                                Elements dataCells = cell.parent().select("span.FormData");
                                for (Element dataCell : dataCells) {
                                        String dateText = dataCell.text().trim();
                                        LocalDate date = parseDate(dateText);
                                        if (date != null) {
                                                form.setPeriodOfReport(date);
                                                break;
                                        }
                                }
                                break;
                        }
                }
        }

        private void parseNonDerivativeTransactions(Document doc, InsiderForm form) {
                // Find Table I
                Elements tables = doc.select("table");
                for (Element table : tables) {
                        if (table.text().contains("Table I") && table.text().contains("Non-Derivative")) {
                                // Find tbody
                                Elements tbody = table.select("tbody");
                                if (!tbody.isEmpty()) {
                                        Elements rows = tbody.first().select("tr");
                                        for (Element row : rows) {
                                                NonDerivativeTransaction transaction = parseNonDerivativeRow(row);
                                                if (transaction != null) {
                                                        form.getNonDerivativeTransactions().add(transaction);
                                                }
                                        }
                                }
                                break;
                        }
                }
        }

        private NonDerivativeTransaction parseNonDerivativeRow(Element row) {
                try {
                        Elements cells = row.select("td");
                        if (cells.size() < 11) {
                                return null;
                        }

                        NonDerivativeTransaction transaction = NonDerivativeTransaction.builder().build();

                        // Column 0: Security Title
                        transaction.setSecurityTitle(extractText(cells.get(0)));

                        // Column 1: Transaction Date
                        String dateText = extractText(cells.get(1));
                        transaction.setTransactionDate(parseDate(dateText));

                        // Column 2: Deemed Execution Date
                        String deemedDateText = extractText(cells.get(2));
                        transaction.setDeemedExecutionDate(parseDate(deemedDateText));

                        // Column 3: Transaction Code
                        transaction.setTransactionCode(extractText(cells.get(3)));

                        // Column 4: Transaction Code V
                        transaction.setTransactionCodeV(extractText(cells.get(4)));

                        // Column 5: Amount (shares)
                        String sharesText = extractText(cells.get(5));
                        transaction.setTransactionShares(parseBigDecimal(sharesText));

                        // Column 6: Acquired (A) or Disposed (D)
                        transaction.setAcquiredDisposedCode(extractText(cells.get(6)));

                        // Column 7: Price
                        String priceText = extractText(cells.get(7));
                        transaction.setTransactionPricePerShare(parseBigDecimal(priceText));

                        // Column 8: Shares Owned Following Transaction
                        String ownedText = extractText(cells.get(8));
                        transaction.setSharesOwnedFollowingTransaction(parseBigDecimal(ownedText));

                        // Column 9: Direct (D) or Indirect (I) Ownership
                        transaction.setDirectOrIndirectOwnership(extractText(cells.get(9)));

                        // Column 10: Nature of Indirect Ownership
                        transaction.setNatureOfOwnership(extractText(cells.get(10)));

                        // Only return if there's actual transaction data
                        if (transaction.getTransactionShares() != null &&
                            transaction.getTransactionShares().compareTo(BigDecimal.ZERO) > 0) {
                                return transaction;
                        }

                        return null;
                } catch (Exception e) {
                        log.warn("Error parsing non-derivative transaction row: {}", e.getMessage());
                        return null;
                }
        }

        private void parseDerivativeTransactions(Document doc, InsiderForm form) {
                // Find Table II
                Elements tables = doc.select("table");
                for (Element table : tables) {
                        if (table.text().contains("Table II") && table.text().contains("Derivative")) {
                                // Find tbody
                                Elements tbody = table.select("tbody");
                                if (!tbody.isEmpty()) {
                                        Elements rows = tbody.first().select("tr");
                                        for (Element row : rows) {
                                                DerivativeTransaction transaction = parseDerivativeRow(row);
                                                if (transaction != null) {
                                                        form.getDerivativeTransactions().add(transaction);
                                                }
                                        }
                                }
                                break;
                        }
                }
        }

        private DerivativeTransaction parseDerivativeRow(Element row) {
                try {
                        Elements cells = row.select("td");
                        if (cells.size() < 16) {
                                return null;
                        }

                        DerivativeTransaction transaction = DerivativeTransaction.builder().build();

                        // Column 0: Security Title
                        transaction.setSecurityTitle(extractText(cells.get(0)));

                        // Column 1: Conversion/Exercise Price
                        String exerciseText = extractText(cells.get(1));
                        transaction.setConversionOrExercisePrice(parseBigDecimal(exerciseText));

                        // Column 2: Transaction Date
                        String dateText = extractText(cells.get(2));
                        transaction.setTransactionDate(parseDate(dateText));

                        // Column 3: Deemed Execution Date
                        String deemedDateText = extractText(cells.get(3));
                        transaction.setDeemedExecutionDate(parseDate(deemedDateText));

                        // Column 4: Transaction Code
                        transaction.setTransactionCode(extractText(cells.get(4)));

                        // Column 5: Transaction Code V
                        transaction.setTransactionCodeV(extractText(cells.get(5)));

                        // Column 6: Securities Acquired (A)
                        String acquiredText = extractText(cells.get(6));
                        transaction.setSecuritiesAcquired(parseBigDecimal(acquiredText));

                        // Column 7: Securities Disposed (D)
                        String disposedText = extractText(cells.get(7));
                        transaction.setSecuritiesDisposed(parseBigDecimal(disposedText));

                        // Column 8: Date Exercisable
                        String dateExercisableText = extractText(cells.get(8));
                        transaction.setDateExercisable(parseDate(dateExercisableText));

                        // Column 9: Expiration Date
                        String expirationText = extractText(cells.get(9));
                        transaction.setExpirationDate(parseDate(expirationText));

                        // Column 10: Underlying Security Title
                        transaction.setUnderlyingSecurityTitle(extractText(cells.get(10)));

                        // Column 11: Underlying Security Shares
                        String underlyingSharesText = extractText(cells.get(11));
                        transaction.setUnderlyingSecurityShares(parseBigDecimal(underlyingSharesText));

                        // Column 12: Price of Derivative Security
                        String priceText = extractText(cells.get(12));
                        transaction.setDerivativeSecurityPrice(parseBigDecimal(priceText));

                        // Column 13: Securities Owned Following Transaction
                        String ownedText = extractText(cells.get(13));
                        transaction.setSecuritiesOwnedFollowingTransaction(parseBigDecimal(ownedText));

                        // Column 14: Direct (D) or Indirect (I) Ownership
                        transaction.setDirectOrIndirectOwnership(extractText(cells.get(14)));

                        // Column 15: Nature of Indirect Ownership
                        transaction.setNatureOfOwnership(extractText(cells.get(15)));

                        // Only return if there's actual transaction data
                        if ((transaction.getSecuritiesAcquired() != null &&
                             transaction.getSecuritiesAcquired().compareTo(BigDecimal.ZERO) > 0) ||
                            (transaction.getSecuritiesDisposed() != null &&
                             transaction.getSecuritiesDisposed().compareTo(BigDecimal.ZERO) > 0)) {
                                return transaction;
                        }

                        return null;
                } catch (Exception e) {
                        log.warn("Error parsing derivative transaction row: {}", e.getMessage());
                        return null;
                }
        }

        private void parseFootnotesAndRemarks(Document doc, InsiderForm form) {
                // Find "Explanation of Responses"
                Elements tables = doc.select("table");
                for (Element table : tables) {
                        String text = table.text();
                        if (text.contains("Explanation of Responses")) {
                                Elements footnoteCells = table.select("td.FootnoteData");
                                StringBuilder footnotes = new StringBuilder();
                                for (Element cell : footnoteCells) {
                                        footnotes.append(cell.text()).append("\n");
                                }
                                form.setFootnotes(footnotes.toString().trim());
                        }

                        if (text.contains("Remarks:")) {
                                Elements remarkCells = table.select("td.FootnoteData");
                                for (Element cell : remarkCells) {
                                        String remarkText = cell.text().trim();
                                        if (remarkText.length() > 0) {
                                                form.setRemarks(remarkText);
                                                break;
                                        }
                                }
                        }
                }
        }

        private void parseSignature(Document doc, InsiderForm form) {
                Elements tables = doc.select("table");
                for (Element table : tables) {
                        Elements rows = table.select("tr");
                        for (Element row : rows) {
                                if (row.text().contains("Signature of Reporting Person")) {
                                        // Previous row should contain signature and date
                                        Element prevRow = row.previousElementSibling();
                                        if (prevRow != null) {
                                                Elements cells = prevRow.select("td");
                                                if (cells.size() >= 2) {
                                                        // Signature in second cell
                                                        Elements sigElements = cells.get(1).select("span.FormData");
                                                        if (!sigElements.isEmpty()) {
                                                                form.setSignature(sigElements.text());
                                                        }

                                                        // Date in third cell
                                                        if (cells.size() >= 3) {
                                                                Elements dateElements = cells.get(2).select("span.FormData");
                                                                if (!dateElements.isEmpty()) {
                                                                        LocalDate sigDate = parseDate(dateElements.text());
                                                                        form.setSignatureDate(sigDate);
                                                                }
                                                        }
                                                }
                                        }
                                        break;
                                }
                        }
                }
        }

        private String extractText(Element element) {
                if (element == null) {
                        return "";
                }
                Elements dataElements = element.select("span.FormData, span.SmallFormData");
                if (!dataElements.isEmpty()) {
                        return dataElements.text().trim();
                }
                return element.text().trim();
        }

        private LocalDate parseDate(String dateText) {
                if (dateText == null || dateText.trim().isEmpty()) {
                        return null;
                }

                dateText = dateText.trim().replaceAll("[^0-9/]", "");

                try {
                        return LocalDate.parse(dateText, DATE_FORMATTER);
                } catch (DateTimeParseException e) {
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
}
