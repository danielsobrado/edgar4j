package org.jds.edgar4j.storage.file;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.beans.BeanWrapperImpl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class FilePageSupport {

    private FilePageSupport() {
    }

    public static <T> Page<T> page(List<T> source, Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return new PageImpl<>(List.copyOf(source));
        }

        List<T> sorted = applySort(source, pageable.getSort());
        int start = Math.toIntExact(Math.min((long) pageable.getPageNumber() * pageable.getPageSize(), sorted.size()));
        int end = Math.min(start + pageable.getPageSize(), sorted.size());
        return new PageImpl<>(sorted.subList(start, end), pageable, sorted.size());
    }

    public static <T> List<T> applySort(List<T> source, Sort sort) {
        List<T> records = new ArrayList<>(source);
        if (sort == null || sort.isUnsorted()) {
            return records;
        }

        Comparator<T> comparator = null;
        for (Sort.Order order : sort) {
            Comparator<Comparable> valueComparator = order.isDescending()
                    ? Comparator.nullsLast(Comparator.reverseOrder())
                    : Comparator.nullsLast(Comparator.naturalOrder());
            Comparator<T> nextComparator = Comparator.comparing(
                    value -> comparableProperty(value, order.getProperty()),
                    valueComparator);
            comparator = comparator == null ? nextComparator : comparator.thenComparing(nextComparator);
        }

        if (comparator != null) {
            records.sort(comparator);
        }
        return records;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Comparable comparableProperty(Object value, String propertyPath) {
        Object propertyValue = readProperty(value, propertyPath);
        if (propertyValue == null) {
            return null;
        }
        if (propertyValue instanceof Comparable comparable) {
            return comparable;
        }
        return propertyValue.toString();
    }

    static Object readProperty(Object value, String propertyPath) {
        BeanWrapperImpl wrapper = new BeanWrapperImpl(value);
        return wrapper.getPropertyValue(propertyPath);
    }
}
