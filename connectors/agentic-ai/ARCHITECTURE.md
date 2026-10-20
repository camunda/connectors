---
architecture_md: 1
component: camunda/connectors/connectors/agentic-ai
system: { id: connectors, map: "camunda/connectors/SYSTEM.md" }
lifecycle: production
kind: [library]
summary: AI Agent connectors (Task and Sub-process) that run LLM tool-calling loops in BPMN, plus standalone MCP and A2A client connectors, the extension API (aiagent-api), conversation memory and chat model providers.
team: { name: connectors-agentic-ai, contact: unknown }
intake: { how: issue, template: FEATURE_REQUEST.md }
owns:
  - AI Agent Task and AI Agent Sub-process connectors and their shared orchestration core (connector-aiagent)
  - Standalone MCP client and A2A client connectors (connector-mcp, connector-a2a), independent of the AI Agent
  - Extension API and model for custom extensions (aiagent-api, aiagent-model) and the agentic-ai-bundle that ships all connectors
  - Agent state machine, conversation memory and conversation store SPI
  - Tool resolution, fromAi() extraction and ad-hoc tool schema generation
  - Chat model provider integrations (LangChain4j and native providers)
  - MCP and A2A gateway tool integrations (aiagent-mcp-integration, aiagent-a2a-integration)
  - "AI Agent real-provider (CPT) test registry (connectors-e2e-test/connectors-e2e-test-agentic-ai/src/test/resources/ai-agent-cpt) and, as maintainer, its CI scripts in .github/ai-agent-cpt (CODEOWNERS review: connectors-core)"
does_not_own:
  - { concept: "SDK, runtime, job-worker execution", owner: camunda/connectors }
  - { concept: Ad-hoc sub-process and engine tool-call semantics, owner: camunda/camunda }
  - { concept: Other AWS Bedrock and vector database connectors' generic AWS base, owner: camunda/connectors/connectors/aws }
depends_on:
  - id: sdk
    component: camunda/connectors
    kind: library
    contract: "connector-sdk/core, and connector-runtime-{core,spring} for each module's Spring auto-configuration; job completion handled in the SDK (ADR 002)"
    architecture: ARCHITECTURE.md
    versions: Same repo and version
    workaround_policy: never
  - id: webhook
    component: camunda/connectors/connectors/webhook
    kind: library
    contract: "connector-webhook (authorization and HMAC signature utilities), used by connector-a2a only"
    architecture: ARCHITECTURE.md
    versions: Same repo and version
    workaround_policy: never
  - id: camunda-client
    component: camunda/camunda
    kind: library
    contract: "camunda-client-java for ad-hoc sub-process activation and agent instance API"
    versions: "Tracks version.camunda in parent/pom.xml"
    workaround_policy: never
  - id: langchain4j
    component: LangChain4j
    kind: external
    contract: "Chat model abstraction, confined to aiagent/chatmodel/provider/langchain4j"
    workaround_policy: never
  - id: llm-providers
    component: "LLM provider APIs (OpenAI, Anthropic, Bedrock, Azure, Google, Mistral, and others)"
    kind: external
    contract: "HTTP APIs via LangChain4j or native providers"
    workaround_policy: never
  - id: mcp-a2a
    component: "MCP servers and A2A agents"
    kind: external
    contract: "MCP client and A2A protocol clients"
    workaround_policy: never
consumers:
  - { who: "Process modelers", via: "AI Agent element templates (element-templates/)", promise: "Templates versioned; migration of running instances limited per ai-agent.md §17" }
  - { who: "Custom extension authors", via: "SPIs: chat model provider, SystemPromptContributor, ConversationStore, gateway tool", promise: "Breaking changes listed in docs/breaking-changes.md" }
exposes:
  - { contract: "AI Agent Task and Sub-process connector types", spec: "element-templates/", policy: "Versioned templates; v1 deprecation marker" }
  - { contract: "MCP client and A2A client connector types", spec: "element-templates/", policy: "Versioned templates; evolve independently of the AI Agent (ADR 015)" }
  - { contract: "Extension SPIs (gateway tool, chat model, conversation storage, system prompt contributor)", spec: "aiagent-api module; docs/reference/ai-agent.md#25-extension-points", policy: "Spring beans implementing the SPI; types referenced by the SPIs live in aiagent-model; breaking changes recorded" }
constraints:
  - { id: C1, name: "Framework-agnostic core (LangChain4j only in provider package)", hard: true, ref: AGENTS.md }
  - { id: C2, name: "E2E tests for engine/BPMN wiring changes", hard: true, ref: AGENTS.md }
  - { id: C3, name: "Real-provider CPT run authorised per PR head SHA", hard: false, ref: AGENTS.md }
  - { id: C4, name: "Conversation store write-ahead and concurrency invariants", hard: true, ref: docs/reference/ai-agent.md }
  - { id: C5, name: "ADR before implementation for significant decisions", hard: false, ref: docs/adr }
  - { id: C6, name: "Module dependency direction and package ownership (ADR 015)", hard: true, ref: AGENTS.md }
decisions: docs/adr

---

# Architecture — camunda/connectors/connectors/agentic-ai

Draft from the repository; not reviewed by its team. The module's deep reference lives in
[AGENTS.md](AGENTS.md) and [docs/reference/ai-agent.md](docs/reference/ai-agent.md); this file only
states boundaries and links.

## 1. Purpose
Lets BPMN processes run LLM agents that call tools, modelled either as a service task or as an ad-hoc
sub-process (recommended). Core platform: [root ARCHITECTURE.md](../../ARCHITECTURE.md); part of the [Camunda Connectors system](../../SYSTEM.md).

## 2. Ownership boundary
**Owns:** see `owns` above. Also CODEOWNERS-assigned: `connectors/embeddings-vector-database`,
`connectors/aws/aws-bedrock-{codeinterpreter,knowledgebase,agentcore-runtime,agentcore-long-term-memory}`
and `connectors-e2e-test/connectors-e2e-test-agentic-ai`; TODO(confirm) whether those get their own files or are covered here.
Cross-team: this team maintains the CPT CI scripts in `.github/ai-agent-cpt`, but CODEOWNERS assigns
all of `.github/` to connectors-core, which reviews changes there.

**Does not own (route here instead):**
| If you need… | It belongs to | How to ask |
|---|---|---|
| SDK or runtime change | connectors-core | Issue in this repo |
| Engine behaviour of ad-hoc sub-processes | `camunda/camunda` | TODO(confirm) |

## 3. Structure
Ten Maven modules plus `agentic-ai-bundle` and the test-only `agentic-ai-archunit-tests`, split by
[ADR 015](docs/adr/015-split-agentic-ai-into-modules.md), group `io.camunda.connector.agenticai`;
package roots are relative to that package:

| Kind | Modules (package root) |
|---|---|
| Libraries | `agentic-ai-common` (`common`), `aiagent-model` (`aiagent.model`), `aiagent-api` (`aiagent.api`), `aiagent-tools-schema` (`tools.schema`) |
| Connectors | `connector-aiagent` (`aiagent`: agent, memory, chat model providers incl. `langchain4j`, stores), `connector-ad-hoc-tools-schema` (`adhoctoolsschema`), `connector-mcp` (`mcp.client`), `connector-a2a` (`a2a.client`) |
| Integrations | `aiagent-mcp-integration` (`mcp.integration`), `aiagent-a2a-integration` (`a2a.integration`) |

Dependency direction: client connectors depend on `agentic-ai-common` only; integrations depend on
their client plus the API; `connector-aiagent` never depends on clients or integrations and finds
gateway tools as beans. Each module has its own auto-configuration, element templates and icons, all
switched by `camunda.connector.agenticai.enabled`. A new connector or integration is added to
`agentic-ai-bundle`. Boundaries are enforced by `agentic-ai-archunit-tests`. Within `connector-aiagent`,
two flavors share `BaseAgentRequestHandler`:
`AgentTaskV2Function` and `AgentSubProcessV2Function` are the current entry points;
`AgentTaskV1Function` and `AgentSubProcessV1Function` are deprecated backward-compatibility shims for
existing v1 process models. Interfaces sit in the package root with a single
`*Impl` alongside, wired as Spring beans.

## 4. Binding decisions
[docs/adr](docs/adr/) (001–015): most often constraining are 002 consolidate job worker into SDK, 003 conversation storage SPI redesign, 009 chat model provider SPI, 014 route v1 requests through native providers (LangChain4j being retired), 015 module split. Architectural invariants: [§24](docs/reference/ai-agent.md#24-architectural-invariants).

## 5. Planning constraints
### C1 — Framework neutrality
- **Question:** Does any new code outside `aiagent/chatmodel/provider/langchain4j` import `dev.langchain4j.*` or leak framework types into domain model?
- **Hard:** yes
- **Detail:** [AGENTS.md](AGENTS.md)

### C2 — Test level
- **Question:** Does correctness depend on engine/BPMN wiring (needs E2E) or is it a pure transformation (unit)?
- **Hard:** yes
- **Detail:** [AGENTS.md](AGENTS.md)

### C3 — Real-provider tests
- **Question:** Does the change touch provider behaviour requiring a CPT row and an authorised run on the head SHA?
- **Hard:** no
- **Detail:** [AGENTS.md](AGENTS.md)

### C4 — Concurrency and memory
- **Question:** Does the change affect job supersession, store-ahead-of-Zeebe ordering or partial tool results?
- **Hard:** yes
- **Detail:** [ai-agent.md §10](docs/reference/ai-agent.md#10-concurrency-challenges--race-conditions)

### C5 — Public extension compatibility
- **Question:** Does it change an SPI (`aiagent-api`/`aiagent-model` types) or process-variable data model? Record in breaking-changes and check running-instance migration.
- **Hard:** no
- **Detail:** [docs/breaking-changes.md](docs/breaking-changes.md)

### C6 — Module boundaries
- **Question:** Does a new class sit under its module's package root, and does the change add a module dependency, package cycle or element-template annotation in a library module? Update `AgenticAiModules` and the arch tests, never `KNOWN_CYCLES`.
- **Hard:** yes
- **Detail:** [AGENTS.md](AGENTS.md#modules), [ADR 015](docs/adr/015-split-agentic-ai-into-modules.md)

## 6. Data and persistence
Conversation memory is pluggable via `ConversationStore`: in-process (process variables), Camunda documents, and AWS AgentCore memory ([aws-agentcore-memory.md](docs/reference/aws-agentcore-memory.md)); agent context is kept in process variables. See [ai-agent.md §6](docs/reference/ai-agent.md#6-conversation-memory--storage).

## 7. Cross-cutting qualities
Credentials via secrets; MCP and A2A calls to external endpoints. TODO(confirm) security, data-residency and observability expectations beyond the agent instance status/metrics API.

## 8. Delivery
As the root ([Delivery](../../ARCHITECTURE.md#8-delivery)). Element templates are versioned; v1 task templates carry a deprecation marker.

## 9. Testing expectations
Unit tests, architecture tests (`./mvnw test -f connectors/agentic-ai/pom.xml -pl agentic-ai-archunit-tests -am '-Dtest=*ArchTest,ModuleInventoryTest' -Dsurefire.failIfNoSpecifiedTests=false`), E2E under `connectors-e2e-test/connectors-e2e-test-agentic-ai`, and real-LLM CPT coverage driven by the registry in the agentic-ai E2E module's test resources (`ai-agent-cpt/registry.json`), validated by `.github/ai-agent-cpt` scripts. See [AGENTS.md](AGENTS.md) §Working in this module.

## 10. Planning conventions
ADRs in `docs/adr/` written before implementation and immutable once merged; keep `AGENTS.md` and `docs/reference/` updated with changes.

## 11. Glossary
- **AHSP:** ad-hoc sub-process hosting agent tools.
- **Gateway tool:** tool that fronts a remote capability (MCP server, A2A agent).
- **CPT:** real-provider test run of the agent against live LLMs.
