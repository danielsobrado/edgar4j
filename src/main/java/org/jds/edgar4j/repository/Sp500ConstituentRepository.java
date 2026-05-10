package org.jds.edgar4j.repository;

import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.model.Sp500Constituent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.context.annotation.Profile;

@Profile("resource-high")
public interface Sp500ConstituentRepository extends MongoRepository<Sp500Constituent, String> {

    Optional<Sp500Constituent> findByTickerIgnoreCase(String ticker);

    Optional<Sp500Constituent> findByCik(String cik);

    boolean existsByTickerIgnoreCase(String ticker);

    List<Sp500Constituent> findBySectorOrderByTickerAsc(String sector);

    List<Sp500Constituent> findAllByOrderByTickerAsc();
}

