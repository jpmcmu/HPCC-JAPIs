# HPCC4J Directory & Package Structure Reference

## 1. Top-Level Directory Layout

```
hpcc4j2/
├── pom.xml                    Parent POM; declares 4 active modules + shared deps/plugins
├── README.md                  Project overview
├── MIGRATION-10.0.md          Migration guide for 10.x upgrades
├── CodeArchitectureAnalysis.md High-level architecture notes
│
├── commons-hpcc/              Module: shared foundation types (no upstream deps)
├── wsclient/                  Module: HPCC ESP SOAP web service clients
├── dfsclient/                 Module: HPCC Distributed File System binary client
├── spark-hpcc/                Module: Apache Spark connector
│
├── clienttools/               Standalone sub-project: Java wrapper for eclcc/client tools
│                              (NOT in parent pom.xml <modules>)
├── rdf2hpcc/                  Standalone sub-project: RDF data ingestion
│                              (NOT in parent pom.xml <modules>)
│
├── eclipse/                   IDE configuration
│   └── HPCC-JAVA-Formatter.xml  Eclipse Java formatter — mandatory for all contributions
│
├── scripts/                   Developer/CI utility scripts
│   ├── jira-ticket-cli/       Shell+PowerShell scripts to create Jira tickets
│   └── wsclient-test-generator/ AI-assisted test generation tooling
│
└── WsDFU_getFileDataColumnsTestGeneration/   Historical one-off test gen analysis docs
```

---

## 2. commons-hpcc Module

**Maven artifact:** `org.hpccsystems:commons-hpcc`  
**No dependencies on other hpcc4j modules.**

```
commons-hpcc/
├── pom.xml
├── README.md
└── src/
    ├── main/
    │   ├── java/
    │   │   └── org/hpccsystems/commons/
    │   │       ├── annotations/        Test category marker annotations
    │   │       ├── benchmarking/       Benchmarking utilities (@Benchmark)
    │   │       ├── ecl/                Core ECL/HPCC schema and type system
    │   │       ├── errors/             Exception hierarchy
    │   │       ├── fastlz4j/           LZ4 compression support
    │   │       ├── filter/             ECL filter/predicate utilities
    │   │       ├── network/            Network helper utilities
    │   │       └── utils/              General utilities
    │   └── javadoc/                    Package-level Javadoc HTML templates
    └── test/
        ├── java/org/hpccsystems/commons/
        └── resources/
            └── LoremIpsum.txt          Test data file
```

### Key Packages in commons-hpcc

#### `org.hpccsystems.commons.ecl`
The heart of the type system. Almost every other module depends on this package.

| File | Purpose |
|---|---|
| `FieldDef.java` | Named, typed field descriptor (supports nesting, unsigned, blob flags). Serializable. |
| `FieldType.java` | Enumeration of all HPCC ECL field types (STRING, INTEGER, REAL, DECIMAL, RECORD, DATASET, SET, …) |
| `HpccSrcType.java` | Source encoding enum (LITTLE_ENDIAN, BIG_ENDIAN, UTF8, UNICODE, …) |
| `RecordDefinitionTranslator.java` | Parses JSON schema from ESP → `FieldDef` tree. ~1038 lines. |
| `FieldFilter.java`, `FieldFilterRange.java`, `FileFilter.java` | Predicate/range filter model for server-side filtering |
| `TestFieldDefinitions.java` | Reusable test schema constants |

#### `org.hpccsystems.commons.errors`

| File | Purpose |
|---|---|
| `HpccFileException.java` | Primary checked exception for DFS operations |
| `HpccError.java`, `HpccErrorBlock.java` | Structured error model |
| `HpccErrorCode.java`, `HpccErrorLevel.java`, `HpccErrorType.java`, `HpccErrorSource.java` | Error classification enums |
| `WUException.java` | Workunit-specific exception |
| `UnparsableContentException.java` | Schema/content parsing failure |

#### `org.hpccsystems.commons.annotations`
Marker interfaces used with JUnit's `@Category` and Maven Surefire `<groups>`:

| Annotation | Usage |
|---|---|
| `BaseTests` | Always-run unit tests (default surefire group) |
| `IntegrationTests` | Require live HPCC cluster |
| `RemoteTests` | Require network |
| `KnownServerIssueTests` | Excluded by default — server bugs |
| `UnverifiedServerIssues` | Excluded by default — suspected server issues |
| `UnverifiedClientIssues` | Excluded by default — suspected client issues |
| `RegressionTests` | Regression suite |
| `Benchmark` | Performance benchmark (used with benchmarking framework) |

---

## 3. wsclient Module

**Maven artifact:** `org.hpccsystems:wsclient`  
**Depends on:** `commons-hpcc`

```
wsclient/
├── pom.xml                    Extensive <excludes> list for old gen stub versions
├── README.md
├── utils/
│   ├── wsInterfaceUpdater.py  WSDL/ESDL → stub generation pipeline script
│   └── README.md
└── src/
    ├── main/
    │   ├── java/
    │   │   └── org/hpccsystems/ws/client/
    │   │       ├── *.java                     High-level service clients + HPCCWsClient
    │   │       ├── antlr/                     ANTLR4-based ECL record grammar parser
    │   │       │   ├── EclRecord.g4           Grammar source
    │   │       │   ├── EclRecordReader.java   Entry into ANTLR parse
    │   │       │   └── gen/                   ANTLR-generated lexer/parser Java classes
    │   │       ├── extended/
    │   │       │   └── HPCCWsAttributesClient.java  Hand-crafted WsAttributes client
    │   │       ├── gen/
    │   │       │   └── axis2/                 ALL Axis2 ADB generated stubs
    │   │       │       ├── filespray/latest/  (only latest/* compiled per service)
    │   │       │       ├── wsattributes/latest/
    │   │       │       ├── wscloud/latest/
    │   │       │       ├── wscodesign/latest/
    │   │       │       ├── wsdfu/latest/      e.g. WsDfuStub.java, DFUInfoRequest.java
    │   │       │       ├── wsdfuxref/latest/
    │   │       │       ├── wsfileio/latest/
    │   │       │       ├── wspackageprocess/latest/
    │   │       │       ├── wsresources/latest/
    │   │       │       ├── wssmc/latest/
    │   │       │       ├── wssql/latest/
    │   │       │       ├── wsstore/latest/
    │   │       │       ├── wstopology/latest/
    │   │       │       └── wsworkunits/latest/
    │   │       ├── platform/                  Platform model objects
    │   │       │   ├── Cluster.java
    │   │       │   ├── Workunit.java
    │   │       │   ├── LogicalFile.java
    │   │       │   ├── PhysicalFile.java
    │   │       │   ├── Platform.java
    │   │       │   ├── Version.java
    │   │       │   └── ...
    │   │       ├── utils/                     Infrastructure utilities
    │   │       │   ├── Connection.java        Connection + credential management
    │   │       │   ├── ObjectPool.java        Generic timed object pool
    │   │       │   ├── DataSingleton.java     Observable base with monitoring thread
    │   │       │   ├── DataSingletonCollection.java  Instance cache/flyweight store
    │   │       │   ├── Axis2ADBStubWrapperMaker.java  Wrapper code generator tool
    │   │       │   ├── Utils.java             Misc utility methods
    │   │       │   ├── FileFormat.java
    │   │       │   ├── DelimitedDataOptions.java
    │   │       │   └── ...
    │   │       └── wrappers/                  Stable wrapper layer
    │   │           ├── *.java                 Hand-written shared wrappers
    │   │           │                          (EspSoapFaultWrapper, ArrayOf*, etc.)
    │   │           ├── gen/                   Tool-generated wrappers (Axis2ADBStubWrapperMaker)
    │   │           │   ├── filespray/
    │   │           │   ├── wscloud/
    │   │           │   ├── wscodesign/
    │   │           │   ├── wsdfu/
    │   │           │   ├── wsdfuxref/
    │   │           │   ├── wsfileio/
    │   │           │   ├── wspackageprocess/
    │   │           │   ├── wsresources/
    │   │           │   ├── wssmc/
    │   │           │   ├── wssql/
    │   │           │   ├── wsstore/
    │   │           │   ├── wstopology/
    │   │           │   └── wsworkunits/
    │   │           ├── wsdfu/                 Hand-written DFU domain wrappers
    │   │           │   ├── DFUFileDetailWrapper.java
    │   │           │   ├── DFUFileAccessInfoWrapper.java
    │   │           │   ├── DFUCreateFileWrapper.java
    │   │           │   ├── DFUDataColumnWrapper.java
    │   │           │   ├── DFUFilePartWrapper.java
    │   │           │   └── ...
    │   │           └── wsworkunits/           Hand-written WU domain wrappers
    │   │               └── WorkunitWrapper.java
    │   └── resources/                         Any bundled resources (e.g. log4j.xml)
    └── test/
        └── java/org/hpccsystems/ws/client/
            └── (all test classes extend BaseHPCCWsClientTest)
```

### Key Package Namespaces in wsclient

| Namespace | Content |
|---|---|
| `org.hpccsystems.ws.client` | High-level service clients, HPCCWsClient |
| `org.hpccsystems.ws.client.utils` | Connection, ObjectPool, Axis2ADBStubWrapperMaker |
| `org.hpccsystems.ws.client.platform` | Platform model (Cluster, Workunit, Version) |
| `org.hpccsystems.ws.client.gen.axis2.<svc>.latest` | **Generated** Axis2 ADB stubs |
| `org.hpccsystems.ws.client.wrappers` | Hand-written cross-service exception wrappers |
| `org.hpccsystems.ws.client.wrappers.gen.<svc>` | **Generated** stable wrapper classes |
| `org.hpccsystems.ws.client.wrappers.wsdfu` | Hand-written DFU domain objects |
| `org.hpccsystems.ws.client.wrappers.wsworkunits` | Hand-written WU domain objects |
| `org.hpccsystems.ws.client.antlr` | ANTLR4 grammar + hand-written reader |
| `org.hpccsystems.ws.client.antlr.gen` | ANTLR-generated lexer/parser |
| `org.hpccsystems.ws.client.extended` | Specialty hand-crafted clients |

### Generated vs. Hand-Written in wsclient

```
GENERATED (never modify directly):
  gen/axis2/**              ← Axis2 WSDL2Java
  wrappers/gen/**           ← Axis2ADBStubWrapperMaker
  antlr/gen/**              ← ANTLR4 tool

HAND-WRITTEN:
  *.java (top-level)        ← Service clients
  utils/                    ← Infrastructure
  platform/                 ← Domain model
  extended/                 ← Specialty clients
  wrappers/*.java           ← Exception wrappers
  wrappers/wsdfu/           ← DFU domain objects
  wrappers/wsworkunits/     ← WU domain objects
  antlr/EclRecord.g4        ← Grammar source
  antlr/EclRecordReader.java ← Grammar consumer
```

---

## 4. dfsclient Module

**Maven artifact:** `org.hpccsystems:dfsclient`  
**Depends on:** `commons-hpcc`, `wsclient`  
**Optional provided deps:** `avro`, `parquet-common`, `parquet-hadoop`, `hadoop-common`

```
dfsclient/
├── pom.xml
├── README.md
└── src/
    ├── main/
    │   ├── java/
    │   │   └── org/hpccsystems/
    │   │       ├── dfs/
    │   │       │   ├── client/              Core DFS read/write classes
    │   │       │   │   ├── HPCCFile.java            Logical file descriptor + partition resolver
    │   │       │   │   ├── DataPartition.java        Physical part address + access token
    │   │       │   │   ├── HpccRemoteFileReader.java Iterator<T> over a single partition
    │   │       │   │   ├── HPCCRemoteFileWriter.java Writer to a single partition
    │   │       │   │   ├── BinaryRecordReader.java   Binary deserialiser (uses FieldDef)
    │   │       │   │   ├── BinaryRecordWriter.java   Binary serialiser
    │   │       │   │   ├── RowServiceInputStream.java  TCP stream from Thor node
    │   │       │   │   ├── RowServiceOutputStream.java TCP stream to Thor node
    │   │       │   │   ├── CircularByteBuffer.java  Prefetch ring buffer
    │   │       │   │   ├── IRecordReader.java        Interface for record readers
    │   │       │   │   ├── IRecordWriter.java        Interface for record writers
    │   │       │   │   ├── IRecordBuilder.java       Interface for record construction
    │   │       │   │   ├── IRecordAccessor.java      Interface for record field access
    │   │       │   │   ├── HPCCRecord.java           Generic Map-based HPCC record
    │   │       │   │   ├── HPCCRecordBuilder.java    Builds HPCCRecord from binary
    │   │       │   │   ├── HPCCRecordAccessor.java   Field accessor for HPCCRecord
    │   │       │   │   ├── ReflectionRecordBuilder.java  Reflection-based POJO builder
    │   │       │   │   ├── ColumnPruner.java         Column projection pruner
    │   │       │   │   ├── CompiledFieldFilter.java  Server-side filter compiler
    │   │       │   │   ├── CompressionAlgorithm.java Compression enum (LZ4, Zstd, …)
    │   │       │   │   ├── AvroGenericRecordAccessor.java  Avro support
    │   │       │   │   ├── AvroRecordTranslator.java
    │   │       │   │   ├── AvroSchemaTranslator.java
    │   │       │   │   ├── ParquetInputFile.java     Parquet integration
    │   │       │   │   ├── ParquetOutputFile.java
    │   │       │   │   ├── PartitionProcessor.java   TLK/index-based partition splitting
    │   │       │   │   ├── RFCCodes.java             Row-service protocol codes
    │   │       │   │   ├── HpccRandomAccessFileReader.java  Random-access index reader
    │   │       │   │   ├── FileUtility.java         Utility methods
    │   │       │   │   └── Utils.java
    │   │       │   └── cluster/                     Cluster address remapping
    │   │       │       ├── ClusterRemapper.java      Main remapper implementation
    │   │       │       ├── AddrRemapper.java         Address-level remapper
    │   │       │       ├── NullRemapper.java         No-op remapper (pass-through)
    │   │       │       └── RemapInfo.java            Remapping configuration
    │   │       └── generated/                        Build-time template output
    │   │           └── package-info.java
    │   └── java-templates/                           Source templates (templating-maven-plugin)
    └── test/
        ├── java/org/hpccsystems/dfs/
        └── resources/                               Test data and config
```

### Key Namespace in dfsclient

| Namespace | Content |
|---|---|
| `org.hpccsystems.dfs.client` | All file read/write classes |
| `org.hpccsystems.dfs.cluster` | Thor node address remapping for virtual/NAT setups |
| `org.hpccsystems.generated` | Build-time template output |

### Generated vs. Hand-Written in dfsclient

```
GENERATED:
  org.hpccsystems.generated.*  ← templating-maven-plugin at build time
                                  (from src/main/java-templates/)

HAND-WRITTEN:
  Everything else in org.hpccsystems.dfs.*
```

---

## 5. spark-hpcc Module

**Maven artifact:** `org.hpccsystems:spark-hpcc`  
**Depends on:** `commons-hpcc`, `wsclient`, `dfsclient`  
**Provided deps:** `spark-core_2.11`, `spark-sql_2.11` (Spark 2.4.6)

```
spark-hpcc/
├── pom.xml
├── README.md
├── LICENSE
├── Examples/
│   └── PySparkExample.ipynb    Jupyter notebook demo (Python + Spark)
└── src/
    ├── main/
    │   ├── java/
    │   │   └── org/hpccsystems/spark/
    │   │       ├── HpccFile.java            Extends dfsclient.HPCCFile; Spark-specific methods
    │   │       ├── HpccRDD.java             RDD<Row> implementation; maps partitions to readers
    │   │       ├── HpccFileWriter.java      Distributed write to HPCC from Spark RDD
    │   │       ├── SparkSchemaTranslator.java FieldDef ↔ Spark StructType conversion
    │   │       ├── GenericRowRecordBuilder.java  IRecordBuilder → Spark Row
    │   │       ├── GenericRowRecordAccessor.java IRecordAccessor from Spark Row
    │   │       ├── FileFilterConverter.java  HPCC FileFilter ↔ Spark filter expressions
    │   │       ├── RowConstructor.java       Python pickle RowConstructor (PySpark bridge)
    │   │       ├── PySparkField.java         PySpark field descriptor
    │   │       ├── PySparkFieldConstructor.java  Pickle constructor for PySparkField
    │   │       ├── Utils.java
    │   │       └── datasource/              Spark DataSource V1 registration
    │   │           ├── HpccRelationProvider.java  RelationProvider + shortName "hpcc"
    │   │           ├── HpccRelation.java           BaseRelation + TableScan + PrunedScan
    │   │           ├── HpccOptions.java            Parsed Spark .option(...) parameters
    │   │           └── package-info.java
    │   ├── javadoc/                          Javadoc supplemental HTML
    │   └── resources/
    │       └── META-INF/services/
    │           └── org.apache.spark.sql.sources.DataSourceRegister
    │                                         Service registration file for "hpcc" shortName
    └── test/
        └── (integration tests extending BaseIntegrationTest)
```

### Key Namespace in spark-hpcc

| Namespace | Content |
|---|---|
| `org.hpccsystems.spark` | Core RDD, File, Writer, Schema classes |
| `org.hpccsystems.spark.datasource` | DataSource API integration |

### Spark DataSource Registration

The DataSource shortname `"hpcc"` is registered via:
- `HpccRelationProvider.shortName()` returning `"hpcc"`
- Java SPI file: `META-INF/services/org.apache.spark.sql.sources.DataSourceRegister`

---

## 6. clienttools Module (Standalone)

Not in parent `pom.xml`. Contains a Java wrapper / launcher for HPCC client tools (primarily `eclcc`).

```
clienttools/
├── pom.xml
├── README.md
└── src/
    └── main/
        └── java/
            └── org/hpccsystems/   (clienttools-specific package hierarchy)
```

---

## 7. rdf2hpcc Module (Standalone)

Not in parent `pom.xml`. RDF data ingestion tooling for loading RDF datasets into HPCC.

```
rdf2hpcc/
├── pom.xml
├── README.md
└── src/
    └── main/
        └── (RDF ingestion source)
```

---

## 8. scripts/ Directory

```
scripts/
├── jira-ticket-cli/
│   ├── create-jira-ticket.sh       Bash script; creates Jira ticket via REST API
│   ├── create-jira-ticket.ps1      PowerShell equivalent
│   └── README.md
│
└── wsclient-test-generator/
    ├── README.md
    ├── TestGeneratorAgent.py       Main Python agent orchestrating AI-based test gen
    ├── TestGeneratorReport.html    HTML report template/output
    ├── MethodAnalysisPrompt.md     LLM prompt: per-method behaviour analysis
    ├── ServiceAnalysisPrompt.md    LLM prompt: per-service analysis
    ├── TestGenerationPrompt.md     LLM prompt: test case generation
    ├── BatchFailureAnalysisPrompt.md LLM prompt: analysing batch compilation failures
    ├── FinalReportPrompt.md        LLM prompt: final summary report
    ├── FixTestCompilationPrompt.md LLM prompt: fix compilation errors in generated tests
    ├── FullServiceModePlan.md      Plan doc for full-service mode generation
    ├── UnverifiedServerIssuesReportPrompt.md  LLM prompt: unverified server issues
    ├── TestDocumentation/          Per-method test documentation
    └── WsResources_FullServiceTestGeneration_2026-03-04/
                                    Output artifacts from a WsResources test gen run
```

---

## 9. eclipse/ Directory

```
eclipse/
└── HPCC-JAVA-Formatter.xml    Eclipse formatter profile
```

**Usage:** All contributions must be formatted with the Eclipse Java formatter configured with this profile. In Eclipse: `Window → Preferences → Java → Code Style → Formatter → Import`. In IntelliJ IDEA or VS Code, via the Eclipse Formatter plugin.

Settings include: 4-space indentation, no tabs, K&R brace style, line-length limits.

---

## 10. Target / Build Output Directories

Each module produces a `target/` directory (git-ignored) with the standard Maven layout:

```
<module>/target/
├── classes/                     Compiled main source .class files
├── test-classes/                Compiled test .class files
├── generated-sources/
│   ├── annotations/             Annotation processor outputs
│   └── java-templates/          (dfsclient only) template expansion output
├── generated-test-sources/
│   └── test-annotations/
├── maven-archiver/
│   └── pom.properties
└── maven-status/
    └── maven-compiler-plugin/
```

---

## 11. Important Configuration Files

| File | Purpose |
|---|---|
| `pom.xml` (root) | Parent POM: module list, shared dependency versions, plugin management, surefire config |
| `wsclient/pom.xml` | WsClient build config: compiler excludes for old gen versions |
| `dfsclient/pom.xml` | DFSClient build config: templating plugin, avro/parquet provided deps |
| `spark-hpcc/pom.xml` | Spark connector config: Spark/Scala version, provided Spark deps |
| `eclipse/HPCC-JAVA-Formatter.xml` | **Mandatory** code formatter |
| `wsclient/utils/wsInterfaceUpdater.py` | WSDL→stub→wrapper pipeline |
| `spark-hpcc/src/main/resources/META-INF/services/org.apache.spark.sql.sources.DataSourceRegister` | Registers "hpcc" DataSource shortname |

---

## 12. Key Maven Properties (from root pom.xml)

```xml
<maven.compiler.release>8</maven.compiler.release>        <!-- Java 8 minimum -->
<axis2.version>2.0.0</axis2.version>
<spark.runtime.version>2.4.6</spark.runtime.version>      <!-- set in spark-hpcc pom -->
<opentelemetry.bom.version>1.38.0</opentelemetry.bom.version>
<log4j.version>2.17.1</log4j.version>
<junit.version>4.13.1</junit.version>
<groups>org.hpccsystems.commons.annotations.BaseTests</groups>
<excludedGroups>
  org.hpccsystems.commons.annotations.KnownServerIssueTests,
  org.hpccsystems.commons.annotations.UnverifiedServerIssues,
  org.hpccsystems.commons.annotations.UnverifiedClientIssues
</excludedGroups>
```

---

## 13. .planning/ Directory (this document's home)

```
.planning/
└── codebase/
    ├── ARCHITECTURE.md     Architectural patterns, data flows, abstractions
    └── STRUCTURE.md        This document — directory and package structure
```

These documents are reference material for development agents and contributors. They are not part of the Maven build.
