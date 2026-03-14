package org.jds.edgar4j.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.jds.edgar4j.model.insider.Company;
import org.jds.edgar4j.model.insider.Insider;
import org.jds.edgar4j.model.insider.InsiderCompanyRelationship;
import org.jds.edgar4j.model.insider.InsiderTransaction;
import org.jds.edgar4j.model.insider.TransactionType;
import org.jds.edgar4j.port.InsiderCompanyDataPort;
import org.jds.edgar4j.port.InsiderCompanyRelationshipDataPort;
import org.jds.edgar4j.port.InsiderDataPort;
import org.jds.edgar4j.port.InsiderTransactionDataPort;
import org.jds.edgar4j.port.TransactionTypeDataPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(InsiderMongoContractTestConfig.class)
@ActiveProfiles({"test", "resource-high", "mongo-contract"})
class InsiderMongoAdaptersSmokeTest {

    @Autowired
    private InsiderCompanyDataPort companyDataPort;

    @Autowired
    private InsiderCompanyRelationshipDataPort relationshipDataPort;

    @Autowired
    private InsiderDataPort insiderDataPort;

    @Autowired
    private InsiderTransactionDataPort transactionDataPort;

    @Autowired
    private TransactionTypeDataPort transactionTypeDataPort;

    @BeforeEach
    void setUp() {
        transactionDataPort.deleteAll();
        relationshipDataPort.deleteAll();
        insiderDataPort.deleteAll();
        companyDataPort.deleteAll();
        transactionTypeDataPort.deleteAll();
    }

    @Test
    void insiderMongoPortsSaveAndQueryEntitiesWithGeneratedIds() {
        TransactionType transactionType = transactionTypeDataPort.save(TransactionType.builder()
                .transactionCode("P")
                .transactionName("Purchase")
                .transactionDescription("Open market purchase")
                .transactionCategory(TransactionType.TransactionCategory.PURCHASE)
                .sortOrder(1)
                .isActive(true)
                .build());

        Company company = companyDataPort.save(Company.builder()
                .cik("0000001234")
                .companyName("Acme Corp")
                .tickerSymbol("ACME")
                .isActive(true)
                .build());

        Insider insider = insiderDataPort.save(Insider.builder()
                .cik("0000005678")
                .fullName("Jane Doe")
                .insiderType(Insider.InsiderType.INDIVIDUAL)
                .isActive(true)
                .build());

        InsiderCompanyRelationship relationship = relationshipDataPort.save(
                InsiderCompanyRelationship.createDirectorRelationship(insider, company));

        InsiderTransaction transaction = transactionDataPort.save(InsiderTransaction.builder()
                .company(company)
                .insider(insider)
                .transactionType(transactionType)
                .accessionNumber("0001234567-24-000001")
                .transactionDate(LocalDate.of(2024, 1, 15))
                .filingDate(LocalDate.of(2024, 1, 16))
                .securityTitle("Common Stock")
                .transactionCode("P")
                .sharesTransacted(new BigDecimal("100"))
                .pricePerShare(new BigDecimal("10.00"))
                .sharesOwnedBefore(new BigDecimal("1000"))
                .sharesOwnedAfter(new BigDecimal("1100"))
                .acquiredDisposed(InsiderTransaction.AcquiredDisposed.ACQUIRED)
                .ownershipNature(InsiderTransaction.OwnershipNature.DIRECT)
                .isDerivative(false)
                .build());

        assertThat(transactionType.getId()).isNotNull();
        assertThat(company.getId()).isNotNull();
        assertThat(insider.getId()).isNotNull();
        assertThat(relationship.getId()).isNotNull();
        assertThat(transaction.getId()).isNotNull();

        assertThat(transactionTypeDataPort.findByTransactionCode("P")).isPresent();
        assertThat(companyDataPort.findByCik("0000001234")).isPresent();
        assertThat(insiderDataPort.findByCik("0000005678")).isPresent();
        assertThat(relationshipDataPort.findCurrentRelationshipsForCompany("0000001234", LocalDate.now())).isNotEmpty();
        assertThat(transactionDataPort.findByAccessionNumber("0001234567-24-000001")).isPresent();
    }
}
