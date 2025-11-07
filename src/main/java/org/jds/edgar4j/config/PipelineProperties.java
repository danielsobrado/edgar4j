package org.jds.edgar4j.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * Configuration properties for insider form download pipeline
 * Supports Forms 3, 4, and 5
 *
 * @author J. Daniel Sobrado
 * @version 2.0
 * @since 2025-11-05
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "edgar4j.pipeline")
public class PipelineProperties {

    /**
     * Enable/disable the scheduled download job
     */
    private boolean enabled = true;

    /**
     * Cron expression for scheduled downloads (default: daily at 6 PM EST)
     */
    private String schedule = "0 0 18 * * ?";

    /**
     * Comma-separated list of form types to download (3, 4, 5)
     * Default: "3,4,5" (all insider forms)
     */
    private String formTypes = "3,4,5";

    /**
     * Number of days to look back for new filings (default: 3 days)
     */
    private int lookbackDays = 3;

    /**
     * Maximum number of concurrent processing threads
     */
    private int maxConcurrentJobs = 5;

    /**
     * SEC rate limit: requests per second (SEC allows 10/sec)
     */
    private int rateLimitPerSecond = 8;

    /**
     * Delay between batches in milliseconds
     */
    private long batchDelayMs = 1000;

    /**
     * Batch size for processing filings
     */
    private int batchSize = 100;

    /**
     * Retry attempts for failed downloads
     */
    private int maxRetries = 3;

    /**
     * Retry delay in milliseconds
     */
    private long retryDelayMs = 5000;

    /**
     * Enable automatic backfill for missing dates
     */
    private boolean autoBackfill = false;

    /**
     * Maximum number of days to backfill in one run
     */
    private int maxBackfillDays = 30;

    /**
     * User agent for SEC requests (required by SEC)
     */
    private String userAgent = "edgar4j/1.0 (compliance@example.com)";

    /**
     * SEC RSS feed URL for latest insider form filings
     * Placeholders: {type}, {start}, {count}
     */
    private String rssFeedUrl = "https://www.sec.gov/cgi-bin/browse-edgar?action=getcurrent&type={type}&company=&dateb=&owner=include&start={start}&count={count}&output=atom";

    /**
     * SEC EDGAR archives base URL
     */
    private String archivesBaseUrl = "https://www.sec.gov/Archives/edgar/data/";

    /**
     * Enable detailed logging
     */
    private boolean verboseLogging = false;
}
