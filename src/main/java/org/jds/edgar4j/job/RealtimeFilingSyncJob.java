package org.jds.edgar4j.job;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;

import org.jds.edgar4j.integration.SecAccessDiagnostics;
import org.jds.edgar4j.integration.SecApiClient;
import org.jds.edgar4j.integration.model.EftsSearchResponse;
import org.jds.edgar4j.model.AppSettings;
import org.jds.edgar4j.model.Filling;
import org.jds.edgar4j.model.Form13DG;
import org.jds.edgar4j.model.Form13F;
import org.jds.edgar4j.model.Form20F;
import org.jds.edgar4j.model.Form3;
import org.jds.edgar4j.model.Form4;
import org.jds.edgar4j.model.Form5;
import org.jds.edgar4j.model.Form6K;
import org.jds.edgar4j.model.Form8K;
import org.jds.edgar4j.repository.AppSettingsRepository;
import org.jds.edgar4j.repository.FillingRepository;
import org.jds.edgar4j.service.DownloadSubmissionsService;
import org.jds.edgar4j.service.Form13DGService;
import org.jds.edgar4j.service.Form13FService;
import org.jds.edgar4j.service.Form20FService;
import org.jds.edgar4j.service.Form3Service;
import org.jds.edgar4j.service.Form4Service;
import org.jds.edgar4j.service.Form5Service;
import org.jds.edgar4j.service.Form6KService;
import org.jds.edgar4j.service.Form8KService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class RealtimeFilingSyncJob {

    private static final String DEFAULT_SETTINGS_ID = "default";
    private static final String DEFAULT_FORMS = "4";
    private static final int DEFAULT_LOOKBACK_HOURS = 1;
    private static final int DEFAULT_MAX_PAGES = 10;
    private static final int DEFAULT_PAGE_SIZE = 100;

    private final SecApiClient secApiClient;
    private final DownloadSubmissionsService downloadSubmissionsService;
    private final FillingRepository fillingRepository;
    private final AppSettingsRepository appSettingsRepository;
    private final Form3Service form3Service;
    private final Form4Service form4Service;
    private final Form5Service form5Service;
    private final Form6KService form6KService;
    private final Form8KService form8KService;
    private final Form13DGService form13DGService;
    private final Form13FService form13FService;
    private final Form20FService form20FService;
    private final ObjectMapper objectMapper;
    private final boolean enabledFallback;
    private final String formsFallback;
    private final int lookbackHoursFallback;
    private final int maxPagesFallback;
    private final int pageSizeFallback;
    private final int secBlockCooldownMinutes;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicInteger lastSyncNewCount = new AtomicInteger(0);
    private final AtomicInteger lastSyncTotalScanned = new AtomicInteger(0);
    private final AtomicReference<Instant> secBlockCooldownUntil = new AtomicReference<>();

    @Autowired
    public RealtimeFilingSyncJob(
            SecApiClient secApiClient,
            DownloadSubmissionsService downloadSubmissionsService,
            FillingRepository fillingRepository,
            AppSettingsRepository appSettingsRepository,
            Form3Service form3Service,
            Form4Service form4Service,
            Form5Service form5Service,
            Form6KService form6KService,
            Form8KService form8KService,
            Form13DGService form13DGService,
            Form13FService form13FService,
            Form20FService form20FService,
            ObjectMapper objectMapper,
            @Value("${edgar4j.jobs.realtime-filing-sync.enabled:true}") boolean enabledFallback,
            @Value("${edgar4j.jobs.realtime-filing-sync.forms:4}") String formsFallback,
            @Value("${edgar4j.jobs.realtime-filing-sync.lookback-hours:1}") int lookbackHoursFallback,
            @Value("${edgar4j.jobs.realtime-filing-sync.max-pages:10}") int maxPagesFallback,
            @Value("${edgar4j.jobs.realtime-filing-sync.page-size:100}") int pageSizeFallback,
            @Value("${edgar4j.jobs.realtime-filing-sync.sec-block-cooldown-minutes:10}") int secBlockCooldownMinutes) {
        this.secApiClient = secApiClient;
        this.downloadSubmissionsService = downloadSubmissionsService;
        this.fillingRepository = fillingRepository;
        this.appSettingsRepository = appSettingsRepository;
        this.form3Service = form3Service;
        this.form4Service = form4Service;
        this.form5Service = form5Service;
        this.form6KService = form6KService;
        this.form8KService = form8KService;
        this.form13DGService = form13DGService;
        this.form13FService = form13FService;
        this.form20FService = form20FService;
        this.objectMapper = objectMapper;
        this.enabledFallback = enabledFallback;
        this.formsFallback = formsFallback;
        this.lookbackHoursFallback = lookbackHoursFallback;
        this.maxPagesFallback = maxPagesFallback;
        this.pageSizeFallback = pageSizeFallback;
        this.secBlockCooldownMinutes = secBlockCooldownMinutes;
    }

    @Scheduled(cron = "${edgar4j.jobs.realtime-filing-sync.cron:0 */15 * * * *}")
    public void syncRecentFilings() {
        SyncConfig config = resolveSyncConfig();
        if (!config.enabled()) {
            log.debug("Realtime filing sync is disabled");
            return;
        }
        if (isSecBlockCooldownActive()) {
            log.warn(
                    "Skipping realtime filing sync until {} because SEC blocked this environment as an undeclared automated tool",
                    secBlockCooldownUntil.get());
            return;
        }

        if (!isRunning.compareAndSet(false, true)) {
            log.warn("Realtime filing sync is already running, skipping");
            return;
        }

        try {
            long startTime = System.currentTimeMillis();
            LocalDateTime syncStartedAt = currentDateTime();
            LocalDate endDate = syncStartedAt.toLocalDate();
            LocalDate startDate = syncStartedAt.minusHours(config.lookbackHours()).toLocalDate();

            log.info(
                    "Starting realtime filing sync at {} with forms={}, lookback={}h, maxPages={}, pageSize={}",
                    syncStartedAt,
                    config.forms(),
                    config.lookbackHours(),
                    config.maxPages(),
                    config.pageSize());

            int newFilings = 0;
            int totalScanned = 0;
            Set<String> seenAccessions = new LinkedHashSet<>();
            Set<String> refreshedSubmissionCiks = new LinkedHashSet<>();
            Map<String, FilingDirectory> filingDirectoryCache = new ConcurrentHashMap<>();

            for (int page = 0; page < config.maxPages(); page++) {
                List<EftsSearchResponse.Hit> hits = fetchPage(config, startDate, endDate, page);
                if (hits.isEmpty()) {
                    break;
                }

                totalScanned += hits.size();
                for (EftsSearchResponse.Hit hit : hits) {
                    String accessionNumber = extractAccessionNumber(hit);
                    if (accessionNumber == null || !seenAccessions.add(accessionNumber)) {
                        continue;
                    }

                    String cik = extractCik(hit.getSource());
                    if (cik == null) {
                        log.debug("Skipping EFTS hit without a resolvable CIK: {}", hit.getId());
                        continue;
                    }

                    try {
                        ResolvedFiling filing = resolveFiling(
                                accessionNumber,
                                cik,
                                hit.getSource(),
                                refreshedSubmissionCiks,
                                filingDirectoryCache);
                        if (filing != null && routeFiling(filing)) {
                            newFilings++;
                        }
                    } catch (Exception e) {
                        if (isSecAutomationBlock(e)) {
                            throw e;
                        }
                        log.debug("Failed to process filing {}: {}", accessionNumber, e.getMessage());
                    }
                }

                if (hits.size() < config.pageSize()) {
                    break;
                }
            }

            lastSyncNewCount.set(newFilings);
            lastSyncTotalScanned.set(totalScanned);
            log.info("Realtime filing sync completed: {} new of {} scanned in {} ms",
                    newFilings,
                    totalScanned,
                    System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            if (isSecAutomationBlock(e)) {
                Instant blockedUntil = activateSecBlockCooldown();
                log.warn(
                        "SEC blocked realtime filing sync as an undeclared automated tool. Cooling down until {}. {}",
                        blockedUntil,
                        summarizeException(e));
                return;
            }
            log.error("Error during realtime filing sync", e);
        } finally {
            isRunning.set(false);
        }
    }

    public void triggerSync() {
        log.info("Manual realtime filing sync triggered");
        syncRecentFilings();
    }

    public boolean isRunning() {
        return isRunning.get();
    }

    public int getLastSyncNewCount() {
        return lastSyncNewCount.get();
    }

    public int getLastSyncTotalScanned() {
        return lastSyncTotalScanned.get();
    }

    private SyncConfig resolveSyncConfig() {
        AppSettings settings = appSettingsRepository.findById(DEFAULT_SETTINGS_ID).orElse(null);
        boolean enabled = settings != null && settings.getRealtimeSyncEnabled() != null
                ? settings.getRealtimeSyncEnabled()
                : enabledFallback;
        String forms = normalizeForms(settings != null ? settings.getRealtimeSyncForms() : formsFallback);
        int lookbackHours = resolvePositive(settings != null ? settings.getRealtimeSyncLookbackHours() : null,
                lookbackHoursFallback > 0 ? lookbackHoursFallback : DEFAULT_LOOKBACK_HOURS);
        int maxPages = resolvePositive(settings != null ? settings.getRealtimeSyncMaxPages() : null,
                maxPagesFallback > 0 ? maxPagesFallback : DEFAULT_MAX_PAGES);
        int pageSize = resolvePositive(settings != null ? settings.getRealtimeSyncPageSize() : null,
                pageSizeFallback > 0 ? pageSizeFallback : DEFAULT_PAGE_SIZE);
        return new SyncConfig(enabled, forms, lookbackHours, maxPages, pageSize);
    }

    private List<EftsSearchResponse.Hit> fetchPage(SyncConfig config, LocalDate startDate, LocalDate endDate, int page) {
        String responseJson = secApiClient.fetchEftsSearch(
                config.forms(),
                startDate,
                endDate,
                page * config.pageSize(),
                config.pageSize());
        try {
            EftsSearchResponse response = objectMapper.readValue(responseJson, EftsSearchResponse.class);
            if (response.getHits() == null || response.getHits().getHits() == null) {
                return List.of();
            }
            return response.getHits().getHits();
        } catch (Exception e) {
            log.error("Failed to parse EFTS response page {}: {}", page, e.getMessage());
            return List.of();
        }
    }

    private ResolvedFiling resolveFiling(
            String accessionNumber,
            String cik,
            EftsSearchResponse.Source source,
            Set<String> refreshedSubmissionCiks,
            Map<String, FilingDirectory> filingDirectoryCache) {
        Filling filling = fillingRepository.findByAccessionNumber(accessionNumber).orElse(null);
        if (filling == null && refreshedSubmissionCiks.add(cik)) {
            refreshCompanySubmissions(cik);
            filling = fillingRepository.findByAccessionNumber(accessionNumber).orElse(null);
        }

        String formType = normalizeFormType(
                filling != null && filling.getFormType() != null ? filling.getFormType().getNumber() : null);
        if (formType == null) {
            formType = normalizeFormType(source != null ? source.getFormType() : null);
        }
        if (formType == null) {
            return null;
        }

        FilingDirectory filingDirectory = requiresDirectoryLookup(formType, filling)
                ? loadFilingDirectory(cik, accessionNumber, filingDirectoryCache)
                : FilingDirectory.EMPTY;

        String primaryDocument = resolvePrimaryDocument(formType, filling, filingDirectory);
        if (primaryDocument == null) {
            return null;
        }

        String infoTableDocument = is13F(formType)
                ? resolveInfoTableDocument(primaryDocument, filingDirectory)
                : null;
        if (is13F(formType) && infoTableDocument == null) {
            return null;
        }

        return new ResolvedFiling(cik, accessionNumber, formType, primaryDocument, infoTableDocument);
    }

    private void refreshCompanySubmissions(String cik) {
        try {
            downloadSubmissionsService.downloadSubmissions(cik);
        } catch (Exception e) {
            log.debug("Failed to refresh submissions for CIK {}: {}", cik, e.getMessage());
        }
    }

    private FilingDirectory loadFilingDirectory(
            String cik,
            String accessionNumber,
            Map<String, FilingDirectory> filingDirectoryCache) {
        String cacheKey = cik + ":" + accessionNumber;
        return filingDirectoryCache.computeIfAbsent(cacheKey, key -> {
            try {
                String indexJson = secApiClient.fetchFiling(cik, accessionNumber, "index.json");
                JsonNode items = objectMapper.readTree(indexJson).path("directory").path("item");
                if (!items.isArray()) {
                    return FilingDirectory.EMPTY;
                }

                List<String> names = new ArrayList<>();
                for (JsonNode item : items) {
                    String name = normalizeDocumentName(item.path("name").asText(null));
                    if (name != null && !name.equalsIgnoreCase("index.json")) {
                        names.add(name);
                    }
                }

                return names.isEmpty() ? FilingDirectory.EMPTY : new FilingDirectory(names);
            } catch (Exception e) {
                if (isSecAutomationBlock(e)) {
                    throw e instanceof RuntimeException runtimeException
                            ? runtimeException
                            : new IllegalStateException(e);
                }
                log.debug("Failed to load filing directory for {}: {}", accessionNumber, e.getMessage());
                return FilingDirectory.EMPTY;
            }
        });
    }

    private boolean requiresDirectoryLookup(String formType, Filling filling) {
        return filling == null
                || filling.getPrimaryDocument() == null
                || filling.getPrimaryDocument().isBlank()
                || is13F(formType);
    }

    private String resolvePrimaryDocument(String formType, Filling filling, FilingDirectory filingDirectory) {
        String primaryDocument = normalizeDocumentName(filling != null ? filling.getPrimaryDocument() : null);
        if (primaryDocument != null) {
            return primaryDocument;
        }

        if (filingDirectory.isEmpty()) {
            return null;
        }

        if (isOwnershipXmlForm(formType) || is13DG(formType)) {
            return firstMatch(filingDirectory.fileNames(), this::isXmlDocument);
        }

        if (is13F(formType)) {
            String primary = firstMatch(filingDirectory.fileNames(), this::isPrimaryDocumentCandidate);
            return primary != null ? primary : firstMatch(filingDirectory.fileNames(), this::isXmlDocument);
        }

        return firstMatch(
                filingDirectory.fileNames(),
                name -> isPrimaryDocumentCandidate(name) || isXmlDocument(name));
    }

    private String resolveInfoTableDocument(String primaryDocument, FilingDirectory filingDirectory) {
        if (filingDirectory.isEmpty()) {
            return isInformationTableCandidate(primaryDocument) ? primaryDocument : null;
        }

        String normalizedPrimary = normalizeDocumentName(primaryDocument);
        String infoTable = firstMatch(filingDirectory.fileNames(),
                name -> isInformationTableCandidate(name) && !name.equalsIgnoreCase(normalizedPrimary));
        if (infoTable != null) {
            return infoTable;
        }

        infoTable = firstMatch(filingDirectory.fileNames(),
                name -> isXmlDocument(name) && !name.equalsIgnoreCase(normalizedPrimary));
        if (infoTable != null) {
            return infoTable;
        }

        return isXmlDocument(normalizedPrimary) ? normalizedPrimary : null;
    }

    private boolean routeFiling(ResolvedFiling filing) {
        String formType = filing.formType();

        if (isForm3(formType)) {
            return persistIfNew(
                    filing.accessionNumber(),
                    form3Service::existsByAccessionNumber,
                    () -> form3Service.downloadAndParse(
                            filing.cik(),
                            filing.accessionNumber(),
                            normalizeOwnershipDocument(filing.primaryDocument())),
                    form3Service::save);
        }

        if (isForm4(formType)) {
            return persistIfNew(
                    filing.accessionNumber(),
                    form4Service::existsByAccessionNumber,
                    () -> form4Service.downloadAndParseForm4(
                            filing.cik(),
                            filing.accessionNumber(),
                            normalizeOwnershipDocument(filing.primaryDocument())),
                    form4Service::save);
        }

        if (isForm5(formType)) {
            return persistIfNew(
                    filing.accessionNumber(),
                    form5Service::existsByAccessionNumber,
                    () -> form5Service.downloadAndParse(
                            filing.cik(),
                            filing.accessionNumber(),
                            normalizeOwnershipDocument(filing.primaryDocument())),
                    form5Service::save);
        }

        if (is13DG(formType)) {
            return persistIfNew(
                    filing.accessionNumber(),
                    form13DGService::existsByAccessionNumber,
                    () -> form13DGService.downloadAndParse(
                            filing.cik(),
                            filing.accessionNumber(),
                            normalizeOwnershipDocument(filing.primaryDocument())),
                    form13DGService::save);
        }

        if (is13F(formType)) {
            return persistIfNew(
                    filing.accessionNumber(),
                    form13FService::existsByAccessionNumber,
                    () -> form13FService.downloadAndParseForm13F(
                            filing.cik(),
                            filing.accessionNumber(),
                            filing.primaryDocument(),
                            filing.infoTableDocument()),
                    form13FService::save);
        }

        if (isForm8K(formType)) {
            return persistIfNew(
                    filing.accessionNumber(),
                    form8KService::existsByAccessionNumber,
                    () -> form8KService.downloadAndParse(
                            filing.cik(),
                            filing.accessionNumber(),
                            filing.primaryDocument()),
                    form8KService::save);
        }

        if (isForm6K(formType)) {
            return persistIfNew(
                    filing.accessionNumber(),
                    form6KService::existsByAccessionNumber,
                    () -> form6KService.downloadAndParse(
                            filing.cik(),
                            filing.accessionNumber(),
                            filing.primaryDocument()),
                    form6KService::save);
        }

        if (isForm20F(formType)) {
            return persistIfNew(
                    filing.accessionNumber(),
                    form20FService::existsByAccessionNumber,
                    () -> form20FService.downloadAndParse(
                            filing.cik(),
                            filing.accessionNumber(),
                            filing.primaryDocument()),
                    form20FService::save);
        }

        log.debug("Realtime filing sync does not yet route form type {}", formType);
        return false;
    }

    private <T> boolean persistIfNew(
            String accessionNumber,
            Predicate<String> existsCheck,
            java.util.function.Supplier<CompletableFuture<T>> downloadAction,
            Function<T, T> saveAction) {
        if (existsCheck.test(accessionNumber)) {
            return false;
        }

        T parsed = downloadAction.get().join();
        if (parsed == null) {
            return false;
        }

        return saveAction.apply(parsed) != null;
    }

    private String extractAccessionNumber(EftsSearchResponse.Hit hit) {
        if (hit == null) {
            return null;
        }

        EftsSearchResponse.Source source = hit.getSource();
        String candidate = firstNonBlank(
                source != null ? source.getAccessionNumber() : null,
                source != null ? source.getAdsh() : null,
                hit.getId());
        return normalizeAccessionNumber(candidate);
    }

    private String extractCik(EftsSearchResponse.Source source) {
        if (source == null) {
            return null;
        }

        List<String> candidates = new ArrayList<>();
        if (source.getCik() != null) {
            candidates.add(source.getCik());
        }
        if (source.getEntityId() != null) {
            candidates.addAll(source.getEntityId());
        }
        if (source.getCiks() != null) {
            candidates.addAll(source.getCiks());
        }

        for (String candidate : candidates) {
            String normalized = normalizeCik(candidate);
            if (normalized != null) {
                return normalized;
            }
        }

        return null;
    }

    private String normalizeAccessionNumber(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }

        String cleaned = rawValue.trim();
        if (cleaned.endsWith(".txt")) {
            cleaned = cleaned.substring(0, cleaned.length() - 4);
        }

        if (cleaned.matches("\\d{10}-\\d{2}-\\d{6}")) {
            return cleaned;
        }

        String digitsOnly = cleaned.replaceAll("\\D", "");
        if (digitsOnly.matches("\\d{18}")) {
            return digitsOnly.substring(0, 10)
                    + "-"
                    + digitsOnly.substring(10, 12)
                    + "-"
                    + digitsOnly.substring(12);
        }

        return cleaned;
    }

    private String normalizeCik(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }

        String digitsOnly = rawValue.replaceAll("\\D", "");
        if (digitsOnly.isBlank()) {
            return null;
        }

        try {
            return String.format("%010d", Long.parseLong(digitsOnly));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String normalizeForms(String forms) {
        if (forms == null || forms.isBlank()) {
            return DEFAULT_FORMS;
        }

        String normalized = java.util.Arrays.stream(forms.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.replaceAll("\\s+", " ").toUpperCase(Locale.ROOT))
                .distinct()
                .reduce((left, right) -> left + "," + right)
                .orElse(DEFAULT_FORMS);

        return normalized.isBlank() ? DEFAULT_FORMS : normalized;
    }

    private int resolvePositive(Integer value, int fallback) {
        return value != null && value > 0 ? value : fallback;
    }

    private boolean isSecBlockCooldownActive() {
        Instant blockedUntil = secBlockCooldownUntil.get();
        if (blockedUntil == null) {
            return false;
        }
        if (Instant.now().isBefore(blockedUntil)) {
            return true;
        }
        secBlockCooldownUntil.compareAndSet(blockedUntil, null);
        return false;
    }

    private Instant activateSecBlockCooldown() {
        int cooldownMinutes = secBlockCooldownMinutes > 0 ? secBlockCooldownMinutes : 10;
        Instant blockedUntil = Instant.now().plus(Duration.ofMinutes(cooldownMinutes));
        secBlockCooldownUntil.set(blockedUntil);
        return blockedUntil;
    }

    private boolean isSecAutomationBlock(Throwable throwable) {
        return SecAccessDiagnostics.isUndeclaredAutomationBlock(throwable);
    }

    private String summarizeException(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() != null ? current.getMessage() : throwable.toString();
    }

    LocalDateTime currentDateTime() {
        return LocalDateTime.now();
    }

    private String normalizeFormType(String formType) {
        if (formType == null || formType.isBlank()) {
            return null;
        }
        return formType.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private String normalizeDocumentName(String documentName) {
        if (documentName == null || documentName.isBlank()) {
            return null;
        }

        String trimmed = documentName.trim();
        int lastSlash = Math.max(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'));
        return lastSlash >= 0 ? trimmed.substring(lastSlash + 1) : trimmed;
    }

    private String normalizeOwnershipDocument(String documentName) {
        return normalizeDocumentName(documentName);
    }

    private boolean isForm3(String formType) {
        return "3".equals(formType) || "3/A".equals(formType);
    }

    private boolean isForm4(String formType) {
        return "4".equals(formType) || "4/A".equals(formType);
    }

    private boolean isForm5(String formType) {
        return "5".equals(formType) || "5/A".equals(formType);
    }

    private boolean isForm8K(String formType) {
        return formType != null && formType.startsWith("8-K");
    }

    private boolean isForm6K(String formType) {
        return formType != null && formType.startsWith("6-K");
    }

    private boolean isForm20F(String formType) {
        return formType != null && formType.startsWith("20-F");
    }

    private boolean is13DG(String formType) {
        return formType != null
                && (formType.startsWith("SC 13D")
                || formType.startsWith("SC 13G"));
    }

    private boolean is13F(String formType) {
        return formType != null && formType.startsWith("13F");
    }

    private boolean isOwnershipXmlForm(String formType) {
        return isForm3(formType) || isForm4(formType) || isForm5(formType);
    }

    private boolean isXmlDocument(String value) {
        if (value == null) {
            return false;
        }

        String lower = value.toLowerCase(Locale.ROOT);
        return lower.endsWith(".xml") && !lower.endsWith(".xsd");
    }

    private boolean isPrimaryDocumentCandidate(String value) {
        if (value == null) {
            return false;
        }

        String lower = value.toLowerCase(Locale.ROOT);
        return (lower.endsWith(".htm")
                || lower.endsWith(".html")
                || lower.endsWith(".txt")
                || lower.endsWith(".xml"))
                && !"index.json".equals(lower)
                && !lower.endsWith(".xsd");
    }

    private boolean isInformationTableCandidate(String value) {
        if (!isXmlDocument(value)) {
            return false;
        }

        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("info")
                || lower.contains("table")
                || lower.contains("13f");
    }

    private String firstMatch(List<String> values, Predicate<String> matcher) {
        return values.stream()
                .filter(matcher)
                .findFirst()
                .orElse(null);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private record SyncConfig(boolean enabled, String forms, int lookbackHours, int maxPages, int pageSize) {
    }

    private record ResolvedFiling(
            String cik,
            String accessionNumber,
            String formType,
            String primaryDocument,
            String infoTableDocument) {
    }

    private record FilingDirectory(List<String> fileNames) {
        private static final FilingDirectory EMPTY = new FilingDirectory(List.of());

        private boolean isEmpty() {
            return fileNames == null || fileNames.isEmpty();
        }
    }
}
