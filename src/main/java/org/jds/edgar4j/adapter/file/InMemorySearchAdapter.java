package org.jds.edgar4j.adapter.file;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jds.edgar4j.model.Filling;
import org.jds.edgar4j.port.CompanyTickerDataPort;
import org.jds.edgar4j.port.FilingSearchPort;
import org.jds.edgar4j.port.FilingSearchPort.Indexable;
import org.jds.edgar4j.port.FilingSearchPort.SearchCriteria;
import org.jds.edgar4j.port.FilingSearchPort.SearchResult;
import org.jds.edgar4j.port.FillingDataPort;
import org.jds.edgar4j.port.TickerDataPort;
import org.jds.edgar4j.search.FilingSearchSupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Profile("resource-low")
@ConditionalOnProperty(name = "edgar4j.search.engine", havingValue = "simple", matchIfMissing = true)
public class InMemorySearchAdapter implements FilingSearchPort {

    private final FillingDataPort fillingDataPort;
    private final TickerDataPort tickerDataPort;
    private final CompanyTickerDataPort companyTickerDataPort;

    public InMemorySearchAdapter(
            @Qualifier("fillingFileAdapter") FillingDataPort fillingDataPort,
            @Qualifier("tickerFileAdapter") TickerDataPort tickerDataPort,
            @Qualifier("companyTickerFileAdapter") CompanyTickerDataPort companyTickerDataPort) {
        this.fillingDataPort = fillingDataPort;
        this.tickerDataPort = tickerDataPort;
        this.companyTickerDataPort = companyTickerDataPort;
    }

    @Override
    public Page<SearchResult> search(String query, Pageable pageable) {
        return search(new SearchCriteria(query, List.of(), null, null, null, null), pageable);
    }

    @Override
    public Page<SearchResult> search(SearchCriteria criteria, Pageable pageable) {
        List<String> resolvedCiks = FilingSearchSupport.resolveCiks(criteria.symbol(), tickerDataPort, companyTickerDataPort);
        boolean symbolFallsBackToText = FilingSearchSupport.hasText(criteria.symbol()) && resolvedCiks.isEmpty();

        List<SearchResult> results = FilingSearchSupport.applyFillingSort(fillingDataPort.findAll(), pageable != null ? pageable.getSort() : null).stream()
                .filter(filling -> matches(filling, criteria, resolvedCiks, symbolFallsBackToText))
                .map(filling -> FilingSearchSupport.toSearchResult(filling, effectiveQuery(criteria, symbolFallsBackToText)))
                .toList();

        return FilingSearchSupport.pageResults(results, pageable);
    }

    @Override
    public List<String> suggest(String prefix, int maxResults) {
        String normalizedPrefix = prefix == null ? "" : prefix.trim().toLowerCase(Locale.ROOT);
        Set<String> suggestions = new LinkedHashSet<>();

        List<Filling> filings = FilingSearchSupport.applyFillingSort(
                fillingDataPort.findAll(),
                Sort.by(Sort.Direction.DESC, "fillingDate"));

        for (Filling filling : filings) {
            addSuggestion(suggestions, filling.getCompany(), normalizedPrefix, maxResults);
            addSuggestion(suggestions, filling.getCik(), normalizedPrefix, maxResults);
            if (suggestions.size() >= maxResults) {
                break;
            }
        }

        return suggestions.stream().limit(maxResults).toList();
    }

    @Override
    public void index(Indexable document) {
        log.debug("In-memory filing search does not maintain a separate index for document {}", document.getSearchId());
    }

    @Override
    public void removeFromIndex(String documentId) {
        log.debug("In-memory filing search does not maintain a separate index for document {}", documentId);
    }

    @Override
    public void rebuildIndex() {
        log.debug("In-memory filing search scans current file-backed filings directly; no rebuild required");
    }

    private boolean matches(Filling filling, SearchCriteria criteria, List<String> resolvedCiks, boolean symbolFallsBackToText) {
        if (!matchesSymbol(filling, criteria.symbol(), resolvedCiks, symbolFallsBackToText)) {
            return false;
        }
        return FilingSearchSupport.matchesFormTypes(filling, criteria.formTypes())
                && FilingSearchSupport.matchesDateRange(filling, criteria.startDate(), criteria.endDate())
                && matchesCik(filling, criteria.cik())
                && FilingSearchSupport.matchesQuery(filling, criteria.query());
    }

    private boolean matchesSymbol(Filling filling, String symbol, List<String> resolvedCiks, boolean symbolFallsBackToText) {
        if (!FilingSearchSupport.hasText(symbol)) {
            return true;
        }
        if (!resolvedCiks.isEmpty()) {
            return resolvedCiks.contains(filling.getCik());
        }
        return !symbolFallsBackToText || FilingSearchSupport.matchesQuery(filling, symbol);
    }

    private boolean matchesCik(Filling filling, String cik) {
        return !FilingSearchSupport.hasText(cik) || cik.trim().equals(filling.getCik());
    }

    private String effectiveQuery(SearchCriteria criteria, boolean symbolFallsBackToText) {
        if (FilingSearchSupport.hasText(criteria.query())) {
            return criteria.query();
        }
        return symbolFallsBackToText ? criteria.symbol() : null;
    }

    private void addSuggestion(Set<String> suggestions, String candidate, String prefix, int maxResults) {
        if (!FilingSearchSupport.hasText(candidate) || suggestions.size() >= maxResults) {
            return;
        }
        if (prefix.isBlank() || candidate.toLowerCase(Locale.ROOT).startsWith(prefix)) {
            suggestions.add(candidate);
        }
    }
}
