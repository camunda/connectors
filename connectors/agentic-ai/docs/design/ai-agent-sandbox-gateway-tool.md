# Solution Design — Sandbox as a BPMN Gateway Tool

**Status:** Draft / PoC proposal
**Baseline:** current `main`.
**Relationship to PR #7594:** This is a *fork of the architecture*, not a continuation. PR #7594
(branch `claude/quirky-cori-ln89rs`) implemented sandboxing as an **in-process concern** of the AI
Agent — a bounded sub-loop inside `BaseAgentRequestHandler.proceed()` that executes `sandbox_*` tools
directly against a `SandboxProvider` SPI, never round-tripping through Zeebe. That PoC works and stays
open as the reference implementation / fallback. **This design inverts the core decision**: the
sandbox becomes a **BPMN gateway tool** (one ad-hoc-sub-process activity, exactly like MCP/A2A), and
every sandbox operation is a Zeebe job round-trip. The goal is to evaluate the modeled approach on the
two axes the in-process loop was weakest on: **durability** and **UX / bring-your-own-sandbox
flexibility**.

> A future *compaction* sub-loop is a separate, unrelated topic and is explicitly out of scope here.

---

## 1. Why invert the model?

The in-process design (PR #7594, §3) chose the sub-loop on the assumption that gateway round-trips are
too expensive for chatty workloads: an agent doing `read → edit → write → run → re-read` fires dozens of
calls, each a full Zeebe round-trip as a gateway tool. In practice a Zeebe round-trip is fast, and the
modeled approach buys things the in-process loop cannot:

- **Durability.** Each tool call is a durable engine record and survives connector/JVM restarts; the
  in-process burst loses its messages if the job fails mid-burst (PR #7594 §8 "durability nuance").
- **Observability.** Every sandbox call is a visible AHSP **element activation** in Operate — white-box
  on the *process* plane, not just the agent-instance transcript.
- **Bring-your-own-sandbox flexibility.** A sandbox is just another connector. Customers can ship their
  own (E2B, AgentCore, Docker, …) by conforming to the gateway *contract* — no change to the AI Agent.
- **Simplicity in the core.** The agent loop reverts to its classic shape (one job = one model call).
  The hardest bug of the in-process model — the **mixed turn** (one assistant message emitting both an
  in-process and an external tool call) — **disappears**, because every tool is now external.

**Cost accounting note:** the modeled approach does *not* increase the model-call count. N internal
tool rounds were already N+1 model calls in the sub-loop; they remain N+1 model calls here. What
changes is that those calls are spread across N+1 **jobs** (N+1 durable records) instead of one job. So
`maxModelCalls` budgeting is unaffected; durability and observability are the deltas.

---

## 2. Architecture overview

```
Ad-hoc sub-process (AI Agent, job-worker flavor)
├── AI Agent (gateway orchestrator)
├── Sandbox connector  ◀── NEW. gateway.type = "sandbox". Exactly one allowed.
│      (Daytona for now; talks to Daytona directly)
├── Download Skill (HTTP)  ── produces skill .zip document(s) → fed to the sandbox connector config
├── …other tools (MCP, A2A, plain BPMN)…
```

Two halves:

1. **A new sandbox connector** (`gateway.type=sandbox`). One BPMN activity. Owns the sandbox: creates
   it, runs skill materialization, executes `bash`/`fs`/document operations against Daytona, and returns
   results. Provider-specific. The blueprint future sandbox connectors copy.
2. **A `SandboxGatewayToolHandler` in agentic-ai core** (mirrors `McpClientGatewayToolHandler`). Knows
   the **fixed sandbox tool contract**, drives discovery, routes LLM tool calls to the sandbox element,
   surfaces the skills catalog into the system prompt, and resolves document handles for import.

The cross-provider abstraction is the **gateway contract** (the fixed `sandbox_*` tool schemas + the
discovery/dispatch variable shapes), **not** a Java SPI. The in-process `SandboxProvider` SPI is dropped
entirely — the connector talks to Daytona directly.

---

## 3. Discovery contract

When the AHSP reaches `TOOL_DISCOVERY` and a `gateway.type=sandbox` element is present, the
`SandboxGatewayToolHandler`:

- **`initiateToolDiscovery`** emits one discovery tool call targeting the sandbox element with
  `{operation: CREATE}`.
- The connector executes `CREATE` (see §5): **always provisions a fresh** Daytona sandbox (no
  create-or-get), labels it **informatively** with `processInstanceKey` + `agentInstanceKey` (the
  `agentInstanceKey` is stamped into the CREATE call from `agentContext.metadata()`; see §6), unzips
  the configured skill documents into `.agents/skills/`, runs the `startupScript` (general bootstrap;
  may also add skills), scans `.agents/skills/`, and returns
  `{handle, catalog:[{name, description, location}], workDir}`. The returned `handle` is the sandbox's
  own id — every later call re-addresses the sandbox by it (the labels never address anything).
- **`handleToolDiscoveryResults`**:
  - injects the **fixed `sandbox_*` tool definitions** into `agentContext.toolDefinitions`, each stamped
    with metadata `{gatewayType: "sandbox", elementId: <sandbox element>, handle, workDir, catalog}`.

> **Implementation note (deviation from the original sketch).** The `GatewayToolHandler` discovery-result
> seam (`handleToolDiscoveryResults`) returns only `List<ToolDefinition>`; the registry persists those via
> `agentContext.withToolDefinitions(...)` and gives handlers **no path to write `agentContext.properties`
> at result time** (MCP/A2A never needed one — they route by parsing the element id out of the tool name,
> and hold no opaque runtime handle). The sandbox's `CREATE` result *is* opaque runtime state. Rather than
> widen the core interface (churning MCP/A2A), we carry **handle + workDir + catalog in the injected
> sandbox tool-def `metadata`** — which is persisted cross-turn just like `properties` would be, and is
> on-thesis with §4's metadata routing. The sandbox **element id** is still tracked in
> `agentContext.properties` (`PROPERTY_SANDBOX`, set at `initiateToolDiscovery`, mirroring `mcpClients`),
> used by `allToolDiscoveryResultsPresent`, result identification, and reconciliation. The handle is a
> short scalar (duplicated across the 5 defs, negligible); the catalog duplication is accepted for the PoC.

The connector does **not** return tool definitions — the tool vocabulary is a fixed contract owned by
core (a sandbox, unlike an MCP server, has a known fixed tool surface). Sandbox creation happens **at
discovery** (not lazily) because the skills catalog must be in the system prompt before the first
conversation turn.

---

## 4. Marking, routing & the max-1 rule

Sandbox tool **dispatch is routed by metadata, not by name prefix** — the key departure from MCP/A2A:

- `ToolDefinition.metadata` carries `gatewayType="sandbox"` (key `ToolDefinition.METADATA_GATEWAY_TYPE`,
  replacing the old `isSandboxTool()` boolean) **and**, per tool, its `operation`
  (`SandboxToolDefinitions.METADATA_OPERATION`) and the target `elementId` (all stamped at discovery).
  The handler's `transformToolCalls` decides "is this mine?" by `gatewayType=="sandbox"` and selects
  the operation **from metadata** — **no prefix is parsed in the dispatch path**, and the per-tool name
  no longer encodes the operation.
- **One name-based seam remains (deliberately):** the shared `GatewayToolHandler.isGatewayManaged(toolName)`
  — used by the registry for document extraction and `elementId` resolution — only receives the tool
  *name*, so the sandbox handler still answers it via the `sandbox_` prefix. Migrating that interface
  seam to metadata would churn MCP/A2A, so it stays a **noted follow-up** (see below).
- The **`sandbox_` name prefix is also a discoverability convention** — the LLM and humans see
  `sandbox_bash`, `sandbox_fs_read`, … — and a **validation reserves the `sandbox_` namespace** so a
  modeled BPMN tool cannot collide with it (this also keeps the residual name-based seam safe).
- **Max one sandbox connector per process**, enforced at discovery (incident on >1, fail fast). Without
  a prefix to disambiguate elements, two sandboxes would both expose a tool literally named
  `sandbox_bash` — an unresolvable collision. Max-1 dissolves it.

MCP and A2A keep their prefix scheme this PR; the broader prefix→metadata migration **of the
`GatewayToolHandler` interface seam** is a **noted future follow-up**.

---

## 5. The new sandbox (Daytona) connector

- **Module placement:** a **standalone package within the agentic-ai module** for now (mirrors where
  the MCP Client connector lives), structured so it splits into its own module later (the codebase split
  is planned anyway). Daytona SDK + the `okhttp-jvm` workaround stay in agentic-ai (they already are).
- **Mode:** a **connector-mode dropdown** (mirroring the MCP Client connector's discriminator) with a
  single **"AI Agent tool"** subtype for the PoC; a **standalone mode** (direct, agent-less invocation)
  can be added later as a second subtype without touching the dispatch path (deferred — standalone has
  no agent to hold the handle across calls). The mode is a visible discriminator so the connector's
  purpose is self-evident in Modeler.
- **Dispatch input:** in AI-Agent-tool mode the per-call properties are **not** surfaced individually
  (the element template can't drive an `operation` dropdown the agent populates). Instead a single
  **hidden** field bound `=toolCall` carries the whole tool call; the connector deserializes it into a
  typed `SandboxToolCall` record using the **document-aware `@ConnectorsObjectMapper`** (so the
  `Document` import payload rehydrates) and switches on its `operation`. The in-flight `handle` from the
  generic gateway seam is read as `sandboxId` on the Daytona side (`@JsonProperty("handle")`).
- **Talks to Daytona directly** — no `SandboxProvider` SPI. Connector unit tests mock the Daytona client.
- **Config** (ports the in-process `DaytonaConnection` onto the connector):
  - `apiKey` (required, redacted), `apiUrl` (self-hosted), `snapshot`,
  - lifecycle `{autoStop, autoArchive, autoDelete}` (each `{mode, duration}`; ISO-8601; auto-archive ≤
    30d; auto-delete DURATION defaults `PT5M`),
  - **`skills: List<Document>`** — fed by the existing "Download Skill" HTTP element (FEEL-resolved);
    the connector unzips these into `.agents/skills/` during `CREATE` (reuses the `SkillBundleReader`
    logic). Keeps the e2e ingestion **identical to today**.
  - **`startupScript`** — an arbitrary shell script run in the sandbox at `CREATE` for **any** workspace
    bootstrap: install OS/language deps (`apt`/`pip`/`npm`), set up tooling, clone repos, configure env,
    pre-seed data, etc. Built **now** — cheap, the connector just `exec`s it. **Populating `.agents/skills/`
    (e.g. `npx -y skills add *`) is one use among many**, complementing — not replacing — the document
    skill path: a deployment can use the documents, the script, both, or neither.
- **Operations** (the `operation` lives inside the typed `SandboxToolCall`, not as a surfaced property):

  | Op | LLM tool | Behavior |
  |---|---|---|
  | `CREATE` | — (discovery) | **Always provisions a fresh sandbox** (no create-or-get); labels it (PI + AI key) informatively; unzip the skill documents into `.agents/skills/`; run the (general-purpose) `startupScript`; scan `.agents/skills/`; return `{handle, catalog, workDir}`. |
  | `BASH` | `sandbox_bash` | `bash -lc`; per-call timeout; output capped/truncated; binary-output marker. **Daytona's toolbox API returns combined stdout+stderr in one stream** — there is no separate `stderr`; the tool description says so. |
  | `FS_READ` | `sandbox_fs_read` | Text, or a binary/oversized marker pointing at export. |
  | `FS_WRITE` | `sandbox_fs_write` | Reliable write (no shell-escaping), creates parent dirs. |
  | `EXPORT_DOCUMENT` | `sandbox_export_document` | Read file bytes → mint a Camunda Document (connector has the factory) → return in the tool result. The handler overrides `extractDocuments` to re-hydrate the `Document` from the result's reference map — the generic `ContentTreeDocumentWalker` can't find `Document`s inside reference maps, so the export path needs this handler-specific walk (a small deviation from "rides the generic extractor path"). |
  | `IMPORT_DOCUMENT` | `sandbox_import_document` | Receives a resolved `Document` (see §7) → write bytes to FS. |

  `fs.list`/`fs.search` are **native Daytona operations** used **connector-internally** (e.g. scanning
  `.agents/skills/`) — **not** exposed as LLM tools (the agent uses `ls`/`grep`/`find` via `bash`).
- **Reconnect & restart:** every per-call activation does `connect(handle)`; Daytona's `get(id)`
  auto-restarts a TTL-stopped sandbox, so a long-parked conversation resumes transparently — the core
  durability win over a connector-JVM session.

---

## 6. Handle lifecycle & teardown

- **Agent holds the handle.** Returned by `CREATE`, persisted in the sandbox tool-def `metadata` (see the
  §3 implementation note — not `agentContext.properties`, because the discovery-result seam cannot write
  properties), injected into **every** sandbox tool-call activation by the handler. Flexible (admits snapshot/fork refs and
  >1 sandbox later); a new pattern vs. self-contained MCP/A2A elements, but the
  `transformToolCalls(AgentContext, …)` seam already receives the context.
- **Labels are informative only.** The sandbox is labelled with `processInstanceKey` +
  `agentInstanceKey`, but the labels **address nothing** — the sandbox is always re-found by the id in
  the handle. `CREATE` therefore **always provisions a fresh sandbox** (no create-or-get-by-label). The
  key reason for using `agentInstanceKey` (not `elementId`) is correctness for the **future reaper**: in
  a multi-instance ad-hoc sub-process the same element id runs for *multiple* agent instances in the
  same process, so `processInstanceKey + elementId` does **not** uniquely identify an agent. The
  `agentInstanceKey` may be absent (agent-instance feature off) — the label is then simply omitted,
  which is harmless since it never addresses anything. Trade-off accepted: a `CREATE` job retried
  mid-discovery can leak a second sandbox until the reaper exists (the labels are its hook).
- **Teardown = provider TTL only** for the PoC (auto-stop/archive/delete). The **engine-tied reaper**
  (delete on process-end/cancel/conversation-end) is the ⭐ **production blocker** — *more* acute here
  than in the in-process model, because the sandbox now lives for the whole conversation across many
  jobs and the stateless connector cannot reap. The labels are the hook the reaper will use.

---

## 7. Documents

- **Registry stays in core.** `DocumentRegistry`/`DocumentHandle`/`DocumentRegistryEntry`, the reworked
  `DocumentReferenceXmlTag` (synthetic `<doc id/>`, raw address dropped), and `DocumentReferenceTagSerializer`
  (registered on the `ToolCallConverterImpl` `ObjectMapper` so any `Document` in a tool-result content tree
  renders as `<doc/>`) are ported from PR #7594 — needed for stable handles and import. The composer's
  `<doc id/>` prefix for **user-prompt** documents is ported too; the **mixed-turn / in-process sub-loop**
  parts of #7594's composer diff (`alreadyAnsweredToolCallIds`/`pendingInputMessages`) are **NOT** ported —
  the gateway model has no in-process sub-loop.
- **Registry lives on the `AgentConversation` aggregate (DP2 resolution).** The registry is conversation-scoped
  state with the conversation's exact lifecycle (loaded with it, grown per turn, stored with it). #7594 carries
  it as a hand-threaded sibling local in `BaseAgentRequestHandler`; we instead make it a **member of
  `AgentConversation`**, with `buildRegistry` (loaded ∪ this-turn input docs) folded into `rehydrate(...)`.
  **Critical invariant:** it is **excluded from `toAgentContext()`** so it never enters the size-limited
  `agentContext` process variable — it is persisted **only** through the conversation store
  (`ConversationStoreRequest`/`ConversationLoadResult`, kept 1:1 with #7594). `AgentResponseHandlerImpl.createResponse`
  then sources the registry from `conversation.documentRegistry()` (no new `createResponse` param). This is a
  deliberate, documented divergence from #7594's structure.
- **Gateway seam carries the registry.** `GatewayToolCallTransformer.transformToolCalls` gains a
  `DocumentRegistry` parameter (handler beans are singletons, so it must ride as a call arg, not handler state);
  MCP/A2A ignore it, sandbox uses it. The unresolvable-id edge case (model hallucinates an id) yields a generic
  connector-side "not found" for now — the rich "available handles" listing would require a gateway short-circuit
  (return a synthetic result without dispatch) and is a flagged follow-up.
- **Export** fits *better* here than in-process: minting a Camunda Document belongs in a connector, which
  has the document-creation context natively.
- **Import** is **handler-contract-driven, not the general `fromAi()` feature.** The fixed
  `sandbox_import_document(id, path?)` tool takes an `id` string. The `SandboxGatewayToolHandler`
  special-cases it in `transformToolCalls`: resolve `id` **against the registry** (`findById` — §11.6
  allow-list, IDOR/SSRF closed, security stays in the agent), then inject the resolved **document
  reference** as a normal activation variable. The connector binds it as a standard `Document` (the
  runtime resolves references to connectors routinely) and writes the bytes. **On an unresolvable id**
  the handler does not short-circuit (the gateway seam transforms calls, it doesn't synthesize results):
  it omits the document and dispatches anyway, and the connector returns `SANDBOX_IMPORT_NO_DOCUMENT` —
  costing one wasted job round-trip on a hallucinated id, with security intact. The general §11.7
  user-declared `fromAi`-document-input mechanism remains a **separate future follow-up**.

---

## 8. Skills (filesystem-based)

Skills move **off the AI Agent config entirely** (both the `sandbox` and `skills` fields are removed
from the agent) and onto the **sandbox connector**, because in the gateway model the agent cannot reach
the FS — the connector materializes and scans it.

- **Source:** `.agents/skills/` in the sandbox workspace (the cross-client convention per the
  [agentskills.io client-implementation guide](https://agentskills.io/client-implementation/adding-skills-support)).
  Populated at `CREATE` from the unzipped `List<Document>` skill bundles and/or by the general-purpose
  `startupScript` (§5) when it chooses to write skills there — either, both, or neither, all writing the
  same dir.
- **Discovery:** the catalog (`{name, description, location}`) is read from `.agents/skills/` during
  `CREATE` and returned in the discovery result; it piggybacks the one discovery round-trip.
- **Catalog → system prompt:** a `SystemPromptContributor` emits `<available_skills>` with each skill's
  **absolute `location`** (now possible — the sandbox exists at discovery, so `workDir` is known, unlike
  the in-process contributor which ran before any session) plus a short **file-read behavioral
  instruction block** ("load the SKILL.md at the listed location via your file-read tool; resolve
  relative paths against the skill directory").
- **Activation = `sandbox_fs_read`.** No dedicated `load_skill` tool. The spec is explicit: file-read
  activation is *"the simplest approach when the model has file access,"* and *"both approaches work in
  practice"* (full file with frontmatter is valid). What we forgo is minor and mostly already-deferred:
  frontmatter stripping (spec: fine), the structured `<skill_resources>` listing (see Tier 3 below),
  enum anti-hallucination (the path comes from the catalog; a bad path is a recoverable file-not-found),
  and compaction-protection tags (window-pinning was already deferred).
- **Bundled scripts / references / assets (Tier 3) — by relative path, not catalog enumeration.** A
  skill bundle is a *directory* (`SKILL.md` + `scripts/`, `references/`, assets); the catalog carries
  only `{name, description, location}`, where `location` is the absolute `SKILL.md` path and its parent
  is the **skill directory**. Per the spec's Tier 3 (*"the model reads referenced files … via fs_read
  and runs bundled scripts via bash"*), the `SKILL.md` body references its resources by **relative
  path**, and the model resolves them against the skill directory — reading them via `sandbox_fs_read`
  and running them via `sandbox_bash`, and `ls`-ing the directory via `bash` to enumerate when needed.
  So no asset locations need to be surfaced in the catalog or the discovery result. The **file-read
  behavioral instruction block must therefore name the skill directory (the parent of `location`) as
  the base for relative paths** — which is exactly the spec's guidance (*"resolve them against the
  skill's directory … and use absolute paths in tool calls"*). The whole bundle is materialized into
  `.agents/skills/<name>/` at `CREATE`, so every referenced file is already present on the FS.

---

## 9. Observability

The sandbox flips from white-box-only-on-the-transcript to **white-box on the process plane too**: every
sandbox call is an AHSP **element activation** in Operate, with a durable record. This is the inverse of
the in-process model (PR #7594 §8), where sandbox calls were invisible in the element tree.

---

## 10. Migration surface (what changes in core)

**Removed from agentic-ai:**
- the in-process sub-loop in `BaseAgentRequestHandler.proceed()` (+ mixed-turn machinery in
  `TurnReconstructor` / `AgentConversationTurnInputComposerImpl`, `maxInternalToolIterations`),
- the internal-tool framework (`InternalToolRegistry` / `InternalToolExecutor` / the `*ToolHandler`s),
- the `SandboxProvider` SPI + in-memory fake + `SandboxSessionFactory`,
- the AI Agent's `sandbox` **and** `skills` config fields.
- Turn semantics revert to classic (one job = one model call).

**Kept / ported from PR #7594:** `DocumentRegistry` / `DocumentHandle` / `<doc id/>` rendering; the
Daytona client integration logic (re-homed into the connector, SPI stripped); `SkillMdParser` /
`SkillBundleReader` (re-homed into the connector); the bash truncation / binary-marker / export
size-guard logic (ported into the connector ops).

**New in agentic-ai:** `SandboxGatewayToolHandler` + the fixed tool-schema contract + the catalog
contributor + max-1 enforcement + `sandbox_` namespace-reservation validation + the new sandbox
connector package.

**Possible bonus — VERIFIED, NOT free (P7).** The gateway model does **not** close the §14.3
agent-instance/system-prompt gap for free. Root cause confirmed on this branch:
`AgentInitializerImpl.provisionAgentInstance` calls `agentInstanceClient.create()` at the
**INITIALIZING** stage — the very first job, *before* gateway discovery runs — and
`CamundaAgentInstanceClient.executeCreate` records the **raw** `configuration.systemPrompt().prompt()`.
The catalog only becomes known at the **READY** transition (`completeToolDiscovery`, a later job, after
the sandbox `CREATE` round-trip), so `create()` cannot see it. If anything the gateway model makes the
catalog available *later* than #7594 (which could resolve it in-JVM at init); the `<available_skills>`
block still reaches the model correctly (it is composed in `proceed()` at the first READY turn from the
catalog metadata on the persisted tool defs), but the agent-instance observability record keeps the raw
prompt. Reflecting the composed prompt there needs one of:
- **(a)** engine support — add `systemPrompt` to `UpdateAgentInstance` (the `UpdateAgentInstanceCommandStep2`
  builder today carries only status + `modelCalls`/`inputTokens`/`outputTokens`/`toolCalls`), then
  compose-at-READY and push via `update()`. This is the same cross-team dependency #7594 identified.
- **(b)** defer `agentInstanceClient.create()` from INITIALIZING to the READY transition and compose the
  prompt in the initializer so `create()` records it directly — avoids the engine dependency, but is a
  structural change (move composition into `AgentInitializer`) **and conflicts with labelling the sandbox
  with `agentInstanceKey` at `CREATE`** (decision §6 / the planned reaper hook): the sandbox `CREATE` runs
  during TOOL_DISCOVERY, *before* READY, so the key would not yet exist.

Neither is free, so this stays a documented follow-up (engine dependency unchanged). Not implemented in
this PoC.

---

## 11. e2e strategy

Drive the **same scenarios A–E** (bash compute, export, import, skill round-trip, catalog) with the
**same LLM-judge assertions** as `AiAgentSandboxSkillsIT`, against a **new BPMN** that wires the gateway
topology (the e2e already uses the **job-worker / ad-hoc-sub-process flavor** —
`io.camunda.agenticai:aiagent-job-worker:1` — so the topology is correct; we add the sandbox gateway
element inside the AHSP, keep the Download-Skill HTTP element but feed its output to the sandbox
connector, and remove the sandbox config from the AI Agent element). Proves behavioral parity with the
in-process PoC. Gated `@EnabledIfEnvironmentVariable` on `DAYTONA_API_KEY` + `AWS_BEDROCK_ACCESS_KEY`.

---

## 12. Risks & open follow-ups

- **⭐ Sandbox reaper** — engine-tied teardown; production blocker (§6).
- **Standalone connector mode** — deferred (§5).
- **Prefix→metadata migration for MCP/A2A** — deferred (§4).
- **General §11.7 `fromAi()` document inputs** — separate follow-up (§7).
- **Frozen-prompt/agent-instance gap** — verify the possible free fix (§10).

---

## 13. Phased task plan (dependency-ordered)

```
P1 ─ P2 ─┬─ P3 ─ P4 ─┬─ P7 ─ P8
         └─ P5       │
              P6 ────┘
```

### P1 — Gateway contract + tool schemas (core)
The fixed `sandbox_*` tool-definition schemas; the `{gatewayType, elementId}` metadata convention;
`sandbox_` namespace-reservation validation. *Acceptance:* schemas compile; validation rejects a modeled
`sandbox_*` tool. No behavior change yet.

### P2 — `SandboxGatewayToolHandler` (core)
Mirror `McpClientGatewayToolHandler`: `initiateToolDiscovery` (emit `CREATE`), `handleToolDiscoveryResults`
(store handle + catalog, inject tool defs), `transformToolCalls` (metadata routing, per-op payload,
import-id resolution), `transformToolCallResults`. Max-1 enforcement at discovery. *Acceptance:* unit
tests on discovery init, result handling, routing, import resolution, max-1 incident.

### P3 — Sandbox connector skeleton + Daytona `CREATE`/`BASH`/`FS_*`
New connector package: `@OutboundConnector`, element template with `gateway.type=sandbox`, request/response
+ `operation` discriminator, ported `DaytonaConnection` config, direct Daytona client. Implement
`CREATE` (idempotent by label), `BASH`, `FS_READ`, `FS_WRITE`. *Acceptance:* gated Daytona IT —
create→write→bash→read→reconnect; unit tests mock the Daytona client.

### P4 — Document export/import ops + registry port — ✅ DONE
Commits: P4a/P4b `b733203515`, P4c `15779c6596`, P4d `41c84e2da5`, P4e `23b1790251`. Full
agentic-ai unit suite green (1498 tests). Full Zeebe round-trip for EXPORT deferred to P8.
Dependency-ordered sub-steps:
- **P4a — Model port (1:1 from #7594, no entanglement):** `DocumentHandle`, `DocumentRegistry`,
  `DocumentRegistryEntry`, and the reworked `DocumentReferenceXmlTag` (replaces main's old sealed-interface
  scheme). Pure copies + their unit tests.
- **P4b — Serializer wiring:** port `DocumentReferenceTagSerializer`; register it on the `ToolCallConverterImpl`
  `ObjectMapper` (`SimpleModule().addSerializer(Document.class, …)`). Port the composer's user-prompt `<doc id/>`
  prefix **only** (skip the mixed-turn/sub-loop diff). Fix the one old call site
  (`AgentConversationTurnInputComposerImpl:255`).
- **P4c — Registry onto the aggregate (DP2):** add `documentRegistry` to `AgentConversation`; fold `buildRegistry`
  into `rehydrate`; exclude from `toAgentContext`; wire `ConversationLoadResult`/`ConversationStoreRequest` (1:1
  with #7594) + the in-process & camunda-document stores to load/persist it.
- **P4d — Gateway seam:** add `DocumentRegistry` param to `transformToolCalls` (transformer + registry impl +
  MCP/A2A/sandbox handlers); `createResponse` sources it from `conversation.documentRegistry()`. Implement
  `SandboxGatewayToolHandler` import-id→reference resolution (replace the P2 `TODO(P4)`); generic not-found on miss.
- **P4e — Connector ops:** `SandboxDaytonaFunction` `EXPORT_DOCUMENT` (mint Document via `DocumentFactory`, return
  in tool-result content) + `IMPORT_DOCUMENT` (bind resolved `Document` reference, write bytes to FS). Add the
  import document-reference field to `SandboxDaytonaRequest` + element-template binding.

*Acceptance:* unit tests on export round-trip + import allow-list rejection; registry survives a store
round-trip; existing converter/composer unit tests stay green.

### P5 — Skills: ingestion + catalog + contributor — ✅ DONE
Commits: P5a `57d80dd0b8` (connector), P5b `3ffb3858f9` (core). Affected sandbox unit suite green (98 tests).
- **P5a — Connector (skill materialization + FS catalog scan):** ported `Skill`/`SkillMdParser`/
  `SkillBundleReader`/`SkillResolver`/`InvalidSkillException` into the connector `sandbox/skill/` package
  (trimmed `SkillResolver`'s `load_skill`/in-process-only API: `resolveByName`/`resolveMetadata`/
  `SkillMetadata`). Added `skills: List<Document>` + `startupScript: String` config to the sandbox
  element. `CREATE` now: `mkdir -p <workDir>/.agents/skills` → materialize each bundle into
  `.agents/skills/<name>/<relativePath>` → run `startupScript` (warn on non-zero, don't fail) → scan
  `find .agents/skills -maxdepth 2 -name SKILL.md` and parse each for `{name, description, location}`.
  Catalog returned in `SandboxCreateResult`. Regenerated templates; declared `jackson-dataformat-yaml`.
- **P5b — Core (`SandboxSkillsSystemPromptContributor`):** reads the catalog from the sandbox tool-def
  metadata (`METADATA_CATALOG`) in `AgentContext`, emits `<available_skills>` with each skill's
  name/description/absolute `location`/skill `directory` + the file-read instruction block. Activation
  is `sandbox_fs_read` of the `location` (no `load_skill` tool). Robust to both in-memory
  (`List<SkillCatalogEntry>`) and JSON-round-tripped (`List<Map>`) catalog forms. Registered (order 90,
  before A2A) in `SandboxDiscoveryConfiguration`.

*Acceptance:* catalog rendered only when skills present (null otherwise); parser leniency cases; the
contributor handles the serialized metadata form; CREATE materializes bundles + scans the FS catalog.
The full `fs_read`-of-`location` round-trip is validated end-to-end in P8.

### P6 — Remove the in-process variant from core — ✅ DONE (no-op)
Verified nothing to remove: this branch was cut from `main`, which never carried the in-process PoC.
No `SandboxProvider`/`SandboxSession`/`InternalTool*` sources exist; the AI Agent request has no
`sandbox`/`skills` config fields; `BaseAgentRequestHandler` has no sub-loop, `maxInternalToolIterations`,
or mixed-turn machinery. Turn semantics are already classic. The migration surface in §10 applies only
to PR #7594 (the in-process reference), not to this branch.

### P7 — Frozen-prompt/agent-instance verification — ✅ DONE (verified; gap persists, not free)
**Finding:** the gap is **not** closed for free; the engine dependency is unchanged. Evidence (this
branch): `agentInstanceClient.create()` runs at INITIALIZING (`AgentInitializerImpl.provisionAgentInstance`),
before discovery, and records the raw `configuration.systemPrompt().prompt()`; the catalog is known only
at READY (`completeToolDiscovery`), a later job. `UpdateAgentInstanceCommandStep2` has no `systemPrompt`
setter (status + metric deltas only). The `<available_skills>` block still reaches the model correctly
(composed in `proceed()` at the first READY turn from the persisted tool-def catalog metadata) — only the
agent-instance observability record is stale. Two non-free fixes documented in §10: (a) add `systemPrompt`
to `UpdateAgentInstance` (cross-team engine dep, same as #7594) + compose-at-READY-and-update; (b) defer
`create()` to READY (conflicts with `agentInstanceKey`→sandbox-`CREATE` labelling). No code change. See §10.

### P8 — Live e2e (scenarios A–E, new BPMN) — ✅ DONE
New gateway-topology BPMN (`agentic-ai-sandbox-gateway-chat.bpmn`) + `AiAgentSandboxGatewayIT`, gated on
`DAYTONA_API_KEY` + `AWS_BEDROCK_ACCESS_KEY`, judge-asserted. Skills are not configured on the AI Agent:
a `Download_Skills` HTTP connector (multi-instance over `skillDownloadUrls`) turns each skill `.zip` into a
Camunda document, which the sandbox gateway connector materializes into `.agents/skills/` at `CREATE`; the
sandbox id for teardown is read from `agentContext.properties.sandbox.handle`. **5/5 green** against real
Daytona + Bedrock (111s).

**Bug found & fixed (the EXPORT round-trip P4 deferred):** `SandboxDaytonaFunction.EXPORT_DOCUMENT`
hardcoded `application/octet-stream` for every exported file. The minted document is re-inlined as LLM
content on the next turn, where `DocumentToContentConverter` rejects the opaque type
(`DocumentConversionException`) → `AD_HOC_SUB_PROCESS_NO_RETRIES` incident → the loop stalls (300s timeout).
Fix: infer the content type from the file extension, mapped to the framework-accepted types (`text/*`,
JSON/XML/YAML, PDF, images), default octet-stream. The in-process PoC avoided this by using the Daytona
FS-provided content type; the gateway `fsRead` returns only bytes, so the type must be inferred.

---

## 14. Revisions after design review (post-P7)

A review of the implemented PoC against this design surfaced three substantive deviations, since
corrected in code (this design has been updated in place to match):

- **R1 — Metadata routing (`§4`).** Dispatch now routes by `gatewayType="sandbox"` + per-tool
  `operation` metadata instead of the `sandbox_` name prefix; the old `isSandboxTool()` boolean is gone.
  The shared `GatewayToolHandler.isGatewayManaged(toolName)` interface seam (registry-level: document
  extraction, `elementId` resolution) intentionally stays name-based — migrating it would churn MCP/A2A
  (a noted follow-up, §12). Also corrected the `sandbox_bash` description to promise **combined**
  stdout/stderr (Daytona has no separate stderr stream — confirmed identical in the in-process PoC).
- **R2 — Connector input model (`§5`).** Replaced the six surfaced per-call properties (incl. a dead
  `operation` dropdown the template couldn't drive) with a connector-mode dropdown (single "AI Agent
  tool" subtype) + a single hidden `=toolCall` field parsed internally into a typed `SandboxToolCall`
  via the document-aware `@ConnectorsObjectMapper`.
- **R3 — Sandbox identity (`§3`, `§6`).** Labels are now `processInstanceKey` + `agentInstanceKey`
  (informative only); the sandbox is addressed by the returned handle/id, `CREATE` always provisions a
  fresh sandbox (no create-or-get). Fixes the multi-instance-AHSP uniqueness bug (`elementId` is not
  unique per agent instance). On the Daytona side the in-flight `handle` is read as `sandboxId`.

Three low-severity items were resolved as documentation only (no behavior change): the `EXPORT_DOCUMENT`
`extractDocuments` override (§5), the import unresolvable-id round-trip (§7), and the skill-name
directory fallback (§8, plus a regression test).
