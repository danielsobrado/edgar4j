package org.jds.edgar4j.startup;

import java.util.List;

import org.jds.edgar4j.model.Filling;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Profile("resource-high & !resource-low")
@RequiredArgsConstructor
public class MongoFilingIndexCleanup implements ApplicationRunner {

    private static final List<String> INVALID_UNIQUE_INDEXES = List.of(
            "fillingType.number",
            "formType.number",
            "fillingType.number_1",
            "formType.number_1"
    );

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(org.springframework.boot.ApplicationArguments args) {
        IndexOperations indexOperations = mongoTemplate.indexOps(Filling.class);

        for (IndexInfo indexInfo : indexOperations.getIndexInfo()) {
            if (!indexInfo.isUnique()) {
                continue;
            }

            String indexName = indexInfo.getName();
            boolean invalidIndex = INVALID_UNIQUE_INDEXES.contains(indexName)
                    || INVALID_UNIQUE_INDEXES.stream().anyMatch(indexName::contains);
            if (!invalidIndex) {
                continue;
            }

            log.warn("Dropping invalid unique Mongo index '{}' from fillings collection", indexName);
            indexOperations.dropIndex(indexName);
        }
    }
}

