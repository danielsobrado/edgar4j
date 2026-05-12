package org.jds.edgar4j.service.impl;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
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
import org.jds.edgar4j.service.dividend.DividendAlertsService;
import org.jds.edgar4j.service.dividend.DividendMetricsService;
import org.jds.edgar4j.service.dividend.DividendEvidenceService;
import org.jds.edgar4j.service.dividend.DividendScreeningService;
import org.jds.edgar4j.service.impl.DividendFilingAnalysisService.AnalyzedFilingData;
import org.jds.edgar4j.service.impl.DividendFilingAnalysisService.DividendFactPoint;
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

    private static final String HISTORY_PERIOD_FY = "FY";
    private static final List<String> DEFAULT_HISTORY_METRICS = List.of(
            "dps_declared",
            "fcf_payout",
            "earnings_payout");
    private static final List<String> DEFAULT_COMPARISON_METRICS = List.of(
            "fcf_payout",
            "dps_cagr_5y",
            "net_debt_to_ebitda");
    private static final Map<String, HistoryMetricDefinition> HISTORY_METRIC_DEFINITIONS = createHistoryMetricDefinitions();
    private static final Map<String, MetricDefinitionData> METRIC_DEFINITIONS = createMetricDefinitions();

    private final CompanyService companyService;
    private final CompanyMarketDataService companyMarketDataService;
    private final DividendFilingAnalysisService dividendFilingAnalysisService;
    private final DividendMetricsService dividendMetricsService;
    private final DividendAlertsService dividendAlertsService;
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

        String normalizedPeriod = normalizeHistoryPeriod(period);
        List<String> requestedMetrics = normalizeMetrics(metrics);
        AnalysisContext context = analyze(tickerOrCik);
        List<HistoryRowData> rows = limitHistoryRows(context.historyRows(), years);

        List<DividendHistoryResponse.MetricSeries> series = requestedMetrics.stream()
                .map(metric -> buildMetricSeries(metric, rows))
                .toList();
        List<DividendHistoryResponse.HistoryRow> responseRows = rows.stream()
                .map(row -> toHistoryRow(row, requestedMetrics))
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
        List<DividendAlertsResponse.AlertEvent> historicalAlerts = buildHistoricalAlerts(context, activeOnly);

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

        List<String> requestedMetrics = normalizeComparisonMetrics(metrics);
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

        List<DividendMetricDefinitionResponse> responseMetrics = requestedMetrics.stream()
                .map(METRIC_DEFINITIONS::get)
                .filter(Objects::nonNull)
                .map(this::toMetricDefinitionResponse)
                .toList();

        return DividendComparisonResponse.builder()
                .metrics(responseMetrics)
                .companies(rows)
                .warnings(warnings.stream().distinct().toList())
                .build();
    }

    @Override
    public List<DividendMetricDefinitionResponse> getMetricDefinitions() {
        return METRIC_DEFINITIONS.values().stream()
                .map(this::toMetricDefinitionResponse)
                .toList();
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

        List<String> requestedMetrics = resolveScreenMetrics(normalizedRequest);
        Map<String, String> metricFormatHints = METRIC_DEFINITIONS.entrySet().stream()
                .collect(
                        LinkedHashMap::new,
                        (map, entry) -> map.put(entry.getKey(), entry.getValue().formatHint()),
                        Map::putAll);
        List<DividendScreenResponse.ScreenResult> results = new ArrayList<>();

        for (String identifier : identifiers) {
            try {
                AnalysisContext context = analyze(identifier);
                DividendScreeningService.ScreeningCandidate screeningCandidate = toScreeningCandidate(context);
                if (dividendScreeningService.matchesScreenFilters(
                        screeningCandidate,
                        normalizedRequest.getFilters(),
                        METRIC_DEFINITIONS.keySet(),
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
                METRIC_DEFINITIONS.keySet());
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

        List<DividendMetricDefinitionResponse> metricDefinitions = requestedMetrics.stream()
                .map(METRIC_DEFINITIONS::get)
                .filter(Objects::nonNull)
                .map(this::toMetricDefinitionResponse)
                .toList();

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

        Double revenue = dividendFilingAnalysisService.getMetric(latestAnnual,
                List.of("Revenue"),
                List.of("Revenues", "RevenueFromContractWithCustomerExcludingAssessedTax"));
        Double operatingCashFlow = dividendFilingAnalysisService.getMetric(latestAnnual,
                List.of("OperatingCashFlow"),
                List.of("NetCashProvidedByUsedInOperatingActivities"));
        Double capitalExpenditures = dividendMetricsService.magnitude(dividendFilingAnalysisService.getMetric(latestAnnual,
                List.of("CapitalExpenditures"),
                List.of("PaymentsToAcquirePropertyPlantAndEquipment", "PaymentsToAcquireProductiveAssets")));
        Double dividendsPaid = dividendMetricsService.magnitude(dividendFilingAnalysisService.getMetric(latestAnnual,
                List.of("DividendsPaid", "PaymentsOfDividendsCommonStock"),
                List.of("PaymentsOfDividendsCommonStock", "DividendsCommonStockCash", "PaymentsOfOrdinaryDividends")));
        Double freeCashFlow = operatingCashFlow != null && capitalExpenditures != null
                ? operatingCashFlow - capitalExpenditures
                : null;

        Double cash = dividendFilingAnalysisService.getMetric(latestBalance,
                List.of("Cash"),
                List.of("CashAndCashEquivalentsAtCarryingValue"));
        Double longTermDebt = dividendMetricsService.magnitude(dividendFilingAnalysisService.getMetric(latestBalance,
                List.of("LongTermDebt"),
                List.of("LongTermDebt")));
        Double shortTermDebt = dividendMetricsService.magnitude(dividendFilingAnalysisService.getMetric(latestBalance,
                List.of("DebtCurrent", "ShortTermDebt"),
                List.of("DebtCurrent", "LongTermDebtCurrent", "ShortTermBorrowings")));
        Double grossDebt = longTermDebt == null && shortTermDebt == null
                ? null
                : dividendMetricsService.defaultIfNull(longTermDebt) + dividendMetricsService.defaultIfNull(shortTermDebt);
        Double netDebt = grossDebt != null && cash != null ? grossDebt - cash : null;
        Double operatingIncome = dividendFilingAnalysisService.getMetric(latestAnnual,
                List.of("OperatingIncome"),
                List.of("OperatingIncomeLoss"));
        Double depreciationAmortization = dividendMetricsService.magnitude(dividendFilingAnalysisService.getMetric(latestAnnual,
                List.of("DepreciationAmortization"),
                List.of("DepreciationDepletionAndAmortization", "DepreciationAndAmortization")));
        Double ebitdaProxy = operatingIncome != null && depreciationAmortization != null
                ? operatingIncome + depreciationAmortization
                : null;
        Double interestExpense = dividendMetricsService.magnitude(dividendFilingAnalysisService.getMetric(latestAnnual,
                List.of("InterestExpense"),
                List.of("InterestExpense", "InterestExpenseDebt")));
        Double currentAssets = dividendFilingAnalysisService.getMetric(latestBalance,
                List.of("TotalCurrentAssets"),
                List.of("AssetsCurrent"));
        Double currentLiabilities = dividendFilingAnalysisService.getMetric(latestBalance,
                List.of("TotalCurrentLiabilities"),
                List.of("LiabilitiesCurrent"));

        Double cashCoverage = dividendMetricsService.safeDivide(freeCashFlow, dividendsPaid);
        Double retainedCash = freeCashFlow != null && dividendsPaid != null ? freeCashFlow - dividendsPaid : null;
        Double netDebtToEbitda = ebitdaProxy != null && ebitdaProxy > 0d ? dividendMetricsService.safeDivide(netDebt, ebitdaProxy) : null;
        Double currentRatio = dividendMetricsService.safeDivide(currentAssets, currentLiabilities);
        Double interestCoverage = dividendMetricsService.safeDivide(operatingIncome, interestExpense);
        Double fcfMargin = dividendMetricsService.safeDivide(freeCashFlow, revenue);

        Double referencePrice = Optional.ofNullable(ticker)
                .flatMap(companyMarketDataService::getStoredMarketData)
                .map(CompanyMarketData::getCurrentPrice)
                .filter(price -> price != null && price > 0d)
                .orElse(null);

        Double dpsLatest = dividendMetricsService.findLatestDividendPerShare(trend);
        Double dpsCagr5y = dividendMetricsService.calculateDividendCagr(trend, 5);
        Integer uninterruptedYears = dividendMetricsService.countUninterruptedYears(trend);
        Integer consecutiveRaises = dividendMetricsService.countConsecutiveRaises(trend);
        Double dividendYield = referencePrice != null && referencePrice > 0d && dpsLatest != null
                ? dpsLatest / referencePrice
                : null;

        DividendOverviewResponse.Snapshot snapshot = DividendOverviewResponse.Snapshot.builder()
                .dpsLatest(dpsLatest)
                .dpsCagr5y(dpsCagr5y)
                .fcfPayoutRatio(freeCashFlow != null && freeCashFlow > 0d ? dividendMetricsService.safeDivide(dividendsPaid, freeCashFlow) : null)
                .uninterruptedYears(uninterruptedYears)
                .consecutiveRaises(consecutiveRaises)
                .netDebtToEbitda(netDebtToEbitda)
                .interestCoverage(interestCoverage)
                .currentRatio(currentRatio)
                .fcfMargin(fcfMargin)
                .dividendYield(dividendYield)
                .build();

        List<DividendOverviewResponse.Alert> alerts = dividendAlertsService.buildAlerts(trend, snapshot);
        int score = dividendAlertsService.buildScore(snapshot, alerts);
        DividendOverviewResponse.DividendRating rating = dividendAlertsService.toRating(score);

        DividendOverviewResponse.CompanySummary companySummary = buildCompanySummary(company, ticker, filings);
        DividendOverviewResponse.Coverage coverage = DividendOverviewResponse.Coverage.builder()
                .revenue(revenue)
                .operatingCashFlow(operatingCashFlow)
                .capitalExpenditures(capitalExpenditures)
                .freeCashFlow(freeCashFlow)
                .dividendsPaid(dividendsPaid)
                .cashCoverage(cashCoverage)
                .retainedCash(retainedCash)
                .build();
        DividendOverviewResponse.Balance balance = DividendOverviewResponse.Balance.builder()
                .cash(cash)
                .grossDebt(grossDebt)
                .netDebt(netDebt)
                .ebitdaProxy(ebitdaProxy)
                .netDebtToEbitda(netDebtToEbitda)
                .currentRatio(currentRatio)
                .interestCoverage(interestCoverage)
                .build();
        Map<String, DividendOverviewResponse.MetricConfidence> confidence = buildConfidence(
                snapshot, trend, dividendFacts, latestAnnual, latestBalance, referencePrice);
        DividendOverviewResponse.Evidence evidence = buildEvidence(latestAnnual, latestCurrentReport);
        List<String> warnings = buildWarnings(annualCandidates, annualAnalyses, quarterlyCandidates, quarterlyAnalyses,
                latestAnnual, latestBalance, dividendFacts, referencePrice);
        List<HistoryRowData> historyRows = buildHistoryRows(annualAnalyses, trend);

        return new AnalysisContext(
                companySummary,
                snapshot,
                confidence,
                alerts,
                coverage,
                balance,
                trend,
                evidence,
                referencePrice,
                warnings,
                latestAnnual,
                latestBalance,
                historyRows,
                score,
                rating);
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

    private String normalizeHistoryPeriod(String period) {
        String normalized = blankToNull(period);
        if (normalized == null) {
            return HISTORY_PERIOD_FY;
        }

        String upper = normalized.toUpperCase(Locale.ROOT);
        if (HISTORY_PERIOD_FY.equals(upper) || "ANNUAL".equals(upper) || "YEARLY".equals(upper)) {
            return HISTORY_PERIOD_FY;
        }

        throw new IllegalArgumentException("Unsupported history period: " + period + ". Only FY is currently supported.");
    }

    private List<String> normalizeMetrics(List<String> metrics) {
        List<String> requested = metrics == null || metrics.isEmpty() ? DEFAULT_HISTORY_METRICS : metrics;
        LinkedHashSet<String> normalized = new LinkedHashSet<>();

        for (String rawMetric : requested) {
            if (rawMetric == null) {
                continue;
            }

            for (String token : rawMetric.split(",")) {
                String metric = blankToNull(token);
                if (metric == null) {
                    continue;
                }

                String normalizedMetric = metric.toLowerCase(Locale.ROOT);
                if (!HISTORY_METRIC_DEFINITIONS.containsKey(normalizedMetric)) {
                    throw new IllegalArgumentException(
                            "Unsupported dividend history metric: " + metric
                                    + ". Supported metrics: " + String.join(", ", HISTORY_METRIC_DEFINITIONS.keySet()));
                }
                normalized.add(normalizedMetric);
            }
        }

        return normalized.isEmpty() ? DEFAULT_HISTORY_METRICS : List.copyOf(normalized);
    }

    private List<String> normalizeComparisonMetrics(List<String> metrics) {
        List<String> requested = metrics == null || metrics.isEmpty() ? DEFAULT_COMPARISON_METRICS : metrics;
        LinkedHashSet<String> normalized = new LinkedHashSet<>();

        for (String rawMetric : requested) {
            if (rawMetric == null) {
                continue;
            }

            for (String token : rawMetric.split(",")) {
                String metric = blankToNull(token);
                if (metric == null) {
                    continue;
                }

                String normalizedMetric = metric.toLowerCase(Locale.ROOT);
                if (!METRIC_DEFINITIONS.containsKey(normalizedMetric)) {
                    throw new IllegalArgumentException(
                            "Unsupported dividend comparison metric: " + metric
                                    + ". Supported metrics: " + String.join(", ", METRIC_DEFINITIONS.keySet()));
                }
                normalized.add(normalizedMetric);
            }
        }

        return normalized.isEmpty() ? DEFAULT_COMPARISON_METRICS : List.copyOf(normalized);
    }

    private List<String> resolveScreenMetrics(DividendScreenRequest request) {
        LinkedHashSet<String> metrics = new LinkedHashSet<>(normalizeComparisonMetrics(request.getMetrics()));
        if (request.getFilters() != null && request.getFilters().getMetrics() != null) {
            request.getFilters().getMetrics().keySet().stream()
                    .map(this::blankToNull)
                    .filter(Objects::nonNull)
                    .map(metric -> metric.toLowerCase(Locale.ROOT))
                    .forEach(metrics::add);
        }

        String sortMetric = blankToNull(request.getSort());
        if (sortMetric != null && METRIC_DEFINITIONS.containsKey(sortMetric.toLowerCase(Locale.ROOT))) {
            metrics.add(sortMetric.toLowerCase(Locale.ROOT));
        }

        return List.copyOf(metrics);
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
        return METRIC_DEFINITIONS.keySet().stream()
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

    private List<HistoryRowData> limitHistoryRows(List<HistoryRowData> historyRows, int years) {
        if (historyRows == null || historyRows.size() <= years) {
            return historyRows != null ? historyRows : List.of();
        }
        return historyRows.subList(historyRows.size() - years, historyRows.size());
    }

    private List<HistoryRowData> buildHistoryRows(
            List<AnalyzedFilingData> annualAnalyses,
            List<DividendOverviewResponse.TrendPoint> trend) {
        Map<LocalDate, DividendOverviewResponse.TrendPoint> trendByPeriodEnd = new LinkedHashMap<>();
        for (DividendOverviewResponse.TrendPoint point : trend) {
            if (point.getPeriodEnd() != null) {
                trendByPeriodEnd.put(point.getPeriodEnd(), point);
            }
        }

        return annualAnalyses.stream()
                .sorted(Comparator.comparing(dividendFilingAnalysisService::resolveSortableAnalysisDate))
                .map(annual -> toHistoryRowData(annual, trendByPeriodEnd.get(dividendFilingAnalysisService.resolvePeriodEnd(annual))))
                .filter(Objects::nonNull)
                .toList();
    }

    private HistoryRowData toHistoryRowData(
            AnalyzedFilingData annual,
            DividendOverviewResponse.TrendPoint trendPoint) {
        LocalDate periodEnd = dividendFilingAnalysisService.resolvePeriodEnd(annual);
        if (annual == null || periodEnd == null) {
            return null;
        }

        Double revenue = dividendFilingAnalysisService.getMetric(annual,
                List.of("Revenue"),
                List.of("Revenues", "RevenueFromContractWithCustomerExcludingAssessedTax"));
        Double operatingCashFlow = dividendFilingAnalysisService.getMetric(annual,
                List.of("OperatingCashFlow"),
                List.of("NetCashProvidedByUsedInOperatingActivities"));
        Double capitalExpenditures = dividendMetricsService.magnitude(dividendFilingAnalysisService.getMetric(annual,
                List.of("CapitalExpenditures"),
                List.of("PaymentsToAcquirePropertyPlantAndEquipment", "PaymentsToAcquireProductiveAssets")));
        Double dividendsPaid = dividendMetricsService.magnitude(dividendFilingAnalysisService.getMetric(annual,
                List.of("DividendsPaid", "PaymentsOfDividendsCommonStock"),
                List.of("PaymentsOfDividendsCommonStock", "DividendsCommonStockCash", "PaymentsOfOrdinaryDividends")));
        Double freeCashFlow = operatingCashFlow != null && capitalExpenditures != null
                ? operatingCashFlow - capitalExpenditures
                : null;
        Double dividendsPerShare = trendPoint != null && trendPoint.getDividendsPerShare() != null
                ? trendPoint.getDividendsPerShare()
                : dividendFilingAnalysisService.getDividendsPerShare(annual);
        Double earningsPerShare = trendPoint != null && trendPoint.getEarningsPerShare() != null
                ? trendPoint.getEarningsPerShare()
                : dividendFilingAnalysisService.getMetric(annual, List.of("EarningsPerShareDiluted"), List.of("EarningsPerShareDiluted"));
        Double earningsPayoutRatio = earningsPerShare != null && earningsPerShare > 0d && dividendsPerShare != null
                ? dividendMetricsService.safeDivide(dividendsPerShare, earningsPerShare)
                : null;
        Double fcfPayoutRatio = freeCashFlow != null && freeCashFlow > 0d && dividendsPaid != null
                ? dividendMetricsService.safeDivide(dividendsPaid, freeCashFlow)
                : null;
        Double cashCoverage = dividendMetricsService.safeDivide(freeCashFlow, dividendsPaid);
        Double retainedCash = freeCashFlow != null && dividendsPaid != null ? freeCashFlow - dividendsPaid : null;
        Double cash = dividendFilingAnalysisService.getMetric(annual,
                List.of("Cash"),
                List.of("CashAndCashEquivalentsAtCarryingValue"));
        Double longTermDebt = dividendMetricsService.magnitude(dividendFilingAnalysisService.getMetric(annual,
                List.of("LongTermDebt"),
                List.of("LongTermDebt")));
        Double shortTermDebt = dividendMetricsService.magnitude(dividendFilingAnalysisService.getMetric(annual,
                List.of("DebtCurrent", "ShortTermDebt"),
                List.of("DebtCurrent", "LongTermDebtCurrent", "ShortTermBorrowings")));
        Double grossDebt = longTermDebt == null && shortTermDebt == null
                ? null
                : dividendMetricsService.defaultIfNull(longTermDebt) + dividendMetricsService.defaultIfNull(shortTermDebt);
        Double netDebt = grossDebt != null && cash != null ? grossDebt - cash : null;
        Double operatingIncome = dividendFilingAnalysisService.getMetric(annual,
                List.of("OperatingIncome"),
                List.of("OperatingIncomeLoss"));
        Double depreciationAmortization = dividendMetricsService.magnitude(dividendFilingAnalysisService.getMetric(annual,
                List.of("DepreciationAmortization"),
                List.of("DepreciationDepletionAndAmortization", "DepreciationAndAmortization")));
        Double ebitdaProxy = operatingIncome != null && depreciationAmortization != null
                ? operatingIncome + depreciationAmortization
                : null;
        Double interestExpense = dividendMetricsService.magnitude(dividendFilingAnalysisService.getMetric(annual,
                List.of("InterestExpense"),
                List.of("InterestExpense", "InterestExpenseDebt")));
        Double netDebtToEbitda = ebitdaProxy != null && ebitdaProxy > 0d ? dividendMetricsService.safeDivide(netDebt, ebitdaProxy) : null;
        Double currentAssets = dividendFilingAnalysisService.getMetric(annual,
                List.of("TotalCurrentAssets"),
                List.of("AssetsCurrent"));
        Double currentLiabilities = dividendFilingAnalysisService.getMetric(annual,
                List.of("TotalCurrentLiabilities"),
                List.of("LiabilitiesCurrent"));
        Double currentRatio = dividendMetricsService.safeDivide(currentAssets, currentLiabilities);
        Double interestCoverage = dividendMetricsService.safeDivide(operatingIncome, interestExpense);
        Double fcfMargin = dividendMetricsService.safeDivide(freeCashFlow, revenue);

        return new HistoryRowData(
                periodEnd,
                dividendFilingAnalysisService.toLocalDate(annual.filing().getFillingDate()),
                annual.filing().getAccessionNumber(),
                dividendsPerShare,
                earningsPerShare,
                earningsPayoutRatio,
                revenue,
                operatingCashFlow,
                capitalExpenditures,
                freeCashFlow,
                dividendsPaid,
                fcfPayoutRatio,
                cashCoverage,
                retainedCash,
                cash,
                grossDebt,
                netDebt,
                ebitdaProxy,
                netDebtToEbitda,
                currentRatio,
                interestCoverage,
                fcfMargin);
    }

    private DividendHistoryResponse.MetricSeries buildMetricSeries(String metric, List<HistoryRowData> rows) {
        HistoryMetricDefinition definition = HISTORY_METRIC_DEFINITIONS.get(metric);
        List<DividendHistoryResponse.MetricPoint> points = rows.stream()
                .map(row -> DividendHistoryResponse.MetricPoint.builder()
                        .periodEnd(row.periodEnd())
                        .filingDate(row.filingDate())
                        .accessionNumber(row.accessionNumber())
                        .value(getHistoryMetricValue(row, metric))
                        .build())
                .filter(point -> point.getValue() != null)
                .toList();

        Double latestValue = points.isEmpty() ? null : points.get(points.size() - 1).getValue();
        Double cagr = dividendMetricsService.calculateMetricCagr(points);
        Double volatility = dividendMetricsService.calculateMetricVolatility(points);

        return DividendHistoryResponse.MetricSeries.builder()
                .metric(definition.id())
                .label(definition.label())
                .unit(definition.unit())
                .latestValue(latestValue)
                .cagr(cagr)
                .volatility(volatility)
                .trend(dividendMetricsService.determineMetricTrend(points, volatility))
                .points(points)
                .build();
    }

    private DividendHistoryResponse.HistoryRow toHistoryRow(HistoryRowData row, List<String> metrics) {
        Map<String, Double> values = new LinkedHashMap<>();
        for (String metric : metrics) {
            values.put(metric, getHistoryMetricValue(row, metric));
        }

        return DividendHistoryResponse.HistoryRow.builder()
                .periodEnd(row.periodEnd())
                .filingDate(row.filingDate())
                .accessionNumber(row.accessionNumber())
                .metrics(values)
                .build();
    }

    private Double getHistoryMetricValue(HistoryRowData row, String metric) {
        if (row == null) {
            return null;
        }

        return switch (metric) {
            case "dps_declared" -> row.dividendsPerShare();
            case "eps_diluted" -> row.earningsPerShare();
            case "earnings_payout" -> row.earningsPayoutRatio();
            case "revenue" -> row.revenue();
            case "operating_cash_flow" -> row.operatingCashFlow();
            case "capital_expenditures" -> row.capitalExpenditures();
            case "free_cash_flow" -> row.freeCashFlow();
            case "dividends_paid" -> row.dividendsPaid();
            case "fcf_payout" -> row.fcfPayoutRatio();
            case "cash_coverage" -> row.cashCoverage();
            case "retained_cash" -> row.retainedCash();
            case "cash" -> row.cash();
            case "gross_debt" -> row.grossDebt();
            case "net_debt" -> row.netDebt();
            case "net_debt_to_ebitda" -> row.netDebtToEbitda();
            case "current_ratio" -> row.currentRatio();
            case "interest_coverage" -> row.interestCoverage();
            case "fcf_margin" -> row.fcfMargin();
            default -> null;
        };
    }

    private List<DividendAlertsResponse.AlertEvent> buildHistoricalAlerts(AnalysisContext context, boolean activeOnly) {
        List<AlertEventData> events = new ArrayList<>();
        Set<String> activeAlertIds = context.alerts().stream()
                .map(DividendOverviewResponse.Alert::getId)
                .collect(java.util.stream.Collectors.toSet());

        HistoryRowData previous = null;
        for (HistoryRowData row : context.historyRows()) {
            events.addAll(buildAlertEventsForRow(previous, row));
            previous = row;
        }

        LocalDate currentPeriodEnd = firstNonNull(
                dividendFilingAnalysisService.resolvePeriodEnd(context.latestBalance()),
                dividendFilingAnalysisService.resolvePeriodEnd(context.latestAnnual()),
                context.companySummary().getLastFilingDate());
        LocalDate currentFilingDate = firstNonNull(
                context.latestBalance() != null ? dividendFilingAnalysisService.toLocalDate(context.latestBalance().filing().getFillingDate()) : null,
                context.latestAnnual() != null ? dividendFilingAnalysisService.toLocalDate(context.latestAnnual().filing().getFillingDate()) : null,
                context.companySummary().getLastFilingDate());
        String currentAccessionNumber = firstNonBlank(
                context.latestBalance() != null ? context.latestBalance().filing().getAccessionNumber() : null,
                context.latestAnnual() != null ? context.latestAnnual().filing().getAccessionNumber() : null);

        for (DividendOverviewResponse.Alert alert : context.alerts()) {
            boolean present = events.stream()
                    .anyMatch(event -> Objects.equals(event.id(), alert.getId())
                            && Objects.equals(event.periodEnd(), currentPeriodEnd));
            if (!present) {
                events.add(new AlertEventData(
                        alert.getId(),
                        alert.getSeverity(),
                        alert.getTitle(),
                        alert.getDescription(),
                        currentPeriodEnd,
                        currentFilingDate,
                        currentAccessionNumber));
            }
        }

        List<DividendAlertsResponse.AlertEvent> response = events.stream()
                .map(event -> DividendAlertsResponse.AlertEvent.builder()
                        .id(event.id())
                        .severity(event.severity())
                        .title(event.title())
                        .description(event.description())
                        .periodEnd(event.periodEnd())
                        .filingDate(event.filingDate())
                        .accessionNumber(event.accessionNumber())
                        .active(activeAlertIds.contains(event.id()) && Objects.equals(event.periodEnd(), currentPeriodEnd))
                        .build())
                .sorted(Comparator.comparing(DividendAlertsResponse.AlertEvent::getPeriodEnd,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(DividendAlertsResponse.AlertEvent::getFilingDate,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(DividendAlertsResponse.AlertEvent::getId))
                .toList();

        return activeOnly
                ? response.stream().filter(DividendAlertsResponse.AlertEvent::isActive).toList()
                : response;
    }

    private List<AlertEventData> buildAlertEventsForRow(HistoryRowData previous, HistoryRowData current) {
        if (current == null) {
            return List.of();
        }

        List<AlertEventData> events = new ArrayList<>();

        if (previous != null
                && current.dividendsPerShare() != null
                && previous.dividendsPerShare() != null
                && previous.dividendsPerShare() > 0d
                && current.dividendsPerShare() < previous.dividendsPerShare()) {
            events.add(toAlertEventData(dividendAlertsService.alert(
                    "dividend-cut",
                    DividendOverviewResponse.AlertSeverity.HIGH,
                    "Dividend cut detected",
                    "The latest annual dividend-per-share value is below the prior year."),
                    current));
        }

        if (current.fcfPayoutRatio() != null && current.fcfPayoutRatio() > 0.85d) {
            events.add(toAlertEventData(dividendAlertsService.alert(
                    "fcf-payout",
                    current.fcfPayoutRatio() > 1d
                            ? DividendOverviewResponse.AlertSeverity.HIGH
                            : DividendOverviewResponse.AlertSeverity.MEDIUM,
                    "Elevated cash payout ratio",
                    "Dividends are consuming most of free cash flow."),
                    current));
        }

        if (current.currentRatio() != null && current.currentRatio() < 1d) {
            events.add(toAlertEventData(dividendAlertsService.alert(
                    "current-ratio",
                    current.currentRatio() < 0.8d
                            ? DividendOverviewResponse.AlertSeverity.HIGH
                            : DividendOverviewResponse.AlertSeverity.MEDIUM,
                    "Thin near-term liquidity",
                    "Current liabilities exceed or nearly exceed current assets."),
                    current));
        }

        if (current.netDebtToEbitda() != null && current.netDebtToEbitda() > 3.5d) {
            events.add(toAlertEventData(dividendAlertsService.alert(
                    "net-debt-to-ebitda",
                    current.netDebtToEbitda() > 5d
                            ? DividendOverviewResponse.AlertSeverity.HIGH
                            : DividendOverviewResponse.AlertSeverity.MEDIUM,
                    "Leverage is running hot",
                    "Net debt is elevated relative to EBITDA proxy."),
                    current));
        }

        if (current.interestCoverage() != null && current.interestCoverage() < 3d) {
            events.add(toAlertEventData(dividendAlertsService.alert(
                    "interest-coverage",
                    current.interestCoverage() < 2d
                            ? DividendOverviewResponse.AlertSeverity.HIGH
                            : DividendOverviewResponse.AlertSeverity.MEDIUM,
                    "Interest coverage is weak",
                    "Operating income has limited cushion versus interest expense."),
                    current));
        }

        return events;
    }

    private AlertEventData toAlertEventData(DividendOverviewResponse.Alert alert, HistoryRowData current) {
        return new AlertEventData(
                alert.getId(),
                alert.getSeverity(),
                alert.getTitle(),
                alert.getDescription(),
                current.periodEnd(),
                current.filingDate(),
                current.accessionNumber());
    }

    private static Map<String, HistoryMetricDefinition> createHistoryMetricDefinitions() {
        Map<String, HistoryMetricDefinition> definitions = new LinkedHashMap<>();
        definitions.put("dps_declared", new HistoryMetricDefinition("dps_declared", "Dividend Per Share", "USD/share"));
        definitions.put("eps_diluted", new HistoryMetricDefinition("eps_diluted", "Diluted EPS", "USD/share"));
        definitions.put("earnings_payout", new HistoryMetricDefinition("earnings_payout", "Earnings Payout Ratio", "ratio"));
        definitions.put("revenue", new HistoryMetricDefinition("revenue", "Revenue", "USD"));
        definitions.put("operating_cash_flow", new HistoryMetricDefinition("operating_cash_flow", "Operating Cash Flow", "USD"));
        definitions.put("capital_expenditures", new HistoryMetricDefinition("capital_expenditures", "Capital Expenditures", "USD"));
        definitions.put("free_cash_flow", new HistoryMetricDefinition("free_cash_flow", "Free Cash Flow", "USD"));
        definitions.put("dividends_paid", new HistoryMetricDefinition("dividends_paid", "Dividends Paid", "USD"));
        definitions.put("fcf_payout", new HistoryMetricDefinition("fcf_payout", "Free Cash Flow Payout Ratio", "ratio"));
        definitions.put("cash_coverage", new HistoryMetricDefinition("cash_coverage", "Cash Coverage", "ratio"));
        definitions.put("retained_cash", new HistoryMetricDefinition("retained_cash", "Retained Cash After Dividends", "USD"));
        definitions.put("cash", new HistoryMetricDefinition("cash", "Cash", "USD"));
        definitions.put("gross_debt", new HistoryMetricDefinition("gross_debt", "Gross Debt", "USD"));
        definitions.put("net_debt", new HistoryMetricDefinition("net_debt", "Net Debt", "USD"));
        definitions.put("net_debt_to_ebitda", new HistoryMetricDefinition("net_debt_to_ebitda", "Net Debt To EBITDA", "ratio"));
        definitions.put("current_ratio", new HistoryMetricDefinition("current_ratio", "Current Ratio", "ratio"));
        definitions.put("interest_coverage", new HistoryMetricDefinition("interest_coverage", "Interest Coverage", "ratio"));
        definitions.put("fcf_margin", new HistoryMetricDefinition("fcf_margin", "Free Cash Flow Margin", "ratio"));
        return Map.copyOf(definitions);
    }

    private static Map<String, MetricDefinitionData> createMetricDefinitions() {
        Map<String, MetricDefinitionData> definitions = new LinkedHashMap<>();
        definitions.put("dps_latest", metric("dps_latest", "Latest Dividend / Share", "USD/share", "currency",
                "overview", "Most recent annual dividend per share used by the overview snapshot."));
        definitions.put("dps_cagr_5y", metric("dps_cagr_5y", "Dividend CAGR (5Y)", "percent", "percent",
                "overview", "Five-year compound annual growth rate for annual dividend per share."));
        definitions.put("fcf_payout", metric("fcf_payout", "Free Cash Flow Payout Ratio", "percent", "percent",
                "overview", "Dividends paid divided by free cash flow."));
        definitions.put("uninterrupted_years", metric("uninterrupted_years", "Uninterrupted Years", "years", "count",
                "overview", "Consecutive annual periods with a non-zero dividend."));
        definitions.put("consecutive_raises", metric("consecutive_raises", "Consecutive Raises", "years", "count",
                "overview", "Consecutive annual periods in which dividend per share increased."));
        definitions.put("net_debt_to_ebitda", metric("net_debt_to_ebitda", "Net Debt To EBITDA", "x", "multiple",
                "overview", "Net debt divided by EBITDA proxy."));
        definitions.put("interest_coverage", metric("interest_coverage", "Interest Coverage", "x", "multiple",
                "overview", "Operating income divided by interest expense."));
        definitions.put("current_ratio", metric("current_ratio", "Current Ratio", "x", "multiple",
                "overview", "Current assets divided by current liabilities."));
        definitions.put("fcf_margin", metric("fcf_margin", "Free Cash Flow Margin", "percent", "percent",
                "overview", "Free cash flow divided by revenue."));
        definitions.put("dividend_yield", metric("dividend_yield", "Dividend Yield", "percent", "percent",
                "overview", "Estimated dividend yield using stored market price."));
        definitions.put("score", metric("score", "Viability Score", "score", "score",
                "overview", "Composite dividend viability score on a 0-100 scale."));
        definitions.put("active_alerts", metric("active_alerts", "Active Alerts", "count", "count",
                "overview", "Number of currently active dividend pressure alerts."));

        HISTORY_METRIC_DEFINITIONS.values().forEach(definition -> definitions.put(
                definition.id(),
                metric(
                        definition.id(),
                        definition.label(),
                        switch (definition.id()) {
                            case "dps_declared", "eps_diluted" -> "USD/share";
                            case "revenue", "operating_cash_flow", "capital_expenditures", "free_cash_flow",
                                    "dividends_paid", "retained_cash", "cash", "gross_debt", "net_debt" -> "USD";
                            case "net_debt_to_ebitda", "interest_coverage", "current_ratio", "cash_coverage" -> "x";
                            default -> "percent";
                        },
                        switch (definition.id()) {
                            case "dps_declared", "eps_diluted" -> "currency";
                            case "revenue", "operating_cash_flow", "capital_expenditures", "free_cash_flow",
                                    "dividends_paid", "retained_cash", "cash", "gross_debt", "net_debt" -> "compact_currency";
                            case "net_debt_to_ebitda", "interest_coverage", "current_ratio", "cash_coverage" -> "multiple";
                            default -> "percent";
                        },
                        "history",
                        "Annual history metric returned by the dividend history endpoint.")));

        return Map.copyOf(definitions);
    }

    private static MetricDefinitionData metric(
            String id,
            String label,
            String unit,
            String formatHint,
            String group,
            String description) {
        return new MetricDefinitionData(id, label, unit, formatHint, group, description);
    }

    private DividendMetricDefinitionResponse toMetricDefinitionResponse(MetricDefinitionData definition) {
        return DividendMetricDefinitionResponse.builder()
                .id(definition.id())
                .label(definition.label())
                .unit(definition.unit())
                .formatHint(definition.formatHint())
                .group(definition.group())
                .description(definition.description())
                .build();
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
                HistoryRowData latestHistoryRow = latestHistoryRow(context);
                yield latestHistoryRow != null ? getHistoryMetricValue(latestHistoryRow, metric) : null;
            }
        };
    }

    private HistoryRowData latestHistoryRow(AnalysisContext context) {
        if (context == null || context.historyRows() == null || context.historyRows().isEmpty()) {
            return null;
        }
        return context.historyRows().get(context.historyRows().size() - 1);
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

    private Map<String, DividendOverviewResponse.MetricConfidence> buildConfidence(
            DividendOverviewResponse.Snapshot snapshot,
            List<DividendOverviewResponse.TrendPoint> trend,
            List<DividendFactPoint> dividendFacts,
            AnalyzedFilingData latestAnnual,
            AnalyzedFilingData latestBalance,
            Double referencePrice) {
        Map<String, DividendOverviewResponse.MetricConfidence> confidence = new LinkedHashMap<>();

        int dividendPointCount = (int) trend.stream().filter(point -> point.getDividendsPerShare() != null).count();
        confidence.put("dpsLatest", !dividendFacts.isEmpty()
                ? DividendOverviewResponse.MetricConfidence.HIGH
                : dividendFilingAnalysisService.hasDirectDividendsPerShare(latestAnnual)
                        ? DividendOverviewResponse.MetricConfidence.MEDIUM
                        : snapshot.getDpsLatest() != null
                                ? DividendOverviewResponse.MetricConfidence.LOW_MEDIUM
                                : DividendOverviewResponse.MetricConfidence.LOW);
        confidence.put("dpsCagr5y", dividendPointCount >= 6
                ? (!dividendFacts.isEmpty()
                        ? DividendOverviewResponse.MetricConfidence.HIGH
                        : DividendOverviewResponse.MetricConfidence.MEDIUM)
                : DividendOverviewResponse.MetricConfidence.LOW);
        confidence.put("fcfPayoutRatio", snapshot.getFcfPayoutRatio() != null
                ? DividendOverviewResponse.MetricConfidence.MEDIUM
                : DividendOverviewResponse.MetricConfidence.LOW);
        confidence.put("uninterruptedYears", dividendPointCount >= 6
                ? DividendOverviewResponse.MetricConfidence.HIGH
                : dividendPointCount >= 2
                        ? DividendOverviewResponse.MetricConfidence.MEDIUM
                        : DividendOverviewResponse.MetricConfidence.LOW);
        confidence.put("consecutiveRaises", dividendPointCount >= 6
                ? DividendOverviewResponse.MetricConfidence.MEDIUM
                : dividendPointCount >= 2
                        ? DividendOverviewResponse.MetricConfidence.LOW_MEDIUM
                        : DividendOverviewResponse.MetricConfidence.LOW);
        confidence.put("netDebtToEbitda", snapshot.getNetDebtToEbitda() != null
                ? DividendOverviewResponse.MetricConfidence.LOW_MEDIUM
                : DividendOverviewResponse.MetricConfidence.LOW);
        confidence.put("interestCoverage", snapshot.getInterestCoverage() != null
                ? DividendOverviewResponse.MetricConfidence.MEDIUM
                : DividendOverviewResponse.MetricConfidence.LOW);
        confidence.put("currentRatio", latestBalance != null && snapshot.getCurrentRatio() != null
                ? DividendOverviewResponse.MetricConfidence.HIGH
                : DividendOverviewResponse.MetricConfidence.LOW);
        confidence.put("fcfMargin", snapshot.getFcfMargin() != null
                ? DividendOverviewResponse.MetricConfidence.MEDIUM
                : DividendOverviewResponse.MetricConfidence.LOW);
        confidence.put("dividendYield", referencePrice != null && snapshot.getDividendYield() != null
                ? DividendOverviewResponse.MetricConfidence.MEDIUM
                : DividendOverviewResponse.MetricConfidence.LOW);

        return confidence;
    }

    private List<String> buildWarnings(
            List<Filling> annualCandidates,
            List<AnalyzedFilingData> annualAnalyses,
            List<Filling> quarterlyCandidates,
            List<AnalyzedFilingData> quarterlyAnalyses,
            AnalyzedFilingData latestAnnual,
            AnalyzedFilingData latestBalance,
            List<DividendFactPoint> dividendFacts,
            Double referencePrice) {
        List<String> warnings = new ArrayList<>();

        if (latestAnnual == null) {
            warnings.add("No analyzable annual XBRL filing was available, so payout and profitability metrics are limited.");
        } else if (annualAnalyses.size() < annualCandidates.size()) {
            warnings.add("Some recent annual filings could not be parsed for XBRL analysis and were excluded from the overview.");
        }

        if (annualAnalyses.size() < 6) {
            warnings.add("Fewer than six annual XBRL filings were available, so long-range dividend trend coverage is limited.");
        }

        if (quarterlyCandidates.isEmpty() || quarterlyAnalyses.isEmpty()) {
            if (latestBalance != null && latestAnnual != null && latestBalance == latestAnnual) {
                warnings.add("No recent quarterly balance-sheet filing was available, so liquidity and leverage use the latest annual report.");
            } else if (latestBalance == null) {
                warnings.add("No recent balance-sheet filing was available, so liquidity and leverage metrics could not be computed.");
            }
        }

        if (dividendFacts.isEmpty()) {
            warnings.add("SEC companyfacts dividend history was unavailable, so streak metrics rely on filing-level XBRL only.");
        }

        if (referencePrice == null) {
            warnings.add("Stored market-price data is unavailable, so dividend yield could not be estimated.");
        }

        return warnings;
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

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        if (values == null) {
            return null;
        }

        for (T value : values) {
            if (value != null) {
                return value;
            }
        }

        return null;
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

    private record HistoryMetricDefinition(
            String id,
            String label,
            String unit) {
    }

    private record MetricDefinitionData(
            String id,
            String label,
            String unit,
            String formatHint,
            String group,
            String description) {
    }

    private record HistoryRowData(
            LocalDate periodEnd,
            LocalDate filingDate,
            String accessionNumber,
            Double dividendsPerShare,
            Double earningsPerShare,
            Double earningsPayoutRatio,
            Double revenue,
            Double operatingCashFlow,
            Double capitalExpenditures,
            Double freeCashFlow,
            Double dividendsPaid,
            Double fcfPayoutRatio,
            Double cashCoverage,
            Double retainedCash,
            Double cash,
            Double grossDebt,
            Double netDebt,
            Double ebitdaProxy,
            Double netDebtToEbitda,
            Double currentRatio,
            Double interestCoverage,
            Double fcfMargin) {
    }

    private record AlertEventData(
            String id,
            DividendOverviewResponse.AlertSeverity severity,
            String title,
            String description,
            LocalDate periodEnd,
            LocalDate filingDate,
            String accessionNumber) {
    }
}
