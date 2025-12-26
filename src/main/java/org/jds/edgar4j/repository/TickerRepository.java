package org.jds.edgar4j.repository;

import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.entity.Exchange;
import org.jds.edgar4j.entity.Ticker;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface TickerRepository extends MongoRepository<Ticker, String> {

    Optional<Ticker> findByCode(String code);

    Optional<Ticker> findByCik(String cik);

    List<Ticker> findByCodeIn(List<String> codes);

    List<Ticker> findByCikIn(List<String> ciks);

    List<Ticker> findByExchange(Exchange exchange);

    @Query("{ 'exchange.code': ?0 }")
    List<Ticker> findByExchangeCode(String exchangeCode);

    @Query("{ 'exchange.code': ?0 }")
    Page<Ticker> findByExchangeCode(String exchangeCode, Pageable pageable);

    List<Ticker> findByNameContainingIgnoreCase(String name);

    @Query("{ $or: [ { 'code': { $regex: ?0, $options: 'i' } }, { 'name': { $regex: ?0, $options: 'i' } }, { 'cik': ?0 } ] }")
    Page<Ticker> searchByCodeOrNameOrCik(String searchTerm, Pageable pageable);

    boolean existsByCode(String code);

    boolean existsByCik(String cik);

    long countByExchange(Exchange exchange);

    @Query(value = "{ 'exchange.code': ?0 }", count = true)
    long countByExchangeCode(String exchangeCode);
}
