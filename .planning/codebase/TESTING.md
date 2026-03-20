# HPCC4J Testing Patterns

## 1. Test Framework

- **JUnit 4** (version `4.13.1`) — declared in root `pom.xml`
- **No mocking framework** — all tests run against real HPCC clusters or self-contained logic
- Key JUnit 4 annotations used: `@Test`, `@Before`, `@BeforeClass`, `@After`, `@AfterClass`, `@Ignore`, `@FixMethodOrder`
- `@FixMethodOrder(MethodSorters.NAME_ASCENDING)` used in `DFSReadWriteTest` where test ordering matters
- Assertions: `org.junit.Assert` (both static and instance usage); static imports common in newer tests
- Conditional execution: `org.junit.Assume` — a failed `Assume` silently skips the test (not a failure)

---

## 2. Test Category Annotations

Categories are declared as **marker interfaces** in `commons-hpcc/src/main/java/org/hpccsystems/commons/annotations/`.

### Available Categories

| Interface | Location | Purpose |
|---|---|---|
| `BaseTests` | `annotations/BaseTests.java` | Unit tests; no HPCC connection required. Run by default. |
| `RemoteTests` | `annotations/RemoteTests.java` | Integration tests requiring a live HPCC cluster. |
| `IntegrationTests` | `annotations/IntegrationTests.java` | Deeper integration tests (workunits lifecycle, attributes). |
| `UnverifiedServerIssues` | `annotations/UnverifiedServerIssues.java` | Tests with suspected server-side bugs; excluded by default. |
| `KnownServerIssueTests` | *(similar marker)* | Tests with confirmed server bugs; excluded by default. |
| `Benchmark` | *(similar marker)* | Performance benchmarks; only run under `benchmark` profile. |

### Usage

```java
// Single category
@Category(org.hpccsystems.commons.annotations.BaseTests.class)
public class CryptoHelperTest { ... }

// Single category — remote test
@Category(org.hpccsystems.commons.annotations.RemoteTests.class)
public abstract class BaseRemoteTest { ... }

// Multiple categories
@Category({org.hpccsystems.commons.annotations.RemoteTests.class,
           org.hpccsystems.commons.annotations.IntegrationTests.class})
public abstract class BaseWsAttributesClientIntegrationTest extends BaseRemoteTest { ... }
```

---

## 3. `BaseRemoteTest` Pattern

**File:** `wsclient/src/test/java/org/hpccsystems/ws/client/BaseRemoteTest.java`

The root base class for all wsclient and dfsclient integration tests. Every test class that connects to HPCC **must** extend this.

### What It Provides

| Field | Type | Description |
|---|---|---|
| `platform` | `Platform` | Platform object; entry point for cluster topology |
| `wsclient` | `HPCCWsClient` | Pre-configured aggregate web service client |
| `connection` | `Connection` | The underlying `Connection` object |
| `connString` | `String` | ESP URL from `System.getProperty("hpccconn")` |
| `hpccUser` | `String` | Username from `System.getProperty("hpccuser")` |
| `hpccPass` | `String` | Password from `System.getProperty("hpccpass")` |
| `thorclustername` | `String` | Thor cluster name |
| `thorClusterFileGroup` | `String` | Thor file group (auto-discovered if not set) |
| `roxieclustername` | `String` | Roxie cluster name |
| `roxieClusterGroup` | `String` | Roxie group (auto-discovered if not set) |
| `testThreadCount` | `int` | Thread count for multi-threaded tests (default: 10) |
| `DEFAULTHPCCFILENAME` | `String` | `"benchmark::all_types::200kb"` — default test file |
| `DEFAULTHPCCSUPERFILENAME` | `String` | `"benchmark::all_types::superfile"` |
| `initializationException` | `Exception` | Stored if `initialize()` throws — used by `initCheck()` |
| `globalOTel` | `OpenTelemetry` | OTel SDK instance for test spans |

### Initialization Flow

```java
// 1. Static initializer runs initialize() and stores any exception
static
{
    try { initialize(); }
    catch (Exception e) { initializationException = e; }
}

// 2. @BeforeClass skips all tests cleanly if init failed
@BeforeClass
public static void initCheck()
{
    Assume.assumeTrue("Error initializing test suite: " + exceptionMessage,
                      initializationException == null);
}

// 3. initialize() builds the Connection, Platform, and HPCCWsClient
connection = new Connection(connString);
connection.setCredentials(hpccUser, hpccPass);
platform = Platform.get(connection);
wsclient = platform.checkOutHPCCWsClient();
```

### ECL Script Execution Helper

```java
// Loads and runs an ECL script from classpath resources
@WithSpan
public static String executeECLScript(String eclFile) throws Exception
{
    // reads ECL from classpath, wraps in WorkunitWrapper, submits via HPCCWsWorkUnitsClient
}

// Called automatically during setup to generate test datasets:
executeECLScript("generate-datasets.ecl");
```

### Multi-threaded Test Helper

```java
public static void executeMultiThreadedTask(Callable<String> callableTask, int threadCount)
        throws InterruptedException
{
    // Creates threadCount futures, asserts each returns empty string (no error)
}
```

---

## 4. `BaseIntegrationTest` Pattern (spark-hpcc)

**File:** `spark-hpcc/src/test/java/org/hpccsystems/spark/BaseIntegrationTest.java`

Extends `BaseRemoteTest` — inherits all connection setup and system property handling — and adds Apache Spark session management.

### Additional Capabilities

```java
// Finds the built Spark fat-jar in target/ for configuring the Spark context classpath
public File findRecentlyBuiltSparkJar() { ... }

// Returns a default SparkConf pointed at master=local with app jar configured
public SparkConf getDefaultSparkConf() { ... }

// Gets or creates a SparkContext (singleton per test suite)
public SparkContext getOrCreateSparkContext() { ... }
public SparkContext getOrCreateSparkContext(SparkConf conf) { ... }

// Gets or creates a SparkSession
public SparkSession getOrCreateSparkSession() { ... }
```

### Spark Tests Use Inherited Connection Helpers

```java
writtenDataSet.write()
    .format("hpcc")
    .mode("overwrite")
    .option("cluster",   getThorCluster())      // from BaseIntegrationTest
    .option("host",      getHPCCClusterURL())
    .option("username",  getHPCCClusterUser())
    .option("password",  getHPCCClusterPass())
    .save(testDataset);
```

---

## 5. Connecting to HPCC in Tests — System Properties

All connection parameters come from **system properties** (with sensible defaults). Pass them via `-D` flags or CI pipeline configuration — never hardcode.

| Property | Default | Notes |
|---|---|---|
| `hpccconn` | `http://localhost:8010` | Full ESP URL with port |
| `hpccuser` | `JunitUser` | Username; empty string allowed for unsecured clusters |
| `hpccpass` | `""` (empty) | Password |
| `thorclustername` | `thor` | Name of the Thor target cluster |
| `roxieclustername` | `roxie` | Name of the Roxie target cluster |
| `thorgroupname` | *(auto-discovered)* | Thor file group; queried from topology if not supplied |
| `roxiegroupname` | *(auto-discovered)* | Roxie file group; queried from topology if not supplied |
| `connecttimeoutmillis` | *(unset = no override)* | Connection timeout in ms |
| `sockettimeoutmillis` | *(unset = no override)* | Socket timeout in ms |
| `testthreadcount` | `10` | Thread count for concurrent tests |
| `logicalfilename` | `benchmark::all_types::200kb` | File used in DFU tests |
| `disableDatasetGeneration` | `false` | Skip ECL dataset generation during init |

### Example Maven Invocation for Remote Tests

```bash
mvn test -P jenkins-on-demand \
  -Dhpccconn=http://mycluster:8010 \
  -Dhpccuser=myuser \
  -Dhpccpass=mypass \
  -Dthorclustername=thor \
  -Droxieclustername=roxie
```

---

## 6. `@BaseTests` Annotation — Unit Tests

Tests tagged `@Category(BaseTests.class)` must be **self-contained** — they run without any live HPCC connection.

### Examples

```java
// commons-hpcc/src/test/java/org/hpccsystems/commons/CryptoHelperTest.java
@Category(org.hpccsystems.commons.annotations.BaseTests.class)
public class CryptoHelperTest
{
    @Test
    public void testCustomCipher() { ... }     // pure Java crypto operations

    @Test
    public void testDefaultCryptoAlgo() { ... }
}

// commons-hpcc/src/test/java/org/hpccsystems/commons/UtilsTests.java
@Category(org.hpccsystems.commons.annotations.BaseTests.class)
public class UtilsTests
{
    @Test
    public void testU8Extraction()
    {
        Assert.assertTrue(Utils.extractUnsigned8Val(-1L).compareTo(BigInteger.ZERO) > 0);
    }
}
```

All `BaseTests` are run during `mvn test` (the default). They should produce zero I/O to external systems.

---

## 7. Containerized vs Bare-metal Test Assumptions

Some tests only apply to bare-metal (non-containerized) HPCC or vice versa. Use `Assume` to skip cleanly:

```java
// Skip if running against containerized HPCC
@Test
@WithSpan
public void testAvailableClusterGroups()
{
    Assume.assumeFalse("Test not valid on containerized HPCC environment",
                       wsclient.isContainerized());
    // ... test body
}

// Skip if running against bare-metal HPCC
@Test
public void testContainerizedFeature()
{
    Assume.assumeTrue("Test only valid on containerized HPCC environment",
                      wsclient.isContainerized());
    // ... test body
}

// Skip if a required resource is null
Assume.assumeNotNull(someOptionalResource);

// Skip unless an explicit condition is met
Assume.assumeTrue("Condition not met", someBoolean);
```

A failed `Assume` causes JUnit to mark the test as **ignored** (not failed) — CI pipelines treat this as a skip.

---

## 8. Mocking Approach

**There is no mocking framework.** All tests are either:

1. **`BaseTests`** — pure unit tests testing Java logic in isolation (no HPCC, no network).
2. **`RemoteTests` / `IntegrationTests`** — full integration tests against a live HPCC cluster.

There is no Mockito, EasyMock, PowerMock, or similar dependency in any `pom.xml`. When a test needs a HPCC cluster, it requires one. If no cluster is available, the test is skipped via `Assume.assumeTrue(initializationException == null)` in `initCheck()`.

---

## 9. Test Resource Files

| File / Directory | Module | Purpose |
|---|---|---|
| `wsclient/src/test/resources/generate-datasets.ecl` | wsclient | ECL script run at test suite startup to create test datasets on HPCC |
| `wsclient/src/test/resources/attributetest/` | wsclient | Data files for `WsAttributesClientTest` |
| `wsclient/src/test/resources/filespraytest/` | wsclient | Data files for `FileSprayClientTest` |
| `commons-hpcc/src/test/resources/LoremIpsum.txt` | commons-hpcc | Sample text data used in string processing tests |
| `dfsclient/src/test/resources/` | dfsclient | Additional DFS-specific test resources |

### ECL Script for Test Data

The file `generate-datasets.ecl` is loaded from the classpath at runtime by `BaseRemoteTest.initialize()`:

```java
executeECLScript("generate-datasets.ecl");
```

This creates standard benchmark datasets on the target HPCC cluster (e.g., `~benchmark::all_types::200KB`, `~unit_test::all_types::thor`, `~unit_test::all_types::json`). Test classes reference these by the constants defined in `BaseRemoteTest`:

```java
public static final String DEFAULTHPCCFILENAME = "benchmark::all_types::200kb";
private static final String[] datasets = {
    "~benchmark::integer::20kb",
    "~unit_test::all_types::thor",
    "~unit_test::all_types::json",
    "~unit_test::all_types::csv"
};
```

---

## 10. Running Tests — Maven Commands and Profiles

### Default (BaseTests only)

```bash
mvn test
# Runs: org.hpccsystems.commons.annotations.BaseTests
# Excludes: KnownServerIssueTests, UnverifiedServerIssues, UnverifiedClientIssues
```

### Remote Tests (Requires Live HPCC)

```bash
mvn test -P jenkins-on-demand \
  -Dhpccconn=http://mycluster:8010 \
  -Dhpccuser=myuser \
  -Dhpccpass=mypass
# Runs: BaseTests + RemoteTests
```

### Release Build (BaseTests only, with signing)

```bash
mvn clean deploy -P release
# Runs: BaseTests
# Signs artifacts with GPG, auto-publishes to Maven Central
```

### Known Server Issues

```bash
mvn test -P known-server-issues
# Runs: KnownServerIssueTests
```

### Benchmarks

```bash
mvn test -P benchmark
# Runs: Benchmark group only
```

### All Profiles Summary

| Profile | Groups Run | Intended Use |
|---|---|---|
| *(default)* | `BaseTests` | CI on PRs, no cluster needed |
| `jenkins-on-demand` | `BaseTests`, `RemoteTests` | CI with live HPCC cluster |
| `jenkins-release` | `BaseTests` | Release pipeline |
| `known-server-issues` | `KnownServerIssueTests` | Debug/tracking server bugs |
| `benchmark` | `Benchmark` | Performance measurement |
| `jfrog-artifactory` | `BaseTests` | Artifact publication to JFrog |

---

## 11. Test Naming Conventions

### Class Names

| Pattern | Example |
|---|---|
| Concrete test class | `[SubjectName]Test` or `[ServiceName]ClientTest` |
| Shared remote base | `Base[Service]Test` or `Base[Feature]Test` |
| Abstract integration base | `Base[Service]IntegrationTest` |
| Version-specific integration | `[Service]_[Version]_Test` |

Examples:
- `CryptoHelperTest` — unit test for `CryptoHelper`
- `WsDFUClientTest` — integration tests for `HPCCWsDFUClient`
- `BaseRemoteTest` — shared remote test base class
- `BaseWsWorkunitsClientIntegrationTest` — abstract integration base
- `WsWorkunitsClientIntegration_54_Test` — integration test for HPCC 5.4 behaviors
- `WsAttributesClientIntegrationTest_620` — integration test for HPCC 6.20 behaviors

### Method Names

All test methods follow the `test[BehaviorUnderTest]()` pattern:

```java
@Test public void testAvailableClusterGroups() { ... }
@Test public void testFileTypeWrapper() { ... }
@Test public void testU8Extraction() { ... }
@Test public void testGetAvailableRoxieClusterNames() { ... }
@Test public void readBadlyDistributedFileTest() { ... }   // variant: no "test" prefix
@Test public void testbuildScanAllValid() throws Exception { ... }  // lowercase 'b' seen
```

The predominant convention is `testCamelCaseDescription()`. Some older tests omit the `test` prefix but this is not preferred for new tests.

---

## 12. Additional Testing Patterns

### `@Before` / `@After` for Setup/Teardown

```java
// Delay between DFU test operations to avoid race conditions  
@Before
public void delayhack()
{
    try { Thread.sleep(5000); }
    catch (InterruptedException e) {}
}

// Cleanup of test workunits after integration tests
@After
public void shutdown()
{
    for (String wuid : testwuids)
    {
        try
        {
            wswuclient.deleteWU(wuid);
        }
        catch (Exception e)
        {
            System.out.println("Could not delete test wuid " + wuid + ":" + e.getMessage());
            e.printStackTrace();
        }
    }
}
```

### `@Ignore` for Temporarily Disabled Tests

```java
@Ignore
@Category(org.hpccsystems.commons.annotations.IntegrationTests.class)
public abstract class BaseWsWorkunitsClientIntegrationTest extends BaseRemoteTest { ... }
```

### OpenTelemetry in Tests

Test methods that exercise service calls are annotated with `@WithSpan` to produce traces during test runs:

```java
@Test
@WithSpan
public void testAvailableClusterGroups()
{
    // ... assertions
}
```

The `BaseRemoteTest` initializes `globalOTel` and exposes `getRemoteTestTracer()` / `getRemoteTestTraceBuilder(String)` for building custom spans in complex test scenarios.

### `@UnverifiedServerIssues` Annotation

For tests that expose suspected server bugs that haven't been fully root-caused yet. They are excluded from the default run but can be surfaced for investigation:

```java
import org.hpccsystems.commons.annotations.UnverifiedServerIssues;

@Category(UnverifiedServerIssues.class)
@Test
public void testSuspectedServerBug() { ... }
```

These appear in `WsDFUClientTest` with the `@Category` alongside imports from the `gen.axis2` package to probe raw stub behavior.
