package org.jds.edgar4j.job;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jds.edgar4j.dto.response.MarketCapBackfillResponse;
import org.jds.edgar4j.model.Form4;
import org.jds.edgar4j.model.Form4Transaction;
import org.jds.edgar4j.port.Form4DataPort;
import org.jds.edgar4j.service.CompanyMarketDataService;
import org.jds.edgar4j.service.Sp500Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MarketDataSyncJob {

    private final CompanyMarketDataService companyMarketDataService;
    private final Sp500Service sp500Service;
    private final Form4DataPort form4Repository;
    private final boolean enabled;
    private final int batchSize;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    @Autowired
    public MarketDataSyncJob(
            CompanyMarketDataService companyMarketDataService,
            Sp500Service sp500Service,
            Form4DataPort form4Repository,
            @Value("${edgar4j.jobs.market-data-sync.enabled:true}") boolean enabled,
            @Value("${edgar4j.jobs.market-data-sync.batch-size:50}") int batchSize) {
        this.companyMarketDataService = companyMarketDataService;
        this.sp500Service = sp500Service;
        this.form4Repository = form4Repository;
        this.enabled = enabled;
        this.batchSize = batchSize;
    }

    @Scheduled(cron = "${edgar4j.jobs.market-data-sync.cron:0 0 */2 * * MON-FRI}")
    public void syncMarketData() {
        if (!enabled) {
            log.debug("Market data sync job is disabled");
            return;
        }

        if (!isRunning.compareAndSet(false, true)) {
            log.warn("Market data sync job is already running, skipping this execution");
            return;
        }

        try {
            log.info("Starting market data sync job at {}", LocalDateTime.now());
            long startTime = System.currentTimeMillis();

            Set<String> sp500Tickers = new LinkedHashSet<>(sp500Service.getAllTickers());
            LocalDate since = LocalDate.now().minusDays(30);
            Set<String> insiderTickers = loadRecentInsiderTickers(since);

            Set<String> allTickers = new LinkedHashSet<>(sp500Tickers);
            allTickers.addAll(insiderTickers);

            if (allTickers.isEmpty()) {
                log.info("No tickers found for market data sync");
                return;
            }

            log.info("Refreshing {} tickers ({} S&P 500 + {} insider-active)",
                    allTickers.size(), sp500Tickers.size(), insiderTickers.size());

            List<String> tickerList = new ArrayList<>(allTickers);
            int effectiveBatchSize = Math.max(1, batchSize);
            for (int start = 0; start < tickerList.size(); start += effectiveBatchSize) {
                int end = Math.min(start + effectiveBatchSize, tickerList.size());
                List<String> batch = tickerList.subList(start, end);
                companyMarketDataService.fetchAndSaveQuotesBatch(batch);
                log.info("Market data sync progress: {}/{}", end, tickerList.size());
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("Market data sync job completed in {} ms", duration);
        } catch (Exception e) {
            log.error("Error during market data sync job", e);
        } finally {
            isRunning.set(false);
        }
    }

    public void triggerSync() {
        log.info("Manual market data sync triggered");
        syncMarketData();
    }

    public MarketCapBackfillResponse triggerMarketCapBackfill(int maxTickers, int lookbackDays) {
        log.info("Manual market-cap backfill triggered");

        if (!isRunning.compareAndSet(false, true)) {
            throw new IllegalStateException("Market data sync job is already running");
        }

        try {
            LocalDate since = LocalDate.now().minusDays(Math.max(1, lookbackDays));
            Set<String> trackedTickers = loadTrackedTickers(since);
            List<String> orderedTickers = trackedTickers.stream()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();

            log.info("Starting market-cap backfill at {} for {} tracked tickers (lookbackDays={}, maxTickers={})",
                    LocalDateTime.now(), orderedTickers.size(), lookbackDays, maxTickers);

            MarketCapBackfillResponse response = companyMarketDataService.backfillMissingMarketCaps(
                    orderedTickers,
                    batchSize,
                    maxTickers);

            log.info("Market-cap backfill completed: processed={} updated={} unresolved={} deferred={}",
                    response.getProcessedTickers(),
                    response.getUpdatedTickers(),
                    response.getUnresolvedTickersCount(),
                    response.getDeferredTickers());

            return response;
        } finally {
            isRunning.set(false);
        }
    }

    public boolean isRunning() {
        return isRunning.get();
    }

    private String normalizeTicker(String ticker) {
        if (ticker == null) {
            return null;
        }

        String normalized = ticker.trim().replace('.', '-').toUpperCase(java.util.Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    private Set<String> loadRecentInsiderTickers(LocalDate since) {
        return form4Repository.findRecentAcquisitions(since).stream()
                .filter(Objects::nonNull)
                .filter(form4 -> hasRecentPurchaseActivity(form4, since))
                .map(Form4::getTradingSymbol)
                .map(this::normalizeTicker)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean hasRecentPurchaseActivity(Form4 form4, LocalDate since) {
        if (form4.getTransactions() == null || form4.getTransactions().isEmpty()) {
            return "A".equalsIgnoreCase(form4.getAcquiredDisposedCode())
                    && form4.getTransactionDate() != null
                    && !form4.getTransactionDate().isBefore(since);
        }

        return form4.getTransactions().stream()
                .filter(Objects::nonNull)
                .anyMatch(transaction -> isRecentOpenMarketPurchase(transaction, since));
    }

    private Set<String> loadTrackedTickers(LocalDate since) {
        Set<String> trackedTickers = new LinkedHashSet<>(sp500Service.getAllTickers());
        trackedTickers.addAll(loadRecentInsiderTickers(since));
        return trackedTickers;
    }

    private boolean isRecentOpenMarketPurchase(Form4Transaction transaction, LocalDate since) {
        return "P".equalsIgnoreCase(transaction.getTransactionCode())
                && "A".equalsIgnoreCase(transaction.getAcquiredDisposedCode())
                && transaction.getTransactionDate() != null
                && !transaction.getTransactionDate().isBefore(since);
    }
}
