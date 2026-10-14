---
architecture_md: 1
component: camunda/connectors
system: { id: connectors, map: "camunda/connectors/SYSTEM.md" }
lifecycle: production
kind: [platform, library, service]
summary: Connector SDK, runtime, element-template generator and shared libraries, plus the bundled runtime applications, that execute Camunda 8 connectors.
team: { name: connectors-core, contact: unknown }
intake: { how: issue, template: FEATURE_REQUEST.md }
owns:
  - Connector SDK API (outbound/inbound connector contracts, validation, test utilities)
  - Connector runtime (job-worker execution, inbound lifecycle, secret and document handling)
  - Element template generation from Java annotations, plus the template validator
  - Shared connector libraries (object mapper, HTTP client, host-IP validation, test utils)
  - Secret provider implementations (AWS, GCP)
  - Runtime applications and bundles (Docker images, SaaS and default bundles)
  - Build parent POM, shared dependency versions, CI under .github/
does_not_own:
  - { concept: "Individual out-of-the-box connectors (except http, webhook, soap)", owner: camunda/connectors/connectors }
  - { concept: "AI Agent, MCP and A2A connectors", owner: camunda/connectors/connectors/agentic-ai }
  - { concept: "Zeebe client, job workers and engine behaviour", owner: camunda/camunda }
  - { concept: Element template schema and Modeler rendering, owner: camunda/element-templates-json-schema }
depends_on:
  - id: camunda-client
    component: camunda/camunda
    kind: library
    contract: "camunda-client-java and camunda-spring-boot-starter (Zeebe job workers, deployment, REST API)"
    versions: "Tracks version.camunda in parent/pom.xml (8.11.0-SNAPSHOT on main)"
    workaround_policy: never
  - id: spring-boot
    component: Spring Boot
    kind: external
    contract: "Runtime and starter modules build on Spring Boot auto-configuration"
    workaround_policy: never
  - id: feel-engine
    component: camunda/feel-scala
    kind: library
    contract: "FEEL evaluation, wrapped by connector-runtime/connector-feel"
    workaround_policy: never
consumers:
  - { who: "Connector authors (Camunda and third party)", via: "connector-sdk/core, connector-sdk/validation, connector-sdk/test", promise: "Public SDK API is source/binary compatible within a minor; ask first before changing" }
  - { who: "Self-managed users embedding the runtime", via: "spring-boot-starter-camunda-connectors", promise: "Recommended embedding path; configuration documented in docs/connector-runtime-configurations.md" }
  - { who: "Camunda SaaS and Self-Managed deployments", via: "Docker images from apps/ (camunda-saas-bundle, default-bundle)", promise: "Released with each Camunda minor from stable/X.Y" }
  - { who: "Connector modules in connectors/", via: "SDK, element-template-generator annotations, connector-commons", promise: "Same repo, changed together" }
  - { who: "Camunda Modeler and Web Modeler", via: "Generated element-templates/*.json", promise: "Templates are versioned per docs/connector-template-versioning.md" }
exposes:
  - { contract: "Outbound/inbound connector SDK", spec: connector-sdk/core, policy: "Java 17; public API changes need discussion" }
  - { contract: "ServiceLoader registration (META-INF/services/io.camunda.connector.api.*)", spec: connector-sdk/core, policy: "Extension point: connectors are discovered on the runtime classpath" }
  - { contract: "SecretProvider SPI", spec: secret-providers/secret-providers-base, policy: "Extension point: registered providers resolve {{secrets.NAME}}" }
  - { contract: "Runtime Spring configuration and properties", spec: docs/connector-runtime-configurations.md, policy: "Additive changes; document in the doc" }
  - { contract: "Element template generator (annotations, Maven plugin, CLI)", spec: element-template-generator/README.md, policy: "Annotations drive templates; see ADR-0001" }
constraints:
  - { id: C1, name: "SDK module compatibility and Java 17 baseline", hard: true, ref: AGENTS.md }
  - { id: C2, name: "Element template versioning and generated-file drift", hard: true, ref: docs/connector-template-versioning.md }
  - { id: C3, name: "Licence boundary (Apache 2.0 vs Camunda Self-Managed Free Edition)", hard: true, ref: AGENTS.md }
  - { id: C4, name: "Secrets resolved centrally in the runtime", hard: true, ref: docs/adr/ADR-0007-centralized-secret-resolution-in-the-connector-runtime.md }
  - { id: C5, name: "Backport to stable branches", hard: false, ref: CONTRIBUTING.md }
decisions: docs/adr/README.md

---

# Architecture — camunda/connectors (core platform)

Draft from the repository; not reviewed by its team.

<!-- This repo hosts several teams (CODEOWNERS). This file covers the core platform owned by
     connectors-core. The out-of-the-box connectors and the AI Agent module have their own files. -->

## 1. Purpose
Provides the toolkit for building connectors (SDK), the runtime that executes them as Zeebe job
workers or inbound listeners, and the generator that produces Modeler element templates. It is
used by connector authors and by Camunda deployments (SaaS, Self-Managed). Part of the [Camunda Connectors system](SYSTEM.md).

## 2. Ownership boundary
**Owns:** see `owns` above. Core also owns `connectors/http` (rest, graphql, polling, http-base),
`connectors/webhook` and `connectors/soap` and the shared `connectors/pom.xml`.

**Does not own (route here instead):**
| If you need… | It belongs to | How to ask |
|---|---|---|
| A new or changed standard connector | `connectors/` (connectors-experience) | Issue in this repo, see [connectors/ARCHITECTURE.md](connectors/ARCHITECTURE.md) |
| AI Agent, MCP, A2A behaviour | `connectors/agentic-ai` (connectors-agentic-ai) | Issue in this repo |
| Job worker or engine semantics | `camunda/camunda` | TODO(confirm) intake channel |
| Element template JSON schema | TODO(confirm) owner repo | TODO(confirm) |

## 3. Structure
| Path | Role |
|---|---|
| `connector-sdk/{core,validation,test}` | Public SDK (Java 17): contracts, bean validation, test harness |
| `connector-runtime/connector-runtime-core` | Framework-independent runtime: discovery, job handling, inbound management, secrets |
| `connector-runtime/connector-runtime-spring`, `spring-boot-starter-camunda-connectors` | Spring wiring; the starter is the recommended embedding |
| `connector-runtime/{connector-feel,jackson-datatype-*}` | FEEL and Jackson support |
| `connector-runtime/connector-runtime-test` | Runtime test support |
| `connector-commons/*` | Shared utilities: object mapper, HTTP client, host-IP validator, test utils |
| `element-template-generator/*` | Annotations, core generator, Maven plugin, CLI, OpenAPI/Postman parsers, validator |
| `secret-providers/*` | Base plus AWS and GCP providers |
| `apps/connector-runtime-application`, `apps/bundle/{default-bundle,camunda-saas-bundle}` | Runnable application and Docker bundles (runtime plus connectors) |
| `parent/` | Shared versions, plugins, enforcer rules |

Dependency direction: connectors → SDK, commons, generator annotations; runtime → SDK; apps → runtime +
connectors. SDK depends on none of the runtime or connectors. See SYSTEM.md DR1–DR3 for what is enforced.

## 4. Binding decisions
[ADR index](docs/adr/README.md). Those that most often shape changes:
- ADR-0001 annotation-driven element template generation
- ADR-0002 annotation-based operations for outbound connectors
- ADR-0003 element-scoped inbound connector properties
- ADR-0004 configuration templates in element templates
- ADR-0007 centralised secret resolution in the runtime

## 5. Planning constraints
Every plan must answer each (applies / n/a + decision or reason).

### C1 — SDK compatibility
- **Question:** Does this change the public SDK API or its Java 17 baseline? Adding record components keeps the old constructor?
- **Hard:** yes
- **Detail:** [AGENTS.md](AGENTS.md)

### C2 — Element template drift and versioning
- **Question:** Do generated templates change, are they regenerated, and does a version bump plus `versioned/` snapshot apply?
- **Hard:** yes
- **Detail:** [docs/connector-template-versioning.md](docs/connector-template-versioning.md)

### C3 — Licence boundary
- **Question:** Does code move between Apache 2.0 modules and Free Edition modules?
- **Hard:** yes
- **Detail:** [AGENTS.md](AGENTS.md)

### C4 — Secrets
- **Question:** Are secrets resolved only through the runtime's central mechanism and never logged?
- **Hard:** yes
- **Detail:** [ADR-0007](docs/adr/ADR-0007-centralized-secret-resolution-in-the-connector-runtime.md)

### C5 — Backports
- **Question:** Must this fix reach `stable/X.Y` branches (label `backport stable/X.Y`)?
- **Hard:** no
- **Detail:** [CONTRIBUTING.md](CONTRIBUTING.md)

## 6. Data and persistence
None in the core: the runtime is stateless apart from Zeebe job state and in-memory inbound registrations. TODO(confirm) whether inbound state is persisted anywhere.

## 7. Cross-cutting qualities
Secrets via `{{secrets.NAME}}` and `SecretProvider`s; host-IP validation in `connector-commons/host-ip-validator`; third-party licences tracked in [docs/third-party-licenses.md](docs/third-party-licenses.md). Dependency CVEs follow the connectors CVE triage skill.

## 8. Delivery
Released with Camunda minors; `main` tracks the next minor and `stable/X.Y` branches carry fixes, via `backport stable/X.Y` labels (never by hand). Minor-version steps: [docs/new-minor-version.md](docs/new-minor-version.md). Docker images built from `apps/`.

## 9. Testing expectations
`mvn clean package` (full), `-Dquickly` skips long tests, `mvn verify -pl <module>` for Docker-based integration tests; `connectors-e2e-test/` modules exercise runtime plus connectors (profile `e2eExcluded` skips them). See [AGENTS.md](AGENTS.md).

## 10. Planning conventions
Conventional Commits; PR template in `.github/PULL_REQUEST_TEMPLATE.md`; issue templates in `.github/ISSUE_TEMPLATE/`; ADRs per `docs/adr/README.md`.

## 11. Glossary
- **Outbound connector:** executed as a Zeebe job worker for a service task.
- **Inbound connector:** long-lived listener (webhook, subscription) that starts or correlates process instances.
- **Element template:** Modeler JSON describing a connector's properties, generated from annotations.
- **Bundle:** runtime plus a set of connectors packaged together.
