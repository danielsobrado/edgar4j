package org.jds.edgar4j.adapter.mongo;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.jds.edgar4j.model.Filling;
import org.jds.edgar4j.port.CompanyTickerDataPort;
import org.jds.edgar4j.port.FilingSearchPort;
import org.jds.edgar4j.port.FilingSearchPort.Indexable;
import org.jds.edgar4j.port.FilingSearchPort.SearchCriteria;
import org.jds.edgar4j.port.FilingSearchPort.SearchResult;
import org.jds.edgar4j.port.TickerDataPort;
import org.jds.edgar4j.search.FilingSearchSupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Profile("resource-high")
@ConditionalOnProperty(name = "spring.elasticsearch.repositories.enabled", havingValue = "false", matchIfMissing = true)
public class MongoTextSearchAdapter implements FilingSearchPort {

    private final MongoTemplate mongoTemplate;
    private final TickerDataPort tickerDataPort;
    private final CompanyTickerDataPort companyTickerDataPort;

    public MongoTextSearchAdapter(
            MongoTemplate mongoTemplate,
            TickerDataPort tickerDataPort,
            CompanyTickerDataPort companyTickerDataPort) {
        this.mongoTemplate = mongoTemplate;
        this.tickerDataPort = tickerDataPort;
        this.companyTickerDataPort = companyTickerDataPort;
    }

    @Override
    public Page<SearchResult> search(String query, Pageable pageable) {
        return search(new SearchCriteria(query, List.of(), null, null, null, null), pageable);
    }

    @Override
    public Page<SearchResult> search(SearchCriteria criteria, Pageable pageable) {
        Query pageQuery = buildQuery(criteria, pageable, true);
        Query countQuery = buildQuery(criteria, pageable, false);

        List<Filling> fillings = mongoTemplate.find(pageQuery, Filling.class);
        long total = mongoTemplate.count(countQuery, Filling.class);
        String queryText = effectiveQuery(criteria);

        List<SearchResult> results = fillings.stream()
                .map(filling -> FilingSearchSupport.toSearchResult(filling, queryText))
                .toList();

        return new PageImpl<>(results, pageable, total);
    }

    @Override
    public List<String> suggest(String prefix, int maxResults) {
        String normalizedPrefix = prefix == null ? "" : prefix.trim().toLowerCase(Locale.ROOT);
        Query query = new Query().limit(Math.max(maxResults * 3, 10));
        if (!normalizedPrefix.isBlank()) {
            Pattern pattern = Pattern.compile("^" + Pattern.quote(prefix.trim()), Pattern.CASE_INSENSITIVE);
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("company").regex(pattern),
                    Criteria.where("cik").regex(pattern)));
        }
        query.with(Sort.by(Sort.Direction.DESC, "fillingDate"));

        Set<String> suggestions = new LinkedHashSet<>();
        for (Filling filling : mongoTemplate.find(query, Filling.class)) {
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
        log.debug("Mongo-backed filing search uses persisted filings directly; no extra index update for {}", document.getSearchId());
    }

    @Override
    public void removeFromIndex(String documentId) {
        log.debug("Mongo-backed filing search uses persisted filings directly; no extra index removal for {}", documentId);
    }

    @Override
    public void rebuildIndex() {
        log.debug("Mongo-backed filing search uses persisted filings directly; no rebuild required");
    }

    private Query buildQuery(SearchCriteria criteria, Pageable pageable, boolean includePaging) {
        Query query = new Query();
        List<Criteria> filters = new ArrayList<>();

        if (FilingSearchSupport.hasText(criteria.cik())) {
            filters.add(Criteria.where("cik").is(criteria.cik().trim()));
        }

        List<String> resolvedCiks = FilingSearchSupport.resolveCiks(criteria.symbol(), tickerDataPort, companyTickerDataPort);
        if (FilingSearchSupport.hasText(criteria.symbol())) {
            if (!resolvedCiks.isEmpty()) {
                filters.add(Criteria.where("cik").in(resolvedCiks));
            } else {
                filters.add(textCriteria(criteria.symbol()));
            }
        }

        if (criteria.formTypes() != null && !criteria.formTypes().isEmpty()) {
            List<String> formTypes = criteria.formTypes().stream()
                    .filter(FilingSearchSupport::hasText)
                    .map(String::trim)
                    .toList();
            if (!formTypes.isEmpty()) {
                filters.add(Criteria.where("formType.number").in(formTypes));
            }
        }

        if (criteria.startDate() != null || criteria.endDate() != null) {
            Criteria dateCriteria = Criteria.where("fillingDate");
            if (criteria.startDate() != null) {
                dateCriteria = dateCriteria.gte(java.sql.Date.valueOf(criteria.startDate()));
            }
            if (criteria.endDate() != null) {
                dateCriteria = dateCriteria.lte(java.sql.Date.valueOf(criteria.endDate()));
            }
            filters.add(dateCriteria);
        }

        if (FilingSearchSupport.hasText(criteria.query())) {
            filters.add(textCriteria(criteria.query()));
        }

        if (!filters.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(filters.toArray(Criteria[]::new)));
        }

        if (includePaging && pageable != null) {
            query.with(pageable);
        }
        if (pageable == null || pageable.getSort().isUnsorted()) {
            query.with(Sort.by(Sort.Direction.DESC, "fillingDate"));
        }
        return query;
    }

    private Criteria textCriteria(String query) {
        List<Criteria> tokenCriteria = FilingSearchSupport.tokenize(query).stream()
                .map(this::tokenCriteria)
                .toList();

        if (tokenCriteria.isEmpty()) {
            return new Criteria();
        }
        return tokenCriteria.size() == 1
                ? tokenCriteria.get(0)
                : new Criteria().andOperator(tokenCriteria.toArray(Criteria[]::new));
    }

    private Criteria tokenCriteria(String token) {
        Pattern pattern = Pattern.compile(Pattern.quote(token), Pattern.CASE_INSENSITIVE);
        List<Criteria> anyField = new ArrayList<>();
        anyField.add(Criteria.where("company").regex(pattern));
        anyField.add(Criteria.where("primaryDocument").regex(pattern));
        anyField.add(Criteria.where("primaryDocDescription").regex(pattern));
        anyField.add(Criteria.where("items").regex(pattern));
        anyField.add(Criteria.where("formType.number").regex(pattern));
        anyField.add(Criteria.where("accessionNumber").regex(pattern));
        if (token.chars().allMatch(Character::isDigit)) {
            anyField.add(Criteria.where("cik").is(token));
        } else {
            anyField.add(Criteria.where("cik").regex(pattern));
        }
        return new Criteria().orOperator(anyField.toArray(Criteria[]::new));
    }

    private String effectiveQuery(SearchCriteria criteria) {
        if (FilingSearchSupport.hasText(criteria.query())) {
            return criteria.query();
        }
        if (FilingSearchSupport.hasText(criteria.symbol())) {
            List<String> resolvedCiks = FilingSearchSupport.resolveCiks(criteria.symbol(), tickerDataPort, companyTickerDataPort);
            if (resolvedCiks.isEmpty()) {
                return criteria.symbol();
            }
        }
        return null;
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
