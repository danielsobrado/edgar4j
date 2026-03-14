package org.jds.edgar4j.service.impl;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jds.edgar4j.config.AppConstants;
import org.jds.edgar4j.dto.response.InsiderPurchaseResponse;
import org.jds.edgar4j.dto.response.InsiderPurchaseSummary;
import org.jds.edgar4j.dto.response.PaginatedResponse;
import org.jds.edgar4j.model.CompanyMarketData;
import org.jds.edgar4j.model.Form4;
import org.jds.edgar4j.model.Form4Transaction;
import org.jds.edgar4j.model.MarketCapSource;
import org.jds.edgar4j.port.Form4DataPort;
import org.jds.edgar4j.service.CompanyMarketDataService;
import org.jds.edgar4j.service.InsiderPurchaseService;
import org.jds.edgar4j.service.Sp500Service;
import org.jds.edgar4j.util.TickerNormalizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class InsiderPurchaseServiceImpl implements InsiderPurchaseService {

    private static final int DEFAULT_LOOKBACK_DAYS = 30;
    private static final int DEFAULT_TOP_LIMIT = 10;
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final String DEFAULT_SORT_BY = "percentChange";

    private final Form4DataPort form4Repository;
    private final CompanyMarketDataService companyMarketDataService;
    private final Sp500Service sp500Service;
    private final Clock clock;

    @Autowired
    public InsiderPurchaseServiceImpl(
            Form4DataPort form4Repository,
            CompanyMarketDataService companyMarketDataService,
            Sp500Service sp500Service) {
        this(form4Repository, companyMarketDataService, sp500Service, Clock.systemDefaultZone());
    }

    InsiderPurchaseServiceImpl(
            Form4DataPort form4Repository,
            CompanyMarketDataService companyMarketDataService,
            Sp500Service sp500Service,
            Clock clock) {
        this.form4Repository = form4Repository;
        this.companyMarketDataService = companyMarketDataService;
        this.sp500Service = sp500Service;
        this.clock = clock;
    }

    @Override
    public PaginatedResponse<InsiderPurchaseResponse> getRecentInsiderPurchases(
            int lookbackDays,
            Double minMarketCap,
            boolean sp500Only,
            Double minTransactionValue,
            String sortBy,
            String sortDir,
            int page,
            int size) {

        int sanitizedLookbackDays = sanitizeLookbackDays(lookbackDays);
        int sanitizedPage = Math.max(AppConstants.DEFAULT_PAGE, page);
        int sanitizedSize = sanitizePageSize(size);
        Double normalizedMinMarketCap = normalizeThreshold(minMarketCap);
        Double normalizedMinTransactionValue = normalizeThreshold(minTransactionValue);

        LocalDate since = LocalDate.now(clock).minusDays(sanitizedLookbackDays);
        List<PurchaseCandidate> candidates = extractOpenMarketPurchases(form4Repository.findRecentAcquisitions(since), since);
        Set<String> sp500Tickers = loadSp500Tickers();
        Map<String, Optional<CompanyMarketData>> marketDataCache = new HashMap<>();

        List<InsiderPurchaseResponse> responses = buildResponses(
                candidates,
                sp500Tickers,
                marketDataCache,
                normalizedMinMarketCap,
                sp500Only,
                normalizedMinTransactionValue);

        responses.sort(getComparator(sortBy, sortDir));

        int start = Math.min(sanitizedPage * sanitizedSize, responses.size());
        int end = Math.min(start + sanitizedSize, responses.size());

        return PaginatedResponse.of(
                responses.subList(start, end),
                sanitizedPage,
                sanitizedSize,
                responses.size());
    }

    @Override
    public List<InsiderPurchaseResponse> getTopInsiderPurchases(int limit) {
        return getRecentInsiderPurchases(
                DEFAULT_LOOKBACK_DAYS,
                null,
                false,
                null,
                DEFAULT_SORT_BY,
                AppConstants.DEFAULT_SORT_DIRECTION,
                AppConstants.DEFAULT_PAGE,
                sanitizeLimit(limit))
                .getContent();
    }

    @Override
    public InsiderPurchaseSummary getSummary(int lookbackDays) {
        LocalDate since = LocalDate.now(clock).minusDays(sanitizeLookbackDays(lookbackDays));
        List<PurchaseCandidate> candidates = extractOpenMarketPurchases(form4Repository.findRecentAcquisitions(since), since);
        Set<String> sp500Tickers = loadSp500Tickers();
        Map<String, Optional<CompanyMarketData>> marketDataCache = new HashMap<>();

        List<InsiderPurchaseResponse> responses = buildResponses(
                candidates,
                sp500Tickers,
                marketDataCache,
                null,
                false,
                null);

        List<Double> percentChanges = responses.stream()
                .map(InsiderPurchaseResponse::getPercentChange)
                .filter(Objects::nonNull)
                .toList();

        double averagePercentChange = percentChanges.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0d);

        return InsiderPurchaseSummary.builder()
                .totalPurchases(responses.size())
                .uniqueCompanies((int) responses.stream()
                        .map(InsiderPurchaseResponse::getTicker)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet())
                        .size())
                .totalPurchaseValue(responses.stream()
                        .map(InsiderPurchaseResponse::getTransactionValue)
                        .filter(Objects::nonNull)
                        .mapToDouble(Float::doubleValue)
                        .sum())
                .averagePercentChange(roundToTwoDecimals(averagePercentChange))
                .positiveChangeCount((int) percentChanges.stream().filter(value -> value > 0d).count())
                .negativeChangeCount((int) percentChanges.stream().filter(value -> value < 0d).count())
                .build();
    }

    private List<InsiderPurchaseResponse> buildResponses(
            List<PurchaseCandidate> candidates,
            Set<String> sp500Tickers,
            Map<String, Optional<CompanyMarketData>> marketDataCache,
            Double minMarketCap,
            boolean sp500Only,
            Double minTransactionValue) {
        List<InsiderPurchaseResponse> responses = new ArrayList<>();

        for (PurchaseCandidate candidate : candidates) {
            try {
                InsiderPurchaseResponse response = buildResponse(
                        candidate,
                        sp500Tickers,
                        marketDataCache,
                        minMarketCap,
                        sp500Only,
                        minTransactionValue);

                if (response != null) {
                    responses.add(response);
                }
            } catch (Exception e) {
                log.debug("Failed to aggregate insider purchase for accession {}: {}",
                        candidate.form4().getAccessionNumber(),
                        e.getMessage(),
                        e);
            }
        }

        return responses;
    }

    private InsiderPurchaseResponse buildResponse(
            PurchaseCandidate candidate,
            Set<String> sp500Tickers,
            Map<String, Optional<CompanyMarketData>> marketDataCache,
            Double minMarketCap,
            boolean sp500Only,
            Double minTransactionValue) {
        Form4 form4 = candidate.form4();
        String ticker = normalizeTicker(form4.getTradingSymbol());
        if (ticker == null) {
            return null;
        }

        boolean isSp500 = sp500Tickers.contains(ticker);
        if (sp500Only && !isSp500) {
            return null;
        }

        Optional<CompanyMarketData> marketData = marketDataCache.computeIfAbsent(ticker, companyMarketDataService::getStoredMarketData);
        Double currentPrice = marketData.map(CompanyMarketData::getCurrentPrice).orElse(null);
        Double marketCap = marketData.map(CompanyMarketData::getMarketCap).orElse(null);
        MarketCapSource marketCapSource = marketData
                .map(this::resolveMarketCapSource)
                .orElse(null);

        if (minMarketCap != null && !meetsMarketCapFilter(marketCap, minMarketCap, isSp500, sp500Only)) {
            return null;
        }

        Float transactionValue = resolveTransactionValue(candidate);
        if (minTransactionValue != null
                && (transactionValue == null || transactionValue.doubleValue() < minTransactionValue)) {
            return null;
        }

        Float purchasePrice = resolvePurchasePrice(candidate);

        return InsiderPurchaseResponse.builder()
                .ticker(ticker)
                .companyName(blankToNull(form4.getIssuerName()))
                .cik(blankToNull(form4.getCik()))
                .insiderName(blankToNull(form4.getRptOwnerName()))
                .insiderTitle(blankToNull(form4.getOfficerTitle()))
                .ownerType(resolveOwnerType(form4))
                .transactionDate(resolveTransactionDate(candidate))
                .purchasePrice(purchasePrice)
                .transactionShares(resolveTransactionShares(candidate))
                .transactionValue(transactionValue)
                .currentPrice(currentPrice)
                .percentChange(calculatePercentChange(currentPrice, purchasePrice))
                .marketCap(marketCap)
                .marketCapSource(marketCapSource)
                .sp500(isSp500)
                .accessionNumber(form4.getAccessionNumber())
                .transactionCode(resolveTransactionCode(candidate))
                .build();
    }

    private boolean meetsMarketCapFilter(Double marketCap, Double minMarketCap, boolean isSp500, boolean sp500Only) {
        if (minMarketCap == null) {
            return true;
        }

        if (marketCap != null) {
            return marketCap >= minMarketCap;
        }

        return sp500Only && isSp500;
    }

    private List<PurchaseCandidate> extractOpenMarketPurchases(List<Form4> acquisitions, LocalDate since) {
        if (acquisitions == null || acquisitions.isEmpty()) {
            return List.of();
        }

        return acquisitions.stream()
                .filter(Objects::nonNull)
                .flatMap(form4 -> toPurchaseCandidates(form4, since))
                .toList();
    }

    private Stream<PurchaseCandidate> toPurchaseCandidates(Form4 form4, LocalDate since) {
        if (form4.getTransactions() == null || form4.getTransactions().isEmpty()) {
            return isFallbackPurchase(form4) && isWithinLookback(form4.getTransactionDate(), since)
                    ? Stream.of(new PurchaseCandidate(form4, null))
                    : Stream.empty();
        }

        return form4.getTransactions().stream()
                .filter(Objects::nonNull)
                .filter(this::isOpenMarketPurchase)
                .filter(transaction -> isWithinLookback(transaction.getTransactionDate(), since))
                .map(transaction -> new PurchaseCandidate(form4, transaction));
    }

    private boolean isOpenMarketPurchase(Form4Transaction transaction) {
        return transaction != null
                && "P".equalsIgnoreCase(transaction.getTransactionCode())
                && "A".equalsIgnoreCase(transaction.getAcquiredDisposedCode());
    }

    private boolean isFallbackPurchase(Form4 form4) {
        return "A".equalsIgnoreCase(form4.getAcquiredDisposedCode());
    }

    private boolean isWithinLookback(LocalDate transactionDate, LocalDate since) {
        return transactionDate != null && !transactionDate.isBefore(since);
    }

    private Set<String> loadSp500Tickers() {
        return sp500Service.getAllTickers().stream()
                .map(this::normalizeTicker)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private String resolveOwnerType(Form4 form4) {
        if (blankToNull(form4.getOwnerType()) != null) {
            return form4.getOwnerType();
        }
        if (form4.isDirector()) {
            return "Director";
        }
        if (form4.isOfficer()) {
            return "Officer";
        }
        if (form4.isTenPercentOwner()) {
            return "10% Owner";
        }
        if (form4.isOther()) {
            return "Other";
        }
        return "Unknown";
    }

    private LocalDate resolveTransactionDate(PurchaseCandidate candidate) {
        return candidate.transaction() != null && candidate.transaction().getTransactionDate() != null
                ? candidate.transaction().getTransactionDate()
                : candidate.form4().getTransactionDate();
    }

    private Float resolveTransactionShares(PurchaseCandidate candidate) {
        return candidate.transaction() != null && candidate.transaction().getTransactionShares() != null
                ? candidate.transaction().getTransactionShares()
                : candidate.form4().getTransactionShares();
    }

    private Float resolvePurchasePrice(PurchaseCandidate candidate) {
        Float purchasePrice = candidate.transaction() != null
                ? candidate.transaction().getTransactionPricePerShare()
                : null;
        if (purchasePrice != null && purchasePrice > 0f) {
            return purchasePrice;
        }
        return candidate.form4().getTransactionPricePerShare();
    }

    private Float resolveTransactionValue(PurchaseCandidate candidate) {
        Form4Transaction transaction = candidate.transaction();
        if (transaction != null) {
            if (transaction.getTransactionValue() != null && transaction.getTransactionValue() > 0f) {
                return transaction.getTransactionValue();
            }

            Float derivedValue = deriveTransactionValue(
                    transaction.getTransactionShares(),
                    transaction.getTransactionPricePerShare());
            if (derivedValue != null) {
                return derivedValue;
            }
        }

        if (candidate.form4().getTransactionValue() != null && candidate.form4().getTransactionValue() > 0f) {
            return candidate.form4().getTransactionValue();
        }

        return deriveTransactionValue(
                candidate.form4().getTransactionShares(),
                candidate.form4().getTransactionPricePerShare());
    }

    private Float deriveTransactionValue(Float transactionShares, Float transactionPricePerShare) {
        if (transactionShares == null || transactionPricePerShare == null) {
            return null;
        }
        return transactionShares * transactionPricePerShare;
    }

    private String resolveTransactionCode(PurchaseCandidate candidate) {
        if (candidate.transaction() != null && blankToNull(candidate.transaction().getTransactionCode()) != null) {
            return candidate.transaction().getTransactionCode();
        }
        return isFallbackPurchase(candidate.form4()) ? "P" : null;
    }

    private Double calculatePercentChange(Double currentPrice, Float purchasePrice) {
        if (currentPrice == null || purchasePrice == null || purchasePrice <= 0f) {
            return null;
        }
        return ((currentPrice - purchasePrice) / purchasePrice) * 100d;
    }

    private Comparator<InsiderPurchaseResponse> getComparator(String sortBy, String sortDir) {
        boolean descending = !"asc".equalsIgnoreCase(sortDir);
        Comparator<String> stringComparator = descending
                ? String.CASE_INSENSITIVE_ORDER.reversed()
                : String.CASE_INSENSITIVE_ORDER;
        Comparator<LocalDate> dateComparator = descending
                ? Comparator.reverseOrder()
                : Comparator.naturalOrder();
        Comparator<Float> floatComparator = descending
                ? Comparator.reverseOrder()
                : Comparator.naturalOrder();
        Comparator<Double> doubleComparator = descending
                ? Comparator.reverseOrder()
                : Comparator.naturalOrder();

        Comparator<InsiderPurchaseResponse> primaryComparator = switch (normalizeSortBy(sortBy)) {
            case "ticker" -> Comparator.comparing(
                    InsiderPurchaseResponse::getTicker,
                    Comparator.nullsLast(stringComparator));
            case "transactionDate" -> Comparator.comparing(
                    InsiderPurchaseResponse::getTransactionDate,
                    Comparator.nullsLast(dateComparator));
            case "transactionValue" -> Comparator.comparing(
                    InsiderPurchaseResponse::getTransactionValue,
                    Comparator.nullsLast(floatComparator));
            case "marketCap" -> Comparator.comparing(
                    InsiderPurchaseResponse::getMarketCap,
                    Comparator.nullsLast(doubleComparator));
            default -> Comparator.comparing(
                    InsiderPurchaseResponse::getPercentChange,
                    Comparator.nullsLast(doubleComparator));
        };

        return primaryComparator
                .thenComparing(InsiderPurchaseResponse::getTransactionDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(InsiderPurchaseResponse::getTicker, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(InsiderPurchaseResponse::getAccessionNumber, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
    }

    private String normalizeSortBy(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return DEFAULT_SORT_BY;
        }

        return switch (sortBy.trim()) {
            case "ticker", "transactionDate", "transactionValue", "marketCap", "percentChange" -> sortBy.trim();
            default -> DEFAULT_SORT_BY;
        };
    }

    private Double normalizeThreshold(Double threshold) {
        return threshold != null && threshold > 0d ? threshold : null;
    }

    private int sanitizeLookbackDays(int lookbackDays) {
        return lookbackDays > 0 ? lookbackDays : DEFAULT_LOOKBACK_DAYS;
    }

    private int sanitizePageSize(int size) {
        if (size < AppConstants.MIN_PAGE_SIZE) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, AppConstants.MAX_PAGE_SIZE);
    }

    private int sanitizeLimit(int limit) {
        if (limit < AppConstants.MIN_PAGE_SIZE) {
            return DEFAULT_TOP_LIMIT;
        }
        return Math.min(limit, AppConstants.MAX_PAGE_SIZE);
    }

    private String normalizeTicker(String ticker) {
        return TickerNormalizer.normalize(ticker);
    }

    private MarketCapSource resolveMarketCapSource(CompanyMarketData marketData) {
        if (marketData == null || marketData.getMarketCap() == null || marketData.getMarketCap() <= 0d) {
            return null;
        }

        return marketData.getMarketCapSource() != null
                ? marketData.getMarketCapSource()
                : MarketCapSource.UNKNOWN;
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private double roundToTwoDecimals(double value) {
        return Math.round(value * 100d) / 100d;
    }

    private record PurchaseCandidate(Form4 form4, Form4Transaction transaction) {
    }
}
