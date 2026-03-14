package org.jds.edgar4j.adapter.file;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

import org.jds.edgar4j.port.BaseDocumentDataPort;
import org.jds.edgar4j.storage.file.FileCollection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public abstract class AbstractFileDataPort<T> implements BaseDocumentDataPort<T> {

    protected final FileCollection<T> collection;

    protected AbstractFileDataPort(FileCollection<T> collection) {
        this.collection = collection;
    }

    @Override
    public <S extends T> S save(S entity) {
        return collection.save(entity);
    }

    @Override
    public <S extends T> List<S> saveAll(Iterable<S> entities) {
        return collection.saveAll(entities);
    }

    @Override
    public Optional<T> findById(String id) {
        return collection.findById(id);
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
    public List<T> findAllById(Iterable<String> ids) {
        return StreamSupport.stream(collection.findAllByIds(ids).spliterator(), false)
                .toList();
    }

    @Override
    public List<T> findAll(Sort sort) {
        return collection.findAll(sort);
    }

    @Override
    public boolean existsById(String id) {
        return findById(id).isPresent();
    }

    @Override
    public long count() {
        return collection.count();
    }

    @Override
    public void deleteById(String id) {
        collection.deleteById(id);
    }

    @Override
    public void delete(T entity) {
        if (entity != null) {
            collection.deleteAll(List.of(entity));
        }
    }

    @Override
    public void deleteAllById(Iterable<? extends String> ids) {
        for (String id : ids) {
            deleteById(id);
        }
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
}
