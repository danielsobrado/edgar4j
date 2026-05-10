package org.jds.edgar4j.repository.insider;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.model.insider.Company;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

/**
 * Repository interface for Company entities
 * 
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-01-01
 */
@Profile("resource-high")
public interface CompanyRepository extends MongoRepository<Company, Long> {

    /**
     * Find company by CIK
     */
    Optional<Company> findByCik(String cik);

    /**
     * Find company by ticker symbol
     */
    Optional<Company> findByTickerSymbol(String tickerSymbol);

    /**
     * Find companies by name (case-insensitive)
     */
    List<Company> findByCompanyNameContainingIgnoreCase(String name);

    /**
     * Find active companies
     */
    List<Company> findByIsActiveTrue();

    /**
     * Find companies by exchange
     */
    List<Company> findByExchange(String exchange);

    /**
     * Find companies by sector
     */
    List<Company> findBySector(String sector);

    /**
     * Find companies with recent filings
     */
    List<Company> findByLastFilingDateAfter(LocalDateTime since);

    /**
     * Find companies by SIC code
     */
    List<Company> findBySicCode(String sicCode);

    /**
     * Check if company exists by CIK
     */
    boolean existsByCik(String cik);

    /**
     * Check if company exists by ticker symbol
     */
    boolean existsByTickerSymbol(String tickerSymbol);

    /**
     * Count active companies
     */
    @Query(value = "{ 'isActive': true }", count = true)
    Long countActiveCompanies();
}

