package org.jds.edgar4j.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.jds.edgar4j.dto.request.CompanySearchRequest;
import org.jds.edgar4j.dto.request.DividendScreenRequest;
import org.jds.edgar4j.dto.response.CompanyListResponse;
import org.jds.edgar4j.dto.response.CompanyResponse;
import org.jds.edgar4j.dto.response.DividendAlertsResponse;
import org.jds.edgar4j.dto.response.DividendComparisonResponse;
import org.jds.edgar4j.dto.response.DividendEvidenceResponse;
import org.jds.edgar4j.dto.response.DividendEventsResponse;
import org.jds.edgar4j.dto.response.DividendHistoryResponse;
import org.jds.edgar4j.dto.response.DividendMetricDefinitionResponse;
import org.jds.edgar4j.dto.response.DividendOverviewResponse;
import org.jds.edgar4j.dto.response.DividendScreenResponse;
import org.jds.edgar4j.dto.response.PaginatedResponse;
import org.jds.edgar4j.exception.ResourceNotFoundException;
import org.jds.edgar4j.model.CompanyMarketData;
import org.jds.edgar4j.model.Filling;
import org.jds.edgar4j.service.CompanyMarketDataService;
import org.jds.edgar4j.service.CompanyService;
import org.jds.edgar4j.service.DividendAnalysisService;
import org.jds.edgar4j.service.dividend.DividendEvidenceService;
import org.jds.edgar4j.service.dividend.DividendScreeningService;
import org.jds.edgar4j.service.impl.DividendFilingAnalysisService.AnalyzedFilingData;
import org.jds.edgar4j.service.impl.DividendFilingAnalysisService.DividendFactPoint;
import org.jds.edgar4j.service.impl.DividendHistoryAnalysisService.HistoryRowData;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DividendAnalysisServiceImpl implements DividendAnalysisService {

    private static final int ANALYSIS_ANNUAL_LIMIT = 6;
    private static final int ANALYSIS_QUARTERLY_LIMIT = 2;

    private static final Set<String> ANNUAL_FORMS = Set.of(
            "10-K", "10-K/A", "20-F", "20-F/A", "40-F", "40-F/A");
    private static final Set<String> QUARTERLY_FORMS = Set.of(
            "10-Q", "10-Q/A");
    private static final Set<String> CURRENT_REPORT_FORMS = Set.of(
            "8-K", "8-K/A");

    private final CompanyService companyService;
    private final CompanyMarketDataService companyMarketDataService;
    private final DividendFilingAnalysisService dividendFilingAnalysisService;
    private final DividendHistoryAnalysisService dividendHistoryAnalysisService;
    private final DividendOverviewComputationService dividendOverviewComputationService;
    private final DividendMetricCatalogService dividendMetricCatalogService;
    private final DividendScreeningService dividendScreeningService;
    private final DividendEvidenceService dividendEvidenceService;

    @Override
    public DividendOverviewResponse getOverview(String tickerOrCik) {
        AnalysisContext context = analyze(tickerOrCik);
        return buildOverview(context);
    }

    @Override
    public DividendHistoryResponse getHistory(String tickerOrCik, List<String> metrics, String period, int years) {
        if (years <= 0) {
            throw new IllegalArgumentException("years must be greater than 0");
        }

        String normalizedPeriod = dividendMetricCatalogService.normalizeHistoryPeriod(period);
        List<String> requestedMetrics = dividendMetricCatalogService.normalizeHistoryMetrics(metrics);
        AnalysisContext context = analyze(tickerOrCik);
        List<HistoryRowData> rows = dividendHistoryAnalysisService.limitHistoryRows(context.historyRows(), years);

        List<DividendHistoryResponse.MetricSeries> series = requestedMetrics.stream()
                .map(metric -> dividendHistoryAnalysisService.buildMetricSeries(
                        metric, rows, dividendMetricCatalogService.historyMetricDefinitions()))
                .toList();
        List<DividendHistoryResponse.HistoryRow> responseRows = rows.stream()
                .map(row -> dividendHistoryAnalysisService.toHistoryRow(row, requestedMetrics))
                .toList();

        return DividendHistoryResponse.builder()
                .company(context.companySummary())
                .period(normalizedPeriod)
                .yearsRequested(years)
                .metrics(requestedMetrics)
                .series(series)
                .rows(responseRows)
                .warnings(context.warnings())
                .build();
    }

    @Override
    public DividendAlertsResponse getAlerts(String tickerOrCik, boolean activeOnly) {
        AnalysisContext context = analyze(tickerOrCik);
        List<DividendAlertsResponse.AlertEvent> historicalAlerts = dividendHistoryAnalysisService.buildHistoricalAlerts(
                context.historyRows(),
                context.alerts(),
                context.latestBalance(),
                context.latestAnnual(),
                context.companySummary().getLastFilingDate(),
                activeOnly);

        return DividendAlertsResponse.builder()
                .company(context.companySummary())
                .activeOnly(activeOnly)
                .activeAlerts(context.alerts())
                .historicalAlerts(historicalAlerts)
                .warnings(context.warnings())
                .build();
    }

    @Override
    public DividendEventsResponse getEvents(String tickerOrCik, LocalDate since) {
        CompanyResponse company = resolveCompany(tickerOrCik)
                .orElseThrow(() -> new ResourceNotFoundException("Company", "tickerOrCik", tickerOrCik));

        String cik = normalizeCik(company.getCik())
                .orElseThrow(() -> new IllegalArgumentException("Company CIK is unavailable"));
        String ticker = normalizeTicker(company.getTicker()).orElseGet(() ->
                companyService.getTickerByCik(cik).orElse(null));

        List<Filling> filings = dividendFilingAnalysisService.loadRecentFilings(cik);
        List<Filling> currentReports = dividendFilingAnalysisService.selectFilings(filings, CURRENT_REPORT_FORMS, false, 12);
        List<Filling> annualReports = dividendFilingAnalysisService.selectFilings(filings, ANNUAL_FORMS, false, 2);

        return dividendEvidenceService.buildEvents(
                buildCompanySummary(company, ticker, filings),
                ticker,
                currentReports,
                annualReports,
                filings,
                since);
    }

    @Override
    public DividendEvidenceResponse getEvidence(String tickerOrCik, String accessionNumber) {
        CompanyResponse company = resolveCompany(tickerOrCik)
                .orElseThrow(() -> new ResourceNotFoundException("Company", "tickerOrCik", tickerOrCik));

        String cik = normalizeCik(company.getCik())
                .orElseThrow(() -> new IllegalArgumentException("Company CIK is unavailable"));
        String ticker = normalizeTicker(company.getTicker()).orElseGet(() ->
                companyService.getTickerByCik(cik).orElse(null));

        List<Filling> filings = dividendFilingAnalysisService.loadRecentFilings(cik);
        Filling filing = dividendEvidenceService.resolveFilingByAccession(filings, accessionNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Filing evidence", "accessionNumber", accessionNumber));

        DividendEvidenceResponse response = dividendEvidenceService.buildEvidence(
                buildCompanySummary(company, ticker, filings),
                filings,
                filing,
                accessionNumber);
        if (response == null) {
            throw new ResourceNotFoundException("Filing text", "accessionNumber", accessionNumber);
        }

        return response;
    }

    @Override
    public DividendComparisonResponse compare(List<String> tickersOrCiks, List<String> metrics) {
        List<String> identifiers = normalizeIdentifiers(tickersOrCiks);
        if (identifiers.isEmpty()) {
            throw new IllegalArgumentException("At least one ticker or CIK is required for comparison");
        }

        List<String> requestedMetrics = dividendMetricCatalogService.normalizeComparisonMetrics(metrics);
        List<DividendComparisonResponse.ComparisonRow> rows = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (String identifier : identifiers) {
            try {
                AnalysisContext context = analyze(identifier);
                rows.add(buildComparisonRow(context, requestedMetrics));
            } catch (RuntimeException e) {
                warnings.add("Could not analyze " + identifier + ": " + e.getMessage());
            }
        }

        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("Dividend comparison", "tickersOrCiks", identifiers);
        }

        List<DividendMetricDefinitionResponse> responseMetrics =
                dividendMetricCatalogService.metricDefinitions(requestedMetrics);

        return DividendComparisonResponse.builder()
                .metrics(responseMetrics)
                .companies(rows)
                .warnings(warnings.stream().distinct().toList())
                .build();
    }

    @Override
    public List<DividendMetricDefinitionResponse> getMetricDefinitions() {
        return dividendMetricCatalogService.allMetricDefinitions();
    }

    @Override
    public DividendScreenResponse screen(DividendScreenRequest request) {
        DividendScreenRequest normalizedRequest = request != null ? request : DividendScreenRequest.builder().build();
        int page = Math.max(0, normalizedRequest.getPage());
        int size = Math.max(1, normalizedRequest.getSize());
        int candidateLimit = Math.max(1, Math.min(
                normalizedRequest.getCandidateLimit(),
                DividendScreeningService.DEFAULT_SCREEN_CANDIDATES));
        List<String> warnings = new ArrayList<>();
        List<String> identifiers = resolveScreenIdentifiers(normalizedRequest, candidateLimit, warnings);
        if (identifiers.isEmpty()) {
            throw new ResourceNotFoundException("Dividend screen candidates", "request", normalizedRequest);
        }

        List<String> requestedMetrics = dividendMetricCatalogService.resolveScreenMetrics(normalizedRequest);
        Map<String, String> metricFormatHints = dividendMetricCatalogService.metricFormatHints();
        List<DividendScreenResponse.ScreenResult> results = new ArrayList<>();

        for (String identifier : identifiers) {
            try {
                AnalysisContext context = analyze(identifier);
                DividendScreeningService.ScreeningCandidate screeningCandidate = toScreeningCandidate(context);
                if (dividendScreeningService.matchesScreenFilters(
                        screeningCandidate,
                        normalizedRequest.getFilters(),
                        dividendMetricCatalogService.metricIds(),
                        metricFormatHints)) {
                    results.add(dividendScreeningService.buildScreenResult(screeningCandidate, requestedMetrics));
                }
            } catch (RuntimeException e) {
                warnings.add("Could not analyze " + identifier + ": " + e.getMessage());
            }
        }

        Comparator<DividendScreenResponse.ScreenResult> comparator = dividendScreeningService.buildScreenComparator(
                blankToNull(normalizedRequest.getSort()),
                normalizedRequest.getDirection(),
                dividendMetricCatalogService.metricIds());
        List<DividendScreenResponse.ScreenResult> sortedResults = results.stream()
                .sorted(comparator)
                .toList();

        int fromIndex = Math.min(page * size, sortedResults.size());
        int toIndex = Math.min(fromIndex + size, sortedResults.size());
        PaginatedResponse<DividendScreenResponse.ScreenResult> paginated = PaginatedResponse.of(
                sortedResults.subList(fromIndex, toIndex),
                page,
                size,
                sortedResults.size());

        List<DividendMetricDefinitionResponse> metricDefinitions =
                dividendMetricCatalogService.metricDefinitions(requestedMetrics);

        return DividendScreenResponse.builder()
                .metrics(metricDefinitions)
                .results(paginated)
                .candidatesEvaluated(identifiers.size())
                .warnings(warnings.stream().distinct().toList())
                .build();
    }

    private AnalysisContext analyze(String tickerOrCik) {
        CompanyResponse company = resolveCompany(tickerOrCik)
                .orElseThrow(() -> new ResourceNotFoundException("Company", "tickerOrCik", tickerOrCik));

        String cik = normalizeCik(company.getCik())
                .orElseThrow(() -> new IllegalArgumentException("Company CIK is unavailable"));
        String ticker = normalizeTicker(company.getTicker()).orElseGet(() ->
                companyService.getTickerByCik(cik).orElse(null));

        List<Filling> filings = dividendFilingAnalysisService.loadRecentFilings(cik);
        List<Filling> annualCandidates = dividendFilingAnalysisService.selectFilings(
                filings, ANNUAL_FORMS, true, ANALYSIS_ANNUAL_LIMIT);
        List<Filling> quarterlyCandidates = dividendFilingAnalysisService.selectFilings(
                filings, QUARTERLY_FORMS, true, ANALYSIS_QUARTERLY_LIMIT);
        Filling latestCurrentReport = dividendFilingAnalysisService.selectLatestCurrentReport(filings, CURRENT_REPORT_FORMS);

        List<AnalyzedFilingData> annualAnalyses = dividendFilingAnalysisService.analyzeFilings(annualCandidates);
        List<AnalyzedFilingData> quarterlyAnalyses = dividendFilingAnalysisService.analyzeFilings(quarterlyCandidates);
        AnalyzedFilingData latestAnnual = annualAnalyses.isEmpty() ? null : annualAnalyses.get(0);
        AnalyzedFilingData latestBalance = !quarterlyAnalyses.isEmpty() ? quarterlyAnalyses.get(0) : latestAnnual;

        List<DividendFactPoint> dividendFacts = dividendFilingAnalysisService.loadDividendFactSeries(cik);
        List<DividendOverviewResponse.TrendPoint> trend = dividendFilingAnalysisService.buildTrend(dividendFacts, annualAnalyses);

        Double referencePrice = Optional.ofNullable(ticker)
                .flatMap(companyMarketDataService::getStoredMarketData)
                .map(CompanyMarketData::getCurrentPrice)
                .filter(price -> price != null && price > 0d)
                .orElse(null);
        DividendOverviewComputationService.OverviewComputation computed = dividendOverviewComputationService.computeOverview(
                annualCandidates,
                annualAnalyses,
                quarterlyCandidates,
                quarterlyAnalyses,
                latestAnnual,
                latestBalance,
                trend,
                dividendFacts,
                referencePrice);
        DividendOverviewResponse.CompanySummary companySummary = buildCompanySummary(company, ticker, filings);
        DividendOverviewResponse.Evidence evidence = buildEvidence(latestAnnual, latestCurrentReport);
        List<HistoryRowData> historyRows = dividendHistoryAnalysisService.buildHistoryRows(annualAnalyses, trend);

        return new AnalysisContext(
                companySummary,
                computed.snapshot(),
                computed.confidence(),
                computed.alerts(),
                computed.coverage(),
                computed.balance(),
                trend,
                evidence,
                referencePrice,
                computed.warnings(),
                latestAnnual,
                latestBalance,
                historyRows,
                computed.score(),
                computed.rating());
    }

    private DividendOverviewResponse buildOverview(AnalysisContext context) {
        return DividendOverviewResponse.builder()
                .company(context.companySummary())
                .viability(DividendOverviewResponse.ViabilitySummary.builder()
                        .rating(context.rating())
                        .activeAlerts(context.alerts().size())
                        .score(context.score())
                        .build())
                .snapshot(context.snapshot())
                .confidence(context.confidence())
                .alerts(context.alerts())
                .coverage(context.coverage())
                .balance(context.balance())
                .trend(context.trend())
                .evidence(context.evidence())
                .referencePrice(context.referencePrice())
                .warnings(context.warnings())
                .build();
    }

    private DividendComparisonResponse.ComparisonRow buildComparisonRow(
            AnalysisContext context,
            List<String> requestedMetrics) {
        Map<String, Double> values = new LinkedHashMap<>();
        for (String metric : requestedMetrics) {
            values.put(metric, getComparisonMetricValue(context, metric));
        }

        return DividendComparisonResponse.ComparisonRow.builder()
                .company(context.companySummary())
                .viability(DividendOverviewResponse.ViabilitySummary.builder()
                        .rating(context.rating())
                        .activeAlerts(context.alerts().size())
                        .score(context.score())
                        .build())
                .values(values)
                .warnings(context.warnings())
                .build();
    }

    private DividendScreenResponse.ScreenResult buildScreenResult(
            AnalysisContext context,
            List<String> requestedMetrics) {
        Map<String, Double> values = new LinkedHashMap<>();
        for (String metric : requestedMetrics) {
            values.put(metric, getComparisonMetricValue(context, metric));
        }

        return DividendScreenResponse.ScreenResult.builder()
                .company(context.companySummary())
                .viability(DividendOverviewResponse.ViabilitySummary.builder()
                        .rating(context.rating())
                        .activeAlerts(context.alerts().size())
                        .score(context.score())
                        .build())
                .values(values)
                .warnings(context.warnings())
                .build();
    }

    private List<String> normalizeIdentifiers(List<String> identifiers) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (identifiers == null) {
            return List.of();
        }

        for (String rawIdentifier : identifiers) {
            if (rawIdentifier == null) {
                continue;
            }

            for (String token : rawIdentifier.split(",")) {
                String identifier = blankToNull(token);
                if (identifier != null) {
                    normalized.add(identifier);
                }
            }
        }

        return List.copyOf(normalized);
    }

    private List<String> resolveScreenIdentifiers(
            DividendScreenRequest request,
            int candidateLimit,
            List<String> warnings) {
        List<String> explicitIdentifiers = normalizeIdentifiers(request.getTickersOrCiks());
        if (!explicitIdentifiers.isEmpty()) {
            return explicitIdentifiers.stream().limit(candidateLimit).toList();
        }

        if (blankToNull(request.getSearchTerm()) == null) {
            warnings.add("No tickersOrCiks or searchTerm were provided, so the screen used the first "
                    + candidateLimit + " locally stored companies.");
        }

        CompanySearchRequest companySearch = CompanySearchRequest.builder()
                .searchTerm(blankToNull(request.getSearchTerm()))
                .page(0)
                .size(candidateLimit)
                .sortBy("name")
                .sortDir("asc")
                .build();
        List<CompanyListResponse> candidates = companyService.searchCompanies(companySearch).getContent();
        return candidates.stream()
                .map(candidate -> firstNonBlank(candidate.getTicker(), candidate.getCik()))
                .filter(Objects::nonNull)
                .toList();
    }

    private Map<String, Double> buildComparisonMetricValues(AnalysisContext context) {
        return dividendMetricCatalogService.metricIds().stream()
                .collect(
                        LinkedHashMap::new,
                        (map, metric) -> map.put(metric, getComparisonMetricValue(context, metric)),
                        Map::putAll);
    }

    private DividendScreeningService.ScreeningCandidate toScreeningCandidate(AnalysisContext context) {
        return new DividendScreeningService.ScreeningCandidate(
                context.companySummary(),
                context.alerts(),
                context.score(),
                context.rating(),
                context.warnings(),
                buildComparisonMetricValues(context));
    }

    private Optional<CompanyResponse> resolveCompany(String tickerOrCik) {
        String identifier = tickerOrCik == null ? "" : tickerOrCik.trim();
        if (identifier.isEmpty()) {
            throw new IllegalArgumentException("tickerOrCik is required");
        }

        if (identifier.chars().allMatch(Character::isDigit)) {
            return companyService.getCompanyByCik(identifier);
        }

        return companyService.getCompanyByTicker(identifier.toUpperCase(Locale.ROOT));
    }

    private Double getComparisonMetricValue(AnalysisContext context, String metric) {
        if (context == null || metric == null) {
            return null;
        }

        return switch (metric) {
            case "dps_latest" -> context.snapshot().getDpsLatest();
            case "dps_cagr_5y" -> context.snapshot().getDpsCagr5y();
            case "fcf_payout" -> context.snapshot().getFcfPayoutRatio();
            case "uninterrupted_years" -> context.snapshot().getUninterruptedYears() != null
                    ? context.snapshot().getUninterruptedYears().doubleValue()
                    : null;
            case "consecutive_raises" -> context.snapshot().getConsecutiveRaises() != null
                    ? context.snapshot().getConsecutiveRaises().doubleValue()
                    : null;
            case "net_debt_to_ebitda" -> context.snapshot().getNetDebtToEbitda();
            case "interest_coverage" -> context.snapshot().getInterestCoverage();
            case "current_ratio" -> context.snapshot().getCurrentRatio();
            case "fcf_margin" -> context.snapshot().getFcfMargin();
            case "dividend_yield" -> context.snapshot().getDividendYield();
            case "score" -> Double.valueOf(context.score());
            case "active_alerts" -> Double.valueOf(context.alerts().size());
            default -> {
                HistoryRowData latestHistoryRow = dividendHistoryAnalysisService.latestHistoryRow(context.historyRows());
                yield latestHistoryRow != null ? dividendHistoryAnalysisService.getHistoryMetricValue(latestHistoryRow, metric) : null;
            }
        };
    }

    private DividendOverviewResponse.CompanySummary buildCompanySummary(
            CompanyResponse company,
            String ticker,
            List<Filling> filings) {
        LocalDate lastFilingDate = filings.stream()
                .map(dividendFilingAnalysisService::resolveSortableFilingDate)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        return DividendOverviewResponse.CompanySummary.builder()
                .cik(normalizeCik(company.getCik()).orElse(company.getCik()))
                .ticker(ticker)
                .name(firstNonBlank(company.getName(), ticker, company.getCik()))
                .sector(blankToNull(company.getSicDescription()))
                .fiscalYearEnd(formatFiscalYearEnd(company.getFiscalYearEnd()))
                .lastFilingDate(lastFilingDate)
                .dataFreshness(Instant.now())
                .build();
    }

    private DividendOverviewResponse.Evidence buildEvidence(AnalyzedFilingData latestAnnual, Filling latestCurrentReport) {
        return DividendOverviewResponse.Evidence.builder()
                .latestAnnualReport(toSourceFiling(latestAnnual != null ? latestAnnual.filing() : null,
                        latestAnnual != null ? latestAnnual.filingUrl() : null))
                .latestCurrentReport(toSourceFiling(latestCurrentReport, dividendFilingAnalysisService.resolveFilingUrl(latestCurrentReport)))
                .build();
    }

    private DividendOverviewResponse.SourceFiling toSourceFiling(Filling filing, String url) {
        if (filing == null) {
            return null;
        }

        return DividendOverviewResponse.SourceFiling.builder()
                .formType(filing.getFormType() != null ? filing.getFormType().getNumber() : null)
                .accessionNumber(filing.getAccessionNumber())
                .filingDate(dividendFilingAnalysisService.toLocalDate(filing.getFillingDate()))
                .url(url)
                .build();
    }

    private String formatFiscalYearEnd(Long fiscalYearEnd) {
        if (fiscalYearEnd == null) {
            return null;
        }
        return String.format("%04d", fiscalYearEnd);
    }

    private Optional<String> normalizeCik(String cik) {
        String normalized = blankToNull(cik);
        if (normalized == null) {
            return Optional.empty();
        }

        String digits = normalized.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return Optional.empty();
        }

        try {
            return Optional.of(String.format("%010d", Long.parseLong(digits)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private Optional<String> normalizeTicker(String ticker) {
        String normalized = blankToNull(ticker);
        return normalized != null ? Optional.of(normalized.toUpperCase(Locale.ROOT)) : Optional.empty();
    }


    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        return null;
    }

    private record AnalysisContext(
            DividendOverviewResponse.CompanySummary companySummary,
            DividendOverviewResponse.Snapshot snapshot,
            Map<String, DividendOverviewResponse.MetricConfidence> confidence,
            List<DividendOverviewResponse.Alert> alerts,
            DividendOverviewResponse.Coverage coverage,
            DividendOverviewResponse.Balance balance,
            List<DividendOverviewResponse.TrendPoint> trend,
            DividendOverviewResponse.Evidence evidence,
            Double referencePrice,
            List<String> warnings,
            AnalyzedFilingData latestAnnual,
            AnalyzedFilingData latestBalance,
            List<HistoryRowData> historyRows,
            int score,
            DividendOverviewResponse.DividendRating rating) {
    }

}
