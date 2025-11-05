package org.jds.edgar4j.model.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for InsiderBuy model
 * Tests calculation methods and helper functions
 *
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-11-05
 */
public class InsiderBuyTests {

    @DisplayName("Test InsiderBuy - Calculate shares owned before")
    @Test
    public void testCalculateSharesOwnedBefore() {
        InsiderBuy buy = InsiderBuy.builder()
            .quantity(new BigDecimal("1000"))
            .sharesOwnedAfter(new BigDecimal("5000"))
            .build();

        buy.calculateSharesOwnedBefore();

        assertEquals(new BigDecimal("4000"), buy.getSharesOwnedBefore());
    }

    @DisplayName("Test InsiderBuy - Calculate shares owned before (new position)")
    @Test
    public void testCalculateSharesOwnedBefore_NewPosition() {
        InsiderBuy buy = InsiderBuy.builder()
            .quantity(new BigDecimal("1000"))
            .sharesOwnedAfter(new BigDecimal("1000"))
            .build();

        buy.calculateSharesOwnedBefore();

        assertEquals(BigDecimal.ZERO, buy.getSharesOwnedBefore());
    }

    @DisplayName("Test InsiderBuy - Calculate ownership change percent")
    @Test
    public void testCalculateOwnershipChange() {
        InsiderBuy buy = InsiderBuy.builder()
            .sharesOwnedBefore(new BigDecimal("1000"))
            .sharesOwnedAfter(new BigDecimal("1500"))
            .build();

        buy.calculateOwnershipChange();

        assertEquals(new BigDecimal("50.00"), buy.getOwnershipChangePercent());
    }

    @DisplayName("Test InsiderBuy - Calculate ownership change percent (new position)")
    @Test
    public void testCalculateOwnershipChange_NewPosition() {
        InsiderBuy buy = InsiderBuy.builder()
            .sharesOwnedBefore(BigDecimal.ZERO)
            .sharesOwnedAfter(new BigDecimal("1000"))
            .build();

        buy.calculateOwnershipChange();

        assertEquals(new BigDecimal("100"), buy.getOwnershipChangePercent());
    }

    @DisplayName("Test InsiderBuy - Calculate ownership change percent (null before)")
    @Test
    public void testCalculateOwnershipChange_NullBefore() {
        InsiderBuy buy = InsiderBuy.builder()
            .sharesOwnedBefore(null)
            .sharesOwnedAfter(new BigDecimal("1000"))
            .build();

        buy.calculateOwnershipChange();

        assertEquals(new BigDecimal("100"), buy.getOwnershipChangePercent());
    }

    @DisplayName("Test InsiderBuy - Calculate transaction value")
    @Test
    public void testCalculateTransactionValue() {
        InsiderBuy buy = InsiderBuy.builder()
            .quantity(new BigDecimal("100"))
            .pricePerShare(new BigDecimal("25.50"))
            .build();

        buy.calculateTransactionValue();

        assertEquals(new BigDecimal("2550.00"), buy.getTransactionValue());
    }

    @DisplayName("Test InsiderBuy - Calculate transaction value (large numbers)")
    @Test
    public void testCalculateTransactionValue_LargeNumbers() {
        InsiderBuy buy = InsiderBuy.builder()
            .quantity(new BigDecimal("10000"))
            .pricePerShare(new BigDecimal("384.34"))
            .build();

        buy.calculateTransactionValue();

        assertEquals(new BigDecimal("3843400.00"), buy.getTransactionValue());
    }

    @DisplayName("Test InsiderBuy - Get formatted trade type (Purchase)")
    @Test
    public void testGetFormattedTradeType_Purchase() {
        InsiderBuy buy = InsiderBuy.builder()
            .tradeType("P")
            .build();

        assertEquals("P - Purchase", buy.getFormattedTradeType());
    }

    @DisplayName("Test InsiderBuy - Get formatted trade type (Sale)")
    @Test
    public void testGetFormattedTradeType_Sale() {
        InsiderBuy buy = InsiderBuy.builder()
            .tradeType("S")
            .build();

        assertEquals("S - Sale", buy.getFormattedTradeType());
    }

    @DisplayName("Test InsiderBuy - Get formatted trade type (Award)")
    @Test
    public void testGetFormattedTradeType_Award() {
        InsiderBuy buy = InsiderBuy.builder()
            .tradeType("A")
            .build();

        assertEquals("A - Award", buy.getFormattedTradeType());
    }

    @DisplayName("Test InsiderBuy - Get formatted trade type (already formatted)")
    @Test
    public void testGetFormattedTradeType_AlreadyFormatted() {
        InsiderBuy buy = InsiderBuy.builder()
            .tradeType("P - Purchase")
            .build();

        assertEquals("P - Purchase", buy.getFormattedTradeType());
    }

    @DisplayName("Test InsiderBuy - Get formatted ownership change")
    @Test
    public void testGetFormattedOwnershipChange() {
        InsiderBuy buy = InsiderBuy.builder()
            .ownershipChangePercent(new BigDecimal("25.50"))
            .build();

        assertEquals("+25.50%", buy.getFormattedOwnershipChange());
    }

    @DisplayName("Test InsiderBuy - Get formatted ownership change (negative)")
    @Test
    public void testGetFormattedOwnershipChange_Negative() {
        InsiderBuy buy = InsiderBuy.builder()
            .ownershipChangePercent(new BigDecimal("-10.00"))
            .build();

        assertEquals("-10.00%", buy.getFormattedOwnershipChange());
    }

    @DisplayName("Test InsiderBuy - Get formatted ownership change (null)")
    @Test
    public void testGetFormattedOwnershipChange_Null() {
        InsiderBuy buy = InsiderBuy.builder()
            .ownershipChangePercent(null)
            .build();

        assertEquals("", buy.getFormattedOwnershipChange());
    }

    @DisplayName("Test InsiderBuy - Is direct ownership")
    @Test
    public void testIsDirectOwnership() {
        InsiderBuy direct = InsiderBuy.builder()
            .ownershipType("D")
            .build();

        assertTrue(direct.isDirectOwnership());
        assertFalse(direct.isIndirectOwnership());
    }

    @DisplayName("Test InsiderBuy - Is indirect ownership")
    @Test
    public void testIsIndirectOwnership() {
        InsiderBuy indirect = InsiderBuy.builder()
            .ownershipType("I")
            .build();

        assertTrue(indirect.isIndirectOwnership());
        assertFalse(indirect.isDirectOwnership());
    }

    @DisplayName("Test InsiderBuy - Complete flow")
    @Test
    public void testCompleteFlow() {
        InsiderBuy buy = InsiderBuy.builder()
            .accessionNumber("0001626431-16-000118")
            .filingDate(LocalDateTime.of(2025, 11, 4, 20, 30, 13))
            .tradeDate(LocalDate.of(2025, 10, 31))
            .ticker("ETN")
            .companyName("Eaton Corp Plc")
            .insiderName("Gerald Johnson")
            .insiderTitle("Director")
            .tradeType("P")
            .pricePerShare(new BigDecimal("384.34"))
            .quantity(new BigDecimal("100"))
            .sharesOwnedAfter(new BigDecimal("200"))
            .ownershipType("D")
            .securityTitle("Common Stock")
            .build();

        // Calculate all fields
        buy.calculateSharesOwnedBefore();
        buy.calculateOwnershipChange();
        buy.calculateTransactionValue();

        // Verify calculations
        assertEquals(new BigDecimal("100"), buy.getSharesOwnedBefore());
        assertEquals(new BigDecimal("100.00"), buy.getOwnershipChangePercent());
        assertEquals(new BigDecimal("38434.00"), buy.getTransactionValue());

        // Verify formatted values
        assertEquals("P - Purchase", buy.getFormattedTradeType());
        assertEquals("+100.00%", buy.getFormattedOwnershipChange());

        // Verify ownership type
        assertTrue(buy.isDirectOwnership());
    }

    @DisplayName("Test InsiderBuy - Builder with defaults")
    @Test
    public void testBuilderDefaults() {
        InsiderBuy buy = InsiderBuy.builder().build();

        assertNotNull(buy);
        assertNull(buy.getTicker());
        assertNull(buy.getQuantity());
        assertNull(buy.getTransactionValue());
    }

    @DisplayName("Test InsiderBuy - All fields populated")
    @Test
    public void testAllFieldsPopulated() {
        InsiderBuy buy = InsiderBuy.builder()
            .accessionNumber("0001234567-25-000001")
            .filingDate(LocalDateTime.now())
            .tradeDate(LocalDate.now())
            .ticker("AAPL")
            .companyName("Apple Inc.")
            .companyCik("0000320193")
            .insiderName("Tim Cook")
            .insiderCik("0001234567")
            .insiderTitle("CEO")
            .tradeType("P")
            .pricePerShare(new BigDecimal("150.00"))
            .quantity(new BigDecimal("1000"))
            .sharesOwnedAfter(new BigDecimal("10000"))
            .sharesOwnedBefore(new BigDecimal("9000"))
            .ownershipChangePercent(new BigDecimal("11.11"))
            .transactionValue(new BigDecimal("150000.00"))
            .ownershipType("D")
            .securityTitle("Common Stock")
            .oneDayChange(new BigDecimal("2.5"))
            .oneWeekChange(new BigDecimal("5.0"))
            .oneMonthChange(new BigDecimal("10.0"))
            .sixMonthChange(new BigDecimal("25.0"))
            .build();

        assertNotNull(buy);
        assertEquals("AAPL", buy.getTicker());
        assertEquals("Tim Cook", buy.getInsiderName());
        assertEquals("CEO", buy.getInsiderTitle());
        assertEquals(new BigDecimal("150000.00"), buy.getTransactionValue());
        assertEquals(new BigDecimal("11.11"), buy.getOwnershipChangePercent());
        assertTrue(buy.isDirectOwnership());
    }
}
