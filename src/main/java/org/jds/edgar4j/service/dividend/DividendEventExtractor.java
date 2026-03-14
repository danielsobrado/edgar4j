package org.jds.edgar4j.service.dividend;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jds.edgar4j.dto.response.DividendEventsResponse;
import org.jds.edgar4j.integration.Form8KParser;
import org.jds.edgar4j.model.Filling;
import org.jds.edgar4j.model.Form8K;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DividendEventExtractor {

    private static final Pattern DECLARATION_PATTERN = Pattern.compile(
            "(?is)\\b(board(?:\\s+of\\s+directors)?|directors?)\\b.{0,160}?\\b(declared|approved|authorized)\\b.{0,220}?\\b(cash\\s+dividend|dividend|distribution)\\b");
    private static final Pattern SPECIAL_PATTERN = Pattern.compile(
            "(?is)\\b(special|one-time|extraordinary|supplemental)\\b.{0,30}?\\bdividend\\b");
    private static final Pattern INCREASE_PATTERN = Pattern.compile(
            "(?is)\\b(increas(?:e|ed|es|ing)|raise(?:d|s)?|higher)\\b.{0,100}?\\bdividend\\b");
    private static final Pattern DECREASE_PATTERN = Pattern.compile(
            "(?is)\\b(reduc(?:e|ed|es|ing)|decreas(?:e|ed|es|ing)|cut(?:s|ting)?|lower(?:ed|ing)?)\\b.{0,100}?\\bdividend\\b");
    private static final Pattern SUSPENSION_PATTERN = Pattern.compile(
            "(?is)\\b(suspend(?:ed|ing)?|omit(?:ted|ting)?|eliminat(?:e|ed|ing)|discontinue(?:d|ing)?)\\b.{0,120}?\\b(dividend|dividends|distribution)\\b");
    private static final Pattern REINSTATEMENT_PATTERN = Pattern.compile(
            "(?is)\\b(reinstate(?:d|ment)?|resume(?:d|s|ing)?)\\b.{0,120}?\\b(dividend|dividends)\\b");
    private static final Pattern POLICY_PATTERN = Pattern.compile(
            "(?is)\\b(at\\s+the\\s+discretion\\s+of\\s+the\\s+board|future\\s+dividends\\s+(?:will|may).{0,120}?(?:depend|subject)|restricted\\s+payments|covenant.{0,100}?dividend|no\\s+assurance.{0,120}?dividend)\\b");
    private static final Pattern AMOUNT_PER_SHARE_PATTERN = Pattern.compile(
            "(?i)\\$\\s*(?<amount>\\d+(?:\\.\\d{1,4})?)\\s*(?:per\\s+(?:common\\s+)?share|a\\s+share|per\\s+share)");
    private static final Pattern RECORD_DATE_PATTERN = Pattern.compile(
            "(?i)\\b(?:record\\s+date|holders\\s+of\\s+record|stockholders\\s+of\\s+record)\\b.{0,60}?\\b(?:on|as\\s+of|at\\s+the\\s+close\\s+of\\s+business\\s+on)\\b\\s*(?<date>" + dateRegex() + ")");
    private static final Pattern PAYABLE_DATE_PATTERN = Pattern.compile(
            "(?i)\\b(?:payable|payment\\s+date|to\\s+be\\s+paid)\\b.{0,40}?\\b(?:on|by|of)\\b\\s*(?<date>" + dateRegex() + ")");
    private static final Pattern DECLARATION_DATE_PATTERN = Pattern.compile(
            "(?i)\\b(?:declared|approved|authorized)\\b.{0,30}?\\bon\\b\\s*(?<date>" + dateRegex() + ")");
    private static final Pattern LEADING_DECLARATION_DATE_PATTERN = Pattern.compile(
            "(?i)\\bon\\b\\s*(?<date>" + dateRegex() + ")\\s*,?\\s*the\\s+board(?:\\s+of\\s+directors)?\\b.{0,80}?\\b(declared|approved|authorized)\\b");
    private static final Pattern DIVIDEND_TYPE_PATTERN = Pattern.compile(
            "(?i)\\b(regular|quarterly|monthly|annual|interim|special|one-time|extraordinary|supplemental)\\b.{0,20}?\\bdividend\\b");

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US),
            DateTimeFormatter.ofPattern("MMMM d yyyy", Locale.US),
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US),
            DateTimeFormatter.ofPattern("MMM d yyyy", Locale.US));

    private final Form8KParser form8KParser;

    public List<ExtractedDividendEvent> extract(String rawDocument, Filling filing, String sourceUrl) {
        if (rawDocument == null || rawDocument.isBlank() || filing == null) {
            return List.of();
        }

        List<SearchSection> sections = buildSections(rawDocument, filing);
        Map<String, ExtractedDividendEvent> events = new LinkedHashMap<>();
        for (SearchSection section : sections) {
            collectEvents(events, section, filing, sourceUrl);
        }

        return events.values().stream()
                .sorted(Comparator
                        .comparing(ExtractedDividendEvent::eventDate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ExtractedDividendEvent::filedDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ExtractedDividendEvent::eventType))
                .toList();
    }

    public String cleanDocumentText(String rawDocument) {
        return clean(rawDocument);
    }

    private List<SearchSection> buildSections(String rawDocument, Filling filing) {
        String cleanedDocument = clean(rawDocument);
        List<SearchSection> sections = new ArrayList<>();

        String formType = filing.getFormType() != null ? filing.getFormType().getNumber() : null;
        if (formType != null && formType.toUpperCase(Locale.ROOT).startsWith("8-K")) {
            Form8K parsed = form8KParser.parse(rawDocument, filing.getAccessionNumber());
            if (parsed != null && parsed.getItemSections() != null) {
                parsed.getItemSections().stream()
                        .filter(section -> "8.01".equals(section.getItemNumber()) || "9.01".equals(section.getItemNumber()))
                        .map(section -> new SearchSection(
                                "ITEM_" + section.getItemNumber(),
                                clean(section.getContent()),
                                0))
                        .filter(section -> !section.text().isBlank())
                        .forEach(sections::add);
            }
        }

        if (!cleanedDocument.isBlank()) {
            sections.add(new SearchSection("DOCUMENT_TEXT", cleanedDocument, 1));
        }

        return sections;
    }

    private void collectEvents(
            Map<String, ExtractedDividendEvent> events,
            SearchSection section,
            Filling filing,
            String sourceUrl) {
        String text = section.text();
        if (text.isBlank()) {
            return;
        }

        maybeAddEvent(events, buildDeclarationEvent(text, section, filing, sourceUrl));
        maybeAddEvent(events, buildSimpleEvent(text, section, filing, sourceUrl,
                DividendEventsResponse.EventType.SUSPENSION,
                SUSPENSION_PATTERN,
                DividendEventsResponse.EventConfidence.HIGH));
        maybeAddEvent(events, buildSimpleEvent(text, section, filing, sourceUrl,
                DividendEventsResponse.EventType.REINSTATEMENT,
                REINSTATEMENT_PATTERN,
                DividendEventsResponse.EventConfidence.MEDIUM));
        maybeAddEvent(events, buildSimpleEvent(text, section, filing, sourceUrl,
                DividendEventsResponse.EventType.INCREASE,
                INCREASE_PATTERN,
                DividendEventsResponse.EventConfidence.MEDIUM));
        maybeAddEvent(events, buildSimpleEvent(text, section, filing, sourceUrl,
                DividendEventsResponse.EventType.DECREASE,
                DECREASE_PATTERN,
                DividendEventsResponse.EventConfidence.MEDIUM));

        if (isAnnualOrQuarterly(filing)) {
            maybeAddEvent(events, buildSimpleEvent(text, section, filing, sourceUrl,
                    DividendEventsResponse.EventType.POLICY_CHANGE,
                    POLICY_PATTERN,
                    DividendEventsResponse.EventConfidence.LOW));
        }
    }

    private ExtractedDividendEvent buildDeclarationEvent(
            String text,
            SearchSection section,
            Filling filing,
            String sourceUrl) {
        Matcher matcher = DECLARATION_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }

        String snippet = extractSnippet(text, matcher.start(), matcher.end());
        BigDecimal amount = extractAmount(snippet);
        LocalDate declarationDate = firstNonNull(
                extractDate(snippet, DECLARATION_DATE_PATTERN),
                extractDate(snippet, LEADING_DECLARATION_DATE_PATTERN),
                extractDate(text, LEADING_DECLARATION_DATE_PATTERN));
        LocalDate recordDate = firstNonNull(extractDate(snippet, RECORD_DATE_PATTERN), extractDate(text, RECORD_DATE_PATTERN));
        LocalDate payableDate = firstNonNull(extractDate(snippet, PAYABLE_DATE_PATTERN), extractDate(text, PAYABLE_DATE_PATTERN));
        DividendEventsResponse.DividendType dividendType = extractDividendType(snippet);
        DividendEventsResponse.EventType eventType = SPECIAL_PATTERN.matcher(snippet).find()
                ? DividendEventsResponse.EventType.SPECIAL
                : DividendEventsResponse.EventType.DECLARATION;
        DividendEventsResponse.EventConfidence confidence = amount != null
                ? DividendEventsResponse.EventConfidence.HIGH
                : DividendEventsResponse.EventConfidence.MEDIUM;

        return ExtractedDividendEvent.builder()
                .eventType(eventType)
                .formType(filing.getFormType() != null ? filing.getFormType().getNumber() : null)
                .accessionNumber(filing.getAccessionNumber())
                .filedDate(toLocalDate(filing.getFillingDate()))
                .declarationDate(declarationDate)
                .recordDate(recordDate)
                .payableDate(payableDate)
                .amountPerShare(amount)
                .dividendType(dividendType)
                .confidence(confidence)
                .extractionMethod("REGEX")
                .sourceSection(section.name())
                .textSnippet(snippet)
                .policyLanguage(extractPolicyLanguage(text))
                .url(sourceUrl)
                .build();
    }

    private ExtractedDividendEvent buildSimpleEvent(
            String text,
            SearchSection section,
            Filling filing,
            String sourceUrl,
            DividendEventsResponse.EventType eventType,
            Pattern pattern,
            DividendEventsResponse.EventConfidence confidence) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return null;
        }

        String snippet = extractSnippet(text, matcher.start(), matcher.end());
        return ExtractedDividendEvent.builder()
                .eventType(eventType)
                .formType(filing.getFormType() != null ? filing.getFormType().getNumber() : null)
                .accessionNumber(filing.getAccessionNumber())
                .filedDate(toLocalDate(filing.getFillingDate()))
                .declarationDate(extractDate(snippet, DECLARATION_DATE_PATTERN))
                .recordDate(extractDate(snippet, RECORD_DATE_PATTERN))
                .payableDate(extractDate(snippet, PAYABLE_DATE_PATTERN))
                .amountPerShare(extractAmount(snippet))
                .dividendType(extractDividendType(snippet))
                .confidence(confidence)
                .extractionMethod("REGEX")
                .sourceSection(section.name())
                .textSnippet(snippet)
                .policyLanguage(eventType == DividendEventsResponse.EventType.POLICY_CHANGE ? snippet : null)
                .url(sourceUrl)
                .build();
    }

    private void maybeAddEvent(Map<String, ExtractedDividendEvent> events, ExtractedDividendEvent candidate) {
        if (candidate == null) {
            return;
        }

        String key = String.join("|",
                Objects.toString(candidate.accessionNumber(), ""),
                Objects.toString(candidate.eventType(), ""),
                Objects.toString(candidate.sourceSection(), ""),
                Objects.toString(candidate.amountPerShare(), ""),
                Objects.toString(candidate.declarationDate(), ""),
                Objects.toString(candidate.recordDate(), ""),
                Objects.toString(candidate.payableDate(), ""));

        events.putIfAbsent(key, candidate);
    }

    private BigDecimal extractAmount(String text) {
        Matcher matcher = AMOUNT_PER_SHARE_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }

        try {
            return new BigDecimal(matcher.group("amount"));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDate extractDate(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return null;
        }

        String raw = matcher.group("date");
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String normalized = raw.replaceAll("\\s+", " ").trim();
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(normalized, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private DividendEventsResponse.DividendType extractDividendType(String text) {
        Matcher matcher = DIVIDEND_TYPE_PATTERN.matcher(text);
        if (!matcher.find()) {
            return DividendEventsResponse.DividendType.UNKNOWN;
        }

        String raw = matcher.group(1).toLowerCase(Locale.ROOT);
        return switch (raw) {
            case "regular" -> DividendEventsResponse.DividendType.REGULAR;
            case "quarterly" -> DividendEventsResponse.DividendType.QUARTERLY;
            case "monthly" -> DividendEventsResponse.DividendType.MONTHLY;
            case "annual" -> DividendEventsResponse.DividendType.ANNUAL;
            case "interim" -> DividendEventsResponse.DividendType.INTERIM;
            case "special", "one-time", "extraordinary", "supplemental" -> DividendEventsResponse.DividendType.SPECIAL;
            default -> DividendEventsResponse.DividendType.UNKNOWN;
        };
    }

    private String extractPolicyLanguage(String text) {
        Matcher matcher = POLICY_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        return extractSnippet(text, matcher.start(), matcher.end());
    }

    private String extractSnippet(String text, int start, int end) {
        int snippetStart = Math.max(0, start - 120);
        int snippetEnd = Math.min(text.length(), end + 180);
        return text.substring(snippetStart, snippetEnd).replaceAll("\\s+", " ").trim();
    }

    private boolean isAnnualOrQuarterly(Filling filing) {
        if (filing == null || filing.getFormType() == null || filing.getFormType().getNumber() == null) {
            return false;
        }

        String formType = filing.getFormType().getNumber().toUpperCase(Locale.ROOT);
        return formType.startsWith("10-K") || formType.startsWith("20-F") || formType.startsWith("40-F")
                || formType.startsWith("10-Q");
    }

    private String clean(String rawHtml) {
        if (rawHtml == null || rawHtml.isBlank()) {
            return "";
        }

        String text = Jsoup.parse(rawHtml)
                .text()
                .replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        return text;
    }

    private LocalDate toLocalDate(java.util.Date value) {
        if (value == null) {
            return null;
        }
        return value.toInstant().atZone(java.time.ZoneOffset.UTC).toLocalDate();
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

    private static String dateRegex() {
        return "(?:January|February|March|April|May|June|July|August|September|October|November|December|"
                + "Jan\\.?|Feb\\.?|Mar\\.?|Apr\\.?|Jun\\.?|Jul\\.?|Aug\\.?|Sep\\.?|Sept\\.?|Oct\\.?|Nov\\.?|Dec\\.?)"
                + "\\s+\\d{1,2},?\\s+\\d{4}";
    }

    private record SearchSection(String name, String text, int priority) {
    }

    public record ExtractedDividendEvent(
            DividendEventsResponse.EventType eventType,
            String formType,
            String accessionNumber,
            LocalDate filedDate,
            LocalDate declarationDate,
            LocalDate recordDate,
            LocalDate payableDate,
            BigDecimal amountPerShare,
            DividendEventsResponse.DividendType dividendType,
            DividendEventsResponse.EventConfidence confidence,
            String extractionMethod,
            String sourceSection,
            String textSnippet,
            String policyLanguage,
            String url) {

        public LocalDate eventDate() {
            return declarationDate != null ? declarationDate : filedDate;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private DividendEventsResponse.EventType eventType;
            private String formType;
            private String accessionNumber;
            private LocalDate filedDate;
            private LocalDate declarationDate;
            private LocalDate recordDate;
            private LocalDate payableDate;
            private BigDecimal amountPerShare;
            private DividendEventsResponse.DividendType dividendType;
            private DividendEventsResponse.EventConfidence confidence;
            private String extractionMethod;
            private String sourceSection;
            private String textSnippet;
            private String policyLanguage;
            private String url;

            public Builder eventType(DividendEventsResponse.EventType eventType) {
                this.eventType = eventType;
                return this;
            }

            public Builder formType(String formType) {
                this.formType = formType;
                return this;
            }

            public Builder accessionNumber(String accessionNumber) {
                this.accessionNumber = accessionNumber;
                return this;
            }

            public Builder filedDate(LocalDate filedDate) {
                this.filedDate = filedDate;
                return this;
            }

            public Builder declarationDate(LocalDate declarationDate) {
                this.declarationDate = declarationDate;
                return this;
            }

            public Builder recordDate(LocalDate recordDate) {
                this.recordDate = recordDate;
                return this;
            }

            public Builder payableDate(LocalDate payableDate) {
                this.payableDate = payableDate;
                return this;
            }

            public Builder amountPerShare(BigDecimal amountPerShare) {
                this.amountPerShare = amountPerShare;
                return this;
            }

            public Builder dividendType(DividendEventsResponse.DividendType dividendType) {
                this.dividendType = dividendType;
                return this;
            }

            public Builder confidence(DividendEventsResponse.EventConfidence confidence) {
                this.confidence = confidence;
                return this;
            }

            public Builder extractionMethod(String extractionMethod) {
                this.extractionMethod = extractionMethod;
                return this;
            }

            public Builder sourceSection(String sourceSection) {
                this.sourceSection = sourceSection;
                return this;
            }

            public Builder textSnippet(String textSnippet) {
                this.textSnippet = textSnippet;
                return this;
            }

            public Builder policyLanguage(String policyLanguage) {
                this.policyLanguage = policyLanguage;
                return this;
            }

            public Builder url(String url) {
                this.url = url;
                return this;
            }

            public ExtractedDividendEvent build() {
                return new ExtractedDividendEvent(
                        eventType,
                        formType,
                        accessionNumber,
                        filedDate,
                        declarationDate,
                        recordDate,
                        payableDate,
                        amountPerShare,
                        dividendType,
                        confidence,
                        extractionMethod,
                        sourceSection,
                        textSnippet,
                        policyLanguage,
                        url);
            }
        }
    }
}
