package org.jds.edgar4j.startup;

import org.jds.edgar4j.model.DailyMasterIndex;
import org.jds.edgar4j.model.MasterIndexEntry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

@Component
@ConditionalOnProperty(name = "edgar4j.elasticsearch.startup.enabled", havingValue = "true", matchIfMissing = true)
public class ElasticsearchStartup implements ApplicationRunner {


    private final ElasticsearchOperations elasticsearchOperations;

    @Autowired
    public ElasticsearchStartup(ElasticsearchOperations elasticsearchOperations) {
        this.elasticsearchOperations = elasticsearchOperations;
    }

    @Override
    public void run(ApplicationArguments args) {
        createIndexAndMappingIfNeeded(MasterIndexEntry.class, "master_index_entry", "mappings/master_index_entry_mapping.json");
        createIndexAndMappingIfNeeded(DailyMasterIndex.class, "daily_master_index", "mappings/daily_master_index_mapping.json");
    }

    private void createIndexAndMappingIfNeeded(Class<?> entityClass, String indexName, String mappingResourcePath) {
        try {
            IndexOperations indexOperations = elasticsearchOperations.indexOps(IndexCoordinates.of(indexName));
            boolean indexExists = indexOperations.exists();

            if (!indexExists) {
                indexOperations.create();

                String mappingJson = loadResourceAsString(mappingResourcePath);
                Document mappingDocument = Document.parse(mappingJson);
                indexOperations.putMapping(mappingDocument);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error creating index and mapping for " + entityClass.getSimpleName(), e);
        }
    }

    private String loadResourceAsString(String resourcePath) throws IOException {
        InputStream inputStream = new ClassPathResource(resourcePath).getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder stringBuilder = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            stringBuilder.append(line);
        }

        return stringBuilder.toString();
    }
}
