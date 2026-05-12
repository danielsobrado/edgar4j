package org.jds.edgar4j.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.jds.edgar4j.dto.request.CompanySearchRequest;
import org.jds.edgar4j.dto.request.DividendScreenRequest;
import org.jds.edgar4j.dto.response.CompanyListResponse;
import org.jds.edgar4j.dto.response.CompanyResponse;
import org.jds.edgar4j.dto.response.DividendOverviewResponse;
import org.jds.edgar4j.model.Filling;
import org.jds.edgar4j.service.CompanyService;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class DividendCompanyContextService {

    private final CompanyService companyService;
    private final DividendFilingAnalysisService dividendFilingAnalysisService;

    Optional<CompanyResponse> resolveCompany(String tickerOrCik) {
        String identifier = tickerOrCik == null ? "" : tickerOrCik.trim();
        if (identifier.isEmpty()) {
            throw new IllegalArgumentException("tickerOrCik is required");
        }

        if (identifier.chars().allMatch(Character::isDigit)) {
            return companyService.getCompanyByCik(identifier);
        }

        return companyService.getCompanyByTicker(identifier.toUpperCase(Locale.ROOT));
    }

    Optional<String> normalizeCik(String cik) {
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

    Optional<String> normalizeTicker(String ticker) {
        String normalized = blankToNull(ticker);
        return normalized != null ? Optional.of(normalized.toUpperCase(Locale.ROOT)) : Optional.empty();
    }

    List<String> normalizeIdentifiers(List<String> identifiers) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (identifiers == null) {
            return List.of();
        }

        for (String rawIdentifier : identifiers) {
            if (rawIdentifier == null) {
                continue;
            }

            for (String token : rawIdentifier.split(",")) {
                String identifier = blankToNull(token);
                if (identifier != null) {
                    normalized.add(identifier);
                }
            }
        }

        return List.copyOf(normalized);
    }

    List<String> resolveScreenIdentifiers(
            DividendScreenRequest request,
            int candidateLimit,
            List<String> warnings) {
        List<String> explicitIdentifiers = normalizeIdentifiers(request.getTickersOrCiks());
        if (!explicitIdentifiers.isEmpty()) {
            return explicitIdentifiers.stream().limit(candidateLimit).toList();
        }

        if (blankToNull(request.getSearchTerm()) == null) {
            warnings.add("No tickersOrCiks or searchTerm were provided, so the screen used the first "
                    + candidateLimit + " locally stored companies.");
        }

        CompanySearchRequest companySearch = CompanySearchRequest.builder()
                .searchTerm(blankToNull(request.getSearchTerm()))
                .page(0)
                .size(candidateLimit)
                .sortBy("name")
                .sortDir("asc")
                .build();
        List<CompanyListResponse> candidates = companyService.searchCompanies(companySearch).getContent();
        return candidates.stream()
                .map(candidate -> firstNonBlank(candidate.getTicker(), candidate.getCik()))
                .filter(Objects::nonNull)
                .toList();
    }

    DividendOverviewResponse.CompanySummary buildCompanySummary(
            CompanyResponse company,
            String ticker,
            List<Filling> filings) {
        LocalDate lastFilingDate = filings.stream()
                .map(dividendFilingAnalysisService::resolveSortableFilingDate)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        return DividendOverviewResponse.CompanySummary.builder()
                .cik(normalizeCik(company.getCik()).orElse(company.getCik()))
                .ticker(ticker)
                .name(firstNonBlank(company.getName(), ticker, company.getCik()))
                .sector(blankToNull(company.getSicDescription()))
                .fiscalYearEnd(formatFiscalYearEnd(company.getFiscalYearEnd()))
                .lastFilingDate(lastFilingDate)
                .dataFreshness(Instant.now())
                .build();
    }

    String blankToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String formatFiscalYearEnd(Long fiscalYearEnd) {
        if (fiscalYearEnd == null) {
            return null;
        }
        return String.format("%04d", fiscalYearEnd);
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
