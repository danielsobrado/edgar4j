package org.jds.edgar4j.test;

import org.jds.edgar4j.service.insider.EdgarApiService;
import org.jds.edgar4j.service.insider.Form4ParserService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.time.LocalDate;
import java.util.List;

/**
 * Manual test runner for Edgar4J Phase 2 functionality
 * Use this to manually test the end-to-end pipeline
 * 
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-01-01
 */
@SpringBootApplication(scanBasePackages = "org.jds.edgar4j")
public class ManualTestRunner {

    public static void main(String[] args) {
        System.out.println("=== Edgar4J Phase 2 Manual Test Runner ===");
        
        ConfigurableApplicationContext context = SpringApplication.run(ManualTestRunner.class, args);
        
        try {
            testDownloadFunctionality(context);
            testForm4Parsing(context);
            testDateRangeProcessing(context);
        } catch (Exception e) {
            System.err.println("Test execution failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            context.close();
        }
    }
    
    private static void testDownloadFunctionality(ConfigurableApplicationContext context) {
        System.out.println("\n--- Testing Download Functionality ---");
        
        EdgarApiService edgarApiService = context.getBean(EdgarApiService.class);
        
        try {
            // Test company tickers download
            System.out.println("1. Testing company tickers download...");
            var tickersFuture = edgarApiService.getCompanyTickers();
            var tickers = tickersFuture.get();
            System.out.println("✅ Successfully downloaded " + tickers.size() + " company tickers");
            
            // Test recent Form 4 filings for Microsoft
            System.out.println("2. Testing Form 4 filings for Microsoft (CIK: 789019)...");
            var filingsFuture = edgarApiService.getRecentForm4Filings("789019");
            var filings = filingsFuture.get();
            System.out.println("✅ Found " + filings.size() + " recent Form 4 filings for Microsoft");
            
            // Test Form 4 document download if filings exist
            if (!filings.isEmpty()) {
                var filing = filings.get(0);
                System.out.println("3. Testing Form 4 document download...");
                var docFuture = edgarApiService.downloadForm4Document("789019", 
                    filing.getAccessionNumber(), filing.getPrimaryDocument());
                var xmlContent = docFuture.get();
                System.out.println("✅ Successfully downloaded Form 4 document (" + 
                    xmlContent.length() + " characters)");
            }
            
        } catch (Exception e) {
            System.err.println("❌ Download test failed: " + e.getMessage());
        }
    }
    
    private static void testForm4Parsing(ConfigurableApplicationContext context) {
        System.out.println("\n--- Testing Form 4 Parsing ---");
        
        Form4ParserService form4ParserService = context.getBean(Form4ParserService.class);
        
        String sampleXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <ownershipDocument>
                <documentType>4</documentType>
                <periodOfReport>2024-01-15</periodOfReport>
                <issuer>
                    <issuerCik>0000789019</issuerCik>
                    <issuerName>MICROSOFT CORP</issuerName>
                    <issuerTradingSymbol>MSFT</issuerTradingSymbol>
                </issuer>
                <reportingOwner>
                    <reportingOwnerId>
                        <rptOwnerCik>0001234567</rptOwnerCik>
                        <rptOwnerName>SMITH JOHN</rptOwnerName>
                    </reportingOwnerId>
                    <reportingOwnerAddress>
                        <rptOwnerStreet1>123 Main Street</rptOwnerStreet1>
                        <rptOwnerCity>Seattle</rptOwnerCity>
                        <rptOwnerState>WA</rptOwnerState>
                        <rptOwnerZipCode>98101</rptOwnerZipCode>
                    </reportingOwnerAddress>
                    <reportingOwnerRelationship>
                        <isDirector>1</isDirector>
                        <isOfficer>0</isOfficer>
                        <isTenPercentOwner>0</isTenPercentOwner>
                        <isOther>0</isOther>
                    </reportingOwnerRelationship>
                </reportingOwner>
            </ownershipDocument>
            """;
        
        try {
            System.out.println("1. Testing XML validation...");
            boolean isValid = form4ParserService.validateForm4Xml(sampleXml);
            System.out.println("✅ XML validation result: " + isValid);
            
            System.out.println("2. Testing issuer extraction...");
            var issuerInfo = form4ParserService.extractIssuerInfo(sampleXml);
            System.out.println("✅ Extracted issuer: " + issuerInfo.getName() + 
                " (CIK: " + issuerInfo.getCik() + ", Symbol: " + issuerInfo.getTradingSymbol() + ")");
            
            System.out.println("3. Testing reporting owner extraction...");
            var ownerInfo = form4ParserService.extractReportingOwnerInfo(sampleXml);
            System.out.println("✅ Extracted owner: " + ownerInfo.getName() + 
                " (CIK: " + ownerInfo.getCik() + ", Director: " + ownerInfo.isDirector() + ")");
            
        } catch (Exception e) {
            System.err.println("❌ Form 4 parsing test failed: " + e.getMessage());
        }
    }
    
    private static void testDateRangeProcessing(ConfigurableApplicationContext context) {
        System.out.println("\n--- Testing Date Range Processing ---");
        
        EdgarApiService edgarApiService = context.getBean(EdgarApiService.class);
        
        try {
            // Test recent business day
            LocalDate endDate = LocalDate.now().minusDays(1);
            while (endDate.getDayOfWeek().getValue() > 5) { // Skip weekends
                endDate = endDate.minusDays(1);
            }
            LocalDate startDate = endDate.minusDays(2);
            
            System.out.println("1. Testing date range processing (" + startDate + " to " + endDate + ")...");
            List<String> accessionNumbers = edgarApiService.getForm4FilingsByDateRange(startDate, endDate);
            System.out.println("✅ Found " + accessionNumbers.size() + " Form 4 filings in date range");
            
            System.out.println("2. Testing daily index processing for " + endDate + "...");
            List<String> dailyAccessionNumbers = edgarApiService.getForm4FilingsFromDailyIndex(endDate);
            System.out.println("✅ Found " + dailyAccessionNumbers.size() + " Form 4 filings for " + endDate);
            
            // Test weekend skipping
            LocalDate saturday = endDate.with(java.time.DayOfWeek.SATURDAY);
            System.out.println("3. Testing weekend skipping for " + saturday + "...");
            List<String> weekendResults = edgarApiService.getForm4FilingsFromDailyIndex(saturday);
            System.out.println("✅ Weekend results (should be empty): " + weekendResults.size());
            
        } catch (Exception e) {
            System.err.println("❌ Date range processing test failed: " + e.getMessage());
        }
    }
}
