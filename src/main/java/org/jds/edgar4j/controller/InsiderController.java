package org.jds.edgar4j.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jds.edgar4j.model.insider.Company;
import org.jds.edgar4j.model.insider.Insider;
import org.jds.edgar4j.model.insider.InsiderTransaction;
import org.jds.edgar4j.model.insider.TransactionType;
import org.jds.edgar4j.repository.insider.CompanyRepository;
import org.jds.edgar4j.repository.insider.InsiderRepository;
import org.jds.edgar4j.repository.insider.InsiderTransactionRepository;
import org.jds.edgar4j.repository.insider.TransactionTypeRepository;
import org.jds.edgar4j.service.analytics.InsiderAnalyticsService;
import org.jds.edgar4j.service.enrichment.CompanyEnrichmentService;
import org.jds.edgar4j.service.provider.MarketDataProvider;
import org.jds.edgar4j.service.provider.MarketDataService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * REST Controller for insider trading data operations
 * 
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-01-01
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/insider")
@RequiredArgsConstructor
public class InsiderController {

    private final CompanyRepository companyRepository;
    private final InsiderRepository insiderRepository;
    private final InsiderTransactionRepository transactionRepository;
    private final TransactionTypeRepository transactionTypeRepository;
    
    // Phase 3: Market Data and Analytics Services
    private final MarketDataService marketDataService;
    private final CompanyEnrichmentService companyEnrichmentService;
    private final InsiderAnalyticsService insiderAnalyticsService;

    /**
     * Get all companies
     */
    @GetMapping("/companies")
    public ResponseEntity<List<Company>> getAllCompanies() {
        log.info("Getting all companies");
        List<Company> companies = companyRepository.findAll();
        return ResponseEntity.ok(companies);
    }

    /**
     * Get company by CIK
     */
    @GetMapping("/companies/{cik}")
    public ResponseEntity<Company> getCompanyByCik(@PathVariable String cik) {
        log.info("Getting company by CIK: {}", cik);
        Optional<Company> company = companyRepository.findByCik(cik);
        return company.map(ResponseEntity::ok)
                     .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create a new company
     */
    @PostMapping("/companies")
    public ResponseEntity<Company> createCompany(@RequestBody Company company) {
        log.info("Creating new company: {}", company.getCompanyName());
        Company savedCompany = companyRepository.save(company);
        return ResponseEntity.ok(savedCompany);
    }

    /**
     * Get all insiders
     */
    @GetMapping("/insiders")
    public ResponseEntity<List<Insider>> getAllInsiders() {
        log.info("Getting all insiders");
        List<Insider> insiders = insiderRepository.findAll();
        return ResponseEntity.ok(insiders);
    }

    /**
     * Get insider by CIK
     */
    @GetMapping("/insiders/{cik}")
    public ResponseEntity<Insider> getInsiderByCik(@PathVariable String cik) {
        log.info("Getting insider by CIK: {}", cik);
        Optional<Insider> insider = insiderRepository.findByCik(cik);
        return insider.map(ResponseEntity::ok)
                     .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create a new insider
     */
    @PostMapping("/insiders")
    public ResponseEntity<Insider> createInsider(@RequestBody Insider insider) {
        log.info("Creating new insider: {}", insider.getFullName());
        Insider savedInsider = insiderRepository.save(insider);
        return ResponseEntity.ok(savedInsider);
    }

    /**
     * Get transactions by company CIK
     */
    @GetMapping("/transactions/company/{cik}")
    public ResponseEntity<Page<InsiderTransaction>> getTransactionsByCompany(
            @PathVariable String cik, 
            Pageable pageable) {
        log.info("Getting transactions for company CIK: {}", cik);
        Page<InsiderTransaction> transactions = transactionRepository.findByCompanyCik(cik, pageable);
        return ResponseEntity.ok(transactions);
    }

    /**
     * Get transactions by insider CIK
     */
    @GetMapping("/transactions/insider/{cik}")
    public ResponseEntity<Page<InsiderTransaction>> getTransactionsByInsider(
            @PathVariable String cik, 
            Pageable pageable) {
        log.info("Getting transactions for insider CIK: {}", cik);
        Page<InsiderTransaction> transactions = transactionRepository.findByInsiderCik(cik, pageable);
        return ResponseEntity.ok(transactions);
    }

    /**
     * Get recent transactions
     */
    @GetMapping("/transactions/recent")
    public ResponseEntity<List<InsiderTransaction>> getRecentTransactions(
            @RequestParam(defaultValue = "30") int days) {
        log.info("Getting transactions from last {} days", days);
        LocalDate since = LocalDate.now().minusDays(days);
        List<InsiderTransaction> transactions = transactionRepository.findRecentTransactions(since);
        return ResponseEntity.ok(transactions);
    }

    /**
     * Get transaction by accession number
     */
    @GetMapping("/transactions/{accessionNumber}")
    public ResponseEntity<InsiderTransaction> getTransactionByAccessionNumber(
            @PathVariable String accessionNumber) {
        log.info("Getting transaction by accession number: {}", accessionNumber);
        Optional<InsiderTransaction> transaction = transactionRepository.findByAccessionNumber(accessionNumber);
        return transaction.map(ResponseEntity::ok)
                         .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create a new transaction
     */
    @PostMapping("/transactions")
    public ResponseEntity<InsiderTransaction> createTransaction(@RequestBody InsiderTransaction transaction) {
        log.info("Creating new transaction: {}", transaction.getAccessionNumber());
        InsiderTransaction savedTransaction = transactionRepository.save(transaction);
        return ResponseEntity.ok(savedTransaction);
    }

    /**
     * Get all transaction types
     */
    @GetMapping("/transaction-types")
    public ResponseEntity<List<TransactionType>> getAllTransactionTypes() {
        log.info("Getting all transaction types");
        List<TransactionType> transactionTypes = transactionTypeRepository.findByIsActiveTrueOrderBySortOrder();
        return ResponseEntity.ok(transactionTypes);
    }

    /**
     * Get transaction type by code
     */
    @GetMapping("/transaction-types/{code}")
    public ResponseEntity<TransactionType> getTransactionTypeByCode(@PathVariable String code) {
        log.info("Getting transaction type by code: {}", code);
        Optional<TransactionType> transactionType = transactionTypeRepository.findByTransactionCode(code);
        return transactionType.map(ResponseEntity::ok)
                             .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        log.info("Health check requested");
        return ResponseEntity.ok("Edgar4J Insider Trading API is running");
    }

    /**
     * Get database statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<DatabaseStats> getStats() {
        log.info("Getting database statistics");
        
        long companyCount = companyRepository.count();
        long insiderCount = insiderRepository.count();
        long transactionCount = transactionRepository.count();
        long transactionTypeCount = transactionTypeRepository.count();
        
        DatabaseStats stats = new DatabaseStats(
            companyCount, 
            insiderCount, 
            transactionCount, 
            transactionTypeCount
        );
        
        return ResponseEntity.ok(stats);
    }

    // ==================== PHASE 3: MARKET DATA & ANALYTICS ENDPOINTS ====================

    /**
     * Get current stock price for a symbol
     */
    @GetMapping("/market-data/price/{symbol}")
    public CompletableFuture<ResponseEntity<MarketDataProvider.StockPrice>> getCurrentPrice(@PathVariable String symbol) {
        log.info("Getting current price for symbol: {}", symbol);
        return marketDataService.getCurrentPrice(symbol)
            .thenApply(stockPrice -> stockPrice != null ? 
                ResponseEntity.ok(stockPrice) : 
                ResponseEntity.notFound().build());
    }

    /**
     * Get historical stock prices for a symbol
     */
    @GetMapping("/market-data/history/{symbol}")
    public CompletableFuture<ResponseEntity<List<MarketDataProvider.StockPrice>>> getHistoricalPrices(
            @PathVariable String symbol,
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate) {
        log.info("Getting historical prices for symbol: {} from {} to {}", symbol, startDate, endDate);
        return marketDataService.getHistoricalPrices(symbol, startDate, endDate)
            .thenApply(ResponseEntity::ok);
    }

    /**
     * Get company profile data
     */
    @GetMapping("/market-data/profile/{symbol}")
    public CompletableFuture<ResponseEntity<MarketDataProvider.CompanyProfile>> getCompanyProfile(@PathVariable String symbol) {
        log.info("Getting company profile for symbol: {}", symbol);
        return marketDataService.getCompanyProfile(symbol)
            .thenApply(profile -> profile != null ? 
                ResponseEntity.ok(profile) : 
                ResponseEntity.notFound().build());
    }

    /**
     * Get financial metrics for a symbol
     */
    @GetMapping("/market-data/metrics/{symbol}")
    public CompletableFuture<ResponseEntity<MarketDataProvider.FinancialMetrics>> getFinancialMetrics(@PathVariable String symbol) {
        log.info("Getting financial metrics for symbol: {}", symbol);
        return marketDataService.getFinancialMetrics(symbol)
            .thenApply(metrics -> metrics != null ? 
                ResponseEntity.ok(metrics) : 
                ResponseEntity.notFound().build());
    }

    /**
     * Get enhanced market data (price + profile + metrics)
     */
    @GetMapping("/market-data/enhanced/{symbol}")
    public CompletableFuture<ResponseEntity<MarketDataService.EnhancedMarketData>> getEnhancedMarketData(@PathVariable String symbol) {
        log.info("Getting enhanced market data for symbol: {}", symbol);
        return marketDataService.getEnhancedMarketData(symbol)
            .thenApply(ResponseEntity::ok);
    }

    /**
     * Get price for a specific date
     */
    @GetMapping("/market-data/price/{symbol}/{date}")
    public CompletableFuture<ResponseEntity<BigDecimal>> getPriceForDate(
            @PathVariable String symbol,
            @PathVariable LocalDate date) {
        log.info("Getting price for symbol: {} on date: {}", symbol, date);
        return marketDataService.getPriceForDate(symbol, date)
            .thenApply(price -> price != null ? 
                ResponseEntity.ok(price) : 
                ResponseEntity.notFound().build());
    }

    /**
     * Get provider status
     */
    @GetMapping("/market-data/providers/status")
    public ResponseEntity<Map<String, MarketDataService.ProviderStatus>> getProviderStatus() {
        log.info("Getting provider status");
        Map<String, MarketDataService.ProviderStatus> status = marketDataService.getProviderStatus();
        return ResponseEntity.ok(status);
    }

    /**
     * Enrich company data with market information
     */
    @PostMapping("/enrichment/company/{cik}")
    public CompletableFuture<ResponseEntity<Company>> enrichCompanyData(@PathVariable String cik) {
        log.info("Enriching company data for CIK: {}", cik);
        return companyEnrichmentService.enrichCompanyData(cik)
            .thenApply(company -> company != null ? 
                ResponseEntity.ok(company) : 
                ResponseEntity.notFound().build());
    }

    /**
     * Get enrichment status for a company
     */
    @GetMapping("/enrichment/status/{cik}")
    public ResponseEntity<CompanyEnrichmentService.EnrichmentStatus> getEnrichmentStatus(@PathVariable String cik) {
        log.info("Getting enrichment status for CIK: {}", cik);
        CompanyEnrichmentService.EnrichmentStatus status = companyEnrichmentService.getEnrichmentStatus(cik);
        return ResponseEntity.ok(status);
    }

    /**
     * Enrich all active companies (background process)
     */
    @PostMapping("/enrichment/companies/all")
    public CompletableFuture<ResponseEntity<String>> enrichAllCompanies() {
        log.info("Starting enrichment of all active companies");
        return companyEnrichmentService.enrichAllActiveCompanies()
            .thenApply(v -> ResponseEntity.ok("Company enrichment process started"));
    }

    /**
     * Calculate transaction analytics
     */
    @GetMapping("/analytics/transaction/{accessionNumber}")
    public ResponseEntity<InsiderAnalyticsService.TransactionAnalytics> getTransactionAnalytics(@PathVariable String accessionNumber) {
        log.info("Calculating transaction analytics for accession: {}", accessionNumber);
        Optional<InsiderTransaction> transaction = transactionRepository.findByAccessionNumber(accessionNumber);
        
        if (transaction.isPresent()) {
            InsiderAnalyticsService.TransactionAnalytics analytics = 
                insiderAnalyticsService.calculateTransactionAnalytics(transaction.get());
            return ResponseEntity.ok(analytics);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Calculate company metrics
     */
    @GetMapping("/analytics/company/{cik}")
    public ResponseEntity<InsiderAnalyticsService.CompanyInsiderMetrics> getCompanyMetrics(
            @PathVariable String cik,
            @RequestParam(defaultValue = "90") int days) {
        log.info("Calculating company metrics for CIK: {} over {} days", cik, days);
        
        Optional<Company> company = companyRepository.findByCik(cik);
        if (company.isPresent()) {
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusDays(days);
            
            List<InsiderTransaction> transactions = transactionRepository.findByCompanyCikAndTransactionDateBetween(
                cik, startDate, endDate);
                
            InsiderAnalyticsService.CompanyInsiderMetrics metrics = 
                insiderAnalyticsService.calculateCompanyMetrics(company.get(), transactions, startDate, endDate);
            return ResponseEntity.ok(metrics);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Calculate insider metrics
     */
    @GetMapping("/analytics/insider/{cik}")
    public ResponseEntity<InsiderAnalyticsService.InsiderMetrics> getInsiderMetrics(
            @PathVariable String cik,
            @RequestParam(defaultValue = "90") int days) {
        log.info("Calculating insider metrics for CIK: {} over {} days", cik, days);
        
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days);
        
        List<InsiderTransaction> transactions = transactionRepository.findByInsiderCikAndTransactionDateBetween(
            cik, startDate, endDate);
            
        InsiderAnalyticsService.InsiderMetrics metrics = 
            insiderAnalyticsService.calculateInsiderMetrics(cik, transactions, startDate, endDate);
        return ResponseEntity.ok(metrics);
    }

    /**
     * Database statistics inner class
     */
    public static class DatabaseStats {
        private final long companyCount;
        private final long insiderCount;
        private final long transactionCount;
        private final long transactionTypeCount;

        public DatabaseStats(long companyCount, long insiderCount, 
                           long transactionCount, long transactionTypeCount) {
            this.companyCount = companyCount;
            this.insiderCount = insiderCount;
            this.transactionCount = transactionCount;
            this.transactionTypeCount = transactionTypeCount;
        }

        public long getCompanyCount() { return companyCount; }
        public long getInsiderCount() { return insiderCount; }
        public long getTransactionCount() { return transactionCount; }
        public long getTransactionTypeCount() { return transactionTypeCount; }
    }
}
