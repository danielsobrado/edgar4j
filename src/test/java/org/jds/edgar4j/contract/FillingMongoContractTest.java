package org.jds.edgar4j.contract;

import org.jds.edgar4j.model.Filling;
import org.jds.edgar4j.port.FillingDataPort;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(MongoContractTestConfig.class)
@ActiveProfiles({"test", "resource-high", "mongo-contract"})
class FillingMongoContractTest extends FillingDataPortContractTest {

    @Autowired
    private FillingDataPort fillingDataPort;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void setUp() {
        mongoTemplate.dropCollection(Filling.class);
    }

    @Override
    protected FillingDataPort port() {
        return fillingDataPort;
    }
}
