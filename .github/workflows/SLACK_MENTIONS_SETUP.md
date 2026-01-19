# Slack Mentions Setup Guide

This guide explains how to configure GitHub secrets for proper Slack user/group mentions in workflow notifications.

## Background

Slack's API requires specific formatting for mentions to be clickable and trigger notifications:
- **User mentions**: `<@USERID>` (e.g., `<@U024BE7LH>`)
- **User group mentions**: `<!subteam^USERGROUP_ID>` (e.g., `<!subteam^S0123456789>`)

Plain text mentions like `@username` or `@groupname` will **not** work—they appear as plain text and don't notify users.

## Required Secrets

Configure these GitHub repository secrets with the appropriate Slack mention format:

### 1. CONNECTORS_RELEASE_MANAGER_SLACK_MENTION
Used in `RELEASE.yaml` to notify the release manager when the release workflow fails.

**Value format:**
- If `connectors-release-manager` is a **user group**: `<!subteam^S0123456789>`
- If it's an **individual user**: `<@U024BE7LH>`

### 2. CONNECTOR_MEDIC_SLACK_MENTION
Used in `DEPLOY_SNAPSHOTS.yaml` to notify the connector medic when the deploy snapshots workflow fails.

**Value format:**
- If `connector-medic` is a **user group**: `<!subteam^S0123456789>`
- If it's an **individual user**: `<@U024BE7LH>`

## How to Find Slack IDs

### For Individual Users:
1. In Slack, click on the user's profile
2. Click "More" → "Copy member ID"
3. Format: `<@USERID>` (e.g., `<@U024BE7LH>`)

### For User Groups:
1. Use the Slack API method `usergroups.list` with your bot token:
   ```bash
   curl -H "Authorization: Bearer YOUR_BOT_TOKEN" \
        https://slack.com/api/usergroups.list
   ```
2. Find your user group in the response and note the `id` field (starts with `S`)
3. Format: `<!subteam^USERGROUP_ID>` (e.g., `<!subteam^S0123456789>`)

## Testing

After setting up the secrets, you can test by:
1. Triggering a workflow that includes Slack notifications
2. Verifying the mention appears as a clickable link in Slack
3. Confirming the mentioned user/group receives a notification

## References
- [Slack API: Formatting message text](https://api.slack.com/reference/surfaces/formatting)
- [Slack API: Mentioning users](https://api.slack.com/reference/surfaces/formatting#mentioning-users)
- [Slack API: chat.postMessage](https://api.slack.com/methods/chat.postMessage)
