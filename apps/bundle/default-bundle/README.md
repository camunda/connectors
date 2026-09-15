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

## Securing `POST /configurations/validate`

The credential-validation endpoint resolves stored secrets in order to run a validator, so it is
closed by default: with nothing configured it answers **404** to every request, which is the signal
Camunda Hub reads as "this runtime does not support credential validation" — it hides the feature
rather than reporting an error.

To enable it, point the runtime at the identity provider whose tokens Hub forwards. Both properties
are required together; setting the issuer alone **fails startup**, because an issuer on its own
would accept every token that IdP signs for any of its clients.

| Property | Environment variable |
|---|---|
| `camunda.connector.auth.self-managed.issuer` | `CAMUNDA_CONNECTOR_AUTH_SELF_MANAGED_ISSUER` |
| `camunda.connector.auth.self-managed.audience` | `CAMUNDA_CONNECTOR_AUTH_SELF_MANAGED_AUDIENCE` |

- `issuer` — OIDC issuer URL. Must be reachable from the runtime at startup (it performs discovery
  to fetch the IdP's signing keys) and must be the same IdP that authenticates your Hub users.
- `audience` — the `aud` claim carried by the tokens Hub forwards to this runtime.

Requests are then accepted only with an `Authorization: Bearer <token>` that verifies against that
issuer's keys, is unexpired, and carries the configured audience. There is no role or claim check
beyond that, so keep the endpoint off untrusted networks.

Only a `BEARER_TOKEN`-auth cluster registration can supply that token, so `NONE`- and `BASIC`-auth
clusters cannot use this feature.
