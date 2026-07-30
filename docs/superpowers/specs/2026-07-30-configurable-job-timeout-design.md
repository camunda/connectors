# Configurable job timeout (GitHub issue #4365)

## Problem

Outbound connector jobs are subject to Zeebe's job activation timeout (default 5 minutes). If a
connector call runs longer than that, the engine reassigns the job to another worker while the
original execution is still in flight, risking duplicate side effects. There is currently no way
for a process/element-template author to extend this per job.

## Solution

Expose the job timeout as an optional job header, `jobTimeout` (ISO-8601 duration, e.g. `PT10M`),
following the exact precedent of the existing `retryBackoff` header. When present, the connector
runtime issues a Zeebe `UpdateJobTimeoutCommand` for that job before invoking the connector
function, extending the deadline the engine enforces server-side.

## Changes

### 1. Keyword

Add `JOB_TIMEOUT_KEYWORD = "jobTimeout"` to
`connector-runtime/connector-runtime-core/.../Keywords.java`, alongside `RETRY_BACKOFF_KEYWORD`.

### 2. Element template property

Add a `jobTimeout` property to `CommonProperties`
(`element-template-generator/core/.../CommonProperties.java`) and bind it into the existing
`PropertyGroup.RETRIES_GROUP` (`.../PropertyGroup.java`) via `new ZeebeTaskHeader("jobTimeout")`,
mirroring `retryBackoff`. Unlike `retryBackoff`, it has **no default value** — an unset field must
mean "don't touch the timeout," since a default would fire an extra command on every job of every
connector.

This is auto-injected into every outbound connector's template by
`ClassBasedTemplateGenerator`, so it lands in every connector's `element-templates/*.json` (not
`versioned/`, and with no `version` bump, since that's a manually-set connector attribute) the next
time templates regenerate. This is mechanical: a Maven plugin regenerates all template JSON during
`process-classes` and CI fails the build if the regenerated output doesn't match what's committed.

### 3. Runtime: `SpringConnectorJobHandler`

`handle(JobClient client, ActivatedJob job)` only receives a `JobClient`, which (confirmed by
reading `camunda-client-java` source) has no `newUpdateTimeoutCommand` — that method only exists on
the full `CamundaClient`. The object handed to `handle()` at runtime is a narrower `JobClientImpl`
that cannot be cast up.

So `SpringConnectorJobHandler` gains a `CamundaClient` constructor parameter, supplied by
`OutboundConnectorManager.openWorkerForOutboundConnector`, which already holds a `CamundaClient` in
scope when it builds the `JobHandlerFactory` lambda.

Before invoking the connector function (in `getConnectorResult`, alongside the existing
`getBackoffDuration` call):

- Read `job.getCustomHeaders().get(Keywords.JOB_TIMEOUT_KEYWORD)`.
- If blank/absent, skip (no default, no-op).
- Parse as `Duration.parse(...)`. On `DateTimeParseException`, throw a new
  `InvalidJobTimeoutException` (mirrors `InvalidBackOffDurationException`), which routes through
  the existing exception handling path and fails the job immediately with `retries=0` — same
  behavior as a malformed `retryBackoff` today.
- On successful parse, call
  `camundaClient.newUpdateTimeoutCommand(job).timeout(duration).execute()` synchronously, using the
  `ActivatedJob` overload so the lease token is carried automatically (fencing the command against
  a superseded/reassigned activation of the same job).
- If the command itself throws (transient network error, job already reassigned, etc.): log a
  `WARN` and proceed with connector execution anyway. This is best-effort — we don't fail a
  perfectly runnable job because an administrative timeout-extension call had a hiccup.

### 4. Deadline threading for the completion commands

`failJob`, `completeJob`, and `throwBpmnError` each build their Zeebe command and pass
`job.getDeadline()` (captured at activation) into
`jobCallbackCommandWrapperFactory.create(command, deadline, ...)`. That `deadline` value is used
only by `JobCallbackCommandWrapper.hasMoreRetries()` to decide whether it's worth **locally**
retrying the completion command after a transient error — not to gate the command with the engine
(that's server-side and depends on the engine's actual job state).

If we extend the job's timeout but a transient error occurs while completing/failing/throwing an
error on it, the stale activation-time deadline would look expired and the client would give up
retrying prematurely, even though the engine would still accept the command. So: when the timeout
update succeeds, compute the new effective deadline (`now + duration`) and thread it through
`internalHandle` → `processFinalResult` → `handleFinalResult`/`handleConnectorError` →
`failJob`/`completeJob`/`throwBpmnError`, replacing `job.getDeadline()` at those three call sites.

## Testing

- Unit tests in `SpringConnectorJobHandlerTest`, mirroring the existing `RetryBackoffTests` nested
  class: valid duration → `newUpdateTimeoutCommand` invoked with the parsed `Duration`; invalid
  duration → job fails with retries=0 and the connector function is never invoked; missing header
  → no update command issued; update command throws → connector still executes and the job still
  completes/fails normally.
- Extend the `JobBuilder` test helper with a mocked `CamundaClient` wired for
  `newUpdateTimeoutCommand`.
- Consider a `@SlowTest` integration test (in the style of `JobRetriesIntegrationTest`) against a
  real embedded engine, asserting the job's actual timeout was extended.

## Out of scope

- No changes to the existing per-connector-type `@OutboundConnectorConfiguration.timeout()` (job
  worker activation/long-poll timeout) — that's a separate, static, connector-wide setting.
- No UI/documentation changes beyond the element template property description itself.
