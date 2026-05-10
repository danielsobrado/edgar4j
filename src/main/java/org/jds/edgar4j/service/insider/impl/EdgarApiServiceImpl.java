package org.jds.edgar4j.service.insider.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.DecimalFormat;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.jds.edgar4j.properties.Edgar4JProperties;
import org.jds.edgar4j.service.SettingsService;
import org.jds.edgar4j.service.insider.EdgarApiService;
import org.jds.edgar4j.service.insider.InsiderTransactionService;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of EdgarApiService for SEC EDGAR API integration
 * 
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EdgarApiServiceImpl implements EdgarApiService {

    private static final String DEFAULT_USER_AGENT = "Edgar4J/1.0";
    private static final int MAX_BULK_COMPANIES = 200;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build();
    private final SettingsService settingsService;
    private final Edgar4JProperties edgar4JProperties;
    private final InsiderTransactionService insiderTransactionService;

    private static final DecimalFormat CIK_FORMAT = new DecimalFormat("0000000000");

    @Override
    public CompletableFuture<Void> processCompanySubmissions(String cik) {
        log.info("Processing company submissions for CIK: {}", cik);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                String formattedCik = formatCik(cik);
                String url = edgar4JProperties.getUrls().getSubmissionsCIKUrl() + formattedCik + ".json";
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", resolveUserAgent())
                    .header("Accept", "application/json")
                    .header("Accept-Encoding", "gzip, deflate")
                    .timeout(Duration.ofSeconds(30))
                    .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    processSubmissionsResponse(response.body(), cik);
                    log.info("Successfully processed submissions for CIK: {}", cik);
                } else {
                    log.warn("Failed to fetch submissions for CIK: {}. Status: {}", cik, response.statusCode());
                }
                
                return null;
                
            } catch (Exception e) {
                log.error("Error processing company submissions for CIK: {}", cik, e);
                throw new RuntimeException("Failed to process company submissions", e);
            }
        });
    }

    @Override
    public CompletableFuture<String> downloadForm4Document(String cik, String accessionNumber, String primaryDocument) {
        log.info("Downloading Form 4 document - CIK: {}, Accession: {}, Document: {}", 
                cik, accessionNumber, primaryDocument);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                String cleanAccessionNumber = accessionNumber.replace("-", "");
                String archiveCik = formatCikForArchivePath(cik);
                String url = String.format("%s/%s/%s/%s", 
                    edgar4JProperties.getUrls().getEdgarDataArchivesUrl(), archiveCik, cleanAccessionNumber, primaryDocument);
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", resolveUserAgent())
                    .header("Accept", "application/xml, text/xml, */*")
                    .header("Accept-Encoding", "gzip, deflate")
                    .timeout(Duration.ofSeconds(30))
                    .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    log.info("Successfully downloaded Form 4 document: {}", accessionNumber);
                    return response.body();
                } else {
                    log.warn("Failed to download Form 4 document: {}. Status: {}", accessionNumber, response.statusCode());
                    return null;
                }
                
            } catch (Exception e) {
                log.error("Error downloading Form 4 document: {}", accessionNumber, e);
                throw new RuntimeException("Failed to download Form 4 document", e);
            }
        });
    }

    @Override
    public CompletableFuture<List<FilingInfo>> getRecentForm4Filings(String cik) {
        log.info("Getting recent Form 4 filings for CIK: {}", cik);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                String formattedCik = formatCik(cik);
                String url = edgar4JProperties.getUrls().getSubmissionsCIKUrl() + formattedCik + ".json";
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", resolveUserAgent())
                    .header("Accept", "application/json")
                    .header("Accept-Encoding", "gzip, deflate")
                    .timeout(Duration.ofSeconds(30))
                    .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    return extractForm4Filings(response.body());
                } else {
                    log.warn("Failed to fetch filings for CIK: {}. Status: {}", cik, response.statusCode());
                    return List.of();
                }
                
            } catch (Exception e) {
                log.error("Error getting recent Form 4 filings for CIK: {}", cik, e);
                return List.of();
            }
        });
    }

    @Override
    public CompletableFuture<Void> processBulkSubmissions() {
        log.info("Processing bulk submissions");
        return CompletableFuture.supplyAsync(() -> {
            List<CompanyTicker> tickers = getCompanyTickers().join();
            if (tickers == null || tickers.isEmpty()) {
                log.info("No tickers found for bulk submissions");
                return null;
            }

            int processed = 0;
            for (CompanyTicker ticker : tickers) {
                if (ticker == null || ticker.getCik() == null || ticker.getCik().isBlank()) {
                    continue;
                }

                try {
                    processCompanySubmissions(ticker.getCik()).get(60, TimeUnit.SECONDS);
                    processed++;
                    if (processed >= MAX_BULK_COMPANIES) {
                        log.warn("Reached bulk processing limit of {} companies; stopping to protect runtime", MAX_BULK_COMPANIES);
                        break;
                    }
                } catch (Exception e) {
                    log.warn("Failed to process bulk submissions for CIK {}: {}", ticker.getCik(), e.getMessage());
                }
            }

            log.info("Finished bulk submissions processing for {} companies", processed);
            return null;
        });
    }

    @Override
    public CompletableFuture<List<CompanyTicker>> getCompanyTickers() {
        log.info("Getting company tickers from SEC");
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(edgar4JProperties.getUrls().getCompanyTickersUrl()))
                    .header("User-Agent", resolveUserAgent())
                    .header("Accept", "application/json")
                    .header("Accept-Encoding", "gzip, deflate")
                    .timeout(Duration.ofSeconds(30))
                    .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    return parseCompanyTickers(response.body());
                } else {
                    log.warn("Failed to fetch company tickers. Status: {}", response.statusCode());
                    return List.of();
                }
                
            } catch (Exception e) {
                log.error("Error getting company tickers", e);
                return List.of();
            }
        });
    }

    /**
     * Process submissions response JSON
     */
    private void processSubmissionsResponse(String jsonResponse, String cik) {
        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            
            // Extract company information
            String companyName = rootNode.path("name").asText();
            String tradingSymbol = rootNode.path("tickers").isArray() && rootNode.path("tickers").size() > 0
                ? rootNode.path("tickers").get(0).asText() : null;
            
            log.info("Company: {} ({}), Ticker: {}", companyName, cik, tradingSymbol);
            
            // Extract recent filings
            JsonNode filings = rootNode.path("filings").path("recent");
            if (filings.isObject()) {
                JsonNode forms = filings.path("form");
                JsonNode accessionNumbers = filings.path("accessionNumber");
                JsonNode filingDates = filings.path("filingDate");
                JsonNode primaryDocuments = filings.path("primaryDocument");
                
                if (forms.isArray() && accessionNumbers.isArray()) {
                    for (int i = 0; i < forms.size(); i++) {
                        String form = forms.get(i).asText();
                        if ("4".equals(form)) {
                            String accessionNumber = accessionNumbers.get(i).asText();
                            String filingDate = textAt(filingDates, i);
                            String primaryDocument = textAt(primaryDocuments, i);
                            if (primaryDocument == null || primaryDocument.isBlank()) {
                                primaryDocument = resolvePrimaryDocument(cik, accessionNumber);
                            }
                            if (primaryDocument == null || primaryDocument.isBlank()) {
                                log.warn("Skipping Form 4 accession {} because no primary XML document was available", accessionNumber);
                                continue;
                            }
                            
                            log.debug("Found Form 4: {} filed on {}", accessionNumber, filingDate);

                            try {
                                String xmlContent = downloadForm4Document(cik, accessionNumber, primaryDocument)
                                    .get(30, TimeUnit.SECONDS);
                                if (xmlContent != null && !xmlContent.isBlank()) {
                                    insiderTransactionService.processForm4Data(xmlContent, accessionNumber);
                                }
                            } catch (Exception e) {
                                log.warn("Failed to process Form 4 accession {} for CIK {}: {}",
                                    accessionNumber, cik, e.getMessage());
                            }
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("Error processing submissions response for CIK: {}", cik, e);
        }
    }

    /**
     * Extract Form 4 filings from submissions JSON
     */
    private List<FilingInfo> extractForm4Filings(String jsonResponse) {
        List<FilingInfo> form4Filings = new ArrayList<>();
        
        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            JsonNode filings = rootNode.path("filings").path("recent");
            
            if (filings.isObject()) {
                JsonNode forms = filings.path("form");
                JsonNode accessionNumbers = filings.path("accessionNumber");
                JsonNode filingDates = filings.path("filingDate");
                JsonNode primaryDocuments = filings.path("primaryDocument");
                
                if (forms.isArray() && accessionNumbers.isArray()) {
                    for (int i = 0; i < forms.size(); i++) {
                        String form = forms.get(i).asText();
                        if ("4".equals(form)) {
                            String accessionNumber = accessionNumbers.get(i).asText();
                            String filingDate = textAt(filingDates, i);
                            String primaryDocument = textAt(primaryDocuments, i);
                            if (primaryDocument == null || primaryDocument.isBlank()) {
                                primaryDocument = resolvePrimaryDocument(rootNode.path("cik").asText(), accessionNumber);
                            }
                            if (primaryDocument == null || primaryDocument.isBlank()) {
                                continue;
                            }
                            
                            String documentUrl = String.format("%s/%s/%s/%s", 
                                edgar4JProperties.getUrls().getEdgarDataArchivesUrl(), 
                                formatCikForArchivePath(rootNode.path("cik").asText()), 
                                accessionNumber.replace("-", ""), 
                                primaryDocument);
                            
                            FilingInfo filingInfo = new FilingInfo(
                                accessionNumber, filingDate, primaryDocument, documentUrl, form);
                            form4Filings.add(filingInfo);
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("Error extracting Form 4 filings", e);
        }
        
        return form4Filings;
    }

    /**
     * Parse company tickers JSON response
     */
    private List<CompanyTicker> parseCompanyTickers(String jsonResponse) {
        List<CompanyTicker> tickers = new ArrayList<>();
        
        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            JsonNode fields = rootNode.path("fields");
            JsonNode data = rootNode.path("data");
            
            if (fields.isArray() && data.isArray()) {
                for (JsonNode tickerData : data) {
                    if (tickerData.isArray() && tickerData.size() >= 4) {
                        String cik = String.valueOf(tickerData.get(0).asLong());
                        String ticker = tickerData.get(1).asText();
                        String title = tickerData.get(2).asText();
                        String exchange = tickerData.get(3).asText();
                        
                        CompanyTicker companyTicker = new CompanyTicker(
                            formatCik(cik), ticker, title, exchange);
                        tickers.add(companyTicker);
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("Error parsing company tickers", e);
        }
        
        return tickers;
    }

    /**
     * Format CIK to 10-digit string with leading zeros
     */
    private String formatCik(String cik) {
        try {
            long cikLong = Long.parseLong(cik);
            return CIK_FORMAT.format(cikLong);
        } catch (NumberFormatException e) {
            log.warn("Invalid CIK format: {}", cik);
            return cik;
        }
    }

    @Override
    public String getForm4Document(String accessionNumber) {
        log.info("Getting Form 4 document for accession number: {}", accessionNumber);
        
        try {
            String cik = extractCikFromAccessionNumber(accessionNumber);
            if (cik == null) {
                log.warn("Could not determine CIK for accession number: {}", accessionNumber);
                return null;
            }

            String primaryDocument = resolvePrimaryDocument(cik, accessionNumber);
            if (primaryDocument == null) {
                log.warn("Could not resolve primary Form 4 XML document for accession number: {}", accessionNumber);
                return null;
            }

            CompletableFuture<String> future = downloadForm4Document(cik, accessionNumber, primaryDocument);
            return future.get(30, TimeUnit.SECONDS);
            
        } catch (Exception e) {
            log.error("Error getting Form 4 document for accession number: {}", accessionNumber, e);
            return null;
        }
    }

    @Override
    public List<String> getForm4FilingsByDateRange(LocalDate startDate, LocalDate endDate) {
        log.info("Getting Form 4 filings from {} to {}", startDate, endDate);
        
        List<String> accessionNumbers = new ArrayList<>();
        
        try {
            LocalDate currentDate = startDate;
            
            while (!currentDate.isAfter(endDate)) {
                List<String> dailyFilings = getForm4FilingsFromDailyIndex(currentDate);
                accessionNumbers.addAll(dailyFilings);
                currentDate = currentDate.plusDays(1);
            }
            
            log.info("Found {} Form 4 filings in date range {} to {}", 
                    accessionNumbers.size(), startDate, endDate);
            
        } catch (Exception e) {
            log.error("Error getting Form 4 filings by date range: {} to {}", startDate, endDate, e);
        }
        
        return accessionNumbers;
    }

    @Override
    public List<String> getForm4FilingsFromDailyIndex(LocalDate date) {
        log.debug("Getting Form 4 filings from daily index for date: {}", date);
        
        List<String> accessionNumbers = new ArrayList<>();
        
        try {
            // Skip weekends
            if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
                return accessionNumbers;
            }
            
            String year = String.valueOf(date.getYear());
            String quarter = "QTR" + ((date.getMonthValue() - 1) / 3 + 1);
            String dateStr = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            
            // SEC daily master index URL format
            String url = String.format("%s/%s/%s/master.%s.idx", 
                edgar4JProperties.getUrls().getEdgarDataArchivesUrl(), year, quarter, dateStr);
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", resolveUserAgent())
                .header("Accept", "text/plain")
                .header("Accept-Encoding", "gzip, deflate")
                .timeout(Duration.ofSeconds(30))
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                accessionNumbers = EdgarForm4ParsingUtils.parseDailyMasterIndex(response.body());
                log.debug("Found {} Form 4 filings in daily index for {}", accessionNumbers.size(), date);
            } else {
                log.debug("Daily master index not available for date: {} (Status: {})", date, response.statusCode());
            }
            
        } catch (Exception e) {
            log.debug("Error getting daily index for date: {} - {}", date, e.getMessage());
        }
        
        return accessionNumbers;
    }
    
    /**
     * Parse daily master index to extract Form 4 accession numbers
     */
    private String extractCikFromAccessionNumber(String accessionNumber) {
        return EdgarForm4ParsingUtils.extractCikFromAccessionNumber(accessionNumber);
    }

    private String textAt(JsonNode arrayNode, int index) {
        if (arrayNode == null || !arrayNode.isArray() || index >= arrayNode.size()) {
            return null;
        }
        return arrayNode.get(index).asText(null);
    }

    private String resolvePrimaryDocument(String cik, String accessionNumber) {
        try {
            String url = String.format("%s/%s/%s/index.json",
                edgar4JProperties.getUrls().getEdgarDataArchivesUrl(),
                formatCikForArchivePath(cik),
                accessionNumber.replace("-", ""));

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", resolveUserAgent())
                .header("Accept", "application/json")
                .header("Accept-Encoding", "gzip, deflate")
                .timeout(Duration.ofSeconds(30))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.debug("Unable to resolve filing index for accession {}. Status: {}", accessionNumber, response.statusCode());
                return null;
            }

            return selectPrimaryXmlDocument(response.body());
        } catch (Exception e) {
            log.debug("Unable to resolve primary document for accession {}", accessionNumber, e);
            return null;
        }
    }

    static String selectPrimaryXmlDocument(String filingIndexJson) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode items = mapper.readTree(filingIndexJson).path("directory").path("item");
            if (!items.isArray()) {
                return null;
            }

            String firstXmlDocument = null;
            for (JsonNode item : items) {
                String name = item.path("name").asText(null);
                if (name == null || !name.toLowerCase().endsWith(".xml")) {
                    continue;
                }

                String lowerName = name.toLowerCase();
                if (lowerName.endsWith(".xsd") || lowerName.equals("filingsummary.xml")) {
                    continue;
                }

                if (lowerName.contains("form4") || lowerName.contains("doc4") || lowerName.contains("ownership")) {
                    return name;
                }

                if (firstXmlDocument == null) {
                    firstXmlDocument = name;
                }
            }

            return firstXmlDocument;
        } catch (Exception e) {
            log.debug("Unable to parse filing index JSON", e);
            return null;
        }
    }

    private String formatCikForArchivePath(String cik) {
        if (cik == null || cik.isBlank()) {
            return cik;
        }

        try {
            return String.valueOf(Long.parseLong(cik));
        } catch (NumberFormatException e) {
            log.warn("Invalid CIK format for archive path: {}", cik);
            return cik;
        }
    }

    private String resolveUserAgent() {
        try {
            String configuredUserAgent = settingsService.getUserAgent();
            if (configuredUserAgent != null && !configuredUserAgent.isBlank()) {
                return configuredUserAgent.trim();
            }
        } catch (Exception e) {
            log.debug("Falling back to default user agent for insider SEC API client", e);
        }
        return DEFAULT_USER_AGENT;
    }
}
