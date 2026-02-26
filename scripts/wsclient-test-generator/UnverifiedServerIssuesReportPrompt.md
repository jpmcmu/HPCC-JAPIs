Create a separate report for **Unverified Server Issues** found while testing ${SERVICE_NAME}.${METHOD_NAME}.

This report must be generated **only** when there are tests categorized with `@Category(UnverifiedServerIssues.class)`.

## 🔍 File Search Strategy

> ⚠️ **Important**: File and directory names may differ in capitalisation or have minor naming differences from what is expected. **Always use fuzzy/case-insensitive searches** — never assume an exact path is correct.
>
> ⚠️ **Always search from the project root directories provided in the context above** (HPCC4J Project Root and HPCC Platform Source Root). Do NOT use `.` as a search root — the script may be run from a different directory and `.` will not be the project root.

**When locating the test file, failure reports, or HPCC Platform source files:**

1. **Use `find -iname` from the project root** (case-insensitive), not from `.`:
   ```bash
   # Failure reports and artifacts — search from HPCC4J project root
   find <HPCC4J_PROJECT_ROOT> -iname "*FailureReport*" -type f

   # HPCC Platform source — search from HPCC Platform source root
   find ${HPCC_SOURCE_DIR}/esp -iname "*.ecm" | xargs grep -il "${SERVICE_NAME}"
   find ${HPCC_SOURCE_DIR}/esp/services -maxdepth 1 -iname "*${SERVICE_NAME}*" -type d
   ```

2. **Try multiple name variants** if the first search returns nothing:
   - With and without the `Ws`/`WS` prefix (e.g., `WsStore` → `Store`, `store`, `ws_store`)
   - All-lowercase, PascalCase, camelCase variants
   - Partial name globs (e.g., `*store*`, `*Store*`)

3. **Use `grep -ril` from the project root** when a name-based search fails:
   ```bash
   grep -ril "${SERVICE_NAME}" ${HPCC_SOURCE_DIR}/esp --include="*.cpp" --include="*.ecm"
   ```

4. **Never give up after one failed search** — try at least 3 different variants/strategies before treating a file as missing.

---

## Inputs

- Service: `${SERVICE_NAME}`
- Method: `${METHOD_NAME}`
- Test file: `${TEST_FILE_PATH}`
- Unverified server-issue tests:
${UNVERIFIED_SERVER_TESTS}

- Review supporting artifacts in `${OUTPUT_DIR}`:
  - `*FailureReport_Iteration*_${DATESTAMP}.md`
  - `*BatchAnalysis_Iteration*_${DATESTAMP}.md`
  - `*TestResults_Iteration*_${DATESTAMP}.json`
  - `${ANALYSIS_FILE}`

## What to Produce

Write a markdown report that documents:

1. **Summary**
   - How many UnverifiedServerIssues tests were identified
   - Which iterations surfaced them (if determinable)

2. **Issue Details (per test)**
   For each test in the list:
   - Test name
   - Observed failure/error (quote the relevant snippet)
   - Why it indicates a server-side issue (not a client/test defect)
   - Likely server component / endpoint involved
   - Links to likely HPCC Platform source areas to inspect (paths under `${HPCC_SOURCE_DIR}`)

3. **Potential Fixes**
   - Concrete hypotheses for root cause
   - Specific code areas/functions to inspect
   - Suggested fix approaches (validation, response shaping, error handling, edge-case logic)

4. **Reproduction Notes**
   - Any datasets or prerequisites implied by the tests/metadata
   - How to reproduce with Maven commands already used by the agent

5. **Next Steps**
   - What to do in HPCC Platform
   - What to do in HPCC4J (if anything)

**IMPORTANT**: Include a datestamp at the end of the report:
```markdown
---
*Generated: ${DATESTAMP}*
```

Save this report to: ${UNVERIFIED_SERVER_REPORT_PATH}
