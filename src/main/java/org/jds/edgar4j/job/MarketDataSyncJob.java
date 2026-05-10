package org.jds.edgar4j.job;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

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

    private static final int MIN_LOOKBACK_DAYS = 1;
    private static final int MAX_LOOKBACK_DAYS = 365;
    private static final int DEFAULT_LOOKBACK_DAYS = 30;

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
        this.batchSize = Math.max(1, batchSize);
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

            Set<String> sp500Tickers = normalizeTickerSet(sp500Service.getAllTickers());
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
            int effectiveLookbackDays = normalizeLookbackDays(lookbackDays);
            int effectiveMaxTickers = normalizeMaxTickers(maxTickers);
            LocalDate since = LocalDate.now().minusDays(effectiveLookbackDays);
            Set<String> trackedTickers = loadTrackedTickers(since);
            List<String> orderedTickers = trackedTickers.stream()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();

            if (orderedTickers.isEmpty()) {
                log.info("No tracked tickers found for backfill");
                return companyMarketDataService.backfillMissingMarketCaps(List.of(), batchSize, effectiveMaxTickers);
            }

            log.info("Starting market-cap backfill at {} for {} tracked tickers (lookbackDays={}, maxTickers={})",
                    LocalDateTime.now(), orderedTickers.size(), effectiveLookbackDays, effectiveMaxTickers);

            MarketCapBackfillResponse response = companyMarketDataService.backfillMissingMarketCaps(
                    orderedTickers,
                    batchSize,
                    effectiveMaxTickers);

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
        return Optional.ofNullable(form4Repository.findRecentAcquisitions(since))
                .orElse(List.of())
                .stream()
                .filter(Objects::nonNull)
                .filter(form4 -> hasRecentPurchaseActivity(form4, since))
                .map(Form4::getTradingSymbol)
                .map(this::normalizeTicker)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> normalizeTickerSet(Collection<String> rawTickers) {
        if (rawTickers == null || rawTickers.isEmpty()) {
            return new LinkedHashSet<>();
        }

        return rawTickers.stream()
                .map(this::normalizeTicker)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
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
        Set<String> trackedTickers = new LinkedHashSet<>(normalizeTickerSet(sp500Service.getAllTickers()));
        trackedTickers.addAll(loadRecentInsiderTickers(since));
        return trackedTickers;
    }

    private boolean isRecentOpenMarketPurchase(Form4Transaction transaction, LocalDate since) {
        return "P".equalsIgnoreCase(transaction.getTransactionCode())
                && "A".equalsIgnoreCase(transaction.getAcquiredDisposedCode())
                && transaction.getTransactionDate() != null
                && !transaction.getTransactionDate().isBefore(since);
    }

    private int normalizeLookbackDays(int lookbackDays) {
        if (lookbackDays < MIN_LOOKBACK_DAYS) {
            log.warn("Invalid lookbackDays value '{}'; using default {}", lookbackDays, DEFAULT_LOOKBACK_DAYS);
            return DEFAULT_LOOKBACK_DAYS;
        }
        if (lookbackDays > MAX_LOOKBACK_DAYS) {
            log.warn("lookbackDays value '{}' exceeds max {}; using max", lookbackDays, MAX_LOOKBACK_DAYS);
            return MAX_LOOKBACK_DAYS;
        }
        return lookbackDays;
    }

    private int normalizeMaxTickers(int maxTickers) {
        if (maxTickers < 0) {
            log.warn("Negative maxTickers '{}' treated as unlimited", maxTickers);
            return 0;
        }
        return maxTickers;
    }
}
