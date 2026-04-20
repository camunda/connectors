# Redesign Conversation Storage SPI: Factory Pattern with Decoupled Message Types

* Deciders: Agentic AI Team
* Date: Apr 20, 2026

## Status

**Implemented**

## Context and Problem Statement

The conversation storage SPI used a callback-based pattern (`executeInSession`) where the store
created a session and invoked a handler callback within its scope. The session interface was coupled
to `RuntimeMemory`, and `storeFromRuntimeMemory` returned a full `AgentContext` — mixing storage
concerns with agent state assembly.

Should we keep the callback pattern or switch to a factory pattern with decoupled message types?

## Decision Drivers

* **Lifecycle clarity**: The callback pattern obscured who owns the session lifecycle. For stores
  managing external resources (e.g., AWS clients), the implicit lifecycle made it hard to reason
  about cleanup guarantees.
* **Coupling to RuntimeMemory**: `loadIntoRuntimeMemory` and `storeFromRuntimeMemory` forced the
  session to know about `RuntimeMemory`, an in-process concern unrelated to storage.
* **Responsibility creep**: `storeFromRuntimeMemory` returned a full `AgentContext`, meaning the
  session was assembling agent state — not just persisting messages.
* **Testability**: The callback pattern required wrapping all test logic inside lambda callbacks,
  making tests harder to read and debug.
* **Future extensibility**: Planned features (completion callbacks, reconciliation) need the caller
  to hold a session reference across the request lifecycle, which the callback pattern doesn't
  support.

## Considered Options

1. Keep the callback-based `executeInSession` pattern
2. Switch to a factory pattern (`createSession`) with `AutoCloseable` sessions and decoupled message
   types

## Decision Outcome

Chosen option: **Option 2 — Factory pattern with decoupled message types**.

### Core changes

- `ConversationStore.executeInSession(handler)` → `createSession()` returning `ConversationSession`
- `ConversationSession` now extends `AutoCloseable`, used via try-with-resources
- `loadIntoRuntimeMemory(agentContext, memory)` → `loadMessages(agentContext)` returning
  `ConversationLoadResult`
- `storeFromRuntimeMemory(agentContext, memory)` → `storeMessages(agentContext, request)` returning
  only `ConversationContext` (storage cursor)
- Caller assembles `AgentContext` via `agentContext.withConversation(cursor)`
- `ConversationSessionHandler` deleted
- `compensateFailedJobCompletion` removed (was deprecated, never invoked)
- New wrapper types: `ConversationLoadResult`, `ConversationStoreRequest`

### Consequences

**Positive:**
- Session lifecycle is explicit and visible in the code (try-with-resources)
- AWS AgentCore client cleanup is now handled by `close()` instead of implicit callback scope
- Sessions work with plain `List<Message>` (via wrapper types), no `RuntimeMemory` dependency
- `storeMessages` returns only what it owns (the storage cursor), not the full agent state
- Tests are simpler — direct method calls instead of callback lambdas
- Wrapper types (`ConversationLoadResult`, `ConversationStoreRequest`) provide stable SPI types that
  can be extended without breaking changes

**Negative:**
- Breaking change for custom `ConversationStore` implementations (see
  [breaking-changes.md](../breaking-changes.md) for migration guide)

### Future improvements

- **Execution identity**: `AgentContext.executionId` (platform-level stable identifier) to replace
  `ConversationContext.conversationId()` as the primary execution key
- **Schema versioning**: `AgentContext.schemaVersion` for explicit data migration across SPI changes
