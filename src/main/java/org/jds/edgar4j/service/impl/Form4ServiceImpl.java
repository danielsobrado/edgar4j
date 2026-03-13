package org.jds.edgar4j.service.impl;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

import org.jds.edgar4j.exception.SecApiException;
import org.jds.edgar4j.integration.Form4Parser;
import org.jds.edgar4j.integration.SecAccessDiagnostics;
import org.jds.edgar4j.integration.SecApiClient;
import org.jds.edgar4j.model.Company;
import org.jds.edgar4j.model.Filling;
import org.jds.edgar4j.model.Form4;
import org.jds.edgar4j.model.Submissions;
import org.jds.edgar4j.storage.DownloadedResourceStore;
import org.jds.edgar4j.model.Ticker;
import org.jds.edgar4j.repository.FillingRepository;
import org.jds.edgar4j.repository.Form4Repository;
import org.jds.edgar4j.repository.TickerRepository;
import org.jds.edgar4j.service.CompanyService;
import org.jds.edgar4j.service.Form4Service;
import org.jds.edgar4j.service.SettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of Form4Service for SEC Form 4 filing operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Form4ServiceImpl implements Form4Service {

    private static final String CACHE_NAMESPACE = "sec-filings";

    private final Form4Repository form4Repository;
    private final Form4Parser form4Parser;
    private final SettingsService settingsService;
    private final SecApiClient secApiClient;
    private final CompanyService companyService;
    private final TickerRepository tickerRepository;
    private final FillingRepository fillingRepository;
    private final DownloadedResourceStore downloadedResourceStore;

    @Value("${edgar4j.urls.edgarDataArchivesUrl}")
    private String edgarDataArchivesUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public CompletableFuture<String> downloadForm4(String cik, String accessionNumber, String primaryDocument) {
        String formUrl = buildFormUrl(cik, accessionNumber, primaryDocument);
        String cached = downloadedResourceStore.readText(CACHE_NAMESPACE, formUrl, java.nio.charset.StandardCharsets.UTF_8)
                .orElse(null);
        if (cached != null) {
            log.debug("Using cached Form 4 for {}", accessionNumber);
            return CompletableFuture.completedFuture(cached);
        }

        log.debug("Downloading Form 4 from: {}", formUrl);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(formUrl))
                .header("User-Agent", settingsService.getUserAgent())
                .header("Accept", "application/xml, text/xml, */*")
                .header("Accept-Encoding", "gzip, deflate")
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    String body = response.body();
                    validateDownloadResponse(formUrl, response.statusCode(), body);
                    if (body != null && !body.isBlank()) {
                        downloadedResourceStore.writeText(
                                CACHE_NAMESPACE,
                                formUrl,
                                body,
                                java.nio.charset.StandardCharsets.UTF_8);
                    }
                    return body;
                })
                .whenComplete((body, error) -> {
                    if (error != null) {
                        log.error("Failed to download Form 4: {}", formUrl, error);
                    } else {
                        log.debug("Downloaded Form 4 content for {}", accessionNumber);
                    }
                });
    }

    @Override
    public Form4 parseForm4(String xml, String accessionNumber) {
        if (xml == null || xml.isBlank()) {
            log.warn("Empty XML content for accession: {}", accessionNumber);
            return null;
        }

        try {
            Form4 form4 = form4Parser.parse(xml, accessionNumber);
            if (form4 != null) {
                Instant now = Instant.now();
                form4.setCreatedAt(now);
                form4.setUpdatedAt(now);
            }
            return form4;
        } catch (Exception e) {
            log.error("Failed to parse Form 4 for accession: {}", accessionNumber, e);
            return null;
        }
    }

    @Override
    public CompletableFuture<Form4> downloadAndParseForm4(String cik, String accessionNumber, String primaryDocument) {
        return downloadForm4(cik, accessionNumber, primaryDocument)
                .thenApply(xml -> parseForm4(xml, accessionNumber))
                .handle((form4, error) -> {
                    if (error == null) {
                        return form4;
                    }

                    Throwable cause = unwrap(error);
                    if (SecAccessDiagnostics.isUndeclaredAutomationBlock(cause)) {
                        throw cause instanceof RuntimeException runtimeException
                                ? runtimeException
                                : new CompletionException(cause);
                    }

                    log.error("Error downloading/parsing Form 4: {}", accessionNumber, cause);
                    return null;
                });
    }

    @Override
    public Form4 save(Form4 form4) {
        if (form4 == null) {
            return null;
        }

        // Check for existing record by accession number
        Optional<Form4> existing = form4Repository.findByAccessionNumber(form4.getAccessionNumber());
        if (existing.isPresent()) {
            Form4 existingForm4 = existing.get();
            form4.setId(existingForm4.getId());
            form4.setCreatedAt(existingForm4.getCreatedAt());
            log.debug("Updating existing Form 4: {}", form4.getAccessionNumber());
        }

        form4.setUpdatedAt(Instant.now());

        try {
            Form4 saved = form4Repository.save(form4);
            log.info("Saved Form 4: {} for {} ({})",
                    saved.getAccessionNumber(),
                    saved.getTradingSymbol(),
                    saved.getRptOwnerName());
            return saved;
        } catch (Exception e) {
            log.error("Failed to save Form 4: {}", form4.getAccessionNumber(), e);
            throw e;
        }
    }

    @Override
    public List<Form4> saveAll(List<Form4> form4List) {
        if (form4List == null || form4List.isEmpty()) {
            return List.of();
        }

        Instant now = Instant.now();
        form4List.forEach(f -> {
            if (f.getCreatedAt() == null) {
                f.setCreatedAt(now);
            }
            f.setUpdatedAt(now);
        });

        try {
            List<Form4> saved = form4Repository.saveAll(form4List);
            log.info("Saved {} Form 4 filings", saved.size());
            return saved;
        } catch (Exception e) {
            log.error("Failed to save Form 4 batch", e);
            throw e;
        }
    }

    @Override
    public Optional<Form4> findByAccessionNumber(String accessionNumber) {
        return form4Repository.findByAccessionNumber(accessionNumber);
    }

    @Override
    public Optional<Form4> findById(String id) {
        return form4Repository.findById(id);
    }

    @Override
    public Page<Form4> findByTradingSymbol(String tradingSymbol, Pageable pageable) {
        Page<Filling> rawPage = resolveCikForSymbol(tradingSymbol)
                .map(cik -> fillingRepository.findByCikAndFormType(cik, "4", toRawPageable(pageable)))
                .orElse(Page.empty(toRawPageable(pageable)));

        if (!rawPage.isEmpty()) {
            return hydrateFromRawFilings(rawPage, pageable, tradingSymbol.toUpperCase());
        }

        return form4Repository.findByTradingSymbol(tradingSymbol, pageable);
    }

    @Override
    public Page<Form4> findByCik(String cik, Pageable pageable) {
        Page<Filling> rawPage = fillingRepository.findByCikAndFormType(cik, "4", toRawPageable(pageable));
        if (!rawPage.isEmpty()) {
            return hydrateFromRawFilings(rawPage, pageable, resolveSymbolForCik(cik).orElse(null));
        }

        return form4Repository.findByCik(cik, pageable);
    }

    @Override
    public List<Form4> findByOwnerName(String ownerName) {
        return form4Repository.findByRptOwnerNameContainingIgnoreCase(ownerName);
    }

    @Override
    public Page<Form4> findByDateRange(LocalDate startDate, LocalDate endDate, Pageable pageable) {
        Page<Filling> rawPage = fillingRepository.searchFillings(
                toDate(startDate, false),
                toDate(endDate, true),
                List.of("4"),
                toRawPageable(pageable));

        if (!rawPage.isEmpty()) {
            return hydrateFromRawFilings(rawPage, pageable, null);
        }

        return form4Repository.findByTransactionDateBetween(startDate, endDate, pageable);
    }

    @Override
    public Page<Form4> findBySymbolAndDateRange(String symbol, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        Page<Filling> rawPage = resolveCikForSymbol(symbol)
                .map(cik -> fillingRepository.findByCikAndFormTypeAndFillingDateBetween(
                        cik,
                        "4",
                        toDate(startDate, false),
                        toDate(endDate, true),
                        toRawPageable(pageable)))
                .orElse(Page.empty(toRawPageable(pageable)));

        if (!rawPage.isEmpty()) {
            return hydrateFromRawFilings(rawPage, pageable, symbol.toUpperCase());
        }

        return form4Repository.findBySymbolAndDateRange(symbol, startDate, endDate, pageable);
    }

    @Override
    public List<Form4> findRecentFilings(int limit) {
        Pageable rawPageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "fillingDate"));
        Page<Filling> rawPage = fillingRepository.findByFormTypeNumber("4", rawPageable);
        if (!rawPage.isEmpty()) {
            return hydrateFromRawFilings(
                    rawPage,
                    PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "transactionDate")),
                    null).getContent();
        }

        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "transactionDate"));
        return form4Repository.findAll(pageable).getContent();
    }

    @Override
    public boolean existsByAccessionNumber(String accessionNumber) {
        return form4Repository.findByAccessionNumber(accessionNumber).isPresent();
    }

    @Override
    public void deleteById(String id) {
        form4Repository.deleteById(id);
        log.info("Deleted Form 4: {}", id);
    }

    @Override
    public InsiderStats getInsiderStats(String tradingSymbol, LocalDate startDate, LocalDate endDate) {
        List<Form4> filings = form4Repository.findByTradingSymbolAndTransactionDateBetween(
                tradingSymbol, startDate, endDate);

        long buys = filings.stream()
                .filter(Form4::isBuy)
                .count();

        long sells = filings.stream()
                .filter(Form4::isSell)
                .count();

        double buyValue = filings.stream()
                .filter(Form4::isBuy)
                .filter(f -> f.getTotalBuyValue() != null)
                .mapToDouble(Form4::getTotalBuyValue)
                .sum();

        double sellValue = filings.stream()
                .filter(Form4::isSell)
                .filter(f -> f.getTotalSellValue() != null)
                .mapToDouble(Form4::getTotalSellValue)
                .sum();

        long directorTx = filings.stream()
                .filter(Form4::isDirector)
                .count();

        long officerTx = filings.stream()
                .filter(Form4::isOfficer)
                .count();

        long tenPercentTx = filings.stream()
                .filter(Form4::isTenPercentOwner)
                .count();

        return new InsiderStats(buys, sells, buyValue, sellValue, directorTx, officerTx, tenPercentTx);
    }

    private Page<Form4> hydrateFromRawFilings(Page<Filling> rawPage, Pageable responsePageable, String fallbackSymbol) {
        List<Filling> rawFilings = rawPage.getContent();
        if (rawFilings.isEmpty()) {
            return Page.empty(responsePageable);
        }

        List<String> accessionNumbers = rawFilings.stream()
                .map(Filling::getAccessionNumber)
                .filter(accession -> accession != null && !accession.isBlank())
                .toList();

        Map<String, Form4> existingByAccession = form4Repository.findByAccessionNumberIn(accessionNumbers).stream()
                .collect(Collectors.toMap(
                        Form4::getAccessionNumber,
                        form4 -> form4,
                        (left, right) -> left,
                        LinkedHashMap::new));

        List<Form4> hydrated = new ArrayList<>();
        for (Filling rawFiling : rawFilings) {
            String accessionNumber = rawFiling.getAccessionNumber();
            if (accessionNumber == null || accessionNumber.isBlank()) {
                continue;
            }

            Form4 form4 = existingByAccession.get(accessionNumber);
            if (form4 == null) {
                form4 = parseAndPersistRawFiling(rawFiling, fallbackSymbol);
                if (form4 != null) {
                    existingByAccession.put(accessionNumber, form4);
                }
            } else {
                form4 = enrichFromRawMetadata(form4, rawFiling, fallbackSymbol);
            }

            if (form4 != null) {
                hydrated.add(form4);
            }
        }

        return new PageImpl<>(hydrated, responsePageable, rawPage.getTotalElements());
    }

    private Form4 parseAndPersistRawFiling(Filling rawFiling, String fallbackSymbol) {
        try {
            Form4 parsed = downloadAndParseForm4(
                    rawFiling.getCik(),
                    rawFiling.getAccessionNumber(),
                    rawFiling.getPrimaryDocument()).join();

            if (parsed == null) {
                log.warn("Failed to parse raw Form 4 filing {}", rawFiling.getAccessionNumber());
                return null;
            }

            return save(enrichFromRawMetadata(parsed, rawFiling, fallbackSymbol));
        } catch (Exception e) {
            log.warn("Failed to hydrate raw Form 4 filing {}: {}", rawFiling.getAccessionNumber(), e.getMessage());
            return form4Repository.findByAccessionNumber(rawFiling.getAccessionNumber()).orElse(null);
        }
    }

    private Form4 enrichFromRawMetadata(Form4 form4, Filling rawFiling, String fallbackSymbol) {
        boolean changed = false;
        String resolvedSymbol = fallbackSymbol;
        if ((resolvedSymbol == null || resolvedSymbol.isBlank()) && rawFiling.getCik() != null) {
            resolvedSymbol = resolveSymbolForCik(rawFiling.getCik()).orElse(null);
        }

        if ((form4.getAccessionNumber() == null || form4.getAccessionNumber().isBlank())
                && rawFiling.getAccessionNumber() != null) {
            form4.setAccessionNumber(rawFiling.getAccessionNumber());
            changed = true;
        }
        if ((form4.getCik() == null || form4.getCik().isBlank()) && rawFiling.getCik() != null) {
            form4.setCik(rawFiling.getCik());
            changed = true;
        }
        if ((form4.getTradingSymbol() == null || form4.getTradingSymbol().isBlank())
                && resolvedSymbol != null && !resolvedSymbol.isBlank()) {
            form4.setTradingSymbol(resolvedSymbol.toUpperCase());
            changed = true;
        }
        if ((form4.getIssuerName() == null || form4.getIssuerName().isBlank())
                && rawFiling.getCompany() != null && !rawFiling.getCompany().isBlank()) {
            form4.setIssuerName(rawFiling.getCompany());
            changed = true;
        }
        if ((form4.getDocumentType() == null || form4.getDocumentType().isBlank())
                && rawFiling.getFormType() != null && rawFiling.getFormType().getNumber() != null) {
            form4.setDocumentType(rawFiling.getFormType().getNumber());
            changed = true;
        }
        if (form4.getPeriodOfReport() == null && rawFiling.getReportDate() != null) {
            form4.setPeriodOfReport(toLocalDate(rawFiling.getReportDate()));
            changed = true;
        }
        if (form4.getTransactionDate() == null && rawFiling.getFillingDate() != null) {
            form4.setTransactionDate(toLocalDate(rawFiling.getFillingDate()));
            changed = true;
        }

        if (changed && form4.getId() != null) {
            return save(form4);
        }
        return form4;
    }

    private Optional<String> resolveCikForSymbol(String tradingSymbol) {
        if (tradingSymbol == null || tradingSymbol.isBlank()) {
            return Optional.empty();
        }

        return tickerRepository.findByCode(tradingSymbol.toUpperCase())
                .map(Ticker::getCik);
    }

    private Optional<String> resolveSymbolForCik(String cik) {
        if (cik == null || cik.isBlank()) {
            return Optional.empty();
        }

        return tickerRepository.findByCik(cik)
                .map(Ticker::getCode);
    }

    private Pageable toRawPageable(Pageable pageable) {
        List<Sort.Order> rawOrders = pageable.getSort().stream()
                .map(order -> new Sort.Order(order.getDirection(), mapRawSortProperty(order.getProperty())))
                .toList();

        Sort rawSort = rawOrders.isEmpty()
                ? Sort.by(Sort.Direction.DESC, "fillingDate")
                : Sort.by(rawOrders);

        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), rawSort);
    }

    private String mapRawSortProperty(String property) {
        return switch (property) {
            case "transactionDate" -> "fillingDate";
            case "periodOfReport" -> "reportDate";
            default -> property;
        };
    }

    private Date toDate(LocalDate value, boolean endOfDay) {
        if (endOfDay) {
            return Date.from(value.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).minusMillis(1));
        }
        return Date.from(value.atStartOfDay().toInstant(ZoneOffset.UTC));
    }

    private LocalDate toLocalDate(Date value) {
        return value.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
    }

    private String buildFormUrl(String cik, String accessionNumber, String primaryDocument) {
        String cleanAccession = accessionNumber.replace("-", "");
        String normalizedCik = cik == null ? "" : cik.replaceFirst("^0+(?!$)", "");
        String normalizedPrimaryDocument = primaryDocument != null && primaryDocument.contains("/")
                ? primaryDocument.substring(primaryDocument.lastIndexOf('/') + 1)
                : primaryDocument;
        return String.format("%s/%s/%s/%s",
                edgarDataArchivesUrl,
                normalizedCik,
                cleanAccession,
                normalizedPrimaryDocument);
    }

    /**
     * Fetches Form 4 filings directly from SEC API when local database is empty.
     * This provides a fallback mechanism for real-time data.
     */
    public List<Form4> fetchFromSecApi(String symbol, LocalDate startDate, LocalDate endDate, int limit) {
        log.info("Fetching Form 4 filings from SEC API for symbol: {} (fallback mode)", symbol);
        List<Form4> form4List = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();
        
        try {
            // First, try to get CIK from local ticker database
            String cik = null;
            var tickerOpt = tickerRepository.findByCode(symbol.toUpperCase());
            if (tickerOpt.isPresent()) {
                cik = tickerOpt.get().getCik();
                log.debug("Found CIK {} for symbol {} in local ticker database", cik, symbol);
            }
            
            // Fallback: try to get CIK from SEC API if not found locally
            if (cik == null) {
                log.debug("CIK not found locally for {}, trying SEC API", symbol);
                String tickersJson = secApiClient.fetchCompanyTickers();
                JsonNode tickersRoot = mapper.readTree(tickersJson);
                
                StringBuilder cikBuilder = new StringBuilder();
                tickersRoot.fields().forEachRemaining(entry -> {
                    JsonNode company = entry.getValue();
                    if (symbol.equalsIgnoreCase(company.path("ticker").asText(""))) {
                        cikBuilder.append(company.path("cik").asText());
                    }
                });
                
                if (cikBuilder.isEmpty()) {
                    log.warn("Could not find CIK for symbol: {}", symbol);
                    return form4List;
                }
                cik = String.format("%010d", Long.parseLong(cikBuilder.toString()));
            }
            
            log.debug("Found CIK {} for symbol {}", cik, symbol);
            
            // Fetch submissions for this CIK
            String submissionsJson = secApiClient.fetchSubmissions(cik);
            log.debug("Submissions response for {}: {}", cik, submissionsJson.substring(0, Math.min(500, submissionsJson.length())));
            JsonNode submissionsRoot = mapper.readTree(submissionsJson);
            
            // SEC submissions JSON uses parallel arrays under filings.recent
            // Structure: { "filings": { "recent": { "form": [...], "accessionNumber": [...], ... } } }
            JsonNode recent = submissionsRoot.path("filings").path("recent");
            JsonNode forms = recent.path("form");
            JsonNode accessionNumbers = recent.path("accessionNumber");
            JsonNode primaryDocuments = recent.path("primaryDocument");
            JsonNode filingDates = recent.path("filingDate");
            
            log.debug("Total filings found in submissions for {}: {}", cik, forms.size());
            
            if (forms.isArray() && forms.size() > 0) {
                int count = 0;
                for (int i = 0; i < forms.size() && count < limit; i++) {
                    // Only process Form 4 filings
                    if (!"4".equals(forms.get(i).asText(""))) {
                        continue;
                    }
                    
                    String accessionNumber = accessionNumbers.get(i).asText("");
                    String primaryDocument = primaryDocuments.get(i).asText("");
                    String filingDate = filingDates.get(i).asText("");
                    
                    if (accessionNumber.isEmpty() || primaryDocument.isEmpty()) {
                        continue;
                    }
                    
                    // Parse the filing date
                    LocalDate filingLocalDate;
                    try {
                        filingLocalDate = LocalDate.parse(filingDate);
                    } catch (Exception e) {
                        continue;
                    }
                    
                    // Filter by date range
                    if (filingLocalDate.isBefore(startDate) || filingLocalDate.isAfter(endDate)) {
                        continue;
                    }
                    
                    // Download and parse the Form 4
                    // Strip XSL stylesheet directory prefix if present (e.g. "xslF345X05/wk-form4.xml")
                    // When primaryDocument has a path prefix, SEC returns HTML not raw XML.
                    String rawPrimaryDocument = primaryDocument.contains("/")
                            ? primaryDocument.substring(primaryDocument.lastIndexOf('/') + 1)
                            : primaryDocument;
                    try {
                        String form4Xml = secApiClient.fetchForm4(cik, accessionNumber, rawPrimaryDocument);
                        Form4 form4 = parseForm4(form4Xml, accessionNumber);
                        if (form4 != null) {
                            if (form4.getTradingSymbol() == null || form4.getTradingSymbol().isBlank()) {
                                form4.setTradingSymbol(symbol);
                            }
                            form4List.add(form4);
                            count++;
                        }
                    } catch (Exception e) {
                        if (SecAccessDiagnostics.isUndeclaredAutomationBlock(e)) {
                            log.warn(
                                    "SEC blocked Form 4 fallback requests while fetching {}. {}",
                                    accessionNumber,
                                    e.getMessage());
                            break;
                        }
                        log.warn("Failed to fetch Form 4: {} - {}", accessionNumber, e.getMessage());
                    }
                }
            }
            
            log.info("Fetched {} Form 4 filings from SEC API for symbol {}", form4List.size(), symbol);
            
        } catch (Exception e) {
            log.error("Error fetching from SEC API for symbol {}: {}", symbol, e.getMessage(), e);
        }
        
        return form4List;
    }
    
    /**
     * Fetches recent Form 4 filings from SEC API when local database is empty.
     */
    public List<Form4> fetchRecentFromSecApi(int limit) {
        log.info("Fetching recent Form 4 filings from SEC API (fallback mode)");
        List<Form4> form4List = new ArrayList<>();
        
        try {
            // Fetch company tickers to iterate through companies
            String tickersJson = secApiClient.fetchCompanyTickers();
            ObjectMapper mapper = new ObjectMapper();
            JsonNode tickersRoot = mapper.readTree(tickersJson);
            
            // SEC company_tickers.json has structure like: {"0": {"ticker": "A", "name": "...", "cik": "0001090872"}, ...}
            // Convert to a list for iteration
            List<JsonNode> companyList = new ArrayList<>();
            tickersRoot.fields().forEachRemaining(entry -> {
                companyList.add(entry.getValue());
            });
            
            if (companyList.isEmpty()) {
                log.warn("No companies found in ticker data");
                return form4List;
            }
            
            // Get recent Form 4 filings by iterating through a sample of companies
            // For a real implementation, you'd use the SEC full-index or RSS feed
            int companiesChecked = 0;
            int maxCompanies = Math.min(companyList.size(), 100); // Limit to avoid rate limiting
            
            for (JsonNode company : companyList) {
                if (form4List.size() >= limit || companiesChecked >= maxCompanies) {
                    break;
                }
                
                String cikStr = company.path("cik").asText();
                String ticker = company.path("ticker").asText("");
                
                if (cikStr.isEmpty()) {
                    continue;
                }
                
                try {
                    // Format CIK to ensure leading zeros
                    String cik = String.format("%010d", Long.parseLong(cikStr));
                    String submissionsJson = secApiClient.fetchSubmissions(cik);
                    JsonNode submissionsRoot = mapper.readTree(submissionsJson);
                    
                    // SEC uses "form4" not "recentForm4"
                    JsonNode recentFilings = submissionsRoot.path("form4");
                    if (recentFilings.isArray() && recentFilings.size() > 0) {
                        // Get the most recent Form 4
                        JsonNode filing = recentFilings.get(0);
                        String accessionNumber = filing.path("accessionNumber").asText("");
                        String primaryDocument = filing.path("primaryDocument").asText("");
                        
                        if (!accessionNumber.isEmpty() && !primaryDocument.isEmpty()) {
                            String form4Xml = secApiClient.fetchForm4(cik, accessionNumber, primaryDocument);
                            Form4 form4 = parseForm4(form4Xml, accessionNumber);
                            if (form4 != null) {
                                form4List.add(form4);
                            }
                        }
                    }
                    
                    companiesChecked++;
                    
                    // Rate limiting - SEC requires 10 requests per second max
                    Thread.sleep(100);
                    
                } catch (Exception e) {
                    if (SecAccessDiagnostics.isUndeclaredAutomationBlock(e)) {
                        log.warn(
                                "SEC blocked recent Form 4 fallback requests while processing {}. {}",
                                ticker,
                                e.getMessage());
                        break;
                    }
                    log.debug("Error processing company {}: {}", ticker, e.getMessage());
                }
            }
            
            log.info("Fetched {} recent Form 4 filings from SEC API", form4List.size());
            
        } catch (Exception e) {
            if (SecAccessDiagnostics.isUndeclaredAutomationBlock(e)) {
                log.warn("SEC blocked recent Form 4 fallback requests. {}", e.getMessage());
                return form4List;
            }
            log.error("Error fetching recent filings from SEC API: {}", e.getMessage(), e);
        }
        
        return form4List;
    }

    private void validateDownloadResponse(String formUrl, int statusCode, String body) {
        if (SecAccessDiagnostics.isUndeclaredAutomationBlock(body)) {
            throw new SecApiException(SecAccessDiagnostics.buildUndeclaredAutomationBlockMessage(
                    formUrl,
                    SecAccessDiagnostics.extractReferenceId(body)));
        }
        if (statusCode == 404) {
            throw new SecApiException("Form 4 resource not found: " + formUrl);
        }
        if (statusCode >= 400) {
            throw new SecApiException("SEC Form 4 download failed with HTTP " + statusCode + " for URL: " + formUrl);
        }
        if (body == null || body.isBlank()) {
            throw new SecApiException("SEC Form 4 response was empty for URL: " + formUrl);
        }
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
