package org.jds.edgar4j.contract;

import org.jds.edgar4j.adapter.mongo.CompanyMongoAdapter;
import org.jds.edgar4j.adapter.mongo.FillingMongoAdapter;
import org.jds.edgar4j.adapter.mongo.Form4MongoAdapter;
import org.jds.edgar4j.adapter.mongo.TickerMongoAdapter;
import org.jds.edgar4j.config.EmbeddedMongoConfig;
import org.jds.edgar4j.repository.CompanyRepository;
import org.jds.edgar4j.repository.FillingRepository;
import org.jds.edgar4j.repository.Form4Repository;
import org.jds.edgar4j.repository.TickerRepository;
import org.jds.edgar4j.startup.MongoFilingIndexCleanup;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@TestConfiguration(proxyBeanMethods = false)
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
        Form4Repository.class,
        CompanyRepository.class,
        TickerRepository.class,
        FillingRepository.class
})
@Import({
        EmbeddedMongoConfig.class,
        MongoFilingIndexCleanup.class,
        Form4MongoAdapter.class,
        CompanyMongoAdapter.class,
        TickerMongoAdapter.class,
        FillingMongoAdapter.class
})
class MongoContractTestConfig {
}
