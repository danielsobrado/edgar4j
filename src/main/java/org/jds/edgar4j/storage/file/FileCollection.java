package org.jds.edgar4j.storage.file;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

public class FileCollection<T> {

    private final Path filePath;
    private final Class<T> type;
    private final ObjectMapper objectMapper;
    private final FileFormat format;
    private final Function<T, String> idGetter;
    private final BiConsumer<T, String> idSetter;
    private final boolean flushOnWrite;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final JavaType listType;

    private List<T> records;

    public FileCollection(
            Path filePath,
            Class<T> type,
            ObjectMapper objectMapper,
            FileFormat format,
            Function<T, String> idGetter,
            BiConsumer<T, String> idSetter,
            boolean flushOnWrite) {
        this.filePath = filePath;
        this.type = type;
        this.objectMapper = objectMapper;
        this.format = format;
        this.idGetter = idGetter;
        this.idSetter = idSetter;
        this.flushOnWrite = flushOnWrite;
        this.listType = objectMapper.getTypeFactory().constructCollectionType(List.class, type);
    }

    public <S extends T> S save(S record) {
        lock.writeLock().lock();
        try {
            ensureLoaded();
            assignIdIfMissing(record);
            String id = idGetter.apply(record);

            int existingIndex = indexOf(id);
            if (existingIndex >= 0) {
                records.set(existingIndex, record);
            } else {
                records.add(record);
            }

            flushIfNeeded();
            return record;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public <S extends T> List<S> saveAll(Iterable<S> entities) {
        lock.writeLock().lock();
        try {
            ensureLoaded();
            List<S> saved = new ArrayList<>();
            for (S entity : entities) {
                assignIdIfMissing(entity);
                String id = idGetter.apply(entity);
                int existingIndex = indexOf(id);
                if (existingIndex >= 0) {
                    records.set(existingIndex, entity);
                } else {
                    records.add(entity);
                }
                saved.add(entity);
            }
            flushIfNeeded();
            return saved;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Optional<T> findById(String id) {
        lock.readLock().lock();
        try {
            ensureLoaded();
            return records.stream()
                    .filter(record -> id != null && id.equals(idGetter.apply(record)))
                    .findFirst();
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<T> findAll() {
        lock.readLock().lock();
        try {
            ensureLoaded();
            return List.copyOf(records);
        } finally {
            lock.readLock().unlock();
        }
    }

    public Page<T> findAll(Pageable pageable) {
        return FilePageSupport.page(findAll(), pageable);
    }

    public List<T> findAll(Sort sort) {
        return FilePageSupport.applySort(findAll(), sort);
    }

    public List<T> findAllMatching(Predicate<T> predicate) {
        lock.readLock().lock();
        try {
            ensureLoaded();
            return records.stream().filter(predicate).toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    public Page<T> findAllMatching(Predicate<T> predicate, Pageable pageable) {
        return FilePageSupport.page(findAllMatching(predicate), pageable);
    }

    public boolean exists(Predicate<T> predicate) {
        lock.readLock().lock();
        try {
            ensureLoaded();
            return records.stream().anyMatch(predicate);
        } finally {
            lock.readLock().unlock();
        }
    }

    public long count() {
        lock.readLock().lock();
        try {
            ensureLoaded();
            return records.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    public long count(Predicate<T> predicate) {
        lock.readLock().lock();
        try {
            ensureLoaded();
            return records.stream().filter(predicate).count();
        } finally {
            lock.readLock().unlock();
        }
    }

    public void deleteById(String id) {
        lock.writeLock().lock();
        try {
            ensureLoaded();
            records.removeIf(record -> id != null && id.equals(idGetter.apply(record)));
            flushIfNeeded();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void deleteAll(Iterable<? extends T> entities) {
        lock.writeLock().lock();
        try {
            ensureLoaded();
            List<String> ids = new ArrayList<>();
            for (T entity : entities) {
                String id = idGetter.apply(entity);
                if (id != null) {
                    ids.add(id);
                }
            }
            if (!ids.isEmpty()) {
                records.removeIf(record -> ids.contains(idGetter.apply(record)));
                flushIfNeeded();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Iterable<T> findAllByIds(Iterable<String> ids) {
        List<String> requestedIds = new ArrayList<>();
        for (String id : ids) {
            requestedIds.add(id);
        }
        return findAllMatching(record -> requestedIds.contains(idGetter.apply(record)));
    }

    public void flush() {
        lock.writeLock().lock();
        try {
            ensureLoaded();
            writeRecords(records);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void ensureLoaded() {
        if (records != null) {
            return;
        }
        records = readRecords();
    }

    private List<T> readRecords() {
        try {
            Files.createDirectories(filePath.getParent());
            if (!Files.exists(filePath)) {
                return new ArrayList<>();
            }

            if (format == FileFormat.JSONL) {
                try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
                    List<T> loaded = new ArrayList<>();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.isBlank()) {
                            loaded.add(objectMapper.readValue(line, type));
                        }
                    }
                    return loaded;
                }
            }

            return objectMapper.readValue(filePath.toFile(), listType);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read file-backed collection " + filePath, e);
        }
    }

    private void writeRecords(List<T> values) {
        Path tempFile = filePath.resolveSibling(filePath.getFileName() + ".tmp");
        try {
            Files.createDirectories(filePath.getParent());
            if (format == FileFormat.JSONL) {
                try (BufferedWriter writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
                    for (T value : values) {
                        writer.write(objectMapper.writeValueAsString(value));
                        writer.newLine();
                    }
                }
            } else {
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempFile.toFile(), values);
            }

            Files.move(tempFile, filePath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write file-backed collection " + filePath, e);
        }
    }

    private void flushIfNeeded() {
        if (flushOnWrite) {
            writeRecords(records);
        }
    }

    private void assignIdIfMissing(T record) {
        if (idGetter.apply(record) == null || idGetter.apply(record).isBlank()) {
            idSetter.accept(record, UUID.randomUUID().toString());
        }
    }

    private int indexOf(String id) {
        for (int i = 0; i < records.size(); i++) {
            if (id != null && id.equals(idGetter.apply(records.get(i)))) {
                return i;
            }
        }
        return -1;
    }
}
