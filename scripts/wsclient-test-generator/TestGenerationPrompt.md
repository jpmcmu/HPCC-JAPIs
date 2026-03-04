## 🔍 File Search Strategy

> ⚠️ **Important**: File and directory names may differ in capitalisation or have minor naming differences from what is expected. **Always use fuzzy/case-insensitive searches** — never assume an exact path is correct.
>
> ⚠️ **Always search from the project root directories provided in the context above** (HPCC4J Project Root and HPCC Platform Source Root). Do NOT use `.` as a search root — the script may be run from a different directory and `.` will not be the project root.

**When locating any file (analysis file, test class, source files):**

1. **Use `find -iname` from the project root** (case-insensitive), not from `.`:
   ```bash
   # Test class — search from HPCC4J project root
   find <HPCC4J_PROJECT_ROOT>/wsclient/src/test -iname "*${SERVICE_NAME}ClientTest*" -type f
   find <HPCC4J_PROJECT_ROOT>/wsclient/src/test -iname "*Test*.java" -type f
   ```

2. **Try multiple name variants** if the first search returns nothing:
   - With and without the `Ws`/`WS` prefix (e.g., `WsStore` → `Store`, `store`)
   - All-lowercase, PascalCase, camelCase variants
   - Partial name globs (e.g., `*store*test*`, `*Store*Test*`)

3. **Use `grep -ril` from the project root** when a name-based search fails:
   ```bash
   grep -ril "class ${SERVICE_NAME}ClientTest" <HPCC4J_PROJECT_ROOT>/wsclient/src
   ```

4. **Never give up after one failed search** — try at least 3 different variants/strategies before treating a file as missing.

---

${TESTING_SCENARIOS_SECTION}
## 🗄️ Dataset Generation — REQUIRED BEFORE WRITING TESTS

> ⚠️ **CRITICAL**: Test datasets are created by submitting `wsclient/src/test/resources/generate-datasets.ecl` to the HPCC cluster via `BaseRemoteTest.initialize()` before each test run. Any dataset a test references **must** be defined in this file or it will not exist at test time.

**Perform these steps IN ORDER before writing any test code:**

### Step D1 — Read the existing dataset file

```bash
cat <HPCC4J_PROJECT_ROOT>/wsclient/src/test/resources/generate-datasets.ecl
```

Note every dataset name already defined (the `dataset_name := '~...'` assignments). These are available for use immediately — do NOT recreate them.

### Step D2 — Determine which datasets each test case needs

For each test case in the analysis file:
1. Check if an existing dataset (from Step D1) is sufficient
2. If not, design the minimal new dataset that satisfies the test requirement

### Step D3 — Append new datasets to `generate-datasets.ecl`

For every new dataset identified in Step D2, **append an ECL block to the file** using the same idempotent pattern already used in the file:

```ecl
// --- New dataset added for ${SERVICE_NAME} ${METHOD_NAME} tests ---
dataset_name_X := '~test::purpose::type';
rec_X := { /* field definitions */ };
ds_X := DATASET(N, TRANSFORM(rec_X, SELF.field := ...; ), DISTRIBUTED);
IF(~Std.File.FileExists(dataset_name_X), OUTPUT(ds_X,,dataset_name_X,overwrite));
```

Rules:
- **Always use the `IF(~Std.File.FileExists(...), OUTPUT(...,overwrite))` guard** — this makes the script idempotent (safe to re-run)
- Follow the naming convention `~test::<purpose>::<type>` for new datasets
- Add a comment above each new block identifying which tests need it
- Do NOT modify or remove any existing blocks in the file
- Append at the end of the file

### Step D4 — Record dataset names in test code

Use the exact dataset names (including `~` prefix and `::` separators) from both the existing and newly added blocks as the file path arguments in your Java test calls.

---

Read ${ANALYSIS_FILE} and implement the recommended test cases 
for ${SERVICE_NAME}.${METHOD_NAME}. Create ${EXPECTED_RESULTS_FILE} 
with the expected results for each test.

If a service analysis file is available at ${SERVICE_ANALYSIS_FILE}, read it for 
inter-method dependency context, test independence guidelines, and functional groupings.
Use this context to ensure tests are self-contained and do not depend on execution order.

All tests must go into the single test class `${SERVICE_NAME}ClientTest.java`.
Use unique test data identifiers per test method to avoid conflicts between tests.

Additionally, create a JSON file named ${TEST_METADATA_FILE} with the following structure:
{
  "service": "${SERVICE_NAME}",
  "method": "${METHOD_NAME}",
  "testClass": "${SERVICE_NAME}ClientTest",
  "tests": [
    {
      "testName": "testMethodName",
      "method": "${METHOD_NAME}",
      "description": "Brief description of what this test validates",
      "category": "basic|edge-case|error-handling|integration",
      "expectedOutcome": "PASS|SKIP",
      "requiresData": true|false,
      "notes": "Any special considerations or requirements"
    }
  ]
}

List ALL test methods you create in this JSON file. This metadata will be used to:
1. Run each test individually using: mvn -B --activate-profiles jenkins-on-demand,remote-test 
   -Dhpccconn=http://eclwatch.default:8010 -Dwssqlconn=http://sql2ecl.default:8510 
   -Dtest=${SERVICE_NAME}ClientTest#<testName> test
2. Track test results and categorize failures
3. Generate comprehensive test reports

Ensure the testName values exactly match the method names in the test class.
