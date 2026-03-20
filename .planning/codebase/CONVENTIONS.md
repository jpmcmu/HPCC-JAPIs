# HPCC4J Code Conventions

## 1. Code Formatting (Eclipse Formatter)

The authoritative formatter profile is `eclipse/HPCC-JAVA-Formatter.xml`, profile name `hpcc4j`.  
Apply it in Eclipse via **Window → Preferences → Java → Code Style → Formatter → Import**.

### Key Settings

| Setting | Value |
|---|---|
| Indentation character | **spaces** (no tabs) |
| Indentation size | **4** |
| Continuation indent | **2** |
| Max line length | **150** characters |
| Brace style | **Allman / next_line** for methods, constructors, blocks, classes, lambdas, enums, switches |
| Array initializers | end_of_line brace |
| Javadoc formatting | **disabled** (`comment.format_javadoc_comments=false`) |
| Line comment formatting | **disabled** (`comment.format_line_comments=false`) |
| Block comment formatting | **disabled** (`comment.format_block_comments=false`) |
| `else`/`catch`/`finally`/`while` | each on its **own line** |
| Compact `else if` | **enabled** — `} else if {` on one line |
| Simple `if` on one line | **enabled** |
| Blank lines before methods | **1** |
| Blank lines before fields | **0** |
| Blank lines between import groups | **1** |
| Type members aligned on columns | **true** |
| `@formatter:off` / `@formatter:on` tags | Supported (set `use_on_off_tags=false` means tags are defined but usage is opt-in) |

### Brace Style Example

```java
// Methods, constructors, classes — brace on NEXT line (Allman style)
public void doSomething()
{
    if (condition)
    {
        // ...
    }
    else if (otherCondition)
    {
        // ...
    }
    else
    {
        // ...
    }

    try
    {
        // ...
    }
    catch (Exception e)
    {
        // ...
    }
    finally
    {
        // ...
    }
}
```

```java
// Array initializer — brace on same line
String[] args = { "a", "b", "c" };
```

---

## 2. License Header Requirements

Every source file must carry an Apache 2.0 license header. **Two accepted formats** are used in the codebase:

### Format A — Hash-bordered (used in `wsclient` and many newer files)

```java
/*##############################################################################

    HPCC SYSTEMS software Copyright (C) 2026 HPCC Systems®.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
############################################################################## */
```

### Format B — Star-bordered (used in `dfsclient`, `spark-hpcc`)

```java
/*******************************************************************************
 *     HPCC SYSTEMS software Copyright (C) 2026 HPCC Systems®.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     ...
 *******************************************************************************/
```

**Rules:**
- Use the current year when creating a new file.
- The copyright line must read exactly `HPCC SYSTEMS software Copyright (C) YEAR HPCC Systems®.`
- Generated wrapper classes (in `wrappers.gen.*`) sometimes place the license header *inside* the class comment block after the `package` statement — follow the pattern of neighboring files.
- Generated stub classes (`gen.axis2.*`) have no license header — do not add one.

---

## 3. Java Naming Conventions

### Classes and Interfaces

| Pattern | Example |
|---|---|
| Service client classes | `HPCCWs[Service]Client` — `HPCCWsDFUClient`, `HPCCWsWorkUnitsClient` |
| Base service client | `BaseHPCCWsClient` |
| Aggregate client | `HPCCWsClient` |
| Client pool | `HPCCWsClientPool` |
| Generated stub wrapper (gen) | `[StubName]Wrapper` — `DFUFileDetailWrapper`, `WsDFUXRefPingResponseWrapper` |
| Hand-crafted wrappers | `[ConceptName]Wrapper` — `WorkunitWrapper`, `ArrayOfEspExceptionWrapper` |
| Test annotation markers | `BaseTests`, `RemoteTests`, `IntegrationTests` (simple marker interfaces) |
| Platform model | `Platform`, `Cluster`, `Version` |

### Methods

- `camelCase` throughout.
- Accessors: `getFoo()` / `setFoo(T val)` / `isFoo()` (boolean).
- Factory methods on singletons: `get(Connection conn)`, `getNoCreate(...)`, `remove(p)`.
- Methods that call remote services: `doesTargetHPCCAuthenticate()`, `getAvailableClusterGroups()`.

### Fields and Variables

- Instance/local variables: `camelCase` — `wsconn`, `thorClusterFileGroup`, `targetHPCCBuildVersion`.
- Logger: **always** `private static final Logger log = LogManager.getLogger(ClassName.class);` — field name is always `log`.
- String and numeric constants: `SCREAMING_SNAKE_CASE` — `DEAFULTECLWATCHPORT`, `DEFAULTECLWATCHTLSPORT`, `DEFAULTHPCCFILENAME`.
- Static singleton collections: `PascalCase` — `public static DataSingletonCollection All`, `SubClients`.

### Constants

```java
// Constants in source classes
public static final String DEAFULTECLWATCHPORT    = "8010";
public static final String DEFAULTECLWATCHTLSPORT = "18010";
public static String       DEFAULTSERVICEPORT     = DEAFULTECLWATCHPORT;

// Constants in test base classes
public static final String DEFAULTHPCCFILENAME      = "benchmark::all_types::200kb";
public static final String DEFAULTHPCCSUPERFILENAME = "benchmark::all_types::superfile";
```

---

## 4. Package Naming Patterns

```
org.hpccsystems                          — root groupId
org.hpccsystems.commons.*                — commons-hpcc module shared utilities
org.hpccsystems.commons.annotations      — test category marker interfaces
org.hpccsystems.commons.ecl.*            — ECL/FieldDef model objects
org.hpccsystems.commons.errors           — HpccFileException, etc.
org.hpccsystems.ws.client                — high-level service clients (HPCCWsClient, etc.)
org.hpccsystems.ws.client.gen.axis2.*    — GENERATED Axis2 ADB stubs — NEVER edit directly
org.hpccsystems.ws.client.wrappers.gen.* — GENERATED thin wrappers around stubs
org.hpccsystems.ws.client.wrappers.*     — hand-crafted wrappers / convenience types
org.hpccsystems.ws.client.utils          — Connection, Utils, DataSingleton, etc.
org.hpccsystems.ws.client.platform       — Platform, Cluster, Version, QuerySet models
org.hpccsystems.ws.client.extended       — HPCCWsAttributesClient (non-standard service clients)
org.hpccsystems.dfs.client               — DFS file read/write, HPCCFile, HpccRemoteFileReader
org.hpccsystems.dfs.cluster              — ClusterRemotefileReader, partition info
org.hpccsystems.spark                    — Spark connector (HpccRDD, HpccFileWriter)
org.hpccsystems.spark.datasource         — HpccRelation, HpccOptions, HpccRelationProvider
```

---

## 5. Error Handling Patterns

### General Rule — Propagate with Context

Public API methods declare `throws Exception` broadly. Service methods also explicitly declare `throws ArrayOfEspExceptionWrapper` when they can return ESP-level error arrays.

```java
public String[] listNamespaces(String storename, boolean global)
        throws Exception, ArrayOfEspExceptionWrapper
{
    if (wsconn == null)
        throw new Exception("Client connection has not been initialized.");

    // ... call soap stub
}
```

### Guard Clauses at Entry

Always validate prerequisites early with an explicit exception message before doing real work:

```java
if (wsconn == null)
    throw new Exception("BaseHPCCWsClient: Cannot get target HPCC auth mode, "
                       + "client connection has not been initialized.");
```

### AxisFault / RemoteException Handling

Axis2 faults (`AxisFault`, which is a `RemoteException`) are caught and re-thrown as `Exception` with contextual message, or handled by extracting the fault detail:

```java
try
{
    // soap call
}
catch (AxisFault e)
{
    // Often logged, message extracted, re-thrown as Exception
    throw new Exception("Service call failed: " + e.getMessage(), e);
}
```

ESP-level exceptions returned in response objects are extracted and thrown as `ArrayOfEspExceptionWrapper`.

### Null Returns vs Exceptions

- When a resource is not found (e.g., a file that may not exist), returning `null` is acceptable.
- When a required initial state is missing, **always throw** with a descriptive message.
- Avoid silent swallowing of exceptions — at minimum log or re-throw.

---

## 6. Javadoc Conventions

Javadoc is required on all public API classes and methods. Auto-formatting is **disabled** — write it manually and preserve whitespace.

```java
/**
 * Fetch pre-existing HPCCWsClient instance from collection; if none exists with
 * the given connection options, create a new instance.
 *
 * @param protocol
 *            the protocol (http or https)
 * @param targetWsECLWatchAddress
 *            the target ESP address
 * @param targetWsECLWatchPort
 *            the target ESP port
 * @param user
 *            the username credential
 * @param password
 *            the password credential
 * @return the HPCC ws client
 */
public static HPCCWsClient get(String protocol, String targetWsECLWatchAddress,
        int targetWsECLWatchPort, String user, String password)
```

**Rules:**
- Each `@param` tag's description is indented with extra spaces (aligned 4 spaces in from the tag).
- `@return` is present on all non-void methods.
- `@throws` is present for all declared checked exceptions.
- The Javadoc plugin excludes generated packages: `org.hpccsystems.ws.client.gen.*`, `org.hpccsystems.ws.client.antlr.*`, `org.hpccsystems.ws.client.wrappers.gen.*`, `org.hpccsystems.ws.client.extended.*`.
- `failOnWarnings=true` in the Javadoc plugin — missing tags cause build failures.

---

## 7. OpenTelemetry `@WithSpan` Usage

The project uses OpenTelemetry for distributed tracing. Every significant service method should be annotated.

### Imports

```java
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
```

### Annotation Patterns

```java
// Simple method spanning
@WithSpan
public boolean doesTargetHPCCAuthenticate() throws Exception { ... }

// Span with parameter attributes captured
public String[] listNSKeys(
        @SpanAttribute String storename,
        @SpanAttribute String namespace,
        @SpanAttribute boolean global) throws Exception, ArrayOfEspExceptionWrapper
{ ... }

// Test methods also annotated (in WsClientTest.java)
@Test
@WithSpan
public void testAvailableClusterGroups() { ... }
```

### SDK Initialization

- `BaseHPCCWsClient` and `BaseRemoteTest` both call `GlobalOpenTelemetry.get()` as the default.
- If `otel.java.global-autoconfigure.enabled=true`, `AutoConfiguredOpenTelemetrySdk.initialize()` is used instead.
- `W3CTraceContextPropagator` is used for context propagation across HTTP requests.
- The `Connection` class injects traceparent/tracestate headers into outbound requests.

---

## 8. Wrapper Classes vs Generated Stubs

### Architecture Layers

```
Caller code
    ↓
High-level clients (HPCCWsClient, HPCCWsDFUClient, etc.)
    ↓
Hand-crafted wrappers (org.hpccsystems.ws.client.wrappers.*)
    ↓
Generated thin wrappers (org.hpccsystems.ws.client.wrappers.gen.*)
    ↓
Generated Axis2 stubs (org.hpccsystems.ws.client.gen.axis2.*)  ← NEVER touch
    ↓
HPCC ESP / WS endpoint
```

### Generated Thin Wrappers (`wrappers.gen.*`)

Auto-generated by `wsclient/utils/wsInterfaceUpdater.py`. Each wrapper class:
- Has a class-level Javadoc block identifying the wrapped stub class.
- Provides a no-arg constructor.
- Provides a `getRaw()` method that builds and returns the underlying generated stub object.

```java
/**
 * Generated Axis2 ADB stub class wrapper
 * Class name: WsDFUXRefPingResponseWrapper
 * Wraps class: org.hpccsystems.ws.client.gen.axis2.wsdfuxref.latest.WsDFUXRefPingResponse
 * Output package : org.hpccsystems.ws.client.wrappers.gen.wsdfuxref
 */
public class WsDFUXRefPingResponseWrapper
{
    public WsDFUXRefPingResponseWrapper() {}

    public org.hpccsystems.ws.client.gen.axis2.wsdfuxref.latest.WsDFUXRefPingResponse getRaw()
    {
        org.hpccsystems.ws.client.gen.axis2.wsdfuxref.latest.WsDFUXRefPingResponse raw =
            new org.hpccsystems.ws.client.gen.axis2.wsdfuxref.latest.WsDFUXRefPingResponse();
        return raw;
    }
}
```

### Hand-crafted Wrappers (`wrappers.*`, non-gen)

Extend generated stub classes to add logic unavailable from the raw ESP interface. Example:

```java
// DFUFileDetailWrapper extends the generated DFUFileDetail, adds column parsing
public class DFUFileDetailWrapper extends DFUFileDetail
{
    private static final long serialVersionUID = 155L;

    private ArrayList<DFUDataColumnWrapper> columns;
    private String                          firstline = null;
    private boolean                         hasheader = false;

    public DFUFileDetailWrapper(DFUFileDetail base)
    {
        copy(base);
    }
    // ... additional methods
}
```

---

## 9. Connection Object Usage Patterns

`Connection` (`org.hpccsystems.ws.client.utils.Connection`) is the single entry point for all connectivity configuration. Never hardcode URLs.

### Standard Setup

```java
// Create from URL string
Connection conn = new Connection("http://myhpcc:8010");

// Set credentials
conn.setCredentials(username, password);

// Optional timeout tuning
conn.setConnectTimeoutMilli(5000);
conn.setSocketTimeoutMilli(30000);

// Obtain high-level client
Platform platform = Platform.get(conn);
HPCCWsClient wsclient = platform.checkOutHPCCWsClient();

// Or directly (skips Platform)
HPCCWsClient client = HPCCWsClient.get(conn);
```

### In Tests — From System Properties

```java
protected final static String connString = System.getProperty("hpccconn", "http://localhost:8010");
protected final static String hpccUser   = System.getProperty("hpccuser", "JunitUser");
protected final static String hpccPass   = System.getProperty("hpccpass", "");

connection = new Connection(connString);
connection.setCredentials(hpccUser, hpccPass);
```

### Object Pooling

High-level clients extend `DataSingleton` and use `DataSingletonCollection`:

```java
// Gets existing client or creates new one — standard pattern
HPCCWsClient client = (HPCCWsClient) All.get(new HPCCWsClient(protocol, host, port, user, pass));

// Gets existing client only — returns null if not found
HPCCWsClient client = (HPCCWsClient) All.getNoCreate(new HPCCWsClient(...));
```

---

## 10. Anti-Patterns Explicitly Avoided

| Anti-Pattern | Correct Approach |
|---|---|
| `gen.axis2.*` classes used directly in caller code | Use `wrappers.gen.*` or `wrappers.*` wrapper classes |
| Hardcoded endpoints: `new Connection("http://prod:8010")` | Read from `System.getProperty("hpccconn", "http://localhost:8010")` |
| `new HPCCWsClient(...)` called every time | Use `HPCCWsClient.get(conn)` or `platform.checkOutHPCCWsClient()` |
| No OpenTelemetry span on service methods | Annotate all service methods with `@WithSpan` |
| Modifying files in `gen.*` or `wrappers.gen.*` | Re-run `wsInterfaceUpdater.py` or extend in `wrappers.*` |
| Silent exception swallowing `catch (Exception e) {}` | At minimum log, or re-throw with context |
| Importing generated package types in calling code | Import only from `wrappers.*` — generated types stay internal |
