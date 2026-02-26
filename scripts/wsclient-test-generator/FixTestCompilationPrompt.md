## 🔍 File Search Strategy

> ⚠️ **Important**: File and directory names may differ in capitalisation or have minor naming differences from what is expected. **Always use fuzzy/case-insensitive searches** — never assume an exact path is correct.
>
> ⚠️ **Always search from the project root directories provided in the context above** (HPCC4J Project Root and HPCC Platform Source Root). Do NOT use `.` as a search root — the script may be run from a different directory and `.` will not be the project root.

**When locating the test file or any referenced source file:**

1. **Use `find -iname` from the HPCC4J project root** (case-insensitive), not from `.`:
   ```bash
   find <HPCC4J_PROJECT_ROOT>/wsclient/src/test -iname "*ClientTest.java" -type f
   find <HPCC4J_PROJECT_ROOT>/wsclient/src -iname "*Test.java"
   ```

2. **Try multiple name variants** if the first search returns nothing:
   - With and without the `Ws`/`WS` prefix
   - All-lowercase, PascalCase, camelCase variants
   - Partial name globs (e.g., `*store*test*`, `*Store*Test*`)

3. **Use `grep -ril` from the project root** when a name-based search fails:
   ```bash
   grep -ril "class.*ClientTest" <HPCC4J_PROJECT_ROOT>/wsclient/src --include="*.java"
   ```

4. **Never give up after one failed search** — try at least 3 different variants/strategies before treating a file as missing.

---

## 🔧 Editing Strategy — CRITICAL

> ⚠️ **The test file was generated from multiple methods and contains many identical boilerplate patterns** (duplicate try-catch blocks, repeated assertions, similar setup code). String-based replacement tools (Edit, replace) **will frequently fail with "Multiple matches found"** because the same code appears many times.

### Preferred editing approach — in priority order:

1. **Whole-file rewrite (STRONGLY PREFERRED)**
   - Read the entire file with `cat`
   - Fix ALL errors at once in memory
   - Write the complete corrected file back using the `write` tool
   - This completely sidesteps the "Multiple matches found" problem
   ```bash
   cat <path-to-test-file>
   # Identify ALL errors, then write the entire corrected file back
   ```

2. **`sed` with explicit line numbers** (when only a few targeted lines need changing)
   - First identify the line numbers: `grep -n "pattern" <file>`
   - Then edit by line number, not by pattern matching:
   ```bash
   # Replace content of line N
   sed -i 'Ns/.*/    new content here/' <file>
   # Or use awk for safer multi-line replacements
   awk 'NR==42 { print "    new content" } NR!=42 { print }' <file> > /tmp/patched.java && mv /tmp/patched.java <file>
   ```

3. **String replacement** — use ONLY as a last resort and ONLY when the pattern is guaranteed unique
   - Before attempting: verify uniqueness with `grep -c "pattern" <file>`
   - If count > 1: do NOT use string replace — fall back to approach 1 or 2 instead

### Workflow

1. Run `mvn clean compile test-compile -B -DskipTests -pl wsclient` from the HPCC4J project root to get the full error list
2. Read the full test file with `cat <test-file-path>`
3. Fix ALL identified errors at once in memory
4. Rewrite the complete corrected file using the `write` tool
5. Run Maven again to verify — repeat if further errors remain

Fix compilation errors ONLY in the test files. Do not modify production source files.
