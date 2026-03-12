package org.jds.edgar4j.repository;

import org.jds.edgar4j.model.MasterIndexEntry;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MasterIndexEntryRepository extends MongoRepository<MasterIndexEntry, String> {
}
