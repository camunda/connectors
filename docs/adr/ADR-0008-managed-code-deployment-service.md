# ADR-0008: Managed code deployment service

## Status
Proposed

## Context

Managed scripts are process-definition resources that must be packaged and deployed to AWS Lambda
or Google Cloud Run functions before process instances invoke them. Provider control-plane calls
can take seconds or minutes, require privileged credentials, and must survive runtime restarts.
They are not ordinary connector job executions.

The Orchestration Cluster owns durable managed-script definitions and exposes leased
reconciliation APIs. Connectors has reusable Camunda client lifecycle, authentication,
multi-physical-tenant, telemetry, and Spring runtime infrastructure, but ordinary connector workers
must not share provider build capacity or failure modes.

## Decision

Add an optional managed-code deployment module to the Connectors repository.

The module:

- is disabled by default;
- runs as a separately configurable workload and identity from ordinary connector workers;
- activates and leases managed-script definitions through the public Camunda API;
- fetches immutable script resources;
- delegates packaging and deployment to a provider-neutral deployment provider;
- renews leases during long provider operations;
- reports idempotent, fenced lifecycle transitions to Camunda;
- resumes provider operations from persisted identifiers after restart;
- isolates each physical tenant and Camunda client;
- exposes a deterministic fake provider for self-contained local validation.

The module does not execute production customer scripts. AWS Lambda and Google Cloud Run remain the
production execution boundaries. Provider adapters are separate from the reconciliation core so
their SDKs and credentials do not become dependencies of the standard Connector Runtime.

Camunda remains the authoritative source for desired state and public lifecycle status. Local
caches may optimize processing but cannot determine correctness.

## Consequences

### Positive

- Provider latency, quotas, SDKs, and credentials remain outside the Zeebe broker.
- The reconciler can stop and restart without losing deployments created while it was unavailable.
- Existing Connectors client lifecycle and physical-tenant patterns are reused.
- Disabling managed code leaves normal connector discovery and workers unchanged.
- Provider implementations can evolve independently behind a small deployment interface.

### Negative

- Managed code introduces another optional runtime workload and configuration surface.
- The Connectors release must remain compatible with the corresponding Camunda reconciliation API.
- Lease renewal, shutdown, multi-tenant routing, and provider idempotency need dedicated tests.
- Real AWS and GCP adapters require additional modules, dependencies, credentials, and operational
  documentation.
