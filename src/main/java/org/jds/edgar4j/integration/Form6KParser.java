package org.jds.edgar4j.integration;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jds.edgar4j.model.Form6K;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Best-effort parser for Form 6-K primary documents (HTML or text).
 */
@Slf4j
@Component
public class Form6KParser {

    private static final Pattern FORM_TYPE_PATTERN =
            Pattern.compile("(?i)\\b(?:form\\s+)?(6-k\\s*/\\s*a|6-k/a|6-k)\\b");

    private static final Pattern TRADING_SYMBOL_INLINE_PATTERN =
            Pattern.compile("(?i)\\btrading\\s+symbol\\(s\\)\\b\\s*[:\\-–—]?\\s*([A-Z][A-Z0-9.]{0,9}(?:\\s*,\\s*[A-Z][A-Z0-9.]{0,9})*)");

    public Form6K parse(String rawDocument, String accessionNumber) {
        if (rawDocument == null || rawDocument.isBlank()) {
            return null;
        }

        try {
            Document doc = Jsoup.parse(rawDocument);
            String formType = detectFormType(doc, rawDocument);
            String tradingSymbol = extractTradingSymbol(doc, rawDocument);

            String reportText = extractReportText(doc, rawDocument);

            List<Form6K.Exhibit> exhibits = extractExhibits(doc);
            if (exhibits.isEmpty()) {
                exhibits = null;
            }

            return Form6K.builder()
                    .accessionNumber(accessionNumber)
                    .formType(formType)
                    .tradingSymbol(tradingSymbol)
                    .reportText(reportText)
                    .exhibits(exhibits)
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse 6-K document for accession: {}", accessionNumber, e);
            return null;
        }
    }

    private String detectFormType(Document doc, String raw) {
        String text = safeLower(doc != null ? doc.text() : raw);
        Matcher m = FORM_TYPE_PATTERN.matcher(text);
        if (!m.find()) {
            return "6-K";
        }
        String match = m.group(1).toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
        if (match.contains("/A")) {
            return "6-K/A";
        }
        return "6-K";
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

    private List<Form6K.Exhibit> extractExhibits(Document doc) {
        if (doc == null) return List.of();

        List<Form6K.Exhibit> exhibits = new ArrayList<>();

        for (Element table : doc.select("table")) {
            String tableText = safeLower(table.text());
            if (!tableText.contains("exhibit") || !tableText.contains("description")) {
                continue;
            }

            Elements rows = table.select("tr");
            if (rows.size() < 2) continue;

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

                exhibitNumber = exhibitNumber.replaceAll("[^0-9.]", "");
                if (exhibitNumber.isBlank()) continue;

                exhibits.add(Form6K.Exhibit.builder()
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

    private String extractReportText(Document doc, String rawDocument) {
        List<String> lines = extractTextLines(doc, rawDocument);
        if (lines.isEmpty()) return null;
        String reportText = String.join("\n", lines);
        return trimAndLimit(reportText, 200_000);
    }

    private List<String> extractTextLines(Document doc, String rawDocument) {
        String textWithNewlines;
        if (doc != null && doc.body() != null) {
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
            if (line.length() > 20_000) {
                line = line.substring(0, 20_000);
            }
            lines.add(line);
        }
        return lines;
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
        t = t.replace('\u00A0', ' ');
        t = t.replaceAll("[\\t\\f\\r]+", " ");
        t = t.replaceAll(" +", " ").trim();
        return t;
    }

    private static String safeLower(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT);
    }
}

