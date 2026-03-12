package org.jds.edgar4j.config;

import java.time.format.DateTimeFormatter;

/**
 * Application-wide constants for edgar4j.
 * Centralizes magic numbers and configuration defaults.
 */
public final class AppConstants {

    private AppConstants() {
        // Prevent instantiation
    }

    // ========== Pagination Defaults ==========
    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MIN_PAGE_SIZE = 1;
    public static final int MAX_PAGE_SIZE = 100;
    public static final String DEFAULT_SORT_DIRECTION = "desc";

    // ========== CIK Formatting ==========
    public static final int CIK_PADDED_LENGTH = 10;
    public static final String CIK_FORMAT_PATTERN = "%010d";

    // ========== Date Formats ==========
    public static final String DATE_FORMAT_PATTERN = "yyyy-MM-dd";
    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(DATE_FORMAT_PATTERN);

    // ========== SEC API ==========
    public static final int DEFAULT_RATE_LIMIT_PER_SECOND = 10;
    public static final long API_TIMEOUT_SECONDS = 30;

    // ========== Form Types ==========
    public static final String FORM_TYPE_10K = "10-K";
    public static final String FORM_TYPE_10Q = "10-Q";
    public static final String FORM_TYPE_8K = "8-K";
    public static final String FORM_TYPE_4 = "4";
    public static final String FORM_TYPE_13F = "13F-HR";
    public static final String FORM_TYPE_13D = "SC 13D";
    public static final String FORM_TYPE_13G = "SC 13G";

    // ========== Thresholds ==========
    public static final double BENEFICIAL_OWNERSHIP_THRESHOLD = 10.0;

    // ========== Validation Messages ==========
    public static final String MSG_CIK_REQUIRED = "CIK is required";
    public static final String MSG_CIK_INVALID = "Invalid CIK format";
    public static final String MSG_FORMAT_REQUIRED = "Export format is required";
    public static final String MSG_PAGE_MIN = "Page number must be at least 0";
    public static final String MSG_SIZE_MIN = "Page size must be at least 1";
    public static final String MSG_SIZE_MAX = "Page size must be at most 100";

    // ========== Cache Names ==========
    public static final String CACHE_COMPANIES = "companies";
    public static final String CACHE_FORM_TYPES = "formTypes";
    public static final String CACHE_DASHBOARD_STATS = "dashboardStats";
    public static final String CACHE_SETTINGS = "settings";
}
