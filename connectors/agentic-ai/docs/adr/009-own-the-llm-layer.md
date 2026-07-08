# Own the LLM layer

* Deciders: Agentic AI Team
* Date: Jul 6, 2026

## Status

**Proposed**

Tracks epic [#7211](https://github.com/camunda/connectors/issues/7211) and its three sub-epics:
[#7212](https://github.com/camunda/connectors/issues/7212) (native provider implementations),
[#7213](https://github.com/camunda/connectors/issues/7213) (connector types + provider-config restructure),
[#7214](https://github.com/camunda/connectors/issues/7214) (capability matrix + multimodal tool results).

Supersedes the hackdays reference on PR [#7151](https://github.com/camunda/connectors/pull/7151),
which is a proof of concept, not the shape we land.

## Context and Problem Statement

The agentic-ai module reaches every LLM through a single `AiFrameworkAdapter` backed by LangChain4j.
That framework's lowest-common-denominator data model blocks features providers support natively —
multimodal tool results, signed/opaque reasoning continuations, granular prompt caching, cache and
reasoning token attribution, provider-native server-side tools, and lossless wire formats for Claude
on AWS / GCP / Azure. Closing these gaps upstream is bounded by LangChain4j's release cadence, and
several of them (single-content-block assistant messages, flat tool-result content) are structural.

Do we keep building on LangChain4j, or own the provider layer end-to-end while preserving today's
BPMN orchestration core (the distributed agent loop, conversation memory, gateway tools, the
`AgentConversation` turn aggregate)?

## Decision Drivers

* **Native feature access** — reach provider-native capabilities (multimodal tool results, reasoning,
  caching, server-side tools) without waiting on an intermediary framework.
* **Provider fidelity** — a provider-neutral SPI that never leaks vendor vocabulary, but round-trips
  provider-opaque content (reasoning/thinking blocks, signatures, compaction blocks, server-tool
  blocks) losslessly.
* **Capability awareness** — the agent knows each `(model, backend)` tuple's real modalities, limits,
  and feature support at request time.
* **HTTP transport parity** — full control over the HTTP client, especially **outbound proxy
  configuration**, at parity with today (same driver as [ADR 001](001-replace-mcp-client-framework.md)).
* **Preserve the orchestration core** — the request handler, memory, gateway tools, and the ADR-007
  turn aggregate stay intact. This is an LLM-seam replacement, not an orchestration rewrite.
* **No user disruption** — existing element templates and saved configurations keep working unchanged;
  breaking changes land only on new connector types.
* **Extensibility** — a custom-provider SPI, and structural groundwork so follow-ups (reasoning,
  caching, compaction, server-side tools, document flow) attach without breaking changes.

## Considered Options

1. **Extend LangChain4j upstream** and keep building on it.
2. **Build a thin per-provider HTTP layer** ourselves.
3. **Native provider implementations over the official vendor Java SDKs**, behind a provider-neutral
   SPI, with LangChain4j demoted to an opt-in bridge.

## Decision Outcome

Chosen option: **Option 3 — native provider implementations over the vendor SDKs.**

We replace the LangChain4j-backed `AiFrameworkAdapter` with a provider-neutral chat SPI, dispatched
by a registry to native implementations built on the official vendor Java SDKs (Anthropic, OpenAI,
AWS Bedrock, Google GenAI). LangChain4j remains available as an opt-in bridge implementation and as a
customization escape hatch, never regressing coverage during the transition.

The rest of this section states the architecture and the principles that constrain it. Type names are
**indicative** — the ADR fixes roles and contracts, not final identifiers.

### 1. The chat SPI replaces the LLM seam, not the orchestrator

The core request handler keeps owning the BPMN-level job: initialization, memory load, input
composition, limit checks, agent-instance updates, and job completion. The **only** thing that
changes for the request handler is what sits behind its single LLM call. Today:

```java
final var chatResponse = framework.executeMeasuringTime(executionContext, conversation.window(...));
final var updatedConversation = conversation.ingest(chatResponse.assistantMessage(), chatResponse.metrics());
```

The seam contract — `(executionContext, windowed snapshot) → (assistantMessage, per-turn metrics)` —
is preserved. Behind it, a **`ChatModelApiRegistry`** dispatches to a **`ChatModelApi`**
implementation, built by a **`ChatModelApiFactory`**.

**Metrics ingestion stays in the `AgentConversation` aggregate** (ADR 007), via
`conversation.ingest(assistantMessage, metrics)`. The PoC's idea of a `ChatClient` that *owns* the
metric update is explicitly dropped — ADR 007 already owns it. The `ChatModelApi` therefore stays
thin: it performs one provider round-trip and returns `(assistantMessage, per-turn metrics)`; the
request handler ingests the result and owns any multi-round loop (below).

### 2. Provider implementations do exactly one round-trip; our code owns every loop

A single logical assistant turn may require several provider round-trips (e.g. Anthropic
`pause_turn` while a server-side tool runs). **Every round-trip goes through our code**, never a
hidden vendor-SDK auto-loop, so metrics, tracing, and events are emitted per round.

* A `ChatModelApi` performs **one** provider round-trip per `call(...)` and returns the resulting
  `AssistantMessage`. The vendor SDK's tool-runner / auto-continuation loop is **not** used.
* When a provider pauses a turn mid-flight (Anthropic `pause_turn`), the interim assistant message
  is **appended to the conversation as a real turn** and the request handler re-invokes the chat
  model to resume. This is a **turn-based loop owned by the request handler**, not an opaque
  resume-state threaded back through `call(...)`: each round is an ordinary persisted turn with its
  own per-round metrics and events, and the sequence of interim turns is the elaboration of one
  logical assistant response.
* Provider-opaque content produced across those rounds (reasoning/thinking blocks, signatures) rides
  on the message itself (§5) and is re-emitted unchanged on the next round, so no server-side-tool
  vocabulary enters the provider-neutral SPI.

The turn-based loop is a general primitive: the same request-handler-driven re-invocation later
underpins agent-driven compaction ([#7401](https://github.com/camunda/connectors/issues/7401)). This
epic **builds the primitive but ships no server-side-tool feature.**

### 3. `StopReason` is diagnostics, never load-bearing

The continue/stop decision is `!conversation.currentTurn().hasToolCalls()` — derived from the tool
calls, never from a finish reason. A normalized `StopReason` therefore exists for **provider-neutral
diagnostics plus a thin predicate surface only**:

```
STOP | LENGTH | TOOL_USE | CONTENT_FILTERED | GUARDRAIL | ERROR | ABORTED | UNKNOWN
```

* The **raw vendor value is always preserved** in `AssistantMessage.metadata`. Normalization for
  logic, raw string for diagnosability/audit.
* **`UNKNOWN` is the mandatory fallback** — unrecognized vendor values map to it, so adding a provider
  is a mapping change, never a domain-model change.
* **No exhaustive switching on `StopReason`** — control flow keys off `hasToolCalls()`; error handling
  uses a small predicate (is this a terminal model-side error?), not a value-by-value switch. The
  enum is part of the persisted `AssistantMessage` contract, so exhaustive switches would make every
  new value a breaking change.
* `pause_turn` and other mid-turn pauses are handled by the turn-based loop (§2), **never** by
  `StopReason`.
* Ratified taxonomy decisions: keep `GUARDRAIL` distinct from `CONTENT_FILTERED` — an extra value is
  effectively free given the no-exhaustive-switching rule, avoids an information-losing normalization,
  and lets providers that separate the two map faithfully (Bedrock `guardrail_intervened` → `GUARDRAIL`
  vs. `content_filtered` → `CONTENT_FILTERED`). Anthropic `refusal` maps to `CONTENT_FILTERED` (a
  model-side content refusal, not a separate guardrail layer).

### 4. Capability matrix — lean, keyed on `(apiFamily, backend, model)`

A configuration-driven matrix advertises each tuple's capabilities, resolved at request time by a
**cascading, specificity-weighted selector model** (CSS-like) rather than the PoC's whole-entry
longest-match:

* Config is expressed as **layers**, each matching on any subset of dimensions — e.g. all Anthropic
  (`provider=anthropic`), then Sonnet models (`provider=anthropic, model=*sonnet*`), then Sonnet on
  Bedrock (`provider=anthropic, model=*sonnet*, backend=bedrock`).
* Resolution **merges properties across all matching layers** (property-level cascade, not whole-entry
  replacement), so a broad layer sets defaults and narrower layers override individual properties.
* **Specificity = how many/how precisely dimensions match** (more matched dimensions → more weight,
  exact match outweighs glob); the highest-specificity layer wins **per property**. On specificity
  ties, **later-declared wins**. A connector-level override is the highest-specificity layer; bundled
  conservative defaults are the lowest (base) layer.
* **Initial properties are minimal** — only what this epic consumes: input/output modalities (drive
  multimodal tool-result routing), max output tokens, and context-window size where the window seam
  reads it.
* The **schema is designed to grow flags without breaking**; each follow-up adds its own
  (`supports_reasoning`, `supports_prompt_caching`, `supports_server_side_tools`, …). We do **not**
  pre-load flags for features we are not shipping.

### 5. Domain-model extensions (structural groundwork)

Additive, and locked in early because retrofitting them is a breaking change to the persisted memory
format and the message model:

* `AssistantMessage` gains `modelId`, `messageId`, `stopReason`. **Not `usage`** — per-turn token
  usage is already modeled on `AgentConversationTurn` via `AgentMetrics.tokenUsage`, and each round
  of the turn-based loop (§2) is its own turn carrying its own usage; adding usage to the message
  would duplicate it.
* **Opaque-carrying content** — reasoning/thinking blocks, signatures, encrypted reasoning items, and
  compaction-shaped blocks are stored as **provider-opaque payload**, not lossily flattened to text.
  This content must round-trip losslessly through the domain model, **all** conversation stores, and
  the window/snapshot, and be re-emitted unchanged on the next round. This opaque-carrying content is
  what the turn-based loop (§2) replays across rounds; it is the prerequisite for reasoning
  ([#7669](https://github.com/camunda/connectors/issues/7669)), compaction (#7401), and server-side
  tools.
* `ToolCallResult` gains `contentBlocks` (consumed by native multimodal emission and by document flow,
  [#7781](https://github.com/camunda/connectors/issues/7781)).
* `TokenUsage` gains **cache and reasoning token dimensions with documented aggregation semantics**
  (whether `cacheRead` is included-in or separate-from input, `reasoning` in or out of output).
  Captured at the provider-implementation level as each native usage-mapper is written — the SDKs already return them,
  and nailing the semantics once avoids a cross-cutting retrofit. Carried through `AgentMetrics`.

### 6. Provider configuration — restructure internally now, break UX only on new connector types

* **Phase 1 introduces an internal normalized provider model** `(wireFormat, backend, apiFamily)` that
  the native factory dispatches on. Today's existing discriminators map into it via a resolver
  (`bedrock` + Claude model → Anthropic wire format, BEDROCK backend; `azureOpenAi` → OpenAI wire
  format, Foundry backend; `openai` → OpenAI wire format, Completions default; `googleVertexAi` →
  Google wire format, Vertex backend). **The user-facing `ProviderConfiguration` types are not
  restructured, so the generated element templates stay byte-identical — no migration for existing
  users.**
* **The wire-format-first UX and all breaking changes land only on new connector types** ("AI Agent
  Task" / "AI Agent Sub-process") in Phase 3, with backend + apiFamily selectors, consolidated OpenAI,
  `googleGenAi`, Anthropic backends, and the custom-provider SPI. Old connector types become
  **deprecated input-rewrite shims** that delegate to the internal model — largely the resolver from
  Phase 1.
* New opt-in features (reasoning/caching config, server-side tools) surface on the new connector types;
  existing templates stay frozen.

### 7. LangChain4j demoted to an opt-in bridge

The bridge wraps the existing `AiFrameworkAdapter` behind a `ChatModelApi`, covering any provider not
yet native so **coverage never regresses**. LangChain4j stays default-on until native parity is
proven, then becomes opt-in (off by default), and remains a customization escape hatch (user-supplied
`dev.langchain4j.model.chat.ChatModel`). The framework-agnostic-core invariant holds: only
`framework/langchain4j/**` imports `dev.langchain4j.*`.

**Planned:** extract the LangChain4j bridge into a **dedicated module** later, as part of the broader
split of the core domain model into its own module — isolating the `dev.langchain4j.*` dependency at
the module boundary (and enforceable by the future ArchUnit suite, epic
[#7537](https://github.com/camunda/connectors/issues/7537)). Out of scope for this epic; noted so the
package layout chosen here doesn't fight that move.

### 8. HTTP transport / proxy parity is first-class

**Proper outbound-proxy support at parity with today is the hard requirement**; the specific HTTP
client is not. Transport configuration (proxy, timeouts, TLS) is a first-class Phase 1 concern per
provider — not a deferred follow-up. Prefer the JDK `java.net.http.HttpClient` where the SDK supports
it; fall back to another sensible client (e.g. OkHttp) per provider where necessary to get correct
proxy behavior.

### Positive Consequences

* Native provider features become reachable without upstream dependency.
* Provider-opaque content round-trips losslessly, unblocking reasoning, caching, compaction, and
  server-side tools as clean follow-ups.
* Existing users are undisturbed; breaking changes are quarantined to new connector types.
* One turn-based loop serves `pause_turn` and agent-driven compaction.

### Negative Consequences

* Five native wire-format implementations to build and maintain (mitigated by the bridge covering
  gaps and by shared SPI/serialization infrastructure).
* Two provider-config shapes coexist during the deprecation window (legacy templates + new connector
  types), bridged by the input-rewrite shims.
* The engine-side agent-instance history/metrics API cannot represent all native output (see
  [Agent-instance API gap register](#agent-instance-api-gap-register)); we degrade and document rather
  than block.

## Pros and Cons of the Options

### Option 1: Extend LangChain4j upstream

* Good, because no new provider code and known patterns.
* Bad, because gated by upstream release cadence.
* Bad, because several gaps are structural (single-content-block assistant messages, flat tool-result
  content) and unlikely to close without breaking upstream changes.

### Option 2: Thin per-provider HTTP layer

* Good, because full control over the wire.
* Bad, because it re-implements what the vendor SDKs already do well (auth, signing, retries,
  streaming, schema derivation) across four providers.

### Option 3: Native implementations over vendor SDKs (chosen)

* Good, because full control over transport (proxy) and access to native features.
* Good, because the SDKs handle auth/signing/retries/streaming.
* Good, because the bridge lets us migrate provider-by-provider without regression.
* Bad, because more provider code to maintain and a coexistence window.

## Implementation Approach

Side-by-side migration behind the SPI; the bridge guarantees no regression. Phases stay within the
epic's filed structure. Detailed per-phase breakdown, file lists, and test plans live in the
[implementation plan](../adr-009-implementation-plan.md).

* **Phase 0 — domain model + SPI skeleton + bridge cutover.** Additive, behaviour-identical: domain
  extensions (§5), the chat SPI interfaces + registry, LangChain4j wired as the first bridge
  implementation. Element templates untouched. **Includes a de-risking spike** proving opaque-block
  round-trip through the in-process, document, and AWS AgentCore conversation stores.
* **Phase 1 (#7212) — native implementations, all five wire formats.** Anthropic-messages (direct)
  first (unblocks the follow-up server-tool work), then OpenAI (completions + responses), Bedrock
  Converse, Google GenAI, and Anthropic cloud backends (Bedrock / Vertex / Foundry). Dispatched from
  the internal normalized model (§6) mapped from existing discriminators — templates frozen. Lands the
  request-handler-owned turn-based loop with per-round metrics/events (§2), deterministic
  tools/system serialization, HTTP transport/proxy parity (§8), and provider-level token-dimension
  capture (§5). LangChain4j demoted to opt-in once parity is proven.
* **Phase 2 (#7214) — capability matrix + multimodal tool results.** The lean matrix (§4), native
  multimodal emission, and native-vs-fallback tool-result routing. Tool-result rendering **converges
  on the existing `<doc/>` scheme** (`DocumentReferenceXmlTag`), not a competing `<document/>` tag, to
  avoid a reconciliation debt for #7781. Keeps the window/snapshot seam pluggable for compaction.
* **Phase 3 (#7213) — new connector types + config restructure + custom-provider SPI.** Wire-format-
  first templates with backend/apiFamily selectors; old connector types become deprecated shims;
  custom-provider SPI. All breaking UX changes isolated here. Element-template version bump per
  `AGENTS.md`.

### Out of scope (follow-ups this epic prepares for)

* **Reasoning configuration** ([#7669](https://github.com/camunda/connectors/issues/7669)) — config/UX
  deferred; opaque reasoning-content round-trip and reasoning token dimensions are groundwork here.
* **Prompt caching** ([#7668](https://github.com/camunda/connectors/issues/7668)) — config/UX deferred;
  cache token dimensions and deterministic prefix serialization are groundwork here.
* **Memory compaction** ([#7401](https://github.com/camunda/connectors/issues/7401)) — consumes the
  turn-based loop (§2), the pluggable window seam, capability context-window sizes, and opaque
  compaction-block round-trip.
* **Provider-native server-side tools** (Claude code execution + skills) — consumes the turn-based
  loop (§2) and a future `supports_server_side_tools` matrix flag; config surfaces on the new
  connector types.
* **Document flow & awareness** ([#7781](https://github.com/camunda/connectors/issues/7781)) — consumes
  `ToolCallResult.contentBlocks`, native multimodal emission, and the `<doc/>` convergence.

## Agent-instance API gap register

The engine-side agent-instance **history/metrics** API (consumed in the request handler via
`createHistoryForInputMessages` / `createHistoryForAssistantMessage` / `notifyMetrics`) cannot
represent all native-provider output end-to-end — reasoning content, provider-opaque blocks, and the
new token dimensions. Convention for the whole epic:

* **Degrade, never fail** — omit unrepresentable content/metrics from the emitted history/metrics
  (e.g. strip opaque blocks before building the history entry). The job completes; conversation memory
  keeps full fidelity.
* **Mark loudly** — every omission gets a code comment, a log line, and an entry in a living gap
  register (this section, kept current through the epic).
* **Consolidate at the end** — a **requirements summary for the agent-instance history/metrics API** is
  part of the epic's Definition of Done: the concrete list of what that API must support to represent
  native output faithfully. That summary is the handoff to whoever owns that API.

| Gap | Native output not representable | Workaround | Phase surfaced |
|-----|----------------------------------|------------|----------------|
| `ReasoningContent` → history | No dedicated reasoning/opaque history content block; `providerPayload` (signature / encrypted reasoning) has no faithful representation | `AgentInstanceHistoryMapper` maps it to a generic **object** history block (marked with a code comment). Latent in Phase 0 — no driver emits `ReasoningContent` yet (reasoning config deferred to #7669); exercised once a native driver produces it | 0 (code site), 1+ (exercised) |

## References

* Epic [#7211](https://github.com/camunda/connectors/issues/7211) and sub-epics
  [#7212](https://github.com/camunda/connectors/issues/7212),
  [#7213](https://github.com/camunda/connectors/issues/7213),
  [#7214](https://github.com/camunda/connectors/issues/7214).
* Hackdays reference PR [#7151](https://github.com/camunda/connectors/pull/7151).
* [ADR 001 — MCP client framework](001-replace-mcp-client-framework.md) (transport/proxy precedent).
* [ADR 006 — data-driven agent initialization](006-data-driven-agent-initialization.md),
  [ADR 007 — agent conversation turn aggregate](007-agent-conversation-turn-aggregate.md).
* [ADR 004 — document handling in tool call results](004-document-handling-in-tool-call-results.md)
  and PR [#6999](https://github.com/camunda/connectors/pull/6999).
* Implementation plan: [adr-009-implementation-plan.md](../adr-009-implementation-plan.md).
