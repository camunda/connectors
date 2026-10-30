---
architecture_md: 1
component: camunda/connectors/connectors
system: { id: connectors, map: "camunda/connectors/SYSTEM.md" }
lifecycle: production
kind: [library]
summary: The out-of-the-box Camunda connectors, one Maven module per connector, each with its element template.
team: { name: connectors-experience, contact: unknown }
intake: { how: issue, template: FEATURE_REQUEST.md }
owns:
  - Behaviour and element templates of each out-of-the-box connector (AWS, Kafka, Slack, email, JDBC, Salesforce, and others)
  - Per-connector inputs, authentication models and error mapping
does_not_own:
  - { concept: "HTTP (REST, GraphQL, polling), webhook and SOAP connectors", owner: camunda/connectors }
  - { concept: "AI Agent, MCP, A2A, embeddings vector database and Bedrock agent connectors", owner: camunda/connectors/connectors/agentic-ai }
  - { concept: IDP extraction connector, owner: camunda/connectors/connectors/idp-extraction }
  - { concept: "SDK, runtime, template generator, shared commons", owner: camunda/connectors }
depends_on:
  - id: sdk
    component: camunda/connectors
    kind: library
    contract: "connector-sdk/core (scope provided), connector-sdk/validation (compile scope)"
    architecture: ARCHITECTURE.md
    versions: Same repo and version
    workaround_policy: never
  - id: generator
    component: camunda/connectors
    kind: library
    contract: "element-template-generator annotations and Maven plugin"
    architecture: ARCHITECTURE.md
    versions: Same repo and version
    workaround_policy: never
  - id: commons
    component: camunda/connectors
    kind: library
    contract: "connector-commons (object mapper, HTTP client, test utils)"
    architecture: ARCHITECTURE.md
    versions: Same repo and version
    workaround_policy: never
consumers:
  - { who: "Process modelers (SaaS and Self-Managed)", via: "Element templates in each module's element-templates/", promise: "Templates versioned; released versions immutable under versioned/" }
  - { who: "Runtime bundles", via: "Maven dependencies in apps/bundle and ServiceLoader files", promise: "Each connector registered via META-INF/services" }
exposes:
  - { contract: "Element templates", spec: "connectors/*/element-templates/*.json", policy: "docs/connector-template-versioning.md" }
  - { contract: "Connector type ids and input/output schemas", spec: "each module's @OutboundConnector / @InboundConnector", policy: "Changing a type id or input is breaking for deployed processes" }
constraints:
  - { id: C1, name: "Generated templates are never hand-edited", hard: true, ref: ../AGENTS.md }
  - { id: C2, name: "Template version bump and snapshot", hard: true, ref: ../docs/connector-template-versioning.md }
  - { id: C3, name: "Both connector annotation and @ElementTemplate present, plus ServiceLoader file", hard: true, ref: ../AGENTS.md }
  - { id: C4, name: "Docs links pinned to the current released minor", hard: true, ref: ../AGENTS.md }
  - { id: C5, name: "Reusable credentials chooser pattern", hard: false, ref: ../docs/credentials-in-element-templates.md }
decisions: ../docs/adr/README.md
---

# Architecture — camunda/connectors/connectors

Draft from the repository; not reviewed by its team.

## 1. Purpose
Ships the standard connectors users drag into BPMN models. Each connector is its own Maven module
built on the core platform ([root ARCHITECTURE.md](../ARCHITECTURE.md)); part of the [Camunda Connectors system](../SYSTEM.md).

## 2. Ownership boundary
**Owns:** see `owns` above. Default owner of everything under `connectors/` unless CODEOWNERS says otherwise.

**Does not own (route here instead):**
| If you need… | It belongs to | How to ask |
|---|---|---|
| SDK or runtime change | connectors-core, [root](../ARCHITECTURE.md) | Issue in this repo |
| AI Agent, MCP, A2A, vector DB, Bedrock agent connectors | connectors-agentic-ai, [agentic-ai](agentic-ai/ARCHITECTURE.md) | Issue in this repo |
| IDP extraction | connectors-idp, [idp-extraction](idp-extraction/ARCHITECTURE.md) | Issue in this repo |
| HTTP (REST, GraphQL, polling), webhook, SOAP | connectors-core | Issue in this repo |

## 3. Structure
One module per connector (some grouped: `aws/`, `google/`, `microsoft/`, `http/`). Typical module:
`src/main/java` (function or `OutboundConnectorProvider` with `@Operation`, model, `@ElementTemplate`),
`src/main/resources/META-INF/services`, `element-templates/` (generated; `versioned/` holds released
snapshots), tests (`*Test`, `*InputValidationTest`, `*SecretsTest`, WireMock, `@SlowTest`).
Reference implementations: `http/rest` (outbound), `webhook` (inbound), `kafka` (both),
`microsoft/azure-blobstorage` (documents). `salesforce` generates its template via a manual
`GenerateElementTemplate`; `operate` and `power-automate` templates are hand-maintained.
Dependencies point at the SDK with `provided` scope; connectors share code only through base modules (`aws/aws-base`, `http/http-base`, `google/google-base`, `microsoft/common`).
Known exception: `agentic-ai/connector-a2a` depends on `webhook` (authorization and HMAC signature utilities) and the `agentic-ai` modules on `connector-runtime-{core,spring}` (SYSTEM.md DR2/DR3); do not add new cross-connector dependencies.

## 4. Binding decisions
[ADR index](../docs/adr/README.md): ADR-0001, 0002, 0003, 0004 (templates and operations), 0005 (storage connector flexibility), 0006 (createDocument FEEL function).

## 5. Planning constraints
### C1 — Generated templates
- **Question:** Which source annotations change, and is the template regenerated (`process-classes`) with a clean `git status`?
- **Hard:** yes
- **Detail:** [AGENTS.md](../AGENTS.md)

### C2 — Template versioning
- **Question:** Is the change non-cosmetic (bump version, archive snapshot) or cosmetic (no bump)? Is the template unreleased?
- **Hard:** yes
- **Detail:** [docs/connector-template-versioning.md](../docs/connector-template-versioning.md)

### C3 — Registration
- **Question:** Does the connector have `@OutboundConnector`/`@InboundConnector`, `@ElementTemplate` and ServiceLoader entries?
- **Hard:** yes
- **Detail:** [AGENTS.md](../AGENTS.md)

### C4 — Documentation links
- **Question:** Do element template links pin the current released minor (not unversioned, not `/next/`)?
- **Hard:** yes
- **Detail:** [AGENTS.md](../AGENTS.md)

### C5 — Credentials
- **Question:** Does the connector add or change a reusable credentials chooser?
- **Hard:** no
- **Detail:** [docs/credentials-in-element-templates.md](../docs/credentials-in-element-templates.md)

## 6. Data and persistence
None owned. Connectors call external systems; files go through `Document` and `DocumentCreationRequest`.

## 7. Cross-cutting qualities
Secrets as `{{secrets.NAME}}`; `ConnectorsObjectMapperSupplier.getCopy()` for JSON; errors via `ConnectorException` / `ConnectorRetryException` / `BpmnError`; Jakarta validation on inputs. AWS SDK v1 allowed only in `aws-sns` for webhook signature verification (`SnsMessageManager`) and subscription confirmation (`SnsSubscriptionConfirmation#confirmSubscription`).

## 8. Delivery
Same trains as the root ([Delivery](../ARCHITECTURE.md#8-delivery)). Bug fixes: ask whether a `backport stable/X.Y` is needed.

## 9. Testing expectations
Unit and input-validation tests per connector, secrets tests, WireMock for HTTP, Docker-based integration via `DockerImages`, E2E modules under `connectors-e2e-test/` for runtime behaviour.

## 10. Planning conventions
Conventional Commits; one concern per PR; PR template in `.github/`.

## 11. Glossary
- **Provider (operation-based):** `OutboundConnectorProvider` with `@Operation` methods, preferred for multi-operation connectors.
- **Hybrid template:** variant under `element-templates/hybrid/` for the hybrid task runtime; generated too.
