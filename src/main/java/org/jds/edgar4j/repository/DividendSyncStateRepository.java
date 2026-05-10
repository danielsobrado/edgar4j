package org.jds.edgar4j.repository;

import java.util.Optional;

import org.jds.edgar4j.model.DividendSyncState;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.repository.MongoRepository;

@Profile("resource-high")
public interface DividendSyncStateRepository
    extends MongoRepository<DividendSyncState, String> {

    Optional<DividendSyncState> findByCik(String cik);

    Optional<DividendSyncState> findByTickerIgnoreCase(String ticker);
}

