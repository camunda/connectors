# Data Model & Agent Context

The **central piece of persisted state**: a snapshot of the agent carried as the `agentContext`
process variable (Sub-process) or via `agent.context` (Task). It holds the lifecycle state, the
resolved tools, a pointer to the stored conversation, cumulative metrics, and the metadata used to
detect process migration. Serialized as JSON, it is passed into every job activation and written back
on every completion. Authoritative definition:
[`AgentContext.java`](../../aiagent-model/src/main/java/io/camunda/connector/agenticai/aiagent/model/AgentContext.java).

**Features.** `AgentContext.features` is the set of features (an insertion-ordered JSON array of strings, omitted
when empty, no schema version bump) the agent instance was created with. A new agent instance gets the
current features when its context is created at its first job (`AgentInitializerImpl`, both flavors): today
`runtimeTools` (`AgentFeatures.RUNTIME_TOOLS`, the runtime tool facility, `AgentContext.supports(String)`).
A context that exists without `features` belongs to an instance created before the features and never gets features
added later, so its behavior stays as it was created. The reasons are in [ADR 016](../../docs/adr/016-runtime-tools.md); in
short, `runtimeTools` decides only whether runtime tools are offered: an instance without it has no runtime tool and no
tool element checked against runtime tool names. The kind and the rejection of a tool call ([`agentic-loop.md`](../../docs/architecture/agentic-loop.md#what-happens-when-tools-complete)) are set for every
instance: the process never activates an element for the call of an unknown tool, so an instance that handed it to the
process would wait for a result that never comes, and the engine raises no incident. This is a deliberate exception to
gating a persisted shape on a feature.
`RuntimeTools` reads the features from the `AgentContext`; they are not part of the configuration fingerprint.

## AgentExecutionContext (the transient request context)

The transient per-invocation request context, assembled fresh for each job activation and never
persisted. It carries the job metadata, the inbound `AgentContext` and tool-call results, the LLM
provider configuration, the system- and user-prompt configurations, and the memory, limits, event, and
response settings that shape the turn. Authoritative definition:
[`AgentExecutionContext.java`](../../aiagent-model/src/main/java/io/camunda/connector/agenticai/aiagent/model/AgentExecutionContext.java).

## ToolCallResultContent (persisted tool-call-result content)

`ToolCallResult` (the transient tool-return DTO a BPMN element hands back, with an untyped `Object
content`) is lifted into `ToolCallResultContent` — the persisted element type — before it enters a
turn's input messages. Unlike `ToolCallResult`, it carries a structured `List<Content>` instead of an
untyped payload, so later stages can inspect the shape of a tool result (documents, images,
reasoning, provider-native blocks) without stringifying it. The lift (`ToolCallResultContent.from`)
maps a `String` to `TextContent`, a `Document` to `DocumentContent`, and anything else to
`ObjectContent`; for a result of a process tool it produces a singleton list. A runtime tool ([Add a runtime tool](../../docs/architecture/extension-points.md#add-a-runtime-tool)) returns a
`RuntimeToolResult` whose content is a list of typed blocks (`RuntimeToolResult.success(String)` is the one text block of
`contentFromObject`), so such a result is typed from the start. `RuntimeTools` turns the object of an `ObjectContent` block
into plain JSON values (maps, lists, strings, numbers, booleans) when the tool returns it and keeps the documents in it,
also those in a record, as they are. The documents of the result are found in plain JSON values, and the object is the
one a later job reads from the conversation.

The sealed `Content` model gained two additive members alongside the existing `TextContent` /
`DocumentContent` / `ObjectContent`:
- **`ReasoningContent`** — an opaque provider reasoning payload (`provider`, `payload`, `text`,
  `metadata`). `payload` is carried verbatim except for the human-readable reasoning text, which is
  lifted into the dedicated `text` field and stripped out of `payload` so it isn't persisted twice.
  Provider-specific request converters merge `text` back into a copy of `payload` before replaying
  the block on the next request, so the round trip is still byte-identical (required for signature
  verification on blocks that have one, and for prompt-caching prefix matching). `text` is `null`
  when a provider has nothing human-readable to extract (e.g. Anthropic's `redacted_thinking`
  blocks).
- **`ProviderContent`** — a provider-native content block preserved verbatim (`provider`, `payload`,
  optional `text`, `metadata`); each payload self-describes its own block shape (e.g. via an internal
  `type` field), so there is no separate discriminator field. `text` is a display-only rendering that
  is never replayed; when present, the history mapper presents the block as plain text.

Neither is produced or consumed by the LangChain4j path yet; they exist so the structured shape is
ready for chat model implementations that need them.

**Backward compatibility (Camunda 8.9):** before `ToolCallResultContent` existed, a persisted tool
call result was the flat `ToolCallResult` shape — `content` was a raw scalar/object/array and any
additional framework-internal properties were flattened as top-level fields. Old state is migrated on
read by an **explicit persisted schema version**, not by inspecting the shape of `content`. Each
conversation-state root carries its own version and is migrated independently — `schemaVersion` on
`AgentContext` (`CURRENT_SCHEMA_VERSION`; absent ⇒ pre-8.10 legacy) for the process-variable payload
(which for the in-process store also holds the embedded messages), `schemaVersion` on the
Camunda-document conversation payload, and the blob-envelope version for AWS AgentCore. A root's version
is read from its own persisted form: for the pointer-based stores (Camunda document, AgentCore) the
`AgentContext` is only a pointer, so its version says nothing about the externally-stored payload, which
must therefore carry its own. On read, a shared upcaster (`AgentContextSchemaMigration`) lifts the legacy flat `content`
into the structured `List<Content>` before binding; `ToolCallResultContent` itself then deserializes
only the current shape (a legacy shape reaching it un-upcasted fails loud). The lift is deliberate: a
legacy gateway (MCP/A2A) result whose `content` was a list of provider blocks is wrapped as a single
opaque `ObjectContent`, never re-split into typed `Content` — a shape heuristic could not distinguish
it, since those blocks share the same `type` discriminators. The write path always re-persists the
current shape, so a conversation touched again after upgrading is migrated forward on its next write.

## Message Identity

Every `Message` (`SystemMessage`, `UserMessage`, `AssistantMessage`, `ToolCallResultMessage`)
carries a self-generated `id()` of type `MessageId`, independent of any provider-issued id (see
[ADR 012](../../docs/adr/012-stable-self-generated-message-ids.md)). A UUIDv7 value is generated once, at
first construction (`@RecordBuilder.Initializer(source = MessageUtil.class, value =
"generateId")`), and stays stable thereafter because it round-trips through persistence rather
than being re-derived. `MessageId` serializes as a bare JSON string and accepts any valid UUID on
parse, not only v7. Messages persisted before this field existed get backfilled with a fresh id
the first time they're deserialized — the same initializer mechanism, no separate migration step.
`AwsAgentCoreConversationStore` round-trips it explicitly via a dedicated property
(`AwsAgentCoreConversationMapper.PROPERTY_ID`), since it doesn't go through Jackson like the other
two stores. `AssistantMessage.messageId()` (provider-sourced) and `ToolCall.id` (provider- or
gateway-sourced) are separate, untouched identities — see the ADR for why they don't merge.
