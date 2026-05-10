package org.jds.edgar4j.contract;

import org.jds.edgar4j.adapter.mongo.InsiderCompanyMongoAdapter;
import org.jds.edgar4j.adapter.mongo.InsiderCompanyRelationshipMongoAdapter;
import org.jds.edgar4j.adapter.mongo.InsiderMongoAdapter;
import org.jds.edgar4j.adapter.mongo.InsiderTransactionMongoAdapter;
import org.jds.edgar4j.adapter.mongo.TransactionTypeMongoAdapter;
import org.jds.edgar4j.config.EmbeddedMongoConfig;
import org.jds.edgar4j.config.InsiderMongoIdConfiguration;
import org.jds.edgar4j.config.MongoSequenceService;
import org.jds.edgar4j.repository.insider.CompanyRepository;
import org.jds.edgar4j.repository.insider.InsiderCompanyRelationshipRepository;
import org.jds.edgar4j.repository.insider.InsiderRepository;
import org.jds.edgar4j.repository.insider.InsiderTransactionRepository;
import org.jds.edgar4j.repository.insider.TransactionTypeRepository;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.batch.autoconfigure.BatchAutoConfiguration;
import org.springframework.boot.data.elasticsearch.autoconfigure.DataElasticsearchAutoConfiguration;
import org.springframework.boot.data.elasticsearch.autoconfigure.DataElasticsearchRepositoriesAutoConfiguration;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.elasticsearch.autoconfigure.ElasticsearchClientAutoConfiguration;
import org.springframework.boot.elasticsearch.autoconfigure.ElasticsearchRestClientAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;

@Configuration(proxyBeanMethods = false)
@Profile("mongo-contract")
@EnableAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        DataJpaRepositoriesAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        BatchAutoConfiguration.class,
        ElasticsearchClientAutoConfiguration.class,
        ElasticsearchRestClientAutoConfiguration.class,
        DataElasticsearchAutoConfiguration.class,
        DataElasticsearchRepositoriesAutoConfiguration.class
})
@EnableMongoRepositories(basePackageClasses = {
        CompanyRepository.class,
        InsiderRepository.class,
        InsiderCompanyRelationshipRepository.class,
        InsiderTransactionRepository.class,
        TransactionTypeRepository.class
}, nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
@Import({
        EmbeddedMongoConfig.class,
        MongoSequenceService.class,
        InsiderMongoIdConfiguration.class,
        InsiderMongoAdapter.class,
        InsiderCompanyMongoAdapter.class,
        InsiderCompanyRelationshipMongoAdapter.class,
        InsiderTransactionMongoAdapter.class,
        TransactionTypeMongoAdapter.class
})
class InsiderMongoContractTestConfig {
}
