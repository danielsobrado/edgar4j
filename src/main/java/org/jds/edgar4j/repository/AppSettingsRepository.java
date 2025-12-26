package org.jds.edgar4j.repository;

import org.jds.edgar4j.entity.AppSettings;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AppSettingsRepository extends MongoRepository<AppSettings, String> {
}
