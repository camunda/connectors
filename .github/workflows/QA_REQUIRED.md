# QA Required Workflow

## Overview
This workflow automates the QA review process when a pull request is labeled with `qa:required`.

## Workflow File
`.github/workflows/QA_REQUIRED.yml`

## Trigger
- **Event**: Pull request labeled or synchronized
- **Label**: `qa:required`

## Steps Performed

1. **Add `deploy:preview` label**
   - Automatically adds the `deploy:preview` label to trigger preview environment deployment

2. **Update PR status to "In QA"**
   - Uses GitHub GraphQL API to update the PR status in the project board
   - Searches for the PR in organization projects (first 10 projects, up to 100 items per project)
   - Updates the "Status" field to "In QA" if found
   - Continues gracefully if PR is not in a project

3. **Add QA assignee**
   - Adds `Szik` as an assignee to the PR
   - Preserves existing assignees

4. **Send Slack notification**
   - Posts a notification to the `#connectors-qa` Slack channel
   - Mentions the QA manager
   - Includes a link to the PR

## Required Secrets

### Vault Secrets (stored in HashiCorp Vault)
- `GITHUB_APP_ID` - GitHub App ID for authentication
- `GITHUB_APP_PRIVATE_KEY` - GitHub App private key
- `CONNECTORS_QA_MANAGER_SLACK_GROUP_ID` - Slack mention string for QA manager (format: `<@USERID>` or `<!subteam^GROUPID>`)
- `CONNECTORS_QA_SLACK_CHANNEL_ID` - Slack channel ID for QA notifications (e.g., `C01234567`)

### GitHub Secrets
- `CONNECTORS_SLACK_BOT_TOKEN` - Slack bot token for posting messages
- `VAULT_ADDR` - Vault server address
- `VAULT_ROLE_ID` - Vault role ID
- `VAULT_SECRET_ID` - Vault secret ID

## Dependencies

### GitHub Actions
- `hashicorp/vault-action@v3.4.0` - For retrieving secrets from Vault
- `actions/create-github-app-token@v2` - For generating GitHub App tokens
- `slackapi/slack-github-action@v2.1.1` - For sending Slack messages

### Tools
- GitHub CLI (`gh`)
- `jq` for JSON processing

## Error Handling
- Project-related steps use `continue-on-error: true` to ensure the workflow completes even if the PR is not in a project
- Checks if assignee already exists before adding
- Provides warning messages for non-critical failures

## Usage
Simply add the `qa:required` label to any pull request to trigger the workflow.

## Notes
- The workflow only runs on open pull requests
- If the PR is synchronized (new commits pushed), the workflow runs again if the `qa:required` label is present
- The workflow searches the first 10 organization projects with up to 100 items per project
