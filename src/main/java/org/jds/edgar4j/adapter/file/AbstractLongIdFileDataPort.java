package org.jds.edgar4j.adapter.file;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

import org.jds.edgar4j.port.BaseInsiderDataPort;
import org.jds.edgar4j.storage.file.FileCollection;
import org.jds.edgar4j.storage.file.FilePageSupport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public abstract class AbstractLongIdFileDataPort<T> implements BaseInsiderDataPort<T> {

    protected final FileCollection<T> collection;

    private final Function<T, Long> longIdGetter;
    private final BiConsumer<T, Long> longIdSetter;
    private final AtomicLong idSequence = new AtomicLong(-1L);
    private final Object saveMonitor = new Object();

    protected AbstractLongIdFileDataPort(
            FileCollection<T> collection,
            Function<T, Long> longIdGetter,
            BiConsumer<T, Long> longIdSetter) {
        this.collection = collection;
        this.longIdGetter = longIdGetter;
        this.longIdSetter = longIdSetter;
    }

    @Override
    public <S extends T> S save(S entity) {
        synchronized (saveMonitor) {
            assignLongIdIfMissing(entity);
            return collection.save(entity);
        }
    }

    @Override
    public <S extends T> List<S> saveAll(Iterable<S> entities) {
        synchronized (saveMonitor) {
            List<S> prepared = new ArrayList<>();
            for (S entity : entities) {
                assignLongIdIfMissing(entity);
                prepared.add(entity);
            }
            return collection.saveAll(prepared);
        }
    }

    @Override
    public Optional<T> findById(Long id) {
        return collection.findById(toStringId(id));
    }

    @Override
    public boolean existsById(Long id) {
        return findById(id).isPresent();
    }

    @Override
    public List<T> findAll() {
        return collection.findAll();
    }

    @Override
    public Page<T> findAll(Pageable pageable) {
        return collection.findAll(pageable);
    }

    @Override
    public List<T> findAllById(Iterable<Long> ids) {
        List<String> stringIds = new ArrayList<>();
        for (Long id : ids) {
            String stringId = toStringId(id);
            if (stringId != null) {
                stringIds.add(stringId);
            }
        }
        return StreamSupport.stream(collection.findAllByIds(stringIds).spliterator(), false)
                .toList();
    }

    @Override
    public List<T> findAll(Sort sort) {
        return collection.findAll(sort);
    }

    @Override
    public long count() {
        return collection.count();
    }

    @Override
    public void deleteById(Long id) {
        String stringId = toStringId(id);
        if (stringId != null) {
            collection.deleteById(stringId);
        }
    }

    @Override
    public void delete(T entity) {
        if (entity != null) {
            collection.deleteAll(List.of(entity));
        }
    }

    @Override
    public void deleteAllById(Iterable<? extends Long> ids) {
        List<String> stringIds = new ArrayList<>();
        for (Long id : ids) {
            String stringId = toStringId(id);
            if (stringId != null) {
                stringIds.add(stringId);
            }
        }
        collection.deleteAllById(stringIds);
    }

    @Override
    public void deleteAll(Iterable<? extends T> entities) {
        collection.deleteAll(entities);
    }

    @Override
    public void deleteAll() {
        collection.deleteAll(collection.findAll());
    }

    protected Optional<T> findFirst(Predicate<T> predicate) {
        return collection.findAllMatching(predicate).stream().findFirst();
    }

    protected Optional<T> findFirstByIndex(String indexName, Object value) {
        return collection.findIndexedFirst(indexName, value);
    }

    protected List<T> findMatching(Predicate<T> predicate) {
        return collection.findAllMatching(predicate);
    }

    protected List<T> findAllByIndex(String indexName, Object value) {
        return collection.findAllIndexed(indexName, value);
    }

    protected Page<T> findMatching(Predicate<T> predicate, Pageable pageable) {
        return collection.findAllMatching(predicate, pageable);
    }

    protected Page<T> page(List<T> source, Pageable pageable, Sort defaultSort) {
        if (pageable == null || pageable.isUnpaged()) {
            List<T> sorted = defaultSort == null ? source : FilePageSupport.applySort(source, defaultSort);
            return FilePageSupport.page(sorted, pageable);
        }

        Sort effectiveSort = pageable.getSort() == null || pageable.getSort().isUnsorted()
                ? defaultSort
                : pageable.getSort();
        Pageable effectivePageable = effectiveSort == null
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), effectiveSort);
        return FilePageSupport.page(source, effectivePageable);
    }

    protected boolean exists(Predicate<T> predicate) {
        return collection.exists(predicate);
    }

    protected boolean existsByIndex(String indexName, Object value) {
        return collection.existsIndexed(indexName, value);
    }

    protected long count(Predicate<T> predicate) {
        return collection.count(predicate);
    }

    protected long countByIndex(String indexName, Object value) {
        return collection.countIndexed(indexName, value);
    }

    protected void registerExactIndex(String indexName, Function<T, ?> keyExtractor) {
        collection.registerIndex(indexName, keyExtractor);
    }

    protected void registerIgnoreCaseIndex(String indexName, Function<T, String> keyExtractor) {
        collection.registerIgnoreCaseIndex(indexName, keyExtractor);
    }

    protected void registerMultiValueExactIndex(String indexName, Function<T, Iterable<?>> keyExtractor) {
        collection.registerMultiValueIndex(indexName, keyExtractor);
    }

    protected void registerMultiValueIgnoreCaseIndex(String indexName, Function<T, Iterable<String>> keyExtractor) {
        collection.registerMultiValueIgnoreCaseIndex(indexName, keyExtractor);
    }

    protected boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    protected boolean containsIgnoreCase(String value, String fragment) {
        return value != null && fragment != null && value.toLowerCase().contains(fragment.toLowerCase());
    }

    protected boolean isTrue(Boolean value) {
        return Boolean.TRUE.equals(value);
    }

    private void assignLongIdIfMissing(T entity) {
        Long existingId = longIdGetter.apply(entity);
        if (existingId != null) {
            synchronizeSequence(existingId);
            return;
        }

        initializeSequenceIfNeeded();
        longIdSetter.accept(entity, idSequence.incrementAndGet());
    }

    private void initializeSequenceIfNeeded() {
        if (idSequence.get() >= 0L) {
            return;
        }

        synchronized (idSequence) {
            if (idSequence.get() >= 0L) {
                return;
            }

            long maxId = collection.findAll().stream()
                    .map(longIdGetter)
                    .filter(Objects::nonNull)
                    .mapToLong(Long::longValue)
                    .max()
                    .orElse(0L);
            idSequence.set(maxId);
        }
    }

    private void synchronizeSequence(Long id) {
        if (id != null) {
            idSequence.accumulateAndGet(id, Math::max);
        }
    }

    private String toStringId(Long id) {
        return id == null ? null : String.valueOf(id);
    }
}
