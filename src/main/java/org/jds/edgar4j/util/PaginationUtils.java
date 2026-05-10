package org.jds.edgar4j.util;

import org.jds.edgar4j.config.AppConstants;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * Shared pagination helpers for API and controller layer defaults.
 */
public final class PaginationUtils {

    private PaginationUtils() {
        // utility class
    }

    public static int normalizePage(int page) {
        return Math.max(AppConstants.DEFAULT_PAGE, page);
    }

    public static int normalizeSize(int size) {
        if (size < AppConstants.MIN_PAGE_SIZE) {
            return AppConstants.MIN_PAGE_SIZE;
        }
        return Math.min(size, AppConstants.MAX_PAGE_SIZE);
    }

    public static Sort.Direction normalizeSortDirection(String sortDirection) {
        return "asc".equalsIgnoreCase(sortDirection) ? Sort.Direction.ASC : Sort.Direction.DESC;
    }

    public static PageRequest pageRequest(int page, int size, String sortField) {
        return PageRequest.of(
                normalizePage(page),
                normalizeSize(size),
                Sort.by(Sort.Direction.DESC, sortField));
    }

    public static PageRequest pageRequest(int page, int size, String sortField, String sortDirection) {
        return PageRequest.of(
                normalizePage(page),
                normalizeSize(size),
                Sort.by(normalizeSortDirection(sortDirection), sortField));
    }
}
