package org.jds.edgar4j.port;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.model.insider.InsiderTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface InsiderTransactionDataPort extends BaseInsiderDataPort<InsiderTransaction> {

    Optional<InsiderTransaction> findByAccessionNumber(String accessionNumber);

    List<InsiderTransaction> findByCompanyCik(String cik);

    Page<InsiderTransaction> findByCompanyCik(String cik, Pageable pageable);

    List<InsiderTransaction> findByCompanyCikAndTransactionDateBetween(String cik, LocalDate startDate, LocalDate endDate);

    List<InsiderTransaction> findByInsiderCik(String cik);

    Page<InsiderTransaction> findByInsiderCik(String cik, Pageable pageable);

    List<InsiderTransaction> findByInsiderCikAndTransactionDateBetween(String cik, LocalDate startDate, LocalDate endDate);

    List<InsiderTransaction> findByTransactionDateBetween(LocalDate startDate, LocalDate endDate);

    List<InsiderTransaction> findByFilingDateBetween(LocalDate startDate, LocalDate endDate);

    List<InsiderTransaction> findByTransactionCode(String transactionCode);

    List<InsiderTransaction> findByAcquiredDisposed(InsiderTransaction.AcquiredDisposed acquiredDisposed);

    List<InsiderTransaction> findSignificantTransactions(BigDecimal threshold);

    List<InsiderTransaction> findRecentTransactions(LocalDate since);

    List<InsiderTransaction> findByCompanyCikAndInsiderCik(String companyCik, String insiderCik);

    List<InsiderTransaction> findByIsDerivativeTrue();

    List<InsiderTransaction> findByAcquiredDisposedAndSharesTransactedGreaterThan(
            InsiderTransaction.AcquiredDisposed acquiredDisposed,
            BigDecimal shares);

    List<InsiderTransaction> findByOwnershipNature(InsiderTransaction.OwnershipNature ownershipNature);

    boolean existsByAccessionNumber(String accessionNumber);

    Long countTransactionsByCompany(String cik);

    Long countTransactionsByInsider(String cik);

    List<InsiderTransaction> findTransactionsWithHighOwnershipChange(BigDecimal threshold);

    List<InsiderTransaction> findLatestTransactionsByCompany(String cik, Pageable pageable);

    List<InsiderTransaction> findBySecurityTitleContainingIgnoreCase(String securityTitle);
}
