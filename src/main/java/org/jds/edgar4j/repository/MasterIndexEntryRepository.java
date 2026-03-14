package org.jds.edgar4j.repository;

import org.jds.edgar4j.model.MasterIndexEntry;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.context.annotation.Profile;

@Profile("resource-high & !resource-low")
public interface MasterIndexEntryRepository extends MongoRepository<MasterIndexEntry, String> {
}
