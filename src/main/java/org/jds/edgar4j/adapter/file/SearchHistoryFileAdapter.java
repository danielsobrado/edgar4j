package org.jds.edgar4j.adapter.file;

import java.util.Comparator;
import java.util.List;

import org.jds.edgar4j.model.SearchHistory;
import org.jds.edgar4j.port.SearchHistoryDataPort;
import org.jds.edgar4j.storage.file.FileFormat;
import org.jds.edgar4j.storage.file.FileStorageEngine;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("resource-low")
public class SearchHistoryFileAdapter extends AbstractFileDataPort<SearchHistory> implements SearchHistoryDataPort {

    public SearchHistoryFileAdapter(FileStorageEngine storageEngine) {
        super(storageEngine.registerCollection(
                "search_history",
                SearchHistory.class,
                FileFormat.JSON,
                SearchHistory::getId,
                SearchHistory::setId));
    }

    @Override
    public List<SearchHistory> findTop10ByOrderByTimestampDesc() {
        return findAll().stream()
                .sorted(Comparator.comparing(SearchHistory::getTimestamp, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .toList();
    }
}
