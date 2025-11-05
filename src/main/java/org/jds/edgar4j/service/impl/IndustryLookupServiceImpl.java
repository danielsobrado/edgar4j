package org.jds.edgar4j.service.impl;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.DecimalFormat;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.jds.edgar4j.model.Company;
import org.jds.edgar4j.model.Submissions;
import org.jds.edgar4j.service.IndustryLookupService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Industry lookup service implementation
 * Fetches company data from SEC EDGAR and caches results
 *
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-11-05
 */
@Slf4j
@Service
public class IndustryLookupServiceImpl implements IndustryLookupService {

    @Value("${edgar4j.urls.submissionsCIKUrl}")
    private String submissionsCIKUrl;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Map<String, Company> cache;
    private final DecimalFormat cikFormatter;

    public IndustryLookupServiceImpl() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.objectMapper = new ObjectMapper();
        this.cache = new ConcurrentHashMap<>();
        this.cikFormatter = new DecimalFormat("0000000000");
    }

    @Override
    public Optional<Company> getCompanyByCik(String cik) {
        if (cik == null || cik.trim().isEmpty()) {
            return Optional.empty();
        }

        // Clean CIK (remove leading zeros, spaces, etc.)
        String cleanedCik = cik.trim().replaceAll("^0+", "");
        if (cleanedCik.isEmpty()) {
            cleanedCik = "0";
        }

        // Check cache first
        Company cached = cache.get(cleanedCik);
        if (cached != null) {
            log.debug("Cache hit for CIK: {}", cleanedCik);
            return Optional.of(cached);
        }

        // Fetch from SEC API
        try {
            log.info("Fetching company data for CIK: {}", cleanedCik);

            long cikLong = Long.parseLong(cleanedCik);
            String formattedCik = cikFormatter.format(cikLong);
            String url = submissionsCIKUrl + formattedCik + ".json";

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "edgar4j/1.0")
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Submissions submissions = objectMapper.readValue(response.body(), Submissions.class);

                Company company = Company.builder()
                    .cik(cleanedCik)
                    .name(submissions.getName())
                    .description(submissions.getDescription())
                    .sic(submissions.getSic())
                    .industry(submissions.getSicDescription())
                    .entityType(submissions.getEntityType())
                    .stateOfIncorporation(submissions.getStateOfIncorporation())
                    .build();

                // Add ticker if available
                if (submissions.getTickers() != null && submissions.getTickers().length > 0) {
                    company.setTicker(submissions.getTickers()[0].getTicker());
                }

                // Cache the result
                cache.put(cleanedCik, company);

                log.info("Successfully fetched company data for CIK: {} - {} ({})",
                    cleanedCik, company.getName(), company.getIndustry());

                return Optional.of(company);

            } else if (response.statusCode() == 404) {
                log.warn("Company not found for CIK: {}", cleanedCik);
                return Optional.empty();

            } else {
                log.error("Failed to fetch company data for CIK: {}, status: {}",
                    cleanedCik, response.statusCode());
                return Optional.empty();
            }

        } catch (NumberFormatException e) {
            log.error("Invalid CIK format: {}", cik, e);
            return Optional.empty();

        } catch (Exception e) {
            log.error("Error fetching company data for CIK: {}", cleanedCik, e);
            return Optional.empty();
        }
    }

    @Override
    public String getIndustryByCik(String cik) {
        return getCompanyByCik(cik)
            .map(Company::getIndustry)
            .orElse(null);
    }

    @Override
    public String getSicCodeByCik(String cik) {
        return getCompanyByCik(cik)
            .map(Company::getSic)
            .orElse(null);
    }

    @Override
    public void clearCache() {
        log.info("Clearing industry lookup cache ({} entries)", cache.size());
        cache.clear();
    }

    /**
     * Get current cache size (for monitoring/testing)
     * @return number of cached companies
     */
    public int getCacheSize() {
        return cache.size();
    }
}
