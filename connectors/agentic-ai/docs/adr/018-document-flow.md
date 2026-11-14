# Document flow: agent documents, document handles and content on demand

* Deciders: Agentic AI Team
* Date: Oct 19, 2026

## Status

**Proposed**. Extends [ADR 004](004-document-handling-in-tool-call-results.md), which stays as it is. Builds on the
runtime tools of [ADR 016](016-runtime-tools.md).

## Context and Problem Statement

A document enters an agent conversation in the user prompt, in event data or in a tool result. With
[ADR 004](004-document-handling-in-tool-call-results.md), the model reads the content of every document. This causes
four problems:

* The content costs tokens in every later request. A request fails when the provider cannot read the file type. Usually
  the model only has to pass the document to a tool.
* The conversation stores a document in every message that holds it. When the window evicts the message, the document is
  lost.
* The model cannot pass a document to a tool. The reference of a Camunda document is long and shows store details.
* The model cannot return a document with the final response.

The agent must give the model these four capabilities:

1. The model sees every document as a short handle and can pass a document to a tool.
2. The model can ask for the content of a document. A process can add the content without a request.
3. The model can return documents with the final response.
4. The agent knows its documents for the lifetime of the agent instance, independent of the window and of the
   conversation store.

The decision applies to the AI Agent Sub-process and to the AI Agent Task. It does not cover document parameters of MCP
and A2A tools, Modeler support or document retention.

## Decision Drivers

* **Tokens**: content goes into a request only when the model needs it.
* **No store details for the model**: store ids and content hashes never go to the model or to tool arguments. The
  history shows the document chip with the Camunda document reference, as for any document.
* **Stable requests**: the bytes of a request prefix do not change between requests, for prompt caching. The agent never
  changes a stored message.
* **Independence from the window and the store**: a document stays known after the window evicts its message. A
  conversation store needs no new code.
* **Running instances keep running**: persisted state and instances of earlier versions load and behave as before.
* **No opaque failures**: the agent checks a limit of the broker before it completes the job, and gives an error that
  the operator can fix. A limit of an LLM provider, such as the supported file types, fails the provider call.
* **No privileged built-ins**: the document tools use the public runtime tool SPI, as a custom tool does.

## Considered Options

1. Keep ADR 004: every request contains the content of every document.
2. The model sees handles. The agent context keeps the agent documents. The content goes to the model on request or by
   opt-in.
3. As option 2, but the agent finds its documents in the messages of the conversation and does not persist them.

## Decision Outcome

Chosen option: **Option 2**. A handle costs a few dozen tokens, so a conversation can hold many documents. The model
needs the content only in two known cases: the model asks for it, or the process designer knows it before.

Option 3 is rejected. A window or a compaction that drops a message also drops its documents. The conversation stores a
document in every message that holds it. A document in several messages needs a rule for the message that wins.

### Vocabulary

| Term                  | Meaning                                                                                                 |
|-----------------------|---------------------------------------------------------------------------------------------------------|
| **Agent documents**   | All documents that an agent instance knows. A field of the agent context (`AgentDocuments`).            |
| **Agent document**    | One of them: its document id, its document origin and its SDK reference (`AgentDocument`).              |
| **Document id**       | The stable id of an agent document, calculated from the document (`AgentDocumentId`).                  |
| **Document origin**   | The location where the document entered the conversation: user prompt, event or tool call (`AgentDocumentOrigin`). |
| **SDK reference**     | The reference of the connector SDK to a Camunda document.                                               |
| **SDK document block** | A content block that holds a live document of the connector SDK (`DocumentContent`).                    |
| **Document block**    | A content block that holds a document id and a delivery (`AgentDocumentContent`).                       |
| **Handle**            | The JSON object that the model reads and writes for a document (`AgentDocumentHandle`).                 |
| **Delivery**          | What the model gets for a document block: `reference` (the handle) or `content` (the handle and the content). |
| **Document handling** | How an agent instance treats documents: `Untracked` or `Tracked`, set by the `documents` feature.       |

### Agent documents in the agent context, document blocks in the conversation

The agent context holds the agent documents. This is a flat, append-only set, addressed by document id. The agent never
changes an agent document after it adds it. The job completion persists the agent documents with the rest of the agent
context.

The conversation holds a document as a document block. The block holds the document id and the delivery only. The agent
reads the file name, the content type and the size from the agent document when it writes the handle. Thus the
conversation and the agent documents never hold the same data twice.

Alternatives:

* *Keep the document as content in the messages.* Every call pays for the bytes, and an evicted message loses the
  document.
* *Store the agent documents in each conversation store.* Three stores need new code, and the documents do not commit
  with the job.
* *Store the handle as text.* The agent must parse the text again, and a handle that a user typed looks like a real one.

Consequences accepted:

* The agent context grows with every document. The agent never removes agent documents. The limit is the message size of
  the broker for a job completion.
* A stored conversation needs the agent documents of the same agent context to show its documents. The same job
  completion writes both.
* The field is part of schema version 2 of the agent context, which an 8.10 runtime rejects. When the field is missing,
  the agent documents are empty.

### Document ids

The agent calculates the document id from the document: `doc_` and 12 hexadecimal characters of a SHA-256 hash. The hash
input is the store and the store id of the Camunda document. The same document gets the same id in every job. When a job runs again, it calculates the same agent
documents. When a new document gets the id of a different document, the agent fails with an error. It never merges the
two documents. With 48 bits, this is not expected.

Alternatives:

* *The store id.* It ignores the store, shows an internal id, and can be any string.
* *A random or sequential id.* It needs persisted state and an index to find a document that comes again.
* *A UUID.* It costs more tokens in every handle. 48 bits are sufficient for one agent instance.

### The model reads handles only

The model sees each document as a handle:

```json
{"type":"camunda.agenticai.document","id":"doc_0123456789ab","fileName":"a.pdf","contentType":"application/pdf","size":123}
```

The model passes `type` and `id` to a tool. The other fields are information only. No
other data about a document goes to the model, to a tool argument or to an error text. The handle has a fixed key order,
so the bytes are the same in every request. The handle has no origin, because the message that holds it shows the
origin.

The handle is JSON because the model writes JSON in tool arguments. The model copies the handle into an argument and
needs no conversion. The namespaced `type` makes sure that the agent does not see an unrelated object with an `id` as a
document.

Alternatives:

* *An XML tag*, as in an instance without the `documents` feature. The tag labels the content that follows it. The model
  must convert it to JSON for a tool argument. The tag also shows store details.
* *The SDK reference as JSON.* It shows store details and URLs with tokens, and it is long.
* *A number per conversation.* It is not stable across windows, stores and instances.
* *The origin in the handle.* The same document reads differently in each message. This costs tokens and cache hits.

Consequence accepted: the handle is the only identity of a document for the model. The model can identify a document
without a file name by its content type only.

### Delivery: `reference` by default, `content` by request or opt-in

Each document block has a delivery. With `reference`, the model sees the handle. With `content`, the model sees the
handle and then the content. The delivery is set on each block. Thus an earlier block keeps its delivery when the same
document comes again with a different delivery.

The default is `reference` for tool results and for a user prompt that sets `reference`. A user prompt without a
delivery, as in a v1 template, behaves as before: all documents go to the model as content.
The v2 templates set `reference` as the default.

Content goes into a request in two ways. Both use the same persisted form:

* **On request.** The model calls the built-in tool `c8__load_document`. The model knows when it must read a document.
  The agent writes into the tool result whether the content follows or is in the conversation already. The content
  follows in the next message.
* **By opt-in.** Only the process knows the documents before the model sees them. Thus only the user prompt (a dropdown)
  and a tool activity (an extension property) can set `content`. This saves one model round trip per document. Events,
  MCP tools and A2A tools use `reference`.

The content stays in each request until its message leaves the window.

### No check of the content

The agent adds the content of a requested document without a check of the content type or the size. A document that the
model provider cannot read, or that is above the limits of the provider, fails the model call. The only decision is that
the agent does not add content again when the model reads it already or an earlier request of the same batch asked for
it.

A check in the agent needs a fixed list of content types and one size limit for all providers. This does not fit the
target architecture, where each provider states what it reads. A check of the content against what the model provider
reads is the follow-up
[#10417](https://github.com/camunda/connectors/issues/10417).

### Document parameters

A tool declares a document parameter with the `fromAi` type `document`. The parameter schema is the handle schema: an
object with `type` and `id`. The schema goes to the provider without changes. The model writes into the argument the
same object that it reads in a message.

A value is a document only when it is a handle of a known document. The agent also accepts the handle as a JSON string,
because models often send nested objects as strings. The agent does not accept a bare id, because a word that is equal
to an id must not become a document. For a document parameter, the agent answers an incorrect value with an error
result. The tool does not run.

A document parameter is the parameter itself, the items of an array or a property of an object, at any depth. The agent
fails the job for a `document` anywhere else, such as in an `anyOf` or `oneOf` alternative or in a type array.

The tool schema generation keeps the type `document` as authored. The agent maps it to the handle schema when it loads
the tools of the process, and only for an instance with the `documents` feature. A handle schema means something only
next to the document registry and the resolution of the agent. The output of the ad-hoc tools schema connector has
neither, so it keeps the authored schema.

Union types are not supported. Supporting them is a large step in complexity for a small gain. The agent would have to
match a value against each alternative to find the handles, decide what an ambiguous value is, and report a problem per
alternative, so the schema rules, the resolution and the error messages all grow. Simple alternatives cover the same
cases: a tool that takes text or a document declares two optional parameters, and a tool that takes several documents
takes a list.

A process tool receives the SDK reference of the agent document. A runtime tool receives the document id. The stored
assistant message and the history keep the handle that the model wrote. A gateway handler that defines its own tools can
declare document parameters. The MCP and A2A handlers declare none, because the remote side defines the tool schema.

Alternatives:

* *A custom schema type.* Each consumer of stored tool definitions must know a non-standard type.
* *A string with a format marker.* Some providers reject an unknown format, and each description needs a hint.
* *A separate field next to the schema.* It must stay in sync with the schema.
* *Accept a bare id or an object without `type`.* A text or an object of the tool result can become a document.
* *Union parameters.* Rejected for the complexity described above. Two parameters, an optional property or a list
  cover the same cases.

### Documents and effects of runtime tools

A runtime tool can return documents in its result, also nested in an object. The agent registers the returned documents
after all calls of the assistant message ran, in the order of the calls. The model sees their handles.

A runtime tool does not change the agent context ([ADR 016](016-runtime-tools.md)). A successful result can name
effects. There are two effects: add the content of a known document to the next request, and select a known document for
the final response. The effects are a sealed type, so a new effect does not compile until each consumer supports it. An
error result has no effects and registers no documents.

The agent applies the effects after all calls of the message ran, in the order of the calls. A call does not see the
documents or the effects of the other calls of the same message. The registration and the effects depend only on the
agent context and the results. Thus a job that runs again calculates the same result.

Alternatives:

* *A general update of the agent context.* A tool could change all state, and a failed attempt could not discard it.
* *Apply the effects of a call before the next call runs.* This ties the contract to a sequential run.
* *A method of the tool context that writes state at once.* A failed attempt must undo the change.

### The built-in tools use the public SPI

`c8__load_document` loads the content of a document, as above. `c8__return_document` selects one document under a key.
The final response holds all selected documents as `responseDocuments`. The agent context keeps the selections, because
the tool can run in an earlier job than the final response. A key that is used again replaces its document. A selection
cannot be removed.

Both tools are ordinary beans of the runtime tool SPI. A bean with the same bean name replaces a built-in tool. Both
tools need the `documents` feature.

Alternatives for the final response: *read handles in the final text* returns documents that nobody selected, because
the model writes handles in text for other reasons.

### The expansion before the model call

The conversation never holds the content of a document. Before each model call, after the window, the expansion changes
each document block into the handle text and, for `content`, an SDK document block. A provider never receives a document
block. The chat model SPI states this rule. For an instance without the `documents` feature, the expansion changes
nothing.

Alternatives: *write the handle in each provider converter* needs the same rule in each converter.

Consequence accepted: no provider receives a document in a tool result. The content goes into a user message after the
tool results.

### Documents outside the window

The window does not show a document whose message it removed. The agent documents still resolve the handle of that
document. Thus the model can pass the document to a tool or load it again.

A note that lists the hidden documents is rejected. It costs one handle per document in every request.

### Only Camunda documents

An agent instance with the `documents` feature accepts only Camunda documents. It supports no external document (a URL)
and no inline document (the content is in the reference).

A Camunda document fits the agent documents. Its content cannot change, and it has a content hash. Its metadata (type,
size, file name and expiry) is known without a download, so the history can show it. An external document is downloaded
again in every job, and its URL can serve other content later. This breaks prompt caching and the content checks. An
inline document carries its content in the reference, so the agent would persist the content in the agent context.

A document that is not a Camunda document fails the job with the error code `UNSUPPORTED_DOCUMENT_REFERENCE`, at each
place where a document enters the conversation: the user prompt, the result of a tool and an event. In a tool result, the
document can be nested in an object at any depth, and a runtime tool can return it. The agent checks the documents on
entry, before it registers or stores one. Thus the agent documents, the conversation, the expansion and the history
hold Camunda documents only. The message names the kind of the document and the place, never a file name or a URL.

A tool result and an event cannot change. The incident repeats on each retry until the process passes a Camunda document.

An instance without the `documents` feature keeps its behavior and accepts external and inline documents. This
includes a new instance of a v1 connector.

### Instance features and the legacy path

The agent context lists the features of the agent instance: `runtimeTools` and `documents`. The agent sets the features
when it creates the instance and does not add features later. A new instance of a v2 connector gets both features. A new
instance of a deprecated v1 connector gets none, so a deployed process keeps its behavior. The `documents` feature selects the document handling:
`Untracked` without the feature, `Tracked` with it. Each step that treats documents matches both variants once and hands
the result to the code below it.

An instance without the `documents` feature behaves as before. Documents are content with the XML tag format, and all
kinds of documents are accepted. There are
no agent documents, response documents or document parameters.

Alternatives: *migrate running instances* is not possible for an append-only store. *A global switch* changes all
running instances at the same time.

Consequences accepted:

* A running instance cannot use document parameters. It must start again.
* The legacy path is a second code path. It stays while instances without the feature exist, and the v1 connectors
  create such instances.
* A rollback is not supported. An earlier runtime removes the new fields and cannot read a document block.

### History

The agent instance history shows what the model reads. A document is its handle, as an object block. Where a document
enters the conversation, the history also shows the document, which a consumer shows as a chip. The history never shows
the SDK reference that a tool received.

### Positive Consequences

* A conversation can hold many documents at a few dozen tokens each. Documents stay known outside the window and with
  each conversation store.
* The model can give a document to a tool and return a document, without bytes or store details.
* Persisted state and running instances of earlier versions load and behave as before.

### Negative Consequences

* The agent context grows with the agent documents, and the agent does not remove them.
* A document that a provider cannot read, or that is above its limits, fails the model call until the check of the
  follow-up exists.
* The content goes to the model only in a user message. It stays in each request until its message leaves the window.
* The legacy path is a second code path.
* Each new instance of a v2 connector gets both document tools. This costs input tokens in each call.

## Follow-up

* **`c8__list_documents`.** A built-in tool that lists the known documents when the model needs them.
* **A switch for the built-in document tools.** A setting of the agent configuration, frozen when the instance starts.
* **Per-tool document scoping.** The documents that each tool can receive.
* **A check of the content against the provider.** Before it adds content, the agent checks the content type and the
  size against what the model provider reads
  ([#10417](https://github.com/camunda/connectors/issues/10417)).
* **Documents in tool results.** A capability matrix of the providers lets the agent put content into a tool result for
  models that accept it.
* **A token-based check, a budget per turn or a way to remove content.**
* **External and inline documents.** Support both kinds of reference. The agent passes an external document to the
  provider as a URL and does not download it. It stores an inline document as a Camunda document on entry
  (https://github.com/camunda/connectors/issues/5587).
* **Response documents in the agent documents.** A marker on the agent document replaces the separate selections.
* **Removal of agent documents and a size budget.**
* **Compaction.** It must keep the agent documents and decide what happens to delivered content.
* **Ranges for `c8__load_document`.** Pages, lines or bytes.
