package org.jds.edgar4j.service.impl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jds.edgar4j.dto.request.DividendAlertResolutionRequest;
import org.jds.edgar4j.dto.request.DividendScreenRequest;
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
import org.jds.edgar4j.service.dividend.DividendAnalysisSnapshotService;
import org.jds.edgar4j.service.dividend.DividendAlertResolutionService;
import org.jds.edgar4j.service.dividend.DividendAlertsService;
import org.jds.edgar4j.service.dividend.DividendEvidenceService;
import org.jds.edgar4j.service.dividend.DividendScreeningService;
import org.jds.edgar4j.service.impl.DividendFilingAnalysisService.AnalyzedFilingData;
import org.jds.edgar4j.service.impl.DividendFilingAnalysisService.DividendFactPoint;
import org.jds.edgar4j.service.impl.DividendHistoryAnalysisService.HistoryRowData;
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
    private final DividendCompanyContextService dividendCompanyContextService;
    private final DividendFilingAnalysisService dividendFilingAnalysisService;
    private final DividendHistoryAnalysisService dividendHistoryAnalysisService;
    private final DividendOverviewComputationService dividendOverviewComputationService;
    private final DividendMetricCatalogService dividendMetricCatalogService;
    private final DividendPeerAnalysisService dividendPeerAnalysisService;
    private final DividendScreeningService dividendScreeningService;
    private final DividendEvidenceService dividendEvidenceService;
    private final DividendAlertResolutionService dividendAlertResolutionService;
    private final DividendAlertsService dividendAlertsService;
    private final DividendAnalysisSnapshotService dividendAnalysisSnapshotService;

    @Override
    public DividendOverviewResponse getOverview(String tickerOrCik) {
        DividendAnalysisContext context = analyze(tickerOrCik, true);
        DividendOverviewResponse overview = buildOverview(context);
        dividendAnalysisSnapshotService.saveOverview(overview);
        return overview;
    }

    @Override
    public DividendHistoryResponse getHistory(String tickerOrCik, List<String> metrics, String period, int years) {
        if (years <= 0) {
            throw new IllegalArgumentException("years must be greater than 0");
        }

        String normalizedPeriod = dividendMetricCatalogService.normalizeHistoryPeriod(period);
        List<String> requestedMetrics = dividendMetricCatalogService.normalizeHistoryMetrics(metrics);
        DividendAnalysisContext context = analyze(tickerOrCik, true);
        List<HistoryRowData> rows = dividendHistoryAnalysisService.limitHistoryRows(context.historyRows(), years);

        List<DividendHistoryResponse.MetricSeries> series = requestedMetrics.stream()
                .map(metric -> dividendHistoryAnalysisService.buildMetricSeries(
                        metric, rows, dividendMetricCatalogService.historyMetricDefinitions()))
                .toList();
        List<DividendHistoryResponse.HistoryRow> responseRows = rows.stream()
                .map(row -> dividendHistoryAnalysisService.toHistoryRow(row, requestedMetrics))
                .toList();

        DividendHistoryResponse history = DividendHistoryResponse.builder()
                .company(context.companySummary())
                .period(normalizedPeriod)
                .yearsRequested(years)
                .metrics(requestedMetrics)
                .series(series)
                .rows(responseRows)
                .warnings(context.warnings())
                .build();
        dividendAnalysisSnapshotService.saveHistory(history);
        return history;
    }

    @Override
    public DividendAlertsResponse getAlerts(String tickerOrCik, boolean activeOnly) {
        DividendAnalysisContext context = analyze(tickerOrCik, true);
        List<DividendAlertsResponse.AlertEvent> historicalAlerts = dividendHistoryAnalysisService.buildHistoricalAlerts(
                context.historyRows(),
                context.alerts(),
                context.latestBalance(),
                context.latestAnnual(),
                context.companySummary().getLastFilingDate(),
                false);
        List<DividendAlertsResponse.AlertEvent> decoratedHistoricalAlerts =
                dividendAlertResolutionService.applyResolutionState(
                        context.companySummary().getCik(),
                        historicalAlerts,
                        activeOnly);

        DividendAlertsResponse alerts = DividendAlertsResponse.builder()
                .company(context.companySummary())
                .activeOnly(activeOnly)
                .activeAlerts(context.alerts())
                .historicalAlerts(decoratedHistoricalAlerts)
                .warnings(context.warnings())
                .build();
        if (!activeOnly) {
            dividendAnalysisSnapshotService.saveAlerts(alerts);
        }
        return alerts;
    }

    @Override
    public DividendAlertsResponse resolveAlert(
            String tickerOrCik,
            String alertId,
            DividendAlertResolutionRequest request) {
        DividendAnalysisContext context = analyze(tickerOrCik, false);
        DividendAlertsResponse.AlertEvent event = resolveTargetAlertEvent(context, alertId, request)
                .orElseThrow(() -> new ResourceNotFoundException("Dividend alert event", "alertId", alertId));
        dividendAlertResolutionService.resolve(
                context.companySummary().getCik(),
                context.companySummary().getTicker(),
                event,
                request != null ? request : DividendAlertResolutionRequest.builder().build());
        return getAlerts(tickerOrCik, false);
    }

    @Override
    public DividendAlertsResponse reopenAlert(
            String tickerOrCik,
            String alertId,
            DividendAlertResolutionRequest request) {
        DividendAnalysisContext context = analyze(tickerOrCik, false);
        DividendAlertsResponse.AlertEvent event = resolveTargetAlertEvent(context, alertId, request)
                .orElseThrow(() -> new ResourceNotFoundException("Dividend alert event", "alertId", alertId));
        dividendAlertResolutionService.reopen(context.companySummary().getCik(), event);
        return getAlerts(tickerOrCik, false);
    }

    @Override
    public DividendEventsResponse getEvents(String tickerOrCik, LocalDate since) {
        CompanyResponse company = dividendCompanyContextService.resolveCompany(tickerOrCik)
                .orElseThrow(() -> new ResourceNotFoundException("Company", "tickerOrCik", tickerOrCik));

        String cik = dividendCompanyContextService.normalizeCik(company.getCik())
                .orElseThrow(() -> new IllegalArgumentException("Company CIK is unavailable"));
        String ticker = dividendCompanyContextService.normalizeTicker(company.getTicker()).orElseGet(() ->
                companyService.getTickerByCik(cik).orElse(null));

        List<Filling> filings = dividendFilingAnalysisService.loadRecentFilings(cik);
        List<Filling> currentReports = dividendFilingAnalysisService.selectFilings(filings, CURRENT_REPORT_FORMS, false, 12);
        List<Filling> annualReports = dividendFilingAnalysisService.selectFilings(filings, ANNUAL_FORMS, false, 2);

        DividendEventsResponse events = dividendEvidenceService.buildEvents(
                buildCompanySummary(company, ticker, filings),
                ticker,
                currentReports,
                annualReports,
                filings,
                since);
        if (since == null) {
            dividendAnalysisSnapshotService.saveEvents(events);
        }
        return events;
    }

    @Override
    public DividendEvidenceResponse getEvidence(String tickerOrCik, String accessionNumber) {
        CompanyResponse company = dividendCompanyContextService.resolveCompany(tickerOrCik)
                .orElseThrow(() -> new ResourceNotFoundException("Company", "tickerOrCik", tickerOrCik));

        String cik = dividendCompanyContextService.normalizeCik(company.getCik())
                .orElseThrow(() -> new IllegalArgumentException("Company CIK is unavailable"));
        String ticker = dividendCompanyContextService.normalizeTicker(company.getTicker()).orElseGet(() ->
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
        List<String> identifiers = dividendCompanyContextService.normalizeIdentifiers(tickersOrCiks);
        if (identifiers.isEmpty()) {
            throw new IllegalArgumentException("At least one ticker or CIK is required for comparison");
        }

        List<String> requestedMetrics = dividendMetricCatalogService.normalizeComparisonMetrics(metrics);
        List<DividendComparisonResponse.ComparisonRow> rows = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (String identifier : identifiers) {
            try {
                DividendAnalysisContext context = analyze(identifier, true);
                rows.add(dividendPeerAnalysisService.buildComparisonRow(context, requestedMetrics));
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
        List<String> identifiers = dividendCompanyContextService.resolveScreenIdentifiers(normalizedRequest, candidateLimit, warnings);
        if (identifiers.isEmpty()) {
            throw new ResourceNotFoundException("Dividend screen candidates", "request", normalizedRequest);
        }

        List<String> requestedMetrics = dividendMetricCatalogService.resolveScreenMetrics(normalizedRequest);
        Map<String, String> metricFormatHints = dividendMetricCatalogService.metricFormatHints();
        List<DividendScreenResponse.ScreenResult> results = new ArrayList<>();

        for (String identifier : identifiers) {
            try {
                DividendAnalysisContext context = analyze(identifier, true);
                DividendScreeningService.ScreeningCandidate screeningCandidate =
                        dividendPeerAnalysisService.toScreeningCandidate(context);
                if (dividendScreeningService.matchesScreenFilters(
                        screeningCandidate,
                        normalizedRequest.getFilters(),
                        dividendMetricCatalogService.metricIds(),
                        metricFormatHints)) {
                    results.add(dividendPeerAnalysisService.buildScreenResult(context, requestedMetrics));
                }
            } catch (RuntimeException e) {
                warnings.add("Could not analyze " + identifier + ": " + e.getMessage());
            }
        }

        Comparator<DividendScreenResponse.ScreenResult> comparator = dividendScreeningService.buildScreenComparator(
                dividendCompanyContextService.blankToNull(normalizedRequest.getSort()),
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

    private DividendAnalysisContext analyze(String tickerOrCik, boolean applyAlertResolutions) {
        CompanyResponse company = dividendCompanyContextService.resolveCompany(tickerOrCik)
                .orElseThrow(() -> new ResourceNotFoundException("Company", "tickerOrCik", tickerOrCik));

        String cik = dividendCompanyContextService.normalizeCik(company.getCik())
                .orElseThrow(() -> new IllegalArgumentException("Company CIK is unavailable"));
        String ticker = dividendCompanyContextService.normalizeTicker(company.getTicker()).orElseGet(() ->
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

        Optional<CompanyMarketData> marketData = Optional.ofNullable(ticker)
                .flatMap(companyMarketDataService::getStoredMarketData);
        Double referencePrice = marketData
                .map(CompanyMarketData::getCurrentPrice)
                .filter(price -> price != null && price > 0d)
                .orElse(null);
        Double marketCap = marketData
                .map(CompanyMarketData::getMarketCap)
                .filter(value -> value != null && value > 0d)
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
                referencePrice,
                marketCap,
                company.getSicDescription());
        DividendOverviewResponse.CompanySummary companySummary = buildCompanySummary(company, ticker, filings);
        DividendOverviewResponse.Evidence evidence = buildEvidence(latestAnnual, latestCurrentReport);
        List<HistoryRowData> historyRows = dividendHistoryAnalysisService.buildHistoryRows(annualAnalyses, trend);
        List<DividendOverviewResponse.Alert> activeAlerts = computed.alerts();
        int score = computed.score();
        DividendOverviewResponse.DividendRating rating = computed.rating();

        if (applyAlertResolutions) {
            List<DividendAlertsResponse.AlertEvent> rawActiveEvents = dividendHistoryAnalysisService.buildHistoricalAlerts(
                    historyRows,
                    computed.alerts(),
                    latestBalance,
                    latestAnnual,
                    companySummary.getLastFilingDate(),
                    true);
            activeAlerts = dividendAlertResolutionService.filterActiveAlerts(
                    companySummary.getCik(),
                    computed.alerts(),
                    rawActiveEvents);
            if (activeAlerts.size() != computed.alerts().size()) {
                score = dividendAlertsService.buildScore(computed.snapshot(), activeAlerts);
                rating = dividendAlertsService.toRating(score);
            }
        }

        return new DividendAnalysisContext(
                companySummary,
                computed.snapshot(),
                computed.confidence(),
                activeAlerts,
                computed.coverage(),
                computed.balance(),
                trend,
                evidence,
                referencePrice,
                computed.warnings(),
                latestAnnual,
                latestBalance,
                historyRows,
                score,
                rating);
    }

    private Optional<DividendAlertsResponse.AlertEvent> resolveTargetAlertEvent(
            DividendAnalysisContext context,
            String alertId,
            DividendAlertResolutionRequest request) {
        List<DividendAlertsResponse.AlertEvent> events = dividendHistoryAnalysisService.buildHistoricalAlerts(
                context.historyRows(),
                context.alerts(),
                context.latestBalance(),
                context.latestAnnual(),
                context.companySummary().getLastFilingDate(),
                false);
        LocalDate requestedPeriodEnd = request != null ? request.getPeriodEnd() : null;
        String requestedAccession = request != null ? request.getAccessionNumber() : null;

        return events.stream()
                .filter(event -> event.getId().equals(alertId))
                .filter(event -> requestedPeriodEnd == null || requestedPeriodEnd.equals(event.getPeriodEnd()))
                .filter(event -> requestedAccession == null || requestedAccession.equals(event.getAccessionNumber()))
                .sorted(Comparator
                        .comparing(DividendAlertsResponse.AlertEvent::isActive, Comparator.reverseOrder())
                        .thenComparing(DividendAlertsResponse.AlertEvent::getPeriodEnd,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(DividendAlertsResponse.AlertEvent::getFilingDate,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .findFirst();
    }

    private DividendOverviewResponse buildOverview(DividendAnalysisContext context) {
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

    private DividendOverviewResponse.CompanySummary buildCompanySummary(
            CompanyResponse company,
            String ticker,
            List<Filling> filings) {
        return dividendCompanyContextService.buildCompanySummary(company, ticker, filings);
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

}
