package org.jds.edgar4j.startup;

import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.CreateIndexResponse;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.PutMappingRequest;
import org.elasticsearch.xcontent.XContentType;
import org.jds.edgar4j.model.DailyMasterIndex;
import org.jds.edgar4j.model.DownloadHistory;
import org.jds.edgar4j.model.Form4;
import org.jds.edgar4j.model.MasterIndexEntry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

@Component
public class ElasticsearchStartup implements ApplicationRunner {


    @Autowired
    private RestHighLevelClient restHighLevelClient;

    @Override
    public void run(ApplicationArguments args) {
        createIndexAndMappingIfNeeded(MasterIndexEntry.class, "master_index_entry", "mappings/master_index_entry_mapping.json");
        createIndexAndMappingIfNeeded(DailyMasterIndex.class, "daily_master_index", "mappings/daily_master_index_mapping.json");
        createIndexAndMappingIfNeeded(Form4.class, "form4", "mappings/form4_mapping.json");
        createIndexAndMappingIfNeeded(DownloadHistory.class, "download_history", "mappings/download_history_mapping.json");
    }

    private void createIndexAndMappingIfNeeded(Class<?> entityClass, String indexName, String mappingResourcePath) {
        try {
            GetIndexRequest getIndexRequest = new GetIndexRequest(indexName);
            boolean indexExists = restHighLevelClient.indices().exists(getIndexRequest, RequestOptions.DEFAULT);

            if (!indexExists) {
                CreateIndexRequest createIndexRequest = new CreateIndexRequest(indexName);
                CreateIndexResponse createIndexResponse = restHighLevelClient.indices().create(createIndexRequest, RequestOptions.DEFAULT);

                if (createIndexResponse.isAcknowledged()) {
                    String mappingJson = loadResourceAsString(mappingResourcePath);

                    PutMappingRequest putMappingRequest = new PutMappingRequest(indexName).source(mappingJson, XContentType.JSON);
                    restHighLevelClient.indices().putMapping(putMappingRequest, RequestOptions.DEFAULT);
                }
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
