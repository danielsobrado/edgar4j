package org.jds.edgar4j.config;

import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jds.edgar4j.model.insider.Company;
import org.jds.edgar4j.model.insider.Insider;
import org.jds.edgar4j.model.insider.InsiderCompanyRelationship;
import org.jds.edgar4j.model.insider.InsiderTransaction;
import org.jds.edgar4j.model.insider.TransactionType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertCallback;

import lombok.RequiredArgsConstructor;

@Configuration
@Profile("resource-high")
@RequiredArgsConstructor
public class InsiderMongoIdConfiguration {

    private final MongoSequenceService sequenceService;

    @Bean
    public BeforeConvertCallback<Insider> insiderIdCallback() {
        return (entity, collection) -> assignIdIfMissing(entity, entity::getId, entity::setId, "insiders");
    }

    @Bean
    public BeforeConvertCallback<Company> insiderCompanyIdCallback() {
        return (entity, collection) -> assignIdIfMissing(entity, entity::getId, entity::setId, "insider_companies");
    }

    @Bean
    public BeforeConvertCallback<InsiderTransaction> insiderTransactionIdCallback() {
        return (entity, collection) -> assignIdIfMissing(entity, entity::getId, entity::setId, "insider_transactions");
    }

    @Bean
    public BeforeConvertCallback<InsiderCompanyRelationship> insiderCompanyRelationshipIdCallback() {
        return (entity, collection) -> assignIdIfMissing(entity, entity::getId, entity::setId, "insider_company_relationships");
    }

    @Bean
    public BeforeConvertCallback<TransactionType> transactionTypeIdCallback() {
        return (entity, collection) -> assignIdIfMissing(entity, entity::getId, entity::setId, "transaction_types");
    }

    private <T> T assignIdIfMissing(T entity, Supplier<Long> idGetter, Consumer<Long> idSetter, String sequenceName) {
        if (idGetter.get() == null) {
            idSetter.accept(sequenceService.nextId(sequenceName));
        }
        return entity;
    }
}
