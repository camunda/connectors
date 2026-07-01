# Slack Mentions Setup Guide

This guide explains how to configure GitHub secrets for proper Slack user/group mentions in workflow notifications.

## Background

Slack's API requires specific formatting for mentions to be clickable and trigger notifications:
- **User mentions**: `<@USERID>` (e.g., `<@U024BE7LH>`)
- **User group mentions**: `<!subteam^USERGROUP_ID>` (e.g., `<!subteam^S0123456789>`)

Plain text mentions like `@username` or `@groupname` will **not** work—they appear as plain text and don't notify users.

## Required Secrets

Configure these GitHub repository secrets with the appropriate Slack mention format:

### 1. CONNECTORS_RELEASE_MANAGER_SLACK_GROUP_ID
Used in `RELEASE.yaml` to notify the release manager when the release workflow fails.

**Value format:**
- If `connectors-release-manager` is a **user group**: `<!subteam^S0123456789>`
- If it's an **individual user**: `<@U024BE7LH>`

### 2. CONNECTORS_MEDIC_SLACK_GROUP_ID
Used in `DEPLOY_SNAPSHOTS.yaml` to notify the connector medic when the deploy snapshots workflow fails.

**Value format:**
- If `connector-medic` is a **user group**: `<!subteam^S0123456789>`
- If it's an **individual user**: `<@U024BE7LH>`

### 3. CONNECTORS_QA_MANAGER_SLACK_GROUP_ID
Used in `QA_REQUIRED.yml` to notify the QA manager when a PR is ready to be tested.

**Value format:**
- If this is a **user group**: `<!subteam^S0123456789>`
- If it's an **individual user**: `<@U024BE7LH>`

### 4. CONNECTORS_QA_SLACK_CHANNEL_ID
Used in `QA_REQUIRED.yml` to specify the Slack channel where QA notifications should be posted (e.g., `#connectors-qa`).

**Value format:**
- Channel ID (e.g., `C01234567` - you can find this by right-clicking on the channel name → "View channel details")

## CODEOWNERS-based E2E failure routing

The nightly E2E `notify` job (`E2E_BRANCH_RUN.yml`) pings the **team that owns the failing
module** in addition to the medic. Ownership is resolved from `CODEOWNERS` (exactly like PR
review assignment, last-match-wins) by the reusable action
[`.github/actions/codeowners-slack-mentions`](../actions/codeowners-slack-mentions/README.md),
which maps each owner team to a Slack mention via:

### Team → secret mapping

`E2E_BRANCH_RUN.yml` assembles the team → Slack-group map **inline from GitHub secrets** (so no
group IDs live in the repo) and passes it to the action. Each owning team maps to a secret:

| CODEOWNERS team | Secret |
|---|---|
| `@camunda/connectors-core` | `CONNECTORS_MEDIC_SLACK_GROUP_ID` |
| `@camunda/connectors-experience` | `CONNECTORS_MEDIC_SLACK_GROUP_ID` |
| `@camunda/connectors-agentic-ai` | `AGENTIC_ORCHESTRATION_MEDIC_SLACK_GROUP_ID` |
| `@camunda/connectors-idp` | `IDP_MEDIC_SLACK_GROUP_ID` |

- **All of these secrets hold a full mention** — `<!subteam^S0123456789>` for a user group (or
  `<@U024BE7LH>` for an individual) — the same format as `CONNECTORS_MEDIC_SLACK_GROUP_ID`. The
  action inserts them verbatim, so a bare `S…` ID would **not** render as a ping.
- A team whose secret is **unset/empty**, a team **absent** from the map, and the default owner
  `@camunda/connectors` all fall back to the always-pinged medic
  (`CONNECTORS_MEDIC_SLACK_GROUP_ID`) — routing is never silent and never emits a broken mention.
- To route a specific connector's e2e failures to its team, add a one-line `CODEOWNERS` entry for
  that `connectors-e2e-test/<module>` path (the `connectors-e2e-test-agentic-ai` entry is the
  existing precedent), and ensure that team has a secret in the map above — no action change needed.

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
