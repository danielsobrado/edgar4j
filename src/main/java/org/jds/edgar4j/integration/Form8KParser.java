package org.jds.edgar4j.integration;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jds.edgar4j.model.Form8K;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Best-effort parser for Form 8-K primary documents (HTML or text).
 *
 * 8-K filings are not consistently structured; this parser focuses on extracting:
 * - Form type (8-K / 8-K/A)
 * - Trading symbol(s) from the cover page table when available
 * - Item sections (Item 1.01, Item 2.02, etc.)
 * - Exhibit index entries when present as a table
 */
@Slf4j
@Component
public class Form8KParser {

    private static final Pattern FORM_TYPE_PATTERN =
            Pattern.compile("(?i)\\b(?:form\\s+)?(8-k\\s*/\\s*a|8-k/a|8-k)\\b");

    private static final Pattern ITEM_HEADING_PATTERN =
            Pattern.compile("(?i)^\\s*item\\s+((?:\\d{1,2})(?:\\.\\d{1,2})?)\\s*(?:[:.\\-–—]|\\s)\\s*(.*)$");

    private static final Pattern TRADING_SYMBOL_INLINE_PATTERN =
            Pattern.compile("(?i)\\btrading\\s+symbol\\(s\\)\\b\\s*[:\\-–—]?\\s*([A-Z][A-Z0-9.]{0,9}(?:\\s*,\\s*[A-Z][A-Z0-9.]{0,9})*)");

    public Form8K parse(String rawDocument, String accessionNumber) {
        if (rawDocument == null || rawDocument.isBlank()) {
            return null;
        }

        try {
            Document doc = Jsoup.parse(rawDocument);
            String formType = detectFormType(doc, rawDocument);
            String tradingSymbol = extractTradingSymbol(doc, rawDocument);

            List<String> lines = extractTextLines(doc, rawDocument);
            List<Form8K.ItemSection> itemSections = extractItemSections(lines);
            String items = itemSections.isEmpty() ? null : String.join(",",
                    itemSections.stream().map(Form8K.ItemSection::getItemNumber).toList());

            List<Form8K.Exhibit> exhibits = extractExhibits(doc);
            if (exhibits.isEmpty()) {
                exhibits = null;
            }

            return Form8K.builder()
                    .accessionNumber(accessionNumber)
                    .formType(formType)
                    .tradingSymbol(tradingSymbol)
                    .items(items)
                    .itemSections(itemSections.isEmpty() ? null : itemSections)
                    .exhibits(exhibits)
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse 8-K document for accession: {}", accessionNumber, e);
            return null;
        }
    }

    private String detectFormType(Document doc, String raw) {
        String text = safeLower(doc != null ? doc.text() : raw);
        Matcher m = FORM_TYPE_PATTERN.matcher(text);
        if (!m.find()) {
            return "8-K";
        }
        String match = m.group(1).toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
        if (match.contains("/A")) {
            return "8-K/A";
        }
        return "8-K";
    }

    private String extractTradingSymbol(Document doc, String raw) {
        String symbolFromTable = extractTradingSymbolFromCoverTable(doc);
        if (symbolFromTable != null) {
            return symbolFromTable;
        }
        String text = doc != null ? doc.text() : raw;
        Matcher m = TRADING_SYMBOL_INLINE_PATTERN.matcher(text);
        if (m.find()) {
            String symbols = m.group(1);
            if (symbols != null) {
                String first = symbols.split(",")[0].trim();
                return first.isBlank() ? null : first;
            }
        }
        return null;
    }

    private String extractTradingSymbolFromCoverTable(Document doc) {
        if (doc == null) return null;

        Elements tables = doc.select("table");
        for (Element table : tables) {
            String tableText = safeLower(table.text());
            if (!tableText.contains("trading symbol")) {
                continue;
            }

            // Try to locate a header row with "Trading Symbol(s)"
            Elements rows = table.select("tr");
            if (rows.isEmpty()) continue;

            int symbolCol = -1;
            for (Element row : rows) {
                Elements headerCells = row.select("th, td");
                for (int i = 0; i < headerCells.size(); i++) {
                    String cellText = safeLower(headerCells.get(i).text());
                    if (cellText.contains("trading symbol")) {
                        symbolCol = i;
                        break;
                    }
                }
                if (symbolCol != -1) {
                    break;
                }
            }

            if (symbolCol == -1) continue;

            // Take the first non-header row that has a value in the symbol column.
            for (Element row : rows) {
                Elements cells = row.select("td");
                if (cells.size() <= symbolCol) continue;
                String candidate = normalizeText(cells.get(symbolCol).text());
                if (candidate == null) continue;
                candidate = candidate.replaceAll("[^A-Z0-9.]", "");
                if (!candidate.isBlank() && candidate.length() <= 10) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private List<Form8K.ItemSection> extractItemSections(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }

        List<Integer> headingIndexes = new ArrayList<>();
        List<String> headingNumbers = new ArrayList<>();
        List<String> headingTitles = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher m = ITEM_HEADING_PATTERN.matcher(line);
            if (!m.matches()) {
                continue;
            }
            String itemNumber = m.group(1);
            String title = normalizeText(m.group(2));
            if (title != null && title.length() > 500) {
                title = title.substring(0, 500);
            }

            // Title sometimes sits on the next short line
            if ((title == null || title.isBlank()) && i + 1 < lines.size()) {
                String next = lines.get(i + 1);
                if (!ITEM_HEADING_PATTERN.matcher(next).matches() && next.length() <= 200) {
                    title = next;
                }
            }

            headingIndexes.add(i);
            headingNumbers.add(itemNumber);
            headingTitles.add(title);
        }

        if (headingIndexes.isEmpty()) {
            return List.of();
        }

        Map<String, CandidateSection> bestByItem = new LinkedHashMap<>();
        for (int h = 0; h < headingIndexes.size(); h++) {
            int startIdx = headingIndexes.get(h);
            int endExclusive = (h + 1 < headingIndexes.size()) ? headingIndexes.get(h + 1) : lines.size();

            String itemNumber = headingNumbers.get(h);
            String title = headingTitles.get(h);

            String content = joinLines(lines, startIdx + 1, endExclusive);
            content = trimAndLimit(content, 200_000);

            // Heuristic: ignore TOC-like entries with little/no content.
            int contentScore = scoreContent(content);
            if (contentScore < 12) {
                continue;
            }

            int adjustedScore = adjustScoreForCoverPage(contentScore, content, startIdx);
            if (adjustedScore < 12) {
                continue;
            }

            CandidateSection candidate = new CandidateSection(itemNumber, title, content, adjustedScore, startIdx);
            CandidateSection existing = bestByItem.get(itemNumber);
            if (existing == null || candidate.contentScore > existing.contentScore) {
                bestByItem.put(itemNumber, candidate);
            }
        }

        if (bestByItem.isEmpty()) {
            return List.of();
        }

        // Preserve document order by sorting on start index for the chosen entries.
        return bestByItem.values().stream()
                .sorted((a, b) -> Integer.compare(a.startIndex, b.startIndex))
                .map(c -> Form8K.ItemSection.builder()
                        .itemNumber(c.itemNumber)
                        .title(c.title)
                        .content(c.content)
                        .build())
                .toList();
    }

    private List<Form8K.Exhibit> extractExhibits(Document doc) {
        if (doc == null) return List.of();

        List<Form8K.Exhibit> exhibits = new ArrayList<>();

        for (Element table : doc.select("table")) {
            String tableText = safeLower(table.text());
            if (!tableText.contains("exhibit") || !tableText.contains("description")) {
                continue;
            }

            Elements rows = table.select("tr");
            if (rows.size() < 2) continue;

            // Determine which columns are exhibit number + description based on header row.
            int exhibitCol = -1;
            int descCol = -1;
            for (Element row : rows) {
                Elements headerCells = row.select("th, td");
                for (int i = 0; i < headerCells.size(); i++) {
                    String t = safeLower(headerCells.get(i).text());
                    if (exhibitCol == -1 && t.contains("exhibit")) exhibitCol = i;
                    if (descCol == -1 && t.contains("description")) descCol = i;
                }
                if (exhibitCol != -1 && descCol != -1) break;
            }

            if (exhibitCol == -1 || descCol == -1) continue;

            for (Element row : rows) {
                Elements cells = row.select("td");
                if (cells.isEmpty() || cells.size() <= Math.max(exhibitCol, descCol)) continue;

                String exhibitNumber = normalizeText(cells.get(exhibitCol).text());
                String description = normalizeText(cells.get(descCol).text());
                if (exhibitNumber == null || exhibitNumber.isBlank()) continue;

                String href = null;
                Element link = row.selectFirst("a[href]");
                if (link != null) {
                    href = normalizeText(link.attr("href"));
                }

                // Basic exhibit number normalization (keep digits/dots only)
                exhibitNumber = exhibitNumber.replaceAll("[^0-9.]", "");
                if (exhibitNumber.isBlank()) continue;

                exhibits.add(Form8K.Exhibit.builder()
                        .exhibitNumber(exhibitNumber)
                        .description(description)
                        .document(href)
                        .build());
            }

            if (!exhibits.isEmpty()) {
                break;
            }
        }

        return exhibits;
    }

    private List<String> extractTextLines(Document doc, String rawDocument) {
        String textWithNewlines;
        if (doc != null && doc.body() != null) {
            // Make line splitting more reliable by injecting newlines at common block boundaries.
            doc.outputSettings(new Document.OutputSettings().prettyPrint(false));
            doc.select("br").append("\n");
            doc.select("p, div, tr, li").prepend("\n");
            textWithNewlines = doc.body().wholeText();
        } else {
            textWithNewlines = rawDocument;
        }

        String normalized = normalizeText(textWithNewlines);
        if (normalized == null) return List.of();

        String[] parts = normalized.split("\\R+");
        List<String> lines = new ArrayList<>(parts.length);
        for (String p : parts) {
            String line = normalizeText(p);
            if (line == null || line.isBlank()) continue;
            // Avoid very long table dumps blowing up memory; keep lines reasonably sized.
            if (line.length() > 20_000) {
                line = line.substring(0, 20_000);
            }
            lines.add(line);
        }
        return lines;
    }

    private static String joinLines(List<String> lines, int startInclusive, int endExclusive) {
        if (lines == null || startInclusive >= endExclusive || startInclusive < 0) {
            return "";
        }
        int end = Math.min(endExclusive, lines.size());
        StringBuilder sb = new StringBuilder();
        for (int i = startInclusive; i < end; i++) {
            String line = lines.get(i);
            if (line == null || line.isBlank()) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(line);
        }
        return sb.toString();
    }

    private static int scoreContent(String content) {
        if (content == null) return 0;
        String c = content.trim();
        if (c.isEmpty()) return 0;
        // Prefer content with more words/characters.
        int lengthScore = Math.min(c.length(), 10_000) / 10;
        int wordScore = Math.min(c.split("\\s+").length, 2_000);
        return lengthScore + wordScore;
    }

    private static int adjustScoreForCoverPage(int score, String content, int startIndex) {
        if (score <= 0 || content == null) return 0;
        if (startIndex > 80) return score;

        String lower = content.toLowerCase(Locale.ROOT);
        boolean looksLikeCoverPage =
                lower.contains("trading symbol") ||
                lower.contains("title of each class") ||
                lower.contains("securities and exchange commission");

        if (!looksLikeCoverPage) {
            return score;
        }
        return score / 10;
    }

    private static String trimAndLimit(String s, int maxChars) {
        if (s == null) return null;
        String t = s.trim();
        if (t.length() <= maxChars) return t;
        return t.substring(0, maxChars);
    }

    private static String normalizeText(String s) {
        if (s == null) return null;
        String t = Normalizer.normalize(s, Normalizer.Form.NFKC);
        t = t.replace('\u00A0', ' '); // nbsp
        t = t.replaceAll("[\\t\\f\\r]+", " ");
        t = t.replaceAll(" +", " ").trim();
        return t;
    }

    private static String safeLower(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT);
    }

    private static final class CandidateSection {
        private final String itemNumber;
        private final String title;
        private final String content;
        private final int contentScore;
        private final int startIndex;

        private CandidateSection(String itemNumber, String title, String content, int contentScore, int startIndex) {
            this.itemNumber = itemNumber;
            this.title = title;
            this.content = content;
            this.contentScore = contentScore;
            this.startIndex = startIndex;
        }
    }
}
