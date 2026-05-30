package org.jds.edgar4j.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jds.edgar4j.config.AppConstants;
import org.jds.edgar4j.dto.request.InsiderActivityScreenRequest;
import org.jds.edgar4j.dto.response.InsiderActivityCoverageResponse;
import org.jds.edgar4j.dto.response.InsiderActivityResponse;
import org.jds.edgar4j.dto.response.PaginatedResponse;
import org.jds.edgar4j.model.CompanyMarketData;
import org.jds.edgar4j.model.Form4;
import org.jds.edgar4j.model.Form4Transaction;
import org.jds.edgar4j.model.MarketCapSource;
import org.jds.edgar4j.port.Form4DataPort;
import org.jds.edgar4j.service.CompanyMarketDataService;
import org.jds.edgar4j.service.InsiderActivityService;
import org.jds.edgar4j.service.Sp500Service;
import org.jds.edgar4j.util.TickerNormalizer;
import org.jds.edgar4j.util.UsMarketCalendar;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class InsiderActivityServiceImpl implements InsiderActivityService {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int EXPORT_LIMIT = 10_000;
    private static final String VIEW_AGGREGATE = "AGGREGATE";
    private static final String VIEW_TRANSACTION = "TRANSACTION";
    private static final String SIDE_BUY = "BUY";
    private static final String SIDE_SELL = "SELL";

    private final Form4DataPort form4Repository;
    private final CompanyMarketDataService companyMarketDataService;
    private final Sp500Service sp500Service;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public InsiderActivityServiceImpl(
            Form4DataPort form4Repository,
            CompanyMarketDataService companyMarketDataService,
            Sp500Service sp500Service,
            ObjectMapper objectMapper) {
        this(form4Repository, companyMarketDataService, sp500Service, objectMapper, Clock.systemDefaultZone());
    }

    InsiderActivityServiceImpl(
            Form4DataPort form4Repository,
            CompanyMarketDataService companyMarketDataService,
            Sp500Service sp500Service,
            ObjectMapper objectMapper,
            Clock clock) {
        this.form4Repository = form4Repository;
        this.companyMarketDataService = companyMarketDataService;
        this.sp500Service = sp500Service;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public PaginatedResponse<InsiderActivityResponse> screen(InsiderActivityScreenRequest request) {
        ResolvedRequest resolved = resolve(request, false);
        List<InsiderActivityResponse> responses = new ArrayList<>(buildResponses(resolved));
        responses.sort(getComparator(resolved.sortBy(), resolved.sortDir(), resolved.view()));

        int start = Math.min(resolved.page() * resolved.size(), responses.size());
        int end = Math.min(start + resolved.size(), responses.size());

        return PaginatedResponse.of(responses.subList(start, end), resolved.page(), resolved.size(), responses.size());
    }

    @Override
    public byte[] export(InsiderActivityScreenRequest request, String format) {
        ResolvedRequest resolved = resolve(request, true);
        List<InsiderActivityResponse> responses = new ArrayList<>(buildResponses(resolved));
        responses.sort(getComparator(resolved.sortBy(), resolved.sortDir(), resolved.view()));
        List<InsiderActivityResponse> limited = responses.size() > EXPORT_LIMIT
                ? responses.subList(0, EXPORT_LIMIT)
                : responses;

        if ("JSON".equalsIgnoreCase(format)) {
            try {
                return objectMapper.writeValueAsBytes(limited);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Failed to export insider activity as JSON", e);
            }
        }

        return toCsv(limited).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public InsiderActivityCoverageResponse coverage(String form, LocalDate from, LocalDate to) {
        LocalDate normalizedFrom = Objects.requireNonNull(from, "from is required");
        LocalDate normalizedTo = Objects.requireNonNull(to, "to is required");
        if (normalizedTo.isBefore(normalizedFrom)) {
            throw new IllegalArgumentException("to must be on or after from");
        }
        if (ChronoUnit.DAYS.between(normalizedFrom, normalizedTo) + 1 > 366) {
            throw new IllegalArgumentException("Coverage window may span at most one year");
        }

        String normalizedForm = normalizeForm(form);
        if (!"4".equals(normalizedForm)) {
            throw new IllegalArgumentException("Unsupported insider form: " + normalizedForm);
        }

        Map<LocalDate, Long> countsByDate = form4Repository
                .findByTransactionDateBetween(normalizedFrom, normalizedTo, Pageable.unpaged())
                .stream()
                .map(Form4::getTransactionDate)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(date -> date, Collectors.counting()));

        List<InsiderActivityCoverageResponse.DayCount> days = countsByDate.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> InsiderActivityCoverageResponse.DayCount.builder()
                        .date(entry.getKey())
                        .count(entry.getValue())
                        .build())
                .toList();
        long totalFilings = days.stream().mapToLong(InsiderActivityCoverageResponse.DayCount::getCount).sum();

        return InsiderActivityCoverageResponse.builder()
                .form(normalizedForm)
                .from(normalizedFrom)
                .to(normalizedTo)
                .totalFilings(totalFilings)
                .days(days)
                .build();
    }

    private String normalizeForm(String form) {
        return form == null || form.isBlank() ? "4" : form.trim();
    }

    private List<InsiderActivityResponse> buildResponses(ResolvedRequest request) {
        List<Candidate> candidates = form4Repository.findRecentTransactions(request.dateFrom()).stream()
                .filter(Objects::nonNull)
                .flatMap(form4 -> toCandidates(form4, request))
                .filter(candidate -> candidate.transactionDate() != null
                        && !candidate.transactionDate().isBefore(request.dateFrom())
                        && !candidate.transactionDate().isAfter(request.dateTo()))
                .filter(candidate -> request.side().equals(candidate.side()))
                .filter(candidate -> request.transactionCodes().isEmpty()
                        || request.transactionCodes().contains(candidate.transactionCode()))
                .filter(candidate -> request.symbol() == null || request.symbol().equals(candidate.ticker()))
                .filter(candidate -> request.insiderTitle() == null
                        || containsIgnoreCase(candidate.insiderTitle(), request.insiderTitle()))
                .filter(candidate -> request.minPrice() == null
                        || candidate.transactionPrice() != null && candidate.transactionPrice() >= request.minPrice())
                .filter(candidate -> request.minShares() == null
                        || candidate.transactionShares() != null && candidate.transactionShares() >= request.minShares())
                .filter(candidate -> request.view().equals(VIEW_AGGREGATE)
                        || request.minTotalAmount() == null
                        || candidate.transactionValue() != null && candidate.transactionValue() >= request.minTotalAmount())
                .toList();

        Set<String> sp500Tickers = loadSp500Tickers();
        Map<String, Optional<CompanyMarketData>> marketDataCache = new HashMap<>();

        if (VIEW_AGGREGATE.equals(request.view())) {
            return buildAggregateResponses(candidates, request, sp500Tickers, marketDataCache);
        }

        return candidates.stream()
                .map(candidate -> enrichTransaction(candidate, sp500Tickers, marketDataCache))
                .toList();
    }

    private List<InsiderActivityResponse> buildAggregateResponses(
            List<Candidate> candidates,
            ResolvedRequest request,
            Set<String> sp500Tickers,
            Map<String, Optional<CompanyMarketData>> marketDataCache) {
        Map<String, List<Candidate>> groups = candidates.stream()
                .collect(Collectors.groupingBy(
                        candidate -> candidate.ticker() + "|" + blankToEmpty(candidate.cik()),
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<InsiderActivityResponse> responses = new ArrayList<>();
        for (List<Candidate> group : groups.values()) {
            float totalValue = (float) group.stream()
                    .map(Candidate::transactionValue)
                    .filter(Objects::nonNull)
                    .mapToDouble(Float::doubleValue)
                    .sum();
            float totalShares = (float) group.stream()
                    .map(Candidate::transactionShares)
                    .filter(Objects::nonNull)
                    .mapToDouble(Float::doubleValue)
                    .sum();
            Set<String> distinctInsiders = group.stream()
                    .map(this::distinctInsiderKey)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            if (request.minTotalAmount() != null && totalValue < request.minTotalAmount()) {
                continue;
            }
            if (request.minShares() != null && totalShares < request.minShares()) {
                continue;
            }
            if (request.minInsiderCount() != null && distinctInsiders.size() < request.minInsiderCount()) {
                continue;
            }

            Candidate latest = group.stream()
                    .max(Comparator.comparing(Candidate::transactionDate))
                    .orElseThrow();
            Optional<CompanyMarketData> marketData = marketDataCache.computeIfAbsent(
                    latest.ticker(),
                    companyMarketDataService::getStoredMarketData);
            Double currentPrice = marketData.map(CompanyMarketData::getCurrentPrice).orElse(null);

            responses.add(InsiderActivityResponse.builder()
                    .view(VIEW_AGGREGATE)
                    .side(request.side())
                    .ticker(latest.ticker())
                    .companyName(latest.companyName())
                    .cik(latest.cik())
                    .latestTransactionDate(latest.transactionDate())
                    .insiderCount(distinctInsiders.size())
                    .transactionCount(group.size())
                    .totalShares(totalShares)
                    .averagePrice(totalShares > 0f && totalValue > 0f ? totalValue / totalShares : null)
                    .totalValue(totalValue)
                    .currentPrice(currentPrice)
                    .percentChange(calculatePercentChange(currentPrice, latest.transactionPrice()))
                    .marketCap(marketData.map(CompanyMarketData::getMarketCap).orElse(null))
                    .marketCapSource(marketData.map(this::resolveMarketCapSource).orElse(null))
                    .sp500(sp500Tickers.contains(latest.ticker()))
                    .transactionCodes(group.stream()
                            .map(Candidate::transactionCode)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toCollection(LinkedHashSet::new)))
                    .build());
        }

        return responses;
    }

    private InsiderActivityResponse enrichTransaction(
            Candidate candidate,
            Set<String> sp500Tickers,
            Map<String, Optional<CompanyMarketData>> marketDataCache) {
        Optional<CompanyMarketData> marketData = marketDataCache.computeIfAbsent(
                candidate.ticker(),
                companyMarketDataService::getStoredMarketData);
        Double currentPrice = marketData.map(CompanyMarketData::getCurrentPrice).orElse(null);

        return InsiderActivityResponse.builder()
                .view(VIEW_TRANSACTION)
                .side(candidate.side())
                .ticker(candidate.ticker())
                .companyName(candidate.companyName())
                .cik(candidate.cik())
                .latestTransactionDate(candidate.transactionDate())
                .transactionDate(candidate.transactionDate())
                .insiderName(candidate.insiderName())
                .insiderTitle(candidate.insiderTitle())
                .ownerType(candidate.ownerType())
                .insiderCount(1)
                .transactionCount(1)
                .transactionShares(candidate.transactionShares())
                .transactionPrice(candidate.transactionPrice())
                .transactionValue(candidate.transactionValue())
                .totalShares(candidate.transactionShares())
                .totalValue(candidate.transactionValue())
                .currentPrice(currentPrice)
                .percentChange(calculatePercentChange(currentPrice, candidate.transactionPrice()))
                .marketCap(marketData.map(CompanyMarketData::getMarketCap).orElse(null))
                .marketCapSource(marketData.map(this::resolveMarketCapSource).orElse(null))
                .sp500(sp500Tickers.contains(candidate.ticker()))
                .accessionNumber(candidate.accessionNumber())
                .transactionCode(candidate.transactionCode())
                .transactionCodes(candidate.transactionCode() == null ? Set.of() : Set.of(candidate.transactionCode()))
                .build();
    }

    private Stream<Candidate> toCandidates(Form4 form4, ResolvedRequest request) {
        String ticker = TickerNormalizer.normalize(form4.getTradingSymbol());
        if (ticker == null) {
            return Stream.empty();
        }

        if (form4.getTransactions() == null || form4.getTransactions().isEmpty()) {
            return toCandidate(form4, null, ticker, request).stream();
        }

        return form4.getTransactions().stream()
                .filter(Objects::nonNull)
                .flatMap(transaction -> toCandidate(form4, transaction, ticker, request).stream());
    }

    private Optional<Candidate> toCandidate(Form4 form4, Form4Transaction transaction, String ticker, ResolvedRequest request) {
        String acquiredDisposedCode = transaction != null
                ? transaction.getAcquiredDisposedCode()
                : form4.getAcquiredDisposedCode();
        String side = resolveSide(acquiredDisposedCode);
        if (side == null) {
            return Optional.empty();
        }

        LocalDate transactionDate = transaction != null && transaction.getTransactionDate() != null
                ? transaction.getTransactionDate()
                : form4.getTransactionDate();
        Float transactionShares = transaction != null && transaction.getTransactionShares() != null
                ? transaction.getTransactionShares()
                : form4.getTransactionShares();
        Float transactionPrice = transaction != null && transaction.getTransactionPricePerShare() != null
                ? transaction.getTransactionPricePerShare()
                : form4.getTransactionPricePerShare();
        Float transactionValue = resolveTransactionValue(form4, transaction, transactionShares, transactionPrice);

        String transactionCode = transaction != null && blankToNull(transaction.getTransactionCode()) != null
                ? transaction.getTransactionCode().trim().toUpperCase(Locale.ROOT)
                : SIDE_BUY.equals(side) ? "P" : "S";

        return Optional.of(new Candidate(
                ticker,
                blankToNull(form4.getIssuerName()),
                blankToNull(form4.getCik()),
                blankToNull(form4.getRptOwnerCik()),
                blankToNull(form4.getRptOwnerName()),
                blankToNull(form4.getOfficerTitle()),
                resolveOwnerType(form4),
                transactionDate,
                transactionShares,
                transactionPrice,
                transactionValue,
                transactionCode,
                side,
                form4.getAccessionNumber()));
    }

    private ResolvedRequest resolve(InsiderActivityScreenRequest request, boolean export) {
        InsiderActivityScreenRequest source = request == null ? new InsiderActivityScreenRequest() : request;
        Preset preset = Preset.from(source.getPreset());
        LocalDate today = LocalDate.now(clock);
        LocalDate dateFrom = source.getDateFrom() != null ? source.getDateFrom() : preset.defaultDateFrom(today);
        LocalDate dateTo = source.getDateTo() != null ? source.getDateTo() : today;
        String side = normalizeOption(source.getSide(), preset.side);
        String view = normalizeView(source.getView(), preset.view);
        Set<String> transactionCodes = normalizeCodes(source.getTransactionCodes(), preset.codes);
        int page = export ? 0 : Math.max(AppConstants.DEFAULT_PAGE, source.getPage());
        int size = export ? EXPORT_LIMIT : sanitizePageSize(source.getSize());

        return new ResolvedRequest(
                preset.name(),
                view,
                side,
                transactionCodes,
                dateFrom,
                dateTo,
                TickerNormalizer.normalize(source.getSymbol()),
                normalizeThreshold(source.getMinPrice()),
                normalizeThreshold(source.getMinShares()),
                normalizeThreshold(source.getMinTotalAmount() != null ? source.getMinTotalAmount() : preset.minTotalAmount),
                normalizeMinInsiderCount(source.getMinInsiderCount(), preset.minInsiderCount),
                blankToNull(source.getInsiderTitle()),
                blankToNull(source.getSortBy()),
                normalizeSortDir(source.getSortDir()),
                page,
                size);
    }

    private Comparator<InsiderActivityResponse> getComparator(String sortBy, String sortDir, String view) {
        boolean descending = !"asc".equalsIgnoreCase(sortDir);
        Comparator<InsiderActivityResponse> comparator = switch (sortBy == null ? "" : sortBy) {
            case "ticker" -> Comparator.comparing(InsiderActivityResponse::getTicker, stringComparator(descending));
            case "insiderCount" -> Comparator.comparing(InsiderActivityResponse::getInsiderCount, integerComparator(descending));
            case "transactionCount" -> Comparator.comparing(InsiderActivityResponse::getTransactionCount, integerComparator(descending));
            case "totalShares" -> Comparator.comparing(InsiderActivityResponse::getTotalShares, floatComparator(descending));
            case "marketCap" -> Comparator.comparing(InsiderActivityResponse::getMarketCap, doubleComparator(descending));
            case "percentChange" -> Comparator.comparing(InsiderActivityResponse::getPercentChange, doubleComparator(descending));
            case "transactionDate" -> Comparator.comparing(
                    VIEW_AGGREGATE.equals(view) ? InsiderActivityResponse::getLatestTransactionDate : InsiderActivityResponse::getTransactionDate,
                    dateComparator(descending));
            default -> Comparator.comparing(
                    VIEW_AGGREGATE.equals(view) ? InsiderActivityResponse::getTotalValue : InsiderActivityResponse::getTransactionValue,
                    floatComparator(descending));
        };
        return comparator
                .thenComparing(InsiderActivityResponse::getLatestTransactionDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(InsiderActivityResponse::getTicker, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
    }

    private Float resolveTransactionValue(
            Form4 form4,
            Form4Transaction transaction,
            Float transactionShares,
            Float transactionPrice) {
        if (transaction != null) {
            if (transaction.getTransactionValue() != null && transaction.getTransactionValue() > 0f) {
                return transaction.getTransactionValue();
            }
            return deriveTransactionValue(transactionShares, transactionPrice);
        }

        if (form4.getTransactionValue() != null && form4.getTransactionValue() > 0f) {
            return form4.getTransactionValue();
        }
        return deriveTransactionValue(transactionShares, transactionPrice);
    }

    private Float deriveTransactionValue(Float transactionShares, Float transactionPrice) {
        if (transactionShares == null || transactionPrice == null) {
            return null;
        }
        return transactionShares * transactionPrice;
    }

    private Comparator<String> stringComparator(boolean descending) {
        return Comparator.nullsLast(descending
                ? String.CASE_INSENSITIVE_ORDER.reversed()
                : String.CASE_INSENSITIVE_ORDER);
    }

    private Comparator<Integer> integerComparator(boolean descending) {
        Comparator<Integer> comparator = descending ? Comparator.reverseOrder() : Comparator.naturalOrder();
        return Comparator.nullsLast(comparator);
    }

    private Comparator<Float> floatComparator(boolean descending) {
        Comparator<Float> comparator = descending ? Comparator.reverseOrder() : Comparator.naturalOrder();
        return Comparator.nullsLast(comparator);
    }

    private Comparator<Double> doubleComparator(boolean descending) {
        Comparator<Double> comparator = descending ? Comparator.reverseOrder() : Comparator.naturalOrder();
        return Comparator.nullsLast(comparator);
    }

    private Comparator<LocalDate> dateComparator(boolean descending) {
        Comparator<LocalDate> comparator = descending ? Comparator.reverseOrder() : Comparator.naturalOrder();
        return Comparator.nullsLast(comparator);
    }

    private String toCsv(List<InsiderActivityResponse> rows) {
        StringBuilder csv = new StringBuilder();
        csv.append("view,side,ticker,companyName,cik,latestTransactionDate,transactionDate,insiderName,insiderTitle,ownerType,")
                .append("insiderCount,transactionCount,totalShares,transactionShares,averagePrice,transactionPrice,totalValue,")
                .append("transactionValue,currentPrice,percentChange,marketCap,marketCapSource,sp500,accessionNumber,transactionCode,transactionCodes\n");
        for (InsiderActivityResponse row : rows) {
            csv.append(csv(row.getView())).append(',')
                    .append(csv(row.getSide())).append(',')
                    .append(csv(row.getTicker())).append(',')
                    .append(csv(row.getCompanyName())).append(',')
                    .append(csv(row.getCik())).append(',')
                    .append(csv(row.getLatestTransactionDate())).append(',')
                    .append(csv(row.getTransactionDate())).append(',')
                    .append(csv(row.getInsiderName())).append(',')
                    .append(csv(row.getInsiderTitle())).append(',')
                    .append(csv(row.getOwnerType())).append(',')
                    .append(csv(row.getInsiderCount())).append(',')
                    .append(csv(row.getTransactionCount())).append(',')
                    .append(csv(row.getTotalShares())).append(',')
                    .append(csv(row.getTransactionShares())).append(',')
                    .append(csv(row.getAveragePrice())).append(',')
                    .append(csv(row.getTransactionPrice())).append(',')
                    .append(csv(row.getTotalValue())).append(',')
                    .append(csv(row.getTransactionValue())).append(',')
                    .append(csv(row.getCurrentPrice())).append(',')
                    .append(csv(row.getPercentChange())).append(',')
                    .append(csv(row.getMarketCap())).append(',')
                    .append(csv(row.getMarketCapSource())).append(',')
                    .append(csv(row.isSp500())).append(',')
                    .append(csv(row.getAccessionNumber())).append(',')
                    .append(csv(row.getTransactionCode())).append(',')
                    .append(csv(row.getTransactionCodes() == null ? "" : String.join("|", row.getTransactionCodes())))
                    .append('\n');
        }
        return csv.toString();
    }

    private String csv(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private Set<String> loadSp500Tickers() {
        return sp500Service.getAllTickers().stream()
                .map(TickerNormalizer::normalize)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private String distinctInsiderKey(Candidate candidate) {
        if (candidate.ownerCik() != null) {
            return "CIK:" + candidate.ownerCik();
        }
        return candidate.insiderName() == null ? null : "NAME:" + candidate.insiderName().toUpperCase(Locale.ROOT);
    }

    private String resolveSide(String acquiredDisposedCode) {
        if ("A".equalsIgnoreCase(acquiredDisposedCode)) {
            return SIDE_BUY;
        }
        if ("D".equalsIgnoreCase(acquiredDisposedCode)) {
            return SIDE_SELL;
        }
        return null;
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

    private MarketCapSource resolveMarketCapSource(CompanyMarketData marketData) {
        if (marketData == null || marketData.getMarketCap() == null || marketData.getMarketCap() <= 0d) {
            return null;
        }
        return marketData.getMarketCapSource() != null ? marketData.getMarketCapSource() : MarketCapSource.UNKNOWN;
    }

    private Double calculatePercentChange(Double currentPrice, Float transactionPrice) {
        if (currentPrice == null || transactionPrice == null || transactionPrice <= 0f) {
            return null;
        }
        return Math.round((((currentPrice - transactionPrice) / transactionPrice) * 100d) * 100d) / 100d;
    }

    private boolean containsIgnoreCase(String value, String needle) {
        return value != null && needle != null && value.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private String normalizeView(String value, String fallback) {
        String normalized = normalizeOption(value, fallback);
        return VIEW_AGGREGATE.equals(normalized) || VIEW_TRANSACTION.equals(normalized) ? normalized : fallback;
    }

    private String normalizeOption(String value, String fallback) {
        String normalized = blankToNull(value);
        return normalized == null ? fallback : normalized.toUpperCase(Locale.ROOT);
    }

    private Set<String> normalizeCodes(Set<String> value, Set<String> fallback) {
        Set<String> source = value == null || value.isEmpty() ? fallback : value;
        return source.stream()
                .map(this::blankToNull)
                .filter(Objects::nonNull)
                .flatMap(code -> Stream.of(code.split(",")))
                .map(this::blankToNull)
                .filter(Objects::nonNull)
                .map(code -> code.toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String normalizeSortDir(String sortDir) {
        return "asc".equalsIgnoreCase(sortDir) ? "asc" : "desc";
    }

    private Double normalizeThreshold(Double value) {
        return value != null && value > 0d ? value : null;
    }

    private Integer normalizeMinInsiderCount(Integer requestValue, Integer presetValue) {
        Integer value = requestValue != null ? requestValue : presetValue;
        return value != null && value > 0 ? value : null;
    }

    private int sanitizePageSize(int size) {
        if (size < AppConstants.MIN_PAGE_SIZE) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, AppConstants.MAX_PAGE_SIZE);
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    private enum Preset {
        LATEST_PURCHASES(SIDE_BUY, VIEW_TRANSACTION, Set.of("P"), 2, null, null),
        LATEST_SALES(SIDE_SELL, VIEW_TRANSACTION, Set.of("S"), 2, null, null),
        MULTI_INSIDER_BUYS(SIDE_BUY, VIEW_AGGREGATE, Set.of("P"), null, 3, 2),
        MULTI_INSIDER_SELLS(SIDE_SELL, VIEW_AGGREGATE, Set.of("S"), null, 3, 2),
        MILLION_DOLLAR_BUYS(SIDE_BUY, VIEW_AGGREGATE, Set.of("P"), null, null, null, 1_000_000d),
        MILLION_DOLLAR_SELLS(SIDE_SELL, VIEW_AGGREGATE, Set.of("S"), null, null, null, 1_000_000d);

        private final String side;
        private final String view;
        private final Set<String> codes;
        private final Integer tradingDays;
        private final Integer months;
        private final Integer minInsiderCount;
        private final Double minTotalAmount;

        Preset(String side, String view, Set<String> codes, Integer tradingDays, Integer months, Integer minInsiderCount) {
            this(side, view, codes, tradingDays, months, minInsiderCount, null);
        }

        Preset(String side, String view, Set<String> codes, Integer tradingDays, Integer months, Integer minInsiderCount, Double minTotalAmount) {
            this.side = side;
            this.view = view;
            this.codes = codes;
            this.tradingDays = tradingDays;
            this.months = months;
            this.minInsiderCount = minInsiderCount;
            this.minTotalAmount = minTotalAmount;
        }

        private LocalDate defaultDateFrom(LocalDate today) {
            if (tradingDays != null) {
                return UsMarketCalendar.startDateForRecentTradingDays(today, tradingDays);
            }
            if (months != null) {
                return today.minusMonths(months);
            }
            return today.minusDays(30);
        }

        static Preset from(String value) {
            if (value == null || value.isBlank()) {
                return LATEST_PURCHASES;
            }
            try {
                return Preset.valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return LATEST_PURCHASES;
            }
        }
    }

    private record ResolvedRequest(
            String preset,
            String view,
            String side,
            Set<String> transactionCodes,
            LocalDate dateFrom,
            LocalDate dateTo,
            String symbol,
            Double minPrice,
            Double minShares,
            Double minTotalAmount,
            Integer minInsiderCount,
            String insiderTitle,
            String sortBy,
            String sortDir,
            int page,
            int size) {
    }

    private record Candidate(
            String ticker,
            String companyName,
            String cik,
            String ownerCik,
            String insiderName,
            String insiderTitle,
            String ownerType,
            LocalDate transactionDate,
            Float transactionShares,
            Float transactionPrice,
            Float transactionValue,
            String transactionCode,
            String side,
            String accessionNumber) {
    }
}
