package org.jds.edgar4j.contract;

import org.jds.edgar4j.port.Form4DataPort;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(MongoContractTestConfig.class)
@ActiveProfiles({"test", "resource-high", "mongo-contract"})
class Form4MongoContractTest extends Form4DataPortContractTest {

    @Autowired
    private Form4DataPort form4DataPort;

    @BeforeEach
    void setUp() {
        form4DataPort.deleteAll();
    }

    @Override
    protected Form4DataPort port() {
        return form4DataPort;
    }
}
