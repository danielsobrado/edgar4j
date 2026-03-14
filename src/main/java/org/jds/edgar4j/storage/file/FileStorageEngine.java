package org.jds.edgar4j.storage.file;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

import jakarta.annotation.PreDestroy;

import com.fasterxml.jackson.databind.ObjectMapper;

public class FileStorageEngine {

    private final FileStorageProperties properties;
    private final ObjectMapper objectMapper;
    private final Map<String, FileCollection<?>> collections = new ConcurrentHashMap<>();

    public FileStorageEngine(FileStorageProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @SuppressWarnings("unchecked")
    public <T> FileCollection<T> registerCollection(
            String name,
            Class<T> type,
            FileFormat format,
            Function<T, String> idGetter,
            BiConsumer<T, String> idSetter) {
        return (FileCollection<T>) collections.computeIfAbsent(name, key -> createCollection(key, type, format, idGetter, idSetter));
    }

    public void flushAll() {
        collections.values().forEach(FileCollection::flush);
    }

    @PreDestroy
    public void flushOnShutdown() {
        flushAll();
    }

    public FileStorageProperties getProperties() {
        return properties;
    }

    public List<String> getRegisteredCollectionNames() {
        return collections.keySet().stream().sorted().toList();
    }

    public int getRegisteredCollectionCount() {
        return collections.size();
    }

    public long getTotalRecordsInMemory() {
        return collections.values().stream()
                .mapToLong(FileCollection::count)
                .sum();
    }

    private <T> FileCollection<T> createCollection(
            String name,
            Class<T> type,
            FileFormat format,
            Function<T, String> idGetter,
            BiConsumer<T, String> idSetter) {
        Path baseDir = properties.resolveCollectionsDirectory();
        Path filePath = baseDir.resolve(name + format.extension());
        return new FileCollection<>(
                filePath,
                type,
                objectMapper,
                format,
                idGetter,
                idSetter,
            properties.isIndexOnStartup(),
                properties.isFlushOnWrite());
    }
}
