package org.jds.edgar4j.contract;

import org.jds.edgar4j.port.PoliticalTradeDataPort;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(MongoContractTestConfig.class)
@ActiveProfiles({"test", "resource-high", "mongo-contract"})
class PoliticalTradeMongoContractTest extends PoliticalTradeDataPortContractTest {

    @Autowired
    private PoliticalTradeDataPort politicalTradeDataPort;

    @BeforeEach
    void setUp() {
        politicalTradeDataPort.deleteAll();
    }

    @Override
    protected PoliticalTradeDataPort port() {
        return politicalTradeDataPort;
    }
}
