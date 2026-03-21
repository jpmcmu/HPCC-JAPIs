# 260320-vtl — Plan: Workflow Overhaul (Sequential Envs + Aggregate Reports)

## Goal
Redesign the test-generator workflow from 3 parallel isolated jobs to a single sequential job
that provisions each HPCC environment in turn, runs the test-fix loop per env, then aggregates
all results into a unified cross-env report. Also adds Step 5 (aggregate) to TestGeneratorAgent.py.

---

## Wave 1 — Python agent changes (no dependencies on workflow)

### 1.1 Add `--start-from-step 5` support
**File**: `scripts/wsclient-test-generator/TestGeneratorAgent.py`

Changes:
- Add `5` to `--start-from-step` choices list (line ~116)
- Update help text to describe Step 5
- Update docstring to mention Step 5

### 1.2 Add Step 5 aggregate block
**File**: `scripts/wsclient-test-generator/TestGeneratorAgent.py`

After the existing Step 4 block and before `print("\n✅ Process complete!")`, add:

```python
# === Step 5: Aggregate cross-environment reports ===
if START_FROM_STEP <= 5:
    print("📊 Step 5: Aggregating cross-environment reports...")

    # Locate all per-env FinalReport files produced this run (or existing in OUTPUT_DIR)
    final_report_files = sorted(glob.glob(
        os.path.join(OUTPUT_DIR, f"*FinalReport_*{DATESTAMP}*.md")
    ))
    results_json_files = sorted(glob.glob(
        os.path.join(OUTPUT_DIR, f"*TestResults_Iteration*{DATESTAMP}*.json")
    ))
    server_issue_files = sorted(glob.glob(
        os.path.join(OUTPUT_DIR, f"*UnverifiedServerIssuesReport_*{DATESTAMP}*.md")
    ))

    if not final_report_files:
        print("⚠️  No per-env FinalReport files found — skipping aggregate report.")
    else:
        aggregate_report_path = os.path.join(
            OUTPUT_DIR, f"{SERVICE_NAME}.AllEnvAggregateReport_{DATESTAMP}.md"
        )
        files_list_md = "\n".join(f"- {f}" for f in final_report_files + results_json_files)
        copilot_generate(
            AGGREGATE_REPORT_PROMPT_FILE,
            aggregate_report_path,
            {
                "SERVICE_NAME": SERVICE_NAME,
                "DATESTAMP": DATESTAMP,
                "OUTPUT_DIR": OUTPUT_DIR,
                "PER_ENV_FINAL_REPORTS": "\n".join(f"- {f}" for f in final_report_files),
                "PER_ENV_RESULTS_JSONS": "\n".join(f"- {f}" for f in results_json_files),
                "PER_ENV_SERVER_ISSUE_FILES": "\n".join(f"- {f}" for f in server_issue_files),
            },
        )
        print(f"✅ Aggregate report created: {aggregate_report_path}")
else:
    print("⏭️  Skipping Step 5: Not requested")
```

Also add the constant near the other prompt file constants (~line 241):
```python
AGGREGATE_REPORT_PROMPT_FILE = os.path.join(os.path.dirname(__file__), "AggregateReportPrompt.md")
```

---

## Wave 2 — New prompt file (independent of workflow)

### 2.1 Create AggregateReportPrompt.md
**File**: `scripts/wsclient-test-generator/AggregateReportPrompt.md`

Prompt instructs Copilot to:
1. Read all per-env FinalReport `.md` files listed in `${PER_ENV_FINAL_REPORTS}`
2. Read all per-env TestResults `.json` files listed in `${PER_ENV_RESULTS_JSONS}`
3. Read any UnverifiedServerIssues files in `${PER_ENV_SERVER_ISSUE_FILES}`
4. Produce a unified markdown report covering:
   - Cross-env test coverage matrix (which tests run in which envs)
   - Per-env pass/fail summary table
   - All server issues deduplicated (same issue across envs counted once)
   - All client issues fixed (merged, deduped)
   - Recommendations sorted by priority
   - Full individual test inventory with per-env status

---

## Wave 3 — Workflow redesign (depends on nothing)

### 3.1 Replace 3 parallel jobs + publish-results → single sequential job
**File**: `.github/workflows/test-generator.yml`

**Remove**: jobs `test-containerized`, `test-baremetal`, `test-secure`, `publish-results`
**Remove**: jobs `build` (inline it into the single job)

**Add**: single job `run-tests` with steps in this order:

```
# One-time setup
1. Check actor authorization
2. Checkout (fetch-depth: 0)
3. Setup JDK 11
4. Setup Python 3.8
5. Setup GitHub Copilot CLI (./.github/actions/setup-copilot-cli)
6. Cache Maven packages
7. Compile project (mvn clean compile test-compile -DskipTests -pl wsclient)

# ENV 1: Containerized (K8s)
8.  Setup microk8s (balchua/microk8s-actions)
9.  Deploy HPCC Platform via Helm (wait for pods)
10. Create environments.json (containerized config)
11. Run step 4 — containerized env
12. Collect K8s logs (if: always())
13. Teardown: helm uninstall hpcc && kubectl delete ns default (if: always())

# ENV 2: Baremetal insecure
14. Extract HPCC Platform version (inline Python)
15. Install HPCC deb
16. Add /etc/hosts entries
17. Start HPCC (hpcc-init start)
18. Wait for HPCC ready (curl retry loop)
19. Create environments.json (baremetal config)
20. Run step 4 — baremetal env

# ENV 3: Secure (reuse baremetal install)
21. Generate htpasswd credentials (openssl)
22. Configure htpasswdsecmgr (inline Python on environment.xml)
23. Apply environment.xml and restart HPCC
24. Create environments.json (secure config, uses $HPCC_USER/$HPCC_PASS from env)
25. Run step 4 — secure env
26. Stop HPCC

# Aggregate
27. Run step 5 — aggregate reports

# Publish
28. Commit and push results branch
29. Print results branch URL
```

### 3.2 Workflow input additions / removals
No changes to existing `workflow_dispatch` inputs — all are still valid.

### 3.3 `continue-on-error` handling
Each `run-tests` step (11, 20, 25) gets `continue-on-error: true` so later envs still run if earlier fails.
K8s teardown (step 13) must run `if: always()` to prevent resource leak.

### 3.4 Git push — simplified
No artifact upload/download needed. The results files are already in the workspace at the end
of the job. Directly commit from the workspace.

---

## Implementation Order
1. `TestGeneratorAgent.py` — Step 5 + constant (Wave 1)
2. `AggregateReportPrompt.md` — new file (Wave 2)
3. `test-generator.yml` — full rewrite (Wave 3)
4. Commit everything

---

## Verification Checklist
- [ ] `python3 TestGeneratorAgent.py --help` shows `--start-from-step` choices include 5
- [ ] `AGGREGATE_REPORT_PROMPT_FILE` constant exists in the script
- [ ] Step 5 block is reachable and globs correctly from OUTPUT_DIR
- [ ] Workflow has single job `run-tests` (no `test-containerized`, `test-baremetal`, etc.)
- [ ] Copilot CLI setup step appears exactly once in the workflow
- [ ] K8s teardown step is `if: always()`
- [ ] Result commit step is at the end of `run-tests`, not a separate job
- [ ] `timeout-minutes: 360` on the job
