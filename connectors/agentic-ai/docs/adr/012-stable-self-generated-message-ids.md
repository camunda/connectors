# Stable, self-generated conversation message ids

* Deciders: Agentic AI Team
* Date: August 13, 2026

## Status

**Implemented**

## Context and Problem Statement

Conversation `Message`s (`SystemMessage`, `UserMessage`, `AssistantMessage`,
`ToolCallResultMessage`) have no independent identity. `AssistantMessage.messageId` exists but is
provider-sourced (present only when the LLM provider returns one) and is used exclusively for AWS
AgentCore round-tripping — it is not a general-purpose identity mechanism, is absent for other
providers, and does not exist at all on the other three message types.

The agent instance history API (camunda/camunda#58789) is being redesigned to support
correlating and deduplicating history items by id — see the companion issue on streaming tool
call results into history as they arrive, whose duplicate-write tradeoff this id is meant to
eventually resolve. To be ready for that, every `Message` needs a stable id of its own, generated
by the connector rather than borrowed from a provider that may not supply one.

## Decision Drivers

* **Coverage**: every message type needs an id, not just the ones a provider happens to
  populate.
* **Stability**: once assigned, an id must not change across reloads of the same persisted
  message — dedup depends on it being the same value every time.
* **Minimal surface area**: avoid new persisted state beyond the id itself; avoid a parallel
  migration mechanism if the existing serialization contract can absorb the change.
* **Non-null domain model**: `@NullMarked` is the convention in this module (see `AGENTS.md`
  "Null safety"); a field that's sometimes absent should either be `@Nullable` or handled so the
  domain type never has to represent "no id" as a real state.
* **No new identity for tool calls**: `ToolCall.id` already serves the correlation needs of
  Zeebe/AHSP element activation and the provider round-trip (a tool result must echo back the
  exact id the provider issued for a `tool_use` block); it must not be touched by this change.

## Considered Options

| Dimension | A — random id, generated once, persisted forward | B — deterministic id, re-derived from position |
|---|---|---|
| Generation | `UUID.randomUUID()` at first construction | e.g. hash of `iterationKey` + role + index |
| Stability mechanism | Persistence (write once, read back unchanged) | Recomputation (same inputs always yield same id) |
| Retry/duplicate-write behavior | Two independently-constructed copies of "the same" message (e.g. a synthetic history-only message built without going through the store) get *different* ids | Two independently-constructed copies get the *same* id automatically |
| Complexity | Needs the id to flow through construction → persistence → reload, but no derivation logic | Needs every construction site to know its own position/turn scoping correctly, including edge cases (continuation rounds, gateway-transformed messages) |
| Fit with existing per-item identity | Coexists with `ToolCall.id` / `ToolCallResultContent.id` (already stable via the provider/gateway) without overlapping it | Same |

## Decision Outcome

**Option A** — a random id, generated once, stable via persistence, not re-derivation.

- **New `id` field on `Message`**, present on all four subtypes (`SystemMessage`, `UserMessage`,
  `AssistantMessage`, `ToolCallResultMessage`) — one id per `Message` instance. Not pushed down to
  individual `ToolCallResultContent` entries within a `ToolCallResultMessage`; those already have
  a stable identity via the existing `ToolCallResult.id` used for tool-call correlation, and
  duplicating it with a second random id would be redundant.
- **`ToolCall.id` is untouched.** It keeps meaning exactly what it means today — provider-issued,
  or connector-constructed for gateway (MCP/A2A) tools — and remains the correlation key for
  Zeebe/AHSP and the provider round-trip. `AssistantMessage.messageId` (the existing AWS
  AgentCore round-trip field) similarly stays untouched, coexisting alongside the new `id` field.
- **Generation mechanism**: each message record declares its `id` component with
  `@RecordBuilder.Initializer(source = MessageUtil.class, value = "generateId")` (the same pattern
  already used for e.g. `AgentMetrics.tokenUsage` defaulting to `TokenUsage.empty()`). This means:
  - Constructing a message via `.builder()...build()` without explicitly setting `.id(...)`
    generates a fresh random id inline.
  - Deserializing persisted JSON that already contains an `"id"` field binds that exact value via
    the Jackson builder proxy, so the id survives every `storeMessages`/`loadMessages` round trip
    unchanged.
  - Deserializing **pre-existing** persisted JSON that has no `"id"` field (any conversation
    written before this change ships) leaves the builder's `id` unset, so the same initializer
    fires and mints a fresh id at load time — backfill-on-load falls out of the same mechanism
    used for fresh construction, with no separate migration code, `schemaVersion` bump, or
    `ConversationStore`-specific handling required. The domain model stays non-null throughout;
    there is no code path where `id()` can return null.
- **Which id a future history item carries depends on its role — this is not uniform.** When the
  redesigned API adds an id-accepting wire call, USER and ASSISTANT history items (1:1 with a
  `UserMessage`/`AssistantMessage`) carry that message's `id()`. TOOL_RESULT history items do
  **not** — `AgentInstanceHistoryMapper.inputHistoryItems` already splits one
  `ToolCallResultMessage` into *N* history items, one per `ToolCallResultContent`
  (`AgentInstanceHistoryMapper.java:86-89`), so a single parent-message id would be wrong (all N
  would collide on the same id, defeating the per-item dedup Feature A depends on). TOOL_RESULT
  items keep using the existing per-item `ToolCallResultContent.id()` (already threaded through as
  `toolCallId` on `AgentInstanceHistoryToolCall`) — unchanged by this ADR.
- **No plumbing added to `AgentInstanceHistoryMapper`/`CamundaAgentInstanceClient` today.** The
  `AgentInstanceClient` interface already receives `AgentConversationTurn`, from which
  `CamundaAgentInstanceClient` extracts the `Message` objects it needs
  (`createHistoryForInputMessages`/`createHistoryForAssistantMessage`) — those objects carry `id()`
  the moment this ADR ships, with no interface or method-signature change required. Threading the
  id further down into `InputHistoryItem` and the private `createHistoryItem` plumbing today would
  add a parameter nothing reads — `CreateAgentHistoryItemCommandStep1` has no field to receive it
  (confirmed via the `camunda-client-java` sources), so it would only be dropped at the bottom.
  When the redesigned API lands, only `CamundaAgentInstanceClient`'s internals change to thread the
  id through (the message is already in scope at each call site); the public interface contract
  stays stable.
- **`AwsAgentCoreConversationStore` round-trips `id` explicitly, since it doesn't go through
  Jackson.** Unlike the in-process and Camunda-document stores (which serialize `Message` via the
  Jackson builder proxy, so `id` round-trips for free), `AwsAgentCoreConversationMapper`
  hand-reconstructs each message from AWS AgentCore's own blob/property format
  (`extractProperties`/`extractMessageFromPayloads`) and previously had no notion of `id` at all —
  every reload would otherwise mint a fresh one, silently breaking the "stable via persistence"
  guarantee for this one store. Fixed by adding a new `PROPERTY_ID` ("camunda.id") written
  unconditionally in `extractProperties` and read back into each of the USER/ASSISTANT/TOOL
  builder branches — the same class of fix as `completedAt` forwarding in gateway tool handlers
  (`AGENTS.md` gotchas), applied here to `id`.

### Positive Consequences

- Every message carries a stable, connector-owned id regardless of provider, with no reliance on
  provider-supplied identifiers that may be absent.
- Backfill for pre-existing persisted conversations is free — the same builder-default mechanism
  that generates a fresh id on construction also covers "id missing from old JSON," with no
  additional migration surface.
- The domain model stays non-null (`id()` never returns null), consistent with this module's
  `@NullMarked` convention.

### Negative Consequences

- A message built more than once outside of persistence (e.g. two independent in-memory
  constructions intended to represent "the same" logical message, such as the synthetic
  history-only `ToolCallResultMessage` built for streaming partial tool results before the
  batch-complete write) gets a *different* random id each time it's constructed — this id is not
  useful for deduplicating that specific case. It only guarantees stability once a message has
  actually been persisted and reloaded.
- Backfilled ids for pre-existing conversations are not reproducible: reloading the same
  not-yet-persisted-with-this-change message twice (before it's ever written back through
  `storeMessages`) would mint two different ids. In practice this window is narrow — the id
  becomes stable as soon as the conversation is next stored.

## Out of Scope

- Threading the id through to the wire-level `CreateAgentHistoryItemCommandStep1` call — the
  current API has no parameter for it; this lands only once the redesigned agent instance history
  API supports it.
- Any change to `ToolCall.id` or `ToolCallResultContent.id` — both remain exactly as they are
  today.
- Deterministic/re-derived ids (rejected Option B above).
