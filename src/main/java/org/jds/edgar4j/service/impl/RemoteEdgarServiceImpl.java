package org.jds.edgar4j.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;

import org.jds.edgar4j.dto.request.RemoteFilingSearchRequest;
import org.jds.edgar4j.dto.response.RemoteFilingResponse;
import org.jds.edgar4j.dto.response.RemoteFilingSearchResponse;
import org.jds.edgar4j.dto.response.RemoteSubmissionFilingResponse;
import org.jds.edgar4j.dto.response.RemoteSubmissionResponse;
import org.jds.edgar4j.dto.response.RemoteTickerResponse;
import org.jds.edgar4j.integration.SecApiClient;
import org.jds.edgar4j.integration.SecApiConfig;
import org.jds.edgar4j.integration.SecResponseParser;
import org.jds.edgar4j.integration.model.SecSubmissionResponse;
import org.jds.edgar4j.model.Ticker;
import org.jds.edgar4j.service.RemoteEdgarService;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class RemoteEdgarServiceImpl implements RemoteEdgarService {

    private static final int MAX_TICKER_LIMIT = 500;
    private static final int MAX_FILINGS_LIMIT = 200;
    private static final int MAX_REMOTE_SEARCH_LIMIT = 500;
    private static final long MAX_REMOTE_RANGE_DAYS = 366;
    private static final String DAILY_INDEX_HEADER = "CIK|Company Name|Form Type|Date Filed|Filename";

    private final SecApiClient secApiClient;
    private final SecApiConfig secApiConfig;
    private final SecResponseParser responseParser;

    @Override
    public List<RemoteTickerResponse> getRemoteTickers(String source, String search, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_TICKER_LIMIT));
        String safeSource = source == null ? "all" : source.trim().toLowerCase(Locale.ROOT);
        String normalizedSearch = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);

        List<Ticker> parsedTickers = switch (safeSource) {
            case "exchanges" -> responseParser.parseTickersExchangeJson(secApiClient.fetchCompanyTickersExchanges());
            case "mf" -> responseParser.parseTickersJson(secApiClient.fetchCompanyTickersMutualFunds());
            default -> responseParser.parseTickersJson(secApiClient.fetchCompanyTickers());
        };

        return parsedTickers.stream()
                .filter(ticker -> matchesSearch(ticker, normalizedSearch))
                .sorted(Comparator.comparing(ticker -> safeString(ticker.getCode())))
                .limit(safeLimit)
                .map(ticker -> RemoteTickerResponse.builder()
                        .cik(ticker.getCik())
                        .ticker(ticker.getCode())
                        .name(ticker.getName())
                        .exchange(ticker.getExchange() != null ? ticker.getExchange().getCode() : null)
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public RemoteSubmissionResponse getRemoteSubmission(String cik, int filingsLimit) {
        if (cik == null || cik.isBlank()) {
            throw new IllegalArgumentException("CIK is required");
        }

        String normalizedCik = cik.trim();
        int safeFilingsLimit = Math.max(1, Math.min(filingsLimit, MAX_FILINGS_LIMIT));
        String json = secApiClient.fetchSubmissions(normalizedCik);
        SecSubmissionResponse submission = responseParser.parseSubmissionResponse(json);

        List<RemoteSubmissionFilingResponse> recentFilings = mapRecentFilings(submission, safeFilingsLimit);

        return RemoteSubmissionResponse.builder()
                .cik(normalizedCik)
                .companyName(submission.getName())
                .sic(submission.getSic())
                .sicDescription(submission.getSicDescription())
                .tickers(submission.getTickers())
                .exchanges(submission.getExchanges())
                .recentFilingsCount(recentFilings.size())
                .recentFilings(recentFilings)
                .build();
    }

    @Override
    public RemoteFilingSearchResponse searchRemoteFilings(RemoteFilingSearchRequest request) {
        NormalizedRemoteFilingSearchRequest normalizedRequest = normalizeRemoteFilingSearchRequest(request);
        RemoteFilingScanResult scanResult = scanRemoteFilings(normalizedRequest, true);

        List<RemoteFilingResponse> filings = scanResult.previewFilings.stream()
                .sorted(Comparator
                        .comparing(RemoteFilingResponse::getFilingDate, Comparator.nullsLast(String::compareTo))
                        .reversed()
                        .thenComparing(RemoteFilingResponse::getCompanyName, Comparator.nullsLast(String::compareTo)))
                .collect(Collectors.toList());

        return RemoteFilingSearchResponse.builder()
                .formType(normalizedRequest.formType())
                .dateFrom(scanResult.scannedDateFrom != null ? scanResult.scannedDateFrom.toString() : normalizedRequest.dateFrom().toString())
                .dateTo(scanResult.scannedDateTo != null ? scanResult.scannedDateTo.toString() : normalizedRequest.dateTo().toString())
                .totalMatches(scanResult.totalMatches)
                .returnedMatches(filings.size())
                .uniqueCompanyCount(scanResult.uniqueCompanyCiks.size())
                .truncated(scanResult.truncated || scanResult.totalMatches > filings.size())
                .searchedDateCount(scanResult.searchedDateCount)
                .availableDateCount(scanResult.availableDateCount)
                .unavailableDateCount(scanResult.unavailableDateCount)
                .filings(filings)
                .build();
    }

    @Override
    public List<String> findMatchingCompanyCiks(RemoteFilingSearchRequest request) {
        NormalizedRemoteFilingSearchRequest normalizedRequest = normalizeRemoteFilingSearchRequest(request);
        return new ArrayList<>(scanRemoteFilings(normalizedRequest, false).uniqueCompanyCiks);
    }

    private List<RemoteSubmissionFilingResponse> mapRecentFilings(SecSubmissionResponse submission, int limit) {
        List<RemoteSubmissionFilingResponse> filings = new ArrayList<>();
        if (submission.getFilings() == null || submission.getFilings().getRecent() == null) {
            return filings;
        }

        SecSubmissionResponse.Recent recent = submission.getFilings().getRecent();
        if (recent.getAccessionNumber() == null || recent.getAccessionNumber().isEmpty()) {
            return filings;
        }

        int max = Math.min(limit, recent.getAccessionNumber().size());
        for (int i = 0; i < max; i++) {
            filings.add(RemoteSubmissionFilingResponse.builder()
                    .accessionNumber(getOrNull(recent.getAccessionNumber(), i))
                    .formType(getOrNull(recent.getForm(), i))
                    .filingDate(getOrNull(recent.getFilingDate(), i))
                    .reportDate(getOrNull(recent.getReportDate(), i))
                    .primaryDocument(getOrNull(recent.getPrimaryDocument(), i))
                    .primaryDocDescription(getOrNull(recent.getPrimaryDocDescription(), i))
                    .build());
        }
        return filings;
    }

    private RemoteFilingScanResult scanRemoteFilings(NormalizedRemoteFilingSearchRequest request, boolean capturePreview) {
        RemoteFilingScanResult result = new RemoteFilingScanResult();

        for (LocalDate currentDate = request.dateTo(); !currentDate.isBefore(request.dateFrom()); currentDate = currentDate.minusDays(1)) {
            result.searchedDateCount++;
            result.scannedDateTo = result.scannedDateTo == null ? currentDate : result.scannedDateTo;
            result.scannedDateFrom = currentDate;

            var dailyIndexContent = secApiClient.fetchDailyMasterIndex(currentDate);
            if (dailyIndexContent.isEmpty()) {
                result.unavailableDateCount++;
                continue;
            }

            result.availableDateCount++;
            List<RemoteFilingResponse> dailyMatches = parseDailyMasterIndex(dailyIndexContent.get(), request, currentDate);
            for (RemoteFilingResponse filing : dailyMatches) {
                result.totalMatches++;
                result.uniqueCompanyCiks.add(filing.getCik());
                if (capturePreview && result.previewFilings.size() < request.limit()) {
                    result.previewFilings.add(filing);
                }
                if (!request.hasExplicitDateRange() && result.totalMatches >= request.limit()) {
                    result.truncated = true;
                    return result;
                }
            }
        }

        return result;
    }

    private List<RemoteFilingResponse> parseDailyMasterIndex(
            String indexContent,
            NormalizedRemoteFilingSearchRequest request,
            LocalDate expectedDate) {
        List<RemoteFilingResponse> filings = new ArrayList<>();
        boolean inDataSection = false;

        for (String rawLine : indexContent.split("\\R")) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }

            if (!inDataSection) {
                if (DAILY_INDEX_HEADER.equalsIgnoreCase(line)) {
                    inDataSection = true;
                }
                continue;
            }

            String[] parts = line.split("\\|", 5);
            if (parts.length < 5) {
                continue;
            }

            String filingFormType = normalizeFormType(parts[2]);
            if (!matchesFormType(filingFormType, request.formType())) {
                continue;
            }

            String filingCik = secApiConfig.formatCik(parts[0].trim());
            String companyName = parts[1].trim();
            if (!matchesCompanyFilter(companyName, request.companyNameFilter())) {
                continue;
            }
            if (!matchesCikFilter(filingCik, request.cikFilters(), request.hasCikFilter())) {
                continue;
            }

            LocalDate filedDate = parseDailyIndexDate(parts[3], expectedDate);
            String archivePath = parts[4].trim();
            filings.add(RemoteFilingResponse.builder()
                    .cik(filingCik)
                    .companyName(companyName)
                    .formType(parts[2].trim())
                    .filingDate(filedDate != null ? filedDate.toString() : expectedDate.toString())
                    .accessionNumber(extractAccessionNumber(archivePath))
                    .archivePath(archivePath)
                    .filingUrl(secApiConfig.getArchiveUrl(archivePath))
                    .build());
        }

        return filings;
    }

    private NormalizedRemoteFilingSearchRequest normalizeRemoteFilingSearchRequest(RemoteFilingSearchRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Remote filing search request is required");
        }

        String normalizedFormType = normalizeFormType(request.getFormType());
        if (normalizedFormType.isEmpty()) {
            throw new IllegalArgumentException("formType is required");
        }

        LocalDate dateFrom = request.getDateFrom();
        LocalDate dateTo = request.getDateTo();
        boolean hasExplicitDateRange = dateFrom != null || dateTo != null;
        if ((dateFrom == null) != (dateTo == null)) {
            throw new IllegalArgumentException("dateFrom and dateTo must both be provided for remote date-range search");
        }
        LocalDate today = LocalDate.now();
        if (hasExplicitDateRange) {
            if (dateFrom.isAfter(dateTo)) {
                throw new IllegalArgumentException("dateFrom must be on or before dateTo");
            }
            if (dateTo.isAfter(today)) {
                throw new IllegalArgumentException("dateTo cannot be in the future");
            }

            long rangeDays = ChronoUnit.DAYS.between(dateFrom, dateTo) + 1;
            if (rangeDays > MAX_REMOTE_RANGE_DAYS) {
                throw new IllegalArgumentException("date range must not exceed 366 days");
            }
        } else {
            dateTo = today;
            dateFrom = today.minusDays(MAX_REMOTE_RANGE_DAYS - 1);
        }

        int safeLimit = request.getLimit() == null
                ? Math.min(100, MAX_REMOTE_SEARCH_LIMIT)
                : Math.max(1, Math.min(request.getLimit(), MAX_REMOTE_SEARCH_LIMIT));

        return new NormalizedRemoteFilingSearchRequest(
                normalizedFormType,
                normalizeCompanyName(request.getCompanyName()),
                resolveCikFilters(request.getTicker(), request.getCik()),
                dateFrom,
                dateTo,
                safeLimit,
                (request.getTicker() != null && !request.getTicker().isBlank())
                        || (request.getCik() != null && !request.getCik().isBlank()),
                hasExplicitDateRange
        );
    }

    private boolean matchesFormType(String filingFormType, String filterFormType) {
        if (filingFormType == null || filingFormType.isBlank() || filterFormType == null || filterFormType.isBlank()) {
            return false;
        }
        if (filingFormType.equals(filterFormType)) {
            return true;
        }
        if (!filingFormType.startsWith(filterFormType) || filingFormType.length() <= filterFormType.length()) {
            return false;
        }
        char nextCharacter = filingFormType.charAt(filterFormType.length());
        return nextCharacter == '-' || nextCharacter == '/';
    }

    private String normalizeFormType(String formType) {
        return formType == null ? "" : formType.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private String normalizeCompanyName(String companyName) {
        return companyName == null ? "" : companyName.trim().toUpperCase(Locale.ROOT);
    }

    private Set<String> resolveCikFilters(String ticker, String cik) {
        if (cik != null && !cik.isBlank()) {
            return Set.of(secApiConfig.formatCik(cik.trim()));
        }

        if (ticker == null || ticker.isBlank()) {
            return Set.of();
        }

        String normalizedTicker = ticker.trim().toUpperCase(Locale.ROOT);
        Set<String> matchingCiks = new LinkedHashSet<>();
        responseParser.parseTickersJson(secApiClient.fetchCompanyTickers()).stream()
                .filter(parsedTicker -> normalizedTicker.equalsIgnoreCase(safeString(parsedTicker.getCode())))
                .map(Ticker::getCik)
                .forEach(matchingCiks::add);
        responseParser.parseTickersJson(secApiClient.fetchCompanyTickersMutualFunds()).stream()
                .filter(parsedTicker -> normalizedTicker.equalsIgnoreCase(safeString(parsedTicker.getCode())))
                .map(Ticker::getCik)
                .forEach(matchingCiks::add);
        return matchingCiks;
    }

    private boolean matchesCompanyFilter(String companyName, String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }
        return safeString(companyName).toUpperCase(Locale.ROOT).contains(filter);
    }

    private boolean matchesCikFilter(String cik, Set<String> cikFilters, boolean hasCikFilter) {
        if (!hasCikFilter) {
            return true;
        }
        return cikFilters.contains(cik);
    }

    private LocalDate parseDailyIndexDate(String value, LocalDate fallbackDate) {
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception e) {
            log.debug("Failed to parse filing date '{}' from daily index, falling back to {}", value, fallbackDate);
            return fallbackDate;
        }
    }

    private String extractAccessionNumber(String archivePath) {
        if (archivePath == null || archivePath.isBlank()) {
            return null;
        }
        int lastSlash = archivePath.lastIndexOf('/');
        String fileName = lastSlash >= 0 ? archivePath.substring(lastSlash + 1) : archivePath;
        int extensionIndex = fileName.lastIndexOf('.');
        return extensionIndex > 0 ? fileName.substring(0, extensionIndex) : fileName;
    }

    private boolean matchesSearch(Ticker ticker, String search) {
        if (search.isEmpty()) {
            return true;
        }
        String code = safeString(ticker.getCode()).toLowerCase(Locale.ROOT);
        String name = safeString(ticker.getName()).toLowerCase(Locale.ROOT);
        String cik = safeString(ticker.getCik()).toLowerCase(Locale.ROOT);
        return code.contains(search) || name.contains(search) || cik.contains(search);
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    private String getOrNull(List<String> list, int index) {
        if (list == null || index >= list.size()) {
            return null;
        }
        return list.get(index);
    }

    private record NormalizedRemoteFilingSearchRequest(
            String formType,
            String companyNameFilter,
            Set<String> cikFilters,
            LocalDate dateFrom,
            LocalDate dateTo,
            int limit,
            boolean hasCikFilter,
            boolean hasExplicitDateRange) {
    }

    private static final class RemoteFilingScanResult {
        private final List<RemoteFilingResponse> previewFilings = new ArrayList<>();
        private final Set<String> uniqueCompanyCiks = new LinkedHashSet<>();
        private int totalMatches;
        private int searchedDateCount;
        private int availableDateCount;
        private int unavailableDateCount;
        private boolean truncated;
        private LocalDate scannedDateFrom;
        private LocalDate scannedDateTo;
    }
}

