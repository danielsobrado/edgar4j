package org.jds.edgar4j.repository;

import org.jds.edgar4j.model.DailyMasterIndex;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DailyMasterIndexRepository extends ElasticsearchRepository<DailyMasterIndex, String> {
}
