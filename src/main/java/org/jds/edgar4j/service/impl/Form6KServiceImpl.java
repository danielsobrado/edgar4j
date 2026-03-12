package org.jds.edgar4j.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.jds.edgar4j.integration.Form6KParser;
import org.jds.edgar4j.integration.SecApiClient;
import org.jds.edgar4j.model.Form6K;
import org.jds.edgar4j.repository.Form6KRepository;
import org.jds.edgar4j.service.Form6KService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of Form 6-K service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Form6KServiceImpl implements Form6KService {

    private final Form6KRepository form6KRepository;
    private final Form6KParser form6KParser;
    private final SecApiClient secApiClient;

    @Override
    public CompletableFuture<String> downloadForm6K(String cik, String accessionNumber, String primaryDocument) {
        log.info("Downloading Form 6-K: CIK={}, accession={}, doc={}", cik, accessionNumber, primaryDocument);
        return secApiClient.fetchFilingAsync(cik, accessionNumber, primaryDocument);
    }

    @Override
    public Form6K parse(String rawDocument, String accessionNumber) {
        return form6KParser.parse(rawDocument, accessionNumber);
    }

    @Override
    public CompletableFuture<Form6K> downloadAndParse(String cik, String accessionNumber, String primaryDocument) {
        return downloadForm6K(cik, accessionNumber, primaryDocument)
                .thenApply(raw -> {
                    if (raw == null || raw.isBlank()) {
                        log.warn("Failed to download Form 6-K for accession: {}", accessionNumber);
                        return null;
                    }

                    Form6K form6K = parse(raw, accessionNumber);
                    if (form6K == null) {
                        log.warn("Failed to parse Form 6-K for accession: {}", accessionNumber);
                        return null;
                    }

                    Instant now = Instant.now();
                    form6K.setCik(cik);
                    form6K.setPrimaryDocument(primaryDocument);
                    if (form6K.getCreatedAt() == null) form6K.setCreatedAt(now);
                    form6K.setUpdatedAt(now);
                    if (form6K.getFormType() == null || form6K.getFormType().isBlank()) {
                        form6K.setFormType("6-K");
                    }
                    return form6K;
                });
    }

    @Override
    public Form6K save(Form6K form6K) {
        if (form6K == null) return null;

        Optional<Form6K> existing = form6KRepository.findByAccessionNumber(form6K.getAccessionNumber());
        if (existing.isPresent()) {
            Form6K current = existing.get();
            form6K.setId(current.getId());
            if (form6K.getCreatedAt() == null) {
                form6K.setCreatedAt(current.getCreatedAt());
            }
        } else if (form6K.getCreatedAt() == null) {
            form6K.setCreatedAt(Instant.now());
        }
        form6K.setUpdatedAt(Instant.now());

        return form6KRepository.save(form6K);
    }

    @Override
    public List<Form6K> saveAll(List<Form6K> form6KList) {
        if (form6KList == null || form6KList.isEmpty()) {
            return List.of();
        }
        Instant now = Instant.now();
        for (Form6K f : form6KList) {
            if (f.getCreatedAt() == null) f.setCreatedAt(now);
            f.setUpdatedAt(now);
        }
        return form6KRepository.saveAll(form6KList);
    }

    @Override
    public Optional<Form6K> findByAccessionNumber(String accessionNumber) {
        return form6KRepository.findByAccessionNumber(accessionNumber);
    }

    @Override
    public Optional<Form6K> findById(String id) {
        return form6KRepository.findById(id);
    }

    @Override
    public Page<Form6K> findByCik(String cik, Pageable pageable) {
        return form6KRepository.findByCik(cik, pageable);
    }

    @Override
    public Page<Form6K> findByTradingSymbol(String tradingSymbol, Pageable pageable) {
        return form6KRepository.findByTradingSymbol(tradingSymbol, pageable);
    }

    @Override
    public Page<Form6K> findByFiledDateRange(LocalDate startDate, LocalDate endDate, Pageable pageable) {
        return form6KRepository.findByFiledDateBetween(startDate, endDate, pageable);
    }

    @Override
    public List<Form6K> findRecentFilings(int limit) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "filedDate"));
        return form6KRepository.findAll(pageable).getContent();
    }

    @Override
    public boolean existsByAccessionNumber(String accessionNumber) {
        return form6KRepository.existsByAccessionNumber(accessionNumber);
    }

    @Override
    public void deleteById(String id) {
        form6KRepository.deleteById(id);
    }
}

