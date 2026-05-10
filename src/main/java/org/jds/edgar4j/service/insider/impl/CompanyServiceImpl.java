package org.jds.edgar4j.service.insider.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jds.edgar4j.model.insider.Company;
import org.jds.edgar4j.port.InsiderCompanyDataPort;
import org.jds.edgar4j.port.InsiderCompanyRelationshipDataPort;
import org.jds.edgar4j.port.InsiderTransactionDataPort;
import org.jds.edgar4j.service.insider.CompanyService;
import org.jds.edgar4j.service.provider.MarketDataProvider;
import org.jds.edgar4j.service.provider.MarketDataService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of CompanyService for managing company data
 * 
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-01-01
 */
@Slf4j
@Service("insiderCompanyService")
@RequiredArgsConstructor
@Transactional
public class CompanyServiceImpl implements CompanyService {

    private final InsiderCompanyDataPort companyRepository;
    private final InsiderTransactionDataPort transactionRepository;
    private final InsiderCompanyRelationshipDataPort relationshipRepository;
    private final MarketDataService marketDataService;

    @Override
    public Company saveCompany(Company company) {
        log.debug("Saving company: {}", company.getCompanyName());
        
        if (company.getCik() != null) {
            company.setCik(formatCik(company.getCik()));
        }
        
        return companyRepository.save(company);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Company> findByCik(String cik) {
        log.debug("Finding company by CIK: {}", cik);
        String formattedCik = formatCik(cik);
        return companyRepository.findByCik(formattedCik);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Company> findByTickerSymbol(String tickerSymbol) {
        log.debug("Finding company by ticker symbol: {}", tickerSymbol);
        if (tickerSymbol == null || tickerSymbol.isBlank()) {
            return Optional.empty();
        }
        return companyRepository.findByTickerSymbol(tickerSymbol.toUpperCase());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Company> findByCompanyName(String name) {
        log.debug("Finding companies by name: {}", name);
        return companyRepository.findByCompanyNameContainingIgnoreCase(name);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Company> findActiveCompanies() {
        log.debug("Finding all active companies");
        return companyRepository.findByIsActiveTrue();
    }

    @Override
    public Company createFromSecData(String cik, String companyName, String tickerSymbol) {
        log.info("Creating company from SEC data - CIK: {}, Name: {}, Ticker: {}", 
                cik, companyName, tickerSymbol);
        
        Company company = Company.builder()
            .cik(formatCik(cik))
            .companyName(companyName)
            .tickerSymbol(tickerSymbol != null ? tickerSymbol.toUpperCase() : null)
            .isActive(true)
            .build();
        
        return saveCompany(company);
    }

    @Override
    public Company updateCompanyInfo(String cik, String companyName, String tickerSymbol, String exchange) {
        log.info("Updating company info - CIK: {}", cik);
        
        Optional<Company> existingCompany = findByCik(cik);
        Company company;
        
        if (existingCompany.isPresent()) {
            company = existingCompany.get();
            company.setCompanyName(companyName);
            company.setTickerSymbol(tickerSymbol != null ? tickerSymbol.toUpperCase() : null);
            company.setExchange(exchange);
        } else {
            company = Company.builder()
                .cik(formatCik(cik))
                .companyName(companyName)
                .tickerSymbol(tickerSymbol != null ? tickerSymbol.toUpperCase() : null)
                .exchange(exchange)
                .isActive(true)
                .build();
        }
        
        return saveCompany(company);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean companyExists(String cik) {
        log.debug("Checking if company exists - CIK: {}", cik);
        String formattedCik = formatCik(cik);
        return companyRepository.existsByCik(formattedCik);
    }

    @Override
    public Company getOrCreateCompany(String cik, String companyName, String tickerSymbol) {
        log.debug("Getting or creating company - CIK: {}", cik);
        
        String formattedCik = formatCik(cik);
        Optional<Company> existingCompany = companyRepository.findByCik(formattedCik);
        
        if (existingCompany.isPresent()) {
            Company company = existingCompany.get();
            
            // Update company information if provided data is more complete
            boolean updated = false;
            
            if (companyName != null && !companyName.equals(company.getCompanyName())) {
                company.setCompanyName(companyName);
                updated = true;
            }
            
            if (tickerSymbol != null && !tickerSymbol.equalsIgnoreCase(company.getTickerSymbol())) {
                company.setTickerSymbol(tickerSymbol.toUpperCase());
                updated = true;
            }
            
            if (updated) {
                return saveCompany(company);
            }
            
            return company;
        } else {
            return createFromSecData(cik, companyName, tickerSymbol);
        }
    }

    @Override
    public Company enrichCompanyData(Company company) {
        log.debug("Enriching company data for: {}", company.getCompanyName());
        
        if (company == null || company.getTickerSymbol() == null || company.getTickerSymbol().isBlank()) {
            return company;
        }

        try {
            MarketDataService.EnhancedMarketData enhancedData = marketDataService
                .getEnhancedMarketData(company.getTickerSymbol())
                .get(30, TimeUnit.SECONDS);

            if (enhancedData == null) {
                return company;
            }

            applyMarketData(company, enhancedData);
            return saveCompany(company);
        } catch (Exception e) {
            log.warn("Unable to enrich company data for {}: {}", company.getTickerSymbol(), e.getMessage());
        }

        return company;
    }

    @Override
    public void updateMarketCap(String cik) {
        log.debug("Updating market cap for CIK: {}", cik);
        
        Optional<Company> companyOpt = findByCik(cik);
        if (companyOpt.isPresent()) {
            Company company = companyOpt.get();
            
            if (company.getTickerSymbol() == null || company.getTickerSymbol().isBlank()) {
                log.debug("Skipping market cap update for {} because no ticker symbol is available", company.getCompanyName());
                return;
            }

            try {
                MarketDataProvider.StockPrice stockPrice = marketDataService
                    .getCurrentPrice(company.getTickerSymbol())
                    .get(30, TimeUnit.SECONDS);

                if (stockPrice == null || stockPrice.getPrice() == null) {
                    log.debug("No current price available for market cap update: {}", company.getTickerSymbol());
                    return;
                }

                company.setCurrentStockPrice(stockPrice.getPrice());
                if (stockPrice.getVolume() != null) {
                    company.setLastTradingVolume(stockPrice.getVolume());
                }
                if (stockPrice.getMarketCap() != null) {
                    company.setMarketCap(BigDecimal.valueOf(stockPrice.getMarketCap()));
                } else {
                    company.calculateMarketCap(stockPrice.getPrice());
                }
                company.setLastMarketDataUpdate(LocalDateTime.now());
                saveCompany(company);
            } catch (Exception e) {
                log.warn("Unable to update market cap for {}: {}", company.getTickerSymbol(), e.getMessage());
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public CompanyStatistics getCompanyStatistics(String cik) {
        log.debug("Getting company statistics for CIK: {}", cik);
        
        Optional<Company> companyOpt = findByCik(cik);
        if (companyOpt.isEmpty()) {
            return new CompanyStatistics(0L, 0L, 0L, null);
        }
        
        Company company = companyOpt.get();
        
        // Get transaction count
        String formattedCik = formatCik(cik);
        Long totalTransactions = transactionRepository.countTransactionsByCompany(formattedCik);
        
        // Get active relationships count
        Long activeRelationships = relationshipRepository.countActiveRelationshipsByCompany(formattedCik);
        
        // Get total insiders (unique count from transactions)
        Long totalInsiders = (long) transactionRepository.findByCompanyCik(formattedCik).stream()
            .filter(t -> t.getInsider() != null && t.getInsider().getCik() != null)
            .map(t -> t.getInsider().getCik())
            .distinct()
            .count();
        
        // Get last filing date
        String lastFilingDate = company.getLastFilingDate() != null 
            ? company.getLastFilingDate().toString() 
            : null;
        
        return new CompanyStatistics(totalInsiders, totalTransactions, activeRelationships, lastFilingDate);
    }

    private void applyMarketData(Company company, MarketDataService.EnhancedMarketData enhancedData) {
        if (enhancedData.hasPrice()) {
            MarketDataProvider.StockPrice stockPrice = enhancedData.getStockPrice();
            company.setCurrentStockPrice(stockPrice.getPrice());
            company.setLastTradingVolume(stockPrice.getVolume());
            if (stockPrice.getMarketCap() != null) {
                company.setMarketCap(BigDecimal.valueOf(stockPrice.getMarketCap()));
            } else {
                company.calculateMarketCap(stockPrice.getPrice());
            }
        }

        if (enhancedData.hasProfile()) {
            MarketDataProvider.CompanyProfile profile = enhancedData.getCompanyProfile();
            if (profile.getName() != null && !profile.getName().isBlank()) {
                company.setCompanyName(profile.getName());
            }
            company.setIndustry(profile.getIndustry());
            company.setSector(profile.getSector());
            company.setCountry(profile.getCountry());
            company.setExchange(profile.getExchange());
            company.setWebsite(profile.getWebsite());
            if (profile.getSharesOutstanding() != null) {
                company.setTotalSharesOutstanding(BigDecimal.valueOf(profile.getSharesOutstanding()));
            }
            if (profile.getMarketCapitalization() != null) {
                company.setMarketCap(BigDecimal.valueOf(profile.getMarketCapitalization()));
            } else if (company.getCurrentStockPrice() != null) {
                company.calculateMarketCap(company.getCurrentStockPrice());
            }
        }

        if (enhancedData.hasMetrics()) {
            MarketDataProvider.FinancialMetrics metrics = enhancedData.getFinancialMetrics();
            company.setPeRatio(metrics.getPeRatio());
            company.setPbRatio(metrics.getPriceToBook());
            company.setBeta(metrics.getBeta());
            company.setDividendYield(metrics.getDividendYield());
            company.setFiftyTwoWeekHigh(metrics.getFiftyTwoWeekHigh());
            company.setFiftyTwoWeekLow(metrics.getFiftyTwoWeekLow());
        }

        company.setLastMarketDataUpdate(LocalDateTime.now());
    }

    /**
     * Format CIK to 10-digit string with leading zeros
     */
    private String formatCik(String cik) {
        if (cik == null || cik.isEmpty()) {
            return cik;
        }
        
        try {
            long cikLong = Long.parseLong(cik);
            return String.format("%010d", cikLong);
        } catch (NumberFormatException e) {
            log.warn("Invalid CIK format: {}", cik);
            return cik;
        }
    }
}
