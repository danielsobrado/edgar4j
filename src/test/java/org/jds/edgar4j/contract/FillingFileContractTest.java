package org.jds.edgar4j.contract;

import java.nio.file.Path;

import org.jds.edgar4j.TestFixtures;
import org.jds.edgar4j.adapter.file.FillingFileAdapter;
import org.jds.edgar4j.port.FillingDataPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

class FillingFileContractTest extends FillingDataPortContractTest {

    @TempDir
    Path tempDir;

    private FillingDataPort fillingDataPort;

    @BeforeEach
    void setUp() {
        fillingDataPort = new FillingFileAdapter(TestFixtures.newFileStorageEngine(tempDir));
        fillingDataPort.deleteAll();
    }

    @Override
    protected FillingDataPort port() {
        return fillingDataPort;
    }
}
