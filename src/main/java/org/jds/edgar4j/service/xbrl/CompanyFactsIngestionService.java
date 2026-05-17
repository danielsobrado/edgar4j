package org.jds.edgar4j.service.xbrl;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.jds.edgar4j.integration.SecApiClient;
import org.jds.edgar4j.integration.SecResponseParser;
import org.jds.edgar4j.integration.model.SecCompanyFactsResponse;
import org.jds.edgar4j.model.NormalizedXbrlFact;
import org.jds.edgar4j.port.NormalizedXbrlFactDataPort;
import org.jds.edgar4j.xbrl.standardization.ConceptStandardizer;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CompanyFactsIngestionService {

    private final SecApiClient secApiClient;
    private final SecResponseParser secResponseParser;
    private final ConceptStandardizer conceptStandardizer;
    private final NormalizedXbrlFactDataPort factDataPort;

    public BulkIngestionResult ingestAll(List<String> ciks, int maxCompanies) {
        if (ciks == null || ciks.isEmpty()) {
            return new BulkIngestionResult(0, 0, 0, 0, List.of(), Map.of());
        }

        List<String> rawCandidates = ciks.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();

        int effectiveLimit = maxCompanies > 0 ? maxCompanies : Integer.MAX_VALUE;
        List<String> candidates = new ArrayList<>();
        List<IngestionResult> results = new ArrayList<>();
        Map<String, String> failures = new LinkedHashMap<>();
        for (String rawCandidate : rawCandidates) {
            if (candidates.size() >= effectiveLimit) {
                break;
            }
            try {
                String normalizedCik = normalizeCik(rawCandidate);
                if (!candidates.contains(normalizedCik)) {
                    candidates.add(normalizedCik);
                }
            } catch (RuntimeException e) {
                failures.put(rawCandidate, e.getMessage());
            }
        }

        for (String cik : candidates) {
            try {
                results.add(ingest(cik));
            } catch (RuntimeException e) {
                failures.put(cik, e.getMessage());
            }
        }

        return new BulkIngestionResult(
                rawCandidates.size(),
                results.size() + failures.size(),
                results.size(),
                failures.size(),
                List.copyOf(results),
                Map.copyOf(failures));
    }

    public IngestionResult ingest(String cik) {
        String normalizedCik = normalizeCik(cik);
        SecCompanyFactsResponse response = secResponseParser.parseCompanyFactsResponse(
                secApiClient.fetchCompanyFacts(normalizedCik));

        List<NormalizedXbrlFact> facts = flatten(normalizedCik, response);
        int inserted = 0;
        int updated = 0;
        int skipped = 0;

        for (NormalizedXbrlFact fact : facts) {
            if (factDataPort.findById(fact.getId()).isPresent()) {
                updated++;
            } else {
                inserted++;
            }
        }

        factDataPort.saveAll(facts);
        recomputeCurrentBest(normalizedCik);

        return new IngestionResult(normalizedCik, inserted, updated, skipped);
    }

    private List<NormalizedXbrlFact> flatten(String cik, SecCompanyFactsResponse response) {
        if (response == null || response.getFacts() == null) {
            return List.of();
        }

        Instant now = Instant.now();
        return response.getFacts().entrySet().stream()
                .flatMap(taxonomyEntry -> taxonomyEntry.getValue().entrySet().stream()
                        .flatMap(tagEntry -> flattenConcept(
                                cik,
                                taxonomyEntry.getKey(),
                                tagEntry.getKey(),
                                tagEntry.getValue(),
                                now).stream()))
                .toList();
    }

    private List<NormalizedXbrlFact> flattenConcept(
            String cik,
            String taxonomy,
            String tag,
            SecCompanyFactsResponse.ConceptFacts conceptFacts,
            Instant now) {
        if (conceptFacts == null || conceptFacts.getUnits() == null) {
            return List.of();
        }

        String standardConcept = conceptStandardizer.mapToStandard(tag);
        return conceptFacts.getUnits().entrySet().stream()
                .flatMap(unitEntry -> unitEntry.getValue().stream()
                        .filter(entry -> entry.getVal() != null)
                        .filter(entry -> entry.getEnd() != null && !entry.getEnd().isBlank())
                        .map(entry -> buildFact(cik, taxonomy, tag, standardConcept, unitEntry.getKey(), entry, now)))
                .toList();
    }

    private NormalizedXbrlFact buildFact(
            String cik,
            String taxonomy,
            String tag,
            String standardConcept,
            String unit,
            SecCompanyFactsResponse.FactEntry entry,
            Instant now) {
        String normalizedUnit = normalizeUnit(unit);
        Map<String, String> dimensions = extractDimensions(entry);
        String dimensionsHash = stableDimensionsHash(dimensions);
        String id = stableId(
                cik,
                taxonomy,
                tag,
                normalizedUnit,
                entry.getEnd(),
                entry.getStart(),
                dimensionsHash,
                entry.getAccn());

        return NormalizedXbrlFact.builder()
                .id(id)
                .cik(cik)
                .taxonomy(taxonomy)
                .tag(tag)
                .standardConcept(standardConcept)
                .unit(normalizedUnit)
                .periodEnd(parseDate(entry.getEnd()))
                .periodStart(parseDate(entry.getStart()))
                .value(entry.getVal())
                .accession(entry.getAccn())
                .form(entry.getForm())
                .fiscalYear(entry.getFy())
                .fiscalPeriod(entry.getFp())
                .filedDate(parseDate(entry.getFiled()))
                .frame(entry.getFrame())
                .source("API")
                .tagVersion(taxonomy)
                .customTag(false)
                .dimensionsHash(dimensionsHash)
                .dimensions(dimensions)
                .currentBest(false)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private String normalizeUnit(String unit) {
        if (unit == null || unit.isBlank()) {
            return "USD";
        }

        String normalized = unit.trim();
        return switch (normalized.toLowerCase(Locale.ROOT)) {
            case "usd" -> "USD";
            case "usd/shares", "usd-per-shares", "usd per shares" -> "USD-per-shares";
            case "pure" -> "PURE";
            case "shares" -> "SHARES";
            default -> normalized.toUpperCase(Locale.ROOT);
        };
    }

    private Map<String, String> extractDimensions(SecCompanyFactsResponse.FactEntry entry) {
        if (entry == null || entry.getExtensionFields() == null || entry.getExtensionFields().isEmpty()) {
            return Map.of();
        }

        Map<String, String> dimensions = new TreeMap<>();
        entry.getExtensionFields().forEach((name, value) -> {
            if (!isDimensionField(name)) {
                return;
            }

            String path = isDimensionContainer(name) && isNestedDimensionValue(value) ? "" : name;
            collectDimensionValue(dimensions, path, value);
        });

        return dimensions.isEmpty() ? Map.of() : new LinkedHashMap<>(dimensions);
    }

    private boolean isDimensionField(String name) {
        if (name == null) {
            return false;
        }

        String normalized = name.toLowerCase(Locale.ROOT);
        return isDimensionContainer(normalized)
                || normalized.contains("axis")
                || normalized.contains("member");
    }

    private boolean isDimensionContainer(String name) {
        if (name == null) {
            return false;
        }

        String normalized = name.toLowerCase(Locale.ROOT);
        return normalized.equals("dim")
                || normalized.equals("dimension")
                || normalized.equals("dimensions")
                || normalized.equals("segment")
                || normalized.equals("segments")
                || normalized.equals("explicitmember")
                || normalized.equals("typedmember");
    }

    private boolean isNestedDimensionValue(Object value) {
        return value instanceof Map<?, ?> || value instanceof Iterable<?>;
    }

    private void collectDimensionValue(Map<String, String> dimensions, String path, Object value) {
        if (value instanceof Map<?, ?> mapValue) {
            mapValue.entrySet().stream()
                    .sorted(Comparator.comparing(entry -> Objects.toString(entry.getKey(), "")))
                    .forEach(entry -> collectDimensionValue(
                            dimensions,
                            appendPath(path, Objects.toString(entry.getKey(), "")),
                            entry.getValue()));
            return;
        }

        if (value instanceof Iterable<?> iterableValue) {
            int index = 0;
            for (Object item : iterableValue) {
                collectDimensionValue(dimensions, "%s[%d]".formatted(path, index), item);
                index++;
            }
            return;
        }

        if (value != null && path != null && !path.isBlank()) {
            dimensions.put(path, Objects.toString(value));
        }
    }

    private String appendPath(String path, String next) {
        if (path == null || path.isBlank()) {
            return next;
        }
        if (next == null || next.isBlank()) {
            return path;
        }
        return path + "." + next;
    }

    private void recomputeCurrentBest(String cik) {
        List<NormalizedXbrlFact> allFacts = factDataPort.findByCik(cik);
        Map<CurrentBestKey, NormalizedXbrlFact> bestFacts = new LinkedHashMap<>();

        for (NormalizedXbrlFact fact : allFacts) {
            fact.setCurrentBest(false);
            CurrentBestKey key = CurrentBestKey.from(fact);
            bestFacts.merge(key, fact, this::chooseBetterFact);
        }

        bestFacts.values().forEach(fact -> fact.setCurrentBest(true));
        factDataPort.saveAll(allFacts);
    }

    private NormalizedXbrlFact chooseBetterFact(NormalizedXbrlFact candidate, NormalizedXbrlFact current) {
        Comparator<NormalizedXbrlFact> comparator = Comparator
                .comparing(NormalizedXbrlFact::getFiledDate, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(fact -> isAmendment(fact.getForm()))
                .thenComparing(fact -> fact.getAccession() == null ? "" : fact.getAccession());
        return comparator.compare(candidate, current) >= 0 ? candidate : current;
    }

    private boolean isAmendment(String form) {
        return form != null && form.endsWith("/A");
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value);
    }

    private String normalizeCik(String cik) {
        if (cik == null || cik.isBlank()) {
            throw new IllegalArgumentException("CIK cannot be null or blank");
        }
        String digits = cik.replaceAll("[^0-9]", "");
        if (digits.isBlank()) {
            throw new IllegalArgumentException("CIK must contain digits");
        }
        return String.format("%010d", Long.parseLong(digits));
    }

    private String stableDimensionsHash(Map<String, String> dimensions) {
        if (dimensions == null || dimensions.isEmpty()) {
            return "";
        }

        return stableId(dimensions.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .toArray(String[]::new));
    }

    private String stableId(String... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String key = String.join("|", java.util.Arrays.stream(parts)
                    .map(part -> part == null ? "" : part)
                    .toList());
            byte[] hash = digest.digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    public record IngestionResult(String cik, int inserted, int updated, int skipped) {
    }

    public record BulkIngestionResult(
            int requested,
            int processed,
            int succeeded,
            int failed,
            List<IngestionResult> results,
            Map<String, String> failures) {
    }

    private record CurrentBestKey(
            String cik,
            String taxonomy,
            String tag,
            String unit,
            LocalDate periodEnd,
            LocalDate periodStart,
            String dimensionsHash) {

        static CurrentBestKey from(NormalizedXbrlFact fact) {
            return new CurrentBestKey(
                    fact.getCik(),
                    fact.getTaxonomy(),
                    fact.getTag(),
                    fact.getUnit(),
                    fact.getPeriodEnd(),
                    fact.getPeriodStart(),
                    Objects.toString(fact.getDimensionsHash(), ""));
        }
    }
}
