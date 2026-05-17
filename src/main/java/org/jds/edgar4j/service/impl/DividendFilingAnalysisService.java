package org.jds.edgar4j.service.impl;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.jds.edgar4j.dto.response.DividendOverviewResponse;
import org.jds.edgar4j.integration.SecApiClient;
import org.jds.edgar4j.integration.SecApiConfig;
import org.jds.edgar4j.integration.SecResponseParser;
import org.jds.edgar4j.integration.model.SecCompanyFactsResponse;
import org.jds.edgar4j.model.Filling;
import org.jds.edgar4j.model.NormalizedXbrlFact;
import org.jds.edgar4j.port.FillingDataPort;
import org.jds.edgar4j.port.NormalizedXbrlFactDataPort;
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
public class DividendFilingAnalysisService {

    private static final Duration XBRL_TIMEOUT = Duration.ofSeconds(45);
    private static final int RECENT_FILING_LIMIT = 80;
    private static final Set<String> ANNUAL_FORMS = Set.of(
            "10-K", "10-K/A", "20-F", "20-F/A", "40-F", "40-F/A");
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
    private static final List<StoredConceptPriority> STORED_DPS_STANDARD_KEYS = List.of(
            new StoredConceptPriority("DividendsPerShare", 0),
            new StoredConceptPriority("DividendsPerShareCashPaid", 1));

    private final FillingDataPort fillingRepository;
    private final SecApiClient secApiClient;
    private final SecResponseParser secResponseParser;
    private final SecApiConfig secApiConfig;
    private final XbrlService xbrlService;
    private final UrlAllowlistValidator urlAllowlistValidator;
    private final DividendMetricsService dividendMetricsService;
    private final NormalizedXbrlFactDataPort normalizedXbrlFactDataPort;

    public List<Filling> loadRecentFilings(String cik) {
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

    public List<Filling> selectFilings(List<Filling> filings, Set<String> allowedForms, boolean xbrlOnly, int limit) {
        return filings.stream()
                .filter(filing -> allowedForms.contains(normalizeFormType(filing.getFormType() != null
                        ? filing.getFormType().getNumber()
                        : null)))
                .filter(filing -> !xbrlOnly || hasUsableXbrlDocument(filing))
                .limit(limit)
                .toList();
    }

    public Filling selectLatestCurrentReport(List<Filling> filings, Set<String> currentReportForms) {
        return filings.stream()
                .filter(filing -> currentReportForms.contains(normalizeFormType(filing.getFormType() != null
                        ? filing.getFormType().getNumber()
                        : null)))
                .findFirst()
                .orElse(null);
    }

    public List<AnalyzedFilingData> analyzeFilings(List<Filling> filings) {
        List<AnalyzedFilingData> analyses = new ArrayList<>();
        for (Filling filing : filings) {
            analyzeFiling(filing).ifPresent(analyses::add);
        }
        analyses.sort(Comparator.comparing(this::resolveSortableAnalysisDate, Comparator.reverseOrder()));
        return analyses;
    }

    public List<DividendFactPoint> loadDividendFactSeries(String cik) {
        List<DividendFactPoint> storedSeries = loadStoredDividendFactSeries(cik);
        if (!storedSeries.isEmpty()) {
            return storedSeries;
        }

        try {
            SecCompanyFactsResponse companyFacts = secResponseParser.parseCompanyFactsResponse(secApiClient.fetchCompanyFacts(cik));
            return extractAnnualDividendSeries(companyFacts);
        } catch (Exception e) {
            log.debug("Could not load dividend company facts for {}", cik, e);
            return List.of();
        }
    }

    public List<DividendOverviewResponse.TrendPoint> buildTrend(
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

    public Double getMetric(AnalyzedFilingData filing, List<String> standardizedKeys, List<String> financialKeys) {
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

    public Double getDividendsPerShare(AnalyzedFilingData filing) {
        Double directValue = getMetric(filing, DIRECT_DPS_STANDARD_KEYS, DIRECT_DPS_FINANCIAL_KEYS);
        if (directValue != null) {
            return directValue;
        }

        Double dividendsPaid = dividendMetricsService.magnitude(getMetric(filing,
                List.of("DividendsPaid", "PaymentsOfDividendsCommonStock"),
                List.of("PaymentsOfDividendsCommonStock", "DividendsCommonStockCash", "PaymentsOfOrdinaryDividends")));
        return dividendMetricsService.safeDivide(dividendsPaid, getSharesOutstanding(filing));
    }

    public boolean hasDirectDividendsPerShare(AnalyzedFilingData filing) {
        if (filing == null) {
            return false;
        }

        return DIRECT_DPS_STANDARD_KEYS.stream().anyMatch(key -> filing.standardizedValues().containsKey(key))
                || DIRECT_DPS_FINANCIAL_KEYS.stream().anyMatch(key -> filing.keyFinancials().containsKey(key));
    }

    public String resolveFilingUrl(Filling filing) {
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

    public LocalDate resolvePeriodEnd(AnalyzedFilingData filing) {
        if (filing == null) {
            return null;
        }

        if (filing.secMetadata() != null && filing.secMetadata().getDocumentPeriodEndDate() != null) {
            return filing.secMetadata().getDocumentPeriodEndDate();
        }
        return firstNonNull(toLocalDate(filing.filing().getReportDate()), toLocalDate(filing.filing().getFillingDate()));
    }

    public LocalDate resolveSortableFilingDate(Filling filing) {
        if (filing == null) {
            return LocalDate.MIN;
        }

        return firstNonNull(toLocalDate(filing.getFillingDate()), toLocalDate(filing.getReportDate()), LocalDate.MIN);
    }

    public LocalDate resolveSortableAnalysisDate(AnalyzedFilingData filing) {
        return firstNonNull(resolvePeriodEnd(filing), toLocalDate(filing.filing().getFillingDate()), LocalDate.MIN);
    }

    public LocalDate toLocalDate(Date date) {
        if (date == null) {
            return null;
        }

        return date.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
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

    private List<DividendFactPoint> loadStoredDividendFactSeries(String cik) {
        String normalizedCik = blankToNull(cik);
        if (normalizedCik == null) {
            return List.of();
        }

        TreeAccumulator<DividendFactPoint> series = new TreeAccumulator<>();
        try {
            for (StoredConceptPriority priority : STORED_DPS_STANDARD_KEYS) {
                List<NormalizedXbrlFact> facts =
                        normalizedXbrlFactDataPort.findByCikAndStandardConceptAndCurrentBestTrueOrderByPeriodEndDesc(
                                normalizedCik,
                                priority.standardConcept());
                if (facts == null) {
                    continue;
                }

                for (NormalizedXbrlFact fact : facts) {
                    if (!isAnnualNormalizedDividendFact(fact)) {
                        continue;
                    }

                    Double value = toDouble(fact.getValue());
                    if (fact.getPeriodEnd() == null || value == null) {
                        continue;
                    }

                    DividendFactPoint candidate = new DividendFactPoint(
                            fact.getPeriodEnd(),
                            fact.getFiledDate(),
                            blankToNull(fact.getAccession()),
                            dividendMetricsService.magnitude(value),
                            priority.priority());
                    series.put(fact.getPeriodEnd(), candidate, this::isBetterFactCandidate);
                }
            }
        } catch (Exception e) {
            log.debug("Could not load normalized dividend facts for {}", normalizedCik, e);
            return List.of();
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

    private boolean isAnnualNormalizedDividendFact(NormalizedXbrlFact fact) {
        if (fact == null || fact.getValue() == null) {
            return false;
        }

        String form = normalizeFormType(fact.getForm());
        if (!ANNUAL_FORMS.contains(form)) {
            return false;
        }

        String fiscalPeriod = blankToNull(fact.getFiscalPeriod());
        if (fiscalPeriod != null) {
            return "FY".equalsIgnoreCase(fiscalPeriod);
        }

        LocalDate start = fact.getPeriodStart();
        LocalDate end = fact.getPeriodEnd();
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

    private boolean hasUsableXbrlDocument(Filling candidate) {
        return candidate != null
                && (candidate.isXBRL() || candidate.isInlineXBRL())
                && resolveFilingUrl(candidate) != null;
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

    private Double getSharesOutstanding(AnalyzedFilingData filing) {
        return firstNonNull(
                getMetric(filing, List.of("SharesOutstanding"), List.of("CommonStockSharesOutstanding")),
                filing != null && filing.secMetadata() != null && filing.secMetadata().getSharesOutstanding() != null
                        ? filing.secMetadata().getSharesOutstanding().doubleValue()
                        : null);
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

    private record StoredConceptPriority(String standardConcept, int priority) {
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

    public record DividendFactPoint(
            LocalDate periodEnd,
            LocalDate filedDate,
            String accessionNumber,
            Double dividendsPerShare,
            int sourcePriority) {
    }

    public record AnalyzedFilingData(
            Filling filing,
            String filingUrl,
            Map<String, BigDecimal> standardizedValues,
            Map<String, BigDecimal> keyFinancials,
            SecFilingExtractor.SecFilingMetadata secMetadata) {
    }
}
