package org.jds.edgar4j.repository;

import org.jds.edgar4j.model.CompanyTicker;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.context.annotation.Profile;

import java.util.List;
import java.util.Optional;

/**
 * Repository for the {@code company_tickers} collection.
 *
 * <p>Provides fast CIK Ã¢â€ â€ Ticker cross-lookups without loading the full
 * {@code submissions} documents.
 */
@Profile("resource-high")
public interface CompanyTickerRepository extends MongoRepository<CompanyTicker, String> {

    /** Find by exact ticker (case-sensitive, as stored in the collection). */
    Optional<CompanyTicker> findByTicker(String ticker);

    /** Find by ticker, ignoring case Ã¢â‚¬â€ useful for user-entered symbols. */
    Optional<CompanyTicker> findByTickerIgnoreCase(String ticker);

    /** Find all entries for a given numeric CIK value. */
    @Query("{ 'cik_str': ?0 }")
    List<CompanyTicker> findByCikStr(Long cikStr);

    /** Return the first (and usually only) entry for a numeric CIK. */
    @Query("{ 'cik_str': ?0 }")
    Optional<CompanyTicker> findFirstByCikStr(Long cikStr);

    /** Case-insensitive prefix search Ã¢â‚¬â€ useful for autocomplete. */
    List<CompanyTicker> findByTickerStartingWithIgnoreCase(String prefix);

    /** Full-text-style title search for autocomplete. */
    List<CompanyTicker> findByTitleContainingIgnoreCase(String title);
}

