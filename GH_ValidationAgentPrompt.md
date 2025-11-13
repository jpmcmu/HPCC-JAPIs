# AI Agent for Issue Validation

Create a Python script that uses `copilot -p` CLI to validate incoming GitHub issues, bug reports, and questions by checking for completeness, duplicates, and existing documentation coverage.

## Architecture

1. Install copilot via npm
2. Import `COPILOT_PAT` secret token to environment for authentication
3. Execute validation workflow

## Validation Workflow

1. **Summarize Issue**: Generate and save a markdown summary of the incoming issue containing all relevant information

2. **Validate Completeness**: Check that required fields are properly populated per `ProperIssueRequirements.md`
   - Output: JSON validation result
   - If validation fails: Post comment requesting missing information and apply `needs-more-information` label

3. **Check Documentation**: Download repository wiki as markdown, then analyze if the issue is:
   - Already documented
   - A user error (with debugging steps)
   - A known issue (with workarounds)
   - Output: JSON analysis document

4. **Compile Issue History**: Fetch all open and closed issues via GitHub API
   - Output: Single JSON file with issue titles and descriptions

5. **Check for Duplicates**: Compare incoming issue against historical issues to identify:
   - Potential duplicates
   - Potential regressions
   - Whether upgrade to newer version would resolve the issue
