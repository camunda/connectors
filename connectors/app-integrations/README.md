# Camunda App Integrations Connector

Outbound connector for sending notifications and managing channels via the Camunda App
Integrations backend (Microsoft Teams and Slack).

It exposes two operations:

- **Send Message** — post plain text to a Camunda-side recipient, a Microsoft Teams channel, or Slack,
  optionally alongside an [Adaptive Card](https://adaptivecards.io/), a
  [Block Kit](https://api.slack.com/block-kit) payload, or a Camunda form.
- **Create Channel** — create a channel in Microsoft Teams or Slack.

## Build

```bash
mvn clean package
```

## Configuration

The App Integrations backend is Camunda-operated infrastructure in both SaaS and Self-Managed, so
its URL and credentials come from the connector runtime's **environment**, not from the element
template. Nothing about the connection is part of the process model.

| Variable | Required | Notes |
| --- | --- | --- |
| `APP_INTEGRATIONS_BASE_URL` | yes | Backend base URL. |
| `APP_INTEGRATIONS_API_KEY` | for API key auth | Sent in the `X-API-KEY` header. |
| `APP_INTEGRATIONS_OAUTH_TOKEN_ENDPOINT` | for OAuth | OAuth 2.0 token endpoint. |
| `APP_INTEGRATIONS_OAUTH_CLIENT_ID` | for OAuth | |
| `APP_INTEGRATIONS_OAUTH_CLIENT_SECRET` | for OAuth | |
| `APP_INTEGRATIONS_OAUTH_AUDIENCE` | no | Target API identifier. |
| `APP_INTEGRATIONS_OAUTH_SCOPES` | no | |
| `APP_INTEGRATIONS_OAUTH_CLIENT_AUTHENTICATION` | no | `credentialsBody` (default) or `basicAuthHeader` — the literals `OAuthService` switches on. |

The mechanism is selected per runtime, not per element:

1. If the OAuth token endpoint, client ID and client secret are all set → **OAuth 2.0 client
   credentials**. The token is fetched, cached, and attached by the connector SDK's HTTP client; on
   a `401` the cached token is invalidated and the request is retried once with a fresh token.
2. Otherwise, if `APP_INTEGRATIONS_API_KEY` is set → **API key**.
3. Otherwise the connector is not configured — see below.

Blank values count as absent. When running in SaaS, the `X-Org-Id` and `X-Cluster-Id` headers are
added automatically from the runtime environment so the backend can attribute the call to the
originating cluster.

### When the connector is not configured

Without the variables above the connector is unusable on that runtime. Every job fails
**immediately and without retries**, raising an incident with error code
`APP_INTEGRATIONS_NOT_CONFIGURED`; the incident message names the missing variables (never their
values). No amount of retrying can supply a missing environment variable, so the job is not retried
at the element template's configured backoff.

Only processes using this connector are affected — the runtime itself still starts and serves every
other connector. To fix an incident, set the variables above and redeploy the connector runtime.

## Element template

### Send Message

**Message** is a plain-text field that is always available and optional — leave it empty to send only
the additional content below, or fill both to send text *and* a card in one message.

**Recipient source** is a switch:

- **Camunda** — any combination of assignee email, candidate users and candidate groups (at least one
  is required). The candidate fields are FEEL lists, e.g. `= ["alice", "bob"]`.
- **Microsoft Teams** — a channel ID, e.g. `19:xxx@thread.tacv2`.
- **Slack** — a second switch, **Slack target**: a channel ID (`C0123456789`) or a user (email address
  or member ID, e.g. `U0123456789`).

**Additional content** is a second switch whose options depend on the recipient, because the formats
each platform accepts differ:

| Recipient | Additional content options |
| --- | --- |
| Camunda | None · Form |
| Microsoft Teams | None · Adaptive card · Form |
| Slack | None · Block Kit · Form |

At most one may be chosen, which is what makes a card/Block Kit payload and a form mutually exclusive.
At least one of message or additional content must be provided — an empty message with "None" is
rejected before any HTTP call.

The **Adaptive card** and **Block Kit blocks** fields are FEEL-enabled and carry real JSON on the wire.
Outbound connectors never evaluate FEEL themselves — the engine evaluates the expression when it
creates the job, so the connector receives an already-parsed object. Pasted JSON works too (a JSON
object/array literal is valid FEEL); a value that still arrives as a string is parsed by the connector,
and anything malformed or of the wrong shape fails with `VALIDATION_ERROR` before the backend is called.

**Form** is a `zeebe:linkedResource` gated on the additional-content switch, so it reaches the connector
in the job's `linkedResources` custom header rather than as a request variable. A `linkedResources`
header is ignored for any other selection.

### Create Channel

**Platform** switches between Microsoft Teams and Slack. Only `Description` is shared:

- **Microsoft Teams** — `Channel name` (max 50 characters), `Team ID` (a raw `groupId` or a full Teams
  URL, whose `groupId` query parameter is extracted automatically), and `Channel type` (`standard`,
  `private`, `shared`; defaults to `standard`).
- **Slack** — `Channel name` (max 80 characters, lowercase letters/digits/hyphens/underscores only),
  an optional `Workspace ID` (falls back to the backend's configured workspace), and a `Private
  channel` flag.

`Channel name` is declared per platform because the rules differ — 50 characters for Microsoft, 80
plus a character restriction for Slack — and an element template cannot express a per-branch
`maxLength`. Both bind to the same `platform.displayName` variable, so only the template property IDs
differ.

## API

The connector flattens the switchable input onto the backend's flat wire contract and sends an explicit
`platform` discriminator — Teams and Slack both address a "channel", so the field shape alone is
ambiguous. Unset fields are omitted entirely.

`POST /api/connector/message`:

```json
{ "platform": "camunda", "email": "a@b.c", "candidateUsers": ["alice"],
  "candidateGroups": ["approvers"], "message": "Please review" }

{ "platform": "camunda", "email": "a@b.c", "message": "Please approve", "formResourceKey": "12345" }

{ "platform": "teams", "channelId": "19:abc@thread.tacv2", "message": "Deploy done",
  "adaptiveCard": { "type": "AdaptiveCard", "version": "1.5", "body": [] } }

{ "platform": "slack", "channelId": "C0123456789", "message": "Deploy done",
  "blocks": [ { "type": "section" } ] }

{ "platform": "slack", "userId": "U0123456789", "message": "Ping" }
```

`POST /api/connector/channel`:

```json
{ "platform": "teams", "displayName": "Releases", "teamId": "<groupId>",
  "membershipType": "standard" }

{ "platform": "slack", "displayName": "releases", "workspaceId": "T0123", "isPrivate": false }
```

### Output

Both operations return the backend's JSON response (e.g. the created channel for *Create Channel*), or
`null` for an acknowledged call with no body. Any response with status `>= 400` is surfaced as a
`ConnectorException` whose error code is the HTTP status.

## Regenerating the element template

The element template is generated from the connector annotations by the
`element-template-generator-maven-plugin` and committed to
[element-templates/app-integrations-connector.json](element-templates/app-integrations-connector.json).
The hybrid (Self-Managed) variant is in [element-templates/hybrid](element-templates/hybrid).
