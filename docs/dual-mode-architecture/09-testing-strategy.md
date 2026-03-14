# Phase 9: Testing Strategy

## Objective

Ensure both resource modes are thoroughly tested with a CI-friendly matrix approach. Tests should verify behavioral equivalence between modes — same input produces same output regardless of storage backend.

## Test Architecture

```
┌──────────────────────────────────────────────────┐
│                Test Pyramid                       │
│                                                   │
│                  ┌─────┐                          │
│                  │ E2E │  Both modes via profiles  │
│                ┌─┴─────┴─┐                        │
│                │  Integ  │  Per-mode, per-adapter  │
│              ┌─┴─────────┴─┐                      │
│              │    Unit     │  Mode-independent     │
│              └─────────────┘                       │
└──────────────────────────────────────────────────┘
```

## Test Categories

### 1. Unit Tests (Mode-Independent)

These test business logic in services with mocked ports. They are identical for both modes because services don't know about the storage backend.

```java
@ExtendWith(MockitoExtension.class)
class Form4ServiceImplTest {

    @Mock
    private Form4DataPort form4DataPort;  // Port interface, not repository
    @Mock
    private FillingDataPort fillingDataPort;
    @Mock
    private TickerDataPort tickerDataPort;

    @InjectMocks
    private Form4ServiceImpl form4Service;

    @Test
    void save_newForm4_setsTimestamps() {
        Form4 form4 = new Form4();
        form4.setAccessionNumber("0001234567-24-000001");
        when(form4DataPort.findByAccessionNumber(any())).thenReturn(Optional.empty());
        when(form4DataPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Form4 saved = form4Service.save(form4);

        assertNotNull(saved.getUpdatedAt());
        verify(form4DataPort).save(form4);
    }
}
```

### 2. Adapter Integration Tests (Per-Mode)

Each adapter implementation gets its own integration test using the appropriate infrastructure.

#### MongoDB Adapter Tests

```java
@DataMongoTest
@ActiveProfiles("resource-high")
@Import(Form4MongoAdapter.class)
class Form4MongoAdapterTest {

    @Autowired
    private Form4DataPort form4DataPort;  // Resolved to Form4MongoAdapter

    @Test
    void save_and_findByAccessionNumber() {
        Form4 form4 = createTestForm4();
        form4DataPort.save(form4);

        Optional<Form4> found = form4DataPort.findByAccessionNumber(form4.getAccessionNumber());
        assertTrue(found.isPresent());
        assertEquals(form4.getTradingSymbol(), found.get().getTradingSymbol());
    }
}
```

#### File Adapter Tests

```java
@SpringBootTest(classes = {FileAdapterConfiguration.class, Form4FileAdapter.class})
@ActiveProfiles("resource-low")
@TempDir
class Form4FileAdapterTest {

    @Autowired
    private Form4DataPort form4DataPort;  // Resolved to Form4FileAdapter

    @Test
    void save_and_findByAccessionNumber() {
        // Same test body as MongoDB test
        Form4 form4 = createTestForm4();
        form4DataPort.save(form4);

        Optional<Form4> found = form4DataPort.findByAccessionNumber(form4.getAccessionNumber());
        assertTrue(found.isPresent());
        assertEquals(form4.getTradingSymbol(), found.get().getTradingSymbol());
    }
}
```

### 3. Behavioral Equivalence Tests (Contract Tests)

A parameterized test suite that runs the same test cases against both adapter implementations. This is the most valuable test category — it guarantees both modes behave identically.

```java
/**
 * Abstract contract test for Form4DataPort.
 * Subclasses provide the adapter under test via different profiles.
 */
abstract class Form4DataPortContractTest {

    protected abstract Form4DataPort getPort();

    @Test
    void saveAndFind_roundTrip() {
        Form4DataPort port = getPort();
        Form4 form4 = createTestForm4("0001234567-24-000001", "AAPL");

        port.save(form4);
        Optional<Form4> found = port.findByAccessionNumber("0001234567-24-000001");

        assertTrue(found.isPresent());
        assertEquals("AAPL", found.get().getTradingSymbol());
    }

    @Test
    void findBySymbolAndDateRange_pagination() {
        Form4DataPort port = getPort();
        // Insert 25 Form4 records for AAPL
        for (int i = 0; i < 25; i++) {
            port.save(createTestForm4("acc-" + i, "AAPL",
                LocalDate.of(2024, 1, i + 1)));
        }

        Page<Form4> page = port.findBySymbolAndDateRange(
            "AAPL",
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2024, 1, 31),
            PageRequest.of(0, 10, Sort.by("transactionDate"))
        );

        assertEquals(10, page.getContent().size());
        assertEquals(25, page.getTotalElements());
        assertEquals(3, page.getTotalPages());
    }

    @Test
    void aggregation_transactionSummary() {
        Form4DataPort port = getPort();
        // Insert buys and sells
        port.save(createBuyForm4("acc-1", "TSLA", 100.0));
        port.save(createBuyForm4("acc-2", "TSLA", 200.0));
        port.save(createSellForm4("acc-3", "TSLA", 150.0));

        var summaries = port.getTransactionSummaryBySymbol(
            "TSLA", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));

        var buySum = summaries.stream().filter(s -> "A".equals(s.getId())).findFirst();
        assertTrue(buySum.isPresent());
        assertEquals(300.0, buySum.get().getTotalValue(), 0.01);
        assertEquals(2L, buySum.get().getCount());
    }

    // ... 20+ contract tests covering all port methods
}

// Concrete subclasses:

@DataMongoTest
@ActiveProfiles("resource-high")
class Form4MongoContractTest extends Form4DataPortContractTest {
    @Autowired private Form4DataPort port;
    @Override protected Form4DataPort getPort() { return port; }
}

@SpringBootTest
@ActiveProfiles("resource-low")
class Form4FileContractTest extends Form4DataPortContractTest {
    @Autowired private Form4DataPort port;
    @Override protected Form4DataPort getPort() { return port; }
}
```

### 4. End-to-End API Tests

Full REST API tests that boot the application in each mode and verify endpoint responses.

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("resource-low")
class Form4ControllerLowModeE2ETest {

    @Autowired
    private WebTestClient webClient;

    @Test
    void getForm4BySymbol_returnsResults() {
        // Seed data via port
        // Call REST endpoint
        // Verify response structure and content
    }
}
```

### 5. File Storage Engine Tests

Dedicated tests for the file storage engine internals.

```java
class FileCollectionTest {

    @TempDir Path tempDir;

    @Test
    void concurrentReadWrite_noCorruption() {
        FileCollection<Form4> collection = createCollection(tempDir);

        // Spawn 10 writer threads + 20 reader threads
        // Verify no data corruption after all threads complete
    }

    @Test
    void flushAndReload_preservesData() {
        FileCollection<Form4> collection = createCollection(tempDir);
        collection.save(createTestForm4());
        collection.flush();

        // Create new collection instance from same path
        FileCollection<Form4> reloaded = createCollection(tempDir);
        reloaded.load();

        assertEquals(1, reloaded.findAll().size());
    }

    @Test
    void atomicFlush_survivesPartialWrite() {
        // Simulate crash during flush
        // Verify previous state is intact
    }
}
```

## CI Matrix Configuration

### GitHub Actions

```yaml
name: Dual-Mode Tests
on: [push, pull_request]

jobs:
  unit-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '25'
      - run: mvn test -Dtest="*UnitTest,*Test" -DexcludedGroups="integration"

  integration-low:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '25'
      - run: mvn verify -Dspring.profiles.active=resource-low -Dtest="*FileAdapter*,*LowMode*,*Contract*"

  integration-high:
    runs-on: ubuntu-latest
    services:
      mongodb:
        image: mongo:7
        ports: ['27017:27017']
      postgres:
        image: postgres:16
        env:
          POSTGRES_DB: edgar4j
          POSTGRES_USER: edgar4j
          POSTGRES_PASSWORD: edgar4j
        ports: ['5432:5432']
      redis:
        image: redis:7
        ports: ['6379:6379']
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '25'
      - run: mvn verify -Dspring.profiles.active=resource-high -Dtest="*MongoAdapter*,*HighMode*,*Contract*"

  e2e-low:
    needs: [integration-low]
    runs-on: ubuntu-latest
    steps:
      - run: mvn verify -Dspring.profiles.active=resource-low -Dtest="*E2E*LowMode*"

  e2e-high:
    needs: [integration-high]
    runs-on: ubuntu-latest
    services:
      mongodb: ...
      postgres: ...
    steps:
      - run: mvn verify -Dspring.profiles.active=resource-high -Dtest="*E2E*HighMode*"
```

## Test Data Fixtures

Create shared test data fixtures used by all test categories:

```java
public class TestFixtures {

    public static Form4 createTestForm4(String accession, String symbol) { ... }
    public static Form4 createBuyForm4(String accession, String symbol, double value) { ... }
    public static Form4 createSellForm4(String accession, String symbol, double value) { ... }
    public static InsiderTransaction createTestTransaction() { ... }
    public static Company createTestCompany(String cik, String ticker) { ... }
}
```

## Test Coverage Targets

| Category | Target | Scope |
|----------|--------|-------|
| Unit tests | 80%+ line coverage | Service layer |
| Contract tests | 100% port method coverage | All 27 port interfaces |
| Adapter tests | All CRUD + query paths | Each adapter type |
| E2E tests | All REST endpoints respond | Critical user flows |
| File engine tests | Concurrency, persistence, edge cases | Storage engine internals |

## Estimated Effort

- **Contract test framework**: 1 day
- **Contract tests (27 ports)**: 3 days
- **File engine tests**: 1 day
- **E2E test setup**: 1 day
- **CI configuration**: 1 day
- **Total**: 6-7 days
