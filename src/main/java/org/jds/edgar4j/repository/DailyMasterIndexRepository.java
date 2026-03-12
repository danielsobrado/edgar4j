package org.jds.edgar4j.repository;

import org.jds.edgar4j.model.DailyMasterIndex;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DailyMasterIndexRepository extends MongoRepository<DailyMasterIndex, String> {
}
