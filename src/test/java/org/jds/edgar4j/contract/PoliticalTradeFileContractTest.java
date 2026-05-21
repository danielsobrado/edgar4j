package org.jds.edgar4j.contract;

import java.nio.file.Path;

import org.jds.edgar4j.TestFixtures;
import org.jds.edgar4j.adapter.file.PoliticalTradeFileAdapter;
import org.jds.edgar4j.port.PoliticalTradeDataPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

class PoliticalTradeFileContractTest extends PoliticalTradeDataPortContractTest {

    @TempDir
    Path tempDir;

    private PoliticalTradeDataPort politicalTradeDataPort;

    @BeforeEach
    void setUp() {
        politicalTradeDataPort = new PoliticalTradeFileAdapter(TestFixtures.newFileStorageEngine(tempDir));
        politicalTradeDataPort.deleteAll();
    }

    @Override
    protected PoliticalTradeDataPort port() {
        return politicalTradeDataPort;
    }
}
