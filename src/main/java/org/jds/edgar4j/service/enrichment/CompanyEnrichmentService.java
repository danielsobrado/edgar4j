package org.jds.edgar4j.service.enrichment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jds.edgar4j.model.insider.Company;
import org.jds.edgar4j.service.insider.CompanyService;
import org.jds.edgar4j.service.provider.MarketDataProvider;
import org.jds.edgar4j.service.provider.MarketDataService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service for enriching company data with market information
 * 
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyEnrichmentService {

    private final CompanyService companyService;
    private final MarketDataService marketDataService;

    /**
     * Enrich company with current market data
     */
    @Async
    @Transactional
    public CompletableFuture<Company> enrichCompanyData(String cik) {
        log.info("Enriching company data for CIK: {}", cik);
        
        return companyService.findByCik(cik)
            .map(company -> enrichCompanyData(company))
            .orElseGet(() -> {
                log.warn("Company not found for CIK: {}", cik);
                return CompletableFuture.completedFuture(null);
            });
    }

    /**
     * Enrich company with current market data
     */
    @Async
    @Transactional
    public CompletableFuture<Company> enrichCompanyData(Company company) {
        if (company == null || company.getTickerSymbol() == null) {
            log.warn("Cannot enrich company data - missing ticker symbol");
            return CompletableFuture.completedFuture(company);
        }
        
        String symbol = company.getTickerSymbol();
        log.debug("Enriching company data for symbol: {}", symbol);
        
        return marketDataService.getEnhancedMarketData(symbol)
            .thenApply(enhancedData -> {
                if (enhancedData != null) {
                    updateCompanyWithMarketData(company, enhancedData);
                    return companyService.save(company);
                } else {
                    log.warn("No market data available for symbol: {}", symbol);
                    return company;
                }
            })
            .exceptionally(throwable -> {
                log.error("Error enriching company data for symbol: {}", symbol, throwable);
                return company;
            });
    }

    /**
     * Enrich multiple companies in batch
     */
    @Async
    public CompletableFuture<Void> enrichAllActiveCompanies() {
        log.info("Starting batch enrichment of all active companies");
        
        return CompletableFuture.runAsync(() -> {
            try {
                List<Company> activeCompanies = companyService.findAllActiveCompanies();
                log.info("Found {} active companies to enrich", activeCompanies.size());
                
                for (Company company : activeCompanies) {
                    if (company.getTickerSymbol() != null) {
                        try {
                            enrichCompanyData(company).get(); // Wait for completion
                            Thread.sleep(1000); // Rate limiting between requests
                        } catch (Exception e) {
                            log.warn("Failed to enrich company {}: {}", company.getTickerSymbol(), e.getMessage());
                        }
                    }
                }
                
                log.info("Completed batch enrichment of all active companies");
                
            } catch (Exception e) {
                log.error("Error during batch company enrichment", e);
            }
        });
    }

    /**
     * Check if company data needs enrichment
     */
    public boolean needsEnrichment(Company company) {
        if (company == null || company.getTickerSymbol() == null) {
            return false;
        }
        
        // Check if data is stale (older than 24 hours)
        if (company.getLastMarketDataUpdate() == null) {
            return true;
        }
        
        return company.getLastMarketDataUpdate().isBefore(LocalDateTime.now().minusHours(24));
    }

    /**
     * Get enrichment status for company
     */
    public EnrichmentStatus getEnrichmentStatus(String cik) {
        return companyService.findByCik(cik)
            .map(this::createEnrichmentStatus)
            .orElse(new EnrichmentStatus(cik, false, "Company not found", null));
    }

    private void updateCompanyWithMarketData(Company company, MarketDataService.EnhancedMarketData enhancedData) {
        log.debug("Updating company {} with enhanced market data", company.getTickerSymbol());
        
        // Update stock price information
        if (enhancedData.hasPrice()) {
            MarketDataProvider.StockPrice stockPrice = enhancedData.getStockPrice();
            company.setCurrentStockPrice(stockPrice.getPrice());
            company.setMarketCap(calculateMarketCap(stockPrice.getPrice(), company.getTotalSharesOutstanding()));
            company.setLastTradingVolume(stockPrice.getVolume());
        }
        
        // Update company profile information
        if (enhancedData.hasProfile()) {
            MarketDataProvider.CompanyProfile profile = enhancedData.getCompanyProfile();
            
            // Update company name if not already set or if different
            if (company.getCompanyName() == null || company.getCompanyName().isEmpty()) {
                company.setCompanyName(profile.getName());
            }
            
            company.setIndustry(profile.getIndustry());
            company.setSector(profile.getSector());
            company.setCountry(profile.getCountry());
            company.setExchange(profile.getExchange());
            company.setWebsite(profile.getWebsite());
            
            if (profile.getMarketCapitalization() != null) {
                company.setMarketCap(BigDecimal.valueOf(profile.getMarketCapitalization()));
            }
            
            if (profile.getSharesOutstanding() != null) {
                company.setTotalSharesOutstanding(BigDecimal.valueOf(profile.getSharesOutstanding()));
            }
        }
        
        // Update financial metrics
        if (enhancedData.hasMetrics()) {
            MarketDataProvider.FinancialMetrics metrics = enhancedData.getFinancialMetrics();
            company.setPeRatio(metrics.getPeRatio());
            company.setPbRatio(metrics.getPriceToBook());
            company.setBeta(metrics.getBeta());
            company.setDividendYield(metrics.getDividendYield());
            company.setFiftyTwoWeekHigh(metrics.getFiftyTwoWeekHigh());
            company.setFiftyTwoWeekLow(metrics.getFiftyTwoWeekLow());
        }
        
        // Update enrichment timestamp
        company.setLastMarketDataUpdate(LocalDateTime.now());
        
        log.debug("Successfully updated company {} with market data", company.getTickerSymbol());
    }

    private BigDecimal calculateMarketCap(BigDecimal stockPrice, BigDecimal sharesOutstanding) {
        if (stockPrice != null && sharesOutstanding != null) {
            return stockPrice.multiply(sharesOutstanding);
        }
        return null;
    }

    private EnrichmentStatus createEnrichmentStatus(Company company) {
        boolean isEnriched = company.getLastMarketDataUpdate() != null;
        boolean needsUpdate = needsEnrichment(company);
        
        String status;
        if (!isEnriched) {
            status = "Not enriched";
        } else if (needsUpdate) {
            status = "Needs update";
        } else {
            status = "Up to date";
        }
        
        return new EnrichmentStatus(
            company.getCik(),
            isEnriched,
            status,
            company.getLastMarketDataUpdate()
        );
    }

    /**
     * Enrichment status information
     */
    public static class EnrichmentStatus {
        private final String cik;
        private final boolean enriched;
        private final String status;
        private final LocalDateTime lastUpdate;

        public EnrichmentStatus(String cik, boolean enriched, String status, LocalDateTime lastUpdate) {
            this.cik = cik;
            this.enriched = enriched;
            this.status = status;
            this.lastUpdate = lastUpdate;
        }

        // Getters
        public String getCik() { return cik; }
        public boolean isEnriched() { return enriched; }
        public String getStatus() { return status; }
        public LocalDateTime getLastUpdate() { return lastUpdate; }
    }
}
