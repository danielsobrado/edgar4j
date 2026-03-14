package org.jds.edgar4j.contract;

import java.nio.file.Path;

import org.jds.edgar4j.TestFixtures;
import org.jds.edgar4j.adapter.file.Form4FileAdapter;
import org.jds.edgar4j.port.Form4DataPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

class Form4FileContractTest extends Form4DataPortContractTest {

    @TempDir
    Path tempDir;

    private Form4DataPort form4DataPort;

    @BeforeEach
    void setUp() {
        form4DataPort = new Form4FileAdapter(TestFixtures.newFileStorageEngine(tempDir));
        form4DataPort.deleteAll();
    }

    @Override
    protected Form4DataPort port() {
        return form4DataPort;
    }
}
