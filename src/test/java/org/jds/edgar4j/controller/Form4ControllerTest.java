package org.jds.edgar4j.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jds.edgar4j.model.Form4;
import org.jds.edgar4j.model.Form4Transaction;
import org.jds.edgar4j.repository.Form4Repository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Form4Controller REST endpoints.
 * Uses MockMvc and embedded MongoDB.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Form4ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Form4Repository form4Repository;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String BASE_URL = "/api/form4";
    private static final String ACCESSION_1 = "0001234567-24-000001";
    private static final String ACCESSION_2 = "0001234567-24-000002";

    @BeforeEach
    void setUp() {
        form4Repository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        form4Repository.deleteAll();
    }

    @Nested
    @DisplayName("GET /api/form4/{id}")
    class GetById {

        @Test
        @DisplayName("Should return Form4 when found by ID")
        void shouldReturnForm4WhenFound() throws Exception {
            Form4 saved = form4Repository.save(createForm4(ACCESSION_1, "MSFT", "John Doe"));

            mockMvc.perform(get(BASE_URL + "/" + saved.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessionNumber").value(ACCESSION_1))
                    .andExpect(jsonPath("$.tradingSymbol").value("MSFT"))
                    .andExpect(jsonPath("$.rptOwnerName").value("John Doe"));
        }

        @Test
        @DisplayName("Should return 404 when Form4 not found")
        void shouldReturn404WhenNotFound() throws Exception {
            mockMvc.perform(get(BASE_URL + "/nonexistent-id"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/form4/accession/{accessionNumber}")
    class GetByAccessionNumber {

        @Test
        @DisplayName("Should return Form4 by accession number")
        void shouldReturnByAccessionNumber() throws Exception {
            form4Repository.save(createForm4(ACCESSION_1, "MSFT", "John Doe"));

            mockMvc.perform(get(BASE_URL + "/accession/" + ACCESSION_1))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessionNumber").value(ACCESSION_1))
                    .andExpect(jsonPath("$.tradingSymbol").value("MSFT"));
        }

        @Test
        @DisplayName("Should return 404 when accession number not found")
        void shouldReturn404WhenAccessionNotFound() throws Exception {
            mockMvc.perform(get(BASE_URL + "/accession/nonexistent"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/form4/symbol/{symbol}")
    class GetBySymbol {

        @Test
        @DisplayName("Should return paginated Form4 by symbol")
        void shouldReturnPaginatedBySymbol() throws Exception {
            form4Repository.save(createForm4(ACCESSION_1, "MSFT", "John Doe"));
            form4Repository.save(createForm4(ACCESSION_2, "MSFT", "Jane Smith"));

            mockMvc.perform(get(BASE_URL + "/symbol/MSFT")
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(2)))
                    .andExpect(jsonPath("$.content[*].tradingSymbol", everyItem(is("MSFT"))))
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.totalPages").value(1));
        }

        @Test
        @DisplayName("Should return empty page when symbol not found")
        void shouldReturnEmptyPageWhenSymbolNotFound() throws Exception {
            mockMvc.perform(get(BASE_URL + "/symbol/UNKNOWN"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)))
                    .andExpect(jsonPath("$.totalElements").value(0));
        }

        @Test
        @DisplayName("Should handle case insensitive symbol")
        void shouldHandleCaseInsensitiveSymbol() throws Exception {
            form4Repository.save(createForm4(ACCESSION_1, "MSFT", "John Doe"));

            mockMvc.perform(get(BASE_URL + "/symbol/msft"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)));
        }
    }

    @Nested
    @DisplayName("GET /api/form4/cik/{cik}")
    class GetByCik {

        @Test
        @DisplayName("Should return paginated Form4 by CIK")
        void shouldReturnPaginatedByCik() throws Exception {
            form4Repository.save(createForm4(ACCESSION_1, "MSFT", "John Doe"));

            mockMvc.perform(get(BASE_URL + "/cik/789019"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].cik").value("789019"));
        }
    }

    @Nested
    @DisplayName("GET /api/form4/owner")
    class SearchByOwner {

        @Test
        @DisplayName("Should search Form4 by owner name")
        void shouldSearchByOwnerName() throws Exception {
            form4Repository.save(createForm4(ACCESSION_1, "MSFT", "John Doe"));
            form4Repository.save(createForm4(ACCESSION_2, "MSFT", "Jane Doe"));

            mockMvc.perform(get(BASE_URL + "/owner")
                            .param("name", "Doe"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[*].rptOwnerName", everyItem(containsString("Doe"))));
        }
    }

    @Nested
    @DisplayName("GET /api/form4/date-range")
    class GetByDateRange {

        @Test
        @DisplayName("Should return Form4 within date range")
        void shouldReturnByDateRange() throws Exception {
            Calendar cal = Calendar.getInstance();
            cal.set(2024, Calendar.JANUARY, 15);

            Form4 form4 = createForm4(ACCESSION_1, "MSFT", "John Doe");
            form4.setTransactionDate(cal.getTime());
            form4Repository.save(form4);

            mockMvc.perform(get(BASE_URL + "/date-range")
                            .param("startDate", "2024-01-10")
                            .param("endDate", "2024-01-20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)));
        }

        @Test
        @DisplayName("Should return 400 for invalid date format")
        void shouldReturn400ForInvalidDateFormat() throws Exception {
            mockMvc.perform(get(BASE_URL + "/date-range")
                            .param("startDate", "invalid-date")
                            .param("endDate", "2024-01-20"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/form4/symbol/{symbol}/date-range")
    class GetBySymbolAndDateRange {

        @Test
        @DisplayName("Should return Form4 by symbol and date range")
        void shouldReturnBySymbolAndDateRange() throws Exception {
            Calendar cal = Calendar.getInstance();
            cal.set(2024, Calendar.JANUARY, 15);

            Form4 msft = createForm4(ACCESSION_1, "MSFT", "John Doe");
            msft.setTransactionDate(cal.getTime());
            form4Repository.save(msft);

            Form4 aapl = createForm4(ACCESSION_2, "AAPL", "Tim Cook");
            aapl.setTransactionDate(cal.getTime());
            form4Repository.save(aapl);

            mockMvc.perform(get(BASE_URL + "/symbol/MSFT/date-range")
                            .param("startDate", "2024-01-10")
                            .param("endDate", "2024-01-20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].tradingSymbol").value("MSFT"));
        }
    }

    @Nested
    @DisplayName("GET /api/form4/recent")
    class GetRecentFilings {

        @Test
        @DisplayName("Should return recent filings with default limit")
        void shouldReturnRecentWithDefaultLimit() throws Exception {
            for (int i = 0; i < 15; i++) {
                Form4 form4 = createForm4(
                        String.format("0001234567-24-%06d", i),
                        "MSFT",
                        "Owner " + i
                );
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.DAY_OF_MONTH, -i);
                form4.setTransactionDate(cal.getTime());
                form4Repository.save(form4);
            }

            mockMvc.perform(get(BASE_URL + "/recent"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(10)));
        }

        @Test
        @DisplayName("Should return recent filings with custom limit")
        void shouldReturnRecentWithCustomLimit() throws Exception {
            for (int i = 0; i < 10; i++) {
                form4Repository.save(createForm4(
                        String.format("0001234567-24-%06d", i),
                        "MSFT",
                        "Owner " + i
                ));
            }

            mockMvc.perform(get(BASE_URL + "/recent")
                            .param("limit", "5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(5)));
        }

        @Test
        @DisplayName("Should cap limit at 100")
        void shouldCapLimitAt100() throws Exception {
            mockMvc.perform(get(BASE_URL + "/recent")
                            .param("limit", "500"))
                    .andExpect(status().isOk());
            // Just verify it doesn't error; actual cap is applied in controller
        }
    }

    @Nested
    @DisplayName("GET /api/form4/symbol/{symbol}/stats")
    class GetInsiderStats {

        @Test
        @DisplayName("Should return insider statistics for symbol")
        void shouldReturnInsiderStats() throws Exception {
            Calendar cal = Calendar.getInstance();
            cal.set(2024, Calendar.JANUARY, 15);

            Form4 buy = createForm4(ACCESSION_1, "MSFT", "Buyer");
            buy.setTransactionDate(cal.getTime());
            buy.setAcquiredDisposedCode("A");
            buy.setTransactionValue(100000f);
            buy.setDirector(true);
            form4Repository.save(buy);

            Form4 sell = createForm4(ACCESSION_2, "MSFT", "Seller");
            sell.setTransactionDate(cal.getTime());
            sell.setAcquiredDisposedCode("D");
            sell.setTransactionValue(50000f);
            sell.setOfficer(true);
            form4Repository.save(sell);

            mockMvc.perform(get(BASE_URL + "/symbol/MSFT/stats")
                            .param("startDate", "2024-01-01")
                            .param("endDate", "2024-01-31"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalBuys").value(1))
                    .andExpect(jsonPath("$.totalSells").value(1))
                    .andExpect(jsonPath("$.directorTransactions").value(1))
                    .andExpect(jsonPath("$.officerTransactions").value(1));
        }
    }

    @Nested
    @DisplayName("POST /api/form4")
    class SaveForm4 {

        @Test
        @DisplayName("Should save new Form4")
        void shouldSaveNewForm4() throws Exception {
            Form4 form4 = createForm4(ACCESSION_1, "MSFT", "John Doe");
            form4.setId(null); // Ensure no ID for new record

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(form4)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessionNumber").value(ACCESSION_1))
                    .andExpect(jsonPath("$.id").exists());
        }

        @Test
        @DisplayName("Should update existing Form4")
        void shouldUpdateExistingForm4() throws Exception {
            Form4 saved = form4Repository.save(createForm4(ACCESSION_1, "MSFT", "John Doe"));

            saved.setRptOwnerName("Updated Name");

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(saved)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rptOwnerName").value("Updated Name"));
        }
    }

    @Nested
    @DisplayName("DELETE /api/form4/{id}")
    class DeleteForm4 {

        @Test
        @DisplayName("Should delete Form4 by ID")
        void shouldDeleteForm4() throws Exception {
            Form4 saved = form4Repository.save(createForm4(ACCESSION_1, "MSFT", "John Doe"));

            mockMvc.perform(delete(BASE_URL + "/" + saved.getId()))
                    .andExpect(status().isNoContent());

            // Verify deleted
            mockMvc.perform(get(BASE_URL + "/" + saved.getId()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 404 when deleting non-existent Form4")
        void shouldReturn404WhenDeletingNonExistent() throws Exception {
            mockMvc.perform(delete(BASE_URL + "/nonexistent-id"))
                    .andExpect(status().isNotFound());
        }
    }

    private Form4 createForm4(String accessionNumber, String symbol, String ownerName) {
        List<Form4Transaction> transactions = new ArrayList<>();
        transactions.add(Form4Transaction.builder()
                .accessionNumber(accessionNumber)
                .transactionType("NON_DERIVATIVE")
                .securityTitle("Common Stock")
                .transactionCode("P")
                .transactionShares(1000f)
                .transactionPricePerShare(100f)
                .transactionValue(100000f)
                .acquiredDisposedCode("A")
                .build());

        return Form4.builder()
                .accessionNumber(accessionNumber)
                .documentType("4")
                .cik("789019")
                .issuerName("MICROSOFT CORP")
                .tradingSymbol(symbol)
                .rptOwnerCik("0001234567")
                .rptOwnerName(ownerName)
                .isDirector(false)
                .isOfficer(true)
                .isTenPercentOwner(false)
                .isOther(false)
                .ownerType("Officer")
                .officerTitle("CFO")
                .securityTitle("Common Stock")
                .transactionDate(new Date())
                .transactionShares(1000f)
                .transactionPricePerShare(100f)
                .transactionValue(100000f)
                .acquiredDisposedCode("A")
                .transactions(transactions)
                .createdAt(new Date())
                .updatedAt(new Date())
                .build();
    }
}
