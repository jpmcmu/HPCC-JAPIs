# HPCC4J — Technology Stack Reference

_Generated: 2026-03-20_

---

## 1. Primary Language & Version Requirements

| Attribute | Value |
|---|---|
| Language | Java |
| Minimum JVM target | **Java 8** (`maven.compiler.release=8` in root `pom.xml`) |
| CI/CD build JDK | **Java 11** (all GitHub Actions workflows use `java-version: '11'`) |
| `rdf2hpcc` exception | Java 11 source/target (standalone module, not part of root reactor) |
| Source encoding | UTF-8 |

The root POM declares `maven.compiler.release=8`, making all reactor modules (commons-hpcc, wsclient, dfsclient, spark-hpcc) bytecode-compatible with Java 8. `clienttools` and `rdf2hpcc` are **not** part of the root `<modules>` reactor and own their own compiler settings.

---

## 2. Build System

### Maven Multi-Module Structure

```
pom.xml                  ← parent / reactor (groupId: org.hpccsystems, artifactId: hpcc4j)
├── commons-hpcc/pom.xml
├── wsclient/pom.xml
├── dfsclient/pom.xml
├── spark-hpcc/pom.xml
├── clienttools/pom.xml  ← standalone, NOT in reactor
└── rdf2hpcc/pom.xml     ← standalone, NOT in reactor
```

**Current project version:** `10.0.33-0-SNAPSHOT`

### Key Maven Plugin Versions (defined in root `pom.xml` `<properties>`)

| Property | Version | Purpose |
|---|---|---|
| `maven.compiler.version` | 3.8.0 | `maven-compiler-plugin` |
| `maven.surefire.version` | 2.22.1 | Test runner |
| `maven.failsafe.version` | 3.1.2 | Integration test runner (spark-hpcc) |
| `maven.assembly.version` | 3.1.1 | Fat-jar assembly |
| `maven.javadoc.version` | 3.1.0 | Javadoc generation |
| `maven.source.version` | 2.2.1 | Source attachment |
| `maven.gpg.version` | 1.6 | Artifact signing |
| `maven.release.version` | 2.5.3 | Release lifecycle |
| `maven.deploy.version` | 2.8.2 | Deployment |
| `maven.jar.version` | 3.0.2 | JAR packaging |
| `maven.resources.version` | 3.0.2 | Resource filtering |
| `maven.clean.version` | 3.1.0 | Clean phase |
| `central.publishing.version` | 0.7.0 | Sonatype Central publishing |
| `codehaus.template.version` | 1.0.0 | `templating-maven-plugin` (dfsclient version token injection) |
| `antlr.version` | 4.10.1 | ANTLR4 grammar + plugin (wsclient ECL parser) |

### Maven Build Commands

```bash
# Build all reactor modules
mvn install

# Release build (signs artifacts, auto-publishes to Central)
mvn clean deploy -P release

# Build with specific Spark version (spark-hpcc only)
mvn install -P spark33
mvn install -P spark34

# Run only BaseTests (default unit test suite)
mvn test

# Run remote/integration tests
mvn test -P remote-test
mvn verify -P integrationtests   # wsclient
```

### Maven Profiles

| Profile ID | Module | Purpose |
|---|---|---|
| `release` | all | GPG sign, skip tests, push to Sonatype Central |
| `jenkins-release` | all | CI release: autoupload to Central + JFrog |
| `jenkins-on-demand` | root | CI: run full RemoteTests + sign |
| `jfrog-artifactory` | root | Deploy to JFrog Artifactory (env-var endpoints) |
| `benchmark` | root | Run `@Benchmark`-annotated tests |
| `known-server-issues` | root | Run `@KnownServerIssueTests`-annotated tests |
| `remote-test` | wsclient, dfsclient | Run `@RemoteTests`-annotated tests |
| `integrationtests` | wsclient | Run `@IntegrationTests` tests |
| `spark24` / `spark33` / `spark34` | spark-hpcc | Select Spark version |
| `generate-*-stub` | wsclient | Re-generate Axis2 stubs from WSDLs |
| `generate-antlr-eclrecparser` | wsclient | Re-generate ANTLR parser from grammar |

---

## 3. Module-by-Module Dependencies

### 3.1 Root POM (inherited by all reactor modules)

| Artifact | Version | Scope | Purpose |
|---|---|---|---|
| `io.opentelemetry:opentelemetry-api` | 1.38.0 (BOM) | compile | OTel trace API |
| `io.opentelemetry:opentelemetry-sdk` | 1.38.0 | compile | OTel SDK |
| `io.opentelemetry:opentelemetry-exporter-logging` | 1.38.0 | compile | OTel log exporter |
| `io.opentelemetry:opentelemetry-sdk-extension-autoconfigure` | 1.38.0 | compile | SDK autoconfigure |
| `io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations` | 2.6.0 | compile | `@WithSpan` / `@SpanAttribute` |
| `io.opentelemetry.semconv:opentelemetry-semconv` | 1.25.0-alpha | compile | Semantic conventions |
| `junit:junit` | 4.13.1 | test | Unit testing |
| `org.apache.logging.log4j:log4j-core` | 2.17.1 | provided | Logging (CVE-mitigated) |
| `org.json:json` | 20231013 | compile | JSON parsing |
| `commons-io:commons-io` | 2.7 | compile | I/O utilities |
| `com.jcraft:jsch` | 0.1.54 | compile | SFTP / SSH (JSch) |
| `javax.mail:mail` | 1.4 | compile | Email (JavaMail) |
| `org.apache.axis2:axis2` | 2.0.0 | compile (pom) | Axis2 web service framework |
| `org.apache.axis2:axis2-kernel` | 2.0.0 | compile | Axis2 kernel |
| `org.apache.axis2:axis2-transport-http` | 2.0.0 | compile | Axis2 HTTP transport |
| `org.apache.axis2:axis2-transport-local` | 2.0.0 | compile | Axis2 local transport |
| `org.apache.axis2:axis2-adb` | 2.0.0 | compile | Axis2 ADB data binding |
| `org.apache.axis2:axis2-jaxws` | 2.0.0 | compile | Axis2 JAX-WS |
| `org.apache.httpcomponents.client5:httpclient5` | 5.4.3 | compile | HTTP client (required since Axis2 v2.0) |
| `org.apache.commons:commons-fileupload2-core` | 2.0.0-M4 | managed | CVE-2025-48976 fix for Axis2 transitive |
| `org.apache.commons:commons-fileupload2-jakarta-servlet6` | 2.0.0-M4 | managed | CVE-2025-48976 fix |

### 3.2 `commons-hpcc`

Inherits all root dependencies. Adds no module-specific runtime dependencies.

**Packages provided:**
- `org.hpccsystems.commons.annotations` — test category marker interfaces (`@BaseTests`, `@RemoteTests`, `@Benchmark`, `@KnownServerIssueTests`, `@UnverifiedServerIssues`, `@IntegrationTests`, `@RegressionTests`)
- `org.hpccsystems.commons.ecl` — `FieldDef`, `RecordDefinitionTranslator`, ECL type system
- `org.hpccsystems.commons.filter` — `SQLFilter`, `SQLExpression`, `SQLOperator`, `SQLFragment`
- `org.hpccsystems.commons.network` — `Network` utilities
- `org.hpccsystems.commons.utils` — `CryptoHelper`, `DigestAlgorithmType`, `Utils`
- `org.hpccsystems.commons.fastlz4j` — native LZ4 compression binding
- `org.hpccsystems.commons.benchmarking` — `IMetric`, `IProfilable`, `SimpleMetric`, `Units`
- `org.hpccsystems.commons.errors` — `HpccFileException`

### 3.3 `wsclient`

Inherits root deps. Additional dependencies:

| Artifact | Version | Scope | Purpose |
|---|---|---|---|
| `org.hpccsystems:commons-hpcc` | project.version | compile | HPCC commons |
| `org.antlr:antlr4` | 4.10.1 | compile | ANTLR4 runtime + tool |
| `org.antlr:antlr4-runtime` | 4.10.1 | compile | ANTLR4 runtime |

**Key packages:**
- `org.hpccsystems.ws.client` — high-level service clients (`HPCCWsClient`, `HPCCWsDFUClient`, `HPCCWsWorkUnitsClient`, `HPCCFileSprayClient`, `HPCCWsTopologyClient`, `HPCCWsSMCClient`, `HPCCWsSQLClient`, `HPCCWsStoreClient`, `HPCCWsFileIOClient`, `HPCCWsDFUXRefClient`, `HPCCWsPackageProcessClient`, `HPCCWsCloudClient`, `HPCCWsCodeSignClient`, `HPCCWsResourcesClient`)
- `org.hpccsystems.ws.client.utils` — `Connection`, `DataSingleton`, `Utils`
- `org.hpccsystems.ws.client.wrappers` — hand-written stable wrappers over generated stubs
- `org.hpccsystems.ws.client.wrappers.gen.*` — programmatically generated wrapper classes
- `org.hpccsystems.ws.client.gen.axis2.*` — Axis2 ADB-generated stubs (never modify directly)
- `org.hpccsystems.ws.client.antlr` / `.antlr.gen` — ANTLR4 ECL record parser

**WSDL services covered** (files in `wsclient/src/main/resources/WSDLs/`):

| Service | Current WSDL Version |
|---|---|
| WsWorkunits | 1.92 |
| WsDFU | 1.64 |
| WsFileSpray / FileSpray | 1.23 |
| WsTopology | 1.31 |
| WsSMC | 1.26 |
| WsPackageProcess | 1.05 |
| WsResources | 1.01 |
| WsFileIO | 1.01 |
| WsStore | 1.01 |
| WsSQL | 3.06 |
| WsDFUXRef | 1.02 |
| WsAttributes | 1.21 |
| WsCodeSign | 1.1 |
| WsCloud | (latest) |

Each service also retains **older versioned WSDLs** for backward compatibility (e.g., `WsDFU-139.wsdl`, `WsWorkunits-156.wsdl` through `-202.wsdl`). Version ranges excluded from compilation are listed in `wsclient/pom.xml` `<excludes>`.

### 3.4 `dfsclient`

Inherits root deps. Additional dependencies:

| Artifact | Version | Scope | Purpose |
|---|---|---|---|
| `org.hpccsystems:commons-hpcc` | project.version | compile | HPCC commons |
| `org.hpccsystems:wsclient` | project.version | compile | WS client for DFU info |
| `org.apache.avro:avro` | 1.12.0 | provided | Avro schema/serialization |
| `org.apache.parquet:parquet-common` | 1.15.2 | provided | Parquet support |
| `org.apache.parquet:parquet-avro` | 1.15.2 | test | Parquet/Avro test |
| `org.apache.parquet:parquet-hadoop` | 1.15.2 | test | Parquet/Hadoop test |
| `org.apache.hadoop:hadoop-client` | 3.3.6 | test | Hadoop test utilities |
| `commons-cli:commons-cli` | 1.5.0 | compile | CLI argument parsing (`FileUtility`) |

Uses `templating-maven-plugin` to inject compile-time constants (e.g., `CompileTimeConstants`) via `dfsclient/src/main/java-templates/`.

**Key packages:**
- `org.hpccsystems.dfs.client` — `HpccRemoteFileReader`, `HPCCRemoteFileWriter`, `RowServiceInputStream`, `RowServiceOutputStream`, `HPCCRecord`, `HPCCRecordBuilder`, `AvroSchemaTranslator`, `AvroRecordTranslator`, `FileUtility`, `ColumnPruner`, `CompressionAlgorithm`
- `org.hpccsystems.dfs.cluster` — `ClusterRemapper`, `AddrRemapper`, `NullRemapper`, `RemapInfo`

### 3.5 `spark-hpcc`

Inherits root deps. Additional dependencies:

| Artifact | Version | Scope | Purpose |
|---|---|---|---|
| `org.apache.spark:spark-core_<scala>` | 2.4.6 (default) | provided | Spark core |
| `org.apache.spark:spark-sql_<scala>` | 2.4.6 (default) | provided | Spark SQL / DataFrames |
| `org.hpccsystems:commons-hpcc` | project.version | compile | Commons |
| `org.hpccsystems:wsclient` | project.version | compile | WS client |
| `org.hpccsystems:dfsclient` | project.version | compile | DFS client |
| `net.razorvine:pyrolite` | 4.13 | provided | Python ↔ Java pickled objects |

**Scala / Spark version matrix** (via profiles):

| Profile | Spark | Scala binary |
|---|---|---|
| `spark24` (default) | 2.4.6 | 2.11 |
| `spark33` | 3.3.2 | 2.12 |
| `spark34` | 3.4.1 | 2.12 |

ANTLR4 excluded from spark-hpcc transitive deps to avoid classpath conflicts with Spark's own ANTLR dependency.

### 3.6 `clienttools` (standalone)

| Artifact | Version | Scope | Purpose |
|---|---|---|---|
| `junit:junit` | 4.13.1 | test | Unit tests |

No parent POM inheritance. Interfaces with `eclcc` via `ProcessBuilder`/`Runtime.exec`. Version: `1.0.0-SNAPSHOT`.

### 3.7 `rdf2hpcc` (standalone)

| Artifact | Version | Scope | Purpose |
|---|---|---|---|
| `org.hpccsystems:wsclient` | 8.12.0-1 | compile | WS client (pinned old version) |
| `org.apache.jena:jena-core` | 4.2.0 | compile | Apache Jena RDF model API |
| `org.apache.jena:jena-arq` | 4.2.0 | compile | SPARQL query engine |
| `junit:junit` | 4.13.1 | test | Unit tests |

Java 11 source/target. Version `1.0.4-SNAPSHOT`. Uses legacy Sonatype (oss.sonatype.org) for snapshot deploy.

---

## 4. Frameworks

### Apache Axis2 (v2.0.0)
- Web service binding framework for all SOAP communication with HPCC ESP
- Data binding: **ADB (Axis2 Data Binding)**
- HTTP transport backed by **Apache HttpClient 5** (`httpclient5:5.4.3`) — mandatory upgrade from HttpClient 4 since Axis2 v2.0
- Stubs generated via `axis2-wsdl2code-maven-plugin` (per-service Maven profiles)
- Authentication: custom `HPCCPreemptiveAuthInterceptor` implements `HttpRequestInterceptor` to force Basic auth without waiting for 401 challenge

### Apache Spark (2.x / 3.x)
- `spark-core` + `spark-sql` — both `provided` scope (deployed by cluster)
- `HpccRelationProvider` implements Spark DataSource API V1
- `HpccRDD` extends `RDD` for partition-parallel HPCC file reading
- `HpccFileWriter` writes Spark DataFrames to HPCC DFS
- `SparkSchemaTranslator` converts Spark `StructType` ↔ HPCC `FieldDef`

### ANTLR4 (v4.10.1)
- Used in `wsclient` to parse ECL record definitions
- Grammar in `wsclient/src/main/java/org/hpccsystems/ws/client/antlr/`
- Generated parser in `org.hpccsystems.ws.client.antlr.gen`
- Regeneration via `-P generate-antlr-eclrecparser` profile

### Apache Jena (v4.2.0)
- Used only in `rdf2hpcc`
- `jena-core` for RDF model/triple manipulation
- `jena-arq` for SPARQL queries

---

## 5. Configuration Management

### No application-level config framework
The library does **not** embed Spring, MicroProfile Config, or similar. Configuration is passed programmatically via:

1. **`Connection` object** (`wsclient/src/main/java/org/hpccsystems/ws/client/utils/Connection.java`) — encapsulates host, port, protocol, credentials
2. **`DataSingleton`** — cached client instances keyed by `Connection` identity (hash/equals on connection fields)
3. **Environment variables** — used in CI (`JFROG_URL`, `JFROG_REPOSITORY`, etc.) and optionally in test harnesses

### Test Configuration
- Tests read HPCC cluster details from environment variables or system properties
- `BaseHPCCWsClientTest` (wsclient test base) provides shared setup
- `BaseIntegrationTest` pattern used in spark-hpcc integration tests
- Log config: `wsclient/src/main/resources/log4j2.xml` (excluded from packaged JARs via maven-jar-plugin `<excludes>`)
- Log4j2 XML excluded from JARs: `log4j.properties`, `log4j2.xml`, `WSDLs/` directory

### Benchmarking Flag
- `project.benchmarking` property (default `false`) controls benchmark-related behavior; set `true` via `-P benchmark` profile

---

## 6. Key Dev Tools

### Eclipse Formatter
- File: `eclipse/HPCC-JAVA-Formatter.xml`
- **Mandatory** for all contributions
- Usage: Eclipse → Window → Preferences → Java → Code Style → Formatter → Import
- Settings: 4-space indentation, no tabs

### License Headers
- Apache License 2.0 headers required on all source files
- Headers must include the current year

### Python Code-Generation Utility
- `wsclient/utils/wsInterfaceUpdater.py` — generates Axis2 stub + wrapper classes from WSDL/ESDL
- Usage documented in `wsclient/utils/README.md`
- Re-run whenever HPCC ESDL interfaces change; output goes to `org.hpccsystems.ws.client.gen.axis2.*`

### Test Generator Tooling
- `scripts/wsclient-test-generator/TestGeneratorAgent.py` — AI-assisted test generation for wsclient services
- Related prompts, reports, and analysis docs in `scripts/wsclient-test-generator/`

### GitHub Actions (CI/CD)
Located in `.github/workflows/`:

| Workflow | Trigger | Purpose |
|---|---|---|
| `Nightly.yml` | schedule (weekdays 02:00 UTC) | `mvn package` on master |
| `JAPIPRBuildAction.yml` | pull_request | PR build + test |
| `BuildTestCandidateAndMaster.yml` | push (candidate/master) | Full build |
| `publish-snapshots-on-merge.yml` | push master/candidate | Deploy SNAPSHOT to Central + JFrog |
| `publish-release-on-merge.yml` | push tag `*-release` | Deploy release to Central + JFrog |
| `codeql-analysis.yml` | push/PR | GitHub CodeQL security scan |
| `k8s-regression-suite.yml` | on-demand | Kubernetes cluster regression |
| `baremetal-regression-suite.yml` | on-demand | Bare-metal cluster regression |
| `auto-upmerge.yml` | push to candidate | Automated branch upmerge |
| `build-docker.yml` | on-demand | Docker image build |

CI uses **Java 11 / AdoptOpenJDK** (`actions/setup-java@v4`, distribution `adopt`).
