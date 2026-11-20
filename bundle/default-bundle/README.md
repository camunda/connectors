# Running Connector Runtime Locally

## Running Options

### Option 1: Maven / IDE

1. Launch via IDE by running the `io.camunda.connector.runtime.app.LocalConnectorRuntime` class
2. Configure by adding `application.properties` to `src/test/resources`

### Option 2: Command Line

```bash
# java \
  -Dcamunda.client.cloud.region=bru-2 \
  -Dcamunda.client.cloud.cluster-id=xxx \
  -Dcamunda.client.auth.client-id=yyy \
  -Dcamunda.client.auth.client-secret=zzz \
  -jar target/connector-runtime-bundle-{VERSION}-with-dependencies.jar
```

## Update: Migration to `camunda-distributions` repository

### Why Migrate?

- The `camunda-platform` is now deprecated

### Migration Steps

1. Clone [the repository](https://github.com/camunda/camunda-distributions/tree/main)
2. Comment out the `connectors` section in the configuration
3. Update your endpoints:
    - **Inbound Connectors Webhook**: `http://localhost:8085/inbound/` (Note the port change)
    - **Operate Interface**: `http://localhost:8081/operate/login` (Now included in core, note the port change)

### Key Changes

- Operate is now integrated into the core distribution
- Updated port configurations for both webhook and Operate interface
- Improved compatibility with current connector implementations

## Securing the management API

The management API lists every active connector, including the raw properties of inbound connector
elements (webhook paths, authentication type, activation and correlation expressions). It is open
by default. To require a token on it, set:

| Property | Environment variable |
|---|---|
| `camunda.connector.auth.self-managed.secure-management-api` | `CAMUNDA_CONNECTOR_AUTH_SELF_MANAGED_SECURE_MANAGEMENT_API` |
| `camunda.connector.auth.self-managed.issuer` | `CAMUNDA_CONNECTOR_AUTH_SELF_MANAGED_ISSUER` |
| `camunda.connector.auth.self-managed.audience` | `CAMUNDA_CONNECTOR_AUTH_SELF_MANAGED_AUDIENCE` |

- `issuer` — OIDC issuer URL. Its discovery endpoint must be reachable from the runtime at startup,
  and its JWKS endpoint must be reachable when tokens are validated.
- `audience` — the `aud` claim carried by the tokens sent to this runtime.

Both must be set when `secure-management-api` is enabled, or the runtime fails to start. Requests
are then accepted only with an `Authorization: Bearer <token>` that verifies against that issuer's
keys, is unexpired, and carries the configured audience. There is no role or claim check beyond
that. The protected routes are:

- `/inbound` (exact path only)
- `/tenants/**`
- `/inbound-instances/**`

Webhook endpoints (`/inbound/{context}`) and `/actuator/**` stay unauthenticated. Anything that
calls the protected routes, such as monitoring scripts, must send a bearer token from the
configured issuer.
