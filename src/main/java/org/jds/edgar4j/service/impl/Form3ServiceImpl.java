package org.jds.edgar4j.service.impl;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.jds.edgar4j.integration.Form3Parser;
import org.jds.edgar4j.integration.SecApiClient;
import org.jds.edgar4j.model.Form3;
import org.jds.edgar4j.repository.Form3Repository;
import org.jds.edgar4j.service.Form3Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of Form 3 service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Form3ServiceImpl implements Form3Service {

    private final Form3Repository form3Repository;
    private final Form3Parser form3Parser;
    private final SecApiClient secApiClient;

    @Override
    public CompletableFuture<String> downloadForm3(String cik, String accessionNumber, String primaryDocument) {
        log.info("Downloading Form 3: CIK={}, accession={}, doc={}", cik, accessionNumber, primaryDocument);
        return secApiClient.fetchFilingAsync(cik, accessionNumber, primaryDocument);
    }

    @Override
    public Form3 parse(String xml, String accessionNumber) {
        return form3Parser.parse(xml, accessionNumber);
    }

    @Override
    public CompletableFuture<Form3> downloadAndParse(String cik, String accessionNumber, String primaryDocument) {
        return downloadForm3(cik, accessionNumber, primaryDocument)
                .thenApply(xml -> {
                    if (xml == null || xml.isBlank()) {
                        log.warn("Failed to download Form 3 for accession: {}", accessionNumber);
                        return null;
                    }

                    Form3 form3 = parse(xml, accessionNumber);
                    if (form3 == null) {
                        log.warn("Failed to parse Form 3 for accession: {}", accessionNumber);
                        return null;
                    }

                    form3.setCik(cik);
                    form3.setCreatedAt(new Date());
                    form3.setUpdatedAt(new Date());
                    if (form3.getDocumentType() == null || form3.getDocumentType().isBlank()) {
                        form3.setDocumentType("3");
                    }
                    return form3;
                });
    }

    @Override
    public Form3 save(Form3 form3) {
        if (form3 == null) return null;

        Optional<Form3> existing = form3Repository.findByAccessionNumber(form3.getAccessionNumber());
        if (existing.isPresent()) {
            Form3 current = existing.get();
            form3.setId(current.getId());
            if (form3.getCreatedAt() == null) {
                form3.setCreatedAt(current.getCreatedAt());
            }
        } else if (form3.getCreatedAt() == null) {
            form3.setCreatedAt(new Date());
        }
        form3.setUpdatedAt(new Date());
        return form3Repository.save(form3);
    }

    @Override
    public List<Form3> saveAll(List<Form3> form3List) {
        if (form3List == null || form3List.isEmpty()) {
            return List.of();
        }
        Date now = new Date();
        for (Form3 f : form3List) {
            if (f.getCreatedAt() == null) f.setCreatedAt(now);
            f.setUpdatedAt(now);
        }
        return form3Repository.saveAll(form3List);
    }

    @Override
    public Optional<Form3> findByAccessionNumber(String accessionNumber) {
        return form3Repository.findByAccessionNumber(accessionNumber);
    }

    @Override
    public Optional<Form3> findById(String id) {
        return form3Repository.findById(id);
    }

    @Override
    public Page<Form3> findByCik(String cik, Pageable pageable) {
        return form3Repository.findByCik(cik, pageable);
    }

    @Override
    public Page<Form3> findByTradingSymbol(String tradingSymbol, Pageable pageable) {
        return form3Repository.findByTradingSymbol(tradingSymbol, pageable);
    }

    @Override
    public Page<Form3> findByFiledDateRange(Date startDate, Date endDate, Pageable pageable) {
        return form3Repository.findByFiledDateBetween(startDate, endDate, pageable);
    }

    @Override
    public List<Form3> findRecentFilings(int limit) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "filedDate"));
        return form3Repository.findAll(pageable).getContent();
    }

    @Override
    public boolean existsByAccessionNumber(String accessionNumber) {
        return form3Repository.existsByAccessionNumber(accessionNumber);
    }

    @Override
    public void deleteById(String id) {
        form3Repository.deleteById(id);
    }
}

