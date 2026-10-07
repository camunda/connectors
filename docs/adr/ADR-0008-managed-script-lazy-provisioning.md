# ADR-0008: Managed Script Worker with Lazy Provisioning

## Status
Proposed

## Context

Managed scripts let a Script Task run customer Python or JavaScript in an isolated cloud function.
The script is an independent, versioned Camunda resource, linked to the task like a form or an RPA
script. Nothing is provisioned when the resource or the BPMN is deployed: the engine resolves the
link to a `resourceKey` when it creates an `io.camunda:managed-script:1` job and passes it in the
`linkedResources` job header. Someone has to fetch the script, provision the cloud function on
first use and invoke it, without running customer code in the orchestration cluster and without
adding deployment-time state to the engine.

## Decision

Add an optional, disabled-by-default `connector-runtime-managed-code` module whose job worker
provisions lazily, inside the first job that needs an artifact:

- It fetches the linked script and optional dependency manifest by `resourceKey` and caches them,
  because a resource key's content is immutable.
- It computes a SHA-256 artifact digest over the script, the generated wrapper, the language, the
  runtime, the manifest and a build-policy version.
- It provisions through a provider SPI that must be idempotent: a deterministic deployment name
  derived from physical tenant, tenant, provider and digest, create-if-absent, and adoption of an
  existing deployment.
- Concurrent jobs of one runtime instance share one provisioning. Provisioning concurrency is
  bounded separately from invocation concurrency.
- The job timeout covers the provisioning timeout plus the execution timeout. A job that is still
  waiting at the provisioning timeout fails with unchanged retries and a backoff; provisioning
  continues and the next attempt resumes it.
- Permanent provisioning errors fail the job without retries; transient ones consume one retry.
- A deployment that the provider reports missing is provisioned again once within the same job.

The deployment registry is an in-memory operational cache per runtime instance, not a source of
truth. Runtime instances do not coordinate; they converge through deterministic names and
create-if-absent at the provider.

Only a `fake` provider ships: provisioning is simulated with a configurable delay and execution
runs in local Node.js or Python subprocesses. It is for trusted development only and is not a
sandbox. Cloud providers are separate implementations of the SPI.

## Consequences

### Positive

- No engine changes beyond linked-resource support for Script Tasks: no deployment records,
  leases or reconciler.
- Deployment stays fast and independent of provider availability.
- Losing the registry or a provider deployment costs first-run latency, not correctness.
- Disabling managed code leaves the ordinary connector workers unchanged.

### Negative

- The first job of each new digest waits for provisioning, and every script edit pays it again.
- Syntax and packaging errors surface at the first execution, not at deployment.
- Several runtime instances can provision the same artifact in parallel; correctness relies on
  provider idempotency (GAP-012), which each cloud provider implementation must prove.
- Provisioning status is visible only through job failure messages and incidents.
- Unused deployments are not evicted yet; `lastUsedAt` is recorded for a later cleanup.
