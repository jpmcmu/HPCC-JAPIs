# Batch Test Failure Analysis - Iteration ${ITERATION}

Please analyze all test failures in ${FAILURE_REPORT_FILE} and determine the appropriate actions.

## 🔍 File Search Strategy

> ⚠️ **Important**: File and directory names may differ in capitalisation or have minor naming differences from what is expected. **Always use fuzzy/case-insensitive searches** — never assume an exact path is correct.
>
> ⚠️ **Always search from the project root directories provided in the context above** (HPCC4J Project Root and HPCC Platform Source Root). Do NOT use `.` as a search root — the script may be run from a different directory and `.` will not be the project root.

**When locating the test file or any other source file:**

1. **Use `find -iname` from the HPCC4J project root** (case-insensitive), not from `.`:
   ```bash
   find <HPCC4J_PROJECT_ROOT>/wsclient/src/test -iname "*ClientTest.java" -type f
   # If the exact path in ${TEST_FILE_PATH} doesn't exist, search broadly:
   find <HPCC4J_PROJECT_ROOT>/wsclient/src -iname "*Test*.java" -path "*/test/*"
   ```

2. **Try multiple name variants** if the first search returns nothing:
   - With and without the `Ws`/`WS` prefix (e.g., `WsStore` → `Store`, `store`)
   - All-lowercase, PascalCase, camelCase variants
   - Partial name globs (e.g., `*store*test*`, `*Store*Test*`)

3. **Use `grep -ril` from the project root** when a name-based search fails:
   ```bash
   grep -ril "class.*ClientTest" <HPCC4J_PROJECT_ROOT>/wsclient/src --include="*.java"
   ```

4. **Never give up after one failed search** — try at least 3 different variants/strategies before treating a file as missing.

---

## Your Task

Review each failed test and categorize them into:

1. **INVALID_TEST**: Test case has incorrect logic, wrong assertions, or tests impossible scenarios
   - These should be FIXED
   
2. **CLIENT_ISSUE**: Valid test that exposes a bug in the Java client wrapper code
   - These should be marked with @Category(UnverifiedClientIssues.class)
   - These should be fixed in the client code if possible, otherwise just categorized
   
3. **SERVER_ISSUE**: Valid test that exposes a bug in the HPCC Platform ESP service
   - These should be marked with @Category(UnverifiedServerIssues.class)
   
4. **DATA_ISSUE**: Valid test but required dataset doesn't exist or has wrong data
   - These should be marked with @Category annotation or skipped with @Ignore
   
5. **INVESTIGATE**: Unclear classification that needs manual review

## Required Actions

For each test, you must:

1. **If INVALID_TEST**: Fix the test code in ${TEST_FILE_PATH}
   - Correct assertions
   - Fix test logic
   - Update test setup
   - Make the test valid

2. **If CLIENT_ISSUE**: Add categorization annotation to ${TEST_FILE_PATH}
   - Add import: `import org.hpccsystems.commons.annotations.UnverifiedClientIssues;`
   - Add annotation: `@Category(UnverifiedClientIssues.class)` before @Test
   - Correct client-side issues if possible
   - Keep test unchanged otherwise

3. **If SERVER_ISSUE**: Add categorization annotation to ${TEST_FILE_PATH}
   - Add import: `import org.hpccsystems.commons.annotations.UnverifiedServerIssues;`
   - Add annotation: `@Category(UnverifiedServerIssues.class)` before @Test
   - Keep test unchanged otherwise

4. **If DATA_ISSUE**: 
   - If dataset is expected to exist but missing: mark as CLIENT_ISSUE or SERVER_ISSUE as appropriate
   - If dataset creation is pending (notes indicate SKIP): leave as-is for now
   
5. **If INVESTIGATE**: Add comment to test explaining why manual review is needed

## Analysis Output

After analyzing all failures, create a summary file: ${SERVICE_NAME}.${METHOD_NAME}BatchAnalysis_Iteration${ITERATION}_${DATESTAMP}.md

Structure:
```markdown
# Batch Analysis Summary - Iteration ${ITERATION}

## Tests to Fix (INVALID_TEST)
- testName1: reason why invalid, what to fix
- testName2: reason why invalid, what to fix

## Tests to Categorize as Client Issues
- testName3: brief explanation of client bug
- testName4: brief explanation of client bug

## Tests to Categorize as Server Issues  
- testName5: brief explanation of server bug
- testName6: brief explanation of server bug

## Tests Requiring Investigation
- testName7: why unclear

## Actions Taken
- Fixed X invalid tests
- Categorized Y as client issues
- Categorized Z as server issues

---
*Generated: ${DATESTAMP}*
```

**IMPORTANT**: Make ALL necessary changes to ${TEST_FILE_PATH} NOW. Don't just create the analysis file - actually modify the test code.
**IMPORTANT**: Make ALL necessary changes to resolve client-side issues in the Java client code, all @Category(UnverifiedClientIssues.class) tests should pass.

After making changes:
1. Save the analysis summary
2. Ensure all test fixes and categorizations are applied
3. The next iteration will re-run the failed tests to verify fixes

Test file location: ${TEST_FILE_PATH}
Failure report: ${FAILURE_REPORT_FILE}
Test results: ${RESULTS_FILE}

--- Code Architecture ---

${CODE_ARCHITECTURE_PROMPT}
