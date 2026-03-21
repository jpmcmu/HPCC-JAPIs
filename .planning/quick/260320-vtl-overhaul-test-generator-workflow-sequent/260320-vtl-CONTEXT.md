# Quick Task 260320-vtl: Overhaul test-generator workflow - Context

**Gathered:** 2026-03-21
**Status:** Ready for planning

<domain>
## Task Boundary

Overhaul the `.github/workflows/test-generator.yml` and `TestGeneratorAgent.py` so
that the workflow:
1. Generates test cases (Steps 0–3) once
2. Provisions environments **sequentially** inside a single job, running the Step 4
   test/fix loop only for tests whose `environmentRequirements` matches the current env
3. After all environments complete, runs a new aggregation step that produces a
   single unified FinalReport and ServerIssues report across all envs

</domain>

<decisions>
## Implementation Decisions

### Env flow
- **Single job, sequential steps** — one workflow job installs each environment in
  sequence (containerized → baremetal → secure) and tears it down before moving to
  the next. No multiple jobs; no parallel runners.

### Test routing
- **Use `environmentRequirements` field from metadata JSON** — the existing
  `environmentRequirements` field in test metadata already encodes which envs each
  test targets. The script filters at runtime using `--env <name>`. No new workflow
  inputs or manual mapping needed.

### Reports
- **Unified report across all envs** — after all three environments have run, a
  single cross-environment FinalReport and a single ServerIssues report are produced,
  covering all tests and all environments together.

### Python changes
- **New Step 5: `--aggregate-reports`** — `TestGeneratorAgent.py` gets a new
  `--start-from-step 5` / `--aggregate-reports` mode that:
  - Reads all per-env `*TestResults_Iteration*.json` files from the output directory
  - Reads all per-env `*FailureReport*.md` / `*BatchAnalysis*.md` files
  - Generates one cross-env `FinalReport_<DATESTAMP>.md`
  - Generates one `UnverifiedServerIssues_<DATESTAMP>.md` (deduplicating issues that
    appear in multiple envs)

### Claude's Discretion
- Exact job/step structure inside the single workflow job
- Whether the HPCC platform is fully uninstalled between envs or just stopped/reconfigured
- Whether the existing `publish-results` job stays as-is or is simplified

</decisions>

<specifics>
## Specific Ideas

- The `copilot` binary only needs to be installed once per job (already the case in
  a single-job design — no duplication issue).
- The three HPCC install/start/teardown blocks should be refactored as reusable
  shell functions or step groups within the single job.
- Aggregation prompt should leverage the existing `FinalReportPrompt.md` template
  but with a new preamble instructing Copilot to read results across all three
  environment subdirectories.

</specifics>

<canonical_refs>
## Canonical References

- `.github/workflows/test-generator.yml` — current workflow to overhaul
- `scripts/wsclient-test-generator/TestGeneratorAgent.py` — Python agent (Steps 0–4)
- `scripts/wsclient-test-generator/FinalReportPrompt.md` — report prompt template
- `scripts/wsclient-test-generator/UnverifiedServerIssuesReportPrompt.md` — issues prompt
- `scripts/wsclient-test-generator/environments.example.json` — env config schema

</canonical_refs>
