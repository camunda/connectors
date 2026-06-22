# Solution Design — Skills, Virtual Filesystem & Code Execution for the AI Agent

**Status:** Draft / PoC proposal
**Baseline:** current `main` (the conversation-turn-aggregate refactor formerly PR #7527 is now
merged — commit `edf995d`). Naming on main: `AgentConversationTurn` (turn aggregate) and
`AgentConversationTurnInputComposerImpl` (turn input assembly → `CompositionResult`).
**Scope:** Make the AI Agent able to drive *agent skills* that ship scripts/CLIs, by adding
(1) lazily-loaded skills, (2) a virtual filesystem, and (3) code/script execution — all backed by a
pluggable, sandbox-first execution environment.

---

## 1. Problem & goals

We want the AI Agent to be substantially more capable: able to run arbitrary scripts/CLIs, write and
run its own code, read/write files, and drive *skills* that bundle instructions plus scripts — the way
a coding agent works. Four concrete capabilities:

1. **Generic script/CLI execution** — a `bash` tool that runs arbitrary shell commands in the sandbox
   (pipes, redirects, globs — e.g. `curl … | jq`, `grep -rn … .`). This is the primary execution
   primitive; the agent writes a file and runs it via `bash` rather than via a separate code tool.
2. **Virtual filesystem** — the agent can `read` and `write` files that persist across its reasoning
   (search/list are done via `bash` — `grep`/`find`/`ls`).
3. **Lazy skill loading** — an *internal* skill tool (not orchestrated through the ad-hoc sub-process)
   so the model discovers skills cheaply and loads full instructions/scripts only on demand
   (progressive disclosure, à la Anthropic "Agent Skills"). Skill scripts run via `bash`.
4. **Document export (OUT)** — an `export_document` tool turns a workspace artifact (e.g. a generated
   `report.pdf`) into a Camunda **Document** so it escapes the sandbox into the process.

### Hard invariants (from design review)

- **Sandbox-first, always.** Commands, code and skill scripts are **never** executed in the
  connector/JVM process. Execution only ever happens in an isolated sandbox. Two non-cloud
  implementations are allowed: a **local Docker executor** (a real container sandbox running on a
  local/remote Docker daemon — for local development and testing) and an **in-memory fake** (for unit
  tests; simulates the FS, runs nothing). Neither executes inside the connector JVM. There is **no
  connector-side command filtering** — the sandbox boundary is the trust boundary; what `bash` may run
  (and reach on the network) is the sandbox's policy.
- **Bring-your-own-sandbox, optional.** The sandbox provider + credentials are configured on the AI
  Agent config (sealed interface + discriminator, like the existing `Authentication` / memory-backend
  pattern). **If no sandbox is configured, the internal tools (`bash`, `fs_read`, `fs_write`,
  `export_document`, `load_skill`) are simply not registered** and the agent behaves exactly as today.
- **Provider-agnostic.** Target a `SandboxProvider` SPI from day one. The **first concrete adapter is
  Daytona** (locked) — chosen for ease of start: it has an **official Java SDK** (`io.daytona:sdk`,
  Java 11+) and provides durable, **indefinitely-retained** workspace persistence (see §5) that
  satisfies our cross-turn requirement. Daytona is just the first implementation behind the SPI; the
  SPI must cleanly admit further providers (AWS AgentCore, Vercel, E2B) and a local Docker executor.

---

## 2. Status quo (what we build on)

The agent loop (`BaseAgentRequestHandler`) is shared by both flavors:

| Flavor | Entry | Loop |
|---|---|---|
| AI Agent **Task** (`AiAgentFunction`) | service task | explicit, modeled in BPMN |
| AI Agent **Sub-process** (`AiAgentJobWorker`) | ad-hoc sub-process | implicit, one Zeebe job = one LLM turn |

**Crucial finding: every tool today round-trips through the ad-hoc sub-process.** Even MCP and A2A.
The "gateway tool" pattern (`GatewayToolHandler`, `McpClientGatewayToolHandler`) is *not* in-process
execution — it is name-multiplexing + discovery + result-shaping. When the LLM calls
`MCP_server___doThing`, `transformToolCalls()` rewrites it to target the MCP-client **BPMN element**;
the job completes, Zeebe activates that element, the MCP connector runs, and the result returns on the
next job via the `toolCallResults` variable. **There is currently zero in-process tool execution in
the agent loop.** That is exactly the gap these features push against.

Relevant seams already present:

- `AgentConfiguration` (`aiagent/model/AgentConfiguration.java`) — static per-invocation config; new
  sandbox config attaches here.
- `AgentToolsResolver` (`aiagent/agent/AgentToolsResolverImpl.java`) — resolves `ToolDefinition`s for
  the LLM; internal tool definitions are injected here when a sandbox is configured.
- `AgentContext.properties` (`Map<String,Object>`) — already used by gateway tools to carry state
  across turns; the **sandbox handle / FS snapshot reference** lives here.
- `BaseAgentRequestHandler.proceed()` — the single place an LLM call happens and the job completes.

Already in repo: `connectors/aws/aws-bedrock-codeinterpreter` (`CodeInterpreterExecutor`) wraps
AgentCore CI: `start/stop` session, `EXECUTE_CODE` (python/js/ts), `executeCommand` (arbitrary CLI),
`writeFiles/readFiles/listFiles/removeFiles`, returns files as Camunda Documents. The
`software.amazon.awssdk:bedrockagentcore` dependency is present. **A single AgentCore session can back
both the virtual filesystem and code execution; skill scripts are just materialized into that FS.**

---

## 3. Core architectural move: the in-process tool sub-loop

Today `proceed()` does exactly one LLM call, stores, and completes the job. We insert a **bounded
in-process sub-loop** right after the assistant message is produced:

```
proceed():
  conversation = rehydrate(...)
  loop:                                            # NEW sub-loop
    chatResponse = framework.executeChatRequest(window)
    conversation = conversation.ingest(chatResponse)
    toolCalls = chatResponse.assistantMessage().toolCalls()
    if toolCalls are ALL internal (skill/fs/code):
        results = internalToolExecutor.execute(toolCalls, sandboxSession)   # in-process, via SPI
        conversation = conversation.append(ToolCallResultMessage(results))
        continue                                    # re-prompt LLM, never complete the job
    else:                                           # external/modeled tool calls OR final answer
        break
  store + complete job (existing path: activate AHSP elements / return response)
```

Properties:

- **Internal tools never touch Zeebe.** No element activation, no job round-trip, no BPMN plumbing
  exposed to the modeler. Fast (a file read is a sandbox call, not a process round-trip).
- **Mixed turns degrade gracefully.** If the LLM emits *both* internal and external tool calls in one
  turn, we execute the internal ones in-process, then complete the job to dispatch the external ones
  (the external results return next turn as today). Simplest correct rule: *if any external tool call
  is present, finish the sub-loop after executing the internal ones.*
- **Bounded.** A `maxInternalToolIterations` guard mirrors the existing `maxModelCalls`; model-call
  metrics keep accruing across sub-loop iterations (each iteration is a real LLM call).
- **Observable via the conversation transcript.** Internal tool calls + results are appended as
  conversation messages and persisted at job completion, so they're auditable on the agent-instance
  conversation plane (see §8). *(Live per-iteration streaming to the engine — #7450 — is out of scope
  for this PoC.)*
- **Sandbox session is scoped to one invocation.** The session opens at the first internal tool call
  and closes (`AutoCloseable`) when `proceed()` returns — so *within a burst*, full state (including
  `pip install`ed packages, running shells) is intact for free.

### In-process vs gateway tool, and mixing

We deliberately do **not** model the filesystem/sandbox as a gateway tool. A gateway/BPMN tool call is a
full Zeebe round-trip per call (complete job → engine activates element → connector runs → result
marshalled back as a process variable → new job). That is ~hundreds of ms to seconds of engine overhead
*per call*, plus a history/audit record each time. An agent doing `read → edit → write → run → re-read`
issues dozens of these per task; as gateway tools that is pathological, in-process it is a direct
sandbox API call. So in-process wins decisively on performance for chatty FS/exec operations.

**BPMN and in-process tools mix freely within a single run** — this is the core of the sub-loop, not a
limitation. In one assistant turn, internal tools execute in-process; if that same turn *also* contains
external (modeled/gateway) tool calls, we execute the internal ones, then end the burst and hand the
external ones to the AHSP exactly as today. There is no "all in-process or all BPMN" mode for a run.
Note also that code execution is *already* available as a BPMN-modeled tool via the existing
`aws-bedrock-codeinterpreter` connector — a modeler who wants full process observability can wire that
explicitly, while the new in-process path is the fast default. Both coexist.

### Why this also simplifies persistence

A whole skill/file/code "reasoning burst" usually happens inside **one** connector invocation = **one**
sandbox session. Cross-invocation persistence is only needed when an *external* (modeled) tool call
interleaves mid-burst. So:

- **Within an invocation:** nothing to persist — the live session holds everything.
- **Across invocations (only when external tools interleave):** persist via the ladder in §5.

This means the hard "installed packages lost every turn" problem is the *secondary* case, not the
common one.

---

## 4. The `SandboxProvider` SPI

Lowest-common-denominator (lifecycle + filesystem + exec) is mandatory; persistence features are
**probed capabilities**, so the agent loop picks the best strategy per provider.

```java
public interface SandboxProvider {
  String id();                                   // "aws-agentcore", "daytona", "e2b", ...
  Set<SandboxCapability> capabilities();
  SandboxSession create(SandboxSpec spec);       // fresh session (optionally from template/snapshot)
  SandboxSession connect(SandboxHandle handle);  // reattach across invocations by opaque id
}

public enum SandboxCapability {
  FS_PERSIST_ACROSS_CONNECTIONS,   // Daytona, Vercel, E2B, ...
  PAUSE_RESUME_FS_ONLY,            // Daytona stop, Vercel stop
  PAUSE_RESUME_MEMORY_STATE,       // E2B, Fly (<=2GB)
  SNAPSHOT, FORK, CUSTOM_TEMPLATE,
  FILE_SEARCH, STREAMING_EXEC, SELF_HOSTABLE
}

public interface SandboxSession extends AutoCloseable {
  SandboxHandle handle();          // opaque, serializable -> stored in agentContext.properties
  ExecResult exec(ExecRequest req);            // shell command (bash -lc); see ExecRequest below
  SandboxFileSystem fs();
  void terminate();
  default void close() { terminate(); }        // default: tear down; persistent strategies override
}

// Shell-only exec — the agent's `bash` tool maps straight onto this. No ExecKind/CODE mode:
// run_code is gone, so code execution = fs.write(script) + exec("python script.py").
record ExecRequest(String command,            // run as `bash -lc "<command>"` (pipes/redirects/globs)
                   @Nullable String cwd,       // default workspace root
                   @Nullable Map<String,String> env,
                   int timeoutSeconds,         // per-call wall clock
                   long maxOutputBytes) {}     // stdout/stderr cap (truncate beyond)
record ExecResult(int exitCode, String stdout, String stderr,
                  boolean truncated) {}        // stdout/stderr are UTF-8 text; binary detected upstream

public interface SandboxFileSystem {
  // read() returns raw bytes (binary-capable); the fs_read tool renders text + a marker for binary.
  byte[] read(String path);
  FileInfo stat(String path);                  // size + detected contentType + isBinary (for export)
  void write(String path, byte[] content);
  void writeBatch(List<FileEntry> entries);
  // list/search are NOT exposed as LLM tools (the agent uses `ls`/`grep`/`find` via bash); they exist
  // for the connector's own use: skill materialization, workspace inspection, document export.
  List<FileInfo> list(String path);
  void delete(String path, boolean recursive);
  default List<Match> search(String dir, String pattern) { throw new UnsupportedOperationException(); }
}

// Optional capability interfaces, obtained via session.as(Pausable.class) iff capability present:
interface Pausable    { SandboxHandle pause(); boolean preservesMemoryState(); }
interface Snapshotable{ SnapshotRef snapshot(); }
interface Forkable    { SandboxSession fork(); }
interface Templatable { TemplateRef buildTemplate(TemplateSpec spec); }
```

- **`SandboxHandle` is the linchpin** for the distributed loop: `{providerId, sessionId, snapshotRef?}`,
  serialized into `agentContext.properties` between turns; `connect(handle)` reattaches from any
  invocation.
- `exec` is **shell-only** (`bash -lc`) — maps onto AgentCore's `executeCommand`, E2B's
  `commands.run`, Daytona's `process.executeCommand`. Provider-native code-run (`executeCode`/`codeRun`)
  is intentionally *not* in the SPI; revisit only if write+bash proves insufficient.
- Capabilities with no public Java API (e.g. Modal) would sit behind a sidecar adapter — same SPI.

### Module layout (decided)

- **PoC: a `sandbox/` package tree inside `agentic-ai`** (`sandbox/spi`, `sandbox/internaltool`,
  `sandbox/skill`, `sandbox/provider/<name>`) with no reverse dependencies into the agent loop except
  through narrow interfaces, so it lifts out into `agentic-ai-sandbox` (+ per-provider) modules later
  (the codebase split is planned for other reasons too).
- Provider deps stay isolated in `sandbox/provider/<name>` (e.g. the AgentCore baseline pulls
  `bedrockagentcore` there only), so extraction is mechanical.
- The non-cloud impls (`sandbox/provider/docker` local executor, `sandbox/provider/fake` in-memory)
  live behind the same SPI.

---

## 5. Persistence ladder (mapped to providers)

| Tier | Mechanism | Survives installs? | Survives parked-for-days? | Providers |
|---|---|---|---|---|
| 0 Ephemeral | session per invocation, nothing kept | n/a | no | all (single-burst case) |
| 1 FS snapshot → Camunda Docs | tar a workdir to a document, restore next turn | only if deps live in the workdir | **yes** (docs durable) | **AgentCore** (+ universal fallback) |
| 2 Provider snapshot/fork | snapshot whole layer, store `snapshotRef` | **yes** | within retention | Daytona, Vercel, E2B template, Modal |
| 3 Pause/resume (memory) | one long-lived sandbox paused between turns | **yes** (+ processes) | within retention | **E2B**, Fly (<=2GB) |

**PoC persistence — provider-native durable workspace (Daytona).** We rely on **provider-native
persistence** and store only an opaque `SandboxHandle` (the Daytona sandbox id) in
`agentContext.properties`, reconnecting each invocation via `daytona.get(id)`. Daytona persists at the
**filesystem level**, not the memory level: an idle sandbox auto-**stops** (filesystem + installed
`pip`/`apt` packages preserved, RAM cleared, compute billing stops), then after a configurable
interval auto-**archives** the filesystem to cheap object storage. **Auto-delete is off by default**,
so a stopped/archived workspace — and everything on its disk — **persists indefinitely**; reconnect
auto-restarts it. This is exactly what the loop needs: the agent re-issues commands each turn, so only
the *filesystem + installed packages* must survive parking — not running processes. (True RAM/process
hibernation, e.g. E2B, is not required and not used.) For long-parked Camunda processes, configure
auto-stop/auto-archive intervals so the workspace lands in cheap archived storage between turns; set
auto-delete off (default) for indefinite retention.

**Deferred to a follow-up:** the **Tier-1 doc-snapshot fallback** (tar the workspace into a Camunda
document, restore next turn) that makes lower-tier / ephemeral providers (incl. AgentCore for truly
long-parked processes) work. The SPI already carries `exportWorkspace/importWorkspace` so this drops
in without touching the agent loop.

**Cleanup** (any persistent tier): a parked/abandoned process won't reliably signal "done", so we need
(a) provider-side TTL/auto-expiry **and** (b) a reaper tied to process-instance/conversation lifecycle
(e.g. hook `ConversationStore.onJobCompleted` / agent-instance termination). PoC: rely on provider TTL
+ close/pause-on-invocation-end; design the reaper, implement later.

---

## 6. Skill model (lazy loading)

A **skill** = a bundle following the Anthropic Agent Skills convention:

```
my-skill/
  SKILL.md          # YAML frontmatter (name, description) + Markdown body = instructions
  scripts/...       # scripts/CLIs the skill ships
  resources/...     # reference material the model reads on demand
```

**We follow the Agent Skills spec exactly — no Camunda-specific extensions.** Required frontmatter is
`name` + `description`; everything else is optional and `allowed-tools` is the only other (experimental)
field in the spec. **There is no `setup` / dependency-install field in the spec, so we do not support
auto-setup.** ([Agent Skills overview](https://platform.claude.com/docs/en/agents-and-tools/agent-skills/overview),
[anthropics/skills](https://github.com/anthropics/skills))

```yaml
---
name: pdf-tools
description: Extract, merge and fill PDF forms. Use when working with PDF files, forms, or document extraction.
---
<full instructions the model reads only after the skill is loaded>
```

Dependencies are the responsibility of the **sandbox environment**: either pre-installed in the
sandbox image/template, or installed by the agent itself at runtime via `bash` (when the sandbox
permits network/installs). Bundled scripts are run via `bash` — exactly the Anthropic model (scripts
executed via bash; their code never enters the context window).

**Source:** skills are **deployed resources** — each skill is a `.zip` deployed to the cluster
(`newDeployResourceCommand().addResourceBytes(zip, "my-skill.zip")`), so it ships with the process
deployment and inherits **versioning, tenant isolation, and deploy-permission governance**. The AI
Agent config references the skills (by resource id/name); a skill registry resolves a reference → the
deployed resource and fetches its bytes via the Camunda client resource API
(`newResourceGetRequest` → `Resource{resourceId, resourceKey, version, …}`, then binary content
fetch). This parallels how the ad-hoc tools schema already pulls process-definition XML from the
engine. *(Not document storage — that's only used for `export_document` OUT.)*

**Progressive disclosure:**

- **Level 1 (always):** when a sandbox + skills are configured, a `SystemPromptContributor` (mirroring
  the existing `A2aSystemPromptContributor`) **auto-appends the list of `{name, description}`** to the
  system prompt. No `list_skills` tool — this trims a round-trip and matches the spec's "metadata
  always in the system prompt" model.
- **Level 2 (on demand):** `load_skill(name)` materializes the bundle into the sandbox FS (lazily —
  the resource zip is unzipped into the sandbox only when the model decides to use the skill) and
  returns the `SKILL.md` body. It does **not** install deps and does **not** declare extra LLM tools.
- **Level 3 (as needed):** the model reads further bundled files via `fs_read` and runs bundled
  scripts via `bash`.

`load_skill` is an **internal tool** executed in the in-process sub-loop — never an AHSP element.

### Skill access flow (client implementation)

This follows the Agent Skills client-implementation guidance
([agentskills.io](https://agentskills.io/client-implementation/adding-skills-support)) for the
**cloud-hosted / sandboxed agent** case: the model cannot scan a local filesystem, so skills are
provisioned from an external source — here, **deployed resources** in the Camunda cluster. **The model
never touches Camunda; the connector is the bridge, and the model only ever sees the sandbox
filesystem.**

```
Camunda deployment resource (skill .zip)
   │ (1) connector fetches resource bytes via the Camunda client (resourceKey → binary content)
   ▼
Connector JVM (unzip in memory)
   │ (2) fs().writeBatch(...) → /workspace/skills/<name>/SKILL.md, scripts/, references/
   ▼
Sandbox filesystem
   ▲ (3) the model reads/executes via in-process tools: fs_read(...) / bash(...)
   └── the LLM
```

Mapped to the three progressive-disclosure tiers:

- **Tier 1 — Catalog (session start).** For each configured skill the connector fetches the resource
  zip and reads just `SKILL.md`'s frontmatter (`name`/`description`), emitting an `<available_skills>`
  section into the system prompt with `name`, `description`, and the **sandbox `location`** the skill
  will occupy (`/workspace/skills/<name>/SKILL.md`) so the model can resolve relative paths to
  absolute. ~50–100 tokens/skill. (Resources are small; the zip is fetched once and the bytes can be
  reused by `load_skill` — only the *unzip into the sandbox* and the full body are deferred to Level
  2.) Omit the section entirely when no skills are configured.
- **Tier 2 — Activation via `load_skill(name)`.** We use a **dedicated activation tool** rather than
  plain file-read activation *because materialization is lazy* — file-read activation would require
  eagerly unpacking every skill into the sandbox at startup. `load_skill`:
  1. fetches the bundle from Camunda (JVM) and unzips it into `/workspace/skills/<name>/` via
     `fs().writeBatch`,
  2. returns the `SKILL.md` **body** (frontmatter stripped), wrapped in `<skill_content name="...">`
     with the skill directory path and a `<skill_resources>` listing of bundled files (names, not
     contents),
  3. is **idempotent** — if the skill is already materialized and in context, it returns a short
     "already loaded" note instead of re-injecting the body.
  The `name` parameter is constrained to an **enum of valid skill names** to prevent hallucinated
  names.
- **Tier 3 — Resources on demand.** The model reads referenced files (REFERENCE.md, schemas) via
  `fs_read` and runs bundled scripts via `bash` — all against the sandbox FS.
  Their contents never round-trip through the connector or Camunda.

Implementation notes (from the guide):

- **Protect skill content from context eviction.** `MessageWindowFilter` (sliding window, default 20)
  must **not** evict an activated skill's instructions mid-conversation — that silently degrades
  behavior with no visible error. Flag `load_skill` results as protected/pinned so the window filter
  preserves them.
- **Parser leniency.** Extract `name`+`description` tolerantly: warn-but-load on name/length
  mismatches; skip a skill only if the description is missing/empty or the YAML is unparseable; handle
  the common "unquoted colon in description" malformation.
- **Trust.** Skills are arbitrary instructions + code, so trust is governed by *who may deploy
  resources* to the cluster — i.e. the existing deployment-permission model, which is admin/CI-governed
  (a stronger story than ad-hoc uploads; the spec warns explicitly about untrusted skills).
- **Why not file-read activation or catalog-in-tool-description?** Both are valid per the guide; we
  choose system-prompt catalog + dedicated tool to preserve laziness and to attach the structured
  resource listing.

---

## 7. Internal tool families (registered only when a sandbox is configured)

A deliberately **minimal** set (bash is the workhorse; search/list/run-code collapse into it):

| Tool | Maps to SPI | Notes |
|---|---|---|
| `bash(command)` | `exec(ExecRequest)` | Run a shell command via `bash -lc` — pipes/redirects/globs (`curl … \| jq`, `grep -rn … .`). **Stateless per call** (workspace FS persists; shell-local cwd/env/bg do not). Returns `stdout`/`stderr` (UTF-8, truncated to a cap) + `exitCode`; per-call timeout. The primary execution primitive. |
| `fs_read(path)` | `SandboxFileSystem.read` | Returns text; for a binary/oversized file returns a structured marker (size + content-type) — never raw bytes — and points at `export_document`. |
| `fs_write(path, content)` | `SandboxFileSystem.write` | Reliable file write (no shell-escaping pain); creates parent dirs. |
| `export_document(path)` | `fs.read` + `DocumentFactory` | Reads the workspace file's bytes and mints a Camunda **Document**, returning a `DocumentContent` (see §8a). The way binary/large artifacts escape the sandbox into the process. |
| `load_skill(name)` | skill registry + FS | Materializes a skill bundle into the FS, returns its `SKILL.md` body (the skills catalog lives in the system prompt, not a tool). |

**Dropped vs. the original sketch:** `run_code` (→ `fs_write` + `bash`, or `python -c`/heredoc),
`fs_search` (→ `grep`/`find` via `bash`), `fs_list` (→ `ls` via `bash`). The SPI keeps `list`/`search`
for the connector's *own* use only (skill materialization, export).

Registration: `AgentToolsResolver` appends these `ToolDefinition`s iff
`AgentConfiguration.sandbox().isPresent()`. Their names are reserved/namespaced (e.g. `_bash`) and
excluded from migration/gateway handling.

### 7a. Bounding & binary handling
- **Output cap + timeout:** `bash` stdout/stderr are truncated to a configurable `maxOutputBytes`
  (last-N-bytes + truncation marker) and the call has a per-invocation `timeoutSeconds`; synchronous
  only (no background/daemon support in the PoC). Protects the LLM context window and the job.
- **Binary never enters context:** `bash` stdout and `fs_read` are treated as UTF-8 text; binary
  (NUL bytes / invalid UTF-8 / over cap) is detected and returned as a marker
  (`⟨binary, 12 KB, application/pdf — use export_document⟩`). The SPI's `fs.read()` keeps the full
  `byte[]`, and `fs.stat()` carries `size`/`contentType`/`isBinary` — so nothing is lost at the
  connector layer; only the model is shielded.

---

## 8. Observability (process history vs agent instance)

There are **two visibility planes**, and internal sandbox tools behave differently on each.

**BPMN element-activation tree (Operate).** Internal tools execute inside one connector invocation
and never round-trip through Zeebe, so they produce **no element instances** — they are invisible in
the element-activation tree (like a subagent within one job). This is a deliberate change from today,
where each LLM iteration is a separate job and each tool call is an activated element (white-box).
External / modeled / **MCP** / **A2A** tools are unchanged — still visible as element activations. If
an external tool interleaves mid-burst, you still see *that* element, so the loop structure is partly
visible (bursts punctuated by external steps).

**Agent-instance conversation history (the dedicated agent observability surface).** This is where
internal tool calls *are* fully visible — they are **first-class conversation messages**, not a
bespoke trace:

- **In conversation storage:** each sub-loop iteration appends an `AssistantMessage` (carrying the
  LLM `toolCalls` — `id`, `name`, `arguments` = the input) and a `ToolCallResultMessage` (carrying
  `ToolCallResult`s — `id`, `name`, `content` = the output) produced by executing the tool in the
  sandbox. `session.storeMessages()` persists them at the end of `proceed()` via the existing
  in-process / Camunda-document backends. Shape is identical to an AHSP-executed tool call; we tag
  internal ones with metadata (e.g. `executedBy: sandbox`) so a UI can distinguish in-process vs BPMN
  execution.
**Decision (PoC):** *not* a black box. Internal tool calls are white-box on the agent-instance
conversation plane — persisted as messages in the conversation store (transcript) — and invisible only
in the BPMN element tree. The PoC does **not** require any new engine endpoint; the persisted
conversation is the audit record.

**Out of scope for the PoC — live per-iteration streaming (#7450).**
[#7450](https://github.com/camunda/connectors/issues/7450) ("send iteration updates from connector to
engine") extends the agent-instance update endpoint to carry conversation data (messages + tool-call
`name`/`input`/`output`) *after each LLM call*. Once it lands, the sub-loop could push an update per
iteration for live, fine-grained mid-job visibility (and the per-iteration update could double as a
job-lease heartbeat). **We explicitly do not depend on or align with #7450 in this PoC** — it is a
separate workstream (nikonovd, 8.10-alpha3). For now we persist messages at job completion (the
existing path) as the observability record.

**Durability nuance:** because the conversation store is written at job completion, a job that
fails/superseded mid-burst loses that burst's in-process messages locally. Acceptable for the PoC;
incremental persistence (or #7450 streaming) addresses it later.

### 8a. Document export (OUT) — in PoC scope

Binary/large artifacts produced in the sandbox (a generated `report.pdf`, chart, CSV) must be able to
**escape into the process** as Camunda Documents. This is **in scope for the PoC** because the plumbing
already exists — almost no new infrastructure:

- **Trigger: an explicit `export_document(path)` tool** (the agent decides what matters). No
  auto-capture / workspace diffing — that adds heuristics + junk-capture risk for no PoC benefit.
- **Mechanism:** the `InternalToolExecutor` is handed the connector's document factory
  (`Function<DocumentCreationRequest, Document>` / `OutboundConnectorContext.create(...)` — the same
  one `aws-bedrock-codeinterpreter` already uses). `export_document` does
  `fs.read(path)` → `createDocument(DocumentCreationRequest.from(bytes).contentType(stat.contentType).fileName(...))`
  → returns a `DocumentContent` in the tool result.
- **Surfacing:** `DocumentContent` rides the existing `ToolCallResultDocumentExtractor` path, so the
  Document lands in the conversation *and* in the final `AgentResponse` — i.e. it becomes a process
  variable / document the downstream BPMN can consume. Reuse the codeinterpreter size/count guards.
- **Deferred (phase 2):** the **IN** direction — materializing input Camunda Documents into
  `/workspace/input/` so the agent can operate on uploaded files. That needs a new config concept +
  materialization step; OUT does not, which is why only OUT is in the PoC.

---

## 9. Configuration shape

`SandboxConfiguration` is a sealed, discriminated interface modeled identically to
`ProviderConfiguration` and `MemoryStorageConfiguration` — `@JsonTypeInfo(use = Id.NAME, property =
"type")` + `@JsonSubTypes` + `@TemplateDiscriminatorProperty`. It is surfaced in the element
templates as a **Sandbox** group (discriminator bound to the `type` property) with `disabled` as the
default value.

Two subtypes are implemented for the PoC:

```java
// default — tools off, behaves as if no sandbox is configured
@TemplateSubType(id = "disabled", label = "Disabled")
record DisabledSandboxConfiguration() implements SandboxConfiguration {}

// primary PoC provider (locked):
@TemplateSubType(id = "daytona", label = "Daytona")
record DaytonaSandboxConfiguration(
    String apiKey,                          // @NotBlank, redacted in toString
    @Nullable String apiUrl,                // self-hosted base URL
    @Nullable String snapshot,              // optional pre-loaded workspace image
    @Nullable Integer autoStopMinutes,      // 0 = never; provider default 15
    @Nullable Integer autoArchiveMinutes)   // provider default if null
    implements SandboxConfiguration {}
```

Future providers (AgentCore, Vercel, E2B, DockerSandbox for local testing) drop in behind the same
SPI. Added to `AgentConfiguration` as `@Nullable SandboxConfiguration sandbox`;
`sandboxConfiguration()` returns `Optional.empty()` for both `null` and `DisabledSandboxConfiguration`,
and a present Optional only for an active provider — so the existing `isPresent()` guard in the
initializer requires no change. A `skills` list (deployed-resource references) will be added as a
parallel field in a follow-up task.

---

## 10. PoC plan (thin end-to-end)

1. **SPI package**: `SandboxProvider`, `SandboxSession`, `SandboxFileSystem`, `SandboxHandle`,
   `ExecRequest/Result`, `SandboxCapability` + optional interfaces. Plus two non-cloud impls: a
   **local Docker executor** (real container sandbox for local testing) and an **in-memory fake**
   (unit tests; simulates FS, runs nothing).
2. **Daytona provider adapter** (`sandbox/provider/daytona`): implement the SPI over the official
   `io.daytona:sdk` (Java 11+) — `create`/`get(id)` (reconnect), `process.executeCommand`/`codeRun`
   (exec), `fs` (read/write/list/search), `stop`/snapshot. Capabilities advertise
   `FS_PERSIST_ACROSS_CONNECTIONS` + `SNAPSHOT`/`FORK` (no memory-state pause). Persistence = reconnect
   by sandbox id; tune auto-stop/auto-archive. Pin the `0.x` SDK version. Later adapters (AgentCore via
   the existing `CodeInterpreterExecutor`, Vercel, E2B) drop in behind the same SPI.
3. **Internal tool layer**: `InternalToolRegistry` (definitions) + `InternalToolExecutor`
   (dispatch to SPI), and the reserved-name handling.
4. **Sub-loop**: extend `BaseAgentRequestHandler.proceed()` with the bounded in-process loop +
   `maxInternalToolIterations`; session lifecycle (open/`connect` lazily, pause-or-close on return).
5. **Persistence (native)**: store the opaque `SandboxHandle` in `agentContext.properties`; `connect`
   to the same sandbox each invocation; pause on park / resume on next. *(Tier-1 doc-snapshot fallback
   for ephemeral providers is deferred to a follow-up — `exportWorkspace/importWorkspace` seam exists.)*
6. **Skills**: `SKILL.md` parser (spec frontmatter only), a resource-backed `SkillRegistry` (resolve
   reference → deployed resource via the Camunda client), a `SystemPromptContributor` that lists
   `{name, description}`, and the `load_skill` internal tool (materialize bundle → return body). No
   auto-setup, no extra tool declarations.
7. **Config + wiring**: `SandboxConfiguration` in `AgentConfiguration`; conditional tool registration
   in `AgentToolsResolver`; Spring wiring in `AgenticAiConnectorsAutoConfiguration`.
8. **Tests**: unit tests with the in-memory fake / local Docker executor covering the sub-loop, skill
   load, FS tools, and handle reconnect; one e2e with mocked LLM exercising load_skill → bash
   → final answer.

### Out of scope for the PoC (designed, deferred)

- **Live per-iteration engine streaming (#7450)** — separate workstream; PoC persists messages at job
  completion instead (§8).
- SaaS egress / secrets / abuse controls (self-managed/hybrid first).
- Tier-1 doc-snapshot fallback for ephemeral/lower-tier providers (incl. AgentCore for long-parked
  processes) — the `exportWorkspace/importWorkspace` SPI seam is in place for it.
- Additional provider adapters beyond Daytona (AgentCore, Vercel, E2B).
- Full sandbox reaper/GC (rely on provider TTL/auto-archive for now).
- Advanced Modeler UX polish (rich tooltips, conditional visibility refinements) — the `Sandbox`
  group is present and functional in the templates (discriminator `type`, subtypes `disabled` /
  `daytona`); further UX tweaks are a minor follow-up.

---

## 11. Resolved decisions & remaining questions

**Resolved (design review):**
- **Tool surface (minimal):** `bash` + `fs_read` + `fs_write` + `export_document` + `load_skill`.
  `bash` (`bash -lc`, stateless per call) is the primary execution primitive — it replaces `run_code`
  (→ write-file + bash) and `fs_search`/`fs_list` (→ `grep`/`find`/`ls`). `fs_read`/`fs_write` stay
  structured (no shell-escaping pain). SPI keeps `fs.list`/`search` for the connector's own use only.
- **Command safety:** **no** connector-side allow/deny list — the sandbox boundary is the trust
  boundary; what `bash` runs and reaches (network/egress) is the sandbox's policy.
- **bash bounding:** output truncated to a cap + per-call timeout, synchronous only (no background).
- **SPI exec:** shell-only (`ExecRequest{command,cwd?,env?,timeoutSeconds,maxOutputBytes}`); no
  `ExecKind`/native code-run.
- **Document export (OUT) — in PoC scope:** explicit `export_document(path)` tool turns a workspace
  artifact into a Camunda Document via the existing factory + `ToolCallResultDocumentExtractor` (T10).
  Binary never enters context (text + marker); SPI `fs.read` is `byte[]` + `fs.stat` metadata. **IN**
  direction (input docs → FS) deferred to phase 2.
- **Module placement:** package inside `agentic-ai` under `sandbox/*`, dependency-isolated so it lifts
  into `agentic-ai-sandbox` (+ per-provider) modules later (codebase split planned anyway).
- **Shared AgentCore code:** copy/adapt the proven bits for the PoC; extract a shared lib at the split.
- **Skill tools:** instructions only — skills follow the Agent Skills spec and may bundle scripts, but
  `load_skill` declares **no** extra LLM tools.
- **Skill discovery:** auto-appended system-prompt section listing `{name, description}` (no
  `list_skills` tool).
- **Skill setup:** not supported (not in the spec); deps handled by the sandbox image or by the agent
  at runtime.
- **Skill source:** skills are **deployed resources** (a `.zip` per skill), referenced from the agent
  config and fetched via the Camunda client resource API (versioned, tenant-scoped, deploy-governed) —
  *not* document storage (which is only used for `export_document` OUT).
- **Skill access model:** follows the agentskills.io cloud/sandboxed client pattern — connector fetches
  the resource zip and materializes it into the sandbox FS; model reads via `fs_read` / runs via
  `bash`; dedicated `load_skill` tool (enum-constrained, structured result) preserves laziness (§6).
  Two concrete touches to *existing* code: (a) `MessageWindowFilter` must **pin/exempt activated skill
  content** from eviction; (b) the system-prompt composer gains a skills catalog contributor.
- **Primary provider:** **Daytona** (locked) — first concrete adapter behind the SPI, chosen for
  ease of start (official Java SDK) + indefinite filesystem persistence. SPI stays provider-agnostic;
  AgentCore/Vercel/E2B are future adapters.
- **Persistence:** provider-native durable filesystem (Daytona stop/archive, reconnect by id, indefinite
  retention) for the PoC; Tier-1 doc-snapshot fallback deferred (for ephemeral providers like AgentCore).
- **Burst bounding:** iteration cap (`maxInternalToolIterations`), accept longer single-job duration.
- **FS lifetime:** per-conversation durable.
- **Local execution:** local Docker executor for testing (+ in-memory fake for unit tests); never in
  the connector JVM.
- **Observability:** *not* a black box — internal tool calls are first-class conversation messages
  persisted in conversation storage (transcript); white-box on the agent-instance plane, invisible
  only in the BPMN element tree (§8). Live per-iteration engine streaming (#7450) is **out of scope**.

**Remaining / to confirm:**
- **Working-directory contract:** single `/workspace` root (assumed; confirm against Daytona's default
  home dir, e.g. `/home/daytona`).
- **Task flavor:** nice-to-have, not the main target. The sub-loop lives in the shared base, so the
  Task flavor gets it for free *if* its completion semantics line up; if it complicates the PoC, ship
  the Sub-process flavor only.

---

## 12. Work breakdown (PoC tasks)

Each task below is a self-contained, PR-sized unit with explicit dependencies and acceptance criteria.
All live under a new `sandbox/*` package tree in `agentic-ai` (module-extractable later). `#7450`
alignment is **out of scope**.

**Dependency order**

```
T1 ─┬─ T2 ─┬─ T3 ─┬─ T6
    │      │      ├─ T7 ─┐
    │      │      └─ T10 ┴─ T8
    ├─ T4 ─┘
    └─ T5 ──────────── T6
    └─ T9 (optional, parallel)
```
T2 now covers `bash` + `fs_read`/`fs_write`; T7 adds `load_skill`; **T10 adds `export_document`**.
Suggested order: T1 → T2 → T4 → T3 → T5 → T6 → T7 → T10 → T8 (T9 anytime after T1).

### T1 — Sandbox SPI core + in-memory fake
- **Scope:** `sandbox/spi`: `SandboxProvider`, `SandboxSession` (`exec`, `fs`, `handle`, `terminate`,
  `AutoCloseable`), `SandboxFileSystem` (incl. `stat` for size/contentType/isBinary), `SandboxHandle`
  (opaque, serializable), `ExecRequest` (shell `command`, `cwd?`, `env?`, `timeoutSeconds`,
  `maxOutputBytes`) / `ExecResult` (`exitCode`, `stdout`, `stderr`, `truncated`) — **shell-only, no
  `ExecKind`**, `SandboxCapability` + optional capability interfaces
  (`Pausable`/`Snapshotable`/`Forkable`/`Templatable`). Plus `sandbox/provider/fake`: an in-memory
  `SandboxProvider` (simulated FS, exec returns canned/echo output; runs nothing).
- **Depends on:** —
- **Acceptance:** interfaces compile with no provider deps; fake supports create→write→read→list→exec→
  handle round-trip; unit tests on the fake. No changes to the agent loop yet.
- **Size:** S–M.

### T2 — Internal tool layer (bash + fs_read/fs_write)
- **Scope:** `sandbox/internaltool`: `InternalToolRegistry` + `InternalToolExecutor` framework (maps a
  `ToolCall` → `SandboxSession` calls → `ToolCallResult`, errors returned as result content; helper to
  classify a `ToolCall` as internal; reserved/namespaced names). Implements the core tools: **`bash`**
  (→ `exec`, with output truncation to `maxOutputBytes` + binary-stdout marker), **`fs_read`** (text +
  binary marker via `fs.stat`), **`fs_write`**. `load_skill` (T7) and `export_document` (T10) plug in
  their own executors on this framework.
- **Depends on:** T1.
- **Acceptance:** executor dispatches each tool to the right SPI call against the fake; truncation +
  binary-marker behavior covered; reserved names documented; unit tests incl. error mapping (non-zero
  exit, missing file, binary).
- **Size:** M.

### T3 — In-process sub-loop in `BaseAgentRequestHandler.proceed()`
- **Scope:** wrap the `executeChatRequest`→`ingest` step in a bounded loop: if the assistant turn's
  tool calls are **all internal**, execute via `InternalToolExecutor`, append a `ToolCallResultMessage`,
  re-prompt; on any external tool call or final answer, exit and take the existing store/complete path.
  Add `maxInternalToolIterations`; ensure internal tool calls never surface in `AgentResponse.toolCalls`
  and are excluded from gateway/migration handling. Session lifecycle: open/`connect` lazily on first
  internal tool call, `close`/stop on return.
- **Depends on:** T1, T2.
- **Acceptance:** unit tests with fake provider + mocked `AiFrameworkAdapter`: (a) a burst of internal
  tools loops without completing the job; (b) an external tool call ends the burst and dispatches via
  AHSP; (c) a final answer completes normally; (d) iteration cap enforced; (e) messages persisted.
- **Size:** L. *(Highest-risk task — touches the core orchestrator.)*

### T4 — Sandbox configuration + conditional tool registration + wiring
- **Scope:** `SandboxConfiguration` sealed type (with `DaytonaSandbox`) on the execution context /
  `AgentConfiguration`; `AgentToolsResolver` appends internal tool definitions **iff** a sandbox is
  configured; Spring wiring (`SandboxProvider` registry keyed by config type) in
  `AgenticAiConnectorsAutoConfiguration`.
- **Depends on:** T1.
- **Acceptance:** no sandbox config → no internal tools, behavior byte-for-byte unchanged; with config
  → internal tools registered and routed to the configured provider; unit tests for both.
- **Size:** M.

### T5 — Daytona provider adapter
- **Scope:** `sandbox/provider/daytona`: implement `SandboxProvider` over `io.daytona:sdk` (pinned
  `0.x`) — `create(spec)`/`connect(handle)` (= `daytona.get(id)`), `exec` (→ `process.executeCommand`,
  shell-only), `fs` (upload/download/list/search/delete/stat), `terminate`/`stop`, snapshot;
  capabilities `FS_PERSIST_ACROSS_CONNECTIONS` + `SNAPSHOT`/`FORK`; `SandboxHandle` = sandbox id; config
  maps to auto-stop/auto-archive.
- **Depends on:** T1.
- **Acceptance:** `@SlowTest`-tagged integration test (Daytona cloud creds or self-hosted) doing
  create→write→`pip install`→run→read→`get(id)` reconnect (deps still present). Skipped when creds
  absent.
- **Size:** M–L.

### T6 — Cross-invocation persistence
- **Scope:** persist `SandboxHandle` in `agentContext.properties`; reconnect via `connect(handle)` at
  the start of each invocation that needs the sandbox; release/stop policy on completion.
- **Depends on:** T3, T5.
- **Acceptance:** simulated two-invocation test (fake: handle survives in agentContext and reconnect
  returns the same FS state; Daytona `@SlowTest`: files + installed deps persist across reconnect).
- **Size:** S–M.

### T7 — Skills (SKILL.md + registry + catalog + load_skill + window pinning)
- **Scope:** `sandbox/skill`: `SkillMdParser` (lenient frontmatter `name`/`description`; body),
  resource-backed `SkillRegistry` (resolve a config reference → deployed resource via the Camunda
  client `newResourceGetRequest`/search + binary content fetch; the zip is fetched once for the catalog
  and reused), `load_skill` execution (unzip resource → `fs().writeBatch` into `/workspace/skills/<name>/`
  → return structured `<skill_content>` body + `<skill_resources>`), a skills-catalog
  `SystemPromptContributor` (`<available_skills>` with sandbox `location`), and **pin activated skill
  content** in `MessageWindowFilter` so it isn't evicted. Enum-constrained `load_skill` name; idempotent.
- **Depends on:** T2, T3, T4.
- **Acceptance:** unit tests — catalog rendered only when skills configured; `load_skill` materializes
  bundle into fake FS and returns structured body; window filter preserves skill content; parser
  leniency cases.
- **Size:** L.

### T8 — End-to-end test + example skill
- **Scope:** one e2e (mocked LLM, embedded engine, fake or Daytona provider) exercising
  `load_skill` → `bash` → `export_document` → final answer; a sample `SKILL.md` bundle fixture.
- **Depends on:** T7, T10 (and T3/T5).
- **Acceptance:** e2e green; demonstrates all four capabilities end-to-end.
- **Size:** M.

### T9 — Local Docker executor *(optional, parallel)*
- **Scope:** `sandbox/provider/docker`: `SandboxProvider` over a local/remote Docker daemon for local
  testing without a cloud account (real container isolation; no JVM execution).
- **Depends on:** T1.
- **Acceptance:** create→write→exec→read against a container; opt-in (requires Docker); `@SlowTest`.
- **Size:** M.

### T10 — Document export (OUT)
- **Scope:** `export_document(path)` internal tool: thread the connector document factory
  (`Function<DocumentCreationRequest, Document>` / `OutboundConnectorContext.create`) into
  `InternalToolExecutor`; read bytes via `fs.read` + `fs.stat` (contentType), mint a Camunda Document,
  return a `DocumentContent` in the tool result so it rides the existing `ToolCallResultDocumentExtractor`
  path into the conversation and final `AgentResponse`. Reuse codeinterpreter size/count guards.
  (IN direction — input docs → `/workspace/input/` — is explicitly deferred.)
- **Depends on:** T2, T3.
- **Acceptance:** unit test — `export_document` on a workspace file yields a `DocumentContent` that the
  extractor surfaces; size guard enforced; binary `fs_read` marker points the model here.
- **Size:** S–M.

**Suggested delivery order:** T1 → T2 → T4 → T3 → T5 → T6 → T7 → T8 (T9 anytime after T1). T1, T2, T4
are low-risk scaffolding; T3 and T7 are the substantive pieces; T5/T6 prove real persistence.

**Cross-cutting (each task):** unit tests per module conventions (unit-only apart from existing Spring
tests); update `AGENTS.md` / reference docs when a task adds public types or behavioral contracts.
