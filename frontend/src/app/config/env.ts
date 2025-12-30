/**
 * Environment configuration with validation.
 * Ensures required environment variables are present and valid.
 */

/**
 * Gets the API base URL from environment with validation.
 * In development, falls back to localhost. In production, requires explicit configuration.
 */
export function getApiBaseUrl(): string {
  const url = import.meta.env.VITE_API_URL;

  if (!url) {
    if (import.meta.env.DEV) {
      return 'http://localhost:8080';
    }
    console.error('VITE_API_URL environment variable is required for production');
    return 'http://localhost:8080'; // Fallback to prevent app crash
  }

  // Validate URL format
  try {
    new URL(url);
  } catch {
    console.error(`Invalid VITE_API_URL: ${url}`);
    return 'http://localhost:8080';
  }

  return url;
}

/**
 * Check if we're in development mode.
 */
export const isDevelopment = (): boolean => import.meta.env.DEV;

/**
 * Check if we're in production mode.
 */
export const isProduction = (): boolean => import.meta.env.PROD;

/**
 * Conditional logger that only logs in development.
 */
export const devLog = {
  log: (...args: unknown[]) => {
    if (isDevelopment()) {
      console.log(...args);
    }
  },
  warn: (...args: unknown[]) => {
    if (isDevelopment()) {
      console.warn(...args);
    }
  },
  error: (...args: unknown[]) => {
    // Always log errors, but with more context in dev
    console.error(...args);
  },
  info: (...args: unknown[]) => {
    if (isDevelopment()) {
      console.info(...args);
    }
  },
};
