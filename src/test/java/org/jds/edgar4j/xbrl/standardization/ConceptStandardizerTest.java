package org.jds.edgar4j.xbrl.standardization;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConceptStandardizerTest {

    private final ConceptStandardizer standardizer = new ConceptStandardizer();

    @Test
    @DisplayName("mapToStandard should cover extended dividend and coverage concepts")
    void mapToStandardShouldCoverExtendedDividendAndCoverageConcepts() {
        assertEquals("DividendsPerShareCashPaid",
                standardizer.mapToStandard("CommonStockDividendsPerShareCashPaid"));
        assertEquals("DividendsPayable",
                standardizer.mapToStandard("DividendsPayableCurrentAndNoncurrent"));
        assertEquals("PreferredDividendsPaid",
                standardizer.mapToStandard("PaymentsOfDividendsPreferredStockAndPreferenceStock"));
        assertEquals("TotalDividendsPaid",
                standardizer.mapToStandard("PaymentsOfDividends"));
        assertEquals("InterestExpense",
                standardizer.mapToStandard("InterestExpenseDebt"));
        assertEquals("IncomeBeforeTaxes",
                standardizer.mapToStandard("IncomeLossFromContinuingOperationsBeforeIncomeTaxes"));
        assertEquals("DepreciationAmortization",
                standardizer.mapToStandard("DepreciationDepletionAndAmortization"));
    }

    @Test
    @DisplayName("mapToStandard should cover liquidity, leverage, and share-count concepts")
    void mapToStandardShouldCoverLiquidityLeverageAndShareCountConcepts() {
        assertEquals("ShortTermDebt", standardizer.mapToStandard("DebtCurrent"));
        assertEquals("AccountsReceivable", standardizer.mapToStandard("ReceivablesNetCurrent"));
        assertEquals("ShortTermInvestments", standardizer.mapToStandard("MarketableSecuritiesCurrent"));
        assertEquals("ShareRepurchases", standardizer.mapToStandard("PaymentsForRepurchaseOfCommonStock"));
        assertEquals("ShareIssuance", standardizer.mapToStandard("ProceedsFromIssuanceOfCommonStock"));
        assertEquals("WeightedAvgSharesDiluted",
                standardizer.mapToStandard("WeightedAverageNumberOfDilutedSharesOutstanding"));
        assertEquals("WeightedAvgSharesBasic",
                standardizer.mapToStandard("WeightedAverageNumberOfSharesOutstandingBasic"));
    }
}
