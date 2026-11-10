# Data Model & Agent Context

The **central piece of persisted state**: a snapshot of the agent carried as the `agentContext`
process variable (Sub-process) or via `agent.context` (Task). It holds the lifecycle state, the
resolved tools, a pointer to the stored conversation, cumulative metrics, and the metadata used to
detect process migration. Serialized as JSON, it is passed into every job activation and written back
on every completion. Authoritative definition:
[`AgentContext.java`](../../aiagent-model/src/main/java/io/camunda/connector/agenticai/aiagent/model/AgentContext.java).

**Features.** `AgentContext.features` is the set of features (an insertion-ordered JSON array of strings, omitted
when empty) the agent instance was created with. A new agent instance gets the
features of its execution when its context is created at its first job (`AgentInitializerImpl`, both flavors,
`AgentExecutionContext.newInstanceFeatures()`). The v2 connectors give `AgentFeatures.ofNewInstance()` and the
deprecated v1 connectors give no feature. The features are `runtimeTools` (`AgentFeatures.RUNTIME_TOOLS`, the runtime tool
facility) and `documents` (`AgentFeatures.DOCUMENTS`, the agent documents); a feature is read with
`AgentContext.supports(String)`, and the document handling of the instance with `AgentContext.documentHandling()`.
A context that exists without `features` belongs to an instance created before the features, or by a v1 connector, and
never gets features added later, so its behavior stays as it was created. The reasons are in [ADR 016](../../docs/adr/016-runtime-tools.md)
and [ADR 018](../../docs/adr/018-document-flow.md); in short, an instance without
`runtimeTools` has no runtime tool and no tool element checked against runtime tool names, and an instance without
`documents` keeps documents as stored content with the older XML tag format and has no agent documents. The kind and the rejection of a tool call ([`agentic-loop.md`](../../docs/architecture/agentic-loop.md#what-happens-when-tools-complete)) are set for every
instance: the process never activates an element for the call of an unknown tool, so an instance that handed it to the
process would wait for a result that never comes, and the engine raises no incident. This is a deliberate exception to
gating a persisted shape on a feature.
`RuntimeTools` and the conversation read the features from the `AgentContext`; they are not part of the configuration
fingerprint.

**Response documents.** `AgentContext.responseDocuments` is the list of the documents selected for the final response, in the
order they were selected (omitted when empty; a missing field binds to an empty list). An entry is
`{key, document, description?}`: the key the process finds the document by, the `AgentDocumentId` of one of the agent
documents and an optional description. A selection under a key that is already used replaces that selection in place
(`AgentContext.withResponseDocument`). The final response carries each selection under its key as a `ReturnedDocument` with
the Camunda document reference of its agent document; the order of its entries is not specified, only this list keeps
the order of selection. The runtime tool `c8__return_document` selects them ([Add a runtime tool](../../docs/architecture/extension-points.md#add-a-runtime-tool)); the list stays in the
context for the lifetime of the agent instance, so every final response carries all of them. It is part of `agentContext`
like the agent documents, so it is independent of the conversation store.

**Documents.** `AgentContext.documents` is the `AgentDocuments` of the agent instance: a list of `AgentDocument` entries
`{id, origin, reference}` (omitted when empty; a missing field binds to no documents), read and
replaced through `AgentContext.documents()` and `withDocuments(...)`. `origin` is the sealed `AgentDocumentOrigin`
(`UserMessage`, `Event`, `ToolCall`), persisted with the discriminator `type`: `{"type": "user_message", "messageId": ...}`,
`{"type": "event", "messageId": ...}` or `{"type": "tool_call", "toolName": ..., "toolCallId": ...}`. `reference` is the document reference of the
connector SDK (`DocumentReferenceModel`), in the JSON the standard `DocumentSerializer` emits; the model module depends
on the SDK for it. The file name, content type and size shown in handles are read from it
(`AgentDocument.Descriptors.of(reference)`), never stored. `AgentDocument.toDocument(DocumentFactory)` restores the live document.
`AgentDocuments` is immutable and insertion-ordered, the first registration of an id wins and a known id is never
replaced; `register(Document, AgentDocumentOrigin)` and `register(List<AgentDocument>)` return the collection that holds the
documents. Registering a document whose id belongs to a different document, one the agent knows or one of the same batch,
fails with an `AgentDocumentIdCollisionException` and a document that is not a Camunda document with an
`UnsupportedDocumentReferenceException`; the request handler turns them into `DOCUMENT_ID_COLLISION` and
`UNSUPPORTED_DOCUMENT_REFERENCE` ([Error Codes](../../connector-aiagent/docs/error-codes.md)). Because it rides in `agentContext`, it is
independent of the conversation store and the context window. The persisted conversation is not self-contained: the
content of a document comes from the agent documents when the conversation is windowed, so replaying a conversation needs
the agent documents of the same `agentContext`; both are written by the same job completion. They are never pruned. A
Camunda document is about 400 bytes; the bound is the broker message size of the job completion (4 MB), which matters
together with the in-process store (see [Document Extraction from Tool Call Results](../../docs/architecture/extension-points.md#document-extraction-from-tool-call-results)). The id
of a document is an `AgentDocumentId` (`aiagent.model.document`, persisted as a plain string) derived from the document:
`doc_` followed by the first 12 lowercase hex characters of the SHA-256 of the store id and the document id (`AgentDocumentId.of`, see
"The handle" in [Document Extraction from Tool Call Results](../../docs/architecture/extension-points.md#document-extraction-from-tool-call-results)). The agent context is written with schema version 2, which an 8.10
connector runtime rejects (see the schema versions under
[ToolCallResultContent](#toolcallresultcontent-persisted-tool-call-result-content)).

**Document handling.** `AgentContext.documentHandling()` is the sealed `DocumentHandling` of the agent instance,
derived from the `documents` feature and not persisted: `Untracked`, or `Tracked(AgentDocuments)`. The composer, the
tool call results input builder, the document expander, the history mapper and the agent instance client match both
variants exhaustively; none of them reads the feature or takes a `boolean`.

**Tool definitions.** `AgentContext.toolDefinitions` holds the `ToolDefinition` of every tool offered to the model:
`name`, `description`, `inputSchema`, the `kind` (written only for a `RUNTIME` tool; absent reads as `PROCESS`) and the
`documentDelivery`, `reference` or `content`, the delivery of the documents the tool returns. `documentDelivery` is
omitted when absent, and absent reads as `reference`. It is part of the configuration fingerprint when set, and the model
providers get the definitions without it (`ToolDefinition.withoutDocumentDelivery`).

## AgentExecutionContext (the transient request context)

The transient per-invocation request context, assembled fresh for each job activation and never
persisted. It carries the job metadata, the document factory of the job (the document store of the physical tenant of
the job), the inbound `AgentContext` and tool-call results, the features of a new agent instance
(`newInstanceFeatures()`, `AgentFeatures.ofNewInstance()` by default), the LLM
provider configuration, the system- and user-prompt configurations, and the memory, limits, event, and
response settings that shape the turn. Authoritative definition:
[`AgentExecutionContext.java`](../../aiagent-model/src/main/java/io/camunda/connector/agenticai/aiagent/model/AgentExecutionContext.java).

## ToolDefinition (a tool offered to the model)

`ToolDefinition` holds the `name`, the `description`, the JSON `inputSchema` and the `kind` (`ToolKind`: a BPMN element of
the process or a runtime tool) of one tool. `ToolDefinition.documentParameters()` lists the document parameters of the
input schema (`DocumentParameter`); see
[Document parameters](../../connector-aiagent/docs/document-parameters.md).

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

`AgentDocumentContent` (content type `agent_document`) is the persisted form of a known document for an agent instance
that tracks its documents: the `AgentDocumentId`, a `delivery`, `reference` or `content`, an optional `note` (a sentence
for the model about the document, omitted when absent and set only on a block with the delivery `reference`) and the
content `metadata`. The file name, content type and size the model reads are those of the agent document of the
id: `AgentDocumentHandle.of(agentDocuments, id)` builds the handle the model sees. A document that is a whole tool result is
persisted as such a block, which is rendered as its handle (`ToolCallResultContent.withDocumentsAsReferences`).
`DocumentExpander.expand` expands it before a request is built (see [Conversation Memory](../../connector-aiagent/docs/conversation-memory.md#agentconversation-conversationsnapshot--messagewindowfilter)): in a user message to the handle text and, with the
delivery `content`, the document content; in a tool result to the handle text of the document. Only the agent sets the block.

`DocumentContentDelivery` is the record of the ids of the documents whose content the model reads or receives while the
results of a batch of tool calls are applied. `deliver(requested)` gives, for each requested document, `Delivered` (the
content is added) or `AlreadyInConversation`, and the `DocumentContentDelivery` for the next request. It does not check
that the ids are known.

`DocumentContent` and `AgentDocumentContent` follow one rule: a new document is a `DocumentContent` (the live document of
the connector SDK), at the input boundaries (the user prompt, events, the results of process tools, a runtime tool that
returns a new document), everywhere for an `Untracked` instance, and as the only document block a provider sees after the
expansion; a known document is an `AgentDocumentContent`. The content for the model comes only from the agent, through
the delivery, never from a block a tool authors. An `AgentDocumentContent` never reaches a provider: every provider
content converter fails with an `UnexpandedAgentDocumentException` (an `IllegalStateException`, in
`aiagent.chatmodel.provider`) when it is handed one. The delivery is stored on each block, so earlier messages keep their
bytes when the same document gets another delivery later (prompt caching).

**Backward compatibility (Camunda 8.9):** before `ToolCallResultContent` existed, a persisted tool
call result was the flat `ToolCallResult` shape — `content` was a raw scalar/object/array and any
additional framework-internal properties were flattened as top-level fields. Old state is migrated on
read by an **explicit persisted schema version**, not by inspecting the shape of `content`. Each
conversation-state root carries its own version and is migrated independently — `schemaVersion` on
`AgentContext` (`CURRENT_SCHEMA_VERSION`, 2; absent ⇒ pre-8.10 legacy) for the process-variable payload
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

Version 2 adds `AgentContext.features`, `AgentContext.documents` and `AgentContext.responseDocuments`, the
`agent_document` content block, and the `kind` and `rejection` of a `ToolCall`. A version 1 root loads
unchanged, because each of them binds to its empty value when it is missing. Every root migrates step by step: unversioned
to 1 lifts the tool call result content, 1 to 2 changes nothing. A runtime that supports a lower version rejects a root
with a higher one, so it fails on state that a newer connector wrote and never rewrites it.

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
