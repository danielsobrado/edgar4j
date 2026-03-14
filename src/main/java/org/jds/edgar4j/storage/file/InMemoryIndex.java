package org.jds.edgar4j.storage.file;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;

final class InMemoryIndex<T> {

    private final String name;
    private final Function<T, String> idExtractor;
    private final Function<T, Iterable<?>> keyExtractor;
    private final Function<Object, String> keyNormalizer;
    private final Map<String, LinkedHashMap<String, T>> buckets = new LinkedHashMap<>();

    InMemoryIndex(
            String name,
            Function<T, String> idExtractor,
            Function<T, Iterable<?>> keyExtractor,
            Function<Object, String> keyNormalizer) {
        this.name = name;
        this.idExtractor = idExtractor;
        this.keyExtractor = keyExtractor;
        this.keyNormalizer = keyNormalizer;
    }

    synchronized void rebuild(Iterable<T> records) {
        buckets.clear();
        for (T record : records) {
            addInternal(record);
        }
    }

    synchronized void add(T record) {
        addInternal(record);
    }

    synchronized void update(String id, T record) {
        if (id == null) {
            return;
        }

        removeByIdInternal(id);
        addInternal(record);
    }

    synchronized void removeById(String id) {
        if (id == null) {
            return;
        }

        removeByIdInternal(id);
    }

    synchronized void remove(T record) {
        String id = idExtractor.apply(record);
        if (id == null) {
            return;
        }

        removeByIdInternal(id);
    }

    synchronized List<T> findAll(Object value) {
        String bucketKey = normalizeLookupValue(value);
        if (bucketKey == null) {
            return List.of();
        }

        LinkedHashMap<String, T> bucket = buckets.get(bucketKey);
        return bucket == null ? List.of() : List.copyOf(bucket.values());
    }

    String name() {
        return name;
    }

    private void addInternal(T record) {
        String id = idExtractor.apply(record);
        if (id == null) {
            return;
        }

        for (String bucketKey : bucketKeysForRecord(record)) {
            buckets.computeIfAbsent(bucketKey, ignored -> new LinkedHashMap<>())
                    .put(id, record);
        }
    }

    private void removeByIdInternal(String id) {
        for (var iterator = buckets.entrySet().iterator(); iterator.hasNext();) {
            Entry<String, LinkedHashMap<String, T>> entry = iterator.next();
            LinkedHashMap<String, T> bucket = entry.getValue();
            bucket.remove(id);
            if (bucket.isEmpty()) {
                iterator.remove();
            }
        }
    }

    private List<String> bucketKeysForRecord(T record) {
        Iterable<?> rawKeys = keyExtractor.apply(record);
        if (rawKeys == null) {
            return List.of();
        }

        LinkedHashMap<String, Boolean> normalizedKeys = new LinkedHashMap<>();
        for (Object rawKey : rawKeys) {
            String normalizedKey = keyNormalizer.apply(rawKey);
            if (normalizedKey != null) {
                normalizedKeys.put(normalizedKey, Boolean.TRUE);
            }
        }
        return List.copyOf(normalizedKeys.keySet());
    }

    private String normalizeLookupValue(Object value) {
        return keyNormalizer.apply(value);
    }
}
