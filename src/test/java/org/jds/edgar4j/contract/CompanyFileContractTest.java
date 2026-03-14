package org.jds.edgar4j.contract;

import java.nio.file.Path;

import org.jds.edgar4j.TestFixtures;
import org.jds.edgar4j.adapter.file.CompanyFileAdapter;
import org.jds.edgar4j.port.CompanyDataPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

class CompanyFileContractTest extends CompanyDataPortContractTest {

    @TempDir
    Path tempDir;

    private CompanyDataPort companyDataPort;

    @BeforeEach
    void setUp() {
        companyDataPort = new CompanyFileAdapter(TestFixtures.newFileStorageEngine(tempDir));
        companyDataPort.deleteAll();
    }

    @Override
    protected CompanyDataPort port() {
        return companyDataPort;
    }
}
