package org.jds.edgar4j.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jds.edgar4j.config.AppConstants;
import org.jds.edgar4j.dto.request.PoliticalTradeScreenRequest;
import org.jds.edgar4j.dto.request.PoliticalTradeSyncRequest;
import org.jds.edgar4j.dto.response.PaginatedResponse;
import org.jds.edgar4j.dto.response.PoliticalTradeResponse;
import org.jds.edgar4j.dto.response.PoliticalTradeSyncResponse;
import org.jds.edgar4j.exception.PoliticalTradeSyncException;
import org.jds.edgar4j.integration.PoliticalTradeSource;
import org.jds.edgar4j.integration.PoliticalTradeSourceRequest;
import org.jds.edgar4j.model.PoliticalTrade;
import org.jds.edgar4j.port.PoliticalTradeDataPort;
import org.jds.edgar4j.service.PoliticalTradeService;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PoliticalTradeServiceImpl implements PoliticalTradeService {

    private static final String DEFAULT_ASSET_TYPE = "stock";
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int DEFAULT_SYNC_PAGES = 25;
    private static final int MAX_SYNC_PAGES = 250;
    private static final int MAX_UNFORCED_SYNC_PAGES = 25;
    private static final int EXPORT_LIMIT = 10_000;
    private static final List<String> ALL_ASSET_SYNC_TYPES = List.of(
            "stock",
            "etf",
            "mutual-fund",
            "crypto",
            "corporate-bond");

    private final PoliticalTradeDataPort politicalTradeDataPort;
    private final PoliticalTradeSource politicalTradeSource;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final AtomicBoolean syncInProgress = new AtomicBoolean(false);

    @Autowired
    public PoliticalTradeServiceImpl(
            PoliticalTradeDataPort politicalTradeDataPort,
            PoliticalTradeSource politicalTradeSource,
            ObjectMapper objectMapper) {
        this(politicalTradeDataPort, politicalTradeSource, objectMapper, Clock.systemDefaultZone());
    }

    PoliticalTradeServiceImpl(
            PoliticalTradeDataPort politicalTradeDataPort,
            PoliticalTradeSource politicalTradeSource,
            ObjectMapper objectMapper,
            Clock clock) {
        this.politicalTradeDataPort = politicalTradeDataPort;
        this.politicalTradeSource = politicalTradeSource;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public PaginatedResponse<PoliticalTradeResponse> screen(PoliticalTradeScreenRequest request) {
        ResolvedRequest resolved = resolve(request, true);
        List<PoliticalTradeResponse> responses = filterAndSort(resolved).stream()
                .map(this::toResponse)
                .toList();

        int start = Math.min(resolved.page() * resolved.size(), responses.size());
        int end = Math.min(start + resolved.size(), responses.size());
        return PaginatedResponse.of(responses.subList(start, end), resolved.page(), resolved.size(), responses.size());
    }

    @Override
    public byte[] export(PoliticalTradeScreenRequest request, String format) {
        ResolvedRequest resolved = resolve(request, false);
        List<PoliticalTradeResponse> responses = filterAndSort(resolved).stream()
                .limit(EXPORT_LIMIT)
                .map(this::toResponse)
                .toList();

        if ("JSON".equalsIgnoreCase(format)) {
            try {
                return objectMapper.writeValueAsBytes(responses);
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("Failed to export political trades as JSON", ex);
            }
        }

        return toCsv(responses).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public PoliticalTradeSyncResponse sync(PoliticalTradeSyncRequest request) {
        if (!syncInProgress.compareAndSet(false, true)) {
            throw new PoliticalTradeSyncException(
                    "Political trade sync is already running",
                    "POLITICAL_TRADE_SYNC_IN_PROGRESS",
                    HttpStatus.CONFLICT);
        }
        try {
            PoliticalTradeSyncRequest source = request == null ? new PoliticalTradeSyncRequest() : request;
            String assetType = normalizeAssetType(source.getAssetType(), DEFAULT_ASSET_TYPE);
            validateSyncAssetType(assetType);
            int maxPages = sanitizeSyncPages(source.getMaxPages());
            if (maxPages > MAX_UNFORCED_SYNC_PAGES && !source.isForce()) {
                throw new PoliticalTradeSyncException(
                        "Political trade backfills over 25 pages require force=true",
                        "POLITICAL_TRADE_SYNC_FORCE_REQUIRED",
                        HttpStatus.BAD_REQUEST);
            }
            Instant syncedAt = Instant.now(clock);
            long startedMillis = clock.millis();
            List<String> sourceAssetTypes = "ALL".equalsIgnoreCase(assetType)
                    ? ALL_ASSET_SYNC_TYPES
                    : List.of(assetType);
            List<PoliticalTrade> fetched = sourceAssetTypes.stream()
                    .flatMap(sourceAssetType -> politicalTradeSource.fetch(new PoliticalTradeSourceRequest(sourceAssetType, maxPages)).stream())
                    .toList();

            if (fetched.isEmpty() && !source.isForce()) {
                throw new PoliticalTradeSyncException(
                        "Political trade source returned no rows; refusing to mark sync successful without force=true",
                        "POLITICAL_TRADE_SYNC_EMPTY_SOURCE",
                        HttpStatus.BAD_GATEWAY);
            }

            int inserted = 0;
            int updated = 0;
            int skipped = 0;
            for (PoliticalTrade fetchedTrade : fetched) {
                if (fetchedTrade.getSourceTradeId() == null) {
                    skipped++;
                    continue;
                }

                PoliticalTrade tradeToSave = politicalTradeDataPort.findBySourceTradeId(fetchedTrade.getSourceTradeId())
                        .map(existing -> merge(existing, fetchedTrade, syncedAt))
                        .orElseGet(() -> prepareNew(fetchedTrade, syncedAt));

                boolean existed = politicalTradeDataPort.existsBySourceTradeId(fetchedTrade.getSourceTradeId());
                politicalTradeDataPort.save(tradeToSave);
                if (existed) {
                    updated++;
                } else {
                    inserted++;
                }
            }

            PoliticalTradeSyncResponse response = PoliticalTradeSyncResponse.builder()
                    .source(politicalTradeSource.sourceName())
                    .assetType(assetType)
                    .requestedPages(maxPages)
                    .fetchedPages(maxPages * sourceAssetTypes.size())
                    .fetchedRows(fetched.size())
                    .insertedRows(inserted)
                    .updatedRows(updated)
                    .skippedRows(skipped)
                    .totalCachedRows(politicalTradeDataPort.count())
                    .forced(source.isForce())
                    .syncedAt(syncedAt)
                    .durationMillis(Math.max(0L, clock.millis() - startedMillis))
                    .build();
            log.info("Political trade sync completed: source={} assetType={} pages={} fetched={} inserted={} updated={} skipped={} durationMs={}",
                    response.getSource(),
                    response.getAssetType(),
                    response.getFetchedPages(),
                    response.getFetchedRows(),
                    response.getInsertedRows(),
                    response.getUpdatedRows(),
                    response.getSkippedRows(),
                    response.getDurationMillis());
            return response;
        } finally {
            syncInProgress.set(false);
        }
    }

    private List<PoliticalTrade> filterAndSort(ResolvedRequest request) {
        List<PoliticalTrade> rows = new ArrayList<>(politicalTradeDataPort.findAll().stream()
                .filter(row -> containsIgnoreCase(row.getPoliticianName(), request.politician()))
                .filter(row -> request.ticker() == null || request.ticker().equals(normalizeTicker(row.getTicker())))
                .filter(row -> containsIgnoreCase(row.getIssuerName(), request.issuer()))
                .filter(row -> equalsIgnoreCase(row.getParty(), request.party()))
                .filter(row -> equalsIgnoreCase(row.getChamber(), request.chamber()))
                .filter(row -> equalsIgnoreCase(row.getState(), request.state()))
                .filter(row -> request.allAssetTypes() || equalsIgnoreCase(row.getAssetType(), request.assetType()))
                .filter(row -> equalsIgnoreCase(row.getTransactionType(), request.transactionType()))
                .filter(row -> containsIgnoreCase(row.getOwner(), request.owner()))
                .filter(row -> within(row.getTradedDate(), request.tradedDateFrom(), request.tradedDateTo()))
                .filter(row -> within(row.getDisclosureDate(), request.disclosureDateFrom(), request.disclosureDateTo()))
                .filter(row -> overlapsAmount(row, request.minAmount(), request.maxAmount()))
                .toList());
        rows.sort(comparator(request.sortBy(), request.sortDir()));
        return rows;
    }

    private PoliticalTrade prepareNew(PoliticalTrade trade, Instant syncedAt) {
        trade.setId(stableId(trade.getSourceTradeId()));
        trade.setFirstSeenAt(syncedAt);
        trade.setUpdatedAt(syncedAt);
        return trade;
    }

    private PoliticalTrade merge(PoliticalTrade existing, PoliticalTrade incoming, Instant syncedAt) {
        existing.setPoliticianName(incoming.getPoliticianName());
        existing.setParty(incoming.getParty());
        existing.setChamber(incoming.getChamber());
        existing.setState(incoming.getState());
        existing.setIssuerName(incoming.getIssuerName());
        existing.setTicker(incoming.getTicker());
        existing.setDisclosureDate(incoming.getDisclosureDate());
        existing.setTradedDate(incoming.getTradedDate());
        existing.setFiledAfterDays(incoming.getFiledAfterDays());
        existing.setOwner(incoming.getOwner());
        existing.setTransactionType(incoming.getTransactionType());
        existing.setAmountLabel(incoming.getAmountLabel());
        existing.setAmountMin(incoming.getAmountMin());
        existing.setAmountMax(incoming.getAmountMax());
        existing.setPrice(incoming.getPrice());
        existing.setAssetType(incoming.getAssetType());
        existing.setSourceTradeUrl(incoming.getSourceTradeUrl());
        existing.setSource(incoming.getSource());
        existing.setUpdatedAt(syncedAt);
        if (existing.getFirstSeenAt() == null) {
            existing.setFirstSeenAt(syncedAt);
        }
        return existing;
    }

    private String stableId(String sourceTradeId) {
        return sourceTradeId == null ? null : sourceTradeId.replace(':', '-');
    }

    private Comparator<PoliticalTrade> comparator(String sortBy, String sortDir) {
        boolean descending = !"asc".equalsIgnoreCase(sortDir);
        Comparator<PoliticalTrade> comparator = switch (sortBy == null ? "" : sortBy) {
            case "politicianName" -> Comparator.comparing(PoliticalTrade::getPoliticianName, stringComparator(descending));
            case "ticker" -> Comparator.comparing(PoliticalTrade::getTicker, stringComparator(descending));
            case "issuerName" -> Comparator.comparing(PoliticalTrade::getIssuerName, stringComparator(descending));
            case "tradedDate" -> Comparator.comparing(PoliticalTrade::getTradedDate, dateComparator(descending));
            case "filedAfterDays" -> Comparator.comparing(PoliticalTrade::getFiledAfterDays, integerComparator(descending));
            case "amount" -> Comparator.comparing(amountSortValue(), doubleComparator(descending));
            case "price" -> Comparator.comparing(PoliticalTrade::getPrice, doubleComparator(descending));
            case "transactionType" -> Comparator.comparing(PoliticalTrade::getTransactionType, stringComparator(descending));
            default -> Comparator.comparing(PoliticalTrade::getDisclosureDate, dateComparator(descending));
        };
        return comparator
                .thenComparing(PoliticalTrade::getDisclosureDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(PoliticalTrade::getPoliticianName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(PoliticalTrade::getTicker, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
    }

    private Function<PoliticalTrade, Double> amountSortValue() {
        return trade -> trade.getAmountMax() != null ? trade.getAmountMax() : trade.getAmountMin();
    }

    private ResolvedRequest resolve(PoliticalTradeScreenRequest request, boolean includePaging) {
        PoliticalTradeScreenRequest source = request == null ? new PoliticalTradeScreenRequest() : request;
        String assetType = normalizeAssetType(source.getAssetType(), DEFAULT_ASSET_TYPE);
        return new ResolvedRequest(
                blankToNull(source.getPolitician()),
                normalizeTicker(source.getTicker()),
                blankToNull(source.getIssuer()),
                blankToNull(source.getParty()),
                blankToNull(source.getChamber()),
                blankToNull(source.getState()),
                assetType,
                "ALL".equalsIgnoreCase(assetType),
                normalizeOption(source.getTransactionType()),
                blankToNull(source.getOwner()),
                source.getTradedDateFrom(),
                source.getTradedDateTo(),
                source.getDisclosureDateFrom(),
                source.getDisclosureDateTo(),
                normalizePositive(source.getMinAmount()),
                normalizePositive(source.getMaxAmount()),
                blankToNull(source.getSortBy()),
                "asc".equalsIgnoreCase(source.getSortDir()) ? "asc" : "desc",
                includePaging ? Math.max(AppConstants.DEFAULT_PAGE, source.getPage()) : 0,
                includePaging ? sanitizePageSize(source.getSize()) : EXPORT_LIMIT);
    }

    private int sanitizePageSize(int size) {
        if (size < AppConstants.MIN_PAGE_SIZE) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, AppConstants.MAX_PAGE_SIZE);
    }

    private int sanitizeSyncPages(Integer maxPages) {
        if (maxPages == null || maxPages < 1) {
            return DEFAULT_SYNC_PAGES;
        }
        return Math.min(maxPages, MAX_SYNC_PAGES);
    }

    private void validateSyncAssetType(String assetType) {
        if ("ALL".equalsIgnoreCase(assetType) || ALL_ASSET_SYNC_TYPES.contains(assetType)) {
            return;
        }
        throw new IllegalArgumentException("Unsupported political trade asset type: " + assetType);
    }

    private String normalizeAssetType(String value, String fallback) {
        String normalized = blankToNull(value);
        return normalized == null ? fallback : normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizeTicker(String ticker) {
        String value = blankToNull(ticker);
        if (value == null) {
            return null;
        }
        String normalized = value.replace("$", "").trim().toUpperCase(Locale.ROOT);
        int suffixIndex = normalized.indexOf(':');
        return suffixIndex > 0 ? normalized.substring(0, suffixIndex) : normalized;
    }

    private String normalizeOption(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private Double normalizePositive(Double value) {
        return value != null && value > 0d ? value : null;
    }

    private boolean containsIgnoreCase(String value, String needle) {
        return needle == null || value != null && value.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private boolean equalsIgnoreCase(String value, String expected) {
        return expected == null || value != null && value.equalsIgnoreCase(expected);
    }

    private boolean within(LocalDate value, LocalDate from, LocalDate to) {
        return (from == null || value != null && !value.isBefore(from))
                && (to == null || value != null && !value.isAfter(to));
    }

    private boolean overlapsAmount(PoliticalTrade trade, Double minAmount, Double maxAmount) {
        if (minAmount == null && maxAmount == null) {
            return true;
        }
        Double min = trade.getAmountMin();
        Double max = trade.getAmountMax();
        if (min == null && max == null) {
            return false;
        }
        double rangeMin = min == null ? max : min;
        double rangeMax = max == null ? min : max;
        return (minAmount == null || rangeMax >= minAmount)
                && (maxAmount == null || rangeMin <= maxAmount);
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private PoliticalTradeResponse toResponse(PoliticalTrade trade) {
        return PoliticalTradeResponse.builder()
                .id(trade.getId())
                .sourceTradeId(trade.getSourceTradeId())
                .politicianName(trade.getPoliticianName())
                .party(trade.getParty())
                .chamber(trade.getChamber())
                .state(trade.getState())
                .issuerName(trade.getIssuerName())
                .ticker(trade.getTicker())
                .disclosureDate(trade.getDisclosureDate())
                .tradedDate(trade.getTradedDate())
                .filedAfterDays(trade.getFiledAfterDays())
                .owner(trade.getOwner())
                .transactionType(trade.getTransactionType())
                .amountLabel(trade.getAmountLabel())
                .amountMin(trade.getAmountMin())
                .amountMax(trade.getAmountMax())
                .price(trade.getPrice())
                .assetType(trade.getAssetType())
                .sourceTradeUrl(trade.getSourceTradeUrl())
                .source(trade.getSource())
                .firstSeenAt(trade.getFirstSeenAt())
                .updatedAt(trade.getUpdatedAt())
                .build();
    }

    private String toCsv(List<PoliticalTradeResponse> rows) {
        StringBuilder csv = new StringBuilder();
        csv.append("sourceTradeId,politicianName,party,chamber,state,issuerName,ticker,disclosureDate,tradedDate,")
                .append("filedAfterDays,owner,transactionType,amountLabel,amountMin,amountMax,price,assetType,sourceTradeUrl,source\n");
        for (PoliticalTradeResponse row : rows) {
            csv.append(csv(row.getSourceTradeId())).append(',')
                    .append(csv(row.getPoliticianName())).append(',')
                    .append(csv(row.getParty())).append(',')
                    .append(csv(row.getChamber())).append(',')
                    .append(csv(row.getState())).append(',')
                    .append(csv(row.getIssuerName())).append(',')
                    .append(csv(row.getTicker())).append(',')
                    .append(csv(row.getDisclosureDate())).append(',')
                    .append(csv(row.getTradedDate())).append(',')
                    .append(csv(row.getFiledAfterDays())).append(',')
                    .append(csv(row.getOwner())).append(',')
                    .append(csv(row.getTransactionType())).append(',')
                    .append(csv(row.getAmountLabel())).append(',')
                    .append(csv(row.getAmountMin())).append(',')
                    .append(csv(row.getAmountMax())).append(',')
                    .append(csv(row.getPrice())).append(',')
                    .append(csv(row.getAssetType())).append(',')
                    .append(csv(row.getSourceTradeUrl())).append(',')
                    .append(csv(row.getSource()))
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

    private Comparator<String> stringComparator(boolean descending) {
        return Comparator.nullsLast(descending
                ? String.CASE_INSENSITIVE_ORDER.reversed()
                : String.CASE_INSENSITIVE_ORDER);
    }

    private Comparator<Integer> integerComparator(boolean descending) {
        Comparator<Integer> comparator = descending ? Comparator.reverseOrder() : Comparator.naturalOrder();
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

    private record ResolvedRequest(
            String politician,
            String ticker,
            String issuer,
            String party,
            String chamber,
            String state,
            String assetType,
            boolean allAssetTypes,
            String transactionType,
            String owner,
            LocalDate tradedDateFrom,
            LocalDate tradedDateTo,
            LocalDate disclosureDateFrom,
            LocalDate disclosureDateTo,
            Double minAmount,
            Double maxAmount,
            String sortBy,
            String sortDir,
            int page,
            int size) {
    }
}
