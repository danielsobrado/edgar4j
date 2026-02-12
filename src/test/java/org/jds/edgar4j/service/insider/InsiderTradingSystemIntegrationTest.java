package org.jds.edgar4j.service.insider;

import org.jds.edgar4j.model.insider.Company;
import org.jds.edgar4j.model.insider.Insider;
import org.jds.edgar4j.model.insider.TransactionType;
import org.jds.edgar4j.repository.insider.CompanyRepository;
import org.jds.edgar4j.repository.insider.InsiderRepository;
import org.jds.edgar4j.repository.insider.TransactionTypeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitExtension;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Phase 1 insider trading system foundation
 * 
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-01-01
 */
@ExtendWith(SpringJUnitExtension.class)
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "edgar4j.urls.submissionsCIKUrl=https://data.sec.gov/submissions/CIK",
    "edgar4j.urls.edgarDataArchivesUrl=https://www.sec.gov/Archives/edgar/data",
    "edgar4j.urls.companyTickersUrl=https://www.sec.gov/files/company_tickers.json"
})
@Transactional
class InsiderTradingSystemIntegrationTest {

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private InsiderRepository insiderRepository;

    @Autowired
    private TransactionTypeRepository transactionTypeRepository;

    @Autowired
    private CompanyService companyService;

    @Autowired
    private InsiderService insiderService;

    @DisplayName("Should initialize transaction types on startup")
    @Test
    void testTransactionTypesInitialization() {
        // Given & When
        List<TransactionType> transactionTypes = transactionTypeRepository.findByIsActiveTrueOrderBySortOrder();
        
        // Then
        assertNotNull(transactionTypes);
        assertFalse(transactionTypes.isEmpty());
        assertTrue(transactionTypes.size() >= 13); // Standard SEC transaction types
        
        // Verify key transaction types exist
        Optional<TransactionType> purchaseType = transactionTypeRepository.findByTransactionCode("P");
        Optional<TransactionType> saleType = transactionTypeRepository.findByTransactionCode("S");
        Optional<TransactionType> awardType = transactionTypeRepository.findByTransactionCode("A");
        
        assertTrue(purchaseType.isPresent());
        assertTrue(saleType.isPresent());
        assertTrue(awardType.isPresent());
        
        assertEquals("Purchase", purchaseType.get().getTransactionName());
        assertEquals("Sale", saleType.get().getTransactionName());
        assertEquals("Award", awardType.get().getTransactionName());
    }

    @DisplayName("Should create and retrieve company")
    @Test
    void testCompanyCreationAndRetrieval() {
        // Given
        String cik = "789019";
        String companyName = "Microsoft Corporation";
        String tickerSymbol = "MSFT";
        
        // When
        Company createdCompany = companyService.createFromSecData(cik, companyName, tickerSymbol);
        
        // Then
        assertNotNull(createdCompany);
        assertNotNull(createdCompany.getId());
        assertEquals("0000789019", createdCompany.getCik()); // Should be formatted with leading zeros
        assertEquals(companyName, createdCompany.getCompanyName());
        assertEquals(tickerSymbol, createdCompany.getTickerSymbol());
        assertTrue(createdCompany.getIsActive());
        
        // Test retrieval
        Optional<Company> retrievedCompany = companyService.findByCik(cik);
        assertTrue(retrievedCompany.isPresent());
        assertEquals(createdCompany.getId(), retrievedCompany.get().getId());
    }

    @DisplayName("Should create and retrieve insider")
    @Test
    void testInsiderCreationAndRetrieval() {
        // Given
        String cik = "1234567890";
        String fullName = "John William Smith Jr.";
        String address = "123 Main Street\nSeattle, WA 98101";
        
        // When
        Insider createdInsider = insiderService.createFromSecData(cik, fullName, address);
        
        // Then
        assertNotNull(createdInsider);
        assertNotNull(createdInsider.getId());
        assertEquals("1234567890", createdInsider.getCik());
        assertEquals(fullName, createdInsider.getFullName());
        assertEquals("John", createdInsider.getFirstName());
        assertEquals("William", createdInsider.getMiddleName());
        assertEquals("Smith", createdInsider.getLastName());
        assertEquals("Jr.", createdInsider.getSuffix());
        assertEquals("Seattle", createdInsider.getCity());
        assertEquals("WA", createdInsider.getState());
        assertEquals("98101", createdInsider.getZipCode());
        assertTrue(createdInsider.getIsActive());
        
        // Test retrieval
        Optional<Insider> retrievedInsider = insiderService.findByCik(cik);
        assertTrue(retrievedInsider.isPresent());
        assertEquals(createdInsider.getId(), retrievedInsider.get().getId());
    }

    @DisplayName("Should handle get or create company logic")
    @Test
    void testGetOrCreateCompany() {
        // Given
        String cik = "123456";
        String companyName = "Test Company";
        String tickerSymbol = "TEST";
        
        // When - First call should create
        Company company1 = companyService.getOrCreateCompany(cik, companyName, tickerSymbol);
        
        // Then
        assertNotNull(company1);
        assertTrue(companyService.companyExists(cik));
        
        // When - Second call should retrieve existing
        Company company2 = companyService.getOrCreateCompany(cik, companyName, tickerSymbol);
        
        // Then
        assertEquals(company1.getId(), company2.getId());
        assertEquals(1, companyRepository.findByCik("0000123456").stream().count());
    }

    @DisplayName("Should handle name parsing correctly")
    @Test
    void testNameParsing() {
        // Given
        Insider insider = new Insider();
        
        // Test simple name
        insider.setFullName("John Smith");
        insiderService.parseNameComponents(insider);
        assertEquals("John", insider.getFirstName());
        assertEquals("Smith", insider.getLastName());
        assertNull(insider.getMiddleName());
        assertNull(insider.getSuffix());
        
        // Test name with middle name
        insider.setFullName("Mary Jane Doe");
        insiderService.parseNameComponents(insider);
        assertEquals("Mary", insider.getFirstName());
        assertEquals("Jane", insider.getMiddleName());
        assertEquals("Doe", insider.getLastName());
        
        // Test name with suffix
        insider.setFullName("Robert James Wilson Jr.");
        insiderService.parseNameComponents(insider);
        assertEquals("Robert", insider.getFirstName());
        assertEquals("James", insider.getMiddleName());
        assertEquals("Wilson", insider.getLastName());
        assertEquals("Jr.", insider.getSuffix());
    }

    @DisplayName("Should format CIK correctly")
    @Test
    void testCikFormatting() {
        // Given
        Company company = new Company();
        
        // When
        company.setCik("123");
        
        // Then
        assertEquals("0000000123", company.getCik());
        
        // When
        company.setCik("789019");
        
        // Then
        assertEquals("0000789019", company.getCik());
    }

    @DisplayName("Should handle repository operations")
    @Test
    void testRepositoryOperations() {
        // Given
        Company company = Company.builder()
            .cik("0000111111")
            .companyName("Repository Test Company")
            .tickerSymbol("RTC")
            .isActive(true)
            .build();
        
        // When
        Company savedCompany = companyRepository.save(company);
        
        // Then
        assertNotNull(savedCompany.getId());
        assertTrue(companyRepository.existsByCik("0000111111"));
        assertEquals(1, companyRepository.findByCompanyNameContainingIgnoreCase("Repository").size());
        
        // Test active companies query
        List<Company> activeCompanies = companyRepository.findByIsActiveTrue();
        assertTrue(activeCompanies.stream().anyMatch(c -> c.getCik().equals("0000111111")));
    }
}
