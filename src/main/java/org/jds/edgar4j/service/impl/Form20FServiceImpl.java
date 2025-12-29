package org.jds.edgar4j.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.jds.edgar4j.integration.Form20FParser;
import org.jds.edgar4j.integration.SecApiClient;
import org.jds.edgar4j.model.Form20F;
import org.jds.edgar4j.repository.Form20FRepository;
import org.jds.edgar4j.service.Form20FService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of Form 20-F service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Form20FServiceImpl implements Form20FService {

    private final Form20FRepository form20FRepository;
    private final Form20FParser form20FParser;
    private final SecApiClient secApiClient;

    @Override
    public CompletableFuture<String> downloadForm20F(String cik, String accessionNumber, String primaryDocument) {
        log.info("Downloading Form 20-F: CIK={}, accession={}, doc={}", cik, accessionNumber, primaryDocument);
        return secApiClient.fetchFilingAsync(cik, accessionNumber, primaryDocument);
    }

    @Override
    public Form20F parse(String rawDocument, String accessionNumber, String primaryDocument) {
        return form20FParser.parse(rawDocument, accessionNumber, primaryDocument);
    }

    @Override
    public CompletableFuture<Form20F> downloadAndParse(String cik, String accessionNumber, String primaryDocument) {
        return downloadForm20F(cik, accessionNumber, primaryDocument)
                .thenApply(raw -> {
                    if (raw == null || raw.isBlank()) {
                        log.warn("Failed to download Form 20-F for accession: {}", accessionNumber);
                        return null;
                    }

                    Form20F form20F = parse(raw, accessionNumber, primaryDocument);
                    if (form20F == null) {
                        log.warn("Failed to parse Form 20-F for accession: {}", accessionNumber);
                        return null;
                    }

                    Instant now = Instant.now();
                    if (form20F.getCreatedAt() == null) form20F.setCreatedAt(now);
                    form20F.setUpdatedAt(now);
                    form20F.setCik(cik);
                    form20F.setPrimaryDocument(primaryDocument);

                    if (form20F.getFormType() == null || form20F.getFormType().isBlank()) {
                        form20F.setFormType("20-F");
                    }
                    if (form20F.getReportDate() == null && form20F.getDocumentPeriodEndDate() != null) {
                        form20F.setReportDate(form20F.getDocumentPeriodEndDate());
                    }

                    return form20F;
                });
    }

    @Override
    public Form20F save(Form20F form20F) {
        if (form20F == null) return null;

        Optional<Form20F> existing = form20FRepository.findByAccessionNumber(form20F.getAccessionNumber());
        if (existing.isPresent()) {
            Form20F current = existing.get();
            form20F.setId(current.getId());
            if (form20F.getCreatedAt() == null) {
                form20F.setCreatedAt(current.getCreatedAt());
            }
        } else if (form20F.getCreatedAt() == null) {
            form20F.setCreatedAt(Instant.now());
        }
        form20F.setUpdatedAt(Instant.now());
        return form20FRepository.save(form20F);
    }

    @Override
    public List<Form20F> saveAll(List<Form20F> form20FList) {
        if (form20FList == null || form20FList.isEmpty()) {
            return List.of();
        }
        Instant now = Instant.now();
        for (Form20F f : form20FList) {
            if (f.getCreatedAt() == null) f.setCreatedAt(now);
            f.setUpdatedAt(now);
        }
        return form20FRepository.saveAll(form20FList);
    }

    @Override
    public Optional<Form20F> findByAccessionNumber(String accessionNumber) {
        return form20FRepository.findByAccessionNumber(accessionNumber);
    }

    @Override
    public Optional<Form20F> findById(String id) {
        return form20FRepository.findById(id);
    }

    @Override
    public Page<Form20F> findByCik(String cik, Pageable pageable) {
        return form20FRepository.findByCik(cik, pageable);
    }

    @Override
    public Page<Form20F> findByTradingSymbol(String tradingSymbol, Pageable pageable) {
        return form20FRepository.findByTradingSymbol(tradingSymbol, pageable);
    }

    @Override
    public Page<Form20F> findByFiledDateRange(LocalDate startDate, LocalDate endDate, Pageable pageable) {
        return form20FRepository.findByFiledDateBetween(startDate, endDate, pageable);
    }

    @Override
    public List<Form20F> findRecentFilings(int limit) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "filedDate"));
        return form20FRepository.findAll(pageable).getContent();
    }

    @Override
    public boolean existsByAccessionNumber(String accessionNumber) {
        return form20FRepository.existsByAccessionNumber(accessionNumber);
    }

    @Override
    public void deleteById(String id) {
        form20FRepository.deleteById(id);
    }
}

