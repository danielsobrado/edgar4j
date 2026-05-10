package org.jds.edgar4j.repository;

import org.jds.edgar4j.model.AppSettings;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.context.annotation.Profile;

@Profile("resource-high")
@ConditionalOnProperty(prefix = "edgar4j", name = "resource-mode", havingValue = "high", matchIfMissing = true)
public interface AppSettingsRepository extends MongoRepository<AppSettings, String> {
}

