package org.jds.edgar4j.repository;

import java.util.List;

import org.jds.edgar4j.model.NormalizedXbrlFact;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.repository.MongoRepository;

@Profile("resource-high")
public interface NormalizedXbrlFactRepository extends MongoRepository<NormalizedXbrlFact, String> {

    List<NormalizedXbrlFact> findByCik(String cik);

    List<NormalizedXbrlFact> findByCikAndStandardConceptAndCurrentBestTrueOrderByPeriodEndDesc(
            String cik,
            String standardConcept);
}
