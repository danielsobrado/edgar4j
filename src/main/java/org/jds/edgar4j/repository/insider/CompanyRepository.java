package org.jds.edgar4j.repository.insider;

import org.jds.edgar4j.model.insider.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Company entities
 * 
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-01-01
 */
@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {

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
    @Query("SELECT c FROM Company c WHERE LOWER(c.companyName) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<Company> findByCompanyNameContainingIgnoreCase(@Param("name") String name);

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
    @Query("SELECT c FROM Company c WHERE c.lastFilingDate > :since")
    List<Company> findByLastFilingDateAfter(@Param("since") LocalDateTime since);

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
    @Query("SELECT COUNT(c) FROM Company c WHERE c.isActive = true")
    Long countActiveCompanies();

    /**
     * Find companies with insider transactions
     */
    @Query("SELECT DISTINCT c FROM Company c JOIN c.insiderTransactions t WHERE t.transactionDate > :since")
    List<Company> findCompaniesWithTransactionsSince(@Param("since") LocalDateTime since);
}
