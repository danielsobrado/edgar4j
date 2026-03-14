package org.jds.edgar4j.storage.file;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    void rebuild(Iterable<T> records) {
        buckets.clear();
        for (T record : records) {
            add(record);
        }
    }

    void add(T record) {
        String id = idExtractor.apply(record);
        if (id == null) {
            return;
        }

        for (String bucketKey : bucketKeysForRecord(record)) {
            buckets.computeIfAbsent(bucketKey, ignored -> new LinkedHashMap<>())
                    .put(id, record);
        }
    }

    void remove(T record) {
        String id = idExtractor.apply(record);
        if (id == null) {
            return;
        }

        for (String bucketKey : bucketKeysForRecord(record)) {
            LinkedHashMap<String, T> bucket = buckets.get(bucketKey);
            if (bucket == null) {
                continue;
            }

            bucket.remove(id);
            if (bucket.isEmpty()) {
                buckets.remove(bucketKey);
            }
        }
    }

    List<T> findAll(Object value) {
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