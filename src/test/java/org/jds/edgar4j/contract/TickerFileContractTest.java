package org.jds.edgar4j.contract;

import java.nio.file.Path;

import org.jds.edgar4j.TestFixtures;
import org.jds.edgar4j.adapter.file.TickerFileAdapter;
import org.jds.edgar4j.port.TickerDataPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

class TickerFileContractTest extends TickerDataPortContractTest {

    @TempDir
    Path tempDir;

    private TickerDataPort tickerDataPort;

    @BeforeEach
    void setUp() {
        tickerDataPort = new TickerFileAdapter(TestFixtures.newFileStorageEngine(tempDir));
        tickerDataPort.deleteAll();
    }

    @Override
    protected TickerDataPort port() {
        return tickerDataPort;
    }
}
