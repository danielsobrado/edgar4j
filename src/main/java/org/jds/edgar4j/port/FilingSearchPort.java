package org.jds.edgar4j.port;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface FilingSearchPort {

    Page<SearchResult> search(String query, Pageable pageable);

    Page<SearchResult> search(SearchCriteria criteria, Pageable pageable);

    List<String> suggest(String prefix, int maxResults);

    void index(Indexable document);

    void removeFromIndex(String documentId);

    void rebuildIndex();

    record SearchResult(
            String id,
            String type,
            String title,
            String snippet,
            double score,
            LocalDate filingDate) {
    }

    record SearchCriteria(
            String query,
            List<String> formTypes,
            String cik,
            String symbol,
            LocalDate startDate,
            LocalDate endDate) {
    }

    interface Indexable {
        String getSearchId();

        String getSearchType();

        String getSearchContent();

        LocalDate getSearchDate();
    }
}