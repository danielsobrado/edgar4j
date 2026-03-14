package org.jds.edgar4j.storage.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;

class FileCollectionConcurrencyTest {

    @TempDir
    Path tempDir;

    @Test
    void concurrentReadWrite_noCorruption() throws Exception {
        FileCollection<TestRecord> collection = new FileCollection<>(
                tempDir.resolve("concurrency.jsonl"),
                TestRecord.class,
                new ObjectMapper(),
                FileFormat.JSONL,
                TestRecord::getId,
                TestRecord::setId,
                true,
                true);
        collection.registerIndex("group", TestRecord::getGroup);

        int writerThreads = 10;
        int readerThreads = 20;
        int recordsPerWriter = 25;
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger errors = new AtomicInteger();

        ExecutorService executor = Executors.newFixedThreadPool(writerThreads + readerThreads);
        List<Callable<Void>> tasks = new ArrayList<>();

        for (int writer = 0; writer < writerThreads; writer++) {
            int writerNumber = writer;
            tasks.add(() -> {
                await(start);
                for (int index = 0; index < recordsPerWriter; index++) {
                    try {
                        collection.save(new TestRecord(
                                "writer-" + writerNumber + "-record-" + index,
                                "group-" + writerNumber));
                    } catch (Exception exception) {
                        errors.incrementAndGet();
                    }
                }
                return null;
            });
        }

        for (int reader = 0; reader < readerThreads; reader++) {
            int readerNumber = reader;
            tasks.add(() -> {
                await(start);
                for (int iteration = 0; iteration < recordsPerWriter; iteration++) {
                    try {
                        collection.findAll();
                        collection.count();
                        collection.findAllIndexed("group", "group-" + (readerNumber % writerThreads));
                    } catch (Exception exception) {
                        errors.incrementAndGet();
                    }
                }
                return null;
            });
        }

        List<Future<Void>> futures = tasks.stream().map(executor::submit).toList();
        start.countDown();

        for (Future<Void> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));

        assertEquals(0, errors.get());
        assertEquals(writerThreads * recordsPerWriter, collection.count());
        assertEquals(recordsPerWriter, collection.findAllIndexed("group", "group-0").size());
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    static class TestRecord {

        private String id;
        private String group;

        TestRecord() {
        }

        TestRecord(String id, String group) {
            this.id = id;
            this.group = group;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getGroup() {
            return group;
        }

        public void setGroup(String group) {
            this.group = group;
        }
    }
}
