# Camunda App Integrations Connector

Outbound connector for sending notifications and managing channels via the Camunda App
Integrations backend (Microsoft Teams).

It exposes two operations:

- **Send Message** — post a message, an [Adaptive Card](https://adaptivecards.io/), or a linked
  form to a user (by email) or a channel.
- **Create Channel** — create a Microsoft Teams channel in a given team.

## Build

```bash
mvn clean package
```

## Authentication

The connector supports two mechanisms, selected per element-template instance:

- **API key** (`apikey`) — for Self-Managed. The key is sent in the `X-API-KEY` header.
- **OAuth 2.0 client credentials** (`oauth-client-credentials-flow`) — for SaaS. The token is
  fetched, cached, and attached by the connector SDK's HTTP client; on a `401` the cached token is
  invalidated and the request is retried once with a fresh token.

When running in SaaS, the `X-Org-Id` and `X-Cluster-Id` headers are added automatically from the
runtime environment so the backend can attribute the call to the originating cluster.

## API

### Send Message

```json
{
  "configuration": {
    "baseUrl": "{{secrets.APP_INTEGRATIONS_BASE_URL}}",
    "authentication": {
      "type": "apikey",
      "apiKey": "{{secrets.APP_INTEGRATIONS_API_KEY}}"
    }
  },
  "channelId": "19:abc123@thread.tacv2",
  "message": "Deployment finished successfully."
}
```

Exactly one of `message`, `adaptiveCardJson`, or a linked form must be provided. A form is supplied
via the `linkedResources` custom header (resource type `form`) on the task, not in the request body.

### Create Channel

```json
{
  "configuration": {
    "baseUrl": "{{secrets.APP_INTEGRATIONS_BASE_URL}}",
    "authentication": {
      "type": "oauth-client-credentials-flow",
      "oauthTokenEndpoint": "https://login.example.com/oauth/token",
      "clientId": "{{secrets.APP_INTEGRATIONS_CLIENT_ID}}",
      "clientSecret": "{{secrets.APP_INTEGRATIONS_CLIENT_SECRET}}",
      "audience": "app-integrations.example.com",
      "clientAuthentication": "credentials-body"
    }
  },
  "teamId": "https://teams.microsoft.com/l/team/...?groupId=00000000-0000-0000-0000-000000000000",
  "displayName": "Release announcements",
  "description": "Automated release notifications",
  "membershipType": "standard"
}
```

`teamId` accepts either a raw `groupId` or a full Teams URL — the `groupId` query parameter is
extracted automatically. `membershipType` is one of `standard`, `private`, or `shared` (defaults to
`standard`).

### Output

Both operations return the backend's JSON response (e.g. the created channel for *Create Channel*),
or `null` for an acknowledged call with no body. Any response with status `>= 400` is surfaced as a
`ConnectorException` whose error code is the HTTP status.

## Element Template

The element template is generated from the connector annotations by the
`element-template-generator-maven-plugin` and committed to
[element-templates/app-integrations-connector.json](element-templates/app-integrations-connector.json).
The hybrid (Self-Managed) variant is in [element-templates/hybrid](element-templates/hybrid).
