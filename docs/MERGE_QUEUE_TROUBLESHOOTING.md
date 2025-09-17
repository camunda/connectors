# Merge Queue Troubleshooting

## Problem: PR Stuck in Merge Queue

When a PR is stuck in the GitHub merge queue despite all checks passing, this is usually due to a GitHub merge queue bug where the queue gets into a stuck state.

### Symptoms
- All required status checks have passed (✅)
- PR has been in the merge queue for an extended period (hours)
- No new activity or errors in the merge queue
- Tests completed successfully but merge hasn't proceeded

### Solution

#### Option 1: Use the Automated Workflow (Recommended)

1. Navigate to [Actions → REFRESH_MERGE_QUEUE](https://github.com/camunda/connectors/actions/workflows/REFRESH_MERGE_QUEUE.yml)
2. Click "Run workflow"
3. Enter the PR number (e.g., `5420`)
4. Click "Run workflow"

#### Option 2: Use the CLI Script

```bash
# From the repository root
./scripts/refresh-merge-queue.sh 5420
```

#### Option 3: Manual Steps

1. Go to the stuck PR
2. Remove it from the merge queue:
   - Click the dropdown next to "Merge when ready"
   - Select "Remove from queue"
3. Re-add it to the merge queue:
   - Click "Merge when ready" again
   - Select "Add to merge queue"

### How the Solution Works

The automated workflow creates a fresh status check on the PR's head commit, which often triggers GitHub's merge queue to reconsider the PR and proceed with the merge. This is a harmless operation that doesn't affect the actual code or tests.

### Prevention

This is a known GitHub issue and cannot be completely prevented, but it occurs more frequently when:
- Multiple PRs are in the queue simultaneously
- There are complex branch protection rules
- The repository has high activity

### Additional Resources

- [GitHub Docs: Managing a merge queue](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/configuring-pull-request-merges/managing-a-merge-queue)
- [GitHub Community: Merge queue troubleshooting](https://github.community/t/merge-queue-issues/)