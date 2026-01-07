# HPCC4J Issue Analysis Prompt

You are analyzing a GitHub issue for the HPCC4J project, a Java library for interacting with HPCC Systems clusters. Your task is to determine if the issue contains sufficient information for developers to investigate and resolve it.

## Issue Details

**Title:** {ISSUE_TITLE}

**Body:**
{ISSUE_BODY}

## Analysis Checklist

### 1. Issue Type Classification
- [ ] Is this a bug report, feature request, question, or documentation issue?
- [ ] Is the issue type clearly indicated or can be inferred?

### 2. Problem Description
- [ ] Is the problem described clearly and specifically?
- [ ] Can you understand what the user is trying to accomplish?
- [ ] Is there a distinction between expected vs actual behavior?

### 3. Reproduction Steps
For bug reports, check if the issue includes:
- [ ] Step-by-step instructions to reproduce the problem
- [ ] Sample code that demonstrates the issue
- [ ] Minimal reproducible example (if applicable)
- [ ] Data or file names involved in the issue

### 4. HPCC4J Module Identification
Determine which module(s) are affected:
- **wsclient** - Web service clients for HPCC ESP services
- **dfsclient** - Direct file system access to HPCC distributed files
- **spark-hpcc** - Spark connector for reading/writing HPCC datasets
- **clienttools** - Java interface to HPCC client tools (eclcc)
- **rdf2hpcc** - RDF data ingestion tool
- **commons-hpcc** - Common utilities

Is the affected module clearly stated or identifiable?

### 5. Version Information

#### HPCC4J Version
- [ ] Is the HPCC4J version specified?
- [ ] Is it a valid version number (e.g., 9.x.x, 10.x.x)?
- [ ] Can be found in `pom.xml` dependency declaration or Maven Central

**How to find:** Check your `pom.xml` dependency:
```xml
<dependency>
    <groupId>org.hpccsystems</groupId>
    <artifactId>wsclient</artifactId>
    <version>X.X.X</version>
</dependency>
```
Or run: `mvn dependency:tree | grep hpccsystems`

#### HPCC Systems Platform Version
- [ ] Is the HPCC Systems cluster version specified?
- [ ] Is it a valid HPCC version (e.g., 7.x.x, 8.x.x, 9.x.x)?
- [ ] Valid major versions: 7, 8, 9, 10 (as of 2025)

**How to find:** 
- ESP web interface: Check footer or About page
- Command line: `eclcc --version` on HPCC server
- API call: Query WsTopology service

**Compatibility Note:** HPCC4J versions should generally align with HPCC platform versions (e.g., HPCC4J 9.x works with HPCC 9.x)

#### Java Version
- [ ] Is the Java version specified?
- [ ] Is it Java 8 or higher (minimum requirement)?
- [ ] Is it a supported LTS version (8, 11, 17, 21)?

**How to find:** Run `java -version` or `mvn -version`

#### Additional Version Info (for spark-hpcc module)
- [ ] Apache Spark version (if using spark-hpcc)
- [ ] Scala version (if applicable)

### 6. Connection Information

#### Cluster Details
- [ ] HPCC cluster endpoint/URL (e.g., http://mycluster:8010)
- [ ] Target cluster name (e.g., "mythor", "myroxie")
- [ ] Port information (default: 8010 for HTTP, 18010 for HTTPS)
- [ ] Protocol (HTTP vs HTTPS)

**How to find:**
- ESP URL used in browser
- Connection code: `new Connection("http://...")`

#### Authentication
- [ ] Authentication method mentioned (username/password, none, etc.)
- [ ] Any authentication errors or permission issues noted

**Privacy Note:** Never share actual passwords; just indicate if authentication is being used

### 7. Error Information

#### Error Messages
- [ ] Full error message text included
- [ ] Stack traces provided (if applicable)
- [ ] Log output included

**How to capture:**
- Java exceptions: Full stack trace from console or logs
- Enable debug logging: Add `-Dlog4j.configuration=log4j.properties` with DEBUG level
- Check `log4j.properties` or `log4j2.xml` configuration

#### Error Context
- [ ] When does the error occur (during connection, read, write, etc.)?
- [ ] Is the error consistent or intermittent?
- [ ] Any recent changes that might have triggered the issue?

### 8. Environment Information

- [ ] Operating system (Linux, Windows, macOS)
- [ ] Build tool (Maven, Gradle) and version
- [ ] Container/deployment environment (Docker, Kubernetes, etc.)
- [ ] Network configuration (proxy, firewall, VPN)

### 9. Code Examples

- [ ] Sample code showing the issue
- [ ] Connection initialization code
- [ ] Relevant configuration files (pom.xml snippet, application properties)

**Best practice:** Provide minimal reproducible code:
```java
Connection conn = new Connection("http://mycluster:8010");
conn.setCredentials("username", "password");
HPCCWsClient client = HPCCWsClient.get(conn);
// ... problematic operation
```

### 10. Additional Context

- [ ] Related files or datasets mentioned
- [ ] Workarounds attempted
- [ ] Links to related issues or documentation
- [ ] Timeline (when did this start happening?)

## Analysis Output Format

Provide your analysis in the following structure:

### Assessment: [SUFFICIENT / NEEDS_MORE_INFO]

### Summary
[Brief 2-3 sentence summary of the issue and its completeness]

### Identified Information
- **Issue Type:** [bug/feature/question/documentation]
- **Affected Module:** [module name or "unclear"]
- **HPCC4J Version:** [version or "NOT PROVIDED"]
- **HPCC Platform Version:** [version or "NOT PROVIDED"]
- **Java Version:** [version or "NOT PROVIDED"]
- **Has Reproduction Steps:** [YES/NO/PARTIAL]
- **Has Error Details:** [YES/NO/PARTIAL]

### Missing Information
[Bulleted list of critical missing information]

### Validation Issues
[List any version numbers or configurations that appear invalid]

### Recommended Questions for Reporter

[Provide 3-5 specific questions to gather missing information, with instructions on how to obtain it]

Example:
1. **What version of HPCC4J are you using?**
   - Check your pom.xml dependency or run: `mvn dependency:tree | grep hpccsystems`

2. **What is your HPCC Systems platform version?**
   - Found in ESP interface footer or run: `eclcc --version`

3. **Can you provide the full error stack trace?**
   - Copy the complete error from your console or log files

### Suggested Labels
[Suggest GitHub labels: bug, enhancement, question, needs-more-info, wsclient, dfsclient, spark-hpcc, etc.]

### Priority Assessment
[Low/Medium/High based on severity and clarity of issue]

---

## Important Notes

1. **Be constructive and helpful** - The goal is to help users provide the information needed to resolve their issue
2. **Validate version numbers** - Check if provided versions are realistic and compatible
3. **Consider security** - Remind users not to share passwords or sensitive connection details
4. **Module context matters** - Different modules have different common issues (connection problems for wsclient, schema issues for spark-hpcc, etc.)
5. **Look for implicit information** - Sometimes version info or error details can be inferred from code snippets or error messages
