# ADR-0008: Managed Script Connector with Lazy Provisioning

## Status
Proposed

## Context

Managed scripts let a Service Task run customer Python or JavaScript in an isolated cloud function.
The script is an independent, versioned Camunda resource, linked to the task like a form or an RPA
script. Nothing is provisioned when the resource or the BPMN is deployed: the engine resolves the
link to a `resourceKey` when it creates an `io.camunda:managed-script:1` job and passes it in the
`linkedResources` job header. Someone has to fetch the script, provision the cloud function on
first use and invoke it, without running customer code in the orchestration cluster and without
adding deployment-time state to the engine.

## Decision

Add an optional, disabled-by-default `connector-runtime-managed-code` module with an outbound
connector, `ManagedScriptFunction`, served by the runtime like any other. Its generated element
template applies to a Service Task, links the script with `zeebe:linkedResource`, and builds the
script input with one FEEL expression mapped to `scriptInput`, the only variable the runtime
fetches. The connector provisions lazily, inside the first job that needs an artifact:

- It fetches the linked script and optional dependency manifest by `resourceKey` and caches them,
  because a resource key's content is immutable.
- It computes a SHA-256 artifact digest over the script, the generated wrapper, the language, the
  runtime, the manifest and a build-policy version.
- It provisions through a provider SPI that must be idempotent: a deterministic deployment name
  derived from physical tenant, tenant, provider and digest, behind a configurable per-cluster
  prefix, create-if-absent, and adoption of an existing deployment.
- Concurrent jobs of one runtime instance share one provisioning. Provisioning concurrency is
  bounded separately from the active jobs of the runtime.
- The job timeout must cover the provisioning timeout plus the execution timeout; the default of
  the runtime does. A job that is still waiting at the provisioning timeout fails with unchanged
  retries and a backoff, through `ConnectorRetryException` and the job retries that `JobContext`
  now exposes; provisioning continues and the next attempt resumes it.
- Permanent provisioning errors fail the job without retries; transient ones consume one retry.
- A deployment that the provider reports missing is provisioned again once within the same job.

The deployment registry is an in-memory operational cache per runtime instance, not a source of
truth. Runtime instances do not coordinate; they converge through deterministic names and
create-if-absent at the provider.

Two providers ship. `aws` deploys each artifact as its own Lambda function with a generated
handler, publishes a version guarded by the code hash and invokes that version. `local` is for
trusted development only; it is not a sandbox. Each artifact is
deployed to its own directory, built in a staging directory and renamed atomically, and adopted
after a restart. A dependency manifest is installed once while provisioning (`pip install
--target`, `npm install --ignore-scripts`), never during an invocation. Execution runs in local
Node.js or Python subprocesses with the Connector Runtime operating-system identity.

The provider SPI has two parts, both selected by the name in
`camunda.connector.managed-code.provider`:

- `ScriptPackager` turns a script written for the Camunda contract, `execute(variables, context)`,
  into what the provider invokes: a generated entrypoint next to the user script, with the
  provider's runtime id (`packageFor`). Its `buildVersion`, a hash of the generated code, is part of
  the artifact digest, so a new entrypoint gives every script a new deployment.
- `ManagedCodeProvider` runs the lifecycle of a packaged artifact on the platform:
  `ensureProvisioned(artifact, package)`, `invoke`, `delete`.

The connector keeps everything else: the digest, the registry, the wait that consumes no retry,
re-provisioning on `DEPLOYMENT_MISSING`, limits and secrets. A provider can also come from a jar
added to the runtime classpath, such as `/opt/custom` in the Docker image, with one bean of each
part; this has not been tested. The SPI
has no compatibility guarantee yet.

## Consequences

### Positive

- No engine changes: linked resources already work on Service Tasks, and there are no deployment
  records, leases or reconciler.
- The runtime's connector machinery handles activation, variable fetching, result and error
  expressions, retries and secrets, as for every connector.
- Deployment stays fast and independent of provider availability.
- Losing the registry or a provider deployment costs first-run latency, not correctness.
- Disabling managed code leaves the ordinary connector workers unchanged.

### Negative

- The first job of each new digest waits for provisioning, and every script edit pays it again.
- Syntax and packaging errors surface at the first execution, not at deployment.
- Several runtime instances can provision the same artifact in parallel; correctness relies on
  provider idempotency (GAP-012), which each cloud provider implementation must prove.
- Provisioning status is visible only through job failure messages and incidents.
- Legacy `{{secrets.<name>}}` placeholders in the script input are not replaced: the connector
  implements `LegacySecretsNotResolved`, for which the runtime uses a deny-all secret filter.
  Secrets reach a script only through `camunda.secrets.<name>`, resolved by the engine.
- Unused deployments are not evicted yet; `lastUsedAt` is recorded and the SPI has `delete` for a
  later cleanup.
