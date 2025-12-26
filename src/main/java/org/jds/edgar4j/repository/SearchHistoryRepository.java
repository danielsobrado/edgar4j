package org.jds.edgar4j.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.jds.edgar4j.entity.SearchHistory;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SearchHistoryRepository extends MongoRepository<SearchHistory, String> {

    List<SearchHistory> findTop10ByOrderByTimestampDesc();

    List<SearchHistory> findByType(String type);

    List<SearchHistory> findByTimestampAfter(LocalDateTime timestamp);

    void deleteByTimestampBefore(LocalDateTime timestamp);
}
