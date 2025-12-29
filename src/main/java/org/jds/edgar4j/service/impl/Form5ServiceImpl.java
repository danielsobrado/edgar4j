package org.jds.edgar4j.service.impl;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.jds.edgar4j.integration.Form5Parser;
import org.jds.edgar4j.integration.SecApiClient;
import org.jds.edgar4j.model.Form5;
import org.jds.edgar4j.repository.Form5Repository;
import org.jds.edgar4j.service.Form5Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of Form 5 service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Form5ServiceImpl implements Form5Service {

    private final Form5Repository form5Repository;
    private final Form5Parser form5Parser;
    private final SecApiClient secApiClient;

    @Override
    public CompletableFuture<String> downloadForm5(String cik, String accessionNumber, String primaryDocument) {
        log.info("Downloading Form 5: CIK={}, accession={}, doc={}", cik, accessionNumber, primaryDocument);
        return secApiClient.fetchFilingAsync(cik, accessionNumber, primaryDocument);
    }

    @Override
    public Form5 parse(String xml, String accessionNumber) {
        return form5Parser.parse(xml, accessionNumber);
    }

    @Override
    public CompletableFuture<Form5> downloadAndParse(String cik, String accessionNumber, String primaryDocument) {
        return downloadForm5(cik, accessionNumber, primaryDocument)
                .thenApply(xml -> {
                    if (xml == null || xml.isBlank()) {
                        log.warn("Failed to download Form 5 for accession: {}", accessionNumber);
                        return null;
                    }

                    Form5 form5 = parse(xml, accessionNumber);
                    if (form5 == null) {
                        log.warn("Failed to parse Form 5 for accession: {}", accessionNumber);
                        return null;
                    }

                    form5.setCik(cik);
                    form5.setCreatedAt(new Date());
                    form5.setUpdatedAt(new Date());
                    if (form5.getDocumentType() == null || form5.getDocumentType().isBlank()) {
                        form5.setDocumentType("5");
                    }
                    return form5;
                });
    }

    @Override
    public Form5 save(Form5 form5) {
        if (form5 == null) return null;

        Optional<Form5> existing = form5Repository.findByAccessionNumber(form5.getAccessionNumber());
        if (existing.isPresent()) {
            Form5 current = existing.get();
            form5.setId(current.getId());
            if (form5.getCreatedAt() == null) {
                form5.setCreatedAt(current.getCreatedAt());
            }
        } else if (form5.getCreatedAt() == null) {
            form5.setCreatedAt(new Date());
        }
        form5.setUpdatedAt(new Date());
        return form5Repository.save(form5);
    }

    @Override
    public List<Form5> saveAll(List<Form5> form5List) {
        if (form5List == null || form5List.isEmpty()) {
            return List.of();
        }
        Date now = new Date();
        for (Form5 f : form5List) {
            if (f.getCreatedAt() == null) f.setCreatedAt(now);
            f.setUpdatedAt(now);
        }
        return form5Repository.saveAll(form5List);
    }

    @Override
    public Optional<Form5> findByAccessionNumber(String accessionNumber) {
        return form5Repository.findByAccessionNumber(accessionNumber);
    }

    @Override
    public Optional<Form5> findById(String id) {
        return form5Repository.findById(id);
    }

    @Override
    public Page<Form5> findByCik(String cik, Pageable pageable) {
        return form5Repository.findByCik(cik, pageable);
    }

    @Override
    public Page<Form5> findByTradingSymbol(String tradingSymbol, Pageable pageable) {
        return form5Repository.findByTradingSymbol(tradingSymbol, pageable);
    }

    @Override
    public Page<Form5> findByFiledDateRange(Date startDate, Date endDate, Pageable pageable) {
        return form5Repository.findByFiledDateBetween(startDate, endDate, pageable);
    }

    @Override
    public List<Form5> findRecentFilings(int limit) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "filedDate"));
        return form5Repository.findAll(pageable).getContent();
    }

    @Override
    public boolean existsByAccessionNumber(String accessionNumber) {
        return form5Repository.existsByAccessionNumber(accessionNumber);
    }

    @Override
    public void deleteById(String id) {
        form5Repository.deleteById(id);
    }
}

