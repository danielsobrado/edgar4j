package org.jds.edgar4j.repository;

import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.model.CompanyMarketData;
import org.jds.edgar4j.port.CompanyMarketDataDataPort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CompanyMarketDataRepository extends MongoRepository<CompanyMarketData, String>, CompanyMarketDataDataPort {

    Optional<CompanyMarketData> findByTickerIgnoreCase(String ticker);

    List<CompanyMarketData> findByTickerIn(List<String> tickers);

    List<CompanyMarketData> findByMarketCapGreaterThanEqual(Double minMarketCap);

    boolean existsByTickerIgnoreCase(String ticker);
}
