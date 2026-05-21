package org.jds.edgar4j.repository;

import java.util.Optional;

import org.jds.edgar4j.model.PoliticalTrade;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.repository.MongoRepository;

@Profile("resource-high")
public interface PoliticalTradeRepository extends MongoRepository<PoliticalTrade, String> {

    Optional<PoliticalTrade> findBySourceTradeId(String sourceTradeId);

    boolean existsBySourceTradeId(String sourceTradeId);
}
