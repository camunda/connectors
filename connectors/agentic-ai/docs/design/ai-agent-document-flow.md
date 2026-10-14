# AI Agent document flow: design decisions

This document records why the document flow of the AI Agent (`connectors/agentic-ai`) is shaped the way it is: the
decisions, the alternatives that were considered and the trade-offs that were accepted. It applies to both flavors (AI
Agent Sub-process and AI Agent Task), which share `BaseAgentRequestHandler`. The behavior itself, the classes and the
formats are described in [`ai-agent.md`](../reference/ai-agent.md) (the "Document Extraction from Tool Call Results"
section, §7 "Document parameters" and §25.4 "Add a runtime tool"); this document does not repeat them.

## 1. Overview

**Goal.** A document that enters an agent conversation (the user prompt, event data, the result of a tool) must be
usable by the model without the conversation growing with its content, and must be usable by tools without the model
handling bytes or store details. Four capabilities follow:

1. The model sees every document of the conversation as a short tag and can pass a document to a tool.
2. The model can ask for the content of a document, within limits, and a process can opt in to adding content
   without the model asking.
3. The model can return documents with the final response.
4. Documents stay known for the lifetime of the agent instance, independent of the context window and the
   conversation store.

**The flow of a document.**

1. *Tool definition.* A `fromAi(..., "document")` parameter is stored as a string schema with the format
   `camunda-document-id`; the model providers get the schema without the format.
2. *Ingestion.* The composer collects every document of the turn input (prompt, tool results, events), and the agent
   registers it in the document registry of the `agentContext`. The persisted messages hold a typed document reference.
3. *Model request.* When the conversation is windowed, the expander turns the references into what the model reads: the
   tag, and for the delivery `content` the native content after it. It also appends the note about documents that left
   the window.
4. *Tool calls.* A document parameter of a call must be the tag of a known document. The agent answers an invalid call
   itself; a valid call reaches the process with the Camunda document reference in the process variable.
5. *Runtime tools.* `c8__get_document` and `c8__return_document` are runtime tools: the agent runs them itself, in
   process, without a BPMN element.
6. *Response.* The documents the model selected with `c8__return_document` are returned as `responseDocuments`.

**The delivery.** Every document reference carries a delivery, `reference` (the model sees the tag) or `content` (the
model also reads the content). `reference` is the default. The delivery `content` is set by `c8__get_document`, by the
user prompt dropdown *Document delivery* and by the extension property `io.camunda.agenticai.documents.delivery` of a
tool activity.

**Scope.** Out of scope are document parameters of MCP and A2A tools (their schemas come from the remote side and have
no document type), Modeler support and document retention. A gateway handler that defines its tools itself can declare
document parameters (section 2.12).

## 2. Decisions

### 2.1 A typed persisted block and a registry in `agentContext`

**Decision.** A document is persisted in the conversation as a `DocumentReferenceContent`, a typed content block with the
id, the attributes of its tag and the delivery. The complete Camunda document reference lives once, in a document
registry that is a field of `AgentContext` (`documents`), persisted with the rest of the context by the same job
completion.

**Alternatives.**

- *Keep the document as content in the messages.* Every later model call pays for the bytes, an
  inline document is stored several times, and a context window that evicts the message loses the document.
- *Derive the registry from the messages when needed.* Documents nested in tool result objects and documents that
  gateway handlers extract are hard to find again, and a window evicts messages.
- *Store the registry next to the messages in each conversation store.* Three stores (in-process, Camunda document,
  AWS AgentCore) would each need an implementation, and the registry would not commit atomically with the job.
- *A text tag in a `TextContent`.* The id and the delivery would have to be parsed back from text, and a tag that a
  user typed would be indistinguishable from one the agent wrote.

**Trade-offs accepted.**

- `agentContext` grows with every document of the conversation (about 400 bytes per Camunda document), and the registry
  is never pruned. The bound is the broker message size of the job completion, 4 MB; it matters only together with the
  in-process store or inline documents.
- The delivery is stored on each block, not per document. An earlier block keeps its bytes when the same document gets
  another delivery later, so the request prefix stays byte-stable for prompt caching and stored messages are never
  rewritten. The cost is that the history can show the same document with different deliveries.
- An inline document holds its content in the registry, in the tool result and in the response documents. The registry
  rejects an inline document above 1 MiB of content (`INLINE_DOCUMENT_TOO_LARGE`) instead of letting the job completion
  fail with an opaque broker error. There is no fallback to a reference-only entry; several documents below the limit
  can still add up past the broker limit.
- No schema version bump: a missing field binds to an empty registry, so persisted state of earlier versions keeps
  loading (section 2.13 for what happens to those instances).

### 2.2 Short tags only; the model never sees store ids

**Decision.** The model sees `<doc id=".." fileName=".." contentType=".." size=".." source|toolName+toolCallId />` and
nothing else. The id is a stable id derived from the document; store id, content hash, URL and message ids never reach
the model, in tool results, in tool arguments or in notes.

**Alternatives.**

- *Show the Camunda document reference JSON.* It leaks internals,
  an external URL can be a presigned URL that carries a token, and the model has to copy a long structure to refer to a
  document.
- *Index numbers per conversation.* Short, but not stable across context windows, stores and agent instances, and a
  tool result with a nested document has no natural number.

**Trade-offs accepted.** The tag is the only identity the model has, so a document without a file name of its own (the
file name equals the id) is distinguished by content type and tool origin only. `contentType` is omitted for external
documents because their metadata is available only after a download; `size` is present only when the reference
metadata carries it, because the tag is never rendered by reading the document.

### 2.3 References by default; the delivery `content`; `c8__get_document`

**Decision.** A document is a reference by default. Content enters the request in two ways that share one guard and one
persisted form: the model calls `c8__get_document`, or the process opts in with the delivery `content`.

**Why references.** Content costs tokens in every later request until its message leaves the window, can make a request
fail when the provider cannot render the type, and the model usually only needs to pass a document to a tool. A tag
costs a few dozen tokens, so a conversation can carry any number of documents.

**Why `c8__get_document` is a tool the model calls.** The model knows whether it needs to read a document. The tool
loads the whole document (no ranges) and rejects, with a tool result the model can act on and without an incident, a
document that is already in the window, of a type outside the allowlist or above the size limit. The answer tells the
model to pass the tag of such a document to a tool that processes it, for example a sandbox.

**Why the delivery `content` exists.** A process that knows the model needs the content (a prompt that always comes with
a short PDF, a tool that returns a text file) would otherwise pay one model round trip per document. The delivery
`content` removes that round trip. It is an opt-in at two places, the user prompt and the tool activity, because those
are the places where the process designer knows what the document is. Events, MCP tools and A2A tools stay references;
a gateway handler can set the delivery on the definitions it creates. A delivery per document or per document type would
be additive.

**Relation between the two.** The delivery `content` calls the same `DocumentContentGuard` as `c8__get_document` and
produces the same persisted form, a follow-up user message with a reference block with the delivery `content` that
leaves the window together with its tool results. A runtime tool asks the agent for the same behavior with
`RuntimeToolResult.withDocumentContent`; `c8__get_document` is one such tool. A document whose content is in the window
is never added twice. When the guard rejects a document in an opt-in, the run does not fail: the document stays a
reference and a note tells the model that no content was added and why, because the model could not have read the
document anyway.

**Guard.** The allowlist is provider-independent (text-based types, PDF, PNG, JPEG, GIF, WebP) and the limit is 256 KB
for text and 10 MB for PDF and images, read from the reference metadata and, without it, by reading at most one byte
more than the limit. A document of unknown type, typically an external one, is treated as text with the stricter limit
rather than rejected. Bytes are a poor proxy for tokens (a PDF of 214 KB can cost about 230,000 input tokens), and a
provider without support for a listed type fails at the model call. Both are accepted until a token-based guard and a
provider capability matrix exist.

**Content is sticky.** A loaded document stays in every request until the message that holds it leaves the window. An
explicit removal or summarization would need its own design.

### 2.4 Where the content comes from at request time

**Decision.** The content of a document is never persisted in the conversation. The expander reads it from the registry
when the conversation is windowed, so the stored conversation stays small and replayable and the registry holds the
only copy of the reference.

**Trade-off.** The persisted conversation is not self-contained: replaying it needs the registry of the same
`agentContext`. Both are written by the same job completion, so they stay consistent.

### 2.5 Only the tag identifies a document

**Decision.** A document parameter resolves only a value that is exactly one `<doc id="..." />` tag, surrounded by
whitespace at most. A bare id is invalid for a plain document parameter. The built-in tools' `id` and `document`
parameters are document parameters and take the tag as well.

**Alternatives.**

- *Accept an id or a tag.* Friendlier to a model that copies only the id.
- *Accept an id found anywhere in the text.* Lenient, but a tool argument that happens to contain an id would be
  replaced.

**Why the tag only.** The model reads tags, not ids, so asking for the thing it reads is the least surprising
contract. It also makes unions (text or a document) unambiguous. A union parameter is a schema node with `anyOf` or
`oneOf` alternatives of which one is a document schema, for a tool that takes a message body, a note or a file. Its
value is a document only when it is exactly one tag. A plain string stays text even when it equals a known id, because a
word that equals an id must never turn into a document by accident; a bare id is therefore text for a union and invalid
for a plain document parameter. Unions are a later slice of the delivery plan, but the shape of the tag is fixed now so
that nothing has to change when they ship.

**Trade-offs accepted.** A model that sends a bare id gets an error and retries once. The error is only the problem
sentences, one per invalid value, without a list of known documents: the model has read the tags in its conversation (and
in the evicted-documents note), so the sentence only needs to say what was wrong. For a union parameter, a runtime tool
cannot tell a bare id the model wrote as text from the id of a tag, because it receives the id of a tag and any other
text as it is. The alternatives of a union are not searched for nested documents: a value cannot be assigned to one
alternative reliably.

### 2.6 Document parameters in tool definitions

**Decision.** A document parameter is stored as a plain JSON schema node, `{"type": "string", "format":
"camunda-document-id"}`, with a hint appended to its description by the `ToolDefinition` constructor. The model
providers get a derived schema without the format. `DocumentParameterSchema` finds the parameters by walking the
schema (top level, array items, nested objects) and `DocumentParameterResolver` walks the same schema the same way.

**Alternatives.** A custom `type: document` in the stored schema would force every consumer of the persisted
definition, the Ad-Hoc Tools Schema Resolver output and the gateway handlers to know a non-standard type. A separate
field on `ToolDefinition` next to the schema would have to stay in sync with it.

**Trade-offs accepted.** Because the stored definition holds the text the model sees, a change of the hint changes
the configuration fingerprint. Another LLM connector that uses the resolver output directly would send the format to
its provider and needs the same rewrite (a follow-up). The shorthand type `document` of `fromAi` is normalized only for
`fromAi` parameters; a gateway handler writes the stored form itself.

### 2.7 Runtime tools run in process

**Decision.** `c8__get_document` and `c8__return_document` are runtime tools: tools the agent runs itself, inside the
job, next to the tools of the process.

**Alternatives.**

- *BPMN elements.* A tool element for each of them in every process would make them visible and replaceable, but a
  job round trip per call is slow, they need the document registry that lives in `agentContext`, and every modeler
  would have to add them correctly.
- *Special-case them in the loop.* The loop would depend on two tools and every future tool (loading a skill, saving a
  memory) would add another branch.

**SPI shape.** `RuntimeTool` has three methods: `definition()`, `isAvailable(executionContext, agentContext)` and
`call(call, executionContext, conversation)`. A custom runtime registers a tool by implementing the interface and
exposing a Spring bean. Nothing else is needed: no builder, no schema helper, no handler type. A tool builds its
definition with `ToolDefinition` and `DocumentParameterSchema.documentSchema` and binds the arguments with
`call.argumentsAs(...)`. This keeps the public surface to one interface and three result types (`RuntimeToolCall`,
`RuntimeToolResult`, `RuntimeToolConversation`), at the price of a few lines of boilerplate per tool; builders were
rejected as more API to maintain than they save.

**Result and conversation.** A `RuntimeToolResult` carries content, an error flag, context updates and the ids of
documents whose content the agent should add (`withDocumentContent`). Any runtime tool can ask for content; a process
tool cannot. The tool runs the `DocumentContentGuard` and does not name a document that is already in the conversation.
The `RuntimeToolConversation` handed to the tool is an immutable record of the agent context and the windowed messages,
built by the handler, not the `AgentConversation`: a tool can read but cannot reach the copy-on-write methods. State
changes only through context updates, because an agent runs one job after the other, possibly on different workers.

**Errors.** Expected failures are `RuntimeToolResult.error` or `RuntimeToolException`, answered as text the model reads.
Any other exception propagates and fails the job: a runtime tool is code of the connector or of the operator, and an
unexpected exception must not turn into text the model acts on. Other frameworks hand every exception to the model by
default; this is a deliberate difference.

**Dogfooding.** The built-in tools live in `tool/runtime/builtin` and use only this API: no special handling exists in
the handler, the composer or the registry. A built-in can be replaced by a bean of the same name.

**Names.** A tool element of the process must not have the name of a registered runtime tool, whether the tool is
available or not: such an element would be indistinguishable to the model and lose against the runtime tool silently,
and the availability of a tool can change while the agent runs. Tool discovery and process migration fail with
`RUNTIME_TOOL_NAME_CONFLICT`. The prefix `c8__` is a convention for built-ins, not enforced; custom tools should use a
vendor prefix.

**Availability.** `isAvailable` must depend only on the configuration of the invocation and on the features of the
agent instance, never on state a tool call changes. The built-ins are available for instances with the `documents`
feature. They are offered to every such instance and cannot be switched off; whether they should become configurable
is open (section 4).

**Other frameworks.** LangChain4j, Spring AI, the MCP Java SDK, the Anthropic tool runner and the OpenAI Agents SDK
pair a definition with an executor, bind JSON arguments to a type and pass a read-only context. The runtime tool SPI
follows that shape. It differs in three places that follow from the agent: state goes through persisted context
updates, an unexpected exception fails the job, and document parameters are declared in the schema and checked by the
framework before the tool runs.

### 2.8 Mixed batches defer the runtime calls

**Decision.** When the model issues runtime calls and process calls in one message, only the process calls go to the
process. An all-runtime batch is answered in the same job (a synthetic round, which counts against `maxModelCalls`). In
a mixed batch the runtime calls, and the rejection answers for invalid document parameters, are answered when the next
turn is composed, merged with the process results in call order. An interrupted turn still answers them. Their
context updates, such as the response documents of `c8__return_document`, are applied at that point, and the history
shows their results with the turn start of that job.

**Alternatives.**

- *Run the runtime calls in the issuing job and persist their results until the process results arrive.* That needs a
  persisted field of pending results in `agentContext`, a consumption rule so they reach exactly one turn, and a
  separate history write. The answers are a function of the conversation, so they can be computed again instead of
  persisted.
- *Run every runtime call in its own synthetic round and the process calls afterwards.* It costs one model call per
  mixed batch and changes what the model sees mid-batch.
- *Make the model split batches.* Not controllable.

**Why deferral.** The real calls are valid work the model asked for, and running them without an extra model call
keeps a mixed batch at one round. The runtime calls are cheap, deterministic functions of the conversation, so
answering them one job later costs nothing the model can observe, and no state has to survive a job boundary.

**Trade-off accepted: classification drift.** The calls of a turn are classified again when the next turn is composed.
If the availability of a runtime tool changes between the two jobs, a call can be classified differently: a call that
was answered by the agent and is now a process call looks like a missing result and the instance stays deferred until
an event interrupts it; a call that went to the process and is now a runtime call runs the tool, although the element
already ran. A deployment in which workers have different runtime tool beans can trigger this too. The rule that the
availability of a tool must not change within one turn is the mitigation; the alternative, persisting the
classification, was rejected as the state it needs to carry is exactly what this design avoids.

**Accepted consequence.** The model receives the results of runtime calls in a mixed batch only with the next request.
A real call that never completes holds that request back, as it holds back every other result of the batch. Response
documents of a mixed batch are applied when the next turn is composed.

### 2.9 The expander renders text; native tool-result documents later

**Decision.** `DocumentExpander` is the single place that turns persisted documents into what providers see. Documents
in tool results are rendered as text: a top-level reference becomes the short tag as a `TextContent`, a document nested
in an object becomes its tag string. Providers have no document-specific code for tool results. The expander is applied
through a decorator of the window, `AgentConversation.window(size, decorator)`; the decorator for an instance without
the `documents` feature is the identity, so those instances keep rendering a `Document` as its reference JSON.

**Alternatives.**

- *Render the tag in every provider converter.* Six converters, each with its own tests, and a rule that has to be
  kept in sync.
- *A marker interface and a global serialization mode.* Hidden state that is easy to get wrong for the instances that
  must not change.

**Trade-offs accepted.** No provider receives a document inside a tool result, so the content of a document reaches the
model only in a user message that follows the tool results, where the tag precedes the native block. Some providers
and models accept documents in tool results; that is an optimization, not a requirement.

**Lifting it later.** Native documents in tool results would be one more branch in the expander plus provider code. The
persisted form needs no change: the follow-up message with a reference with the delivery `content` records that
the content was added and why, not where it is shown. A capability matrix would let the expander ask whether the current
model reads the content type and accepts documents in tool results. If not, the tag stays and a note says that no
content is shown to this model, and the call does not fail. If yes, the content is placed in the tool result of the
tool call that carries it, derived from persisted data (the `c8__get_document` call whose argument names the document,
or the tool call in the registry source of a tool with the delivery `content`). A model switch needs no migration:
the window is built on every call, so the next request is derived for the new model. Providers whose wire format for
documents in tool results has no end-to-end test need one first.

### 2.10 The evicted-documents note

**Decision.** When the window hides the message that introduced a registry document, a transient user message lists
its tag after the messages: the preamble "Documents from earlier messages that are no longer part of this conversation
context. They are still available by tag:" and a reference block per document. The note is built per request, is never
persisted and never part of `allMessages()`.

**Alternatives.** Adding to the system prompt breaks the frozen-per-instance prompt; rewriting a stored message breaks
byte stability for replay and prompt caching. As a suffix the note is the only thing that changes when the window
slides, so cached prefixes stay valid. It is skipped after an assistant message so a continuation round does not
repeat it.

**Visibility.** A document is visible when its id appears in the windowed messages as a document, a reference or a
nested document in a tool result, or when the message that introduced it is windowed. Text is never read, so a tag a
user or the model typed does not make a document visible.

**Trade-off accepted.** The note is not reported to the agent instance history (section 4). A document that left the
window can be passed to a tool or loaded again, within the guard, but a model that needs it may have lost context
around it.

### 2.11 Returning documents

**Decision.** `c8__return_document` selects one document per call under a key; the final response carries all selected
documents as `responseDocuments`. Selections live in `AgentContext.responseDocuments` because the tool can be called in
an earlier job than the final response, and stay for the lifetime of the instance, so a later final response (after user
feedback, for example) carries the earlier documents too. A key used again replaces its entry in place.

**Alternatives.** *Interpret `<doc/>` tags in the final text.* The model writes tags in prose for other reasons, so
this returns documents nobody selected. *An opt-in response option.* The tools are always offered for new instances;
the response field is omitted when empty.

**Trade-off accepted.** No retract: a key can be replaced but not removed. The returned reference is a registry
snapshot, including inline content.

### 2.12 Gateway tools

**Decision.** Document parameters are resolved on the raw tool call, by the definition the model saw, before the
gateway transformation. A gateway handler that defines its tools itself declares document parameters in the stored
form and receives the Camunda document references in the arguments it wraps; an invalid call is answered with an error
and the handler is not given the call. It can also set the delivery on the definitions it creates. The MCP and A2A
handlers declare no document parameters and set no delivery, so their tools take no documents and their documents stay
references. Documents they return are ingested and passable to ad-hoc tools and to handlers that declare document
parameters.

**Why not MCP and A2A now.** The MCP schema comes from the remote server and has no document type; passing documents
needs a mapping from the remote schema and from a document reference into the format the remote side expects.

### 2.13 Features per agent instance, and instances without the feature

**Decision.** `AgentContext.features` lists the features an agent instance was created with (`runtimeTools`,
`documents`). It is assigned when the instance is created and never added later. An instance without the `documents`
feature keeps the document handling it was created with: documents are content in the conversation in the older tag
format, there is no registry, no response documents, no document parameters, and its tool results render a `Document` as
its reference JSON. An instance without the `runtimeTools` feature is offered no runtime tool and checks no tool name,
because its process, which may already contain an element with such a name, must keep running.

**Alternatives.** *Migrate running instances.* Rewriting stored messages is not possible for an append-only store, and
a conversation with messages in two forms is hard to reason about. *A global switch.* It would change every running
instance at once.

**Trade-off accepted.** A running instance cannot use document parameters; it has to be started anew, and the
schema of its tools after a process migration still carries the hint that asks for a document it has no tag for. The
legacy path is a second code path in the composer, the window, the history mapper and the response handler, gated on
`AgentContext.supports(..)` and pinned by a golden conversation of Camunda 8.10. A split of the composer into a legacy
composer and a document composer was measured and rejected because it adds lines; the document handling stays inside
`AgentConversationTurnInputComposerImpl`.

**Downgrade.** An older connector runtime ignores the new fields and drops them on its next write, and reads a
persisted reference block as unknown content. An instance an upgraded runtime reads after that has neither features nor
registry and is handled as an instance without the feature.

### 2.14 The configuration fingerprint

**Decision.** The fingerprint of `AgentConfiguration` is a SHA-256 over a Jackson serialization with sorted keys of the
model, provider, system prompt, model-call limit and tool definitions (the process tools and the available runtime
tools). A `CONFIGURATION` history item is reported only when the fingerprint changes.

**Why.** A `ToolDefinition` rebuilt through its builder or a JSON round trip holds its schema in an immutable map of
unspecified order, so a hash of its `toString` would differ between workers and JVMs. Sorting the keys of the
hash input makes it stable without touching the order of what is sent to the model and persisted. Lists keep their
order, so the order of tools is significant.

**Trade-off accepted.** An in-flight instance reports one extra `CONFIGURATION` item after the upgrade, and a change of
the hint or of a runtime tool definition reports a new one for running instances. Number types are not normalized
(`1` and `1.0` hash differently), which is harmless for schemas that are authored once.

### 2.15 History

**Principle.** The agent instance history records what the model sent and received, in a representation suited to the
history; process data stays in the tool element variables. The history therefore shows tags in backticks (so a markdown
renderer shows them) and the documents of references next to them, the arguments of a call as the model sent them (a
document parameter holds the tag), and runtime tool calls and results without an element id. It does not show the
Camunda document reference a tool received, which is process data.

**Audit.** Each message of a model request is covered by a history item: the system prompt by the `CONFIGURATION` item,
user messages by `USER` items, assistant messages by `ASSISTANT` items and tool results by `TOOL_RESULT` items. The
representations may differ: a document with content is a `DOCUMENT` block next to its tag in the history, the tag plus
the native block in the request; a tool result with a document is its tag followed by the document. The history shows
the document next to every reference of a user message, so it is a superset of what the model receives. A test records
every model request of a document flow and requires each message to be covered by an item; the only message it lists
as not covered is the evicted-documents note.

**Mixed batches.** The history shows the results of runtime calls with the turn start of the job that composes them. The
engine deduplicates items by `historyItemId`, so results that more than one job writes appear once.

**Limits of the history API.** It has no tool kind and no result status, so a runtime tool is recognizable only by the
missing element id and an error result is plain text; tool input schemas are not reported; the documents the agent
returns appear only as the text of the `c8__return_document` result and as the tags in its arguments.

## 3. Test strategy

Unit tests cover each rule where it lives: schema walking and the provider rewrite, the resolver and its error texts,
the composer (document references, sources, the merge of runtime and process results in call order, partial arrival,
interrupts), the expander and the window decorator, the guard, the built-in tools, the runtime tool handler with a
test-only stateful tool, the history mapper, the fingerprint and the persistence round trips. A golden conversation of
Camunda 8.10 pins the behavior of instances without the feature. End-to-end tests run against a WireMock model, with a
placeholder transformer that lets scripted tool calls name the ids of documents the store generated. The sub-process
flavor runs the detailed matrices: the round trip with each conversation store, unknown tags, eviction, loading by type
and size, the delivery `content` for prompts and tools, union parameters, mixed batches with an early report in the
history, and a history audit that reads the history back through the query API and checks that every model-visible
message is covered. The task flavor keeps the round trip, one mixed-batch wiring scenario and the user prompt document
tests, plus tests of instances without the feature. A global assertion checks every recorded model request for store
ids, content hashes and the document type marker. Not covered end to end are real-provider document tests, which need
paid model calls, a gateway handler with document parameters (the unit tests cover it) and the wire format of a
document with content on Bedrock (converter unit tests only, because the event-stream response bodies cannot carry the
id placeholders).

## 4. Known gaps and follow-ups

**Conversation and window**

- The evicted-documents note is not reported to the history; it should become a `USER` item with a content-hash
  `historyItemId`.
- Document content is sticky until its message leaves the window. A budget per turn, a token-based guard or a removal
  mechanism would complement the byte limits.
- Compaction that rewrites or drops messages must keep the registry intact and decide what happens to references with
  the delivery `content` and the follow-up messages that hold them.
- `c8__get_document` loads a whole document. A range (pages, lines, bytes) would be an additive field of the reference
  next to the delivery and an optional argument.

**Providers and guards**

- A provider-specific capability matrix (#7230) would reject a type before the load instead of failing the model call,
  and would drive the native tool-result representation (#7232) of section 2.9.
- A content type sniffed from the bytes is not attempted for documents of unknown type.

**Documents in tools and results**

- MCP and A2A tools take no documents (section 2.12).
- Inline documents are held several times and are capped at 1 MiB. The history mapper cannot map an inline document of a
  user message and fails; a tool result shows its tag only. A reference-only entry for inline documents is open.
- The resolver output of the Ad-Hoc Tools Schema Resolver carries the format `camunda-document-id`; another LLM
  connector needs the same rewrite.
- The marker `io.camunda.agenticai.documents.delivery` is set by hand in the properties panel; a template property
  would make it discoverable. There is no Modeler or linting support for the `document` type of `fromAi`.
- A document that expires in its store is not noticed by the agent; whether the agent should drop registry entries and
  tell the model, or leave expiry to the tool that dereferences the document, is open.
- Documents mentioned only in the content of an evicted assistant message are not tracked, and `<doc/>` tags the model
  writes in the final text are not interpreted.

**Runtime tools**

- The built-in tools are offered to every new instance with no opt-out, which costs input tokens in every call, can
  change tool selection for agents that never meant to use documents, and occupies two tool names. A response option or
  a per-agent allow and deny list of runtime tools is open, and future runtime tools multiply the effects.
- A runtime tool that returns a `Document` as its content gets the document registered and shown as a tag when its
  call is answered in a mixed batch; in an all-runtime batch the document is neither registered nor shown as a tag.

**History**

- An engine API request for a tool kind, a result status and tool input schemas would remove the limits listed in
  section 2.15.
