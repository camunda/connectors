# Camunda 8 Connectors Spring Boot Starter

This module provides a Spring Boot starter for the Camunda 8 Connector runtime.
The runtime code itself is provided by the [Connector Runtime Spring](../connector-runtime-spring)
module.

## Usage

Refer to the [top-level documentation](../README.md) for the Connector runtime.

## Configuration

The Connector runtime used with this starter can be configured via the following properties:

| Property                                             | Description                                                                                                           | Default |
|------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------|---------|
| `camunda.connector.polling.enabled`                  | Whether Operate polling is enabled. This is required for inbound Connectors.                                          | `true`  |
| `camunda.connector.polling.interval`                 | The interval in which Operate polls for new process deployments.                                                      | `5000`  |
| `camunda.connector.webhook.enabled`                  | Whether webhook connector support is enabled.                                                                         | `true`  |
| `camunda.connector.configuration-validation.enabled` | Whether credential validation (`POST /configurations/validate`) is served. Read the section below before enabling it. | `false` |

### Credential validation must be authenticated

`POST /configurations/validate` resolves stored secrets in order to run a credential validator, and the validator then
presents the resolved credential to the endpoint named by the resolved configuration — so the credential leaves the
runtime on an outbound request. The response never carries a resolved value, but the route is only as trustworthy as the
callers that can reach it.

The route is off by default. Enabling it requires deciding who may reach it, in one of two ways.

#### Option 1 — let the runtime authenticate the route

Set Spring Boot's standard resource-server issuer alongside the property:

```properties
camunda.connector.configuration-validation.enabled=true
spring.security.oauth2.resourceserver.jwt.issuer-uri=https://<cluster-issuer>/auth/realms/camunda-platform
```

The runtime then requires a valid token from that issuer on `/configurations/**` and leaves every other route
(`/inbound/**` webhooks, `/inbound-instances/**`, `/outbound/**`, `/actuator/**`) anonymous as before. The cluster must
also be registered with bearer-token authentication in the calling application, so that it attaches the caller's token.

**If the property is enabled and no issuer is configured, the route is refused rather than served anonymously**, and the
runtime logs a warning naming the missing property at startup. Both halves of the misconfiguration therefore fail
closed: credential validation stops working, rather than resolving secrets for whoever asks.

Note that a token accepted this way proves the caller holds a valid token from the cluster's issuer — it is
authentication, not an entitlement check against the specific secrets the route resolves.

#### Option 2 — restrict the path in front of the runtime

The runtime's HTTP port also serves the deliberately public `/inbound/**` webhook paths, so the route cannot be
protected by restricting the port. It needs an HTTP-aware rule that matches the path — an ingress or reverse proxy that
authenticates `/configurations/**`.

Two traps when writing that rule:

- A Kubernetes `NetworkPolicy` is not enough on its own: it filters by address and port, not by path, so any source
  allowed to reach the public webhook paths can also reach `/configurations/**`.
- `/inbound` is a prefix of `/inbound-instances`. A rule that allows webhooks by prefix match rather than by exact path
  segment also exposes the management routes.

Put the restriction in place before enabling the property. The same applies to the other management routes
(`/inbound`, `/inbound-instances`, `/outbound`), which are also unauthenticated, but those expose configuration
metadata rather than credentials.

#### Diagnosing "credential validation does not appear"

While the route is disabled the runtime answers `404` there, which a calling application is expected to read as "this
cluster does not offer credential validation" — Camunda Hub maps it to an `unsupported` result and hides it, so nothing
surfaces in the UI. An operator who expects credential validation and sees no sign of it should check this property
first. A runtime older than the release that introduced the route answers `404` for the same reason, and the two cases
are indistinguishable from the caller's side.

### Overriding Connector Configuration

You can override connector configuration using the Camunda Spring Boot client's native configuration. This is useful when you need to customize connector types for specific use cases.

**YAML configuration example:**

```yaml
camunda:
  client:
    mode: selfmanaged
    worker:
      override:
        "[io.camunda:http-json:1]":
          type: "io.camunda.eaat:http-json:1"
```

**Properties file example:**

```properties
camunda.client.mode=selfmanaged
camunda.client.worker.override.[io.camunda:http-json:1].type=io.camunda.eaat:http-json:1
```

This configuration intercepts jobs with the original type and remaps them to be handled by a worker with a different type. In the example above:
- Jobs with type `io.camunda:http-json:1` are intercepted
- They are remapped to be handled by the worker configured for `io.camunda.eaat:http-json:1`
- This enables gradual connector migration without changing BPMN models

For alternative configuration methods using environment variables, refer to the [Manual Discovery section](https://docs.camunda.io/docs/next/self-managed/components/connectors/connectors-configuration/#manual-discovery-of-connectors) in the documentation.
