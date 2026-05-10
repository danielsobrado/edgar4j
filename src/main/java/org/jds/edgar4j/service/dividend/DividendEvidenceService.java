package org.jds.edgar4j.service.dividend;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.jds.edgar4j.dto.response.DividendEventsResponse;
import org.jds.edgar4j.dto.response.DividendEvidenceResponse;
import org.jds.edgar4j.dto.response.DividendOverviewResponse;
import org.jds.edgar4j.integration.SecApiConfig;
import org.jds.edgar4j.integration.SecApiClient;
import org.jds.edgar4j.model.Company;
import org.jds.edgar4j.model.CompanyMarketData;
import org.jds.edgar4j.model.Filling;
import org.jds.edgar4j.validation.UrlAllowlistValidator;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DividendEvidenceService {

    private static final int MAX_EVIDENCE_TEXT_LENGTH = 12_000;

    private final SecApiClient secApiClient;
    private final SecApiConfig secApiConfig;
    private final UrlAllowlistValidator urlAllowlistValidator;
    private final DividendEventExtractor dividendEventExtractor;

    public DividendEventsResponse buildEvents(
            DividendOverviewResponse.CompanySummary companySummary,
            String ticker,
            List<Filling> currentReports,
            List<Filling> annualReports,
            List<Filling> recentFilings,
            LocalDate since) {
        List<DividendEventsResponse.DividendEvent> events = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (currentReports == null || currentReports.isEmpty()) {
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
                .company(companySummary)
                .events(filteredEvents)
                .warnings(warnings.stream().distinct().toList())
                .build();
    }

    public DividendEvidenceResponse buildEvidence(
            DividendOverviewResponse.CompanySummary companySummary,
            List<Filling> filings,
            Filling filing,
            String accessionNumber) {
        String accessionNumberToMatch = accessionNumber;
        if (filing == null || filings == null || filings.isEmpty()) {
            return null;
        }

        String rawDocument = loadFilingDocument(filing)
                .orElse(null);
        if (rawDocument == null) {
            return null;
        }

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
                .company(companySummary)
                .filing(toSourceFiling(filing, filingUrl))
                .highlights(highlights)
                .cleanedText(blankToNull(cleanedPreview))
                .truncated(truncated)
                .warnings(warnings.stream().distinct().toList())
                .build();
    }

    public Optional<Filling> resolveFilingByAccession(List<Filling> filings, String accessionNumber) {
        String normalizedRequested = normalizeAccession(accessionNumber);
        if (normalizedRequested == null) {
            return Optional.empty();
        }

        for (Filling filing : filings) {
            if (accessionMatches(filing != null ? filing.getAccessionNumber() : null, normalizedRequested)) {
                return Optional.ofNullable(filing);
            }
        }

        return Optional.empty();
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

    private LocalDate toLocalDate(java.util.Date date) {
        if (date == null) {
            return null;
        }

        return date.toInstant().atZone(java.time.ZoneOffset.UTC).toLocalDate();
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
}
