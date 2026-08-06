# Camunda App Integrations Connector

Outbound connector for sending notifications and managing channels via the Camunda App
Integrations backend (Microsoft Teams).

It exposes two operations:

- **Send Message** — post a text message, an [Adaptive Card](https://adaptivecards.io/), or a
  Camunda form to a Camunda-side recipient or a Teams channel.
- **Create Channel** — create a Microsoft Teams channel in a given team.

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
| `APP_INTEGRATIONS_OAUTH_CLIENT_AUTHENTICATION` | no | `credentials-body` (default) or `basic-auth-header`. |

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

**Recipient** is a switch:

- **Camunda** — any combination of assignee email, candidate users and candidate groups (at least
  one is required). The candidate fields are FEEL lists, e.g. `= ["alice", "bob"]`.
- **Microsoft Teams** — a channel ID, e.g. `19:xxx@thread.tacv2`.

**Message type** is a switch with three mutually exclusive options:

- **Text message** — plain text.
- **Adaptive card** — a custom adaptive-card JSON payload.
- **Form** — a Camunda form the backend renders as an adaptive card. The form is a
  `zeebe:linkedResource`, gated on this message type, and reaches the connector in the job's
  `linkedResources` custom header rather than as a request variable. A `linkedResources` header is
  ignored for the other two message types.

### Create Channel

`teamId` accepts either a raw `groupId` or a full Teams URL — the `groupId` query parameter is
extracted automatically. `membershipType` is one of `standard`, `private`, or `shared` (defaults to
`standard`).

## API

The connector flattens the switchable input onto the backend's flat wire contract. A Send Message
call posts to `/api/connector/message` with unset fields omitted:

```json
{
  "email": "user@example.com",
  "candidateUsers": ["alice", "bob"],
  "candidateGroups": ["approvers"],
  "channelId": "19:abc123@thread.tacv2",
  "message": "Deployment finished successfully.",
  "adaptiveCardJson": "{\"type\":\"AdaptiveCard\"}",
  "formResourceKey": "12345"
}
```

A Create Channel call posts to `/api/connector/channel`:

```json
{
  "teamId": "00000000-0000-0000-0000-000000000000",
  "displayName": "Release announcements",
  "description": "Automated release notifications",
  "membershipType": "standard"
}
```

### Output

Both operations return the backend's JSON response (e.g. the created channel for *Create Channel*),
or `null` for an acknowledged call with no body. Any response with status `>= 400` is surfaced as a
`ConnectorException` whose error code is the HTTP status.

## Element Template

The element template is generated from the connector annotations by the
`element-template-generator-maven-plugin` and committed to
[element-templates/app-integrations-connector.json](element-templates/app-integrations-connector.json).
The hybrid (Self-Managed) variant is in [element-templates/hybrid](element-templates/hybrid).
