package org.jds.edgar4j.repository;

import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.model.DividendAlertResolution;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.repository.MongoRepository;

@Profile("resource-high")
public interface DividendAlertResolutionRepository
    extends MongoRepository<DividendAlertResolution, String> {

    Optional<DividendAlertResolution> findByResolutionKey(String resolutionKey);

    List<DividendAlertResolution> findByCik(String cik);
}
