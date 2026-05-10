package org.jds.edgar4j.repository.insider;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.model.insider.Insider;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

/**
 * Repository interface for Insider entities
 * 
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-01-01
 */
@Profile("resource-high")
public interface InsiderRepository extends MongoRepository<Insider, Long> {

    /**
     * Find insider by CIK
     */
    Optional<Insider> findByCik(String cik);

    /**
     * Find insiders by full name (case-insensitive)
     */
    List<Insider> findByFullNameContainingIgnoreCase(String name);

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
    List<Insider> findByLastTransactionDateAfter(LocalDateTime since);

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
    @Query(value = "{ 'isActive': true }", count = true)
    Long countActiveInsiders();
}
