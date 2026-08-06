# Job lease support in the Connector SDK

GitHub issue: https://github.com/camunda/connectors/issues/8044

## Context

Job lease tokens (ADR 0005-810 in `camunda/camunda`) let a Zeebe job worker fence its
complete/fail/throw-error commands against stale re-activations after a timeout race. The AI
Agent connector needs a lease token surfaced so it can safely push agent history & metrics during
execution and use the token for its own agent-history requests. This spec covers only the SDK
primitive: making lease activation configurable per outbound connector, and exposing the
resulting token to connector code. Adoption by the AI Agent connector itself (opting in, and using
the token for history/metrics requests) is out of scope and tracked separately.

### Upstream status (verified 2026-08-06)

The blocker, camunda/camunda#57812, merged into `camunda/camunda` main on 2026-08-05 (PR #59475).
Connectors already depends on `camunda-client-java` / `camunda-spring-boot-starter` at
`8.10.0-SNAPSHOT` (`parent/pom.xml:73`), and the snapshot already resolves with:

- `JobWorkerValue.withLease` (`SourceAware<Boolean>`, default `Empty`)
- `ActivatedJob.getLeaseToken()` → `String`, `null` if activated without a lease
- `CompleteJobCommandStep1.withLeaseToken(String)` (and the fail/throw-error equivalents)

No dependency version bump is required.

### Why completion fencing needs no code change here

`SpringConnectorJobHandler` (`connector-runtime/connector-runtime-spring/src/main/java/io/camunda/connector/runtime/outbound/job/SpringConnectorJobHandler.java`)
builds every complete/fail/throw-error command from the `ActivatedJob`-based overloads
(`client.newCompleteCommand(job)`, `newFailCommand(job)`, `newThrowErrorCommand(job)`) — never the
`long jobKey` overloads. Per the upstream client's own doc comment on
`CompleteJobCommandStep1#withLeaseToken`, the `ActivatedJob`-based overload carries the job's lease
token onto the command automatically. This was verified against every production call site in the
module (grepped repo-wide; the only non-test file building these commands is
`SpringConnectorJobHandler`). So once a job is activated with a lease, its completion is already
fenced — nothing to change in the command-building code.

Also verified: `JobWorkerManager.createJobWorker` (camunda-spring-boot-starter) runs all
`JobWorkerValueCustomizer`s — including `PropertyBasedJobWorkerValueCustomizer` — over the
hand-built `JobWorkerValue` the connector runtime constructs. `SourceAware` priority ordering
(`Empty`=0, `FromAnnotation`=3, `FromOverrideProperty`=4) guarantees the upstream
`camunda.client.worker.override.<type>.with-lease` property always wins over whatever the
`@OutboundConnector` annotation set, regardless of value. So the operator escape hatch is
preserved no matter what this change does.

## Changes

### 1. `@OutboundConnector` annotation

`connector-sdk/core/src/main/java/io/camunda/connector/api/annotation/OutboundConnector.java`

Add:

```java
/** Whether to activate jobs for this connector with a lease. Default {@code false}. */
boolean withLease() default false;
```

### 2. `OutboundConnectorConfiguration`

`connector-runtime/connector-runtime-core/src/main/java/io/camunda/connector/runtime/core/config/OutboundConnectorConfiguration.java`

Add a `boolean withLease` field to the record, following the shape of the existing `timeout`
field. Update the secondary (4-arg) constructor to default it to `false`.

Populate it at all three construction sites:

- `ConnectorConfigurationUtil.getOutboundConnectorConfiguration` — from `annotation.withLease()`
- `DefaultOutboundConnectorFactory.toConfiguration` — from `outboundConnector.withLease()`
- `EnvVarsConnectorDiscovery.loadOutboundConfiguration` — from
  `annotationConfig.map(OutboundConnectorConfiguration::withLease).orElse(false)`

No new environment variable / property override is introduced for this field — the upstream
`camunda.client.worker.override.<type>.with-lease` property already covers the
operator-override case for any connector's job type, hand-built `JobWorkerValue` or not.

### 3. Wiring into the job worker

`connector-runtime/connector-runtime-spring/src/main/java/io/camunda/connector/runtime/outbound/lifecycle/OutboundConnectorManager.java`,
in `openWorkerForOutboundConnector`, alongside the existing `timeout` wiring:

```java
if (connector.withLease()) {
  jobWorkerValue.setWithLease(new FromAnnotation<>(true));
}
```

Left unset (`Empty`) when `false`, mirroring the existing `timeout` pattern (only set when there's
something to set) — not because setting `FromAnnotation<>(false)` would be unsafe (it wouldn't;
the override property always wins per the priority ordering above), but for consistency with the
existing code shape in this method.

### 4. `JobContext`

`connector-sdk/core/src/main/java/io/camunda/connector/api/outbound/JobContext.java`

Add as a **default method**, not abstract — this interface is public SDK API
(`connector-sdk/core`), and a default method avoids a source-breaking change for any
out-of-tree implementer:

```java
/**
 * The lease token identifying this job's activation, or {@code null} if the job was activated
 * without a lease. Pass it along when making requests that should be fenced against a stale,
 * superseded activation of this same job.
 */
default String getLeaseToken() {
  return null;
}
```

Implementations to update:

- `ActivatedJobContext` (`connector-runtime/connector-runtime-core/src/main/java/io/camunda/connector/runtime/core/outbound/ActivatedJobContext.java`)
  — production implementation, wraps the real `ActivatedJob`. Override:
  ```java
  @Override
  public String getLeaseToken() {
    return activatedJob.getLeaseToken();
  }
  ```
- `TestJobContext` (`connector-runtime/connector-runtime-test/src/main/java/io/camunda/connector/runtime/test/outbound/TestJobContext.java`)
  — public test-support SDK type used by connector authors' unit tests. Add a `leaseToken` field
  with a getter override and a `setLeaseToken` setter, matching the existing field/setter pattern
  used for `type`, `bpmnProcessId`, etc.

## Out of scope

- Setting `withLease = true` on any AI Agent connector function's `@OutboundConnector` annotation.
- Any AI Agent connector logic that reads or forwards the lease token for its own agent-history /
  metrics requests.
- Any new environment-variable or property override specific to Connectors for `withLease` — the
  upstream Spring Boot SDK property already serves that purpose.
- `@LeaseToken` parameter injection — not applicable to connector handlers (they receive one
  flattened context object, not per-parameter resolution), and not requested by the issue.

## Testing plan

- `ConnectorConfigurationUtilTest` (or equivalent): `@OutboundConnector(withLease = true)` on a
  test connector class → `getOutboundConnectorConfiguration(...).withLease()` is `true`.
- `OutboundConnectorManagerTest` (or equivalent): a connector configuration with `withLease = true`
  results in `JobWorkerValue.getWithLease()` being `FromAnnotation<>(true)`; `withLease = false`
  leaves it `Empty`.
- `JobHandlerContextTest` (`connector-runtime/connector-runtime-core/src/test/java/io/camunda/connector/runtime/core/outbound/JobHandlerContextTest.java`,
  which exercises `ActivatedJobContext` — there's no dedicated `ActivatedJobContextTest`):
  `getLeaseToken()` delegates to `activatedJob.getLeaseToken()`, including the `null` case.
- `TestJobContext`: getter/setter round-trip for `leaseToken` (extend
  `OutboundConnectorContextBuilderTest` if it's the natural home, or add a direct unit test).
