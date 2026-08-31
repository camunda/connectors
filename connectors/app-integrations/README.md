# Camunda App Integrations Connector

Outbound connector for sending notifications and managing channels via the Camunda App
Integrations backend (Microsoft Teams and Slack).

It exposes two operations:

- **Send Message** — post plain text to a Camunda-side recipient, a Microsoft Teams channel, user or
  conversation, or Slack, optionally alongside an [Adaptive Card](https://adaptivecards.io/), a
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
| `APP_INTEGRATIONS_CLUSTER_ID` | for OAuth | The orchestration cluster's **UUID**, as configured in the App Integrations backend (`clusters[].uuid`). Not a cluster name. Sent in the `X-Cluster-Id` header. |

The mechanism is selected per runtime, not per element:

1. If the OAuth token endpoint, client ID and client secret are all set → **OAuth 2.0 client
   credentials**. The token is fetched, cached, and attached by the connector SDK's HTTP client; on
   a `401` the cached token is invalidated and the request is retried once with a fresh token.
   `APP_INTEGRATIONS_CLUSTER_ID` is required too, see below.
2. Otherwise, if `APP_INTEGRATIONS_API_KEY` is set → **API key**.
3. Otherwise the connector is not configured — see below.

Blank values count as absent.

### Cluster and tenant identification

The backend has to know which cluster a call comes from. It reads the `X-Cluster-Id` header first
and only falls back to the API key, which is per-cluster or per-tenant. Under OAuth there is no such
fallback, so a runtime that sends no cluster id is rejected.

The connector therefore sends `X-Cluster-Id` on every runtime, taking it from
`APP_INTEGRATIONS_CLUSTER_ID` and falling back to `CAMUNDA_CLIENT_CLOUD_CLUSTERID`, which SaaS
injects on its own. Set `APP_INTEGRATIONS_CLUSTER_ID` in Self-Managed. It is required under OAuth,
where a missing value raises `APP_INTEGRATIONS_NOT_CONFIGURED` before any HTTP call, and optional
under API key auth, where the backend recovers the cluster from the key.

`X-Org-Id` is sent only in SaaS, from the runtime environment. Self-Managed has no organization to
configure and the backend substitutes its own.

`X-Physical-Tenant-Id` carries the job's physical tenant, meaning the orchestration cluster (engine)
the job was activated from, so a runtime serving several engines routes to the right Teams or Slack
installation. The connector reads it off the job rather than from the environment, and omits the
header when the job carries none, leaving the backend to apply its own `default`. The value must
match a `clusters[].physicalTenants[].id` in the backend configuration, or the literal `default`;
anything else is rejected. This is the runtime's existing `camunda.clients.<name>.physical-tenant-id`
configuration and needs nothing App-Integrations-specific.

Do not confuse the physical tenant with the logical (multi-tenancy) tenant. The logical tenant is
not part of this contract and is never sent.

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
- **Microsoft Teams** — a second switch, **Teams target**: a channel ID (`19:xxx@thread.tacv2`), a
  user (the recipient's Microsoft Entra object ID; they must have connected the app), or a
  conversation returned by a previous send, which posts the message as a reply in it.
- **Slack** — a second switch, **Slack target**: a channel ID (`C0123456789`) or a user ID
  (`U0123456789`), plus an optional **Thread** — the message ID of a previous send, to reply in its
  thread instead of posting a new message. A Slack thread is an anchor within either target rather
  than an address of its own, which is why it sits beside the switch rather than inside it.

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

- **Microsoft Teams** — `Channel name` (max 50 characters), `Team ID` (a raw group ID or a full Teams
  URL, whose `groupId` query parameter is extracted automatically), and `Channel type`. Only
  `standard` is available for now: private and shared channels are not yet stable on Microsoft's
  side, so they are switched off and will follow in a later version. A request carrying `private` or
  `shared` is rejected before the backend is called.
- **Slack** — `Channel name` (max 80 characters, lowercase letters/digits/hyphens/underscores only),
  an optional `Workspace ID` (falls back to the backend's configured workspace), and a `Private
  channel` flag.

`Channel name` is declared per platform because the rules differ — 50 characters for Microsoft, 80
plus a character restriction for Slack — and an element template cannot express a per-branch
`maxLength`. Both bind to the same `platform.displayName` variable, so only the template property IDs
differ.

### Chat message templates

Four further templates let a process take part in a Teams or Slack conversation. They are not
connectors: the backend publishes the messages itself, so these are plain BPMN message events with
nothing to configure on the runtime.

| Template | Element | What it does |
| --- | --- | --- |
| App Integrations Chat Conversation Start Event | Message start event | Starts a process when someone writes to the Camunda app in Teams or Slack. |
| App Integrations Chat Message Intermediate Event | Intermediate catch event | Waits for the next message in the conversation. |
| App Integrations Chat Message Receive Task | Receive task | The same wait, as a task. |
| App Integrations Chat Message Boundary Event | Boundary event | The same wait, attached to an activity. |

The three waiting templates listen on the conversation named by their **Conversation** field, which
defaults to the conversation the process is already holding. A process started from the chat start
event therefore loops through a conversation with no configuration at all; to reply into a
conversation a *Send Message* opened instead, point the field at that delivery's `conversationKey`.

The incoming message lands in the `chatMessage` variable.

## API

The connector flattens the switchable input onto the backend's flat wire contract and sends an explicit
`platform` discriminator — Teams and Slack both address a "channel", so the field shape alone is
ambiguous. Unset fields are omitted entirely.

Every message also carries `processDefinitionId`, the BPMN process ID of the process the job came
from, so the backend can match notification rules scoped to a specific process.

`POST /api/connector/message`:

```json
{ "platform": "camunda", "processDefinitionId": "order-process", "email": "a@b.c",
  "candidateUsers": ["alice"], "candidateGroups": ["approvers"], "message": "Please review" }

{ "platform": "camunda", "processDefinitionId": "order-process", "email": "a@b.c",
  "message": "Please approve", "formResourceKey": "12345" }

{ "platform": "teams", "processDefinitionId": "order-process", "channelId": "19:abc@thread.tacv2",
  "message": "Deploy done",
  "adaptiveCard": { "type": "AdaptiveCard", "version": "1.5", "body": [] } }

{ "platform": "teams", "processDefinitionId": "order-process",
  "userId": "6b1e0f9a-1f3d-4a2b-9d0e-4c1b2a3d4e5f", "message": "Ping" }

{ "platform": "teams", "processDefinitionId": "order-process",
  "conversationId": "19:abc@thread.tacv2;messageid=17123456789", "message": "Following up" }

{ "platform": "slack", "processDefinitionId": "order-process", "channelId": "C0123456789",
  "message": "Deploy done", "blocks": [ { "type": "section" } ] }

{ "platform": "slack", "processDefinitionId": "order-process", "userId": "U0123456789",
  "threadTs": "1712345678.000100", "message": "Ping" }
```

The backend requires **exactly one** Teams target (`channelId`, `userId` or `conversationId`).

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

*Send Message* reports **every** destination the message resolved to, and every one it did not:

```json
{ "deliveries": [ { "platform": "teams", "conversation": "19:abc@thread.tacv2;messageid=17123456789",
                    "messageId": "17123456789",
                    "conversationKey": "teams:19:abc@thread.tacv2;messageid=17123456789" } ],
  "failures": [ { "platform": "slack", "conversation": "C0123", "reason": "not_in_channel" } ] }
```

A single delivery is a one-element list, read as `deliveries[1].conversation` (FEEL is 1-indexed).
`failures` is non-empty on a partial success, so a process that must not proceed on an incomplete
fan-out can check it. To reply to a delivery, pass `conversation` back as the Slack channel target
with `messageId` as **Thread**, or as the Teams conversation target.

`conversationKey` identifies the chat conversation the message landed in. It is an opaque value to be
compared, never taken apart, and it is what the chat message templates below correlate on. A backend
that predates it sends no such field.

## Regenerating the element template

The element template is generated from the connector annotations by the
`element-template-generator-maven-plugin` and committed to
[element-templates/app-integrations-connector.json](element-templates/app-integrations-connector.json).
The hybrid (Self-Managed) variant is in [element-templates/hybrid](element-templates/hybrid).

The four chat message templates are hand-written and have no generator and no hybrid variant — they
configure BPMN message events, not a connector runtime.
