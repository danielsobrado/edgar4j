package org.jds.edgar4j.search;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.jds.edgar4j.model.CompanyTicker;
import org.jds.edgar4j.model.Filling;
import org.jds.edgar4j.port.CompanyTickerDataPort;
import org.jds.edgar4j.port.FilingSearchPort;
import org.jds.edgar4j.port.TickerDataPort;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class FilingSearchSupport {

    private FilingSearchSupport() {
    }

    public static Page<FilingSearchPort.SearchResult> pageResults(List<FilingSearchPort.SearchResult> source, Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return new PageImpl<>(List.copyOf(source));
        }

        List<FilingSearchPort.SearchResult> sorted = applyResultSort(source, pageable.getSort());
        int start = Math.toIntExact(Math.min((long) pageable.getPageNumber() * pageable.getPageSize(), sorted.size()));
        int end = Math.min(start + pageable.getPageSize(), sorted.size());
        return new PageImpl<>(sorted.subList(start, end), pageable, sorted.size());
    }

    public static List<Filling> applyFillingSort(List<Filling> source, Sort sort) {
        List<Filling> records = new ArrayList<>(source);
        Sort effectiveSort = sort;
        if (effectiveSort == null || effectiveSort.isUnsorted()) {
            effectiveSort = Sort.by(Sort.Direction.DESC, "fillingDate");
        }

        Comparator<Filling> comparator = null;
        for (Sort.Order order : effectiveSort) {
            Comparator<Filling> nextComparator = (left, right) -> compareValues(
                comparableProperty(left, order.getProperty()),
                comparableProperty(right, order.getProperty()),
                order.isDescending());
            comparator = comparator == null ? nextComparator : comparator.thenComparing(nextComparator);
        }

        if (comparator != null) {
            records.sort(comparator);
        }
        return records;
    }

    public static List<String> resolveCiks(String symbol, TickerDataPort tickerDataPort, CompanyTickerDataPort companyTickerDataPort) {
        if (!hasText(symbol)) {
            return List.of();
        }

        String normalized = symbol.trim().toUpperCase(Locale.ROOT);
        Set<String> resolved = new LinkedHashSet<>();

        tickerDataPort.findByCode(normalized)
                .map(ticker -> ticker.getCik())
                .filter(FilingSearchSupport::hasText)
                .ifPresent(resolved::add);

        Optional<CompanyTicker> companyTicker = companyTickerDataPort.findByTickerIgnoreCase(symbol.trim());
        companyTicker.map(CompanyTicker::getCikStr)
                .filter(Objects::nonNull)
                .ifPresent(cikValue -> {
                    String rawCik = String.valueOf(cikValue);
                    resolved.add(rawCik);
                    resolved.add(String.format("%010d", cikValue));
                });

        return List.copyOf(resolved);
    }

    public static FilingSearchPort.SearchResult toSearchResult(Filling filling, String query) {
        return new FilingSearchPort.SearchResult(
                filling.getId(),
                type(filling),
                title(filling),
                snippet(filling, query),
                score(filling, query),
                filingDate(filling.getFillingDate()));
    }

    public static boolean matchesFormTypes(Filling filling, List<String> formTypes) {
        if (formTypes == null || formTypes.isEmpty()) {
            return true;
        }

        String formType = filling.getFormType() != null ? filling.getFormType().getNumber() : null;
        if (!hasText(formType)) {
            return false;
        }

        return formTypes.stream()
                .filter(FilingSearchSupport::hasText)
                .anyMatch(type -> formType.equalsIgnoreCase(type.trim()));
    }

    public static boolean matchesDateRange(Filling filling, LocalDate startDate, LocalDate endDate) {
        LocalDate filingDate = filingDate(filling.getFillingDate());
        if (filingDate == null) {
            return startDate == null && endDate == null;
        }
        if (startDate != null && filingDate.isBefore(startDate)) {
            return false;
        }
        if (endDate != null && filingDate.isAfter(endDate)) {
            return false;
        }
        return true;
    }

    public static boolean matchesQuery(Filling filling, String query) {
        if (!hasText(query)) {
            return true;
        }

        String searchableText = searchableText(filling);
        return tokenize(query).stream().allMatch(searchableText::contains);
    }

    public static List<String> tokenize(String query) {
        if (!hasText(query)) {
            return List.of();
        }

        return List.of(query.trim().toLowerCase(Locale.ROOT).split("\\s+"))
                .stream()
                .filter(FilingSearchSupport::hasText)
                .toList();
    }

    public static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public static String searchableText(Filling filling) {
        return String.join(" ",
                lower(filling.getCompany()),
                lower(filling.getCik()),
                lower(filling.getAccessionNumber()),
                lower(filling.getPrimaryDocument()),
                lower(filling.getPrimaryDocDescription()),
                lower(filling.getItems()),
                lower(filling.getFormType() != null ? filling.getFormType().getNumber() : null),
                lower(filling.getFormType() != null ? filling.getFormType().getDescription() : null));
    }

    public static String title(Filling filling) {
        String company = hasText(filling.getCompany()) ? filling.getCompany() : filling.getCik();
        String formType = filling.getFormType() != null ? filling.getFormType().getNumber() : null;
        if (hasText(company) && hasText(formType)) {
            return company + " - " + formType;
        }
        return hasText(company) ? company : Optional.ofNullable(filling.getAccessionNumber()).orElse("Filing");
    }

    public static String type(Filling filling) {
        return filling.getFormType() != null && hasText(filling.getFormType().getNumber())
                ? filling.getFormType().getNumber().toLowerCase(Locale.ROOT)
                : "filing";
    }

    public static String snippet(Filling filling, String query) {
        List<String> candidates = Arrays.asList(
                filling.getPrimaryDocDescription(),
                filling.getItems(),
                filling.getPrimaryDocument(),
                filling.getCompany(),
                filling.getAccessionNumber(),
                filling.getCik());
        List<String> tokens = tokenize(query);

        for (String candidate : candidates) {
            if (!hasText(candidate)) {
                continue;
            }
            String lowerCandidate = candidate.toLowerCase(Locale.ROOT);
            if (tokens.isEmpty() || tokens.stream().anyMatch(lowerCandidate::contains)) {
                return candidate;
            }
        }

        return FilingSearchSupport.title(filling);
    }

    public static double score(Filling filling, String query) {
        if (!hasText(query)) {
            return 1.0d;
        }

        String normalizedQuery = query.trim().toLowerCase(Locale.ROOT);
        double score = 0.0d;
        if (hasText(filling.getCompany()) && filling.getCompany().equalsIgnoreCase(query.trim())) {
            score += 5.0d;
        }
        if (hasText(filling.getCik()) && filling.getCik().equals(query.trim())) {
            score += 5.0d;
        }
        if (hasText(filling.getAccessionNumber()) && filling.getAccessionNumber().equalsIgnoreCase(query.trim())) {
            score += 4.0d;
        }

        String searchableText = searchableText(filling);
        for (String token : tokenize(normalizedQuery)) {
            if (searchableText.contains(token)) {
                score += 1.0d;
            }
        }
        return score == 0.0d ? 1.0d : score;
    }

    public static LocalDate filingDate(java.util.Date value) {
        return value == null ? null : value.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private static List<FilingSearchPort.SearchResult> applyResultSort(List<FilingSearchPort.SearchResult> source, Sort sort) {
        List<FilingSearchPort.SearchResult> records = new ArrayList<>(source);
        Sort effectiveSort = sort;
        if (effectiveSort == null || effectiveSort.isUnsorted()) {
            effectiveSort = Sort.by(Sort.Direction.DESC, "filingDate");
        }

        Comparator<FilingSearchPort.SearchResult> comparator = null;
        for (Sort.Order order : effectiveSort) {
            String property = "fillingDate".equals(order.getProperty()) ? "filingDate" : order.getProperty();
            Comparator<FilingSearchPort.SearchResult> nextComparator = (left, right) -> compareValues(
                comparableProperty(left, property),
                comparableProperty(right, property),
                order.isDescending());
            comparator = comparator == null ? nextComparator : comparator.thenComparing(nextComparator);
        }

        if (comparator != null) {
            records.sort(comparator);
        }
        return records;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Comparable<?> comparableProperty(Object source, String property) {
        Object value = new BeanWrapperImpl(source).getPropertyValue(property);
        if (value == null) {
            return null;
        }
        if (value instanceof Comparable comparable) {
            return comparable;
        }
        return value.toString();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static int compareValues(Comparable left, Comparable right, boolean descending) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return descending ? right.compareTo(left) : left.compareTo(right);
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}