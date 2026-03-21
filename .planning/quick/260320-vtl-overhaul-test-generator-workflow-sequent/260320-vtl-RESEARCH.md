# 260320-vtl — Research: Workflow Overhaul (Sequential Envs + Aggregate Reports)

## 1. TestGeneratorAgent.py Architecture

### Step Flow (lines 932–1557)
- **Step 0**: Full-service service discovery (`ServiceAnalysisPrompt.md`) → writes `{SVC}.MethodList_{DATE}.json`
- **Step 1**: Per-method analysis (`MethodAnalysisPrompt.md`) → writes `{SVC}.{Method}Analysis_{DATE}.md`
- **Step 2**: Per-method test generation (`TestGenerationPrompt.md`) → writes Java test class + `{SVC}.{Method}TestMetadata_{DATE}.json`
- **Step 3**: Maven compile loop (up to 5 iterations, `FixTestCompilationPrompt.md`)
- **Step 4**: Per-env test-run + fix loop:
  - If `--env-config` given, loads `HPCCEnvironment` objects from JSON
  - If `--env` given, filters to one named env; otherwise iterates all envs in config
  - Inner `while iteration < MAX_TEST_FIX_ITERATIONS` loop:
    - Calls `run_tests_parallel()` → captures pass/fail per test
    - Saves `{SVC}.{Method}TestResults_Iteration{N}_{DATE}.json`
    - On failures: builds failure report → calls `BatchFailureAnalysisPrompt.md` via copilot → back to top
  - After loop: generates `{SVC}.{Method}FinalReport_{DATE}.md` (via `FinalReportPrompt.md`)
  - If `UnverifiedServerIssues` tests exist: generates `{SVC}.{Method}UnverifiedServerIssuesReport_{DATE}.md`

### Step 4 Environment Loop (lines 1219–1333)
```python
active_environments: List[HPCCEnvironment] = []
# populated from --env-config / --env filter
for env in active_environments:
    # sets hpcc_conn, wssql_conn, hpcc_user, hpcc_pass
    # filters tests by env.name against test["environmentRequirements"]
    # runs iteration loop
# Final summary + generate FinalReport + generate ServerIssues report
```

The **FinalReport and ServerIssues are generated once, after all envs have run**, using the last `test_results` and `results_file` in scope. This means today's single invocation already loops multiple envs, but only produces one FinalReport reflecting the last env's results.

### Output File Naming
All outputs land in `OUTPUT_DIR = f"{SVC}_FullServiceTestGeneration_{DATE}"` or `f"{SVC}_{Method}TestGeneration_{DATE}"`.

Key output files per environment run:
- `{SVC}.AllMethodsTestResults_Iteration{N}_{DATE}.json` — JSON of pass/fail per test
- `{SVC}.AllMethodsFailureReport_Iteration{N}_{DATE}.md`
- `{SVC}.AllMethodsBatchAnalysis_Iteration{N}_{DATE}.md`
- `{SVC}.AllMethodsFinalReport_{DATE}.md` ← generated after envs loop
- `{SVC}.AllMethodsUnverifiedServerIssuesReport_{DATE}.md`

When `--env containerized` flag is used (current workflow), only tests with `environmentRequirements` containing `"containerized"` run.

### `run_tests_parallel()` signature (line 754)
```python
def run_tests_parallel(tests_to_execute, test_class, hpcc_conn, wssql_conn,
                       hpcc_user, hpcc_pass, disable_dataset_generation=False,
                       num_threads=1)
```
Returns a list of `{"metadata": {...}, "result": {"passed": bool, "test_name", "error_message", "output", "exit_code"}}`.

### `--start-from-step` choices (current): `[0, 1, 2, 3, 4]`
Adding `5` requires extending the `choices` list and adding the block after Step 4.

---

## 2. Current Workflow Structure (.github/workflows/test-generator.yml, 717 lines)

### Job Map
| Job | Depends on | Env type | Key steps |
|---|---|---|---|
| `build` | — | none | checkout → JDK11 → compile → upload artifact |
| `test-containerized` | build | K8s/microk8s | checkout → JDK → Python → copilot-cli → get artifact → microk8s → helm HPCC → test → upload artifact |
| `test-baremetal` | build | bare metal insecure | checkout → JDK → Python → copilot-cli → get artifact → dpkg HPCC → start → test → upload artifact |
| `test-secure` | build | bare metal secure | checkout → JDK → Python → copilot-cli → get artifact → dpkg HPCC → htpasswdsecmgr → start → test → upload artifact |
| `publish-results` | all 3 test jobs | — | download 3 artifacts → commit to branch |

### Parallel vs Sequential
All 3 test jobs run **in parallel** after `build`. The `publish-results` job waits for all 3.

### Shared Setup (repeated 3x)
- `actions/checkout@v4`
- `actions/setup-java@v1` (JDK 11)
- `actions/setup-python@v2` (3.8)
- `./.github/actions/setup-copilot-cli` (installs Copilot CLI, validates actor)
- `actions/cache@v3` (Maven)
- `actions/download-artifact@v4` (wsclient-test-classes)

### HPCC Version Detection (2x in baremetal/secure jobs)
Inline Python `--shell python` block that:
1. Reads git tags matching `hpcc4j_*-release`
2. Constructs URLs for latest+fallback version
3. Writes to `$GITHUB_OUTPUT`

This block is **identical** in test-baremetal and test-secure — factoring it out as a reusable step or composite action would be cleaner.

### Teardown Gap
Current jobs have **no teardown steps**. Each job runs on an isolated runner so cleanup isn't needed. In a sequential single-job design, teardown IS needed between envs.

### Port / Hostname Conflict Risk
Both containerized and baremetal use `eclwatch.default:8010`:
- Containerized: routed through microk8s DNS → K8s service → pod
- Baremetal: via `/etc/hosts` entry `127.0.0.1 eclwatch.default` → HPCC daemon

They will conflict if both are running simultaneously. Sequential execution requires:
1. `helm uninstall hpcc --namespace default` after containerized tests
2. Then install baremetal HPCC deb

### Baremetal → Secure Reuse Opportunity
Secure is just baremetal + htpasswdsecmgr config. In sequential flow, we can:
- Install HPCC once (step for baremetal install)
- Run insecure tests
- Reconfigure htpasswdsecmgr on the same install (no reinstall)
- Restart HPCC
- Run secure tests
- Stop HPCC
This saves ~5-7 min of HPCC download + install time.

---

## 3. Step 5: Aggregate Report Design

### What it needs to do
Combine results from all 3 envs into:
1. `{SVC}.AllMethodsAggregateReport_{DATE}.md` — unified cross-env test report
2. `{SVC}.AllMethodsAllEnvServerIssues_{DATE}.md` — all unverified server issues across all envs

### Inputs Step 5 needs
- All per-env `*FinalReport_*.md` files
- All per-env `*TestResults_Iteration*.json` files (last iteration per env)
- All per-env `*UnverifiedServerIssuesReport_*.md` files (if they exist)
- `SERVICE_NAME`, `DATESTAMP`, `OUTPUT_DIR`

### Implementation Approach
Add to Python after Step 4:
```python
if START_FROM_STEP <= 5:
    # Glob all FinalReport files from this run
    final_reports = glob(...*FinalReport*{DATESTAMP}*.md)
    # Glob all latest TestResults JSONs per env
    # Invoke copilot with AggregateReportPrompt.md
    # Invoke copilot with AllEnvServerIssuesPrompt.md if any server issues found
```

### `--start-from-step 5` entry point
When `--start-from-step 5` is passed:
- Skip Steps 0–4
- Need `OUTPUT_DIR` to point to existing run's output
- Currently `OUTPUT_DIR` is derived from args, which is fine if the same args are passed

---

## 4. Workflow Redesign Target

### New single-job sequential structure
```yaml
jobs:
  run-tests:
    name: Build → Test → Aggregate
    runs-on: ubuntu-latest
    if: github.repository != 'hpcc-systems/hpcc4j'
    environment: test-runner

    steps:
      # === One-time setup ===
      - checkout
      - check-actor
      - setup-jdk-11
      - setup-python-3.8
      - setup-copilot-cli      # ← only once
      - cache-maven
      - compile-project

      # === ENV 1: Containerized (K8s) ===
      - setup-microk8s
      - deploy-hpcc-helm
      - create-env-config (containerized)
      - run-tests --env containerized --start-from-step 4
      - collect-k8s-logs (always)
      - teardown-helm         # helm uninstall

      # === ENV 2: Baremetal (insecure) ===
      - extract-hpcc-version
      - install-hpcc-deb
      - add-hosts-entries
      - start-hpcc
      - create-env-config (baremetal)
      - run-tests --env baremetal --start-from-step 4

      # === ENV 3: Secure (reuse baremetal install) ===
      - generate-htpasswd-credentials
      - configure-htpasswdsecmgr
      - restart-hpcc
      - create-env-config (secure)
      - run-tests --env secure --start-from-step 4
      - stop-hpcc

      # === Aggregate reports (Step 5) ===
      - run-agent --start-from-step 5

      # === Commit results ===
      - commit-push-branch
```

### Key advantages of single-job design
1. Copilot CLI installed once (no 3x duplication)
2. HPCC version detection runs once (baremetal install shared)
3. Maven build runs once (in-process, no artifact upload/download needed)
4. Results naturally co-located in the workspace for step 5 aggregation
5. Publish step becomes simpler (no artifact download, just commit)

### Tradeoff: Job timeout
GitHub default job timeout is 6 hours. Sizing estimate:
- Containerized: K8s setup ~10min + K8s deploy ~8min + tests ~20-60min = ~40-80min
- Baremetal: HPCC install ~5min + tests ~20-60min = ~25-65min
- Secure: reconfig ~2min + tests ~20-60min = ~22-62min
- Aggregate: ~5min
- Total worst case: ~230min ≈ 3h50m → well within 6h limit

Include `timeout-minutes: 360` on the job for explicit safety.

---

## 5. Key Files to Change

| File | Change |
|---|---|
| `TestGeneratorAgent.py` | Add `5` to `--start-from-step` choices; add Step 5 aggregate block |
| `AggregateReportPrompt.md` | New prompt file for cross-env report |
| `.github/workflows/test-generator.yml` | Replace 3 parallel jobs + publish-results → 1 sequential job |

No changes needed to:
- Any existing prompt files (FinalReportPrompt.md, etc.)
- `environments.example.json` schema
- `HPCCEnvironment` dataclass
- HPCC version detection logic (reuse)
