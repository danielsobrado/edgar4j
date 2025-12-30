package org.jds.edgar4j.service.impl;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.jds.edgar4j.integration.Form4Parser;
import org.jds.edgar4j.model.Form4;
import org.jds.edgar4j.repository.Form4Repository;
import org.jds.edgar4j.service.Form4Service;
import org.jds.edgar4j.service.SettingsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of Form4Service for SEC Form 4 filing operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Form4ServiceImpl implements Form4Service {

    private final Form4Repository form4Repository;
    private final Form4Parser form4Parser;
    private final SettingsService settingsService;

    @Value("${edgar4j.urls.edgarDataArchivesUrl}")
    private String edgarDataArchivesUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    @Override
    public CompletableFuture<HttpResponse<String>> downloadForm4(String cik, String accessionNumber, String primaryDocument) {
        String formUrl = buildFormUrl(cik, accessionNumber, primaryDocument);
        log.debug("Downloading Form 4 from: {}", formUrl);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(formUrl))
                .header("User-Agent", settingsService.getUserAgent())
                .header("Accept", "application/xml, text/xml, */*")
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .whenComplete((response, error) -> {
                    if (error != null) {
                        log.error("Failed to download Form 4: {}", formUrl, error);
                    } else {
                        log.debug("Downloaded Form 4, status: {}", response.statusCode());
                    }
                });
    }

    @Override
    public Form4 parseForm4(String xml, String accessionNumber) {
        if (xml == null || xml.isBlank()) {
            log.warn("Empty XML content for accession: {}", accessionNumber);
            return null;
        }

        try {
            Form4 form4 = form4Parser.parse(xml, accessionNumber);
            if (form4 != null) {
                Instant now = Instant.now();
                form4.setCreatedAt(now);
                form4.setUpdatedAt(now);
            }
            return form4;
        } catch (Exception e) {
            log.error("Failed to parse Form 4 for accession: {}", accessionNumber, e);
            return null;
        }
    }

    @Override
    public CompletableFuture<Form4> downloadAndParseForm4(String cik, String accessionNumber, String primaryDocument) {
        return downloadForm4(cik, accessionNumber, primaryDocument)
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        log.warn("Non-200 response for Form 4: {} - {}", accessionNumber, response.statusCode());
                        return null;
                    }
                    return parseForm4(response.body(), accessionNumber);
                })
                .exceptionally(e -> {
                    log.error("Error downloading/parsing Form 4: {}", accessionNumber, e);
                    return null;
                });
    }

    @Override
    public Form4 save(Form4 form4) {
        if (form4 == null) {
            return null;
        }

        // Check for existing record by accession number
        Optional<Form4> existing = form4Repository.findByAccessionNumber(form4.getAccessionNumber());
        if (existing.isPresent()) {
            Form4 existingForm4 = existing.get();
            form4.setId(existingForm4.getId());
            form4.setCreatedAt(existingForm4.getCreatedAt());
            log.debug("Updating existing Form 4: {}", form4.getAccessionNumber());
        }

        form4.setUpdatedAt(Instant.now());

        try {
            Form4 saved = form4Repository.save(form4);
            log.info("Saved Form 4: {} for {} ({})",
                    saved.getAccessionNumber(),
                    saved.getTradingSymbol(),
                    saved.getRptOwnerName());
            return saved;
        } catch (Exception e) {
            log.error("Failed to save Form 4: {}", form4.getAccessionNumber(), e);
            throw e;
        }
    }

    @Override
    public List<Form4> saveAll(List<Form4> form4List) {
        if (form4List == null || form4List.isEmpty()) {
            return List.of();
        }

        Instant now = Instant.now();
        form4List.forEach(f -> {
            if (f.getCreatedAt() == null) {
                f.setCreatedAt(now);
            }
            f.setUpdatedAt(now);
        });

        try {
            List<Form4> saved = form4Repository.saveAll(form4List);
            log.info("Saved {} Form 4 filings", saved.size());
            return saved;
        } catch (Exception e) {
            log.error("Failed to save Form 4 batch", e);
            throw e;
        }
    }

    @Override
    public Optional<Form4> findByAccessionNumber(String accessionNumber) {
        return form4Repository.findByAccessionNumber(accessionNumber);
    }

    @Override
    public Optional<Form4> findById(String id) {
        return form4Repository.findById(id);
    }

    @Override
    public Page<Form4> findByTradingSymbol(String tradingSymbol, Pageable pageable) {
        return form4Repository.findByTradingSymbol(tradingSymbol, pageable);
    }

    @Override
    public Page<Form4> findByCik(String cik, Pageable pageable) {
        return form4Repository.findByCik(cik, pageable);
    }

    @Override
    public List<Form4> findByOwnerName(String ownerName) {
        return form4Repository.findByRptOwnerNameContainingIgnoreCase(ownerName);
    }

    @Override
    public Page<Form4> findByDateRange(LocalDate startDate, LocalDate endDate, Pageable pageable) {
        return form4Repository.findByTransactionDateBetween(startDate, endDate, pageable);
    }

    @Override
    public Page<Form4> findBySymbolAndDateRange(String symbol, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        return form4Repository.findBySymbolAndDateRange(symbol, startDate, endDate, pageable);
    }

    @Override
    public List<Form4> findRecentFilings(int limit) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "transactionDate"));
        return form4Repository.findAll(pageable).getContent();
    }

    @Override
    public boolean existsByAccessionNumber(String accessionNumber) {
        return form4Repository.findByAccessionNumber(accessionNumber).isPresent();
    }

    @Override
    public void deleteById(String id) {
        form4Repository.deleteById(id);
        log.info("Deleted Form 4: {}", id);
    }

    @Override
    public InsiderStats getInsiderStats(String tradingSymbol, LocalDate startDate, LocalDate endDate) {
        List<Form4> filings = form4Repository.findByTradingSymbolAndTransactionDateBetween(
                tradingSymbol, startDate, endDate);

        long buys = filings.stream()
                .filter(Form4::isBuy)
                .count();

        long sells = filings.stream()
                .filter(Form4::isSell)
                .count();

        double buyValue = filings.stream()
                .filter(Form4::isBuy)
                .filter(f -> f.getTotalBuyValue() != null)
                .mapToDouble(Form4::getTotalBuyValue)
                .sum();

        double sellValue = filings.stream()
                .filter(Form4::isSell)
                .filter(f -> f.getTotalSellValue() != null)
                .mapToDouble(Form4::getTotalSellValue)
                .sum();

        long directorTx = filings.stream()
                .filter(Form4::isDirector)
                .count();

        long officerTx = filings.stream()
                .filter(Form4::isOfficer)
                .count();

        long tenPercentTx = filings.stream()
                .filter(Form4::isTenPercentOwner)
                .count();

        return new InsiderStats(buys, sells, buyValue, sellValue, directorTx, officerTx, tenPercentTx);
    }

    private String buildFormUrl(String cik, String accessionNumber, String primaryDocument) {
        String cleanAccession = accessionNumber.replace("-", "");
        return String.format("%s/%s/%s/%s",
                edgarDataArchivesUrl,
                cik,
                cleanAccession,
                primaryDocument);
    }
}
