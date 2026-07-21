# Own the LLM invocation layer with a ChatModel provider SPI

* Deciders: Agentic AI Team
* Date: Jul 21, 2026

## Status

**Accepted**. Realized by this change; further providers and the capability model follow in subsequent ADRs.

## Context and Problem Statement

The agentic module invokes LLMs through a single `AiFrameworkAdapter` bound to LangChain4J. That was
sufficient for a text-and-tool-call loop, but it has become the limiting factor for where we want to take the
module:

* Every provider is coupled to LangChain4J's feature set and release cadence. Provider-specific capabilities
  (native server-side tool use, structured reasoning, provider-native content) cannot be surfaced without
  either waiting for upstream support or working around the abstraction.
* The invocation contract is a single request/response. There is no notion of a provider *continuing* a turn
  (e.g. running a server-side tool and resuming), so the orchestration loop cannot model multi-round turns.
* A tool-call result is reduced to an untyped string payload. Its structure — documents, images, reasoning,
  provider-native blocks — is lost before persistence, which blocks any downstream handling that needs to know
  the shape of a result.
* Only input/output token counts are captured, even though providers increasingly report prompt-cache and
  reasoning token usage.

Should we continue extending the LangChain4J-bound adapter, or own the invocation layer behind a module SPI so
we can add native providers, richer content, and turn continuation on our own terms?

## Decision Drivers

* **Extensibility**: add an LLM provider by implementing an SPI and registering a bean, without touching
  orchestration, memory, or the data model.
* **Turn continuation**: support providers that continue a turn, via a loop rather than a single call.
* **State-of-the-art tool-call-result handling**: persist tool results as structured content so later stages
  can route documents, images, and reasoning natively instead of stringifying them.
* **Vendor-neutral control**: cross-cutting guards (such as content filtering) should be enforced once, in the
  orchestrator, off a normalized signal rather than per provider.
* **Behavior identity**: introduce the abstraction without changing current LangChain4J behavior.
* **Richer metrics**: capture prompt-cache and reasoning token usage where a provider reports it.

## Considered Options

1. Keep `AiFrameworkAdapter` and extend the LangChain4J integration as needs arise.
2. Introduce a `ChatModel` provider SPI owned by the module, with LangChain4J as the first implementation
   behind it.

## Decision Outcome

Chosen option: **Option 2 — a `ChatModel` provider SPI**, because it decouples the module from any single
framework, models turn continuation and structured content as first-class concepts, and lets cross-cutting
concerns live in the orchestrator, while keeping today's LangChain4J behavior identical.

The SPI and its surrounding contract:

* **`ChatModel`** (`AutoCloseable`) exposes `execute(ChatRequest) : ChatResult`. It is resolved and
  closed per request (try-with-resources), so a provider may hold per-invocation resources without a shared
  singleton lifecycle.
* **`ChatModelRegistry`** resolves a provider by `supports(configuration)` and fails loud when zero or more
  than one factory matches, replacing the previous fixed adapter binding. It dispatches on a neutral
  `ChatModelConfiguration` — the type carried through the agent configuration, exposing only `provider()` and
  `model()` — so a provider can supply its own configuration through the SPI rather than being confined to the
  module's built-in provider union.
* **Turn-based continuation**: `ChatResult` is a sealed `Completed | Continuation`. The request handler
  loops while the result is a `Continuation`, persisting each round as a separate turn. LangChain4J always
  returns `Completed`, so the loop runs exactly once for it — behavior-identical to the previous single call.
* **Normalized stop reasons**: a sealed `StopReason` maps provider finish reasons to a neutral vocabulary. The
  content-filter guard is enforced generically in the handler off the `CONTENT_FILTERED` stop reason (before
  ingesting the response), so every current and future provider inherits it rather than each reimplementing it.
* **Structured content model**: tool-call results are persisted as a structured `List<Content>`
  (`ToolCallResultContent`) instead of an untyped payload, and the sealed content model gains `ReasoningContent`
  (an opaque provider reasoning payload) and `ProviderContent` (a provider-native block preserved verbatim).
  Pre-existing (Camunda 8.9) state is migrated on read (see *Persisted schema version* below) rather than
  inferred from the JSON shape. This shapes the SPI to
  support state-of-the-art tool-call-result handling; the capability-aware routing that *consumes* the
  structure is a later change, and the current document handling defined in
  [ADR 004](004-document-handling-in-tool-call-results.md) is preserved unchanged.
* **Metrics**: `AgentMetrics.TokenUsage` gains prompt-cache and reasoning token counts, populated from
  provider-reported detail where available and omitted from persisted JSON when zero. It intentionally does
  not expose a combined `totalTokenCount` on the domain type: consumers use `inputTokenCount()` /
  `outputTokenCount()` (plus the auxiliary cache/reasoning counts) directly, since a single summed figure would
  be ambiguous about whether cache/reasoning tokens are already included.
* **Persisted schema version**: each conversation-state root records its own explicit schema version,
  so it is self-describing and migrated independently of the others — `schemaVersion` on `AgentContext`
  for the process-variable payload (which for the in-process store also carries the embedded messages),
  `schemaVersion` on the Camunda-document conversation payload, and the blob-envelope version for AWS
  AgentCore. A root's version is read from its own persisted form; for the pointer-based stores (Camunda
  document, AgentCore) this is essential, because there the `AgentContext` is only a pointer and its
  version says nothing about the externally-stored payload. On read, a single shared upcaster migrates
  state persisted before the structured shape into it; the domain types then deserialize only the current
  shape. The version is authoritative, rather than inferring the format from the shape of `content` — a
  heuristic that was ambiguous with gateway (MCP/A2A) tool results persisted as a list of provider content
  blocks sharing the same type discriminators, which could be mis-read as domain content. The write path
  always persists the current shape, so a conversation is migrated forward on its next write.

LangChain4J is reshaped into per-provider factories behind this SPI, with each factory's model-building logic
unchanged.

### Non-goals

Deliberately out of scope here and addressed by separate ADRs / changes:

* A model **capability matrix** describing what each backend supports.
* **Native (non-LangChain4J) provider** implementations.
* **Capability-aware tool-call-result routing** / inline document lifting. This change only establishes the
  structured persisted shape; it does not yet consume it, and preserves ADR 004 behavior.

### Positive Consequences

* New providers plug in through the SPI without changes to orchestration, memory, or the data model.
* Turn continuation and cross-cutting guards are modeled once and inherited by every provider.
* Tool-call results carry structure for future native rendering and routing.
* Prompt-cache and reasoning usage are captured where providers report them.

### Negative Consequences

* The persisted tool-call-result format changes, requiring migration of pre-existing state on read (handled by
  an explicit persisted schema version and a shared upcaster, covered by golden-state tests).
* The new content blocks and metric fields are additive and not yet produced or consumed by every path.

## Pros and Cons of the Options

### Option 1: Continue extending `AiFrameworkAdapter`

* Good, because there is no migration effort and the abstraction is already in place.
* Good, because LangChain4J covers the current text-and-tool-call use case.
* Bad, because provider capabilities remain gated behind LangChain4J's feature set and release cadence.
* Bad, because the single request/response contract cannot express turn continuation.
* Bad, because tool-call results stay untyped, blocking structured downstream handling.

### Option 2: A `ChatModel` provider SPI (chosen)

* Good, because providers become pluggable and independent of any single framework.
* Good, because continuation, normalized stop reasons, and structured content are first-class.
* Good, because cross-cutting guards live once in the orchestrator.
* Good, because it is introduced with behavior identity for the existing LangChain4J providers.
* Bad, because it adds an SPI layer and a per-request model lifecycle, and changes a persisted format (mitigated
  by backward-compatible reads).
