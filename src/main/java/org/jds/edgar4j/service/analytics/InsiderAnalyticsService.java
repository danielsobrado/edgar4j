package org.jds.edgar4j.service.analytics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jds.edgar4j.model.insider.Company;
import org.jds.edgar4j.model.insider.InsiderTransaction;
import org.jds.edgar4j.service.provider.MarketDataService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Service for calculating advanced insider trading analytics and metrics
 * 
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-01-01
 */
@Slf4j
@Service
@Profile("resource-high")
@RequiredArgsConstructor
public class InsiderAnalyticsService {

    private final MarketDataService marketDataService;

    /**
     * Calculate comprehensive transaction analytics
     */
    public TransactionAnalytics calculateTransactionAnalytics(InsiderTransaction transaction) {
        if (transaction == null) {
            throw new IllegalArgumentException("Transaction is required");
        }

        log.debug("Calculating analytics for transaction: {}", transaction.getAccessionNumber());
        
        TransactionAnalytics analytics = new TransactionAnalytics();
        analytics.setTransactionId(transaction.getId());
        analytics.setAccessionNumber(transaction.getAccessionNumber());
        
        // Calculate transaction value if not already calculated
        if (transaction.getTransactionValue() == null && transaction.getSharesTransacted() != null && transaction.getPricePerShare() != null) {
            BigDecimal transactionValue = transaction.getSharesTransacted().multiply(transaction.getPricePerShare());
            analytics.setTransactionValue(transactionValue);
        } else {
            analytics.setTransactionValue(transaction.getTransactionValue());
        }
        
        // Calculate ownership change
        analytics.setOwnershipChange(calculateOwnershipChange(transaction));
        analytics.setOwnershipPercentageChange(calculateOwnershipPercentageChange(transaction));
        
        // Calculate significance scores
        analytics.setValueSignificanceScore(calculateValueSignificanceScore(transaction));
        analytics.setOwnershipSignificanceScore(calculateOwnershipSignificanceScore(transaction));
        analytics.setOverallSignificanceScore(calculateOverallSignificanceScore(analytics));
        
        // Market timing analysis
        analytics.setMarketTimingScore(calculateMarketTimingScore(transaction));
        
        // Transaction type classification
        analytics.setTransactionClassification(classifyTransaction(transaction));
        
        log.debug("Completed analytics calculation for transaction: {}", transaction.getAccessionNumber());
        return analytics;
    }

    /**
     * Calculate company-level insider trading metrics
     */
    public CompanyInsiderMetrics calculateCompanyMetrics(Company company, List<InsiderTransaction> transactions, LocalDate startDate, LocalDate endDate) {
        log.debug("Calculating company metrics for: {}", company.getTickerSymbol());
        
        // Filter transactions by date range
        List<InsiderTransaction> relevantTransactions = transactions.stream()
            .filter(t -> t != null && t.getTransactionDate() != null)
            .filter(t -> !t.getTransactionDate().isBefore(startDate) && !t.getTransactionDate().isAfter(endDate))
            .collect(Collectors.toList());
        
        CompanyInsiderMetrics metrics = new CompanyInsiderMetrics();
        metrics.setCompanyId(company.getId());
        metrics.setTickerSymbol(company.getTickerSymbol());
        metrics.setPeriodStartDate(startDate);
        metrics.setPeriodEndDate(endDate);
        
        // Basic transaction statistics
        metrics.setTotalTransactions(relevantTransactions.size());
        metrics.setPurchaseTransactions(countTransactionsByType(relevantTransactions, "P"));
        metrics.setSaleTransactions(countTransactionsByType(relevantTransactions, "S"));
        
        // Calculate total values
        BigDecimal totalPurchaseValue = calculateTotalValueByType(relevantTransactions, "P");
        BigDecimal totalSaleValue = calculateTotalValueByType(relevantTransactions, "S");
        metrics.setTotalPurchaseValue(totalPurchaseValue);
        metrics.setTotalSaleValue(totalSaleValue);
        metrics.setNetTransactionValue(totalPurchaseValue.subtract(totalSaleValue));
        
        // Calculate ratios and averages
        metrics.setBuySellRatio(calculateBuySellRatio(totalPurchaseValue, totalSaleValue));
        metrics.setAverageTransactionValue(calculateAverageTransactionValue(relevantTransactions));
        
        // Insider sentiment analysis
        metrics.setInsiderSentiment(calculateInsiderSentiment(relevantTransactions));
        
        // Transaction frequency analysis
        metrics.setTransactionFrequency(calculateTransactionFrequency(relevantTransactions, startDate, endDate));
        
        // Concentration metrics
        metrics.setTransactionConcentration(calculateTransactionConcentration(relevantTransactions));
        
        log.debug("Completed company metrics calculation for: {}", company.getTickerSymbol());
        return metrics;
    }

    /**
     * Calculate insider-level analytics
     */
    public InsiderMetrics calculateInsiderMetrics(String insiderCik, List<InsiderTransaction> transactions, LocalDate startDate, LocalDate endDate) {
        log.debug("Calculating insider metrics for CIK: {}", insiderCik);
        
        List<InsiderTransaction> relevantTransactions = transactions.stream()
            .filter(t -> t != null && t.getTransactionDate() != null)
            .filter(t -> !t.getTransactionDate().isBefore(startDate) && !t.getTransactionDate().isAfter(endDate))
            .collect(Collectors.toList());
        
        InsiderMetrics metrics = new InsiderMetrics();
        metrics.setInsiderCik(insiderCik);
        metrics.setPeriodStartDate(startDate);
        metrics.setPeriodEndDate(endDate);
        
        // Basic statistics
        metrics.setTotalTransactions(relevantTransactions.size());
        metrics.setTotalValue(calculateTotalTransactionValue(relevantTransactions));
        metrics.setAverageTransactionSize(calculateAverageTransactionValue(relevantTransactions));
        
        // Transaction patterns
        metrics.setTransactionPattern(analyzeTransactionPattern(relevantTransactions));
        metrics.setPreferredTransactionType(findMostCommonTransactionType(relevantTransactions));
        
        // Performance metrics
        metrics.setSuccessRate(calculateTransactionSuccessRate(relevantTransactions));
        metrics.setOverallPerformance(calculateOverallPerformance(relevantTransactions));
        
        return metrics;
    }

    private BigDecimal calculateOwnershipChange(InsiderTransaction transaction) {
        if (transaction.getSharesOwnedBefore() != null && transaction.getSharesOwnedAfter() != null) {
            return transaction.getSharesOwnedAfter().subtract(transaction.getSharesOwnedBefore());
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal calculateOwnershipPercentageChange(InsiderTransaction transaction) {
        if (transaction.getOwnershipPercentageBefore() != null && transaction.getOwnershipPercentageAfter() != null) {
            return transaction.getOwnershipPercentageAfter().subtract(transaction.getOwnershipPercentageBefore());
        }
        return BigDecimal.ZERO;
    }

    private int calculateValueSignificanceScore(InsiderTransaction transaction) {
        if (transaction.getTransactionValue() == null) {
            return 0;
        }
        
        BigDecimal value = transaction.getTransactionValue().abs();
        
        if (value.compareTo(BigDecimal.valueOf(10_000_000)) >= 0) return 10; // $10M+
        if (value.compareTo(BigDecimal.valueOf(5_000_000)) >= 0) return 9;   // $5M+
        if (value.compareTo(BigDecimal.valueOf(1_000_000)) >= 0) return 8;   // $1M+
        if (value.compareTo(BigDecimal.valueOf(500_000)) >= 0) return 7;     // $500K+
        if (value.compareTo(BigDecimal.valueOf(100_000)) >= 0) return 6;     // $100K+
        if (value.compareTo(BigDecimal.valueOf(50_000)) >= 0) return 5;      // $50K+
        if (value.compareTo(BigDecimal.valueOf(10_000)) >= 0) return 4;      // $10K+
        if (value.compareTo(BigDecimal.valueOf(5_000)) >= 0) return 3;       // $5K+
        if (value.compareTo(BigDecimal.valueOf(1_000)) >= 0) return 2;       // $1K+
        return 1;
    }

    private int calculateOwnershipSignificanceScore(InsiderTransaction transaction) {
        BigDecimal ownershipChange = calculateOwnershipPercentageChange(transaction).abs();
        
        if (ownershipChange.compareTo(BigDecimal.valueOf(5.0)) >= 0) return 10; // 5%+
        if (ownershipChange.compareTo(BigDecimal.valueOf(2.0)) >= 0) return 8;  // 2%+
        if (ownershipChange.compareTo(BigDecimal.valueOf(1.0)) >= 0) return 6;  // 1%+
        if (ownershipChange.compareTo(BigDecimal.valueOf(0.5)) >= 0) return 4;  // 0.5%+
        if (ownershipChange.compareTo(BigDecimal.valueOf(0.1)) >= 0) return 2;  // 0.1%+
        return 1;
    }

    private int calculateOverallSignificanceScore(TransactionAnalytics analytics) {
        return (int) Math.round((analytics.getValueSignificanceScore() * 0.6 + 
                                analytics.getOwnershipSignificanceScore() * 0.4));
    }

    private int calculateMarketTimingScore(InsiderTransaction transaction) {
        Double directionalReturn = calculateDirectionalReturn(transaction);
        if (directionalReturn == null) {
            return 5;
        }

        if (directionalReturn >= 0.25) return 10;
        if (directionalReturn >= 0.15) return 9;
        if (directionalReturn >= 0.10) return 8;
        if (directionalReturn >= 0.05) return 7;
        if (directionalReturn >= 0.02) return 6;
        if (directionalReturn > -0.02) return 5;
        if (directionalReturn > -0.05) return 4;
        if (directionalReturn > -0.10) return 3;
        if (directionalReturn > -0.15) return 2;
        if (directionalReturn > -0.25) return 1;
        return 0;
    }

    private String classifyTransaction(InsiderTransaction transaction) {
        if (Boolean.TRUE.equals(transaction.getIsDerivative())) {
            if ("M".equals(transaction.getTransactionCode())) {
                return "Option Exercise";
            } else if ("A".equals(transaction.getTransactionCode())) {
                return "Derivative Grant";
            }
            return "Derivative Transaction";
        } else {
            if ("P".equals(transaction.getTransactionCode())) {
                return "Purchase";
            } else if ("S".equals(transaction.getTransactionCode())) {
                return "Sale";
            } else if ("A".equals(transaction.getTransactionCode())) {
                return "Award/Grant";
            }
            return "Other";
        }
    }

    private long countTransactionsByType(List<InsiderTransaction> transactions, String transactionCode) {
        return transactions.stream()
            .filter(t -> t != null && transactionCode.equals(t.getTransactionCode()))
            .count();
    }

    private BigDecimal calculateTotalValueByType(List<InsiderTransaction> transactions, String transactionCode) {
        return transactions.stream()
            .filter(t -> t != null && transactionCode.equals(t.getTransactionCode()))
            .filter(t -> t.getTransactionValue() != null)
            .map(InsiderTransaction::getTransactionValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateTotalTransactionValue(List<InsiderTransaction> transactions) {
        return transactions.stream()
            .filter(t -> t != null)
            .filter(t -> t.getTransactionValue() != null)
            .map(InsiderTransaction::getTransactionValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateBuySellRatio(BigDecimal totalPurchaseValue, BigDecimal totalSaleValue) {
        if (totalSaleValue.compareTo(BigDecimal.ZERO) == 0) {
            return totalPurchaseValue.compareTo(BigDecimal.ZERO) > 0 ? BigDecimal.valueOf(Double.MAX_VALUE) : BigDecimal.ZERO;
        }
        return totalPurchaseValue.divide(totalSaleValue, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateAverageTransactionValue(List<InsiderTransaction> transactions) {
        List<InsiderTransaction> validTransactions = transactions.stream()
            .filter(t -> t != null)
            .filter(t -> t.getTransactionValue() != null)
            .collect(Collectors.toList());
        
        if (validTransactions.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal total = calculateTotalTransactionValue(validTransactions);
        return total.divide(BigDecimal.valueOf(validTransactions.size()), 2, RoundingMode.HALF_UP);
    }

    private String calculateInsiderSentiment(List<InsiderTransaction> transactions) {
        long purchases = countTransactionsByType(transactions, "P");
        long sales = countTransactionsByType(transactions, "S");
        
        if (purchases > sales * 2) {
            return "BULLISH";
        } else if (sales > purchases * 2) {
            return "BEARISH";
        } else {
            return "NEUTRAL";
        }
    }

    private double calculateTransactionFrequency(List<InsiderTransaction> transactions, LocalDate startDate, LocalDate endDate) {
        if (transactions.isEmpty()) {
            return 0.0;
        }
        
        long daysBetween = ChronoUnit.DAYS.between(startDate, endDate);
        if (daysBetween <= 0) {
            return transactions.size();
        }
        return (double) transactions.size() / daysBetween * 30; // Transactions per month
    }

    private double calculateTransactionConcentration(List<InsiderTransaction> transactions) {
        if (transactions.isEmpty()) {
            return 0.0;
        }
        
        // Group transactions by insider
        Map<String, Long> transactionsByInsider = transactions.stream()
            .filter(t -> t != null && t.getInsider() != null && t.getInsider().getCik() != null)
            .collect(Collectors.groupingBy(
                t -> t.getInsider().getCik(),
                Collectors.counting()
            ));

        if (transactionsByInsider.isEmpty()) {
            return 0.0;
        }
        
        // Calculate Herfindahl-Hirschman Index
        long totalTransactions = transactionsByInsider.values().stream()
            .mapToLong(Long::longValue)
            .sum();
        double hhi = transactionsByInsider.values().stream()
            .mapToDouble(count -> {
                double share = (double) count / totalTransactions;
                return share * share;
            })
            .sum();
        
        return hhi * 10000; // Normalize to 0-10000 scale
    }

    private String analyzeTransactionPattern(List<InsiderTransaction> transactions) {
        if (transactions.isEmpty()) {
            return "NONE";
        }

        List<InsiderTransaction> orderedTransactions = transactions.stream()
            .filter(t -> t != null)
            .filter(t -> t.getTransactionDate() != null)
            .sorted(Comparator.comparing(InsiderTransaction::getTransactionDate))
            .collect(Collectors.toList());

        if (orderedTransactions.size() < 2) {
            return "INFREQUENT";
        }

        long activeDays = ChronoUnit.DAYS.between(
            orderedTransactions.get(0).getTransactionDate(),
            orderedTransactions.get(orderedTransactions.size() - 1).getTransactionDate()) + 1;
        if (activeDays <= 7 && orderedTransactions.size() >= 3) {
            return "CLUSTERED";
        }

        long distinctTransactionCodes = orderedTransactions.stream()
            .map(InsiderTransaction::getTransactionCode)
            .filter(Objects::nonNull)
            .distinct()
            .count();
        if (distinctTransactionCodes > 2) {
            return "MIXED";
        }

        double monthlyFrequency = (double) orderedTransactions.size() / Math.max(activeDays, 1) * 30;
        if (monthlyFrequency >= 4.0) {
            return "FREQUENT";
        }
        if (monthlyFrequency <= 0.5) {
            return "INFREQUENT";
        }
        return "REGULAR";
    }

    private String findMostCommonTransactionType(List<InsiderTransaction> transactions) {
        Map<String, Long> typeCount = transactions.stream()
            .filter(t -> t != null)
            .map(InsiderTransaction::getTransactionCode)
            .filter(Objects::nonNull)
            .collect(Collectors.groupingBy(
                code -> code,
                Collectors.counting()
            ));
        
        return typeCount.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("UNKNOWN");
    }

    private double calculateTransactionSuccessRate(List<InsiderTransaction> transactions) {
        List<Double> directionalReturns = transactions.stream()
            .map(this::calculateDirectionalReturn)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        if (directionalReturns.isEmpty()) {
            return 0.0;
        }

        long successfulTransactions = directionalReturns.stream()
            .filter(returnValue -> returnValue > 0.0)
            .count();
        return (double) successfulTransactions / directionalReturns.size() * 100.0;
    }

    private double calculateOverallPerformance(List<InsiderTransaction> transactions) {
        return transactions.stream()
            .map(this::calculateDirectionalReturn)
            .filter(Objects::nonNull)
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0) * 100.0;
    }

    private Double calculateDirectionalReturn(InsiderTransaction transaction) {
        if (transaction == null || transaction.getCompany() == null || transaction.getTransactionDate() == null) {
            return null;
        }

        String symbol = transaction.getCompany().getTickerSymbol();
        if (symbol == null || symbol.isBlank()) {
            return null;
        }

        BigDecimal entryPrice = transaction.getPricePerShare();
        if (entryPrice == null || entryPrice.compareTo(BigDecimal.ZERO) <= 0) {
            entryPrice = fetchPrice(symbol, transaction.getTransactionDate());
        }

        BigDecimal exitPrice = fetchPrice(symbol, transaction.getTransactionDate().plusDays(30));
        if (entryPrice == null || exitPrice == null || entryPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        BigDecimal rawReturn = exitPrice.subtract(entryPrice)
            .divide(entryPrice, 8, RoundingMode.HALF_UP);

        if (isSale(transaction)) {
            rawReturn = rawReturn.negate();
        } else if (!isPurchase(transaction)) {
            return null;
        }

        return rawReturn.doubleValue();
    }

    private BigDecimal fetchPrice(String symbol, LocalDate date) {
        try {
            return marketDataService.getPriceForDate(symbol, date).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("Unable to fetch market price for {} on {}", symbol, date, e);
            return null;
        }
    }

    private boolean isPurchase(InsiderTransaction transaction) {
        return transaction != null && ("P".equals(transaction.getTransactionCode())
            || InsiderTransaction.AcquiredDisposed.ACQUIRED.equals(transaction.getAcquiredDisposed()));
    }

    private boolean isSale(InsiderTransaction transaction) {
        return transaction != null && ("S".equals(transaction.getTransactionCode())
            || InsiderTransaction.AcquiredDisposed.DISPOSED.equals(transaction.getAcquiredDisposed()));
    }

    /**
     * Transaction analytics data transfer object
     */
    public static class TransactionAnalytics {
        private Long transactionId;
        private String accessionNumber;
        private BigDecimal transactionValue;
        private BigDecimal ownershipChange;
        private BigDecimal ownershipPercentageChange;
        private int valueSignificanceScore;
        private int ownershipSignificanceScore;
        private int overallSignificanceScore;
        private int marketTimingScore;
        private String transactionClassification;

        // Getters and setters
        public Long getTransactionId() { return transactionId; }
        public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }

        public String getAccessionNumber() { return accessionNumber; }
        public void setAccessionNumber(String accessionNumber) { this.accessionNumber = accessionNumber; }

        public BigDecimal getTransactionValue() { return transactionValue; }
        public void setTransactionValue(BigDecimal transactionValue) { this.transactionValue = transactionValue; }

        public BigDecimal getOwnershipChange() { return ownershipChange; }
        public void setOwnershipChange(BigDecimal ownershipChange) { this.ownershipChange = ownershipChange; }

        public BigDecimal getOwnershipPercentageChange() { return ownershipPercentageChange; }
        public void setOwnershipPercentageChange(BigDecimal ownershipPercentageChange) { this.ownershipPercentageChange = ownershipPercentageChange; }

        public int getValueSignificanceScore() { return valueSignificanceScore; }
        public void setValueSignificanceScore(int valueSignificanceScore) { this.valueSignificanceScore = valueSignificanceScore; }

        public int getOwnershipSignificanceScore() { return ownershipSignificanceScore; }
        public void setOwnershipSignificanceScore(int ownershipSignificanceScore) { this.ownershipSignificanceScore = ownershipSignificanceScore; }

        public int getOverallSignificanceScore() { return overallSignificanceScore; }
        public void setOverallSignificanceScore(int overallSignificanceScore) { this.overallSignificanceScore = overallSignificanceScore; }

        public int getMarketTimingScore() { return marketTimingScore; }
        public void setMarketTimingScore(int marketTimingScore) { this.marketTimingScore = marketTimingScore; }

        public String getTransactionClassification() { return transactionClassification; }
        public void setTransactionClassification(String transactionClassification) { this.transactionClassification = transactionClassification; }
    }

    /**
     * Company-level insider metrics
     */
    public static class CompanyInsiderMetrics {
        private Long companyId;
        private String tickerSymbol;
        private LocalDate periodStartDate;
        private LocalDate periodEndDate;
        private int totalTransactions;
        private long purchaseTransactions;
        private long saleTransactions;
        private BigDecimal totalPurchaseValue;
        private BigDecimal totalSaleValue;
        private BigDecimal netTransactionValue;
        private BigDecimal buySellRatio;
        private BigDecimal averageTransactionValue;
        private String insiderSentiment;
        private double transactionFrequency;
        private double transactionConcentration;

        // Getters and setters
        public Long getCompanyId() { return companyId; }
        public void setCompanyId(Long companyId) { this.companyId = companyId; }

        public String getTickerSymbol() { return tickerSymbol; }
        public void setTickerSymbol(String tickerSymbol) { this.tickerSymbol = tickerSymbol; }

        public LocalDate getPeriodStartDate() { return periodStartDate; }
        public void setPeriodStartDate(LocalDate periodStartDate) { this.periodStartDate = periodStartDate; }

        public LocalDate getPeriodEndDate() { return periodEndDate; }
        public void setPeriodEndDate(LocalDate periodEndDate) { this.periodEndDate = periodEndDate; }

        public int getTotalTransactions() { return totalTransactions; }
        public void setTotalTransactions(int totalTransactions) { this.totalTransactions = totalTransactions; }

        public long getPurchaseTransactions() { return purchaseTransactions; }
        public void setPurchaseTransactions(long purchaseTransactions) { this.purchaseTransactions = purchaseTransactions; }

        public long getSaleTransactions() { return saleTransactions; }
        public void setSaleTransactions(long saleTransactions) { this.saleTransactions = saleTransactions; }

        public BigDecimal getTotalPurchaseValue() { return totalPurchaseValue; }
        public void setTotalPurchaseValue(BigDecimal totalPurchaseValue) { this.totalPurchaseValue = totalPurchaseValue; }

        public BigDecimal getTotalSaleValue() { return totalSaleValue; }
        public void setTotalSaleValue(BigDecimal totalSaleValue) { this.totalSaleValue = totalSaleValue; }

        public BigDecimal getNetTransactionValue() { return netTransactionValue; }
        public void setNetTransactionValue(BigDecimal netTransactionValue) { this.netTransactionValue = netTransactionValue; }

        public BigDecimal getBuySellRatio() { return buySellRatio; }
        public void setBuySellRatio(BigDecimal buySellRatio) { this.buySellRatio = buySellRatio; }

        public BigDecimal getAverageTransactionValue() { return averageTransactionValue; }
        public void setAverageTransactionValue(BigDecimal averageTransactionValue) { this.averageTransactionValue = averageTransactionValue; }

        public String getInsiderSentiment() { return insiderSentiment; }
        public void setInsiderSentiment(String insiderSentiment) { this.insiderSentiment = insiderSentiment; }

        public double getTransactionFrequency() { return transactionFrequency; }
        public void setTransactionFrequency(double transactionFrequency) { this.transactionFrequency = transactionFrequency; }

        public double getTransactionConcentration() { return transactionConcentration; }
        public void setTransactionConcentration(double transactionConcentration) { this.transactionConcentration = transactionConcentration; }
    }

    /**
     * Insider-level metrics
     */
    public static class InsiderMetrics {
        private String insiderCik;
        private LocalDate periodStartDate;
        private LocalDate periodEndDate;
        private int totalTransactions;
        private BigDecimal totalValue;
        private BigDecimal averageTransactionSize;
        private String transactionPattern;
        private String preferredTransactionType;
        private double successRate;
        private double overallPerformance;

        // Getters and setters
        public String getInsiderCik() { return insiderCik; }
        public void setInsiderCik(String insiderCik) { this.insiderCik = insiderCik; }

        public LocalDate getPeriodStartDate() { return periodStartDate; }
        public void setPeriodStartDate(LocalDate periodStartDate) { this.periodStartDate = periodStartDate; }

        public LocalDate getPeriodEndDate() { return periodEndDate; }
        public void setPeriodEndDate(LocalDate periodEndDate) { this.periodEndDate = periodEndDate; }

        public int getTotalTransactions() { return totalTransactions; }
        public void setTotalTransactions(int totalTransactions) { this.totalTransactions = totalTransactions; }

        public BigDecimal getTotalValue() { return totalValue; }
        public void setTotalValue(BigDecimal totalValue) { this.totalValue = totalValue; }

        public BigDecimal getAverageTransactionSize() { return averageTransactionSize; }
        public void setAverageTransactionSize(BigDecimal averageTransactionSize) { this.averageTransactionSize = averageTransactionSize; }

        public String getTransactionPattern() { return transactionPattern; }
        public void setTransactionPattern(String transactionPattern) { this.transactionPattern = transactionPattern; }

        public String getPreferredTransactionType() { return preferredTransactionType; }
        public void setPreferredTransactionType(String preferredTransactionType) { this.preferredTransactionType = preferredTransactionType; }

        public double getSuccessRate() { return successRate; }
        public void setSuccessRate(double successRate) { this.successRate = successRate; }

        public double getOverallPerformance() { return overallPerformance; }
        public void setOverallPerformance(double overallPerformance) { this.overallPerformance = overallPerformance; }
    }
}
