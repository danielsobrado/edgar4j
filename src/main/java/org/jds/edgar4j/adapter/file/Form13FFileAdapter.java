package org.jds.edgar4j.adapter.file;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jds.edgar4j.model.Form13F;
import org.jds.edgar4j.model.Form13FHolding;
import org.jds.edgar4j.port.Form13FDataPort;
import org.jds.edgar4j.repository.Form13FRepository.FilerSummary;
import org.jds.edgar4j.repository.Form13FRepository.HoldingSummary;
import org.jds.edgar4j.repository.Form13FRepository.PortfolioSnapshot;
import org.jds.edgar4j.storage.file.FileFormat;
import org.jds.edgar4j.storage.file.FileStorageEngine;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@Profile("resource-low")
public class Form13FFileAdapter extends AbstractFileDataPort<Form13F> implements Form13FDataPort {

    public Form13FFileAdapter(FileStorageEngine storageEngine) {
        super(storageEngine.registerCollection(
                "form13f",
                Form13F.class,
                FileFormat.JSONL,
                Form13F::getId,
                Form13F::setId));
    }

    @Override
    public Optional<Form13F> findByAccessionNumber(String accessionNumber) {
        return findFirst(value -> accessionNumber != null && accessionNumber.equals(value.getAccessionNumber()));
    }

    @Override
    public Page<Form13F> findByCik(String cik, Pageable pageable) {
        return findMatching(value -> cik != null && cik.equals(value.getCik()), pageable);
    }

    @Override
    public Page<Form13F> findByFilerNameContainingIgnoreCase(String filerName, Pageable pageable) {
        return findMatching(value -> containsIgnoreCase(value.getFilerName(), filerName), pageable);
    }

    @Override
    public Page<Form13F> findByReportPeriod(LocalDate reportPeriod, Pageable pageable) {
        return findMatching(value -> reportPeriod != null && reportPeriod.equals(value.getReportPeriod()), pageable);
    }

    @Override
    public Page<Form13F> findByReportPeriodBetween(LocalDate startDate, LocalDate endDate, Pageable pageable) {
        return findMatching(value -> between(value.getReportPeriod(), startDate, endDate), pageable);
    }

    @Override
    public Page<Form13F> findByCikAndReportPeriodBetween(String cik, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        return findMatching(value -> cik != null
                && cik.equals(value.getCik())
                && between(value.getReportPeriod(), startDate, endDate), pageable);
    }

    @Override
    public List<Form13F> findByCikAndReportPeriodBetweenList(String cik, LocalDate startDate, LocalDate endDate) {
        return findMatching(value -> cik != null
                && cik.equals(value.getCik())
                && between(value.getReportPeriod(), startDate, endDate));
    }

    @Override
    public List<Form13F> findByHoldingCusip(String cusip) {
        return findMatching(value -> hasHolding(value, holding -> cusip != null && cusip.equals(holding.getCusip())));
    }

    @Override
    public Page<Form13F> findByHoldingCusip(String cusip, Pageable pageable) {
        return findMatching(value -> hasHolding(value, holding -> cusip != null && cusip.equals(holding.getCusip())), pageable);
    }

    @Override
    public Page<Form13F> findByHoldingIssuerName(String issuerName, Pageable pageable) {
        return findMatching(value -> hasHolding(value, holding -> containsIgnoreCase(holding.getNameOfIssuer(), issuerName)), pageable);
    }

    @Override
    public boolean existsByAccessionNumber(String accessionNumber) {
        return exists(value -> accessionNumber != null && accessionNumber.equals(value.getAccessionNumber()));
    }

    @Override
    public boolean existsByCikAndReportPeriod(String cik, LocalDate reportPeriod) {
        return exists(value -> cik != null && cik.equals(value.getCik()) && reportPeriod != null && reportPeriod.equals(value.getReportPeriod()));
    }

    @Override
    public List<FilerSummary> getTopFilersByPeriod(LocalDate reportPeriod, int limit) {
        Map<String, AggregatedFilerSummary> aggregated = new LinkedHashMap<>();
        for (Form13F filing : findMatching(value -> reportPeriod != null && reportPeriod.equals(value.getReportPeriod()))) {
            String key = (filing.getCik() != null ? filing.getCik() : "") + "|" + (filing.getFilerName() != null ? filing.getFilerName() : "");
            AggregatedFilerSummary existing = aggregated.getOrDefault(key,
                    new AggregatedFilerSummary(filing.getCik(), filing.getFilerName(), 0L, 0));
            long totalValue = existing.totalValue() + (filing.getTotalValue() != null ? filing.getTotalValue() : 0L);
            int holdingsCount = existing.holdingsCount() + (filing.getHoldingsCount() != null ? filing.getHoldingsCount() : 0);
            aggregated.put(key, new AggregatedFilerSummary(filing.getCik(), filing.getFilerName(), totalValue, holdingsCount));
        }

        return aggregated.values().stream()
                .sorted(Comparator.comparing(AggregatedFilerSummary::totalValue, Comparator.reverseOrder()))
                .limit(limit)
                .map(value -> new FilerSummaryView(
                        new FilerKeyView(value.cik(), value.filerName()),
                        value.totalValue(),
                        value.holdingsCount()))
                .map(FilerSummary.class::cast)
                .toList();
    }

    @Override
    public List<HoldingSummary> getTopHoldingsByPeriod(LocalDate reportPeriod, int limit) {
        Map<String, AggregatedHoldingSummary> aggregated = new LinkedHashMap<>();
        for (Form13F filing : findMatching(value -> reportPeriod != null && reportPeriod.equals(value.getReportPeriod()))) {
            if (filing.getHoldings() == null) {
                continue;
            }
            for (Form13FHolding holding : filing.getHoldings()) {
                if (holding == null || holding.getCusip() == null) {
                    continue;
                }
                AggregatedHoldingSummary existing = aggregated.getOrDefault(
                        holding.getCusip(),
                        new AggregatedHoldingSummary(holding.getCusip(), holding.getNameOfIssuer(), 0L, 0L, 0));
                aggregated.put(holding.getCusip(), new AggregatedHoldingSummary(
                        holding.getCusip(),
                        existing.issuerName() != null ? existing.issuerName() : holding.getNameOfIssuer(),
                        existing.totalShares() + (holding.getSharesOrPrincipalAmount() != null ? holding.getSharesOrPrincipalAmount() : 0L),
                        existing.totalValue() + (holding.getValue() != null ? holding.getValue() : 0L),
                        existing.filerCount() + 1));
            }
        }

        return aggregated.values().stream()
                .sorted(Comparator.comparing(AggregatedHoldingSummary::totalValue, Comparator.reverseOrder()))
                .limit(limit)
                .map(value -> new HoldingSummaryView(
                        value.cusip(),
                        value.issuerName(),
                        value.totalShares(),
                        value.totalValue(),
                        value.filerCount()))
                .map(HoldingSummary.class::cast)
                .toList();
    }

    @Override
    public List<PortfolioSnapshot> getPortfolioHistory(String cik) {
        return findMatching(value -> cik != null && cik.equals(value.getCik())).stream()
                .sorted(Comparator.comparing(Form13F::getReportPeriod, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(value -> new PortfolioSnapshotView(
                        value.getReportPeriod(),
                        value.getTotalValue(),
                        value.getHoldingsCount()))
                .map(PortfolioSnapshot.class::cast)
                .toList();
    }

    private boolean between(LocalDate value, LocalDate startDate, LocalDate endDate) {
        return value != null && !value.isBefore(startDate) && !value.isAfter(endDate);
    }

    private boolean hasHolding(Form13F filing, java.util.function.Predicate<Form13FHolding> predicate) {
        return filing.getHoldings() != null && filing.getHoldings().stream().anyMatch(predicate);
    }

    private record AggregatedFilerSummary(String cik, String filerName, Long totalValue, Integer holdingsCount) {
    }

    private record AggregatedHoldingSummary(
            String cusip,
            String issuerName,
            Long totalShares,
            Long totalValue,
            Integer filerCount) {
    }

    private record FilerSummaryView(
            FilerKeyView id,
            Long totalValue,
            Integer holdingsCount) implements FilerSummary {

        @Override
        public FilerKey getId() {
            return id;
        }

        @Override
        public Long getTotalValue() {
            return totalValue;
        }

        @Override
        public Integer getHoldingsCount() {
            return holdingsCount;
        }
    }

    private record FilerKeyView(String cik, String filerName) implements FilerSummary.FilerKey {

        @Override
        public String getCik() {
            return cik;
        }

        @Override
        public String getFilerName() {
            return filerName;
        }
    }

    private record HoldingSummaryView(
            String id,
            String issuerName,
            Long totalShares,
            Long totalValue,
            Integer filerCount) implements HoldingSummary {

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getIssuerName() {
            return issuerName;
        }

        @Override
        public Long getTotalShares() {
            return totalShares;
        }

        @Override
        public Long getTotalValue() {
            return totalValue;
        }

        @Override
        public Integer getFilerCount() {
            return filerCount;
        }
    }

    private record PortfolioSnapshotView(
            LocalDate reportPeriod,
            Long totalValue,
            Integer holdingsCount) implements PortfolioSnapshot {

        @Override
        public LocalDate getReportPeriod() {
            return reportPeriod;
        }

        @Override
        public Long getTotalValue() {
            return totalValue;
        }

        @Override
        public Integer getHoldingsCount() {
            return holdingsCount;
        }
    }
}
