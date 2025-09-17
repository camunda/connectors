# Immediate Fix for PR 5420

## Quick Solution

To fix the merge queue issue for PR 5420 right now:

### Option 1: Workflow (after this PR is merged)
1. Go to: https://github.com/camunda/connectors/actions/workflows/REFRESH_MERGE_QUEUE.yml
2. Click "Run workflow"
3. Enter `5420` in the PR number field
4. Click "Run workflow"

### Option 2: Manual (available now)
1. Go to: https://github.com/camunda/connectors/pull/5420
2. Click the dropdown arrow next to "Merge when ready"
3. Select "Remove from queue"
4. Wait a few seconds
5. Click "Merge when ready" again
6. Select "Add to merge queue"

## Why This Happens

GitHub's merge queue occasionally gets stuck when:
- All status checks pass ✅
- But the queue doesn't proceed with the merge
- No error messages are shown
- The PR sits in the queue for hours

This is a known GitHub issue that affects many repositories with merge queues enabled.

## Status of PR 5420

- **Created**: 2025-09-17 12:01 UTC
- **Tests passed**: 2025-09-17 15:06 UTC  
- **Stuck since**: ~2 hours ago
- **All checks**: ✅ Passing
- **Issue**: Merge queue not proceeding

The PR is ready to merge and just needs the queue to be refreshed.