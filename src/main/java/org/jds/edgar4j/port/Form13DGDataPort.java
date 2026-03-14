package org.jds.edgar4j.port;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.model.Form13DG;
import org.jds.edgar4j.repository.Form13DGRepository.BeneficialOwnerSummary;
import org.jds.edgar4j.repository.Form13DGRepository.OwnerPortfolioEntry;
import org.jds.edgar4j.repository.Form13DGRepository.OwnershipHistoryEntry;
import org.jds.edgar4j.repository.Form13DGRepository.ScheduleTypeCount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface Form13DGDataPort extends BaseDocumentDataPort<Form13DG> {

    Optional<Form13DG> findByAccessionNumber(String accessionNumber);

    Page<Form13DG> findByFormType(String formType, Pageable pageable);

    Page<Form13DG> findByScheduleType(String scheduleType, Pageable pageable);

    Page<Form13DG> findByIssuerCik(String issuerCik, Pageable pageable);

    Page<Form13DG> findByIssuerNameContainingIgnoreCase(String issuerName, Pageable pageable);

    List<Form13DG> findByCusip(String cusip);

    Page<Form13DG> findByCusip(String cusip, Pageable pageable);

    List<Form13DG> findByFilingPersonCik(String filingPersonCik);

    Page<Form13DG> findByFilingPersonCik(String filingPersonCik, Pageable pageable);

    Page<Form13DG> findByFilingPersonNameContainingIgnoreCase(String filingPersonName, Pageable pageable);

    Page<Form13DG> findByEventDateBetween(LocalDate startDate, LocalDate endDate, Pageable pageable);

    Page<Form13DG> findByFiledDateBetween(LocalDate startDate, LocalDate endDate, Pageable pageable);

    Page<Form13DG> findByMinPercentOfClass(Double minPercent, Pageable pageable);

    Page<Form13DG> findTenPercentOwners(Pageable pageable);

    Page<Form13DG> findByMinSharesBeneficiallyOwned(Long minShares, Pageable pageable);

    long countByScheduleType(String scheduleType);

    boolean existsByAccessionNumber(String accessionNumber);

    Page<Form13DG> findAmendments(Pageable pageable);

    Page<Form13DG> findInitialFilings(Pageable pageable);

    List<BeneficialOwnerSummary> getTopBeneficialOwnersByCusip(String cusip, int limit);

    List<OwnershipHistoryEntry> getOwnershipHistory(String filingPersonCik, String issuerCik);

    List<ScheduleTypeCount> countByScheduleTypeInDateRange(LocalDate startDate, LocalDate endDate);

    List<Form13DG> getRecentActivistFilings(int limit);

    List<OwnerPortfolioEntry> getOwnerPortfolio(String filingPersonCik);
}
