---
title: 'Local Toolbox: reusable AI Agent tool sets via a process-reference gateway tool'
labels: kind:epic, component:ai, component:connectors
repo: camunda/product-hub
filed: https://github.com/camunda/product-hub/issues/3772
---

# Value Proposition Statement

Let an AI Agent Sub-process (AHSP) reference another deployed process — by process ID and
optional version — as a single gateway tool. That referenced process is itself just a plain
ad-hoc sub-process (start event → tool AHSP, no AI Agent template applied → end event); its tool
elements get auto-discovered and exposed to the calling agent exactly the way MCP/A2A tools are
today, without standing up an external MCP server or redefining the same tools in every process.
A dedicated `meta` argument, exposed on the gateway's element template, lets the caller pass
deterministic configuration straight through to the toolbox instance, bypassing the LLM entirely.

# User Problem

**Context**: building "Ticket Genie" (Jira support-ticket automation), we need several AI Agent
processes to share one tool set — attachment handling (notably for Self-Managed), Jira lookups,
support-phrasing guidelines. Today nothing is shared: every AHSP that hosts an agent has to define
its own tool elements, so the same tool gets modeled N times across N processes with no single
source of truth.

We looked at three ways to reuse tools across agent processes in the same cluster:

- **Cluster MCP + MCP start event** — works with the already-shipped MCP gateway resolver, but
  adds a network round-trip through an MCP server, gives poor traceability of which tools are
  actually exposed, and has no deterministic-variable channel: every argument has to come from the
  model until a `meta`-argument mechanism exists.
- **Separate AHSP + Agent worker** — genuine reuse, but forces every consuming process into a
  single combined diagram, and falls back to an older/deprecated worker-composition pattern.
- **A new gateway/call-activity type** — reuse the AI Agent's existing gateway-tool extensibility
  (the same mechanism MCP and A2A already use) with a process reference as the backing "server."
  Open questions: does every calling process get upgraded automatically when a new toolbox version
  is deployed, and is this non-standard BPMN.

None of these give us, today, a way to say "this element is itself a toolbox — discover its tools
and expose them to me" the way an MCP client element already does generically. And there's no
channel for deterministic (non-LLM) inputs into a gateway-resolved tool at all.

Two structural constraints shape the solution:
- **AHSP config is frozen at sub-process entry** (existing architecture invariant) — a toolbox's
  discovered schema must stay stable for the life of a calling agent's conversation, which argues
  for referencing an explicit process **version**, not "latest" binding.
- **In-process tool refresh can't be standardized** — detecting that a running agent's tools
  changed mid-conversation (e.g. an MCP server's tool list changing) isn't reliably solvable in
  general; version pinning is the practical answer, and the responsibility for a compatible
  upgrade shifts to whoever deploys a new toolbox version.

This isn't only an internal need — customers already ask for reusable, auto-discovered tool sets
within their own cluster, independent of the broader cross-cluster MCP-exposure work in
[camunda/product-hub#3353](https://github.com/camunda/product-hub/issues/3353).

# User Stories

- As an agent process author, I want to reference another deployed process (by process ID,
  optionally pinned to a version) as a single gateway element in my AHSP, so I can reuse a shared
  tool set without redefining it or standing up an MCP server.
- As an agent process author, I want the referenced process's own AHSP tool elements — including
  their `fromAi()`-tagged parameters — to be discovered automatically and exposed to my agent as
  regular tools, the same way MCP/A2A tools are discovered today.
- As an agent process author, I want to pass a fixed set of deterministic variables (tenant ID,
  connection config, feature flags, …) into the toolbox instance via a `meta` argument on the
  gateway element, separate from the LLM-controlled `fromAi()` arguments, and have that argument
  surfaced as a first-class property on the gateway's element template.
- As a toolbox author, I want my toolbox process to be a plain ad-hoc sub-process (start event →
  AHSP → end event) with no AI Agent template applied, so it's discovered as a tool host, not
  treated as an agent itself.
- As a platform engineer, I want the toolbox reference to require an explicit process ID/version,
  so deploying a new toolbox version can't silently change the tool contract of an
  already-running agent conversation.
- As a Camunda engineer evaluating this before productizing it, I want a hacky/local experiment —
  built as a custom connector outside the shipped AI Agent code, with the built-in gateway runtime
  disabled where needed — so we validate discovery, versioning, and meta-arg wiring before
  deciding what (if anything) belongs in the core AI Agent gateway-tool framework.

# Implementation Notes

- Reuse the **Gateway Tool Pattern**
  ([`ai-agent.md` §19](https://github.com/camunda/connectors/blob/17bc53f190c65cbb943a920b40af13e0a13d9afa/connectors/agentic-ai/docs/reference/ai-agent.md#19-gateway-tool-pattern)):
  add a new `GatewayToolHandler` (e.g. `type = "localToolbox"`), detected via the same
  `io.camunda.agenticai.gateway.type` extension property already used for `mcpClient`/`a2aClient`
  ([`TypePropertyBasedGatewayToolDefinitionResolver`](https://github.com/camunda/connectors/blob/17bc53f190c65cbb943a920b40af13e0a13d9afa/connectors/agentic-ai/connector-agentic-ai/src/main/java/io/camunda/connector/agenticai/adhoctoolsschema/schema/TypePropertyBasedGatewayToolDefinitionResolver.java)).
- **Discovery** without an MCP round-trip: resolve the toolbox's schema the way the Task-flavor
  connector already resolves its *own* AHSP — fetch the referenced process's BPMN XML
  (`GET /process-definitions/{key}/xml`, as `ProcessDefinitionAdHocToolElementsResolver` does),
  locate its ad-hoc sub-process, and run the existing `fromAi()` FEEL-parameter extraction
  (`AdHocToolElementParameterExtractor`, `AdHocToolSchemaGeneratorImpl` — see
  [`ai-agent.md` §7](https://github.com/camunda/connectors/blob/17bc53f190c65cbb943a920b40af13e0a13d9afa/connectors/agentic-ai/docs/reference/ai-agent.md#7-tool-resolution))
  over it — the identical machinery already used for the calling agent's own tool elements. This
  turns a `(processId, version)` reference into a `GatewayToolDefinition`, then N
  `ToolDefinition`s once resolved, mirroring the MCP `tools/list` discovery flow (`INITIALIZING` →
  `TOOL_DISCOVERY` → `READY`).
- **Execution**: model the gateway element as a multi-instance activity looping over routed tool
  calls, each instance starting the toolbox process (start event → tool AHSP → end event) and
  folding the result back — the same namespacing `transformToolCalls`/`transformToolCallResults`
  already do for MCP/A2A (LLM sees `LocalToolbox_<elementId>___<toolName>`; the process activates
  the real toolbox element with `{toolCall: {...}}`).
- **Modeling contract**: the referenced process must be start event → tool sub-process (AHSP) →
  end event, and must **not** have the AI Agent Sub-process template applied — it's a plain tool
  host, not an agent.
- **`meta` argument**: a fixed, non-`fromAi()` input on the gateway element's element template,
  mapped straight into the toolbox instance's start-event variables — deterministic config that
  never passes through the LLM. This is the missing piece the Cluster-MCP option lacked.
- **Versioning**: explicit process ID + optional version, resolved once at AHSP entry (consistent
  with the existing "sub-process config frozen at entry" invariant,
  [`ai-agent.md` §17](https://github.com/camunda/connectors/blob/17bc53f190c65cbb943a920b40af13e0a13d9afa/connectors/agentic-ai/docs/reference/ai-agent.md#17-migration)).
  No in-process tool-refresh support in v1 — deliberately out of scope; customers pin versions
  rather than binding to latest.
- **Sequencing**: build first as a local/hacky experiment (custom connector + element template,
  hybrid cluster setup) outside the shipped AI Agent connector; report back on developer
  experience before deciding whether to fold any of it into the core gateway-tool framework for a
  future release (informally floated as a candidate scope for 8.11 — not a commitment).
- **Related work** — this is a lighter-weight, same-cluster complement, not a replacement:
  - [camunda/product-hub#3353 — Exposing Camunda Processes as MCP Tools](https://github.com/camunda/product-hub/issues/3353) —
    the heavier cross-cluster MCP-server direction (targets 8.10-alpha4); Local Toolbox trades
    that scope for immediate, no-server, in-cluster reuse.
  - [camunda/product-hub#2616 — Input/output specification (data contract) for reusable assets](https://github.com/camunda/product-hub/issues/2616) —
    same underlying need for typed contracts on reusable BPMN assets; could eventually replace the
    `fromAi()`-scraping approach used here.
  - [camunda/product-hub#3065 — Process Instance Migration: Support Migration for Ad-Hoc Subprocess Instances](https://github.com/camunda/product-hub/issues/3065) and
    [camunda/product-hub#3069 — [QA-Issue] Migrate ad-hoc subprocess instances](https://github.com/camunda/product-hub/issues/3069) —
    relevant to how version pinning/migration of a toolbox's own process definition should behave
    long-term.
  - [camunda/product-hub#2743 — AI Agent Connector for Ad-Hoc Subprocess](https://github.com/camunda/product-hub/issues/2743) and
    [camunda/product-hub#2779 — AI Agent connector - memory management and tools orchestration](https://github.com/camunda/product-hub/issues/2779) —
    the existing gateway-tool foundation (MCP/A2A) this epic reuses.
  - Reference implementation pointer for the concrete gateway-element wiring pattern to mirror:
    [`ai-agent-chat-with-mcp.bpmn` (AHSP flavor)](https://github.com/camunda/connectors/blob/17bc53f190c65cbb943a920b40af13e0a13d9afa/connectors/agentic-ai/examples/ai-agent/ad-hoc-sub-process/ai-agent-chat-mcp/ai-agent-chat-with-mcp.bpmn) and
    [`ai-agent-service-task-chat-with-mcp.bpmn` (Task flavor)](https://github.com/camunda/connectors/blob/17bc53f190c65cbb943a920b40af13e0a13d9afa/connectors/agentic-ai/examples/ai-agent/service-task/ai-agent-chat-mcp/ai-agent-service-task-chat-with-mcp.bpmn).
