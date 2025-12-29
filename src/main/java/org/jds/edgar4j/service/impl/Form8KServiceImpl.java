package org.jds.edgar4j.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.jds.edgar4j.integration.Form8KParser;
import org.jds.edgar4j.integration.SecApiClient;
import org.jds.edgar4j.model.Form8K;
import org.jds.edgar4j.repository.Form8KRepository;
import org.jds.edgar4j.service.Form8KService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of Form 8-K service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Form8KServiceImpl implements Form8KService {

    private final Form8KRepository form8KRepository;
    private final Form8KParser form8KParser;
    private final SecApiClient secApiClient;

    @Override
    public CompletableFuture<String> downloadForm8K(String cik, String accessionNumber, String primaryDocument) {
        log.info("Downloading Form 8-K: CIK={}, accession={}, doc={}", cik, accessionNumber, primaryDocument);
        return secApiClient.fetchFilingAsync(cik, accessionNumber, primaryDocument);
    }

    @Override
    public Form8K parse(String rawDocument, String accessionNumber) {
        return form8KParser.parse(rawDocument, accessionNumber);
    }

    @Override
    public CompletableFuture<Form8K> downloadAndParse(String cik, String accessionNumber, String primaryDocument) {
        return downloadForm8K(cik, accessionNumber, primaryDocument)
                .thenApply(raw -> {
                    if (raw == null || raw.isBlank()) {
                        log.warn("Failed to download Form 8-K for accession: {}", accessionNumber);
                        return null;
                    }

                    Form8K form8K = parse(raw, accessionNumber);
                    if (form8K == null) {
                        log.warn("Failed to parse Form 8-K for accession: {}", accessionNumber);
                        return null;
                    }

                    Instant now = Instant.now();
                    form8K.setCik(cik);
                    form8K.setPrimaryDocument(primaryDocument);
                    if (form8K.getCreatedAt() == null) form8K.setCreatedAt(now);
                    form8K.setUpdatedAt(now);
                    if (form8K.getFormType() == null || form8K.getFormType().isBlank()) {
                        form8K.setFormType("8-K");
                    }
                    return form8K;
                });
    }

    @Override
    public Form8K save(Form8K form8K) {
        if (form8K == null) return null;

        Optional<Form8K> existing = form8KRepository.findByAccessionNumber(form8K.getAccessionNumber());
        if (existing.isPresent()) {
            Form8K current = existing.get();
            form8K.setId(current.getId());
            if (form8K.getCreatedAt() == null) {
                form8K.setCreatedAt(current.getCreatedAt());
            }
        } else if (form8K.getCreatedAt() == null) {
            form8K.setCreatedAt(Instant.now());
        }
        form8K.setUpdatedAt(Instant.now());

        return form8KRepository.save(form8K);
    }

    @Override
    public List<Form8K> saveAll(List<Form8K> form8KList) {
        if (form8KList == null || form8KList.isEmpty()) {
            return List.of();
        }
        Instant now = Instant.now();
        for (Form8K f : form8KList) {
            if (f.getCreatedAt() == null) f.setCreatedAt(now);
            f.setUpdatedAt(now);
        }
        return form8KRepository.saveAll(form8KList);
    }

    @Override
    public Optional<Form8K> findByAccessionNumber(String accessionNumber) {
        return form8KRepository.findByAccessionNumber(accessionNumber);
    }

    @Override
    public Optional<Form8K> findById(String id) {
        return form8KRepository.findById(id);
    }

    @Override
    public Page<Form8K> findByCik(String cik, Pageable pageable) {
        return form8KRepository.findByCik(cik, pageable);
    }

    @Override
    public Page<Form8K> findByTradingSymbol(String tradingSymbol, Pageable pageable) {
        return form8KRepository.findByTradingSymbol(tradingSymbol, pageable);
    }

    @Override
    public Page<Form8K> findByFiledDateRange(LocalDate startDate, LocalDate endDate, Pageable pageable) {
        return form8KRepository.findByFiledDateBetween(startDate, endDate, pageable);
    }

    @Override
    public List<Form8K> findRecentFilings(int limit) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "filedDate"));
        return form8KRepository.findAll(pageable).getContent();
    }

    @Override
    public boolean existsByAccessionNumber(String accessionNumber) {
        return form8KRepository.existsByAccessionNumber(accessionNumber);
    }

    @Override
    public void deleteById(String id) {
        form8KRepository.deleteById(id);
    }
}

