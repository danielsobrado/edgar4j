package org.jds.edgar4j.repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.jds.edgar4j.model.Ticker;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

public class TickerRepositoryImpl {

    private final MongoOperations mongoOperations;

    public TickerRepositoryImpl(MongoOperations mongoOperations) {
        this.mongoOperations = mongoOperations;
    }

    public Optional<Ticker> findByCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }

        Query query = new Query(Criteria.where("code").regex(exactIgnoreCasePattern(code)));
        return Optional.ofNullable(mongoOperations.findOne(query, Ticker.class));
    }

    public List<Ticker> findByCodeIn(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }

        LinkedHashMap<String, Pattern> patternsByCode = new LinkedHashMap<>();
        for (String code : codes) {
            if (code != null && !code.isBlank()) {
                patternsByCode.putIfAbsent(code.toLowerCase(java.util.Locale.ROOT), exactIgnoreCasePattern(code));
            }
        }
        if (patternsByCode.isEmpty()) {
            return List.of();
        }

        List<Criteria> criteria = patternsByCode.values().stream()
                .map(pattern -> Criteria.where("code").regex(pattern))
                .toList();
        Query query = new Query(new Criteria().orOperator(criteria));
        return mongoOperations.find(query, Ticker.class);
    }

    private Pattern exactIgnoreCasePattern(String code) {
        return Pattern.compile("^" + Pattern.quote(code.trim()) + "$", Pattern.CASE_INSENSITIVE);
    }
}