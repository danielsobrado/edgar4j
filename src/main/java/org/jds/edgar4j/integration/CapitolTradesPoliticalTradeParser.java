package org.jds.edgar4j.integration;

import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jds.edgar4j.model.PoliticalTrade;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

class CapitolTradesPoliticalTradeParser {

    private static final String SOURCE = "CAPITOL_TRADES";
    private static final String BASE_URL = "https://www.capitoltrades.com";
    private static final Pattern INTEGER_PATTERN = Pattern.compile("-?\\d+");
    private static final Map<String, Month> MONTHS = Map.ofEntries(
            Map.entry("jan", Month.JANUARY),
            Map.entry("january", Month.JANUARY),
            Map.entry("feb", Month.FEBRUARY),
            Map.entry("february", Month.FEBRUARY),
            Map.entry("mar", Month.MARCH),
            Map.entry("march", Month.MARCH),
            Map.entry("apr", Month.APRIL),
            Map.entry("april", Month.APRIL),
            Map.entry("may", Month.MAY),
            Map.entry("jun", Month.JUNE),
            Map.entry("june", Month.JUNE),
            Map.entry("jul", Month.JULY),
            Map.entry("july", Month.JULY),
            Map.entry("aug", Month.AUGUST),
            Map.entry("august", Month.AUGUST),
            Map.entry("sep", Month.SEPTEMBER),
            Map.entry("sept", Month.SEPTEMBER),
            Map.entry("september", Month.SEPTEMBER),
            Map.entry("oct", Month.OCTOBER),
            Map.entry("october", Month.OCTOBER),
            Map.entry("nov", Month.NOVEMBER),
            Map.entry("november", Month.NOVEMBER),
            Map.entry("dec", Month.DECEMBER),
            Map.entry("december", Month.DECEMBER));

    private final Clock clock;

    CapitolTradesPoliticalTradeParser(Clock clock) {
        this.clock = clock;
    }

    List<PoliticalTrade> parse(String html, URI pageUri, String requestedAssetType) {
        if (html == null || html.isBlank()) {
            return List.of();
        }

        Document document = Jsoup.parse(html, pageUri == null ? BASE_URL : pageUri.toString());
        Elements rows = document.select("tr:has(.cell--politician):has(.cell--traded-issuer)");
        List<PoliticalTrade> trades = new ArrayList<>();
        for (Element row : rows) {
            parseRow(row, requestedAssetType).ifPresent(trades::add);
        }
        return trades;
    }

    private Optional<PoliticalTrade> parseRow(Element row, String requestedAssetType) {
        Elements cells = row.select("> td");
        if (cells.size() < 9) {
            return Optional.empty();
        }

        String politicianName = text(row, ".politician-name a");
        String issuerName = text(row, ".issuer-name a");
        String ticker = normalizeTicker(text(row, ".issuer-ticker"));
        if (politicianName == null || issuerName == null) {
            return Optional.empty();
        }

        String detailHref = row.select("a[href^=/trades/]").stream()
                .map(link -> link.attr("href"))
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
        String sourceTradeUrl = absoluteUrl(detailHref);
        String sourceTradeId = sourceTradeId(detailHref, row);

        List<String> publishedParts = cellParts(cells.get(2));
        List<String> tradedParts = cellParts(cells.get(3));
        String amountLabel = amountLabel(cells.get(7));

        String assetType = Optional.ofNullable(inferAssetType(row))
                .orElseGet(() -> normalizeAssetType(requestedAssetType));

        PoliticalTrade trade = PoliticalTrade.builder()
                .sourceTradeId(sourceTradeId)
                .politicianName(politicianName)
                .party(text(row, ".party"))
                .chamber(text(row, ".chamber"))
                .state(text(row, ".us-state-compact"))
                .issuerName(issuerName)
                .ticker(ticker)
                .disclosureDate(parseDateParts(publishedParts))
                .tradedDate(parseDateParts(tradedParts))
                .filedAfterDays(parseInteger(cells.get(4).text()))
                .owner(text(cells.get(5), ".q-label"))
                .transactionType(normalizeTransactionType(text(row, ".tx-type")))
                .amountLabel(amountLabel)
                .amountMin(parseAmountRange(amountLabel).min())
                .amountMax(parseAmountRange(amountLabel).max())
                .price(parsePrice(cells.get(8).text()))
                .assetType(assetType)
                .sourceTradeUrl(sourceTradeUrl)
                .source(SOURCE)
                .build();

        return Optional.of(trade);
    }

    private List<String> cellParts(Element cell) {
        List<String> parts = cell.select(".text-center > div").eachText().stream()
                .map(this::blankToNull)
                .filter(Objects::nonNull)
                .toList();
        if (!parts.isEmpty()) {
            return parts;
        }
        String text = blankToNull(cell.text());
        return text == null ? List.of() : List.of(text.split("\\s+"));
    }

    private LocalDate parseDateParts(List<String> parts) {
        if (parts == null || parts.isEmpty()) {
            return null;
        }

        LocalDate today = LocalDate.now(clock);
        String joined = String.join(" ", parts).trim();
        String last = parts.get(parts.size() - 1).trim().toLowerCase(Locale.ROOT);
        if ("today".equals(last)) {
            return today;
        }
        if ("yesterday".equals(last)) {
            return today.minusDays(1);
        }
        if ("n/a".equals(last) || "-".equals(last)) {
            return null;
        }

        Matcher matcher = Pattern.compile("(\\d{1,2})\\s+([A-Za-z]{3,9})\\s+(\\d{4})").matcher(joined);
        if (!matcher.find()) {
            return null;
        }

        Month month = MONTHS.get(matcher.group(2).toLowerCase(Locale.ROOT));
        if (month == null) {
            return null;
        }
        return LocalDate.of(Integer.parseInt(matcher.group(3)), month, Integer.parseInt(matcher.group(1)));
    }

    private String amountLabel(Element cell) {
        String label = text(cell, ".trade-size .text-size-2");
        if (label != null) {
            return normalizeDash(label);
        }
        label = text(cell, ".trade-size");
        return label == null ? null : normalizeDash(label);
    }

    private AmountRange parseAmountRange(String label) {
        if (label == null) {
            return new AmountRange(null, null);
        }
        String normalized = normalizeDash(label)
                .replace("$", "")
                .replace(",", "")
                .replace(" ", "")
                .toUpperCase(Locale.ROOT);
        if (normalized.isBlank() || "N/A".equals(normalized)) {
            return new AmountRange(null, null);
        }

        String[] parts = normalized.split("-");
        Double min = parseMagnitude(parts[0]);
        Double max = parts.length > 1 ? parseMagnitude(parts[1]) : min;
        return new AmountRange(min, max);
    }

    private Double parseMagnitude(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        double multiplier = 1d;
        String number = value;
        if (value.endsWith("K")) {
            multiplier = 1_000d;
            number = value.substring(0, value.length() - 1);
        } else if (value.endsWith("M")) {
            multiplier = 1_000_000d;
            number = value.substring(0, value.length() - 1);
        } else if (value.endsWith("B")) {
            multiplier = 1_000_000_000d;
            number = value.substring(0, value.length() - 1);
        }
        try {
            return Double.parseDouble(number) * multiplier;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Double parsePrice(String text) {
        String value = blankToNull(text);
        if (value == null || "N/A".equalsIgnoreCase(value)) {
            return null;
        }
        try {
            return Double.parseDouble(value.replace("$", "").replace(",", "").trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Integer parseInteger(String text) {
        String value = blankToNull(text);
        if (value == null) {
            return null;
        }
        Matcher matcher = INTEGER_PATTERN.matcher(value);
        return matcher.find() ? Integer.parseInt(matcher.group()) : null;
    }

    private String normalizeTicker(String ticker) {
        String value = blankToNull(ticker);
        if (value == null) {
            return null;
        }
        String normalized = value.replace("$", "").trim().toUpperCase(Locale.ROOT);
        int suffixIndex = normalized.indexOf(':');
        return suffixIndex > 0 ? normalized.substring(0, suffixIndex) : normalized;
    }

    private String normalizeTransactionType(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeAssetType(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? "stock" : normalized.toLowerCase(Locale.ROOT);
    }

    private String inferAssetType(Element row) {
        String value = blankToNull(row.attr("data-asset-type"));
        if (value == null) {
            value = blankToNull(row.attr("data-assetType"));
        }
        if (value == null) {
            value = row.select("[data-asset-type], [data-assetType]").stream()
                    .map(element -> element.hasAttr("data-asset-type")
                            ? element.attr("data-asset-type")
                            : element.attr("data-assetType"))
                    .map(this::blankToNull)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        }
        if (value == null) {
            value = text(row, ".asset-type, .assetType");
        }
        if (value == null) {
            value = row.classNames().stream()
                    .filter(className -> className.startsWith("asset-type--") || className.startsWith("asset--"))
                    .map(className -> className.replace("asset-type--", "").replace("asset--", ""))
                    .findFirst()
                    .orElse(null);
        }
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT).replace(' ', '-');
    }

    private String sourceTradeId(String detailHref, Element row) {
        String href = blankToNull(detailHref);
        if (href != null) {
            int slash = href.lastIndexOf('/');
            String id = slash >= 0 ? href.substring(slash + 1) : href;
            if (!id.isBlank()) {
                return SOURCE + ":" + id;
            }
        }
        return SOURCE + ":" + sha256(row.text());
    }

    private String absoluteUrl(String href) {
        String value = blankToNull(href);
        if (value == null) {
            return null;
        }
        return value.startsWith("http") ? value : BASE_URL + value;
    }

    private String text(Element root, String selector) {
        if (root == null) {
            return null;
        }
        return blankToNull(root.select(selector).first() == null ? null : root.select(selector).first().text());
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeDash(String value) {
        return value == null ? null : value.replace('\u2013', '-').replace('\u2014', '-');
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private record AmountRange(Double min, Double max) {
    }
}
