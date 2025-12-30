/**
 * Application-wide constants for edgar4j frontend.
 * Centralizes magic numbers and configuration defaults.
 */

// ========== API Configuration ==========
export const API_CONFIG = {
  TIMEOUT_MS: 30000,
  DEFAULT_HEADERS: {
    'Content-Type': 'application/json',
  },
} as const;

// ========== Pagination Defaults ==========
export const PAGINATION = {
  DEFAULT_PAGE: 0,
  DEFAULT_PAGE_SIZE: 20,
  MIN_PAGE_SIZE: 10,
  MAX_PAGE_SIZE: 100,
  PAGE_SIZE_OPTIONS: [10, 20, 50, 100] as const,
} as const;

// ========== Polling Intervals ==========
export const POLLING = {
  DOWNLOAD_JOB_MS: 2000,
  ACTIVE_JOBS_MS: 5000,
  COPY_FEEDBACK_MS: 2000,
} as const;

// ========== Notification Durations ==========
export const NOTIFICATION = {
  DEFAULT_DURATION_MS: 5000,
  ERROR_DURATION_MS: 7000,
  SUCCESS_DURATION_MS: 4000,
} as const;

// ========== Form Types ==========
export const SEC_FORM_TYPES = {
  FORM_10K: '10-K',
  FORM_10Q: '10-Q',
  FORM_8K: '8-K',
  FORM_4: '4',
  FORM_3: '3',
  FORM_5: '5',
  FORM_13F: '13F-HR',
  FORM_13D: 'SC 13D',
  FORM_13G: 'SC 13G',
  FORM_6K: '6-K',
  FORM_20F: '20-F',
} as const;

// ========== Validation ==========
export const VALIDATION = {
  CIK_PATTERN: /^[0-9]{1,10}$/,
  TICKER_MAX_LENGTH: 10,
  COMPANY_NAME_MAX_LENGTH: 200,
} as const;

// ========== Error Messages ==========
export const ERROR_MESSAGES = {
  GENERIC: 'An unexpected error occurred',
  NETWORK: 'Network error. Please check your connection.',
  TIMEOUT: 'Request timed out. Please try again.',
  EXPORT_CSV_FAILED: 'Failed to export to CSV',
  EXPORT_JSON_FAILED: 'Failed to export to JSON',
  DOWNLOAD_FAILED: 'Failed to start download',
  FETCH_FAILED: 'Failed to fetch data',
} as const;
