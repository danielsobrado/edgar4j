package org.jds.edgar4j.job;

import org.jds.edgar4j.model.Company;
import org.jds.edgar4j.repository.CompanyRepository;
import org.jds.edgar4j.service.DownloadSubmissionsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Scheduled job for syncing company filings from SEC.
 * Runs periodically to keep filing data up-to-date.
 */
@Component
public class FilingSyncJob {

    private static final Logger log = LoggerFactory.getLogger(FilingSyncJob.class);

    private final CompanyRepository companyRepository;
    private final DownloadSubmissionsService downloadSubmissionsService;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicInteger processedCount = new AtomicInteger(0);
    private final AtomicInteger totalCount = new AtomicInteger(0);

    @Value("${edgar4j.jobs.filing-sync.enabled:true}")
    private boolean enabled;

    @Value("${edgar4j.jobs.filing-sync.batch-size:100}")
    private int batchSize;

    @Value("${edgar4j.jobs.filing-sync.delay-between-companies-ms:1000}")
    private long delayBetweenCompanies;

    public FilingSyncJob(CompanyRepository companyRepository,
                         DownloadSubmissionsService downloadSubmissionsService) {
        this.companyRepository = companyRepository;
        this.downloadSubmissionsService = downloadSubmissionsService;
    }

    /**
     * Sync filings for all companies every 4 hours.
     * This is a long-running job that processes companies in batches.
     */
    @Scheduled(cron = "${edgar4j.jobs.filing-sync.cron:0 0 */4 * * *}")
    public void syncFilings() {
        if (!enabled) {
            log.debug("Filing sync job is disabled");
            return;
        }

        if (!isRunning.compareAndSet(false, true)) {
            log.warn("Filing sync job is already running, skipping this execution");
            return;
        }

        try {
            log.info("Starting filing sync job at {}", LocalDateTime.now());
            long startTime = System.currentTimeMillis();
            processedCount.set(0);

            // Get total count of companies
            long total = companyRepository.count();
            totalCount.set((int) total);
            log.info("Processing {} companies for filing sync", total);

            int page = 0;
            Page<Company> companies;

            do {
                companies = companyRepository.findAll(PageRequest.of(page, batchSize));

                for (Company company : companies.getContent()) {
                    try {
                        syncCompanyFilings(company);
                        processedCount.incrementAndGet();

                        // Rate limiting - delay between companies
                        if (delayBetweenCompanies > 0) {
                            Thread.sleep(delayBetweenCompanies);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.warn("Filing sync job interrupted");
                        return;
                    } catch (Exception e) {
                        log.error("Error syncing filings for company CIK: {}", company.getCik(), e);
                    }
                }

                page++;
                log.info("Filing sync progress: {}/{} companies processed", processedCount.get(), total);

            } while (companies.hasNext());

            long duration = System.currentTimeMillis() - startTime;
            log.info("Filing sync job completed. Processed {} companies in {} ms", processedCount.get(), duration);

        } catch (Exception e) {
            log.error("Error during filing sync job", e);
        } finally {
            isRunning.set(false);
        }
    }

    private void syncCompanyFilings(Company company) {
        try {
            downloadSubmissionsService.downloadSubmissions(company.getCik());
        } catch (Exception e) {
            log.warn("Failed to sync filings for company: {} (CIK: {})", company.getName(), company.getCik());
            throw e;
        }
    }

    /**
     * Sync filings for a specific company by CIK.
     */
    public void syncCompanyFilings(String cik) {
        log.info("Manual filing sync triggered for CIK: {}", cik);
        try {
            downloadSubmissionsService.downloadSubmissions(cik);
        } catch (Exception e) {
            log.error("Error syncing filings for CIK: {}", cik, e);
        }
    }

    public boolean isRunning() {
        return isRunning.get();
    }

    public int getProgress() {
        int total = totalCount.get();
        if (total == 0) return 0;
        return (processedCount.get() * 100) / total;
    }

    public int getProcessedCount() {
        return processedCount.get();
    }

    public int getTotalCount() {
        return totalCount.get();
    }
}
