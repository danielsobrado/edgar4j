package org.jds.edgar4j.repository;

import java.util.Optional;

import org.jds.edgar4j.model.DividendAnalysisSnapshot;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.repository.MongoRepository;

@Profile("resource-high")
public interface DividendAnalysisSnapshotRepository
    extends MongoRepository<DividendAnalysisSnapshot, String> {

    Optional<DividendAnalysisSnapshot> findByCik(String cik);

    Optional<DividendAnalysisSnapshot> findByTickerIgnoreCase(String ticker);
}
