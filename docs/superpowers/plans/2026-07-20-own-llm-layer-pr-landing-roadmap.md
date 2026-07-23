# Own the LLM Layer — PR Landing Roadmap

**Status:** decomposition agreed 2026-07-20. This is the epic-level split of the #7211 vertical pilot into
independently-mergeable PRs. Each PR gets its own detailed implementation plan (via `writing-plans`) when we
start it.

> **This document is working scaffolding on the pilot branch and NEVER travels to a PR.** The pilot branch
> `agentic-ai/issue-7211-vertical-pilot` is a reference/role-model only and is never merged.

## Way of working

- One **new branch off `main`** per PR below.
- We iterate on review until it's good, then open the PR.
- Once merged, we start the next part; repeat until everything needed is implemented.
- **Clean PR history:** coherent commits describing the actual change. NO superpowers docs, NO references to
  the pilot or its `C*` steps, NO review/crit/round/task wording in commit messages.
- **ADRs replace superpowers docs** in what ships. Planning docs stay on the pilot branch only.
- No golden persisted-state fixtures exist; the existing e2e suite is the behavior-identity net.

## Dependency DAG

```
PR1 ──▶ PR2 ──▶ PR3 ──┬──▶ PR4 (Anthropic)
                      └──▶ PR5 (OpenAI)
```

PR1→PR2→PR3 is a strict stack. PR4 and PR5 both stack on PR3 and are independent of each other (can be
open/reviewed in parallel).

---

## PR1 — Generic SPI + message-model reshape (v1 / Langchain4J only, behavior-identical)

**Replaces** the earlier PR #7847.

**Scope:**
- Package moves: remove the "framework" term; separate the generic execution layer from provider-specific
  code (`aiagent.chatmodel` = SPI at root; concrete providers nested under `aiagent.chatmodel.provider.*`;
  `capabilities`/`multimodal`/`transport` elevated to `aiagent`).
- `ChatModelApi` SPI: `AutoCloseable`, registry routing via `supports()`, per-provider Langchain4J factories.
- `StopReason` sealed hierarchy.
- Turn-based continuation support (the `Completed | Continuation` loop).
- Content-block message model: `ProviderContent` + `ReasoningContent` blocks; **`ToolCallResult` content
  `Object` → `List<Content>` (Layer-1 container change, as on the pilot branch)**, populated exactly as
  ADR-004 renders today (serialized result as `ObjectContent`/`TextContent` **+** the separate synthetic
  `<doc/>` `UserMessage` kept). **Behavior identical.** (Decided — not gated: the capability-driven tool-call
  result routing lands as a follow-up after this ships, and needs the list shape.)
- Cache + reasoning-token metrics: `AgentMetrics.TokenUsage` gains cache/reasoning fields **and** the
  per-provider L4J factories map them from LangChain4J's own `TokenUsage` subclasses — **live for 4 of 6
  providers** (Anthropic/Bedrock/OpenAI/OpenAI-compatible); Azure/Vertex stay at zero as today. (Not "dormant".)
- `ChatModelApiConfiguration`; the existing `model.request.provider.ProviderConfiguration` implements it
  directly (no wrapper). **Name and package unchanged in PR1** — the `.provider` → `.v1` package rename and the
  `ProviderConfiguration` → `V1ProviderConfiguration` type rename are **deferred to PR3**, where v2 gives the
  split meaning.

**Explicitly NOT in scope:** any capability-driven placement, doc-lifting-inline, or
`CapabilityAwareToolCallResultStrategy`. Documents in tool results keep the ADR-004 synthetic-user-message
behavior verbatim.

**Commit sequence (move-first) — 5 commits.** (Original 7-step sketch collapsed after discovery: the SPI +
factories + config are coupled — the old and new SPI cross-reference through the factory layer, and the
`V1ChatModelApiConfiguration` wrapper the pilot deletes never existed on main — so they land as one atomic
reshape commit. Each commit compiles and its gating tests pass on its own.)
1. **Package relocation** — `framework/*` (`AiFrameworkAdapter`, `AiFrameworkChatResponse`, `langchain4j/*`)
   → `chatmodel/*` + `chatmodel/provider/langchain4j/*`. Pure moves, types keep their names, `framework`
   package removed. **`ChatModelHttpProxySupport` is a pure move — the `HttpTransportSupport` extraction (and
   `transport` package) is deferred to PR4/PR5**, where the native OkHttp clients first need it.
2. **New content blocks** — `ReasoningContent` + `ProviderContent` under `model/message/content/` + sealed
   permits + `@JsonSubTypes` + the two forced switch arms (`AgentInstanceHistoryMapper`, L4J
   `ContentConverterImpl`). Additive, no v1 producer.
3. **Tool-call-result shape** — `ToolCallResultMessage.results` `List<ToolCallResult>` →
   `List<ToolCallResultContent>` (`Object` content → `List<Content>`) + an 8.9-BC Jackson deserializer +
   composer split (ADR-004 synthetic `<doc/>` message preserved) + L4J `ToolCallConverterImpl` + AWS-AgentCore
   mapper + history-mapper retypes. (`MessageWindowFilter`/`TurnReconstructor` verified NOT touchpoints.)
4. **`ChatModelApi` SPI reshape** (atomic; was 4+5+7) — `ChatModelApi` (`AutoCloseable`) + `ChatModelRequest` +
   `ChatModelResult` (`Completed | Continuation`) + `StopReason` sealed + `ChatModelApiRegistry(Impl)` (registry
   bean in `AgenticAiConnectorsAutoConfiguration`) + reshaped `Langchain4JChatModelApi` + abstract
   `Langchain4JChatModelApiFactory<T>` + 6 concrete factories + `ChatModelApiConfiguration` marker +
   `ProviderConfiguration` implements it directly (no wrapper) + `BaseAgentRequestHandler` continuation loop +
   delete the old `AiFrameworkAdapter`/`ChatModelFactory`/`ChatModelProvider(Registry)` layer. `ChatModelApi`
   has **no `capabilities()`** in PR1 (that + `ModelCapabilities` are PR2). **Content-filter behavior preserved
   but elevated to the generic loop:** `ChatMessageConverterImpl` maps `FinishReason.CONTENT_FILTER` →
   `StopReason.CONTENT_FILTERED`, and `BaseAgentRequestHandler` throws `ERROR_CODE_MODEL_RESPONSE_CONTENT_FILTERED`
   on that stop reason before ingest — vendor-neutral, so every future provider inherits the guard.
5. **Cache + reasoning-token metrics** — `AgentMetrics.TokenUsage` gains `cacheReadTokenCount` /
   `cacheCreationTokenCount` / `reasoningTokenCount` (`@JsonInclude(NON_DEFAULT)`) + the 4 provider factories'
   `mapTokenUsage` overrides reading L4J's `AnthropicTokenUsage`/`BedrockTokenUsage`/`OpenAiTokenUsage` detail.

**Technique:** use the JetBrains MCP move / `rename_refactoring` so references update automatically rather than
hand-editing ~70 referencing files; keep the staged diff rename-clean (git rename detection preserves
history/blame; `git mv` where a move isn't driven through the IDE). Requires the PR1 worktree open + indexed in
IntelliJ at implementation time; fallback is `git mv` + compiler-driven import fixes.

**Load-bearing "identity" work** (what makes "e2e green, zero test change" actually true): the
`Object → List<Content>` shape must round-trip byte-identically through conversation-variable serialization,
the L4J `ChatMessageConverter`, `MessageWindowFilter`, `AgentInstanceHistoryMapper`, and `TurnReconstructor`.

**Exit criteria:** full existing e2e suite green with no test changes.

**ADR:** overarching "Own the LLM layer" ADR (SPI + continuation + content-block message model), framing it as
the successor to the prior ADR-005/009 direction.

---

## PR2 — Generic `ModelCapabilities` (v1-wired, mostly dormant)

**Scope:**
- `ModelCapabilities` interface + capability matrix resolver (fixed-hierarchy deep-merge cascade:
  family defaults → agnostic → backend-specific).
- Only the capabilities native providers will consume: reasoning/thinking, effort, prompt caching, tool flags.
- Wired for v1 (L4J) — dormant, no behavior change.

**Explicitly NOT in scope:** `toolResultModalities()` and the capability-aware tool-result routing strategy —
both deferred to the tool-result persist-redesign follow-up, where they get a real consumer. Do not ship a
dead accessor.

**ADR:** model capability matrix.

---

## PR3 — v2 job workers + element templates + config types (no provider behavior)

**Scope:**
- v2 entry points (`AiAgentSubProcessV2Function` / `AiAgentTaskV2Function`).
- v2 element templates + wiring.
- `V2ProviderConfiguration` sealed union with `AnthropicChatModel` + `OpenAiChatModel` **config types only**
  (backend discriminator, reasoning/effort/prompt-caching fields, `capabilityOverride`, `apiFamily` for
  OpenAI).
- **Absorbs the v1 rename deferred from PR1:** `model.request.provider` → `model.request.v1` package rename and
  `ProviderConfiguration` → `V1ProviderConfiguration` type rename, landing alongside the new
  `model.request.v2` / `V2ProviderConfiguration` so the v1/v2 split appears in one coherent change.
- **No request converters; factories NOT registered.** v2 is selectable in the template but not runnable until
  PR4/PR5 register the factories.

**Depends on PR2** (`capabilityOverride` references the capability model).

---

## PR4 — Native Anthropic (direct backend)

**Scope:**
- `AnthropicChatModelApiFactory` registered; Messages API request/response converter.
- **Direct backend only** (Bedrock backend deferred).
- **Keep** reasoning (thinking) + prompt-caching config.
- **No** server-side tools (code execution / web search / web fetch); **no** Skills.
- Deterministic WireMock/SSE wire-format tests travel with this PR; `RealProviderApiSmokeIT` Anthropic rows
  land here.

**ADR:** native Anthropic provider (standalone or a section of the overarching ADR).

---

## PR5 — Native OpenAI

**Scope:**
- `OpenAiChatModelApiFactory` registered; Completions + Responses families.
- **Keep** effort config.
- **No** server-side tools (web search / code interpreter); **no** Skills.
- Deterministic WireMock/SSE wire-format tests + `RealProviderApiSmokeIT` OpenAI rows land here.

Independent of PR4; both stack on PR3.

---

## Deferred follow-ups (after the pilot lands)

- **Tool-call-result persist-redesign** — consume `toolResultModalities()`, lift documents inline, drop the
  eager synthetic message, decide placement once at ingestion and persist it (see the separate handoff:
  `docs/superpowers/2026-07-20-persisted-tool-call-result-documents-handoff.md`). Own ADR.
- Anthropic server tools (code execution, web search, web fetch) + version configurability.
- Anthropic Skills.
- Anthropic Bedrock backend(s) (the "Bedrock reshape" decision: Converse for generic AWS; two Claude-on-AWS
  backends).
- OpenAI server tools (web search, code interpreter).
- OpenAI / Anthropic Skills.
- Streaming SPI decision — if we ever move the *SPI surface* to streaming (natives already consume vendor APIs
  streamably regardless). Cheapest to settle before PR4 freezes the provider contract.
- v1/v2 config merge (assessment done; user deferred).

## ADR plan

- **Own the LLM layer** (SPI + continuation + content-block message model) → with PR1.
- **Model capability matrix** → with PR2.
- **Tool-result container shape** (`List<Content>`) → noted in the PR1 ADR; the full doc-placement redesign
  gets its own ADR in the follow-up.
- Native provider specifics → sections of the overarching ADR or short standalone ADRs with PR4/PR5.
