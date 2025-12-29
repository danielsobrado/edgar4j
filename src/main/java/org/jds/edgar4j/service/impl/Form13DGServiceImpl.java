package org.jds.edgar4j.service.impl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.jds.edgar4j.integration.Form13DGParser;
import org.jds.edgar4j.integration.SecApiClient;
import org.jds.edgar4j.model.Form13DG;
import org.jds.edgar4j.repository.Form13DGRepository;
import org.jds.edgar4j.repository.Form13DGRepository.BeneficialOwnerSummary;
import org.jds.edgar4j.repository.Form13DGRepository.OwnerPortfolioEntry;
import org.jds.edgar4j.repository.Form13DGRepository.OwnershipHistoryEntry;
import org.jds.edgar4j.repository.Form13DGRepository.ScheduleTypeCount;
import org.jds.edgar4j.service.Form13DGService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of Schedule 13D/13G service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Form13DGServiceImpl implements Form13DGService {

    private final Form13DGRepository form13DGRepository;
    private final Form13DGParser form13DGParser;
    private final SecApiClient secApiClient;

    // ========== DOWNLOAD AND PARSE ==========

    @Override
    public CompletableFuture<String> downloadForm13DG(String cik, String accessionNumber, String document) {
        log.info("Downloading Schedule 13D/G: CIK={}, accession={}, doc={}",
                cik, accessionNumber, document);
        return secApiClient.fetchFilingAsync(cik, accessionNumber, document);
    }

    @Override
    public Form13DG parse(String xml, String accessionNumber) {
        log.debug("Parsing Schedule 13D/G for accession: {}", accessionNumber);
        return form13DGParser.parse(xml, accessionNumber);
    }

    @Override
    public CompletableFuture<Form13DG> downloadAndParse(String cik, String accessionNumber, String document) {
        return downloadForm13DG(cik, accessionNumber, document)
                .thenApply(xml -> {
                    if (xml == null || xml.isBlank()) {
                        log.error("Failed to download Schedule 13D/G for accession: {}", accessionNumber);
                        return null;
                    }

                    Form13DG form13DG = parse(xml, accessionNumber);
                    if (form13DG == null) {
                        log.error("Failed to parse Schedule 13D/G for accession: {}", accessionNumber);
                        return null;
                    }

                    // Set filed date to today if not set
                    if (form13DG.getFiledDate() == null) {
                        form13DG.setFiledDate(LocalDate.now());
                    }

                    return form13DG;
                });
    }

    // ========== CRUD OPERATIONS ==========

    @Override
    public Form13DG save(Form13DG form13DG) {
        log.info("Saving Schedule 13D/G: accession={}, issuer={}, filer={}",
                form13DG.getAccessionNumber(), form13DG.getIssuerName(), form13DG.getFilingPersonName());
        return form13DGRepository.save(form13DG);
    }

    @Override
    public List<Form13DG> saveAll(List<Form13DG> form13DGList) {
        log.info("Saving {} Schedule 13D/G filings", form13DGList.size());
        return form13DGRepository.saveAll(form13DGList);
    }

    @Override
    public Optional<Form13DG> findByAccessionNumber(String accessionNumber) {
        return form13DGRepository.findByAccessionNumber(accessionNumber);
    }

    @Override
    public Optional<Form13DG> findById(String id) {
        return form13DGRepository.findById(id);
    }

    @Override
    public void deleteById(String id) {
        log.info("Deleting Schedule 13D/G: {}", id);
        form13DGRepository.deleteById(id);
    }

    @Override
    public boolean existsByAccessionNumber(String accessionNumber) {
        return form13DGRepository.existsByAccessionNumber(accessionNumber);
    }

    // ========== SEARCH BY SCHEDULE TYPE ==========

    @Override
    public Page<Form13DG> findByScheduleType(String scheduleType, Pageable pageable) {
        return form13DGRepository.findByScheduleType(scheduleType, pageable);
    }

    @Override
    public Page<Form13DG> findByFormType(String formType, Pageable pageable) {
        return form13DGRepository.findByFormType(formType, pageable);
    }

    @Override
    public long countByScheduleType(String scheduleType) {
        return form13DGRepository.countByScheduleType(scheduleType);
    }

    // ========== SEARCH BY ISSUER ==========

    @Override
    public Page<Form13DG> findByIssuerCik(String issuerCik, Pageable pageable) {
        return form13DGRepository.findByIssuerCik(issuerCik, pageable);
    }

    @Override
    public Page<Form13DG> findByIssuerName(String issuerName, Pageable pageable) {
        return form13DGRepository.findByIssuerNameContainingIgnoreCase(issuerName, pageable);
    }

    @Override
    public Page<Form13DG> findByCusip(String cusip, Pageable pageable) {
        return form13DGRepository.findByCusip(cusip, pageable);
    }

    // ========== SEARCH BY BENEFICIAL OWNER ==========

    @Override
    public Page<Form13DG> findByFilingPersonCik(String filingPersonCik, Pageable pageable) {
        return form13DGRepository.findByFilingPersonCik(filingPersonCik, pageable);
    }

    @Override
    public Page<Form13DG> findByFilingPersonName(String filingPersonName, Pageable pageable) {
        return form13DGRepository.findByFilingPersonNameContainingIgnoreCase(filingPersonName, pageable);
    }

    // ========== SEARCH BY DATE ==========

    @Override
    public Page<Form13DG> findByEventDateRange(LocalDate startDate, LocalDate endDate, Pageable pageable) {
        return form13DGRepository.findByEventDateBetween(startDate, endDate, pageable);
    }

    @Override
    public Page<Form13DG> findByFiledDateRange(LocalDate startDate, LocalDate endDate, Pageable pageable) {
        return form13DGRepository.findByFiledDateBetween(startDate, endDate, pageable);
    }

    // ========== SEARCH BY OWNERSHIP ==========

    @Override
    public Page<Form13DG> findByMinPercentOfClass(Double minPercent, Pageable pageable) {
        return form13DGRepository.findByMinPercentOfClass(minPercent, pageable);
    }

    @Override
    public Page<Form13DG> findTenPercentOwners(Pageable pageable) {
        return form13DGRepository.findTenPercentOwners(pageable);
    }

    @Override
    public Page<Form13DG> findByMinSharesBeneficiallyOwned(Long minShares, Pageable pageable) {
        return form13DGRepository.findByMinSharesBeneficiallyOwned(minShares, pageable);
    }

    // ========== RECENT FILINGS ==========

    @Override
    public List<Form13DG> findRecentFilings(int limit) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "filedDate"));
        return form13DGRepository.findAll(pageable).getContent();
    }

    @Override
    public List<Form13DG> findRecent13DFilings(int limit) {
        return form13DGRepository.findTop10ByScheduleTypeOrderByFiledDateDesc("13D");
    }

    @Override
    public List<Form13DG> findRecent13GFilings(int limit) {
        return form13DGRepository.findTop10ByScheduleTypeOrderByFiledDateDesc("13G");
    }

    // ========== AMENDMENTS ==========

    @Override
    public Page<Form13DG> findAmendments(Pageable pageable) {
        return form13DGRepository.findAmendments(pageable);
    }

    @Override
    public Page<Form13DG> findInitialFilings(Pageable pageable) {
        return form13DGRepository.findInitialFilings(pageable);
    }

    // ========== ANALYTICS ==========

    @Override
    public List<BeneficialOwnerSummary> getTopBeneficialOwners(String cusip, int limit) {
        return form13DGRepository.getTopBeneficialOwnersByCusip(cusip, limit);
    }

    @Override
    public List<OwnershipHistoryEntry> getOwnershipHistory(String filingPersonCik, String issuerCik) {
        return form13DGRepository.getOwnershipHistory(filingPersonCik, issuerCik);
    }

    @Override
    public List<ScheduleTypeCount> getFilingCountsByScheduleType(LocalDate startDate, LocalDate endDate) {
        return form13DGRepository.countByScheduleTypeInDateRange(startDate, endDate);
    }

    @Override
    public List<Form13DG> getRecentActivistFilings(int limit) {
        return form13DGRepository.getRecentActivistFilings(limit);
    }

    @Override
    public List<OwnerPortfolioEntry> getOwnerPortfolio(String filingPersonCik) {
        return form13DGRepository.getOwnerPortfolio(filingPersonCik);
    }

    @Override
    public OwnershipComparison compareOwnership(String filingPersonCik, String issuerCik) {
        log.debug("Comparing ownership for filer: {} issuer: {}", filingPersonCik, issuerCik);

        List<OwnershipHistoryEntry> history = form13DGRepository.getOwnershipHistory(filingPersonCik, issuerCik);

        if (history.isEmpty()) {
            return null;
        }

        // Get the latest filing for names
        List<Form13DG> filings = form13DGRepository.findByFilingPersonCik(filingPersonCik);
        Optional<Form13DG> latestFiling = filings.stream()
                .filter(f -> issuerCik.equals(f.getIssuerCik()))
                .max(Comparator.comparing(Form13DG::getEventDate));

        String filingPersonName = latestFiling.map(Form13DG::getFilingPersonName).orElse(null);
        String issuerName = latestFiling.map(Form13DG::getIssuerName).orElse(null);
        String cusip = latestFiling.map(Form13DG::getCusip).orElse(null);

        // Convert history entries to data points
        List<OwnershipDataPoint> dataPoints = history.stream()
                .map(h -> new OwnershipDataPoint(
                        h.getEventDate(),
                        h.getPercentOfClass(),
                        h.getSharesBeneficiallyOwned(),
                        h.getFormType()
                ))
                .toList();

        // Calculate changes
        OwnershipHistoryEntry earliest = history.get(history.size() - 1);
        OwnershipHistoryEntry latest = history.get(0);

        Double earliestPercent = earliest.getPercentOfClass();
        Double latestPercent = latest.getPercentOfClass();
        Double percentChange = (earliestPercent != null && latestPercent != null) ?
                latestPercent - earliestPercent : null;

        Long earliestShares = earliest.getSharesBeneficiallyOwned();
        Long latestShares = latest.getSharesBeneficiallyOwned();
        Long sharesChange = (earliestShares != null && latestShares != null) ?
                latestShares - earliestShares : null;

        return new OwnershipComparison(
                filingPersonCik, filingPersonName, issuerCik, issuerName, cusip,
                dataPoints, earliestPercent, latestPercent, percentChange,
                earliestShares, latestShares, sharesChange
        );
    }

    @Override
    public BeneficialOwnershipSnapshot getBeneficialOwnershipSnapshot(String cusip) {
        log.debug("Getting beneficial ownership snapshot for CUSIP: {}", cusip);

        List<Form13DG> filings = form13DGRepository.findByCusip(cusip);

        if (filings.isEmpty()) {
            return null;
        }

        // Get the most recent filing for each beneficial owner
        java.util.Map<String, Form13DG> latestByOwner = new java.util.HashMap<>();
        for (Form13DG filing : filings) {
            String ownerCik = filing.getFilingPersonCik();
            if (ownerCik == null) continue;

            Form13DG existing = latestByOwner.get(ownerCik);
            if (existing == null ||
                (filing.getEventDate() != null && existing.getEventDate() != null &&
                 filing.getEventDate().isAfter(existing.getEventDate()))) {
                latestByOwner.put(ownerCik, filing);
            }
        }

        // Get issuer info from any filing
        Form13DG anyFiling = filings.get(0);
        String securityTitle = anyFiling.getSecurityTitle();
        String issuerName = anyFiling.getIssuerName();
        String issuerCik = anyFiling.getIssuerCik();

        // Build owner details
        List<BeneficialOwnerDetail> owners = new ArrayList<>();
        double totalPercent = 0.0;
        long totalShares = 0L;
        int activistCount = 0;
        int passiveCount = 0;
        LocalDate latestDate = null;

        for (Form13DG filing : latestByOwner.values()) {
            boolean isActivist = filing.is13D();

            owners.add(new BeneficialOwnerDetail(
                    filing.getFilingPersonCik(),
                    filing.getFilingPersonName(),
                    filing.getPercentOfClass(),
                    filing.getSharesBeneficiallyOwned(),
                    filing.getEventDate(),
                    filing.getScheduleType(),
                    isActivist
            ));

            if (filing.getPercentOfClass() != null) {
                totalPercent += filing.getPercentOfClass();
            }
            if (filing.getSharesBeneficiallyOwned() != null) {
                totalShares += filing.getSharesBeneficiallyOwned();
            }

            if (isActivist) {
                activistCount++;
            } else {
                passiveCount++;
            }

            if (filing.getEventDate() != null) {
                if (latestDate == null || filing.getEventDate().isAfter(latestDate)) {
                    latestDate = filing.getEventDate();
                }
            }
        }

        // Sort owners by percentage descending
        owners.sort((a, b) -> {
            if (a.percentOfClass() == null) return 1;
            if (b.percentOfClass() == null) return -1;
            return Double.compare(b.percentOfClass(), a.percentOfClass());
        });

        return new BeneficialOwnershipSnapshot(
                cusip, securityTitle, issuerName, issuerCik,
                owners, totalPercent, totalShares,
                activistCount, passiveCount, latestDate
        );
    }
}
