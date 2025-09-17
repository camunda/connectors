#!/bin/bash

# Script to refresh merge queue status for stuck PRs
# Usage: ./refresh-merge-queue.sh <PR_NUMBER>

set -e

PR_NUMBER=$1

if [ -z "$PR_NUMBER" ]; then
    echo "Usage: $0 <PR_NUMBER>"
    echo "Example: $0 5420"
    exit 1
fi

echo "🔄 Refreshing merge queue status for PR #$PR_NUMBER"

# Check if gh CLI is installed
if ! command -v gh &> /dev/null; then
    echo "❌ GitHub CLI (gh) is required but not installed."
    echo "Please install it from: https://cli.github.com/"
    exit 1
fi

# Check if user is authenticated
if ! gh auth status &> /dev/null; then
    echo "❌ Not authenticated with GitHub CLI. Please run 'gh auth login'"
    exit 1
fi

# Trigger the workflow
echo "📋 Triggering REFRESH_MERGE_QUEUE workflow..."
gh workflow run REFRESH_MERGE_QUEUE.yml -f pr_number="$PR_NUMBER"

echo "✅ Workflow triggered successfully!"
echo "📊 You can monitor the workflow at:"
echo "   https://github.com/camunda/connectors/actions/workflows/REFRESH_MERGE_QUEUE.yml"
echo ""
echo "🎯 This should help unblock PR #$PR_NUMBER from the merge queue."
echo "💡 If the issue persists, you may need to:"
echo "   1. Remove the PR from the merge queue manually"
echo "   2. Re-add it to the merge queue"
echo "   3. Contact GitHub support if it's a persistent issue"