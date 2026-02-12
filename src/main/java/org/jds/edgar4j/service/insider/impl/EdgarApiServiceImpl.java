package org.jds.edgar4j.service.insider.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jds.edgar4j.properties.Edgar4JProperties;
import org.jds.edgar4j.service.insider.EdgarApiService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build();

    @Value("${edgar4j.urls.submissionsCIKUrl}")
    private String submissionsCIKUrl;

    @Value("${edgar4j.urls.edgarDataArchivesUrl}")
    private String edgarDataArchivesUrl;

    @Value("${edgar4j.urls.companyTickersUrl}")
    private String companyTickersUrl;

    private static final String USER_AGENT = "Edgar4J/1.0 (edgar4j@example.com)";
    private static final DecimalFormat CIK_FORMAT = new DecimalFormat("0000000000");

    @Override
    public CompletableFuture<Void> processCompanySubmissions(String cik) {
        log.info("Processing company submissions for CIK: {}", cik);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                String formattedCik = formatCik(cik);
                String url = submissionsCIKUrl + formattedCik + ".json";
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
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
                String url = String.format("%s/%s/%s/%s", 
                    edgarDataArchivesUrl, cik, cleanAccessionNumber, primaryDocument);
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/xml, text/xml, */*")
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
                String url = submissionsCIKUrl + formattedCik + ".json";
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
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
        log.info("Processing bulk submissions (not yet implemented)");
        
        return CompletableFuture.supplyAsync(() -> {
            // TODO: Implement bulk submissions processing
            // This would involve:
            // 1. Download bulk submissions ZIP file
            // 2. Extract and parse JSON files
            // 3. Process company and filing data
            // 4. Store in database
            
            log.warn("Bulk submissions processing not yet implemented");
            return null;
        });
    }

    @Override
    public CompletableFuture<List<CompanyTicker>> getCompanyTickers() {
        log.info("Getting company tickers from SEC");
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(companyTickersUrl))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
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
                            String filingDate = filingDates.get(i).asText();
                            String primaryDocument = primaryDocuments.get(i).asText();
                            
                            log.debug("Found Form 4: {} filed on {}", accessionNumber, filingDate);
                            
                            // TODO: Process Form 4 filing
                            // This would involve downloading and parsing the Form 4 document
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
                            String filingDate = filingDates.get(i).asText();
                            String primaryDocument = primaryDocuments.get(i).asText();
                            
                            String documentUrl = String.format("%s/%s/%s/%s", 
                                edgarDataArchivesUrl, 
                                rootNode.path("cik").asText(), 
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
            // TODO: Implement logic to find CIK and primary document for the accession number
            // For now, this is a simplified implementation
            // In a full implementation, you would:
            // 1. Query your database to find the CIK and primary document for this accession number
            // 2. Or parse the accession number to extract CIK if following SEC format
            
            // Temporary implementation - extract CIK from accession number pattern
            String cik = extractCikFromAccessionNumber(accessionNumber);
            String primaryDocument = "doc4.xml"; // Default Form 4 document name
            
            if (cik != null) {
                CompletableFuture<String> future = downloadForm4Document(cik, accessionNumber, primaryDocument);
                return future.get(); // Blocking call for synchronous method
            }
            
            log.warn("Could not determine CIK for accession number: {}", accessionNumber);
            return null;
            
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
                edgarDataArchivesUrl, year, quarter, dateStr);
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/plain")
                .timeout(Duration.ofSeconds(30))
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                accessionNumbers = parseDailyMasterIndex(response.body());
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
    private List<String> parseDailyMasterIndex(String indexContent) {
        List<String> accessionNumbers = new ArrayList<>();
        
        try {
            String[] lines = indexContent.split("\\n");
            boolean dataSection = false;
            
            for (String line : lines) {
                // Skip header until we reach the data section
                if (line.startsWith("CIK|Company Name|Form Type|Date Filed|Filename")) {
                    dataSection = true;
                    continue;
                }
                
                if (dataSection && line.trim().length() > 0) {
                    String[] fields = line.split("\\|");
                    
                    if (fields.length >= 5) {
                        String formType = fields[2].trim();
                        
                        if ("4".equals(formType)) {
                            String filename = fields[4].trim();
                            String accessionNumber = extractAccessionFromFilename(filename);
                            
                            if (accessionNumber != null) {
                                accessionNumbers.add(accessionNumber);
                            }
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            log.warn("Error parsing daily master index: {}", e.getMessage());
        }
        
        return accessionNumbers;
    }
    
    /**
     * Extract accession number from filename
     */
    private String extractAccessionFromFilename(String filename) {
        try {
            // Filename format: edgar/data/CIK/ACCESSION-NUMBER/PRIMARY-DOCUMENT
            String[] parts = filename.split("/");
            if (parts.length >= 4) {
                return parts[3]; // ACCESSION-NUMBER part
            }
        } catch (Exception e) {
            log.warn("Error extracting accession number from filename: {}", filename);
        }
        return null;
    }
    
    /**
     * Extract CIK from accession number (simplified implementation)
     */
    private String extractCikFromAccessionNumber(String accessionNumber) {
        try {
            // SEC accession number format: NNNNNNNNNN-NN-NNNNNN
            // First 10 digits are typically the CIK
            if (accessionNumber != null && accessionNumber.length() >= 10) {
                String cikPart = accessionNumber.substring(0, 10);
                return formatCik(cikPart);
            }
        } catch (Exception e) {
            log.warn("Error extracting CIK from accession number: {}", accessionNumber);
        }
        return null;
    }
}
