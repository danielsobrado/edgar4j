package org.jds.edgar4j.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

@Service
@Profile("resource-high")
@RequiredArgsConstructor
public class MongoSequenceService {

    private final ObjectProvider<MongoOperations> mongoOperationsProvider;

    public long nextId(String sequenceName) {
        MongoOperations mongoOperations = mongoOperationsProvider.getObject();
        Query query = Query.query(Criteria.where("_id").is(sequenceName));
        Update update = new Update().inc("value", 1L);
        FindAndModifyOptions options = FindAndModifyOptions.options().upsert(true).returnNew(true);

        MongoSequence sequence = mongoOperations.findAndModify(query, update, options, MongoSequence.class);
        if (sequence == null) {
            throw new IllegalStateException("Failed to allocate Mongo sequence for " + sequenceName);
        }
        return sequence.getValue();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Document(collection = "mongo_sequences")
    static class MongoSequence {

        @Id
        private String id;

        private long value;
    }
}
