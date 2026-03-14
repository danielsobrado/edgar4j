package org.jds.edgar4j.service.impl;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.jds.edgar4j.dto.response.CompanyResponse;
import org.jds.edgar4j.dto.response.DividendOverviewResponse;
import org.jds.edgar4j.dto.response.DividendSyncStatusResponse;
import org.jds.edgar4j.exception.ResourceNotFoundException;
import org.jds.edgar4j.model.DividendSyncState;
import org.jds.edgar4j.model.Filling;
import org.jds.edgar4j.port.DividendSyncStateDataPort;
import org.jds.edgar4j.port.FillingDataPort;
import org.jds.edgar4j.service.CompanyMarketDataService;
import org.jds.edgar4j.service.CompanyService;
import org.jds.edgar4j.service.DividendAnalysisService;
import org.jds.edgar4j.service.DividendSyncService;
import org.jds.edgar4j.service.DownloadSubmissionsService;
import org.jds.edgar4j.service.Sp500Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DividendSyncServiceImpl implements DividendSyncService {

    private static final int RECENT_FILING_LIMIT = 25;
    private static final Set<String> CURRENT_REPORT_FORMS = Set.of("8-K", "8-K/A");

    private final CompanyService companyService;
    private final DownloadSubmissionsService downloadSubmissionsService;
    private final FillingDataPort fillingRepository;
    private final DividendSyncStateDataPort dividendSyncStateRepository;
    private final CompanyMarketDataService companyMarketDataService;
    private final DividendAnalysisService dividendAnalysisService;
    private final Sp500Service sp500Service;

    @Override
    public DividendSyncStatusResponse syncCompany(String tickerOrCik, boolean refreshMarketData) {
        CompanyResponse company = resolveCompany(tickerOrCik);
        String cik = normalizeCik(company.getCik())
                .orElseThrow(() -> new IllegalArgumentException("Company CIK is unavailable"));
        String ticker = normalizeTicker(company.getTicker())
                .orElseGet(() -> companyService.getTickerByCik(cik).orElse(null));

        Instant now = Instant.now();
        DividendSyncState state = dividendSyncStateRepository.findByCik(cik)
                .orElseGet(() -> newState(cik, ticker, company.getName(), now));
        updateStateIdentity(state, cik, ticker, company.getName(), now);
        state.setSyncStatus(DividendSyncState.SyncStatus.IN_PROGRESS);
        state.setUpdatedAt(now);
        dividendSyncStateRepository.save(state);

        boolean refreshedMarketData = false;
        boolean analysisWarmupSucceeded = false;
        List<String> warnings = new ArrayList<>();

        try {
            downloadSubmissionsService.downloadSubmissions(cik);

            List<Filling> recentFilings = loadRecentFilings(cik);
            List<String> newAccessions = detectNewAccessions(recentFilings, state.getLastAccession());
            String latestAccession = recentFilings.stream()
                    .map(Filling::getAccessionNumber)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);

            state.setLastFactsSync(now);
            state.setLastAccession(latestAccession);
            state.setLastNewFilingsDetected(newAccessions.size());
            state.setFactsVersion(newAccessions.isEmpty() ? state.getFactsVersion() : state.getFactsVersion() + 1);

            if (refreshMarketData && ticker != null) {
                companyMarketDataService.fetchAndSaveQuote(ticker);
                state.setLastMarketDataSync(now);
                refreshedMarketData = true;
            } else if (refreshMarketData) {
                warnings.add("Market data refresh was skipped because the company ticker could not be resolved.");
            }

            try {
                dividendAnalysisService.getOverview(cik);
                analysisWarmupSucceeded = true;
                state.setLastAnalysisWarmup(now);
                if (containsCurrentReport(recentFilings, newAccessions)) {
                    dividendAnalysisService.getEvents(cik, LocalDate.now(ZoneOffset.UTC).minusYears(1));
                    state.setLastEventsSync(now);
                }
            } catch (RuntimeException e) {
                warnings.add("Dividend analysis warm-up failed: " + e.getMessage());
            }

            state.setSyncStatus(DividendSyncState.SyncStatus.IDLE);
            state.setLastSuccessfulSync(now);
            state.setErrorMessage(null);
            state.setRetryCount(0);
            state.setNextRetryAt(null);
            state.setUpdatedAt(now);
            dividendSyncStateRepository.save(state);

            return toStatusResponse(
                    state,
                    buildCompanySummary(company, ticker, resolveLatestFilingDate(recentFilings)),
                    true,
                    refreshedMarketData,
                    analysisWarmupSucceeded,
                    warnings);
        } catch (RuntimeException e) {
            log.warn("Dividend sync failed for {} ({}): {}", tickerOrCik, cik, e.getMessage());
            state.setSyncStatus(DividendSyncState.SyncStatus.ERROR);
            state.setRetryCount(state.getRetryCount() + 1);
            state.setErrorMessage(e.getMessage());
            state.setNextRetryAt(now.plus(computeBackoff(state.getRetryCount())));
            state.setUpdatedAt(now);
            dividendSyncStateRepository.save(state);

            warnings.add("Dividend sync failed: " + e.getMessage());
            return toStatusResponse(
                    state,
                    buildCompanySummary(company, ticker, null),
                    false,
                    refreshedMarketData,
                    analysisWarmupSucceeded,
                    warnings);
        }
    }

    @Override
    public DividendSyncStatusResponse getSyncStatus(String tickerOrCik) {
        CompanyResponse company = resolveCompany(tickerOrCik);
        String cik = normalizeCik(company.getCik())
                .orElseThrow(() -> new IllegalArgumentException("Company CIK is unavailable"));
        String ticker = normalizeTicker(company.getTicker())
                .orElseGet(() -> companyService.getTickerByCik(cik).orElse(null));
        List<Filling> recentFilings = loadRecentFilings(cik);
        List<String> warnings = new ArrayList<>();

        DividendSyncState state = dividendSyncStateRepository.findByCik(cik)
                .orElseGet(() -> {
                    warnings.add("No dividend sync has been recorded yet for this company.");
                    return newState(cik, ticker, company.getName(), Instant.now());
                });
        updateStateIdentity(state, cik, ticker, company.getName(), state.getUpdatedAt() != null ? state.getUpdatedAt() : Instant.now());

        return toStatusResponse(
                state,
                buildCompanySummary(company, ticker, resolveLatestFilingDate(recentFilings)),
                false,
                false,
                state.getLastAnalysisWarmup() != null,
                warnings);
    }

    @Override
    public List<DividendSyncStatusResponse> syncTrackedCompanies(int maxCompanies, boolean refreshMarketData) {
        Set<String> tickers = new LinkedHashSet<>(sp500Service.getAllTickers());
        return tickers.stream()
                .filter(Objects::nonNull)
                .filter(ticker -> !ticker.isBlank())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .limit(maxCompanies > 0 ? maxCompanies : Long.MAX_VALUE)
                .map(ticker -> syncCompany(ticker, refreshMarketData))
                .toList();
    }

    private CompanyResponse resolveCompany(String tickerOrCik) {
        String identifier = blankToNull(tickerOrCik)
                .orElseThrow(() -> new IllegalArgumentException("tickerOrCik must not be blank"));
        return (identifier.chars().allMatch(Character::isDigit)
                ? companyService.getCompanyByCik(identifier)
                : companyService.getCompanyByTicker(identifier))
                .orElseThrow(() -> new ResourceNotFoundException("Company", "tickerOrCik", tickerOrCik));
    }

    private DividendSyncState newState(String cik, String ticker, String companyName, Instant now) {
        return DividendSyncState.builder()
                .id(cik)
                .cik(cik)
                .ticker(ticker)
                .companyName(companyName)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private void updateStateIdentity(
            DividendSyncState state,
            String cik,
            String ticker,
            String companyName,
            Instant now) {
        if (state.getId() == null) {
            state.setId(cik);
        }
        state.setCik(cik);
        state.setTicker(ticker);
        state.setCompanyName(companyName);
        if (state.getCreatedAt() == null) {
            state.setCreatedAt(now);
        }
    }

    private List<Filling> loadRecentFilings(String cik) {
        return fillingRepository.findByCik(
                        cik,
                        PageRequest.of(0, RECENT_FILING_LIMIT, Sort.by(Sort.Direction.DESC, "fillingDate")))
                .getContent();
    }

    private List<String> detectNewAccessions(List<Filling> recentFilings, String previousLatestAccession) {
        List<String> accessions = new ArrayList<>();
        for (Filling filing : recentFilings) {
            String accessionNumber = filing.getAccessionNumber();
            if (accessionNumber == null) {
                continue;
            }
            if (matchesAccession(accessionNumber, previousLatestAccession)) {
                break;
            }
            accessions.add(accessionNumber);
        }
        return accessions;
    }

    private boolean containsCurrentReport(List<Filling> recentFilings, List<String> newAccessions) {
        if (newAccessions.isEmpty()) {
            return false;
        }
        Set<String> newAccessionSet = new LinkedHashSet<>(newAccessions);
        return recentFilings.stream()
                .filter(filing -> filing.getAccessionNumber() != null && newAccessionSet.contains(filing.getAccessionNumber()))
                .map(Filling::getFormType)
                .filter(Objects::nonNull)
                .map(formType -> formType.getNumber())
                .filter(Objects::nonNull)
                .map(number -> number.toUpperCase(Locale.ROOT))
                .anyMatch(CURRENT_REPORT_FORMS::contains);
    }

    private LocalDate resolveLatestFilingDate(List<Filling> filings) {
        return filings.stream()
                .map(Filling::getFillingDate)
                .filter(Objects::nonNull)
                .findFirst()
                .map(date -> date.toInstant().atZone(ZoneOffset.UTC).toLocalDate())
                .orElse(null);
    }

    private Duration computeBackoff(int retryCount) {
        long hours = Math.min(24L, retryCount <= 0 ? 1L : (long) Math.pow(2, retryCount - 1));
        return Duration.ofHours(hours);
    }

    private DividendSyncStatusResponse toStatusResponse(
            DividendSyncState state,
            DividendOverviewResponse.CompanySummary companySummary,
            boolean refreshedSubmissions,
            boolean refreshedMarketData,
            boolean analysisWarmupSucceeded,
            List<String> warnings) {
        return DividendSyncStatusResponse.builder()
                .company(companySummary)
                .status(state.getSyncStatus())
                .createdAt(state.getCreatedAt())
                .updatedAt(state.getUpdatedAt())
                .lastFactsSync(state.getLastFactsSync())
                .lastEventsSync(state.getLastEventsSync())
                .lastSuccessfulSync(state.getLastSuccessfulSync())
                .lastMarketDataSync(state.getLastMarketDataSync())
                .lastAnalysisWarmup(state.getLastAnalysisWarmup())
                .lastAccession(state.getLastAccession())
                .retryCount(state.getRetryCount())
                .errorMessage(state.getErrorMessage())
                .nextRetryAt(state.getNextRetryAt())
                .factsVersion(state.getFactsVersion())
                .newFilingsDetected(state.getLastNewFilingsDetected())
                .refreshedSubmissions(refreshedSubmissions)
                .refreshedMarketData(refreshedMarketData)
                .analysisWarmupSucceeded(analysisWarmupSucceeded)
                .warnings(warnings.stream().distinct().toList())
                .build();
    }

    private DividendOverviewResponse.CompanySummary buildCompanySummary(
            CompanyResponse company,
            String resolvedTicker,
            LocalDate latestFilingDate) {
        String fiscalYearEnd = company.getFiscalYearEnd() != null
                ? String.format("%04d", company.getFiscalYearEnd())
                : null;
        return DividendOverviewResponse.CompanySummary.builder()
                .cik(company.getCik())
                .ticker(firstNonBlank(company.getTicker(), resolvedTicker))
                .name(firstNonBlank(company.getName(), company.getTicker(), company.getCik()))
                .fiscalYearEnd(fiscalYearEnd)
                .lastFilingDate(latestFilingDate)
                .build();
    }

    private boolean matchesAccession(String left, String right) {
        return normalizeAccession(left)
                .flatMap(normalizedLeft -> normalizeAccession(right).map(normalizedLeft::equals))
                .orElse(false);
    }

    private Optional<String> normalizeAccession(String accessionNumber) {
        return blankToNull(accessionNumber)
                .map(value -> value.replaceAll("[^0-9]", ""))
                .filter(value -> !value.isBlank());
    }

    private Optional<String> normalizeCik(String cik) {
        return blankToNull(cik)
                .map(value -> value.replaceAll("[^0-9]", ""))
                .filter(value -> !value.isBlank())
                .map(value -> String.format("%010d", Long.parseLong(value)));
    }

    private Optional<String> normalizeTicker(String ticker) {
        return blankToNull(ticker).map(value -> value.toUpperCase(Locale.ROOT));
    }

    private Optional<String> blankToNull(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? Optional.empty() : Optional.of(trimmed);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
