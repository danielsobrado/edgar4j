package org.jds.edgar4j.adapter.file;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.jds.edgar4j.model.Form13DG;
import org.jds.edgar4j.port.Form13DGDataPort;
import org.jds.edgar4j.repository.Form13DGRepository.BeneficialOwnerSummary;
import org.jds.edgar4j.repository.Form13DGRepository.OwnerPortfolioEntry;
import org.jds.edgar4j.repository.Form13DGRepository.OwnershipHistoryEntry;
import org.jds.edgar4j.repository.Form13DGRepository.ScheduleTypeCount;
import org.jds.edgar4j.storage.file.FilePageSupport;
import org.jds.edgar4j.storage.file.FileFormat;
import org.jds.edgar4j.storage.file.FileStorageEngine;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@Profile("resource-low")
public class Form13DGFileAdapter extends AbstractFileDataPort<Form13DG> implements Form13DGDataPort {

    private static final Comparator<LocalDate> LOCAL_DATE_DESC = Comparator.nullsLast(Comparator.reverseOrder());
    private static final String INDEX_ACCESSION_NUMBER = "accessionNumber";
    private static final String INDEX_FORM_TYPE = "formType";
    private static final String INDEX_SCHEDULE_TYPE = "scheduleType";
    private static final String INDEX_ISSUER_CIK = "issuerCik";
    private static final String INDEX_CUSIP = "cusip";
    private static final String INDEX_FILING_PERSON_CIK = "filingPersonCik";
    private static final String INDEX_AMENDMENT_TYPE = "amendmentType";

    public Form13DGFileAdapter(FileStorageEngine storageEngine) {
        super(storageEngine.registerCollection(
                "form13dg",
                Form13DG.class,
                FileFormat.JSONL,
                Form13DG::getId,
                Form13DG::setId));
        registerExactIndex(INDEX_ACCESSION_NUMBER, Form13DG::getAccessionNumber);
        registerIgnoreCaseIndex(INDEX_FORM_TYPE, Form13DG::getFormType);
        registerIgnoreCaseIndex(INDEX_SCHEDULE_TYPE, Form13DG::getScheduleType);
        registerExactIndex(INDEX_ISSUER_CIK, Form13DG::getIssuerCik);
        registerExactIndex(INDEX_CUSIP, Form13DG::getCusip);
        registerExactIndex(INDEX_FILING_PERSON_CIK, Form13DG::getFilingPersonCik);
        registerIgnoreCaseIndex(INDEX_AMENDMENT_TYPE, Form13DG::getAmendmentType);
    }

    @Override
    public Optional<Form13DG> findByAccessionNumber(String accessionNumber) {
        return findFirstByIndex(INDEX_ACCESSION_NUMBER, accessionNumber);
    }

    @Override
    public Page<Form13DG> findByFormType(String formType, Pageable pageable) {
        return FilePageSupport.page(findAllByIndex(INDEX_FORM_TYPE, formType), pageable);
    }

    @Override
    public Page<Form13DG> findByScheduleType(String scheduleType, Pageable pageable) {
        return FilePageSupport.page(findAllByIndex(INDEX_SCHEDULE_TYPE, scheduleType), pageable);
    }

    @Override
    public Page<Form13DG> findByIssuerCik(String issuerCik, Pageable pageable) {
        return FilePageSupport.page(findAllByIndex(INDEX_ISSUER_CIK, issuerCik), pageable);
    }

    @Override
    public Page<Form13DG> findByIssuerNameContainingIgnoreCase(String issuerName, Pageable pageable) {
        return findMatching(value -> containsIgnoreCase(value.getIssuerName(), issuerName), pageable);
    }

    @Override
    public List<Form13DG> findByCusip(String cusip) {
        return findAllByIndex(INDEX_CUSIP, cusip);
    }

    @Override
    public Page<Form13DG> findByCusip(String cusip, Pageable pageable) {
        return FilePageSupport.page(findAllByIndex(INDEX_CUSIP, cusip), pageable);
    }

    @Override
    public List<Form13DG> findByFilingPersonCik(String filingPersonCik) {
        return findAllByIndex(INDEX_FILING_PERSON_CIK, filingPersonCik);
    }

    @Override
    public Page<Form13DG> findByFilingPersonCik(String filingPersonCik, Pageable pageable) {
        return FilePageSupport.page(findAllByIndex(INDEX_FILING_PERSON_CIK, filingPersonCik), pageable);
    }

    @Override
    public Page<Form13DG> findByFilingPersonNameContainingIgnoreCase(String filingPersonName, Pageable pageable) {
        return findMatching(value -> containsIgnoreCase(value.getFilingPersonName(), filingPersonName), pageable);
    }

    @Override
    public Page<Form13DG> findByEventDateBetween(LocalDate startDate, LocalDate endDate, Pageable pageable) {
        return findMatching(value -> between(value.getEventDate(), startDate, endDate), pageable);
    }

    @Override
    public Page<Form13DG> findByFiledDateBetween(LocalDate startDate, LocalDate endDate, Pageable pageable) {
        return findMatching(value -> between(value.getFiledDate(), startDate, endDate), pageable);
    }

    @Override
    public Page<Form13DG> findByMinPercentOfClass(Double minPercent, Pageable pageable) {
        return findMatching(value -> value.getPercentOfClass() != null && minPercent != null && value.getPercentOfClass() >= minPercent, pageable);
    }

    @Override
    public Page<Form13DG> findTenPercentOwners(Pageable pageable) {
        return findMatching(value -> value.getPercentOfClass() != null && value.getPercentOfClass() >= 10d, pageable);
    }

    @Override
    public Page<Form13DG> findByMinSharesBeneficiallyOwned(Long minShares, Pageable pageable) {
        return findMatching(value -> value.getSharesBeneficiallyOwned() != null && minShares != null && value.getSharesBeneficiallyOwned() >= minShares, pageable);
    }

    @Override
    public long countByScheduleType(String scheduleType) {
        return countByIndex(INDEX_SCHEDULE_TYPE, scheduleType);
    }

    @Override
    public boolean existsByAccessionNumber(String accessionNumber) {
        return existsByIndex(INDEX_ACCESSION_NUMBER, accessionNumber);
    }

    @Override
    public Page<Form13DG> findAmendments(Pageable pageable) {
        return FilePageSupport.page(findAllByIndex(INDEX_AMENDMENT_TYPE, "AMENDMENT"), pageable);
    }

    @Override
    public Page<Form13DG> findInitialFilings(Pageable pageable) {
        return FilePageSupport.page(findAllByIndex(INDEX_AMENDMENT_TYPE, "INITIAL"), pageable);
    }

    @Override
    public List<BeneficialOwnerSummary> getTopBeneficialOwnersByCusip(String cusip, int limit) {
        Map<String, Form13DG> latestByOwner = latestByKey(
                findByCusip(cusip),
                Form13DG::getFilingPersonCik,
                Form13DG::getEventDate);

        return latestByOwner.values().stream()
                .sorted(Comparator.comparing(Form13DG::getPercentOfClass, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .map(value -> new BeneficialOwnerSummaryView(
                        value.getFilingPersonCik(),
                        value.getFilingPersonName(),
                        value.getPercentOfClass(),
                        value.getSharesBeneficiallyOwned(),
                        value.getEventDate(),
                        value.getScheduleType()))
                .map(BeneficialOwnerSummary.class::cast)
                .toList();
    }

    @Override
    public List<OwnershipHistoryEntry> getOwnershipHistory(String filingPersonCik, String issuerCik) {
        return findAllByIndex(INDEX_FILING_PERSON_CIK, filingPersonCik).stream()
            .filter(value -> issuerCik != null && issuerCik.equals(value.getIssuerCik()))
                .sorted(Comparator.comparing(Form13DG::getEventDate, LOCAL_DATE_DESC))
                .map(value -> new OwnershipHistoryEntryView(
                        value.getEventDate(),
                        value.getPercentOfClass(),
                        value.getSharesBeneficiallyOwned(),
                        value.getFormType(),
                        value.getAmendmentType()))
                .map(OwnershipHistoryEntry.class::cast)
                .toList();
    }

    @Override
    public List<ScheduleTypeCount> countByScheduleTypeInDateRange(LocalDate startDate, LocalDate endDate) {
        Map<String, Long> counts = findMatching(value -> between(value.getFiledDate(), startDate, endDate)).stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        value -> value.getScheduleType() != null ? value.getScheduleType() : "UNKNOWN",
                        LinkedHashMap::new,
                        java.util.stream.Collectors.counting()));

        return counts.entrySet().stream()
                .map(entry -> new ScheduleTypeCountView(entry.getKey(), entry.getValue()))
                .map(ScheduleTypeCount.class::cast)
                .toList();
    }

    @Override
    public List<Form13DG> getRecentActivistFilings(int limit) {
        return findAllByIndex(INDEX_SCHEDULE_TYPE, "13D").stream()
            .filter(value -> value.getPurposeOfTransaction() != null)
                .sorted(Comparator.comparing(Form13DG::getEventDate, LOCAL_DATE_DESC))
                .limit(limit)
                .toList();
    }

    @Override
    public List<OwnerPortfolioEntry> getOwnerPortfolio(String filingPersonCik) {
        Map<String, Form13DG> latestByIssuer = latestByKey(
                findByFilingPersonCik(filingPersonCik),
                Form13DG::getIssuerCik,
                Form13DG::getEventDate);

        return latestByIssuer.values().stream()
                .map(value -> new OwnerPortfolioEntryView(
                        value.getIssuerCik(),
                        value.getIssuerName(),
                        value.getCusip(),
                        value.getPercentOfClass(),
                        value.getSharesBeneficiallyOwned(),
                        value.getEventDate(),
                        value.getScheduleType()))
                .map(OwnerPortfolioEntry.class::cast)
                .toList();
    }

    private boolean between(LocalDate value, LocalDate startDate, LocalDate endDate) {
        return value != null && !value.isBefore(startDate) && !value.isAfter(endDate);
    }

    private Map<String, Form13DG> latestByKey(
            List<Form13DG> filings,
            Function<Form13DG, String> keyExtractor,
            Function<Form13DG, LocalDate> dateExtractor) {
        Map<String, Form13DG> latestByKey = new LinkedHashMap<>();
        for (Form13DG filing : filings) {
            String key = keyExtractor.apply(filing);
            if (key == null) {
                continue;
            }
            Form13DG existing = latestByKey.get(key);
            if (existing == null) {
                latestByKey.put(key, filing);
                continue;
            }
            LocalDate existingDate = dateExtractor.apply(existing);
            LocalDate newDate = dateExtractor.apply(filing);
            if (Comparator.nullsLast(LocalDate::compareTo).compare(newDate, existingDate) > 0) {
                latestByKey.put(key, filing);
            }
        }
        return latestByKey;
    }

    private record BeneficialOwnerSummaryView(
            String id,
            String filingPersonName,
            Double percentOfClass,
            Long sharesBeneficiallyOwned,
            LocalDate latestEventDate,
            String scheduleType) implements BeneficialOwnerSummary {

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getFilingPersonName() {
            return filingPersonName;
        }

        @Override
        public Double getPercentOfClass() {
            return percentOfClass;
        }

        @Override
        public Long getSharesBeneficiallyOwned() {
            return sharesBeneficiallyOwned;
        }

        @Override
        public LocalDate getLatestEventDate() {
            return latestEventDate;
        }

        @Override
        public String getScheduleType() {
            return scheduleType;
        }
    }

    private record OwnershipHistoryEntryView(
            LocalDate eventDate,
            Double percentOfClass,
            Long sharesBeneficiallyOwned,
            String formType,
            String amendmentType) implements OwnershipHistoryEntry {

        @Override
        public LocalDate getEventDate() {
            return eventDate;
        }

        @Override
        public Double getPercentOfClass() {
            return percentOfClass;
        }

        @Override
        public Long getSharesBeneficiallyOwned() {
            return sharesBeneficiallyOwned;
        }

        @Override
        public String getFormType() {
            return formType;
        }

        @Override
        public String getAmendmentType() {
            return amendmentType;
        }
    }

    private record ScheduleTypeCountView(String id, Long count) implements ScheduleTypeCount {

        @Override
        public String getId() {
            return id;
        }

        @Override
        public Long getCount() {
            return count;
        }
    }

    private record OwnerPortfolioEntryView(
            String id,
            String issuerName,
            String cusip,
            Double percentOfClass,
            Long sharesBeneficiallyOwned,
            LocalDate latestEventDate,
            String scheduleType) implements OwnerPortfolioEntry {

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getIssuerName() {
            return issuerName;
        }

        @Override
        public String getCusip() {
            return cusip;
        }

        @Override
        public Double getPercentOfClass() {
            return percentOfClass;
        }

        @Override
        public Long getSharesBeneficiallyOwned() {
            return sharesBeneficiallyOwned;
        }

        @Override
        public LocalDate getLatestEventDate() {
            return latestEventDate;
        }

        @Override
        public String getScheduleType() {
            return scheduleType;
        }
    }
}
