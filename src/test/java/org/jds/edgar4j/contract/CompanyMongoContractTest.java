package org.jds.edgar4j.contract;

import org.jds.edgar4j.port.CompanyDataPort;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(MongoContractTestConfig.class)
@ActiveProfiles({"test", "resource-high", "mongo-contract"})
class CompanyMongoContractTest extends CompanyDataPortContractTest {

    @Autowired
    private CompanyDataPort companyDataPort;

    @BeforeEach
    void setUp() {
        companyDataPort.deleteAll();
    }

    @Override
    protected CompanyDataPort port() {
        return companyDataPort;
    }
}
