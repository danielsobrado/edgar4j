package org.jds.edgar4j.service.xbrl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

@Component
public class SecBulkNotesDatasetParser {

    private static final String EMPTY_DIMENSION_HASH = "0x00000000";

    public List<NumRecord> parseNumLines(List<String> lines) {
        if (lines == null || lines.size() <= 1) {
            return List.of();
        }

        return lines.stream()
                .skip(1)
                .map(this::parseNumLine)
                .filter(Objects::nonNull)
                .toList();
    }

    public List<CustomDividendTag> findCustomDividendTags(List<String> tagLines) {
        if (tagLines == null || tagLines.size() <= 1) {
            return List.of();
        }

        return tagLines.stream()
                .skip(1)
                .map(this::parseTagLine)
                .filter(Objects::nonNull)
                .filter(tag -> tag.custom() && isDividendRelated(tag.tag()))
                .map(tag -> new CustomDividendTag(tag.tag(), tag.version(), tag.label()))
                .toList();
    }

    public DividendClassification classifyDividendDimension(String dimHash, Map<String, DimRecord> dimensions) {
        String normalizedHash = blankToNull(dimHash);
        if (normalizedHash == null || EMPTY_DIMENSION_HASH.equalsIgnoreCase(normalizedHash)) {
            return DividendClassification.CONSOLIDATED;
        }

        DimRecord dimension = dimensions != null ? dimensions.get(normalizedHash) : null;
        if (dimension == null) {
            return DividendClassification.UNKNOWN;
        }

        String dimensionText = (dimension.dimensionName() + " " + dimension.memberName()).toLowerCase(Locale.ROOT);
        if (dimensionText.contains("preferred")) {
            return DividendClassification.PREFERRED;
        }
        if (dimensionText.contains("common")) {
            return DividendClassification.COMMON;
        }
        if (dimensionText.contains("special")) {
            return DividendClassification.SPECIAL;
        }
        return DividendClassification.OTHER;
    }

    public List<NumRecord> deduplicateByInlinePriority(List<NumRecord> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }

        Map<String, NumRecord> bestByKey = new LinkedHashMap<>();
        for (NumRecord record : records) {
            if (record == null) {
                continue;
            }
            bestByKey.merge(record.deduplicationKey(), record, this::choosePreferredRecord);
        }
        return List.copyOf(bestByKey.values());
    }

    private NumRecord choosePreferredRecord(NumRecord candidate, NumRecord current) {
        return Comparator.comparingInt(NumRecord::inlineXbrlPriority)
                .thenComparing(NumRecord::accession)
                .compare(candidate, current) < 0
                        ? candidate
                        : current;
    }

    private NumRecord parseNumLine(String line) {
        String[] fields = split(line);
        if (fields.length < 10) {
            return null;
        }

        try {
            return new NumRecord(
                    fields[0],
                    fields[1],
                    fields[2],
                    blankToNull(fields[3]),
                    LocalDate.parse(fields[4], DateTimeFormatter.BASIC_ISO_DATE),
                    Integer.parseInt(fields[5]),
                    fields[6],
                    new BigDecimal(fields[7]),
                    blankToNull(fields[8]) != null ? fields[8] : EMPTY_DIMENSION_HASH,
                    parseInlinePriority(fields[9]));
        } catch (Exception e) {
            return null;
        }
    }

    private TagRecord parseTagLine(String line) {
        String[] fields = split(line);
        if (fields.length < 4) {
            return null;
        }

        return new TagRecord(
                fields[0],
                fields[1],
                "1".equals(fields[2]) || "true".equalsIgnoreCase(fields[2]),
                fields[3]);
    }

    private boolean isDividendRelated(String tag) {
        if (tag == null) {
            return false;
        }
        String normalized = tag.toLowerCase(Locale.ROOT);
        return normalized.contains("dividend")
                || normalized.contains("distribution")
                || normalized.contains("payout")
                || normalized.contains("declared");
    }

    private int parseInlinePriority(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            return Integer.MAX_VALUE;
        }
        return Integer.parseInt(normalized);
    }

    private String[] split(String line) {
        return line != null ? line.split("\t", -1) : new String[0];
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record NumRecord(
            String accession,
            String tag,
            String version,
            String coRegistrant,
            LocalDate periodEnd,
            int quarters,
            String unit,
            BigDecimal value,
            String dimensionHash,
            int inlineXbrlPriority) {

        private String deduplicationKey() {
            return String.join("|",
                    Objects.toString(accession, ""),
                    Objects.toString(tag, ""),
                    Objects.toString(version, ""),
                    Objects.toString(periodEnd, ""),
                    Objects.toString(unit, ""),
                    Objects.toString(dimensionHash, ""));
        }
    }

    public record DimRecord(String dimensionHash, String dimensionName, String memberName) {
    }

    public record CustomDividendTag(String tag, String version, String label) {
    }

    private record TagRecord(String tag, String version, boolean custom, String label) {
    }

    public enum DividendClassification {
        CONSOLIDATED,
        COMMON,
        PREFERRED,
        SPECIAL,
        OTHER,
        UNKNOWN
    }
}
