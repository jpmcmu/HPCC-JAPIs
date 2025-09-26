# Auto Upmerge Workflows

This repository includes automated upmerge functionality that builds on the existing upmerge testing infrastructure.

## Overview

The auto upmerge system consists of three GitHub Actions workflows:

1. **upmerge-test.yml** - Tests if a PR can be safely upmerged (existing)
2. **upmerge-auto.yml** - Automatically performs upmerges when tests pass (new)
3. **upmerge-auto-comment.yml** - Posts results of the auto upmerge process (new)

## How It Works

### Workflow Sequence

1. A PR is created against a candidate branch (e.g., `candidate-9.8.x`)
2. The **upmerge-test** workflow runs automatically and tests if the PR can be upmerged to all subsequent branches (9.10.x, 9.12.x, 9.14.x, 10.x, master)
3. If the upmerge test **succeeds**, the **auto-upmerge** workflow triggers automatically
4. The auto-upmerge workflow:
   - Downloads the test results to determine which branches are safe to upmerge to
   - For each successful branch, creates a new upmerge branch
   - Merges the original changes and updates the version appropriately
   - Creates a new PR for manual review
5. The **upmerge-auto-comment** workflow posts a summary comment on the original PR

### Branch Resolution Logic

The system follows the same branch resolution logic as the existing upmerge test:

- Starting from a candidate branch like `candidate-9.8.x`
- Upmerges to: `candidate-9.10.x`, `candidate-9.12.x`, `candidate-9.14.x`, `candidate-10.x`, `master`
- Each target branch gets its appropriate version from its `pom.xml`

### Example Workflow

1. PR #123 created against `candidate-9.8.x`
2. Upmerge test runs and finds that it can safely merge to `candidate-9.10.x` and `master`, but conflicts with `candidate-9.12.x`
3. Auto upmerge workflow creates two new PRs:
   - "Auto upmerge from candidate-9.8.x to candidate-9.10.x (Original PR #123)"
   - "Auto upmerge from candidate-9.8.x to master (Original PR #123)"
4. A comment is posted on PR #123 summarizing the results

## Configuration

### Repository Variables

- `AUTO_UPMERGE_DRY_RUN` - Set to "true" to enable dry run mode (default: false)

### Required Permissions

The workflows require the following permissions:
- `contents: write` - To create branches and commit changes
- `pull-requests: write` - To create PRs and post comments
- `actions: read` - To download artifacts from the upmerge test

## Safety Features

### Dry Run Mode

Set the repository variable `AUTO_UPMERGE_DRY_RUN` to "true" to enable dry run mode. In this mode:
- All git operations are performed (checkout, merge, version updates)
- No branches are pushed to remote
- No PRs are created
- Results show what would have been done

### Error Handling

- Each upmerge operation is independent - if one fails, others continue
- Failed upmerges are reported in the summary comment
- Detailed error logs are available in the workflow run
- Original test results are preserved for troubleshooting

### Manual Review Required

- Auto upmerge creates PRs but does NOT auto-merge them
- Each upmerged PR requires manual review and approval
- This provides a safety net for any unexpected issues

## Files Created

### upmerge-auto.yml

The main auto upmerge workflow that:
- Triggers when upmerge-test completes successfully
- Downloads test results to determine target branches
- Performs git operations (merge, version update, branch creation)
- Creates PRs using the GitHub API
- Saves detailed results as artifacts

### upmerge-auto-comment.yml

A supporting workflow that:
- Triggers when auto-upmerge completes (success or failure)
- Downloads auto upmerge results
- Posts a formatted comment on the original PR
- Provides troubleshooting information for failures

## Troubleshooting

### Common Issues

1. **Permission Errors**
   - Ensure the workflows have the required permissions
   - Check that the GITHUB_TOKEN has sufficient scope

2. **Maven Version Update Failures**
   - Ensure Maven is available in the runner
   - Check that pom.xml files are valid

3. **Git Operation Failures**
   - May indicate merge conflicts that weren't caught in testing
   - Check git logs in the workflow output

4. **PR Creation Failures**
   - May indicate API rate limits or permission issues
   - Check the GitHub API response in workflow logs

### Getting Help

1. Check the workflow run logs for detailed error messages
2. Review the artifacts uploaded by each workflow
3. Enable dry run mode to test changes safely
4. Review the original upmerge test results to understand which branches should have succeeded

## Testing

To test the auto upmerge functionality:

1. Create a test PR against a candidate branch
2. Ensure it passes the upmerge test
3. Monitor the auto upmerge workflow execution
4. Review created PRs and comments
5. Use dry run mode for safe testing

## Maintenance

The workflows inherit the project configuration from the upmerge test, including:
- Tag patterns (`hpcc4j_*-release`)
- Branch naming conventions (`candidate-X.Y.x`)
- Version resolution logic

Updates to the upmerge test logic may require corresponding updates to the auto upmerge workflows.