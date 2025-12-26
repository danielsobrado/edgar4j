package org.jds.edgar4j.repository;

import org.jds.edgar4j.model.MasterIndexEntry;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MasterIndexEntryRepository extends ElasticsearchRepository<MasterIndexEntry, String> {
}
