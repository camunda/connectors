---
system_md: 1
id: connectors
name: Camunda Connectors
summary: SDK, runtime and out-of-the-box connectors that let Camunda 8 processes call and receive events from external systems, shipped as libraries and Docker bundles for SaaS and Self-Managed.
dri: camunda/connectors-core
roles:
  - { id: platform, meaning: "SDK, runtime, element-template generator, shared commons, secret providers and the runnable bundles" }
  - { id: connector, meaning: "Out-of-the-box connectors built on the platform, one Maven module each, each with its element templates" }
components:
  - { component: camunda/connectors, role: platform }
  - { component: camunda/connectors/connectors, role: connector }
  - { component: camunda/connectors/connectors/agentic-ai, role: connector }
  - { component: camunda/connectors/connectors/idp-extraction, role: connector }
rules:
  - { id: DR1, rule: "connector may depend on platform (connector-core with provided scope; connector-validation, generator annotations and commons at compile scope); platform libraries (SDK, runtime, commons, generator) never depend on a connector", enforced_by: "Maven reactor (a cycle fails the build); no explicit check that the SDK stays connector-free (gap)" }
  - { id: DR2, rule: "Among production (shipped) modules, only the bundles in apps/ depend on both the runtime and connectors; E2E test modules (connectors-e2e-test/) may compose them freely. Known exception: the connectors/agentic-ai modules depend on connector-runtime-{core,spring} for their Spring auto-configuration", enforced_by: "none yet (gap)" }
  - { id: DR3, rule: "Connectors share code only through base modules (aws-base, http-base, google-base, microsoft/common), not through each other. Known exception: connectors/agentic-ai/connector-a2a depends on connector-webhook for webhook authorization and HMAC signature utilities", enforced_by: "none yet (gap)" }
depends_on_systems: [orchestration-cluster]
---

# System — Camunda Connectors

Draft from the repository; not reviewed by its DRI.

Confirmed by @pablocabrera85 on 2026-10-14: DRI is connectors-core; the orchestration cluster's
system id is `orchestration-cluster`; `idp-extraction` is a member with its own ARCHITECTURE.md.
Still open: whether `embeddings-vector-database` and the Bedrock agent modules are separate
components, and whether anything outside this repo belongs to the system.

## 1. Purpose and products
Connectors let BPMN processes reach external systems: outbound connectors run as Zeebe job workers on
service tasks, inbound connectors (webhooks, subscriptions, polling) start or correlate process
instances. Users meet the system through:

- **Element templates** in Desktop and Web Modeler (one or more per connector).
- **Docker images** `camunda/connectors-bundle` (default bundle) and `camunda/connectors-bundle-saas`
  (SaaS), built from [`apps/`](apps/README.md).
- **Maven artifacts** for custom connectors and embedded runtimes: the SDK and
  `spring-boot-starter-camunda-connectors`.

## 2. Roles and dependency rules
Two roles. The **platform** (`camunda/connectors`, connectors-core) is what every connector builds on
and what runs them; the **connector** role is the catalogue, split by owning team per CODEOWNERS:
connectors-experience (default), connectors-agentic-ai (`agentic-ai`, plus
`embeddings-vector-database` and the Bedrock agent modules — TODO(confirm) whether those are
separate components), connectors-idp ([`idp-extraction`](connectors/idp-extraction/ARCHITECTURE.md)). connectors-core also owns the HTTP, webhook and
SOAP connectors, which sit in the connector role.

DR1 keeps the SDK stable for third-party authors. DR2 makes the bundles the single place where a
connector set is chosen. DR3 avoids hidden coupling between connectors with separate owners. Both DR2 and DR3 have one known
exception (`agentic-ai`, see the rules above); do not add new ones without moving the shared
code into a base module.

## 3. Composition paths
- **New connector behaviour:** `connector module (annotations) → element-template-generator →
  element-templates/*.json → Modeler`, and `connector module → apps/bundle → Docker image → cluster`.
  A change reaches users only when both the template and the bundle ship.
- **SDK or runtime capability** (e.g. document handling, secrets, inbound deduplication):
  `connector-sdk → connector-runtime-core → runtime-spring → spring-boot-starter → bundles`, and every
  connector that uses it. Third-party connectors and self-managed embedded runtimes receive it too.
- **Element template conventions** (generator, validator, docs-link checks): apply to every
  connector's template at the next regeneration, and to CI via `.github/workflows/`.
- **Non-product consumers:** CI workflows (template drift, validator, docs links, AI Agent CPT
  matrix), the end-to-end suites in `connectors-e2e-test/`, and coding agents reading `AGENTS.md`
  and the skills in `.agents/skills/`.

## 4. Edges to other systems
- **Orchestration cluster (`camunda/camunda`):** the runtime uses `camunda-client-java` and
  `camunda-spring-boot-starter` for job workers, message correlation, deployments and the REST API;
  `version.camunda` in `parent/pom.xml` tracks its minor.
- **Modeling:** element templates follow the `@camunda/zeebe-element-templates-json-schema` schema and
  are rendered by Desktop and Web Modeler.
- **FEEL:** `feel-engine` (`camunda/feel-scala`) evaluates expressions in inputs and the runtime.
- **Docs (`camunda/camunda-docs`):** user documentation; templates link to versioned pages.

## 5. Conventions
Connectors are discovered through `ServiceLoader` and carry `@OutboundConnector`/`@InboundConnector`
plus `@ElementTemplate`; exception: the `agentic-ai` modules register their connectors as Spring beans via per-module
`*AutoConfiguration` classes (`META-INF/spring/...AutoConfiguration.imports`), with no
`META-INF/services` connector registration; templates are generated, never hand-edited (ADR-0001). Multi-operation
connectors use `@Operation` (ADR-0002). Secrets as `{{secrets.NAME}}`, resolved centrally (ADR-0007).
Licence split: Apache 2.0 for SDK, runtime, HTTP REST connector and generator; Camunda Self-Managed
Free Edition for most connectors. Details: [AGENTS.md](AGENTS.md), [docs/adr](docs/adr/README.md).

## 6. Shared qualities and delivery
All components release together from this repo with each Camunda minor: `main` is the next minor,
`stable/X.Y` carries fixes via `backport stable/X.Y` labels. Element templates are versioned
independently ([docs/connector-template-versioning.md](docs/connector-template-versioning.md)).
Dependency CVEs are triaged with the `connectors-cve-triage-v3` skill.
