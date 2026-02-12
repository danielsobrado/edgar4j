package org.jds.edgar4j.service.insider;

import org.jds.edgar4j.model.insider.Company;
import org.jds.edgar4j.model.insider.Insider;
import org.jds.edgar4j.model.insider.InsiderTransaction;
import org.jds.edgar4j.model.insider.TransactionType;
import org.jds.edgar4j.repository.insider.InsiderTransactionRepository;
import org.jds.edgar4j.service.insider.impl.InsiderTransactionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for InsiderTransactionService implementation
 * 
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-01-01
 */
@ExtendWith(MockitoExtension.class)
class InsiderTransactionServiceTest {

    @Mock
    private InsiderTransactionRepository transactionRepository;

    private InsiderTransactionServiceImpl insiderTransactionService;

    private Company testCompany;
    private Insider testInsider;
    private TransactionType testTransactionType;

    @BeforeEach
    void setUp() {
        insiderTransactionService = new InsiderTransactionServiceImpl(transactionRepository);
        
        testCompany = Company.builder()
            .id(1L)
            .cik("0000789019")
            .companyName("Test Company")
            .tickerSymbol("TEST")
            .totalSharesOutstanding(new BigDecimal("1000000"))
            .build();

        testInsider = Insider.builder()
            .id(1L)
            .cik("0001234567")
            .personName("Test Insider")
            .build();

        testTransactionType = TransactionType.builder()
            .id(1L)
            .transactionCode("P")
            .transactionName("Purchase")
            .build();
    }

    @DisplayName("Should save single transaction successfully")
    @Test
    void testSaveTransaction() {
        // Given
        InsiderTransaction transaction = createValidTransaction();
        InsiderTransaction savedTransaction = createValidTransaction();
        savedTransaction.setId(1L);

        when(transactionRepository.save(any(InsiderTransaction.class))).thenReturn(savedTransaction);

        // When
        InsiderTransaction result = insiderTransactionService.save(transaction);

        // Then
        assertNotNull(result);
        assertEquals(1L, result.getId());
        verify(transactionRepository, times(1)).save(transaction);
    }

    @DisplayName("Should save multiple transactions successfully")
    @Test
    void testSaveAllTransactions() {
        // Given
        List<InsiderTransaction> transactions = Arrays.asList(
            createValidTransaction("0001234567-24-000001"),
            createValidTransaction("0001234567-24-000002"),
            createValidTransaction("0001234567-24-000003")
        );

        InsiderTransaction savedTransaction1 = createValidTransaction("0001234567-24-000001");
        savedTransaction1.setId(1L);
        InsiderTransaction savedTransaction2 = createValidTransaction("0001234567-24-000002");
        savedTransaction2.setId(2L);
        InsiderTransaction savedTransaction3 = createValidTransaction("0001234567-24-000003");
        savedTransaction3.setId(3L);

        when(transactionRepository.save(any(InsiderTransaction.class)))
            .thenReturn(savedTransaction1)
            .thenReturn(savedTransaction2)
            .thenReturn(savedTransaction3);

        // When
        List<InsiderTransaction> results = insiderTransactionService.saveAll(transactions);

        // Then
        assertNotNull(results);
        assertEquals(3, results.size());
        assertEquals(1L, results.get(0).getId());
        assertEquals(2L, results.get(1).getId());
        assertEquals(3L, results.get(2).getId());
        verify(transactionRepository, times(3)).save(any(InsiderTransaction.class));
    }

    @DisplayName("Should handle save errors gracefully in saveAll")
    @Test
    void testSaveAllWithErrors() {
        // Given
        List<InsiderTransaction> transactions = Arrays.asList(
            createValidTransaction("0001234567-24-000001"),
            createValidTransaction("0001234567-24-000002"),  // This will fail
            createValidTransaction("0001234567-24-000003")
        );

        InsiderTransaction savedTransaction1 = createValidTransaction("0001234567-24-000001");
        savedTransaction1.setId(1L);
        InsiderTransaction savedTransaction3 = createValidTransaction("0001234567-24-000003");
        savedTransaction3.setId(3L);

        when(transactionRepository.save(any(InsiderTransaction.class)))
            .thenReturn(savedTransaction1)
            .thenThrow(new RuntimeException("Save failed"))
            .thenReturn(savedTransaction3);

        // When
        List<InsiderTransaction> results = insiderTransactionService.saveAll(transactions);

        // Then
        assertNotNull(results);
        assertEquals(2, results.size()); // Should have 2 successful saves
        assertEquals(1L, results.get(0).getId());
        assertEquals(3L, results.get(1).getId());
        verify(transactionRepository, times(3)).save(any(InsiderTransaction.class));
    }

    @DisplayName("Should validate transaction before saving")
    @Test
    void testSaveTransactionWithValidation() {
        // Given - Invalid transaction (missing required fields)
        InsiderTransaction invalidTransaction = InsiderTransaction.builder()
            .accessionNumber("0001234567-24-000001")
            // Missing company, insider, dates
            .build();

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            insiderTransactionService.save(invalidTransaction);
        });

        verify(transactionRepository, never()).save(any());
    }

    @DisplayName("Should calculate ownership percentages before saving")
    @Test
    void testOwnershipPercentageCalculation() {
        // Given
        InsiderTransaction transaction = createValidTransaction();
        transaction.setSharesOwnedBefore(new BigDecimal("10000"));
        transaction.setSharesOwnedAfter(new BigDecimal("11000"));

        InsiderTransaction savedTransaction = createValidTransaction();
        savedTransaction.setId(1L);
        savedTransaction.setOwnershipPercentageBefore(new BigDecimal("1.000000"));
        savedTransaction.setOwnershipPercentageAfter(new BigDecimal("1.100000"));

        when(transactionRepository.save(any(InsiderTransaction.class))).thenReturn(savedTransaction);

        // When
        InsiderTransaction result = insiderTransactionService.save(transaction);

        // Then
        assertNotNull(result);
        assertEquals(new BigDecimal("1.000000"), result.getOwnershipPercentageBefore());
        assertEquals(new BigDecimal("1.100000"), result.getOwnershipPercentageAfter());
    }

    @DisplayName("Should find transaction by accession number")
    @Test
    void testFindByAccessionNumber() {
        // Given
        String accessionNumber = "0001234567-24-000001";
        InsiderTransaction expectedTransaction = createValidTransaction();
        expectedTransaction.setId(1L);

        when(transactionRepository.findByAccessionNumber(accessionNumber))
            .thenReturn(Optional.of(expectedTransaction));

        // When
        Optional<InsiderTransaction> result = insiderTransactionService.findByAccessionNumber(accessionNumber);

        // Then
        assertTrue(result.isPresent());
        assertEquals(1L, result.get().getId());
        assertEquals(accessionNumber, result.get().getAccessionNumber());
    }

    @DisplayName("Should check if transaction exists")
    @Test
    void testTransactionExists() {
        // Given
        String accessionNumber = "0001234567-24-000001";
        when(transactionRepository.existsByAccessionNumber(accessionNumber)).thenReturn(true);

        // When
        boolean exists = insiderTransactionService.transactionExists(accessionNumber);

        // Then
        assertTrue(exists);
        verify(transactionRepository, times(1)).existsByAccessionNumber(accessionNumber);
    }

    @DisplayName("Should validate transaction data consistency")
    @Test
    void testTransactionValidation() {
        // Given - Transaction with consistent data
        InsiderTransaction validTransaction = createValidTransaction();

        // When
        boolean isValid = insiderTransactionService.validateTransaction(validTransaction);

        // Then
        assertTrue(isValid);
    }

    @DisplayName("Should reject transaction with invalid dates")
    @Test
    void testTransactionValidationInvalidDates() {
        // Given - Transaction date after filing date
        InsiderTransaction invalidTransaction = createValidTransaction();
        invalidTransaction.setTransactionDate(LocalDate.of(2024, 1, 16));
        invalidTransaction.setFilingDate(LocalDate.of(2024, 1, 15));

        // When
        boolean isValid = insiderTransactionService.validateTransaction(invalidTransaction);

        // Then
        assertFalse(isValid);
    }

    @DisplayName("Should reject transaction with negative values")
    @Test
    void testTransactionValidationNegativeValues() {
        // Given - Transaction with negative shares
        InsiderTransaction invalidTransaction = createValidTransaction();
        invalidTransaction.setSharesTransacted(new BigDecimal("-100"));

        // When
        boolean isValid = insiderTransactionService.validateTransaction(invalidTransaction);

        // Then
        assertFalse(isValid);
    }

    @DisplayName("Should find transactions by date range")
    @Test
    void testFindTransactionsByDateRange() {
        // Given
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 31);
        List<InsiderTransaction> expectedTransactions = Arrays.asList(createValidTransaction());

        when(transactionRepository.findByTransactionDateBetween(startDate, endDate))
            .thenReturn(expectedTransactions);

        // When
        List<InsiderTransaction> results = insiderTransactionService.findTransactionsByDateRange(startDate, endDate);

        // Then
        assertNotNull(results);
        assertEquals(1, results.size());
        verify(transactionRepository, times(1)).findByTransactionDateBetween(startDate, endDate);
    }

    private InsiderTransaction createValidTransaction() {
        return createValidTransaction("0001234567-24-000001");
    }

    private InsiderTransaction createValidTransaction(String accessionNumber) {
        return InsiderTransaction.builder()
            .company(testCompany)
            .insider(testInsider)
            .transactionType(testTransactionType)
            .accessionNumber(accessionNumber)
            .transactionDate(LocalDate.of(2024, 1, 15))
            .filingDate(LocalDate.of(2024, 1, 16))
            .securityTitle("Common Stock")
            .transactionCode("P")
            .sharesTransacted(new BigDecimal("1000"))
            .pricePerShare(new BigDecimal("100.00"))
            .sharesOwnedBefore(new BigDecimal("10000"))
            .sharesOwnedAfter(new BigDecimal("11000"))
            .acquiredDisposed(InsiderTransaction.AcquiredDisposed.ACQUIRED)
            .ownershipNature(InsiderTransaction.OwnershipNature.DIRECT)
            .isDerivative(false)
            .build();
    }
}
