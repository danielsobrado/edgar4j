package org.jds.edgar4j.contract;

import org.jds.edgar4j.port.TickerDataPort;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(MongoContractTestConfig.class)
@ActiveProfiles({"test", "resource-high", "mongo-contract"})
class TickerMongoContractTest extends TickerDataPortContractTest {

    @Autowired
    private TickerDataPort tickerDataPort;

    @BeforeEach
    void setUp() {
        tickerDataPort.deleteAll();
    }

    @Override
    protected TickerDataPort port() {
        return tickerDataPort;
    }
}
