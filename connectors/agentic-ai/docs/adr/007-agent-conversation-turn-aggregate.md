# Agent Conversation Turn Aggregate (Stage 2)

* Deciders: Agentic AI Team
* Date: Jun 17, 2026

## Status

**Implemented** on branch `agentic-ai/agent-conversation`.

Continues the work started in [ADR 006](006-data-driven-agent-initialization.md), which introduced
`AgentConversation` as a thin transient aggregate and flagged turn-behavior migration as Stage 2.

## Context and Problem Statement

After ADR 006, `AgentConversation` held a flat `RuntimeMemory` message list and the current
`AgentContext`, but owned no turn-level behavior. The following responsibilities remained scattered:

- **Message assembly**: `AgentMessagesHandlerImpl` built the next LLM input
  (user prompt, tool call results, event messages, document extraction) and returned a raw
  `List<Message>` alongside a boolean flag indicating whether to proceed.
- **Limits checking**: `AgentLimitsValidatorImpl` queried raw `AgentContext.metrics.modelCalls`
  against the configured cap; the check had no structural relationship to the turn being built.
- **Metrics delta**: `BaseAgentRequestHandler` captured `initialMetrics` at entry, then subtracted
  it from `agentContext.metrics` after the LLM call; the delta was implicit state leaking across
  method boundaries.
- **Message windowing**: `MessageWindowRuntimeMemory` was a stateful decorator whose mutation
  semantics were non-obvious; it also shadowed `RuntimeMemory`, making the two types easy to
  confuse.
- **Proceed / wait / cancel**: `BaseAgentRequestHandler` inspected the returned message list to
  decide between three outcomes (proceed, no-op wait, cancellation), but the decision logic was
  split between `AgentMessagesHandlerImpl` (building the list), the abstract method
  `modelCallPrerequisitesFulfilled`, and the subclass-specific overrides.

`AgentMessagesHandlerImpl`'s unit test had grown unwieldy; changes to message assembly
required updates in three separate places. `RuntimeMemory` was passed as a mutable parameter
through multiple layers that had no need to mutate it.

How should turn behavior be consolidated without changing the serialized `AgentContext` contract or
requiring a data migration for existing conversations?

## Decision Drivers

* **Explicit turn structure**: a "turn" (one LLM call with its input messages, assistant response,
  and per-turn metrics) should be a first-class domain concept, not an implicit slice of a flat list.
* **Single decision point**: the proceed / wait / cancel outcome should be expressed as a typed
  value returned from a single component, not inferred by the caller from an empty list + boolean.
* **No mutable shared state**: the working representation of a conversation should be immutable;
  each transformation returns a new instance.
* **Backward compatibility**: existing persisted conversations (flat message lists in `agentContext`)
  must rehydrate without a data migration.
* **Testability**: turn assembly and limits checking should be testable in isolation, without
  constructing a full handler invocation.

## Considered Options

1. **Keep the procedural approach**: retain `RuntimeMemory`, `AgentMessagesHandler`,
   `AgentLimitsValidator` as separate Spring beans; the handler continues threading them as locals.
2. **Extract handler sub-methods, keep the types**: collapse the three handler phases into private
   methods on `BaseAgentRequestHandler`; keep `RuntimeMemory` mutable but stop passing it as a
   parameter. Same behavior, no new concepts.
3. **Immutable turn aggregate**: promote `AgentConversation` to own the turn lifecycle:
   `ConversationTurn` as a named concept, `TurnReconstructor` for backward-compatible rehydration
   from the flat stored list, `ConversationTurnComposer` returning a sealed `AgentInput` decision,
   `ConversationSnapshot` as the read-only windowed LLM view. Delete `RuntimeMemory` and the
   standalone validator/handler beans.

## Decision Outcome

Chosen option: **Option 3: immutable turn aggregate**, because it resolves the root causes
(scattered turn logic, mutable shared state, an untypable proceed/wait/cancel outcome) rather than
reorganizing the same structure.

### Core changes

**`ConversationTurn` record**: the unit of one LLM call:

```
ConversationTurn(int iterationKey, List<Message> inputMessages,
                 @Nullable AssistantMessage assistantMessage, AgentMetrics metrics)
```

A turn is *pending* (`assistantMessage == null`) when created by `rehydrate`, and *complete* after
`ingest`. `iterationKey` is 1-based and counts LLM calls across the agent's lifetime.

**`AgentConversation` as an immutable aggregate**: copy-on-write state transitions:

| Method | Description |
|--------|-------------|
| `rehydrate(history, systemMessage, inputMessages, agentContext, config)` | Build from reconstructed history + the composed system message and current-turn input as a pending turn |
| `ingest(assistantMessage, tokenUsage)` | Complete the pending turn with the LLM response |
| `withStoredConversation(ref)` | Update the persistence cursor after storing messages |
| `toAgentContext()` | Reduce back to the serialized `AgentContext` with updated metrics |

The composed system message is passed in by the handler and may be `null` when the composed system
prompt is blank (in which case no system message is sent to the LLM or persisted).

**`TurnReconstructor`**: rebuilds the `ConversationTurn` list from the persisted flat message
list. Groups messages by scanning for `AssistantMessage` boundaries; assigns `iterationKey` by
position (1-based count of completed assistant messages). All reconstructed turns carry
`AgentMetrics.empty()`: per-invocation metrics are computed live from the current turn, not read
from historical turns.

This provides backward compatibility with all existing conversations without a data migration.

**`AgentInput` sealed interface**: the decision produced by `ConversationTurnComposer`:

```
AgentInput.None          // wait for more tool results
AgentInput.Cancellation  // conversation cannot continue (e.g. no user prompt)
AgentInput.NextTurn      // messages ready; proceed to LLM
```

Replaces the boolean `modelCallPrerequisitesFulfilled` + raw `List<Message>` pair.

**`ConversationTurnComposer` / `ConversationTurnComposerImpl`**: replaces `AgentMessagesHandler`.
Takes `AgentConversation`, returns `AgentInput`. Owns message assembly (user prompt, tool results,
event messages, document extraction) and the proceed/wait/cancel routing decision.

**`ConversationSnapshot`**: the transient, windowed, read-only view sent to the LLM. Built by
`AgentConversation` via `MessageWindowFilter.apply(allMessages(), windowSize)`.
`AiFrameworkAdapter` takes a `ConversationSnapshot` instead of a mutable `RuntimeMemory`.

**`MessageWindowFilter`**: extracted as a pure static utility (`apply(List<Message>, int)`).
Replaces the stateful `MessageWindowRuntimeMemory` decorator.

**`BaseAgentRequestHandler` as a thin orchestrator**: the `converse` path reduces to:

```
load + reconstruct history → compose input
  → None:         handleNoOp
  → Cancellation: handleInputCancel
  → NextTurn:     createSystemMessage → rehydrate → checkLimits
                  → [LLM call] → ingest → persist → toAgentContext → buildResponse
```

The model-call limit is checked in `throwIfLimitsReached`, which reads the cumulative
`modelCalls` from `AgentConversation.totalMetrics()` (backed by the durable `AgentContext.metrics`,
not the reconstructed per-turn metrics — see below).

**Deleted components**: `AgentLimitsValidator`, `AgentLimitsValidatorImpl`,
`AgentMessagesHandler`, `RuntimeMemory`, `DefaultRuntimeMemory`, `MessageWindowRuntimeMemory`.
Their responsibilities now live in `BaseAgentRequestHandler.throwIfLimitsReached` (reading
`AgentConversation.totalMetrics()`), `ConversationTurnComposerImpl`, and `MessageWindowFilter`.

### Serialization contract

`AgentContext` is unchanged. Messages continue to be stored as a flat list (via `ConversationStore`
/ `ConversationSession`) and re-inflated into turns on the next invocation. The `AgentState` enum
(`INITIALIZING` / `TOOL_DISCOVERY` / `READY`) is unchanged.

### Consequences

**Positive:**
- Turn boundaries are explicit and testable in isolation; `AgentConversation` and
  `ConversationTurnComposerImpl` each have focused unit tests.
- The proceed / wait / cancel outcome is a typed sealed interface; the `switch` in the handler is
  exhaustive with no `default`.
- Per-turn metrics are stored directly on the turn; the agent instance update sends `currentTurn.metrics()` as-is, eliminating the captured-at-entry `initialMetrics` local.
- No data migration; existing conversations rehydrate transparently.

**Negative:**
- Per-turn historical metrics are not persisted in the message list: reconstructed previous turns
  always carry `AgentMetrics.empty()`. Cumulative metrics (model calls, tokens, tool calls) are
  therefore read from the durable `AgentContext.metrics` — `totalMetrics()` returns
  `baseAgentContext().metrics()` and `toAgentContext()` increments it by the current turn's delta.
  The model-call limit check must use this cumulative counter, **not** a sum over reconstructed
  turns (which is always zero). Per-turn historical cost reporting would need the metrics to be
  persisted alongside messages (see Future improvements).
- Two "conversation" vocabularies coexist: `ConversationStore` / `ConversationSession` /
  `ConversationContext` (message persistence layer) vs. `AgentConversation` / `ConversationTurn`
  (turn aggregate layer). The distinction must be kept clear in code reviews and documentation.

### Future improvements

- If per-turn historical metrics become a requirement, store them alongside messages (e.g. as a
  message metadata field or a parallel structure), and update `TurnReconstructor` to read them.
- The `AgentConversation` / `ConversationTurn` model is currently only held in memory. Serializing
  it into in the `ConversationStore` lifecycle would allow richer recovery on rehydration; a first implementation should
  include a one-time fallback path that reconstructs from the old flat message list when the new
  structure is absent, so existing conversations migrate transparently.
- Add a `schemaVersion` field to the `AgentContext` serialized format. This gives future migrations
  an explicit discriminator instead of relying on field presence heuristics.
