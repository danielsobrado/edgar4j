package org.jds.edgar4j.port;

import java.util.List;

import org.jds.edgar4j.model.SearchHistory;

public interface SearchHistoryDataPort extends BaseDocumentDataPort<SearchHistory> {

    List<SearchHistory> findTop10ByOrderByTimestampDesc();
}
