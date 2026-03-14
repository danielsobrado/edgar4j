package org.jds.edgar4j.adapter.file;

import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.model.Filling;
import org.jds.edgar4j.port.FillingDataPort;
import org.jds.edgar4j.storage.file.FileFormat;
import org.jds.edgar4j.storage.file.FileStorageEngine;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@Profile("resource-low")
public class FillingFileAdapter extends AbstractFileDataPort<Filling> implements FillingDataPort {

    private static final String INDEX_ACCESSION_NUMBER = "accessionNumber";
    private static final String INDEX_CIK = "cik";

    public FillingFileAdapter(FileStorageEngine storageEngine) {
        super(storageEngine.registerCollection(
                "fillings",
                Filling.class,
                FileFormat.JSONL,
                Filling::getId,
                Filling::setId));
        registerExactIndex(INDEX_ACCESSION_NUMBER, Filling::getAccessionNumber);
        registerExactIndex(INDEX_CIK, Filling::getCik);
    }

    @Override
    public Optional<Filling> findByAccessionNumber(String accessionNumber) {
        return findFirstByIndex(INDEX_ACCESSION_NUMBER, accessionNumber);
    }

    @Override
    public Page<Filling> findByCik(String cik, Pageable pageable) {
        return org.jds.edgar4j.storage.file.FilePageSupport.page(findAllByIndex(INDEX_CIK, cik), pageable);
    }

    @Override
    public Page<Filling> findByCompany(String company, Pageable pageable) {
        return findMatching(value -> company != null && company.equals(value.getCompany()), pageable);
    }

    @Override
    public Page<Filling> findByFormTypeNumber(String formTypeNumber, Pageable pageable) {
        return findMatching(value -> matchesFormType(value, formTypeNumber), pageable);
    }

    @Override
    public Page<Filling> findByCikAndFormType(String cik, String formTypeNumber, Pageable pageable) {
        return findMatching(value -> cik != null && cik.equals(value.getCik()) && matchesFormType(value, formTypeNumber), pageable);
    }

    @Override
    public Page<Filling> findByCikAndFormTypeIn(String cik, List<String> formTypeNumbers, Pageable pageable) {
        return findMatching(value -> cik != null
                && cik.equals(value.getCik())
                && formTypeNumbers != null
                && formTypeNumbers.stream().anyMatch(type -> matchesFormType(value, type)), pageable);
    }

    @Override
    public Page<Filling> findByCikAndFormTypeAndFillingDateBetween(
            String cik,
            String formTypeNumber,
            Date startDate,
            Date endDate,
            Pageable pageable) {
        return findMatching(value -> cik != null
                && cik.equals(value.getCik())
                && matchesFormType(value, formTypeNumber)
                && between(value.getFillingDate(), startDate, endDate), pageable);
    }

    @Override
    public Page<Filling> findByCikIn(List<String> ciks, Pageable pageable) {
        return findMatching(value -> ciks != null && ciks.contains(value.getCik()), pageable);
    }

    @Override
    public Page<Filling> findByCikInAndFormType(List<String> ciks, String formTypeNumber, Pageable pageable) {
        return findMatching(value -> ciks != null && ciks.contains(value.getCik()) && matchesFormType(value, formTypeNumber), pageable);
    }

    @Override
    public Page<Filling> findByCikInAndFormTypeIn(List<String> ciks, List<String> formTypeNumbers, Pageable pageable) {
        return findMatching(value -> ciks != null
                && ciks.contains(value.getCik())
                && formTypeNumbers != null
                && formTypeNumbers.stream().anyMatch(type -> matchesFormType(value, type)), pageable);
    }

    @Override
    public Page<Filling> searchFillings(Date startDate, Date endDate, List<String> formTypes, Pageable pageable) {
        return findMatching(value -> between(value.getFillingDate(), startDate, endDate)
                && formTypes != null
                && formTypes.stream().anyMatch(type -> matchesFormType(value, type)), pageable);
    }

    @Override
    public Page<Filling> searchByCompanyOrCik(String searchTerm, Pageable pageable) {
        return findMatching(value -> (value.getCompany() != null && containsIgnoreCase(value.getCompany(), searchTerm))
                || (searchTerm != null && searchTerm.equals(value.getCik())), pageable);
    }

    @Override
    public Page<Filling> findAllByOrderByFillingDateDesc(Pageable pageable) {
        List<Filling> sorted = findAll().stream()
                .sorted(Comparator.comparing(Filling::getFillingDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        if (pageable == null || pageable.isUnpaged()) {
            return new PageImpl<>(sorted);
        }

        int start = Math.toIntExact(Math.min((long) pageable.getPageNumber() * pageable.getPageSize(), sorted.size()));
        int end = Math.min(start + pageable.getPageSize(), sorted.size());
        return new PageImpl<>(sorted.subList(start, end), pageable, sorted.size());
    }

    @Override
    public List<Filling> findTop10ByOrderByFillingDateDesc() {
        return findAll().stream()
                .sorted(Comparator.comparing(Filling::getFillingDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .toList();
    }

    @Override
    public long countByFormTypeNumber(String formTypeNumber) {
        return count(value -> matchesFormType(value, formTypeNumber));
    }

    @Override
    public Page<Filling> findRecentXbrlFilingsByCik(String cik, Pageable pageable) {
        return findMatching(value -> cik != null
                && cik.equals(value.getCik())
                && (value.isXBRL() || value.isInlineXBRL()), pageable);
    }

    private boolean matchesFormType(Filling filling, String formTypeNumber) {
        return filling.getFormType() != null
                && formTypeNumber != null
                && formTypeNumber.equalsIgnoreCase(filling.getFormType().getNumber());
    }

    private boolean between(Date value, Date startDate, Date endDate) {
        return value != null
                && !value.before(startDate)
                && !value.after(endDate);
    }
}
