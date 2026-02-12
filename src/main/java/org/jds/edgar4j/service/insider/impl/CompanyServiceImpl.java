package org.jds.edgar4j.service.insider.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jds.edgar4j.model.insider.Company;
import org.jds.edgar4j.repository.insider.CompanyRepository;
import org.jds.edgar4j.repository.insider.InsiderCompanyRelationshipRepository;
import org.jds.edgar4j.repository.insider.InsiderTransactionRepository;
import org.jds.edgar4j.service.insider.CompanyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Implementation of CompanyService for managing company data
 * 
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CompanyServiceImpl implements CompanyService {

    private final CompanyRepository companyRepository;
    private final InsiderTransactionRepository transactionRepository;
    private final InsiderCompanyRelationshipRepository relationshipRepository;

    @Override
    public Company saveCompany(Company company) {
        log.debug("Saving company: {}", company.getCompanyName());
        
        // Ensure CIK is properly formatted
        if (company.getCik() != null) {
            company.setCik(company.getCik());
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
        
        // TODO: Implement market data enrichment
        // This would integrate with external APIs for:
        // - Market cap calculation
        // - Sector and industry classification
        // - Exchange information
        // - SIC code enrichment
        
        return company;
    }

    @Override
    public void updateMarketCap(String cik) {
        log.debug("Updating market cap for CIK: {}", cik);
        
        Optional<Company> companyOpt = findByCik(cik);
        if (companyOpt.isPresent()) {
            Company company = companyOpt.get();
            
            // TODO: Implement market cap calculation
            // This would require:
            // 1. Get current stock price from market data API
            // 2. Calculate market cap = stock price * shares outstanding
            // 3. Update company record
            
            log.debug("Market cap update not implemented yet for: {}", company.getCompanyName());
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
        Long totalTransactions = transactionRepository.countTransactionsByCompany(cik);
        
        // Get active relationships count
        Long activeRelationships = relationshipRepository.countActiveRelationshipsByCompany(cik);
        
        // Get total insiders (unique count from transactions)
        Long totalInsiders = (long) transactionRepository.findByCompanyCik(cik).stream()
            .map(t -> t.getInsider().getCik())
            .distinct()
            .toArray().length;
        
        // Get last filing date
        String lastFilingDate = company.getLastFilingDate() != null 
            ? company.getLastFilingDate().toString() 
            : null;
        
        return new CompanyStatistics(totalInsiders, totalTransactions, activeRelationships, lastFilingDate);
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
