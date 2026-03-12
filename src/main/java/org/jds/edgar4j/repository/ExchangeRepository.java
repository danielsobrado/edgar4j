package org.jds.edgar4j.repository;

import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.model.Exchange;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExchangeRepository extends MongoRepository<Exchange, String> {

    Optional<Exchange> findByCode(String code);

    Optional<Exchange> findByName(String name);

    List<Exchange> findByCountry(String country);

    boolean existsByCode(String code);

    boolean existsByName(String name);
}

