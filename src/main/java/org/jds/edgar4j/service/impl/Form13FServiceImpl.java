package org.jds.edgar4j.service.impl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.jds.edgar4j.integration.Form13FParser;
import org.jds.edgar4j.integration.SecApiClient;
import org.jds.edgar4j.model.Form13F;
import org.jds.edgar4j.model.Form13FHolding;
import org.jds.edgar4j.repository.Form13FRepository;
import org.jds.edgar4j.repository.Form13FRepository.FilerSummary;
import org.jds.edgar4j.repository.Form13FRepository.HoldingSummary;
import org.jds.edgar4j.repository.Form13FRepository.PortfolioSnapshot;
import org.jds.edgar4j.service.Form13FService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of Form 13F service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Form13FServiceImpl implements Form13FService {

    private final Form13FRepository form13FRepository;
    private final Form13FParser form13FParser;
    private final SecApiClient secApiClient;

    @Override
    public CompletableFuture<String> downloadForm13F(String cik, String accessionNumber, String infoTableDocument) {
        log.info("Downloading Form 13F Information Table: CIK={}, accession={}, doc={}",
                cik, accessionNumber, infoTableDocument);
        return secApiClient.fetchFilingAsync(cik, accessionNumber, infoTableDocument);
    }

    @Override
    public CompletableFuture<String> downloadPrimaryDocument(String cik, String accessionNumber, String primaryDocument) {
        log.info("Downloading Form 13F primary document: CIK={}, accession={}, doc={}",
                cik, accessionNumber, primaryDocument);
        return secApiClient.fetchFilingAsync(cik, accessionNumber, primaryDocument);
    }

    @Override
    public Form13F parseInformationTable(String xml, String accessionNumber) {
        log.debug("Parsing Form 13F Information Table for accession: {}", accessionNumber);
        return form13FParser.parseInformationTable(xml, accessionNumber);
    }

    @Override
    public void parseAndAddMetadata(Form13F form13F, String primaryDocXml) {
        form13FParser.parseMetadata(form13F, primaryDocXml);
    }

    @Override
    public CompletableFuture<Form13F> downloadAndParseForm13F(String cik, String accessionNumber,
            String primaryDocument, String infoTableDocument) {

        CompletableFuture<String> infoTableFuture =
                downloadForm13F(cik, accessionNumber, infoTableDocument);

        CompletableFuture<String> primaryDocFuture =
                downloadPrimaryDocument(cik, accessionNumber, primaryDocument);

        return infoTableFuture.thenCombine(primaryDocFuture, (infoTableXml, primaryDocXml) -> {
            if (infoTableXml == null || infoTableXml.isBlank()) {
                log.error("Failed to download information table for accession: {}", accessionNumber);
                return null;
            }

            Form13F form13F = parseInformationTable(infoTableXml, accessionNumber);
            if (form13F == null) {
                log.error("Failed to parse information table for accession: {}", accessionNumber);
                return null;
            }

            if (primaryDocXml != null && !primaryDocXml.isBlank()) {
                parseAndAddMetadata(form13F, primaryDocXml);
            } else {
                log.warn("Failed to download primary document for accession: {}", accessionNumber);
            }

            return form13F;
        });
    }

    @Override
    public Form13F save(Form13F form13F) {
        if (form13F == null) {
            return null;
        }
        log.info("Saving Form 13F: accession={}, filer={}",
                form13F.getAccessionNumber(), form13F.getFilerName());
        form13FRepository.findByAccessionNumber(form13F.getAccessionNumber())
                .ifPresent(existing -> form13F.setId(existing.getId()));
        return form13FRepository.save(form13F);
    }

    @Override
    public List<Form13F> saveAll(List<Form13F> form13FList) {
        log.info("Saving {} Form 13F filings", form13FList.size());
        return form13FRepository.saveAll(form13FList);
    }

    @Override
    public Optional<Form13F> findByAccessionNumber(String accessionNumber) {
        return form13FRepository.findByAccessionNumber(accessionNumber);
    }

    @Override
    public Optional<Form13F> findById(String id) {
        return form13FRepository.findById(id);
    }

    @Override
    public Page<Form13F> findByCik(String cik, Pageable pageable) {
        return form13FRepository.findByCik(cik, pageable);
    }

    @Override
    public Page<Form13F> findByFilerName(String filerName, Pageable pageable) {
        return form13FRepository.findByFilerNameContainingIgnoreCase(filerName, pageable);
    }

    @Override
    public Page<Form13F> findByReportPeriod(LocalDate reportPeriod, Pageable pageable) {
        return form13FRepository.findByReportPeriod(reportPeriod, pageable);
    }

    @Override
    public Page<Form13F> findByReportPeriodRange(LocalDate startDate, LocalDate endDate, Pageable pageable) {
        return form13FRepository.findByReportPeriodBetween(startDate, endDate, pageable);
    }

    @Override
    public Page<Form13F> findByCikAndReportPeriodRange(String cik, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        return form13FRepository.findByCikAndReportPeriodBetween(cik, startDate, endDate, pageable);
    }

    @Override
    public Page<Form13F> findByHoldingCusip(String cusip, Pageable pageable) {
        return form13FRepository.findByHoldingCusip(cusip, pageable);
    }

    @Override
    public Page<Form13F> findByHoldingIssuerName(String issuerName, Pageable pageable) {
        return form13FRepository.findByHoldingIssuerName(issuerName, pageable);
    }

    @Override
    public List<Form13F> findRecentFilings(int limit) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "filedDate"));
        return form13FRepository.findAll(pageable).getContent();
    }

    @Override
    public List<Form13F> findRecentFilingsByCik(String cik, int limit) {
        int safeLimit = limit > 0 ? limit : 10;
        Pageable pageable = PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "reportPeriod"));
        return form13FRepository.findByCik(cik, pageable).getContent();
    }

    @Override
    public boolean existsByAccessionNumber(String accessionNumber) {
        return form13FRepository.existsByAccessionNumber(accessionNumber);
    }

    @Override
    public boolean existsByCikAndReportPeriod(String cik, LocalDate reportPeriod) {
        return form13FRepository.existsByCikAndReportPeriod(cik, reportPeriod);
    }

    @Override
    public void deleteById(String id) {
        log.info("Deleting Form 13F: {}", id);
        form13FRepository.deleteById(id);
    }

    @Override
    public List<FilerSummary> getTopFilers(LocalDate reportPeriod, int limit) {
        return form13FRepository.getTopFilersByPeriod(reportPeriod, limit);
    }

    @Override
    public List<HoldingSummary> getTopHoldings(LocalDate reportPeriod, int limit) {
        return form13FRepository.getTopHoldingsByPeriod(reportPeriod, limit);
    }

    @Override
    public List<PortfolioSnapshot> getPortfolioHistory(String cik) {
        return form13FRepository.getPortfolioHistory(cik);
    }

    @Override
    public List<Form13FHolding> getHoldings(String accessionNumber) {
        return form13FRepository.findByAccessionNumber(accessionNumber)
                .map(Form13F::getHoldings)
                .orElse(List.of());
    }

    @Override
    public Optional<Form13FHolding> getHoldingByCusip(String accessionNumber, String cusip) {
        return form13FRepository.findByAccessionNumber(accessionNumber)
                .flatMap(form -> form.getHoldings().stream()
                        .filter(h -> cusip.equals(h.getCusip()))
                        .findFirst());
    }

    @Override
    public InstitutionalOwnershipStats getInstitutionalOwnership(String cusip, LocalDate reportPeriod) {
        log.debug("Getting institutional ownership for CUSIP: {} period: {}", cusip, reportPeriod);

        List<Form13F> filings = form13FRepository.findByHoldingCusip(cusip);

        // Filter by report period
        List<Form13F> periodFilings = filings.stream()
                .filter(f -> reportPeriod.equals(f.getReportPeriod()))
                .toList();

        if (periodFilings.isEmpty()) {
            return new InstitutionalOwnershipStats(cusip, null, reportPeriod, 0, 0L, 0L, List.of());
        }

        String issuerName = null;
        long totalShares = 0;
        long totalValue = 0;
        List<TopHolder> topHolders = new ArrayList<>();

        for (Form13F filing : periodFilings) {
            for (Form13FHolding holding : filing.getHoldings()) {
                if (cusip.equals(holding.getCusip())) {
                    if (issuerName == null) {
                        issuerName = holding.getNameOfIssuer();
                    }
                    long shares = holding.getSharesOrPrincipalAmount() != null ? holding.getSharesOrPrincipalAmount() : 0L;
                    long value = holding.getValue() != null ? holding.getValue() : 0L;
                    totalShares += shares;
                    totalValue += value;
                    topHolders.add(new TopHolder(filing.getCik(), filing.getFilerName(), shares, value));
                }
            }
        }

        // Sort and limit top holders
        topHolders.sort((a, b) -> Long.compare(b.value(), a.value()));
        if (topHolders.size() > 10) {
            topHolders = topHolders.subList(0, 10);
        }

        return new InstitutionalOwnershipStats(
                cusip, issuerName, reportPeriod,
                periodFilings.size(), totalShares, totalValue, topHolders
        );
    }

    @Override
    public HoldingsComparison compareHoldings(String cik, LocalDate period1, LocalDate period2) {
        log.debug("Comparing holdings for CIK: {} between {} and {}", cik, period1, period2);

        List<Form13F> filings1 = form13FRepository.findByCikAndReportPeriodBetweenList(cik, period1, period1);
        List<Form13F> filings2 = form13FRepository.findByCikAndReportPeriodBetweenList(cik, period2, period2);

        if (filings1.isEmpty() || filings2.isEmpty()) {
            return new HoldingsComparison(cik, null, period1, period2,
                    List.of(), List.of(), List.of(), List.of(), 0L);
        }

        Form13F filing1 = filings1.get(0);
        Form13F filing2 = filings2.get(0);

        Map<String, Form13FHolding> holdings1Map = new HashMap<>();
        Map<String, Form13FHolding> holdings2Map = new HashMap<>();

        if (filing1.getHoldings() != null) {
            for (Form13FHolding h : filing1.getHoldings()) {
                holdings1Map.put(h.getCusip(), h);
            }
        }
        if (filing2.getHoldings() != null) {
            for (Form13FHolding h : filing2.getHoldings()) {
                holdings2Map.put(h.getCusip(), h);
            }
        }

        List<HoldingChange> newPositions = new ArrayList<>();
        List<HoldingChange> closedPositions = new ArrayList<>();
        List<HoldingChange> increasedPositions = new ArrayList<>();
        List<HoldingChange> decreasedPositions = new ArrayList<>();
        long totalValueChange = 0;

        // Find new and increased positions
        for (Map.Entry<String, Form13FHolding> entry : holdings2Map.entrySet()) {
            String cusip = entry.getKey();
            Form13FHolding h2 = entry.getValue();
            Form13FHolding h1 = holdings1Map.get(cusip);

            if (h1 == null) {
                // New position
                newPositions.add(createHoldingChange(cusip, h2.getNameOfIssuer(), null, h2));
                totalValueChange += h2.getValue() != null ? h2.getValue() : 0;
            } else {
                // Existing position - check for change
                long shares1 = h1.getSharesOrPrincipalAmount() != null ? h1.getSharesOrPrincipalAmount() : 0;
                long shares2 = h2.getSharesOrPrincipalAmount() != null ? h2.getSharesOrPrincipalAmount() : 0;
                long value1 = h1.getValue() != null ? h1.getValue() : 0;
                long value2 = h2.getValue() != null ? h2.getValue() : 0;

                if (shares2 > shares1) {
                    increasedPositions.add(createHoldingChange(cusip, h2.getNameOfIssuer(), h1, h2));
                } else if (shares2 < shares1) {
                    decreasedPositions.add(createHoldingChange(cusip, h2.getNameOfIssuer(), h1, h2));
                }
                totalValueChange += (value2 - value1);
            }
        }

        // Find closed positions
        for (Map.Entry<String, Form13FHolding> entry : holdings1Map.entrySet()) {
            String cusip = entry.getKey();
            if (!holdings2Map.containsKey(cusip)) {
                Form13FHolding h1 = entry.getValue();
                closedPositions.add(createHoldingChange(cusip, h1.getNameOfIssuer(), h1, null));
                totalValueChange -= h1.getValue() != null ? h1.getValue() : 0;
            }
        }

        return new HoldingsComparison(
                cik, filing2.getFilerName(), period1, period2,
                newPositions, closedPositions, increasedPositions, decreasedPositions,
                totalValueChange
        );
    }

    private HoldingChange createHoldingChange(String cusip, String issuerName,
            Form13FHolding h1, Form13FHolding h2) {

        Long shares1 = h1 != null ? h1.getSharesOrPrincipalAmount() : null;
        Long shares2 = h2 != null ? h2.getSharesOrPrincipalAmount() : null;
        Long value1 = h1 != null ? h1.getValue() : null;
        Long value2 = h2 != null ? h2.getValue() : null;

        Double percentChange = null;
        if (shares1 != null && shares1 > 0 && shares2 != null) {
            percentChange = ((double) (shares2 - shares1) / shares1) * 100;
        }

        return new HoldingChange(cusip, issuerName, shares1, shares2, value1, value2, percentChange);
    }
}
