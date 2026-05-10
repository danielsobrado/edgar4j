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
import org.jds.edgar4j.integration.SecApiClient;
import org.jds.edgar4j.integration.SecApiConfig;
import org.jds.edgar4j.integration.SecResponseParser;
import org.jds.edgar4j.integration.model.SecCompanyFactsResponse;
import org.jds.edgar4j.model.CompanyMarketData;
import org.jds.edgar4j.model.Filling;
import org.jds.edgar4j.port.FillingDataPort;
import org.jds.edgar4j.service.CompanyMarketDataService;
import org.jds.edgar4j.service.CompanyService;
import org.jds.edgar4j.service.DividendAnalysisService;
import org.jds.edgar4j.service.dividend.DividendAlertsService;
import org.jds.edgar4j.service.dividend.DividendEventExtractor;
import org.jds.edgar4j.service.dividend.DividendMetricsService;
import org.jds.edgar4j.validation.UrlAllowlistValidator;
import org.jds.edgar4j.xbrl.XbrlService;
import org.jds.edgar4j.xbrl.model.XbrlInstance;
import org.jds.edgar4j.xbrl.sec.SecFilingExtractor;
import org.jds.edgar4j.xbrl.standardization.ConceptStandardizer;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DividendAnalysisServiceImpl implements DividendAnalysisService {

    private static final Duration XBRL_TIMEOUT = Duration.ofSeconds(45);
    private static final int RECENT_FILING_LIMIT = 80;
    private static final int ANALYSIS_ANNUAL_LIMIT = 6;
    private static final int ANALYSIS_QUARTERLY_LIMIT = 2;
    private static final int MAX_EVIDENCE_TEXT_LENGTH = 12_000;
    private static final int MAX_SCREEN_CANDIDATES = 100;

    private static final Set<String> ANNUAL_FORMS = Set.of(
            "10-K", "10-K/A", "20-F", "20-F/A", "40-F", "40-F/A");
    private static final Set<String> QUARTERLY_FORMS = Set.of(
            "10-Q", "10-Q/A");
    private static final Set<String> CURRENT_REPORT_FORMS = Set.of(
            "8-K", "8-K/A");

    private static final List<String> DIRECT_DPS_STANDARD_KEYS = List.of(
            "DividendsPerShare",
            "CommonStockDividendsPerShareDeclared",
            "CommonStockDividendsPerShareCashPaid",
            "CommonStockDividendsPerShareDeclaredAndPaid");
    private static final List<String> DIRECT_DPS_FINANCIAL_KEYS = List.of(
            "CommonStockDividendsPerShareDeclared",
            "CommonStockDividendsPerShareCashPaid",
            "CommonStockDividendsPerShareDeclaredAndPaid",
            "DividendsPerShare");
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
    private final FillingDataPort fillingRepository;
    private final CompanyMarketDataService companyMarketDataService;
    private final SecApiClient secApiClient;
    private final SecResponseParser secResponseParser;
    private final SecApiConfig secApiConfig;
    private final XbrlService xbrlService;
    private final UrlAllowlistValidator urlAllowlistValidator;
    private final DividendEventExtractor dividendEventExtractor;
    private final DividendMetricsService dividendMetricsService;
    private final DividendAlertsService dividendAlertsService;

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

        List<Filling> filings = loadRecentFilings(cik);
        List<Filling> currentReports = selectFilings(filings, CURRENT_REPORT_FORMS, false, 12);
        List<Filling> annualReports = selectFilings(filings, ANNUAL_FORMS, false, 2);

        List<DividendEventsResponse.DividendEvent> events = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (currentReports.isEmpty()) {
            warnings.add("No recent 8-K filings were available for dividend event extraction.");
        }

        for (Filling filing : currentReports) {
            extractDividendEvents(events, warnings, filing);
        }

        for (Filling filing : annualReports) {
            extractDividendEvents(events, warnings, filing);
        }

        List<DividendEventsResponse.DividendEvent> filteredEvents = events.stream()
                .filter(event -> since == null || !resolveEventDate(event).isBefore(since))
                .sorted(Comparator
                        .comparing(this::resolveEventDate, Comparator.reverseOrder())
                        .thenComparing(DividendEventsResponse.DividendEvent::getFiledDate,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(DividendEventsResponse.DividendEvent::getId))
                .toList();

        if (filteredEvents.isEmpty()) {
            warnings.add("No dividend declaration or policy events were extracted from the currently available filing text.");
        }

        return DividendEventsResponse.builder()
                .company(buildCompanySummary(company, ticker, filings))
                .events(filteredEvents)
                .warnings(warnings.stream().distinct().toList())
                .build();
    }

    @Override
    public DividendEvidenceResponse getEvidence(String tickerOrCik, String accessionNumber) {
        CompanyResponse company = resolveCompany(tickerOrCik)
                .orElseThrow(() -> new ResourceNotFoundException("Company", "tickerOrCik", tickerOrCik));

        String cik = normalizeCik(company.getCik())
                .orElseThrow(() -> new IllegalArgumentException("Company CIK is unavailable"));
        String ticker = normalizeTicker(company.getTicker()).orElseGet(() ->
                companyService.getTickerByCik(cik).orElse(null));

        List<Filling> filings = loadRecentFilings(cik);
        Filling filing = filings.stream()
                .filter(candidate -> accessionMatches(candidate.getAccessionNumber(), accessionNumber))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Filing evidence", "accessionNumber", accessionNumber));

        String rawDocument = loadFilingDocument(filing)
                .orElseThrow(() -> new ResourceNotFoundException("Filing text", "accessionNumber", accessionNumber));
        String filingUrl = resolveFilingUrl(filing);
        List<String> warnings = new ArrayList<>();

        List<DividendEvidenceResponse.EvidenceHighlight> highlights = dividendEventExtractor
                .extract(rawDocument, filing, filingUrl).stream()
                .map(this::toEvidenceHighlight)
                .toList();
        if (highlights.isEmpty()) {
            warnings.add("No dividend highlights were extracted from the filing text.");
        }

        String cleanedDocument = dividendEventExtractor.cleanDocumentText(rawDocument);
        boolean truncated = cleanedDocument.length() > MAX_EVIDENCE_TEXT_LENGTH;
        String cleanedPreview = truncated
                ? cleanedDocument.substring(0, MAX_EVIDENCE_TEXT_LENGTH).trim()
                : cleanedDocument;
        if (cleanedPreview.isBlank()) {
            warnings.add("The filing document did not produce usable cleaned text.");
        }
        if (truncated) {
            warnings.add("Cleaned filing text preview was truncated to the first 12000 characters.");
        }

        return DividendEvidenceResponse.builder()
                .company(buildCompanySummary(company, ticker, filings))
                .filing(toSourceFiling(filing, filingUrl))
                .highlights(highlights)
                .cleanedText(blankToNull(cleanedPreview))
                .truncated(truncated)
                .warnings(warnings.stream().distinct().toList())
                .build();
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
        int candidateLimit = Math.max(1, Math.min(normalizedRequest.getCandidateLimit(), MAX_SCREEN_CANDIDATES));
        List<String> warnings = new ArrayList<>();
        List<String> identifiers = resolveScreenIdentifiers(normalizedRequest, candidateLimit, warnings);
        if (identifiers.isEmpty()) {
            throw new ResourceNotFoundException("Dividend screen candidates", "request", normalizedRequest);
        }

        List<String> requestedMetrics = resolveScreenMetrics(normalizedRequest);
        List<DividendScreenResponse.ScreenResult> results = new ArrayList<>();

        for (String identifier : identifiers) {
            try {
                AnalysisContext context = analyze(identifier);
                if (matchesScreenFilters(context, normalizedRequest.getFilters())) {
                    results.add(buildScreenResult(context, requestedMetrics));
                }
            } catch (RuntimeException e) {
                warnings.add("Could not analyze " + identifier + ": " + e.getMessage());
            }
        }

        Comparator<DividendScreenResponse.ScreenResult> comparator = buildScreenComparator(
                blankToNull(normalizedRequest.getSort()),
                normalizedRequest.getDirection());
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

        List<Filling> filings = loadRecentFilings(cik);
        List<Filling> annualCandidates = selectFilings(filings, ANNUAL_FORMS, true, ANALYSIS_ANNUAL_LIMIT);
        List<Filling> quarterlyCandidates = selectFilings(filings, QUARTERLY_FORMS, true, ANALYSIS_QUARTERLY_LIMIT);
        Filling latestCurrentReport = selectLatestCurrentReport(filings);

        List<AnalyzedFilingData> annualAnalyses = analyzeFilings(annualCandidates);
        List<AnalyzedFilingData> quarterlyAnalyses = analyzeFilings(quarterlyCandidates);
        AnalyzedFilingData latestAnnual = annualAnalyses.isEmpty() ? null : annualAnalyses.get(0);
        AnalyzedFilingData latestBalance = !quarterlyAnalyses.isEmpty() ? quarterlyAnalyses.get(0) : latestAnnual;

        List<DividendFactPoint> dividendFacts = loadDividendFactSeries(cik);
        List<DividendOverviewResponse.TrendPoint> trend = buildTrend(dividendFacts, annualAnalyses);

        Double revenue = getMetric(latestAnnual,
                List.of("Revenue"),
                List.of("Revenues", "RevenueFromContractWithCustomerExcludingAssessedTax"));
        Double operatingCashFlow = getMetric(latestAnnual,
                List.of("OperatingCashFlow"),
                List.of("NetCashProvidedByUsedInOperatingActivities"));
        Double capitalExpenditures = dividendMetricsService.magnitude(getMetric(latestAnnual,
                List.of("CapitalExpenditures"),
                List.of("PaymentsToAcquirePropertyPlantAndEquipment", "PaymentsToAcquireProductiveAssets")));
        Double dividendsPaid = dividendMetricsService.magnitude(getMetric(latestAnnual,
                List.of("DividendsPaid", "PaymentsOfDividendsCommonStock"),
                List.of("PaymentsOfDividendsCommonStock", "DividendsCommonStockCash", "PaymentsOfOrdinaryDividends")));
        Double freeCashFlow = operatingCashFlow != null && capitalExpenditures != null
                ? operatingCashFlow - capitalExpenditures
                : null;

        Double cash = getMetric(latestBalance,
                List.of("Cash"),
                List.of("CashAndCashEquivalentsAtCarryingValue"));
        Double longTermDebt = dividendMetricsService.magnitude(getMetric(latestBalance,
                List.of("LongTermDebt"),
                List.of("LongTermDebt")));
        Double shortTermDebt = dividendMetricsService.magnitude(getMetric(latestBalance,
                List.of("DebtCurrent", "ShortTermDebt"),
                List.of("DebtCurrent", "LongTermDebtCurrent", "ShortTermBorrowings")));
        Double grossDebt = longTermDebt == null && shortTermDebt == null
                ? null
                : dividendMetricsService.defaultIfNull(longTermDebt) + dividendMetricsService.defaultIfNull(shortTermDebt);
        Double netDebt = grossDebt != null && cash != null ? grossDebt - cash : null;
        Double operatingIncome = getMetric(latestAnnual,
                List.of("OperatingIncome"),
                List.of("OperatingIncomeLoss"));
        Double depreciationAmortization = dividendMetricsService.magnitude(getMetric(latestAnnual,
                List.of("DepreciationAmortization"),
                List.of("DepreciationDepletionAndAmortization", "DepreciationAndAmortization")));
        Double ebitdaProxy = operatingIncome != null && depreciationAmortization != null
                ? operatingIncome + depreciationAmortization
                : null;
        Double interestExpense = dividendMetricsService.magnitude(getMetric(latestAnnual,
                List.of("InterestExpense"),
                List.of("InterestExpense", "InterestExpenseDebt")));
        Double currentAssets = getMetric(latestBalance,
                List.of("TotalCurrentAssets"),
                List.of("AssetsCurrent"));
        Double currentLiabilities = getMetric(latestBalance,
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

    private boolean matchesScreenFilters(
            AnalysisContext context,
            DividendScreenRequest.DividendScreenFilters filters) {
        if (context == null || filters == null) {
            return true;
        }

        if (filters.getViabilityRatings() != null
                && !filters.getViabilityRatings().isEmpty()
                && !filters.getViabilityRatings().contains(context.rating())) {
            return false;
        }

        if (filters.getSectors() != null && !filters.getSectors().isEmpty()) {
            String sector = blankToNull(context.companySummary().getSector());
            boolean sectorMatch = filters.getSectors().stream()
                    .map(this::blankToNull)
                    .filter(Objects::nonNull)
                    .anyMatch(candidate -> sector != null && sector.equalsIgnoreCase(candidate));
            if (!sectorMatch) {
                return false;
            }
        }

        if (filters.getMetrics() == null || filters.getMetrics().isEmpty()) {
            return true;
        }

        for (Map.Entry<String, DividendScreenRequest.MetricRange> entry : filters.getMetrics().entrySet()) {
            String metric = blankToNull(entry.getKey());
            if (metric == null) {
                continue;
            }

            String normalizedMetric = metric.toLowerCase(Locale.ROOT);
            if (!METRIC_DEFINITIONS.containsKey(normalizedMetric)) {
                throw new IllegalArgumentException(
                        "Unsupported dividend screen metric: " + metric
                                + ". Supported metrics: " + String.join(", ", METRIC_DEFINITIONS.keySet()));
            }

            Double actualValue = getComparisonMetricValue(context, normalizedMetric);
            Double minValue = normalizeScreenBound(normalizedMetric, entry.getValue() != null ? entry.getValue().getMin() : null);
            Double maxValue = normalizeScreenBound(normalizedMetric, entry.getValue() != null ? entry.getValue().getMax() : null);

            if (minValue != null && (actualValue == null || actualValue < minValue)) {
                return false;
            }
            if (maxValue != null && (actualValue == null || actualValue > maxValue)) {
                return false;
            }
        }

        return true;
    }

    private Double normalizeScreenBound(String metric, Double rawValue) {
        if (rawValue == null) {
            return null;
        }

        MetricDefinitionData definition = METRIC_DEFINITIONS.get(metric);
        if (definition != null
                && "percent".equalsIgnoreCase(definition.formatHint())
                && Math.abs(rawValue) > 1d) {
            return rawValue / 100d;
        }
        return rawValue;
    }

    private Comparator<DividendScreenResponse.ScreenResult> buildScreenComparator(String sort, String direction) {
        String normalizedSort = sort != null ? sort.toLowerCase(Locale.ROOT) : "score";
        boolean descending = direction == null || !"asc".equalsIgnoreCase(direction);

        if ("name".equals(normalizedSort)) {
            return (left, right) -> compareNullableStrings(
                    firstNonBlank(left.getCompany().getName(), left.getCompany().getTicker(), left.getCompany().getCik()),
                    firstNonBlank(right.getCompany().getName(), right.getCompany().getTicker(), right.getCompany().getCik()),
                    descending);
        }

        if ("ticker".equals(normalizedSort)) {
            return (left, right) -> compareNullableStrings(
                    left.getCompany().getTicker(),
                    right.getCompany().getTicker(),
                    descending);
        }

        if (!METRIC_DEFINITIONS.containsKey(normalizedSort)) {
            throw new IllegalArgumentException(
                    "Unsupported dividend screen sort field: " + sort
                            + ". Supported fields: " + String.join(", ", METRIC_DEFINITIONS.keySet()) + ", name, ticker");
        }
        return (left, right) -> compareNullableDoubles(
                left.getValues().get(normalizedSort),
                right.getValues().get(normalizedSort),
                descending);
    }

    private int compareNullableStrings(String left, String right, boolean descending) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        int comparison = String.CASE_INSENSITIVE_ORDER.compare(left, right);
        return descending ? -comparison : comparison;
    }

    private int compareNullableDoubles(Double left, Double right, boolean descending) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        int comparison = Double.compare(left, right);
        return descending ? -comparison : comparison;
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
                .sorted(Comparator.comparing(this::resolveSortableAnalysisDate))
                .map(annual -> toHistoryRowData(annual, trendByPeriodEnd.get(resolvePeriodEnd(annual))))
                .filter(Objects::nonNull)
                .toList();
    }

    private HistoryRowData toHistoryRowData(
            AnalyzedFilingData annual,
            DividendOverviewResponse.TrendPoint trendPoint) {
        LocalDate periodEnd = resolvePeriodEnd(annual);
        if (annual == null || periodEnd == null) {
            return null;
        }

        Double revenue = getMetric(annual,
                List.of("Revenue"),
                List.of("Revenues", "RevenueFromContractWithCustomerExcludingAssessedTax"));
        Double operatingCashFlow = getMetric(annual,
                List.of("OperatingCashFlow"),
                List.of("NetCashProvidedByUsedInOperatingActivities"));
        Double capitalExpenditures = dividendMetricsService.magnitude(getMetric(annual,
                List.of("CapitalExpenditures"),
                List.of("PaymentsToAcquirePropertyPlantAndEquipment", "PaymentsToAcquireProductiveAssets")));
        Double dividendsPaid = dividendMetricsService.magnitude(getMetric(annual,
                List.of("DividendsPaid", "PaymentsOfDividendsCommonStock"),
                List.of("PaymentsOfDividendsCommonStock", "DividendsCommonStockCash", "PaymentsOfOrdinaryDividends")));
        Double freeCashFlow = operatingCashFlow != null && capitalExpenditures != null
                ? operatingCashFlow - capitalExpenditures
                : null;
        Double dividendsPerShare = trendPoint != null && trendPoint.getDividendsPerShare() != null
                ? trendPoint.getDividendsPerShare()
                : getDividendsPerShare(annual);
        Double earningsPerShare = trendPoint != null && trendPoint.getEarningsPerShare() != null
                ? trendPoint.getEarningsPerShare()
                : getMetric(annual, List.of("EarningsPerShareDiluted"), List.of("EarningsPerShareDiluted"));
        Double earningsPayoutRatio = earningsPerShare != null && earningsPerShare > 0d && dividendsPerShare != null
                ? dividendMetricsService.safeDivide(dividendsPerShare, earningsPerShare)
                : null;
        Double fcfPayoutRatio = freeCashFlow != null && freeCashFlow > 0d && dividendsPaid != null
                ? dividendMetricsService.safeDivide(dividendsPaid, freeCashFlow)
                : null;
        Double cashCoverage = dividendMetricsService.safeDivide(freeCashFlow, dividendsPaid);
        Double retainedCash = freeCashFlow != null && dividendsPaid != null ? freeCashFlow - dividendsPaid : null;
        Double cash = getMetric(annual,
                List.of("Cash"),
                List.of("CashAndCashEquivalentsAtCarryingValue"));
        Double longTermDebt = dividendMetricsService.magnitude(getMetric(annual,
                List.of("LongTermDebt"),
                List.of("LongTermDebt")));
        Double shortTermDebt = dividendMetricsService.magnitude(getMetric(annual,
                List.of("DebtCurrent", "ShortTermDebt"),
                List.of("DebtCurrent", "LongTermDebtCurrent", "ShortTermBorrowings")));
        Double grossDebt = longTermDebt == null && shortTermDebt == null
                ? null
                : dividendMetricsService.defaultIfNull(longTermDebt) + dividendMetricsService.defaultIfNull(shortTermDebt);
        Double netDebt = grossDebt != null && cash != null ? grossDebt - cash : null;
        Double operatingIncome = getMetric(annual,
                List.of("OperatingIncome"),
                List.of("OperatingIncomeLoss"));
        Double depreciationAmortization = dividendMetricsService.magnitude(getMetric(annual,
                List.of("DepreciationAmortization"),
                List.of("DepreciationDepletionAndAmortization", "DepreciationAndAmortization")));
        Double ebitdaProxy = operatingIncome != null && depreciationAmortization != null
                ? operatingIncome + depreciationAmortization
                : null;
        Double interestExpense = dividendMetricsService.magnitude(getMetric(annual,
                List.of("InterestExpense"),
                List.of("InterestExpense", "InterestExpenseDebt")));
        Double netDebtToEbitda = ebitdaProxy != null && ebitdaProxy > 0d ? dividendMetricsService.safeDivide(netDebt, ebitdaProxy) : null;
        Double currentAssets = getMetric(annual,
                List.of("TotalCurrentAssets"),
                List.of("AssetsCurrent"));
        Double currentLiabilities = getMetric(annual,
                List.of("TotalCurrentLiabilities"),
                List.of("LiabilitiesCurrent"));
        Double currentRatio = dividendMetricsService.safeDivide(currentAssets, currentLiabilities);
        Double interestCoverage = dividendMetricsService.safeDivide(operatingIncome, interestExpense);
        Double fcfMargin = dividendMetricsService.safeDivide(freeCashFlow, revenue);

        return new HistoryRowData(
                periodEnd,
                toLocalDate(annual.filing().getFillingDate()),
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
                resolvePeriodEnd(context.latestBalance()),
                resolvePeriodEnd(context.latestAnnual()),
                context.companySummary().getLastFilingDate());
        LocalDate currentFilingDate = firstNonNull(
                context.latestBalance() != null ? toLocalDate(context.latestBalance().filing().getFillingDate()) : null,
                context.latestAnnual() != null ? toLocalDate(context.latestAnnual().filing().getFillingDate()) : null,
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

    private void extractDividendEvents(
            List<DividendEventsResponse.DividendEvent> events,
            List<String> warnings,
            Filling filing) {
        Optional<String> rawDocument = loadFilingDocument(filing);
        if (rawDocument.isEmpty()) {
            warnings.add("Filing text could not be loaded for accession "
                    + firstNonBlank(filing != null ? filing.getAccessionNumber() : null, "(unknown)")
                    + ".");
            return;
        }

        String filingUrl = resolveFilingUrl(filing);
        List<DividendEventsResponse.DividendEvent> extracted = dividendEventExtractor.extract(rawDocument.get(), filing, filingUrl).stream()
                .map(this::toDividendEventResponse)
                .toList();
        extracted.forEach(event -> putIfAbsent(events, event));
    }

    private Optional<String> loadFilingDocument(Filling filing) {
        if (filing == null) {
            return Optional.empty();
        }

        String cik = normalizeCik(filing.getCik()).orElse(null);
        String accessionNumber = blankToNull(filing.getAccessionNumber());
        String primaryDocument = firstNonBlank(
                filing.getPrimaryDocument(),
                extractDocumentNameFromUrl(filing.getUrl()));
        if (cik == null || accessionNumber == null || primaryDocument == null) {
            return Optional.empty();
        }

        try {
            return Optional.ofNullable(secApiClient.fetchFiling(cik, accessionNumber, primaryDocument));
        } catch (Exception e) {
            log.debug("Could not load filing document for {}", accessionNumber, e);
            return Optional.empty();
        }
    }

    private String extractDocumentNameFromUrl(String url) {
        String normalized = blankToNull(url);
        if (normalized == null) {
            return null;
        }

        int queryIndex = normalized.indexOf('?');
        String withoutQuery = queryIndex >= 0 ? normalized.substring(0, queryIndex) : normalized;
        int slashIndex = withoutQuery.lastIndexOf('/');
        if (slashIndex < 0 || slashIndex == withoutQuery.length() - 1) {
            return null;
        }

        String candidate = withoutQuery.substring(slashIndex + 1);
        return blankToNull(candidate);
    }

    private DividendEventsResponse.DividendEvent toDividendEventResponse(
            DividendEventExtractor.ExtractedDividendEvent event) {
        LocalDate eventDate = event.eventDate();
        String sourceSection = blankToNull(event.sourceSection());
        return DividendEventsResponse.DividendEvent.builder()
                .id(String.join(":",
                        firstNonBlank(event.accessionNumber(), "unknown"),
                        Objects.toString(event.eventType(), "event"),
                        firstNonBlank(sourceSection, "document"),
                        Objects.toString(eventDate, "undated")))
                .eventType(event.eventType())
                .formType(event.formType())
                .accessionNumber(event.accessionNumber())
                .filedDate(event.filedDate())
                .declarationDate(event.declarationDate())
                .recordDate(event.recordDate())
                .payableDate(event.payableDate())
                .amountPerShare(event.amountPerShare() != null ? event.amountPerShare().doubleValue() : null)
                .dividendType(event.dividendType())
                .confidence(event.confidence())
                .extractionMethod(event.extractionMethod())
                .sourceSection(sourceSection)
                .textSnippet(blankToNull(event.textSnippet()))
                .policyLanguage(blankToNull(event.policyLanguage()))
                .url(blankToNull(event.url()))
                .build();
    }

    private DividendEvidenceResponse.EvidenceHighlight toEvidenceHighlight(
            DividendEventExtractor.ExtractedDividendEvent event) {
        DividendEventsResponse.DividendEvent response = toDividendEventResponse(event);
        return DividendEvidenceResponse.EvidenceHighlight.builder()
                .id(response.getId())
                .eventType(response.getEventType())
                .confidence(response.getConfidence())
                .sourceSection(response.getSourceSection())
                .snippet(response.getTextSnippet())
                .policyLanguage(response.getPolicyLanguage())
                .build();
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

    private LocalDate resolveEventDate(DividendEventsResponse.DividendEvent event) {
        return firstNonNull(
                event.getDeclarationDate(),
                event.getPayableDate(),
                event.getRecordDate(),
                event.getFiledDate(),
                LocalDate.MIN);
    }

    private void putIfAbsent(
            List<DividendEventsResponse.DividendEvent> events,
            DividendEventsResponse.DividendEvent candidate) {
        boolean duplicate = events.stream().anyMatch(existing ->
                Objects.equals(existing.getEventType(), candidate.getEventType())
                        && Objects.equals(existing.getAccessionNumber(), candidate.getAccessionNumber())
                        && Objects.equals(existing.getSourceSection(), candidate.getSourceSection())
                        && Objects.equals(existing.getAmountPerShare(), candidate.getAmountPerShare())
                        && Objects.equals(existing.getDeclarationDate(), candidate.getDeclarationDate())
                        && Objects.equals(existing.getRecordDate(), candidate.getRecordDate())
                        && Objects.equals(existing.getPayableDate(), candidate.getPayableDate()));
        if (!duplicate) {
            events.add(candidate);
        }
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

    private boolean accessionMatches(String candidate, String requested) {
        String normalizedCandidate = normalizeAccession(candidate);
        String normalizedRequested = normalizeAccession(requested);
        return normalizedCandidate != null
                && normalizedRequested != null
                && normalizedCandidate.equals(normalizedRequested);
    }

    private String normalizeAccession(String accessionNumber) {
        String normalized = blankToNull(accessionNumber);
        if (normalized == null) {
            return null;
        }

        String digits = normalized.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? null : digits;
    }

    private List<Filling> loadRecentFilings(String cik) {
        LinkedHashMap<String, Filling> merged = new LinkedHashMap<>();

        try {
            fillingRepository.findByCik(
                            cik,
                            PageRequest.of(0, RECENT_FILING_LIMIT, Sort.by(Sort.Direction.DESC, "fillingDate")))
                    .getContent()
                    .forEach(filing -> putIfAbsent(merged, filing));
        } catch (Exception e) {
            log.debug("Could not load local filings for {}", cik, e);
        }

        try {
            String submissionsJson = secApiClient.fetchSubmissions(cik);
            secResponseParser.toFillings(secResponseParser.parseSubmissionResponse(submissionsJson))
                    .forEach(filing -> putIfAbsent(merged, filing));
        } catch (Exception e) {
            log.debug("Could not load SEC submissions fallback for {}", cik, e);
        }

        return merged.values().stream()
                .sorted(Comparator.comparing(this::resolveSortableFilingDate, Comparator.reverseOrder()))
                .toList();
    }

    private void putIfAbsent(Map<String, Filling> merged, Filling candidate) {
        if (candidate == null) {
            return;
        }

        String accessionNumber = blankToNull(candidate.getAccessionNumber());
        String key = accessionNumber != null
                ? accessionNumber
                : "%s|%s|%s".formatted(
                        blankToNull(candidate.getCik()),
                        normalizeFormType(candidate.getFormType() != null ? candidate.getFormType().getNumber() : null),
                        resolveSortableFilingDate(candidate));
        merged.putIfAbsent(key, candidate);
    }

    private List<Filling> selectFilings(List<Filling> filings, Set<String> allowedForms, boolean xbrlOnly, int limit) {
        return filings.stream()
                .filter(filing -> allowedForms.contains(normalizeFormType(filing.getFormType() != null
                        ? filing.getFormType().getNumber()
                        : null)))
                .filter(filing -> !xbrlOnly || hasUsableXbrlDocument(filing))
                .limit(limit)
                .toList();
    }

    private Filling selectLatestCurrentReport(List<Filling> filings) {
        return filings.stream()
                .filter(filing -> CURRENT_REPORT_FORMS.contains(normalizeFormType(filing.getFormType() != null
                        ? filing.getFormType().getNumber()
                        : null)))
                .findFirst()
                .orElse(null);
    }

    private List<AnalyzedFilingData> analyzeFilings(List<Filling> filings) {
        List<AnalyzedFilingData> analyses = new ArrayList<>();
        for (Filling filing : filings) {
            analyzeFiling(filing).ifPresent(analyses::add);
        }
        analyses.sort(Comparator.comparing(this::resolveSortableAnalysisDate, Comparator.reverseOrder()));
        return analyses;
    }

    private Optional<AnalyzedFilingData> analyzeFiling(Filling filing) {
        String filingUrl = resolveFilingUrl(filing);
        if (filingUrl == null) {
            return Optional.empty();
        }

        try {
            XbrlInstance instance = xbrlService.parseFromUrl(filingUrl).block(XBRL_TIMEOUT);
            if (instance == null) {
                return Optional.empty();
            }

            ConceptStandardizer.StandardizedData standardized = xbrlService.standardize(instance);
            return Optional.of(new AnalyzedFilingData(
                    filing,
                    filingUrl,
                    standardized != null ? standardized.getLatestValues() : Map.of(),
                    xbrlService.getKeyFinancials(instance),
                    xbrlService.extractSecMetadata(instance)));
        } catch (Exception e) {
            log.debug("Could not analyze filing {} for dividend overview",
                    filing != null ? filing.getAccessionNumber() : null,
                    e);
            return Optional.empty();
        }
    }

    private List<DividendFactPoint> loadDividendFactSeries(String cik) {
        try {
            SecCompanyFactsResponse companyFacts = secResponseParser.parseCompanyFactsResponse(secApiClient.fetchCompanyFacts(cik));
            return extractAnnualDividendSeries(companyFacts);
        } catch (Exception e) {
            log.debug("Could not load dividend company facts for {}", cik, e);
            return List.of();
        }
    }

    private List<DividendFactPoint> extractAnnualDividendSeries(SecCompanyFactsResponse companyFacts) {
        if (companyFacts == null || companyFacts.getFacts() == null || companyFacts.getFacts().isEmpty()) {
            return List.of();
        }

        TreeAccumulator<DividendFactPoint> series = new TreeAccumulator<>();
        List<TagPriority> priorities = List.of(
                new TagPriority("us-gaap", "CommonStockDividendsPerShareDeclared", 0),
                new TagPriority("us-gaap", "CommonStockDividendsPerShareCashPaid", 1),
                new TagPriority("us-gaap", "CommonStockDividendsPerShareDeclaredAndPaid", 2));

        for (TagPriority priority : priorities) {
            SecCompanyFactsResponse.ConceptFacts conceptFacts = getConceptFacts(companyFacts, priority.taxonomy(), priority.tag());
            if (conceptFacts == null || conceptFacts.getUnits() == null) {
                continue;
            }

            conceptFacts.getUnits().values().forEach(entries -> {
                if (entries == null) {
                    return;
                }

                for (SecCompanyFactsResponse.FactEntry entry : entries) {
                    if (!isAnnualDividendFact(entry)) {
                        continue;
                    }

                    LocalDate periodEnd = parseDate(entry.getEnd());
                    Double value = toDouble(entry.getVal());
                    if (periodEnd == null || value == null) {
                        continue;
                    }

                    DividendFactPoint candidate = new DividendFactPoint(
                            periodEnd,
                            parseDate(entry.getFiled()),
                            blankToNull(entry.getAccn()),
                            dividendMetricsService.magnitude(value),
                            priority.priority());
                    series.put(periodEnd, candidate, this::isBetterFactCandidate);
                }
            });
        }

        return series.values();
    }

    private SecCompanyFactsResponse.ConceptFacts getConceptFacts(
            SecCompanyFactsResponse companyFacts,
            String taxonomy,
            String tag) {
        Map<String, SecCompanyFactsResponse.ConceptFacts> taxonomyFacts = companyFacts.getFacts().get(taxonomy);
        return taxonomyFacts != null ? taxonomyFacts.get(tag) : null;
    }

    private boolean isAnnualDividendFact(SecCompanyFactsResponse.FactEntry entry) {
        if (entry == null || entry.getVal() == null) {
            return false;
        }

        String form = normalizeFormType(entry.getForm());
        if (!ANNUAL_FORMS.contains(form)) {
            return false;
        }

        String fiscalPeriod = blankToNull(entry.getFp());
        if (fiscalPeriod != null) {
            return "FY".equalsIgnoreCase(fiscalPeriod);
        }

        LocalDate start = parseDate(entry.getStart());
        LocalDate end = parseDate(entry.getEnd());
        return start != null && end != null && ChronoUnit.DAYS.between(start, end) >= 300;
    }

    private boolean isBetterFactCandidate(DividendFactPoint candidate, DividendFactPoint current) {
        if (current == null) {
            return true;
        }

        if (candidate.sourcePriority() != current.sourcePriority()) {
            return candidate.sourcePriority() < current.sourcePriority();
        }

        LocalDate candidateFiled = candidate.filedDate() != null ? candidate.filedDate() : LocalDate.MIN;
        LocalDate currentFiled = current.filedDate() != null ? current.filedDate() : LocalDate.MIN;
        return candidateFiled.isAfter(currentFiled);
    }

    private List<DividendOverviewResponse.TrendPoint> buildTrend(
            List<DividendFactPoint> dividendFacts,
            List<AnalyzedFilingData> annualAnalyses) {
        Map<LocalDate, TrendAccumulator> byPeriodEnd = new LinkedHashMap<>();

        for (DividendFactPoint fact : dividendFacts) {
            if (fact.periodEnd() == null) {
                continue;
            }
            TrendAccumulator accumulator = byPeriodEnd.computeIfAbsent(fact.periodEnd(), TrendAccumulator::new);
            accumulator.dividendsPerShare = fact.dividendsPerShare();
            accumulator.filingDate = fact.filedDate();
            accumulator.accessionNumber = firstNonBlank(accumulator.accessionNumber, fact.accessionNumber());
        }

        for (AnalyzedFilingData annual : annualAnalyses) {
            LocalDate periodEnd = resolvePeriodEnd(annual);
            if (periodEnd == null) {
                continue;
            }

            TrendAccumulator accumulator = byPeriodEnd.computeIfAbsent(periodEnd, TrendAccumulator::new);
            accumulator.filingDate = firstNonNull(accumulator.filingDate, toLocalDate(annual.filing().getFillingDate()));
            accumulator.accessionNumber = firstNonBlank(accumulator.accessionNumber, annual.filing().getAccessionNumber());
            accumulator.earningsPerShare = firstNonNull(accumulator.earningsPerShare,
                    getMetric(annual, List.of("EarningsPerShareDiluted"), List.of("EarningsPerShareDiluted")));
            if (accumulator.dividendsPerShare == null) {
                accumulator.dividendsPerShare = getDividendsPerShare(annual);
            }
        }

        return byPeriodEnd.values().stream()
                .map(TrendAccumulator::toResponse)
                .filter(point -> point.getDividendsPerShare() != null || point.getEarningsPerShare() != null)
                .sorted(Comparator.comparing(DividendOverviewResponse.TrendPoint::getPeriodEnd))
                .toList();
    }

    private Double getDividendsPerShare(AnalyzedFilingData filing) {
        Double directValue = getMetric(filing, DIRECT_DPS_STANDARD_KEYS, DIRECT_DPS_FINANCIAL_KEYS);
        if (directValue != null) {
            return directValue;
        }

        Double dividendsPaid = dividendMetricsService.magnitude(getMetric(filing,
                List.of("DividendsPaid", "PaymentsOfDividendsCommonStock"),
                List.of("PaymentsOfDividendsCommonStock", "DividendsCommonStockCash", "PaymentsOfOrdinaryDividends")));
        return dividendMetricsService.safeDivide(dividendsPaid, getSharesOutstanding(filing));
    }

    private Double getSharesOutstanding(AnalyzedFilingData filing) {
        return firstNonNull(
                getMetric(filing, List.of("SharesOutstanding"), List.of("CommonStockSharesOutstanding")),
                filing != null && filing.secMetadata() != null && filing.secMetadata().getSharesOutstanding() != null
                        ? filing.secMetadata().getSharesOutstanding().doubleValue()
                        : null);
    }

    private Double getMetric(AnalyzedFilingData filing, List<String> standardizedKeys, List<String> financialKeys) {
        if (filing == null) {
            return null;
        }

        for (String key : standardizedKeys) {
            BigDecimal value = filing.standardizedValues().get(key);
            if (value != null) {
                return value.doubleValue();
            }
        }

        for (String key : financialKeys) {
            BigDecimal value = filing.keyFinancials().get(key);
            if (value != null) {
                return value.doubleValue();
            }
        }

        return null;
    }

    private DividendOverviewResponse.CompanySummary buildCompanySummary(
            CompanyResponse company,
            String ticker,
            List<Filling> filings) {
        LocalDate lastFilingDate = filings.stream()
                .map(this::resolveSortableFilingDate)
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
                .latestCurrentReport(toSourceFiling(latestCurrentReport, resolveFilingUrl(latestCurrentReport)))
                .build();
    }

    private DividendOverviewResponse.SourceFiling toSourceFiling(Filling filing, String url) {
        if (filing == null) {
            return null;
        }

        return DividendOverviewResponse.SourceFiling.builder()
                .formType(filing.getFormType() != null ? filing.getFormType().getNumber() : null)
                .accessionNumber(filing.getAccessionNumber())
                .filingDate(toLocalDate(filing.getFillingDate()))
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
                : hasDirectDividendsPerShare(latestAnnual)
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

    private boolean hasDirectDividendsPerShare(AnalyzedFilingData filing) {
        if (filing == null) {
            return false;
        }

        return DIRECT_DPS_STANDARD_KEYS.stream().anyMatch(key -> filing.standardizedValues().containsKey(key))
                || DIRECT_DPS_FINANCIAL_KEYS.stream().anyMatch(key -> filing.keyFinancials().containsKey(key));
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

    private String resolveFilingUrl(Filling filing) {
        if (filing == null) {
            return null;
        }

        String cik = blankToNull(filing.getCik());
        String accessionNumber = blankToNull(filing.getAccessionNumber());
        String primaryDocument = blankToNull(filing.getPrimaryDocument());
        if (cik != null && accessionNumber != null && primaryDocument != null) {
            String generatedUrl = secApiConfig.getFilingUrl(cik, accessionNumber, primaryDocument);
            if (isAllowedUrl(generatedUrl)) {
                return generatedUrl;
            }
        }

        String rawUrl = blankToNull(filing.getUrl());
        if (rawUrl == null) {
            return null;
        }

        String resolvedUrl = rawUrl.contains("://")
                ? rawUrl
                : secApiConfig.getArchiveUrl(rawUrl);
        return isAllowedUrl(resolvedUrl) ? resolvedUrl : null;
    }

    private boolean isAllowedUrl(String url) {
        if (url == null) {
            return false;
        }

        try {
            urlAllowlistValidator.validateXbrlUrl(url);
            return true;
        } catch (IllegalArgumentException e) {
            log.debug("Skipping disallowed filing URL {}", url);
            return false;
        }
    }

    private boolean hasUsableXbrlDocument(Filling candidate) {
        return candidate != null
                && (candidate.isXBRL() || candidate.isInlineXBRL())
                && resolveFilingUrl(candidate) != null;
    }

    private LocalDate resolvePeriodEnd(AnalyzedFilingData filing) {
        if (filing == null) {
            return null;
        }

        if (filing.secMetadata() != null && filing.secMetadata().getDocumentPeriodEndDate() != null) {
            return filing.secMetadata().getDocumentPeriodEndDate();
        }
        return firstNonNull(toLocalDate(filing.filing().getReportDate()), toLocalDate(filing.filing().getFillingDate()));
    }

    private LocalDate resolveSortableFilingDate(Filling filing) {
        if (filing == null) {
            return LocalDate.MIN;
        }

        return firstNonNull(toLocalDate(filing.getFillingDate()), toLocalDate(filing.getReportDate()), LocalDate.MIN);
    }

    private LocalDate resolveSortableAnalysisDate(AnalyzedFilingData filing) {
        return firstNonNull(resolvePeriodEnd(filing), toLocalDate(filing.filing().getFillingDate()), LocalDate.MIN);
    }

    private LocalDate toLocalDate(Date date) {
        if (date == null) {
            return null;
        }

        return date.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
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

    private String normalizeFormType(String formType) {
        String normalized = blankToNull(formType);
        return normalized != null ? normalized.toUpperCase(Locale.ROOT) : null;
    }

    private LocalDate parseDate(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            return null;
        }

        try {
            return LocalDate.parse(normalized);
        } catch (Exception e) {
            return null;
        }
    }

    private Double toDouble(BigDecimal value) {
        return value != null ? value.doubleValue() : null;
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

    private record TagPriority(String taxonomy, String tag, int priority) {
    }

    private record DividendFactPoint(
            LocalDate periodEnd,
            LocalDate filedDate,
            String accessionNumber,
            Double dividendsPerShare,
            int sourcePriority) {
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

    private record AnalyzedFilingData(
            Filling filing,
            String filingUrl,
            Map<String, BigDecimal> standardizedValues,
            Map<String, BigDecimal> keyFinancials,
            SecFilingExtractor.SecFilingMetadata secMetadata) {
    }

    private static final class TrendAccumulator {
        private final LocalDate periodEnd;
        private LocalDate filingDate;
        private String accessionNumber;
        private Double dividendsPerShare;
        private Double earningsPerShare;

        private TrendAccumulator(LocalDate periodEnd) {
            this.periodEnd = periodEnd;
        }

        private DividendOverviewResponse.TrendPoint toResponse() {
            return DividendOverviewResponse.TrendPoint.builder()
                    .periodEnd(periodEnd)
                    .filingDate(filingDate)
                    .accessionNumber(accessionNumber)
                    .dividendsPerShare(dividendsPerShare)
                    .earningsPerShare(earningsPerShare)
                    .build();
        }
    }

    private static final class TreeAccumulator<T> {
        private final Map<LocalDate, T> values = new LinkedHashMap<>();

        private void put(LocalDate key, T candidate, java.util.function.BiPredicate<T, T> chooser) {
            T current = values.get(key);
            if (current == null || chooser.test(candidate, current)) {
                values.put(key, candidate);
            }
        }

        private List<T> values() {
            return values.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(Map.Entry::getValue)
                    .toList();
        }
    }
}
