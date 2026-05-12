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
import org.jds.edgar4j.service.impl.DividendHistoryAnalysisService.HistoryMetricDefinition;
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
    private final DividendHistoryAnalysisService dividendHistoryAnalysisService;
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
        List<HistoryRowData> rows = dividendHistoryAnalysisService.limitHistoryRows(context.historyRows(), years);

        List<DividendHistoryResponse.MetricSeries> series = requestedMetrics.stream()
                .map(metric -> dividendHistoryAnalysisService.buildMetricSeries(metric, rows, HISTORY_METRIC_DEFINITIONS))
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
        List<HistoryRowData> historyRows = dividendHistoryAnalysisService.buildHistoryRows(annualAnalyses, trend);

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

    private record MetricDefinitionData(
            String id,
            String label,
            String unit,
            String formatHint,
            String group,
            String description) {
    }

}
