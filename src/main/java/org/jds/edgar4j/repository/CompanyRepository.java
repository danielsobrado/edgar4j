package org.jds.edgar4j.repository;

import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.model.Company;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface CompanyRepository extends MongoRepository<Company, String> {

    Optional<Company> findByCik(String cik);

    Optional<Company> findByTicker(String ticker);

    List<Company> findByTickerIn(List<String> tickers);

    List<Company> findByNameContainingIgnoreCase(String name);

    @Query("{ $or: [ { 'name': { $regex: ?0, $options: 'i' } }, { 'ticker': { $regex: ?0, $options: 'i' } }, { 'cik': ?0 } ] }")
    Page<Company> searchByNameOrTickerOrCik(String searchTerm, Pageable pageable);

    boolean existsByCik(String cik);

    boolean existsByTicker(String ticker);

    long countByTaxonomy(String taxonomy);
}

