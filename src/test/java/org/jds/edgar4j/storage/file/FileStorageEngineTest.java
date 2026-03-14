package org.jds.edgar4j.storage.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;

class FileStorageEngineTest {

    @TempDir
    Path tempDir;

    @Test
    void flushOnShutdown_persistsBufferedCollectionsWhenFlushOnWriteDisabled() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setBasePath(tempDir.toString());
        properties.setCollectionsPath("collections");
        properties.setIndexOnStartup(true);
        properties.setFlushOnWrite(false);

        ObjectMapper objectMapper = new ObjectMapper();
        FileStorageEngine storageEngine = new FileStorageEngine(properties, objectMapper);
        FileCollection<TestRecord> collection = storageEngine.registerCollection(
                "records",
                TestRecord.class,
                FileFormat.JSONL,
                TestRecord::getId,
                TestRecord::setId);

        collection.save(new TestRecord("1", "persisted"));

        Path filePath = tempDir.resolve("collections").resolve("records.jsonl");
        assertFalse(Files.exists(filePath));

        storageEngine.flushOnShutdown();

        assertTrue(Files.exists(filePath));

        FileStorageEngine reloadedEngine = new FileStorageEngine(properties, objectMapper);
        FileCollection<TestRecord> reloadedCollection = reloadedEngine.registerCollection(
                "records",
                TestRecord.class,
                FileFormat.JSONL,
                TestRecord::getId,
                TestRecord::setId);

        assertEquals("persisted", reloadedCollection.findById("1").orElseThrow().getValue());
    }

    static class TestRecord {

        private String id;
        private String value;

        TestRecord() {
        }

        TestRecord(String id, String value) {
            this.id = id;
            this.value = value;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }
}