package org.jds.edgar4j.storage.file;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;

class FileCollectionIndexTest {

    @TempDir
    Path tempDir;

    @Test
    void indexesSupportExactAndIgnoreCaseLookups() {
        FileCollection<TestRecord> collection = newCollection(tempDir.resolve("records.json"), true);
        collection.registerIndex("cik", TestRecord::getCik);
        collection.registerIgnoreCaseIndex("ticker", TestRecord::getTicker);
        collection.registerMultiValueIgnoreCaseIndex("aliases", TestRecord::getAliases);

        TestRecord first = collection.save(new TestRecord(null, "1001", "AcMe"));
        first.setAliases(List.of("Acme", "Acme Corp"));
        collection.save(first);
        TestRecord second = collection.save(new TestRecord(null, "1002", "MSFT"));
        second.setAliases(List.of("Microsoft", "Windows"));
        collection.save(second);

        assertThat(collection.findIndexedFirst("cik", "1001")).contains(first);
        assertThat(collection.findAllIndexed("ticker", "acme")).containsExactly(first);
        assertThat(collection.findIndexedFirst("ticker", "msft")).contains(second);
        assertThat(collection.findAllIndexed("aliases", "acme corp")).containsExactly(first);
        assertThat(collection.findAllIndexed("aliases", "windows")).containsExactly(second);
    }

    @Test
    void indexesStayInSyncAcrossUpdatesAndDeletes() {
        FileCollection<TestRecord> collection = newCollection(tempDir.resolve("records-sync.json"), true);
        collection.registerIndex("cik", TestRecord::getCik);
        collection.registerIgnoreCaseIndex("ticker", TestRecord::getTicker);

        TestRecord record = collection.save(new TestRecord(null, "1001", "OLD"));
        record.setTicker("NEW");
        record.setCik("2002");
        collection.save(record);

        assertThat(collection.findAllIndexed("ticker", "old")).isEmpty();
        assertThat(collection.findIndexedFirst("ticker", "new")).contains(record);
        assertThat(collection.findIndexedFirst("cik", "2002")).contains(record);

        collection.deleteById(record.getId());

        assertThat(collection.findAllIndexed("ticker", "new")).isEmpty();
        assertThat(collection.findIndexedFirst("cik", "2002")).isEmpty();
    }

    @Test
    void indexesCanBeBuiltFromPersistedDataOnStartup() {
        Path filePath = tempDir.resolve("startup.jsonl");

        FileCollection<TestRecord> writer = new FileCollection<>(
                filePath,
                TestRecord.class,
                new ObjectMapper(),
                FileFormat.JSONL,
                TestRecord::getId,
                TestRecord::setId,
                false,
                true);
        writer.saveAll(List.of(
                new TestRecord(null, "3003", "ABC"),
                new TestRecord(null, "4004", "XYZ")));

        FileCollection<TestRecord> reader = new FileCollection<>(
            filePath,
            TestRecord.class,
            new ObjectMapper(),
            FileFormat.JSONL,
            TestRecord::getId,
            TestRecord::setId,
            true,
            true);
        reader.registerIndex("cik", TestRecord::getCik);
        reader.registerIgnoreCaseIndex("ticker", TestRecord::getTicker);

        assertThat(reader.findIndexedFirst("cik", "3003")).get().extracting(TestRecord::getTicker).isEqualTo("ABC");
        assertThat(reader.findIndexedFirst("ticker", "xyz")).get().extracting(TestRecord::getCik).isEqualTo("4004");
    }

    private FileCollection<TestRecord> newCollection(Path filePath, boolean indexOnStartup) {
        return new FileCollection<>(
                filePath,
                TestRecord.class,
                new ObjectMapper(),
                FileFormat.JSON,
                TestRecord::getId,
                TestRecord::setId,
                indexOnStartup,
                true);
    }

    static class TestRecord {

        private String id;
        private String cik;
        private String ticker;
        private List<String> aliases;

        TestRecord() {
        }

        TestRecord(String id, String cik, String ticker) {
            this.id = id;
            this.cik = cik;
            this.ticker = ticker;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getCik() {
            return cik;
        }

        public void setCik(String cik) {
            this.cik = cik;
        }

        public String getTicker() {
            return ticker;
        }

        public void setTicker(String ticker) {
            this.ticker = ticker;
        }

        public List<String> getAliases() {
            return aliases;
        }

        public void setAliases(List<String> aliases) {
            this.aliases = aliases;
        }
    }
}