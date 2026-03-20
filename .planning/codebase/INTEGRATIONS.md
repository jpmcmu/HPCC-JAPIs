# HPCC4J — External Integrations Reference

_Generated: 2026-03-20_

---

## 1. HPCC Systems Cluster Communication

### 1.1 ESP (Enterprise Service Platform) — SOAP/HTTP

All high-level service calls go through the HPCC ESP layer via SOAP over HTTP/HTTPS.

**Default Ports**

| Protocol | Port | Constant |
|---|---|---|
| HTTP | **8010** | `BaseHPCCWsClient.DEAFULTECLWATCHPORT` |
| HTTPS (TLS) | **18010** | `BaseHPCCWsClient.DEFAULTECLWATCHTLSPORT` |

**Connection setup:**
```java
// HTTP
Connection conn = new Connection("http://myhpcc:8010");
conn.setCredentials("username", "password");
HPCCWsClient client = HPCCWsClient.get(conn);

// HTTPS
Connection conn = new Connection("https://myhpcc:18010");
conn.setCredentials("username", "password");
HPCCWsClient client = HPCCWsClient.get(conn);
```

**Connection class:** `wsclient/src/main/java/org/hpccsystems/ws/client/utils/Connection.java`

Fields managed by `Connection`:
- `protocol` — `http` or `https`, detected via `Connection.isSslProtocol()`
- `port` — with `portDelimiter = ':'`
- `Credentials` — inner class holding `userName` / `password`, Base64-encoded for Basic auth
- URL parsing utilities for extracting host/port from connection strings

### 1.2 DFSClient — Binary Row Service Protocol

Direct file partition access bypasses ESP and communicates with **DAFILESERV** (row service) via a low-level binary TCP socket protocol.

**Key classes:**
- `RowServiceInputStream` (`dfsclient/src/main/java/org/hpccsystems/dfs/client/RowServiceInputStream.java`) — reads file partitions over raw TCP socket
- `RowServiceOutputStream` (`dfsclient/src/main/java/org/hpccsystems/dfs/client/RowServiceOutputStream.java`) — writes file partitions
- `HpccRemoteFileReader` — orchestrates partition reads using `RowServiceInputStream`
- `HPCCRemoteFileWriter` — orchestrates partition writes

**Socket details:**
- Uses `java.net.Socket` / `javax.net.ssl.SSLSocket` depending on SSL flag
- SSL support: `SSLSocketFactory.getDefault()` wraps plain socket with TLS for encrypted partition access
- Port is cluster/partition-specific — obtained from DFU metadata via WsClient (not hardcoded)
- `RemapInfo` object handles Kubernetes/NAT address remapping: `RemapInfo(thorNodes, ip, rowServicePort, useSSL)`
- Socket operation timeout: `RowServiceInputStream.DEFAULT_SOCKET_OP_TIMEOUT_MS` (configurable via `FileUtility` CLI)

**File access flow:**
1. Call `HPCCWsDFUClient` (via wsclient) to get `DFUFileAccessInfoWrapper` — contains partition metadata and data node addresses
2. `ClusterRemapper` (or `AddrRemapper` / `NullRemapper`) translates cluster addresses for local/K8s reachability
3. `RowServiceInputStream` opens binary socket to each data node and streams partition data

### 1.3 SFTP / SSH Transfer

`JSch` (`com.jcraft:jsch:0.1.54`) is included as a root-level dependency for secure file transfer (FileSpray spray operations, legacy SSH tunnels).

---

## 2. Authentication Mechanisms

### 2.1 HTTP Basic Authentication (Preemptive)

All ESP SOAP calls use **preemptive HTTP Basic authentication** — credentials are sent in the initial request without waiting for a `401 WWW-Authenticate` challenge.

**Implementation:** `HPCCPreemptiveAuthInterceptor` (`wsclient/src/main/java/org/hpccsystems/ws/client/HPCCPreemptiveAuthInterceptor.java`)
- Implements `org.apache.hc.core5.http.HttpRequestInterceptor` (HttpClient 5 API)
- Registered against the Axis2 `HttpTransportPropertiesImpl` configuration
- Uses `org.apache.hc.client5.http.auth.UsernamePasswordCredentials`
- Credentials derived from `Connection.setCredentials(user, password)`

**Unsecured clusters:** Username and password can be empty strings for clusters with no authentication.

### 2.2 TLS / SSL

- **ESP connections:** `Connection.isSslProtocol()` detects `https://` protocol prefix and configures Axis2 transport accordingly; default TLS port is `18010`
- **DFS connections:** `RowServiceInputStream` uses `SSLSocketFactory` when `RemapInfo.useSSL = true`; both ESP metadata retrieval and direct row service reads can be independently TLS-enabled
- No custom truststore configuration is present in the codebase — relies on the JVM's default truststore

### 2.3 Detection of Authentication Requirement

```java
// BaseHPCCWsClient
@WithSpan
public boolean doesTargetHPCCAuthenticate() throws Exception
```

Queries the cluster to determine whether authentication is required at runtime, caching the result in `targetHPCCAuthenticates`.

---

## 3. Web Service Binding Technology

### 3.1 Apache Axis2 ADB (Axis Data Binding)

**Version:** `axis2:2.0.0`

All HPCC ESP web services are consumed via Axis2 with the ADB data binding strategy.

**Code generation:**
- Source: WSDL files in `wsclient/src/main/resources/WSDLs/`
- Tool: `axis2-wsdl2code-maven-plugin` (activated via per-service Maven profiles)
- Output package: `org.hpccsystems.ws.client.gen.axis2.<service>.<version>`
- Binding: `<databindingName>adb</databindingName>`
- Sync mode: synchronous (`<syncMode>sync</syncMode>`)

**Regeneration example:**
```bash
# Regenerate WsDFU stubs from WSDL
mvn -P generate-wsdfu-stub process-resources -DskipTests

# Regenerate WsWorkunits stubs
mvn -P generate-wsworkunits-stub process-resources -DskipTests
```

**Available generation profiles:**
- `generate-wsworkunits-stub` (WsWorkunits.wsdl v1.92)
- `generate-wsdfu-stub` (WsDFU.wsdl v1.64)
- `generate-filespray-stub` (WsFileSpray.wsdl v1.23)
- `generate-wspackageprocess-stub` (WsPackageProcess.wsdl v1.05)
- `generate-wsresources-stub` (WsResources.wsdl v1.01)
- `generate-wssmc-stub` (WsSMC.wsdl v1.26)
- `generate-wsdfuxref-stub` (WsDFUXRef.wsdl v1.02)
- `generate-wstopology-stub` (WsTopology.wsdl v1.31)
- `generate-wsfileio-stub` (WsFileIO.wsdl v1.01)
- `generate-wsattributes-stub` (WsAttributes.wsdl v1.21)
- `generate-wscodesign-stub` (WsCodeSign.wsdl v1.1)
- `generate-wsstore-stub` (WsStore.wsdl v1.01)
- `generate-wssql-stub` (WsSQL.wsdl v3.06)
- `generate-wscloud-stub` (WsCloud.wsdl)

### 3.2 Wrapper Layer Architecture

Generated stubs are **never used directly by callers**. A stable wrapper layer isolates consumers:

```
Caller → HPCCWsDFUClient (high-level client)
              ↓
         org.hpccsystems.ws.client.wrappers.gen.wsdfu.*  (generated wrapper classes)
              ↓
         org.hpccsystems.ws.client.gen.axis2.wsdfu.latest.*  (Axis2 ADB stubs)
              ↓
         HPCC ESP (SOAP/HTTP)
```

- `BaseHPCCWsClient` — abstract base for all service clients; manages `Connection`, Axis2 `Stub`, `Options`, `EndpointReference`
- `HPCCWsClientPool` — object pool for client instances (check-in/check-out pattern using `DataSingleton` cache)
- Wrapper code generated by `wsclient/utils/wsInterfaceUpdater.py`

### 3.3 ANTLR4 ECL Parser

An ANTLR4-based parser (`grammar` in `wsclient/src/main/java/org/hpccsystems/ws/client/antlr/`) handles ECL record definition strings returned by HPCC. Generated parser lives in `org.hpccsystems.ws.client.antlr.gen`.

---

## 4. OpenTelemetry / Distributed Tracing

### Integration Scope

OpenTelemetry is integrated at the **root POM level**, meaning all reactor modules receive tracing support automatically.

**OTel dependency versions:**
```
opentelemetry-bom: 1.38.0
opentelemetry-instrumentation-annotations: 2.6.0
opentelemetry-semconv: 1.25.0-alpha
```

**Usage volume:**
- `wsclient`: **263** OTel-annotated sites
- `dfsclient`: **41** OTel-annotated sites

### Span Instrumentation

Public methods throughout the client stack are annotated with `@WithSpan` (from `opentelemetry-instrumentation-annotations`):

```java
// Connection.java — traces individual HTTP requests
@WithSpan
public String sendHttpRequest(
    @SpanAttribute String url,
    @SpanAttribute String method) { ... }

// BaseHPCCWsClient.java — traces service calls
@WithSpan
public boolean doesTargetHPCCAuthenticate() throws Exception { ... }

// RowServiceInputStream.java — traces binary partition reads
@WithSpan
private void openConnection(...) { ... }
```

### Span Attributes Used

Semantic conventions applied:
- `HttpAttributes` (`http.request.method`, `http.response.status_code`)
- `ServerAttributes` (`server.address`, `server.port`)
- `ExceptionAttributes` (`exception.type`, `exception.message`)
- `ServiceAttributes` (service identity)

### Span Context Propagation

```java
// BaseHPCCWsClient.java
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
```

W3C Trace Context propagation is enabled for outbound HTTP calls, enabling distributed tracing across HPCC ESP services.

### SDK Configuration

```java
// BaseHPCCWsClient.java
private static OpenTelemetry globalOTel = null;

// GlobalOpenTelemetry.get() used in Connection.java and RowServiceInputStream.java
```

`opentelemetry-sdk-extension-autoconfigure` is included, so the SDK auto-configures from environment variables (`OTEL_EXPORTER_OTLP_ENDPOINT`, `OTEL_SERVICE_NAME`, etc.) if provided at runtime. Default exporter: `opentelemetry-exporter-logging`.

---

## 5. Apache Jena / RDF Integration (`rdf2hpcc`)

`rdf2hpcc` uses Apache Jena as its primary dependency for RDF ingestion into HPCC:

| Component | Artifact | Purpose |
|---|---|---|
| Jena Core | `jena-core:4.2.0` | RDF model, triple store, ontology |
| Jena ARQ | `jena-arq:4.2.0` | SPARQL 1.1 query engine |
| WsClient | `wsclient:8.12.0-1` | Target HPCC upload (pinned release) |

**Flow:** RDF triples (parsed by Jena) → transformed → uploaded to HPCC via `wsclient` spray/DFU operations.

---

## 6. Apache Spark Integration (`spark-hpcc`)

### DataSource API

The connector uses Spark DataSource V1 API:

```java
Dataset<Row> df = spark.read()
    .format("hpcc")
    .option("host", "http://127.0.0.1:8010")
    .option("cluster", "mythor")
    .option("path", "/spark/test/dataset")
    .load();

df.write()
    .format("hpcc")
    .mode("overwrite")
    .option("host", "http://127.0.0.1:8010")
    .option("cluster", "mythor")
    .save("/spark/test/output");
```

### Key Classes

| Class | Role |
|---|---|
| `HpccRelationProvider` | Entry point, implements `RelationProvider` / `CreatableRelationProvider` |
| `HpccRDD` | Extends `RDD[Row]`, reads HPCC file partitions in parallel |
| `HpccFileWriter` | Writes Spark DataFrames to HPCC DFS |
| `SparkSchemaTranslator` | Converts `StructType` ↔ HPCC `FieldDef` |

### File Path Conventions (Databricks Compatible)

```
Legacy:  namespace::filename     (e.g., spark::test::dataset)
URI:     /namespace/filename     (preferred, required for Databricks)
```

### Pyrolite Bridge

`net.razorvine:pyrolite:4.13` (`provided` scope) bridges Python pickled objects when using PySpark; see `spark-hpcc/Examples/PySparkExample.ipynb`.

---

## 7. Maven Repository / Deployment Targets

### Snapshot Repository

| Repository | URL |
|---|---|
| **Sonatype Central Snapshots** | `https://central.sonatype.com/repository/maven-snapshots/` |

Configured in root `pom.xml` `<distributionManagement>` and `<repositories>`. Used automatically on `mvn deploy` without the `release` profile.

### Release Repository

| Repository | Profile | URL |
|---|---|---|
| **Maven Central** (via Sonatype Central Portal) | `release`, `jenkins-release` | Portal API via `central-publishing-maven-plugin:0.7.0` |
| **JFrog Artifactory** | `jfrog-artifactory` | `${env.JFROG_URL}/artifactory/${env.JFROG_REPOSITORY}` |

Release to Maven Central requires:
- GPG signing (key provisioned via `secrets.CENTRAL_SIGNING_SECRET` in GitHub)
- `autoPublish=true` (set by `release` / `jenkins-release` profiles)
- Passphrase via `-Dgpg.passphrase=...`

**Legacy (`rdf2hpcc` only):** Uses old Sonatype OSSRH (`https://oss.sonatype.org/`) with `nexus-staging-maven-plugin:1.6.7`.

### Published Artifacts on Maven Central

| Module | Group | Artifact |
|---|---|---|
| Parent | `org.hpccsystems` | `hpcc4j` |
| Commons | `org.hpccsystems` | `commons-hpcc` |
| WsClient | `org.hpccsystems` | `wsclient` |
| DFSClient | `org.hpccsystems` | `dfsclient` |
| Spark | `org.hpccsystems` | `spark-hpcc` |

Badges on `README.md` link to Maven Central via `maven-badges.herokuapp.com`.

---

## 8. CI/CD Pipeline Integrations

### GitHub Actions

Hosted at `.github/workflows/`. Key external integrations:

| Integration | Usage |
|---|---|
| `actions/checkout@v4` | Source checkout |
| `actions/setup-java@v4` (AdoptOpenJDK 11) | Java environment |
| Sonatype Central Portal | SNAPSHOT + release deploy (`secrets.CENTRAL_USER_NAME`, `secrets.CENTRAL_PASS`) |
| JFrog Artifactory | Mirror deploy (`secrets.JFROG_USER`, `secrets.JFROG_TOKEN`, `vars.JFROG_URL`) |
| GPG signing | Artifact signing (`secrets.CENTRAL_SIGNING_SECRET`, `secrets.SIGN_MODULES_PASSPHRASE`) |
| GitHub CodeQL | Static analysis / SAST (`codeql-analysis.yml`) |
| Jira (track.hpccsystems.com) | Issue tracking (`Jirabot.yml`, `JirabotMerge.yml`) |
| HPCC baremetal cluster | Regression suites (`baremetal-regression-suite.yml`) |
| HPCC Kubernetes cluster | K8s regression (`k8s-regression-suite.yml`) |

### Snapshot Auto-Publish Trigger

```yaml
# publish-snapshots-on-merge.yml
on:
  push:
    branches:
      - 'master'
      - 'candidate-*'
```

### Release Trigger

```yaml
# publish-release-on-merge.yml
on:
  push:
    tags:
      - '*-release'
```

### Issue Tracking

- **Jira project:** `JAPI` at `https://track.hpccsystems.com/browse/JAPI`
- **GitHub Issues** used for `spark-hpcc` specifically (`https://github.com/hpcc-systems/Spark-HPCC/issues`)

---

## 9. Security Vulnerability Management

### CVE-Mitigated Dependencies

| CVE / Issue | Component | Resolution |
|---|---|---|
| Log4Shell (GHSA-jfh8-c2jp-5v3q) | `log4j-core` | Bumped to `2.17.1+` in root POM; scope `provided` |
| CVE-2025-48976 | `commons-fileupload2` (Axis2 transitive) | Overridden to `2.0.0-M4` in `<dependencyManagement>` |
| WsClient packaging error | `wsclient 6.8.0` / `6.8.2` | Documented in README, versions withdrawn |

### Packaging: Log4j Exclusion from JARs

```xml
<!-- root pom.xml maven-jar-plugin -->
<excludes>
  <exclude>**/log4j.properties</exclude>
  <exclude>**/log4j2.xml</exclude>
  <exclude>**/WSDLs</exclude>
</excludes>
```

Log4j configuration files and WSDL resources are **excluded from packaged JARs** to prevent configuration leakage and bloat in downstream consumers.
