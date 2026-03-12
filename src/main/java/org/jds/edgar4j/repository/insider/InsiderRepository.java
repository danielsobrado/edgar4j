package org.jds.edgar4j.repository.insider;

import org.jds.edgar4j.model.insider.Insider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Insider entities
 * 
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-01-01
 */
@Repository
public interface InsiderRepository extends JpaRepository<Insider, Long> {

    /**
     * Find insider by CIK
     */
    Optional<Insider> findByCik(String cik);

    /**
     * Find insiders by full name (case-insensitive)
     */
    @Query("SELECT i FROM Insider i WHERE LOWER(i.fullName) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<Insider> findByFullNameContainingIgnoreCase(@Param("name") String name);

    /**
     * Find insiders by last name
     */
    List<Insider> findByLastNameIgnoreCase(String lastName);

    /**
     * Find active insiders
     */
    List<Insider> findByIsActiveTrue();

    /**
     * Find insiders by type
     */
    List<Insider> findByInsiderType(Insider.InsiderType insiderType);

    /**
     * Find insiders with recent transactions
     */
    @Query("SELECT i FROM Insider i WHERE i.lastTransactionDate > :since")
    List<Insider> findByLastTransactionDateAfter(@Param("since") LocalDateTime since);

    /**
     * Find insiders by city
     */
    List<Insider> findByCityIgnoreCase(String city);

    /**
     * Find insiders by state
     */
    List<Insider> findByStateIgnoreCase(String state);

    /**
     * Check if insider exists by CIK
     */
    boolean existsByCik(String cik);

    /**
     * Count active insiders
     */
    @Query("SELECT COUNT(i) FROM Insider i WHERE i.isActive = true")
    Long countActiveInsiders();

    /**
     * Find insiders with transactions for specific company
     */
    @Query("SELECT DISTINCT i FROM Insider i JOIN i.insiderTransactions t WHERE t.company.cik = :cik")
    List<Insider> findInsidersWithTransactionsForCompany(@Param("cik") String cik);

    /**
     * Find insiders with active company relationships
     */
    @Query("SELECT DISTINCT i FROM Insider i JOIN i.companyRelationships r WHERE r.isActive = true")
    List<Insider> findInsidersWithActiveRelationships();
}
