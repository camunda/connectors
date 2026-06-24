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

The in-process design (PR #7594, §3) chose the sub-loop precisely *because* gateway round-trips are
expensive: an agent doing `read → edit → write → run → re-read` fires dozens of calls, and as gateway
tools each is a full Zeebe round-trip. That is a real latency cost, and we keep it in view as the
explicit risk this PoC measures. But the modeled approach buys things the in-process loop cannot:

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
changes is that those calls are spread across N+1 **jobs** (with N+1 durable records and N+1 round-trip
latencies) instead of one job. So `maxModelCalls` budgeting is unaffected; latency and durability are
the deltas.

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
- The connector executes `CREATE` (see §5): **idempotently** create-or-get the Daytona sandbox by its
  `processInstanceKey` + `agentInstanceKey` labels, unzip the configured skill documents into
  `.agents/skills/`, run the `startupScript` (general bootstrap; may also add skills), scan
  `.agents/skills/`, and return `{handle, catalog:[{name, description, location}], workDir}`.
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

Sandbox tools are **routed by metadata, not by name prefix** — this is the key departure from MCP/A2A:

- `ToolDefinition.metadata` carries `gatewayType=sandbox` (the existing `isSandboxTool()` flag) **and**
  the target `elementId` (stamped at discovery). The handler's `transformToolCalls` routes any
  sandbox-metadata call to that element with `{operation: <BASH|FS_READ|…>, handle, …args}`. **No prefix
  is parsed.**
- The **`sandbox_` name prefix is kept purely as a discoverability convention** — the LLM and humans see
  `sandbox_bash`, `sandbox_fs_read`, … — and a **validation reserves the `sandbox_` namespace** so a
  modeled BPMN tool cannot collide with it.
- **Max one sandbox connector per process**, enforced at discovery (incident on >1, fail fast). This is
  a *direct consequence* of dropping the prefix: without a prefix to disambiguate elements, two
  sandboxes would both expose a tool literally named `sandbox_bash` — an unresolvable name collision.
  Max-1 dissolves it.

MCP and A2A keep their prefix scheme this PR; the broader prefix→metadata migration is a **noted future
follow-up**.

---

## 5. The new sandbox (Daytona) connector

- **Module placement:** a **standalone package within the agentic-ai module** for now (mirrors where
  the MCP Client connector lives), structured so it splits into its own module later (the codebase split
  is planned anyway). Daytona SDK + the `okhttp-jvm` workaround stay in agentic-ai (they already are).
- **Mode:** **AI-Agent-tool mode only** for the PoC. Operation dispatch is structured so a **standalone
  mode** (direct, agent-less invocation) can be added later — deferred because standalone has no agent
  to hold the handle across calls, which is extra surface not core to validating the model.
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
- **Operations** (single `operation` discriminator on the request):

  | Op | LLM tool | Behavior |
  |---|---|---|
  | `CREATE` | — (discovery) | Idempotent create-or-get by label; unzip the skill documents into `.agents/skills/`; run the (general-purpose) `startupScript`; scan `.agents/skills/`; return `{handle, catalog, workDir}`. |
  | `BASH` | `sandbox_bash` | `bash -lc`; truncate stdout/stderr to a cap; binary-stdout marker; per-call timeout. |
  | `FS_READ` | `sandbox_fs_read` | Text, or a binary/oversized marker pointing at export. |
  | `FS_WRITE` | `sandbox_fs_write` | Reliable write (no shell-escaping), creates parent dirs. |
  | `EXPORT_DOCUMENT` | `sandbox_export_document` | Read file bytes → mint a Camunda Document (connector has the factory) → return in the tool result; rides the existing `ToolCallResultDocumentExtractor` path. |
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
- **Labels.** The sandbox is labelled with `processInstanceKey` + `agentInstanceKey`. These power (a)
  idempotent `CREATE` (create-or-get) and (b) the future reaper's "find sandboxes for PI X" query.
- **Teardown = provider TTL only** for the PoC (auto-stop/archive/delete). The **engine-tied reaper**
  (delete on process-end/cancel/conversation-end) is the ⭐ **production blocker** — *more* acute here
  than in the in-process model, because the sandbox now lives for the whole conversation across many
  jobs and the stateless connector cannot reap. The labels are the hook the reaper will use.

---

## 7. Documents

- **Registry stays in core.** `DocumentRegistry`/`DocumentHandle` and the `<doc id/>` rendering are
  ported from PR #7594 unchanged — still needed for stable handles and import.
- **Export** fits *better* here than in-process: minting a Camunda Document belongs in a connector, which
  has the document-creation context natively.
- **Import** is **handler-contract-driven, not the general `fromAi()` feature.** The fixed
  `sandbox_import_document(id, path?)` tool takes an `id` string. The `SandboxGatewayToolHandler`
  special-cases it in `transformToolCalls`: resolve `id` **against the registry** (`findById` → reject
  if absent — §11.6 allow-list, IDOR/SSRF closed, security stays in the agent), then inject the resolved
  **document reference** as a normal activation variable. The connector binds it as a standard `Document`
  (the runtime resolves references to connectors routinely) and writes the bytes. The general §11.7
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

**Possible bonus:** discovery completing before turn 1 may close the frozen-prompt/agent-instance gap
(PR #7594 §14.3) without the deferred engine API, by composing the system prompt at READY with the full
catalog. **To verify** during implementation.

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

- **⭐ Latency** — the explicit thing this PoC measures. Per-call round-trips on chatty FS/exec
  workloads. If it proves prohibitive, a connector-internal batching/loop is the documented (rejected-
  for-now) optimization.
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

### P4 — Document export/import ops + registry port
Port `DocumentRegistry`/`DocumentHandle`/`<doc id/>`. Connector `EXPORT_DOCUMENT` (mint Document) +
`IMPORT_DOCUMENT` (receive resolved `Document`, write FS). Handler import-id→reference resolution.
*Acceptance:* unit tests on export round-trip + import allow-list rejection; registry survives a store
round-trip.

### P5 — Skills: ingestion + catalog + contributor
Port `SkillMdParser`/`SkillBundleReader` into the connector; `CREATE` unzips `List<Document>` into
`.agents/skills/`, scans, returns catalog. Core `SystemPromptContributor` emits `<available_skills>` with
absolute `location`s + the file-read instruction block. *Acceptance:* catalog rendered only when skills
present; parser leniency cases; `fs_read` of a `location` returns the body.

### P6 — Remove the in-process variant from core
Delete the sub-loop, internal-tool framework, SPI/fake, `SandboxSessionFactory`, and the AI Agent
`sandbox`/`skills` config fields. Revert turn semantics. *Acceptance:* full module suite green; no
`SandboxProvider`/internal-tool references remain; AI Agent behaves as today when no sandbox element is
present.

### P7 — Frozen-prompt/agent-instance verification
Verify whether composing at READY (catalog known) closes §14.3. Implement if free; else re-note the
engine dependency. *Acceptance:* agent-instance record reflects the composed prompt, or the gap is
re-documented with the precise blocker.

### P8 — Live e2e (scenarios A–E, new BPMN)
New gateway-topology BPMN; port the `AiAgentSandboxSkillsIT` scenarios + judge assertions. *Acceptance:*
5/5 green against real Daytona + Bedrock.
