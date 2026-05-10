package org.jds.edgar4j.job;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jds.edgar4j.model.Company;
import org.jds.edgar4j.model.Filling;
import org.jds.edgar4j.port.CompanyDataPort;
import org.jds.edgar4j.port.FillingDataPort;
import org.jds.edgar4j.port.TickerDataPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job for checking data integrity and generating statistics.
 */
@Component
public class DataIntegrityJob {

    private static final Logger log = LoggerFactory.getLogger(DataIntegrityJob.class);
    private static final int CHECK_PAGE_SIZE = 1_000;
    private static final String CHECK_PASSED = "PASSED";
    private static final String CHECK_FAILED = "FAILED";
    private static final String HEALTHY = "HEALTHY";
    private static final String DEGRADED = "DEGRADED";

    private final CompanyDataPort companyRepository;
    private final FillingDataPort fillingRepository;
    private final TickerDataPort tickerRepository;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    private Map<String, Object> lastReport = new HashMap<>();
    private LocalDateTime lastRunTime;

    @Value("${edgar4j.jobs.data-integrity.enabled:true}")
    private boolean enabled;

    public DataIntegrityJob(CompanyDataPort companyRepository,
                            FillingDataPort fillingRepository,
                            TickerDataPort tickerRepository) {
        this.companyRepository = companyRepository;
        this.fillingRepository = fillingRepository;
        this.tickerRepository = tickerRepository;
    }

    /**
     * Run data integrity check daily at midnight.
     */
    @Scheduled(cron = "${edgar4j.jobs.data-integrity.cron:0 0 0 * * *}")
    public void checkDataIntegrity() {
        if (!enabled) {
            log.debug("Data integrity job is disabled");
            return;
        }

        if (!isRunning.compareAndSet(false, true)) {
            log.warn("Data integrity job is already running, skipping this execution");
            return;
        }

        Map<String, Object> report = new HashMap<>();
        long startTime = System.currentTimeMillis();

        try {
            log.info("Starting data integrity check at {}", LocalDateTime.now());

            long companyCount = companyRepository.count();
            long fillingCount = fillingRepository.count();
            long tickerCount = tickerRepository.count();

            Set<String> companyCiks = loadCompanyCiks();
            long orphanedFilingsCount = countOrphanedFilings(companyCiks);
            long missingTickerCount = countCompaniesMissingTicker();

            report.put("companyCount", companyCount);
            report.put("fillingCount", fillingCount);
            report.put("tickerCount", tickerCount);
            report.put("orphanedFilingsCount", orphanedFilingsCount);
            report.put("missingTickersCount", missingTickerCount);

            String orphanedFilingsCheck = orphanedFilingsCount == 0 ? CHECK_PASSED : CHECK_FAILED;
            String missingTickersCheck = missingTickerCount == 0 ? CHECK_PASSED : CHECK_FAILED;
            report.put("orphanedFilingsCheck", orphanedFilingsCheck);
            report.put("missingTickersCheck", missingTickersCheck);

            String databaseHealth = CHECK_PASSED.equals(orphanedFilingsCheck) && CHECK_PASSED.equals(missingTickersCheck)
                    ? HEALTHY
                    : DEGRADED;
            report.put("databaseHealth", databaseHealth);
            report.put("status", CHECK_PASSED);

            long duration = System.currentTimeMillis() - startTime;
            report.put("checkDurationMs", duration);
            report.put("timestamp", LocalDateTime.now().toString());

            lastReport = report;
            lastRunTime = LocalDateTime.now();

            log.info("Data integrity check completed in {} ms", duration);
            log.info("Report: Companies={}, Filings={}, Tickers={}",
                    companyCount, fillingCount, tickerCount);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            report.put("status", CHECK_FAILED);
            report.put("databaseHealth", DEGRADED);
            report.put("checkDurationMs", duration);
            report.put("timestamp", LocalDateTime.now().toString());
            report.put("error", e.getMessage());
            lastReport = report;
            lastRunTime = LocalDateTime.now();
            log.error("Error during data integrity check after {} ms", duration, e);
        } finally {
            isRunning.set(false);
        }
    }

    public Map<String, Object> getLastReport() {
        return new HashMap<>(lastReport);
    }

    public LocalDateTime getLastRunTime() {
        return lastRunTime;
    }

    public boolean isRunning() {
        return isRunning.get();
    }

    /**
     * Trigger manual integrity check.
     */
    public void triggerCheck() {
        log.info("Manual data integrity check triggered");
        checkDataIntegrity();
    }

    private Set<String> loadCompanyCiks() {
        Set<String> companyCiks = new LinkedHashSet<>();
        int page = 0;

        while (true) {
            Page<Company> companies = companyRepository.findAll(PageRequest.of(page, CHECK_PAGE_SIZE));
            for (Company company : companies) {
                String normalizedCik = normalizeCik(company != null ? company.getCik() : null);
                if (normalizedCik != null) {
                    companyCiks.add(normalizedCik);
                }
            }

            if (!companies.hasNext()) {
                break;
            }
            page++;
        }

        return Collections.unmodifiableSet(companyCiks);
    }

    private long countOrphanedFilings(Set<String> companyCiks) {
        long orphanedCount = 0;
        int page = 0;

        while (true) {
            Page<Filling> fillings = fillingRepository.findAll(PageRequest.of(page, CHECK_PAGE_SIZE));
            for (Filling filling : fillings) {
                String normalizedCik = normalizeCik(filling != null ? filling.getCik() : null);
                if (normalizedCik == null || !companyCiks.contains(normalizedCik)) {
                    orphanedCount++;
                }
            }

            if (!fillings.hasNext()) {
                break;
            }
            page++;
        }

        return orphanedCount;
    }

    private long countCompaniesMissingTicker() {
        long missingTickers = 0;
        int page = 0;

        while (true) {
            Page<Company> companies = companyRepository.findAll(PageRequest.of(page, CHECK_PAGE_SIZE));
            for (Company company : companies) {
                String ticker = company != null ? company.getTicker() : null;
                if (ticker == null || ticker.isBlank()) {
                    missingTickers++;
                }
            }

            if (!companies.hasNext()) {
                break;
            }
            page++;
        }

        return missingTickers;
    }

    private String normalizeCik(String cik) {
        if (cik == null || cik.isBlank()) {
            return null;
        }

        String digitsOnly = cik.replaceAll("\\D", "");
        if (digitsOnly.isBlank()) {
            return null;
        }

        try {
            return String.format("%010d", Long.parseLong(digitsOnly));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
