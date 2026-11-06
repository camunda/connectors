# Document flow: agent documents, document handles and content on demand

* Deciders: Agentic AI Team
* Date: Oct 19, 2026

## Status

**Proposed**. Extends [ADR 004](004-document-handling-in-tool-call-results.md), which stays as it is. Builds on the
runtime tools of [ADR 016](016-runtime-tools.md).

## Context and Problem Statement

A document enters an agent conversation as part of the user prompt, as event data or in the result of a tool.
[ADR 004](004-document-handling-in-tool-call-results.md) makes the model read the content: the agent extracts the
documents of a tool result into a follow-up user message, with the content next to a tag.

That does not scale, and it does not cover what agents do with documents:

* The content costs tokens in every later request until its message leaves the context window, and a request fails when
  the provider cannot render the type. The model usually only needs to pass the document to a tool.
* An inline document is stored several times in the conversation, and a window that evicts the message loses the
  document.
* The model cannot pass a document to a tool. The Camunda document reference is a long structure with store details,
  and an external URL can be a presigned URL that carries a token.
* The model cannot return a document with the final response.

The goal is that a document is usable by the model without the conversation growing with its content, and usable by
tools without the model handling bytes or store details. Four capabilities follow:

1. The model sees every document of the conversation as a short handle and can pass a document to a tool.
2. The model can ask for the content of a document, within limits, and a process can opt in to adding content without
   the model asking.
3. The model can return documents with the final response.
4. Documents stay known for the lifetime of the agent instance, independent of the context window and of the
   conversation store.

The decision applies to both flavors (AI Agent Sub-process and AI Agent Task). Out of scope are document parameters of
MCP and A2A tools, whose schemas come from the remote side, Modeler support and document retention.

## Decision Drivers

* **Tokens and provider compatibility**: content enters a request only when it is needed and only when every provider can
  render it.
* **No store details for the model**: store ids, content hashes and URLs never reach the model, its tool arguments or
  the history.
* **Stable requests**: the bytes of a request prefix stay the same across requests, for prompt caching, and stored
  messages are never rewritten.
* **Independence from the window and the store**: a document stays known when its message is evicted, and no
  conversation store needs its own implementation.
* **Running instances keep running**: persisted state and agent instances of earlier versions keep loading and
  behaving as before.
* **No opaque failures**: a limit the broker enforces is checked up front, with an error the operator can act on.
* **Operator control**: the limits for content are configuration, not code.
* **No privileged built-ins**: the document tools use the public runtime tool SPI like any custom tool.

## Considered Options

1. Keep ADR 004: content of every document in the request, as a follow-up user message.
2. References by default, the agent documents in the agent context, content on request or by opt-in (chosen).
3. References by default, with the known documents derived from the messages of the conversation instead of
   persisted agent documents (dismissed: a window or a compaction that drops a message drops its documents, an inline
   document is stored in every message that holds it, and the same document in several messages needs a rule for the
   occurrence that wins).

Option 2 has several decisions of its own, each with alternatives, in the decision outcome.

## Decision Outcome

Chosen option: **Option 2**, because a handle costs a few dozen tokens, so a conversation can carry any number of
documents, and the cases in which the model needs the content are rare and known: it asks for it, or the process
designer knows it up front.

### Vocabulary

| Term                       | Meaning                                                                                                |
|----------------------------|--------------------------------------------------------------------------------------------------------|
| **Agent documents**        | The documents an agent instance knows, a field of the agent context (`AgentDocuments`).                |
| **Agent document**         | One of them: the document id, its origin and its Camunda document reference (`AgentDocument`).         |
| **Document origin**        | Where a document entered the conversation: the user prompt, an event, or a tool call.                  |
| **Document id**            | The stable identity of an agent document, derived from the document (`AgentDocumentId`).               |
| **Document block**         | The content of a message that names an agent document by id and holds its delivery (`AgentDocumentContent`). |
| **New document block**     | The content of a message that holds a live document of the connector SDK (`DocumentContent`).          |
| **Handle**                 | The JSON object the model reads for a document: its type, id and what is known about it (`DocumentHandle`). |
| **Document handling**      | How an agent instance treats its documents: `Untracked` or `Tracked`, derived from the `documents` feature. |

### New and known documents

A document is new or known to the agent, and the two have different content types:

* A **new document** is a `DocumentContent`, the live document of the connector SDK. It is the content at the places
  where a document enters the conversation: the user prompt, events, the results of process tools and a runtime tool that
  returns a new document. It is the content of every document of an instance that does not track its documents. It is
  also the only document block a chat model provider sees, after the expansion.
* A **known document** is an `AgentDocumentContent`, the persisted form of a document for an instance that tracks its
  documents. It never reaches a provider: the expansion turns it into its handle text and, when it is delivered as content,
  a `DocumentContent`.

The content for the model comes only from the agent, through the delivery, never from a block that a tool authors.

### Agent documents in the agent context, typed block in the conversation

The agent keeps the agent documents: a flat set of the documents the agent instance knows, addressed by id. It is a
field of the agent context, persisted by the same job completion as the rest of the context. An agent document holds
the id, the origin of the document and its Camunda document reference in the format of the connector SDK, once. The set is
append-only and has no paths and no listing. An agent document is not changed after it was added; state per document, such
as the selection for the final response, could become part of it later.

A document is persisted in the conversation as a typed content block, `AgentDocumentContent`, that holds the id and the
delivery. What the model is told about the document (file name, content type and size) is read from the agent
document when the handle is rendered, so the conversation and the agent documents never hold the same fact twice.

Alternatives:

* *Keep the document as content in the messages.* Every later call pays for the bytes, an inline document is stored
  several times, and an evicted message loses the document.
* *Derive the agent documents from the messages.* A document would be known only while a message that holds it is in the
  window, and a compaction that drops messages would lose it. An inline document would be stored in every message that
  holds it and grow the variable. The same document can occur in several messages, which needs a rule for the occurrence
  that wins. Documents nested in tool results or extracted by gateway handlers are hard to find again.
* *Agent documents next to the messages in each conversation store.* Three stores would need an implementation, and the
  documents would not commit atomically with the job.
* *A handle as text.* The id and the delivery would have to be parsed back, and a handle a user typed would look like
  one the agent wrote.

Consequences accepted:

* The agent context grows with every document of the conversation, and the agent documents are never pruned. The bound is the
  message size of the broker for a job completion.
* The persisted conversation is not self-contained: a block holds the id and the delivery only, and the handle and the
  content come from the agent documents when the conversation is windowed, so replaying needs the agent documents of the same
  agent context. Both are written by the same job completion.
* There is no schema version bump: a missing field binds to no agent documents, so earlier state loads.

### Document ids

A document has a stable id, the value type `AgentDocumentId`. It is the identity of the document in its handle, in the arguments
of a tool call and in the agent documents. The id is derived from the document: `doc_` followed by 12 hexadecimal characters of
a SHA-256 over the identity of the document. The identity is, by kind of reference, the store and the id in the store of a
Camunda document, the URL of an external document, and the content of an inline document. The same document gets the same
id in every job, so registering a document again adds nothing, and a job that runs again computes the same agent documents. Registering a
document whose id belongs to a different document, one the agent knows or one registered with it, fails with an error
and never merges the two; with 48 bits this is not expected to happen.

The model never reads a store id, the id of a document in its store or a URL; the id is opaque, and short because it is
repeated in every handle.

Alternatives:

* *The id in the store verbatim.* It ignores the store, shows an internal id, and can be any string a producer chose.
* *An id assigned when the document is registered, random or sequential.* It is independent of the document, but needs
  persisted state, and an index from the identity of a document to its id to recognize a document that comes again. A
  document nested in a tool result has to be matched by identity anyway.
* *A longer id, such as a UUID.* It costs tokens in every handle, and 48 bits are enough for the documents of one agent
  instance.

Consequences accepted: the identity of an external document is its URL, so a presigned URL that changes gives the same
file a new id; two inline documents with the same content are one document.

### Documents of runtime tools

A runtime tool can return a document in its result, also nested in an object. The agent registers the documents of the
results of one assistant message after all its calls ran, in the order of the calls, with the call as their origin, and
shows the model the handles of the documents in place of the documents. This happens in one place that the composer of a
mixed batch and the runtime tool round of a job share, so both give the same result. A result that is an error registers
nothing.

A runtime tool reads the documents the agent knows when the batch of calls starts. The calls of one message do not see
the documents the other calls returned, and the next turn knows them. This follows the contract of
[ADR 016](016-runtime-tools.md): the calls of one message may run concurrently and a call must not depend on another
call of the same message. The registration is a function of the agent context and the results only, so a job that runs
again from the same context computes the same agent documents.

Alternatives: *register the documents while the calls run* lets a call see the documents of an earlier call and ties the
contract to a sequential execution; *a method of the tool context that registers a document* gives a tool write access
to the state of the agent, which ADR 016 avoids.

### State changes only through the named effects

A runtime tool does not change the agent context ([ADR 016](016-runtime-tools.md)). What the document flow needs beyond
the content of a result is a request to the agent, and a successful result names it. There are two effects: add the
content of a known document to the request that follows the tool results, and select a known document for the final
response. The effects belong to a successful result only. An error result has none, so a failed call never applies half of
what it asked for. The effects are a closed set, a sealed type, so a new kind of effect fails to compile wherever effects
are applied.

The agent applies the effects after all calls of a message ran, in the order of the calls, in the place that registers
the returned documents and builds the input of the next turn. The composer of a mixed batch and the runtime tool round of
a job share that place, so both give the same input. A call neither sees nor depends on the effect of another call, which
follows the contract of ADR 016. Whether an effect is applied depends on the agent context, the results and the documents
whose content the model reads as of the start of the batch only, so a job that runs again computes the same input. A
result can name a document it returns itself. A document that nobody knows is a defect of the tool and fails the job. The
content guard checks every document: when it rejects one, the document stays a reference and the agent says so in the
tool result. For every document a result asks content for, the agent writes one sentence at the end of that result, after
the content the tool returned: the content follows in the next message, the content is not loaded and why, or it is in the
conversation already. The tool writes none of it, because the agent decides where the content goes. A document whose
content the model reads already, or that an earlier result of the same message asked for, is not added again.

The selection of a response document changes the agent context, which is the one state a tool can reach. The place that
applies the effects therefore hands the selections on with the input, and the caller applies the input to the agent
context it persists, together with the documents the input registers (`TurnInput.applyTo`). The selections are kept in the
order of the calls, and a key that is already used, by an earlier call or an earlier turn, keeps its position and gets the
new document. The update is a function of the agent context and the results only, so a job that runs again from the same
context computes the same context. The selection of a result whose content the guard rejects is applied all the same. Besides the registered documents of the results, the
response documents are all a tool changes in the agent context; metrics, tool definitions, the conversation and the
properties stay with the agent.

Alternatives:

* *A general update of the agent context* (a function from the context to the context). Rejected: a tool could rewrite
  anything, including metrics and persisted tool definitions; effects could not be discarded with a failed attempt or
  applied in a defined order; and the SPI would be bound to the whole shape of the context.
* *State per tool.* Rejected: it needs a namespace in the agent context and a migration story, and no tool needs it. The
  author keeps no state in the bean, because the agent runs on any worker.
* *A property of the agent context, set by a tool.* Deferred: the properties of the context hold the state of the
  gateway tools (the MCP and A2A client elements), and a tool needs its own key namespace first. It is added when a tool
  needs it.
* *Apply the effects of a call before the next call runs.* Rejected: a call would see what earlier calls did, which ties
  the contract to a sequential execution.
* *A method of the tool context that adds the content right away.* Rejected: it gives a tool write access to the state of
  the agent while it runs, and a failed attempt would have to undo it.

### The model reads document handles only

The model sees a document as a small JSON object, its handle:

```json
{"type":"camunda.agenticai.document","id":"doc_0123456789ab","fileName":"a.pdf","contentType":"application/pdf","size":123}
```

The keys come in this order: `type`, `id`, `fileName`, `contentType`, `size` and `note`. Everything but `type` and `id` is
omitted when it is not known, and `size` is a number. The handle is read-only information apart from `type` and `id`,
which are what the model passes to a tool. `note` is a sentence about the document that only the agent writes. Nothing else about a document reaches the model, a tool argument or an error text. The handle
sits in the message it belongs to: in a user message as a text block before the document, in a tool result as text, and in
the object of a tool result in place of the document, as a nested JSON object. It is written by Jackson from a record with
a fixed key order, so the bytes are the same in every request, which keeps the prompt cache valid.

The handle holds no origin. The message it sits in tells where the document came from: the user prompt, an event or the
result of a tool call.

Why JSON and a namespaced `type`:

* The model writes JSON in tool arguments, so the form it reads is the form it writes back to pass a document. A handle it
  copies into an argument needs no parsing of attributes or XML escaping.
* The `type` marks the object as a document for the agent and for the schema of a document parameter, so a tool argument
  that merely has an `id` field is never taken for a document, and a namespaced value is not a word another object of a
  tool result uses as its own type.

Alternatives: *an XML tag* (`<doc id="..."/>`) needs escaping, a parser that tells it from text a user typed, and is not what
the model writes in an argument; *the Camunda document reference as JSON* (the format of the connector SDK) leaks the store,
the document id in the store and, for an external document, a URL that can carry a token, and is long to copy; *index
numbers per conversation* are not stable across windows, stores and instances, and a nested document has no natural number;
*an origin in the handle* repeats what the surrounding message shows and makes the same document read differently in every
message, which costs tokens and cache hits.

Consequence accepted: the handle is the only identity the model has, so a document without a name of its own is told apart
by type only. The type of an external document is not in its handle, because its metadata is known only after a download.

### Delivery: `reference` by default, `content` as an opt-in

Every document reference carries a delivery. With `reference` the model sees the handle only. With `content` it also
reads the content after the handle. The delivery is stored on each block, so an earlier block keeps its bytes when the same
document gets another delivery later; the cost is that the history can show one document with two deliveries.

The default is `reference` for the documents of a tool result and for the documents of the user prompt when the process
sets `reference`. A user prompt without a delivery, as in a process modeled before the option existed and in every v1
template, behaves as before: every document goes out as content, with no content guard, and a failure of the provider
stays a failure of the provider. An explicit `content` runs the guard. The v2 templates preset the dropdown to
`reference`, so a newly modeled process chooses it. The documents of an external URL in the user prompt always go out as
content, without the guard, whatever the delivery: the provider reads them.

Content enters the request in two ways, which share one guard and one persisted form:

* **On request.** The model calls the built-in tool `c8__load_document`. The model knows whether it needs to read a
  document, and the tool result tells it when a document cannot be loaded. The tool returns no text of its own: the agent
  writes into the result that the content follows in the next message, or that it is not loaded and why. The message with
  the content starts with the preamble `Document content from tool calls (added by the system):` and holds the handle and
  the document of each document that is loaded. It loads the whole document. A range would be additive.
* **By opt-in.** The process knows what a document is at two places, so only they can opt in: the user prompt, with a
  dropdown, and a tool activity, with an extension property. This removes one model round trip per document for a prompt
  that always comes with a short PDF or a tool that returns a text file. Events, MCP tools and A2A tools stay references;
  a gateway handler can set the delivery on the tool definitions it creates.

Content is sticky: a loaded document stays in every request until the message that holds it leaves the window. An
explicit removal or a summary would need its own design.

### The content guard

One guard decides every addition of content, whoever asks:

* It classifies a document by content type with the classification of the model providers (text-based types, PDF, PNG,
  JPEG, GIF, WebP). A document the guard lets pass is one every provider renders. A document without a content type is
  not loadable, because a provider fails the call for it.
* The limits per kind (text, PDF, image) are operator configuration, not a setting of a job. Their defaults are below
  the limits of the most restrictive supported provider and apply to every provider.
* **An external document is treated like every other document.** The guard checks it after the download, with the size
  of the metadata (`Content-Length`) when known and the length of the downloaded bytes otherwise, and the content type
  of the response. The user prompt keeps its behavior: the model provider reads the content of an external document in
  a user prompt whatever the delivery, as it reads every document of a user prompt.
* What the model reads about a rejection is a standalone sentence with the content type, the size and the limit at most,
  so that it holds no id; the agent puts it behind the name of the document in the tool result. The text is persisted and a failure message
  can hold a URL with a token, so the cause goes to the log only.
* A rejection leaves the document a reference and says so in the tool result, whoever asked, and is no incident: the content of the result of the tool
  and the documents it returns stay as they are, and the model could not have read the document anyway.

Bytes are a poor proxy for tokens (a small PDF can cost many input tokens), and limits that are the same for every
provider are as strict as the strictest one. Both are accepted until a token-based guard and a provider capability
matrix exist.

### Only the handle identifies a document; document parameters

A tool declares a parameter that takes a document with the type `document` of `fromAi`. The stored schema is the schema of
the handle, an object with the required properties `type` (the one value `camunda.agenticai.document`) and `id`, and it goes to
the model provider as it is. The model reads a handle in a message and writes the same object into the argument, so the
schema asks for what the model has seen. The persisted definition needs no custom type and no marker, and nothing is derived
or added where the provider tool definitions are built: the stored definition, the configuration fingerprint and the request
hold the same wording. The schema sets no `additionalProperties: false`; a provider whose strict mode needs it adds it, and the
agent ignores extra fields, so a model that copies `fileName` or `size` along with the handle is not rejected.

A value resolves only when it is a handle of a known document, and one rule defines a handle everywhere: an object with the
`type` `camunda.agenticai.document`, or a string that is exactly such an object as JSON, surrounding whitespace ignored. The `type` is
required: an object with only an `id` could be any object a tool result holds, and the `type` is what makes the model's intent
unambiguous. Other fields of the object are ignored. The JSON string is accepted because models and tool-calling APIs stringify
nested objects often enough that rejecting the call would cost a round trip for no gain. A string that is the bare id of a document is
not a handle. The error text corrects the model in one round trip, and a lenient reading would let a word that equals an id turn
into a document by accident. The schema advertises the handle object only. A parameter that always takes a document gets an
error for any other value, an unknown id included, and the agent answers the call with it, without the tool running and without
activating a process element. The error depends on the value: it tells the model to pass no document when the agent knows
none, names an id that is not known, and otherwise names the expected shape, so that a model that invents an id learns that the
conversation has no such document. Alternatives: *accept a bare id too* lets a text that equals an id become a document and makes
the contract two-shaped; *accept an id found anywhere in the text* would replace a tool argument that happens to contain an id;
*accept an object without a `type`* cannot tell a handle from another object in a union. The model reads handles, so asking
for what it reads stays the contract. A later step could validate the arguments against the schema per provider; the agent does not
yet.

Parameters that take a document or something else are unions: an `anyOf` or `oneOf` with more than one alternative and a
handle schema anywhere inside its alternatives, directly, in the properties of an object alternative, in the items of an array
alternative or in a nested union. The type array shorthand is normalized in the same places. Typical unions are one document or
a list of documents, and text or a file with a caption. The agent does not have to know which alternative the model matched: the handle
identifies itself by its `type`, so it scans the value of a union, not the schema. Every handle at or below the value is a
document, and its id must be known. Everything else, text (also a string that equals a known id), numbers, and the rest of an object
or an array around its handles, is passed as it is to the tool, which may take it for another alternative of the union, such as a
structured value; this holds also for a union without a text alternative. The search for document parameters stops at a union. It does not
reach the keywords `allOf`, `not`, `if`, `then`, `else`, `additionalProperties`, `patternProperties`, `prefixItems` or `$defs` and
`$ref`, where a handle schema is not recognized and the shorthand stays as authored.

A process tool receives the Camunda document reference of the agent document, a runtime tool the `AgentDocumentId`. Passing a
handle to a tool never adds content. The agent resolves the parameters on the call the model made, before a gateway handler
transforms it, as a function of the call, the tool definition and the agent documents only, so a job that runs again from the
same context computes the same result. The stored assistant message and the history keep the handle the model wrote; the
reference exists only in the call that goes to the process. A gateway handler that defines its tools itself can declare document
parameters and receives the references. The MCP and A2A handlers declare none: the schema of an MCP tool comes from the
remote server and has no document type, so passing documents needs a mapping into the format the remote side expects.

Alternatives for the schema: *a custom `type: document`* would force every consumer of persisted definitions to know a
non-standard type; *a string with a format marker and a hint in the description* is rejected by providers that do not know
the format, needs a rewrite per provider and puts a text the model must follow into every description; *a separate field
next to the schema* would have to stay in sync with it.

### The built-in tools

`c8__load_document` loads a document, as above. `c8__return_document` selects one document per call under a key, with an
optional description, by the effect of its result, and the final response carries all selected documents as
`responseDocuments`. The selections live in the agent context, because the tool can run in an earlier job than the final
response, and stay for the lifetime of the instance, so a later final response carries them too. A key used again replaces
its entry in place. There is no retract. A call with an invalid key, a missing parameter or an unknown document is answered
with an error result and selects nothing. The final response carries the Camunda document reference of each selected
document, which an inline document holds with its content, and the order of the entries of the response is not specified:
the context keeps the order of selection.

Alternatives for returning: *interpret handles in the final text* returns documents nobody selected, because the model
writes handles in prose for other reasons; *an opt-in response option* is not needed while the field is omitted when empty.

Both tools are offered to every new instance and cannot be switched off yet (see Follow-up). They use only the runtime
tool SPI of ADR 016.

### Built-in tools use the public SPI

The connector ships `c8__load_document` and `c8__return_document` as built-in runtime tools. Each is an ordinary bean of
the SPI, depends only on the API, the model and a few libraries (an architecture test enforces it), and is replaced by
registering a bean with its bean name. A bean with another bean name but the name of a built-in tool fails the startup,
because tool names are unique. The core names no built-in tool in its own code. A built-in tool is enabled for the agent
instances that have the feature it needs, as a custom tool would be: both tools work with the agent documents, so they need
the `documents` feature.

### Where content comes from, and how tool results render

The content of a document is never persisted in the conversation. One component expands the persisted blocks into what
the providers see, when the conversation is windowed: in a user message the handle and, for the delivery `content`, the
content after it; in a tool result the handle as text. Providers have no code for documents in tool results. The component
is a named step of the model call that follows the window, and for an instance that does not track its documents it
leaves the messages as they are.

Alternatives: *render the handle in every provider converter* means six converters with the same rule to keep in sync; *a
marker interface and a global serialization mode* is hidden state that is easy to get wrong for instances that must not
change.

The expansion is the one place that guarantees that no `AgentDocumentContent` reaches a provider, and the chat model SPI
states it: a provider never has to handle the block. A provider that switches over every content type still needs a
branch for it, and that branch fails loudly; it is never reached, and no provider needs a test of its own for it.

Consequence accepted: no provider receives a document inside a tool result, so the content reaches the model only in a
user message after the tool results. Providers and models that accept documents in tool results would make this an
optimization, and the persisted form needs no change for it (see Follow-up).

### Documents outside the window

The window never announces a document whose message it hides. The agent documents still resolve the handle of such a document, so
a handle that is still in the model's context can be passed to a tool or loaded again within the guard.

A note that lists the hidden documents was considered and dropped. It costs one handle per document in every request for the
life of the instance, and it is not part of the history, which is the audit record of what the model reads. A tool the
model calls to list the known documents costs tokens only when needed, and is a follow-up.

### Inline documents are limited

The connector runtime limits the variables of a job completion (`InlineSizeGuard`, 1.5 MB), and an inline document
holds its content in the agent context. An inline document above that limit can never be completed, so the agent
documents reject it when it is registered, before the model is called, with an error code of its own instead of the
generic error of the job completion. Everything else, such as several inline documents that are too large together, is
left to the check of the runtime. There is no fallback to a reference-only entry.

### Instance features and the legacy path

The agent context lists the features an agent instance was created with: `runtimeTools` and `documents`. A feature is
assigned when the instance is created and never added later. The `documents` feature decides the document handling of the
instance, the sealed `DocumentHandling` of the model: `Untracked` without the feature, `Tracked` with the agent documents
with it. Every place that treats documents differently for the two matches both variants, so a third kind of handling
cannot be forgotten anywhere. An instance without `documents` keeps the handling it was
created with: documents are content in the conversation with the earlier XML tag format, there are no agent documents, no response
documents and no document parameters, and a document in a tool result renders as its reference JSON.

Alternatives: *migrate running instances* is not possible for an append-only store, and a conversation with messages in
two forms is hard to reason about; *a global switch* would change every running instance at once.

Consequences accepted:

* A running instance cannot use document parameters. It has to be started anew, and the tool schema it gets after a
  process migration is the handle schema, which asks for a document it has no handle for.
* The legacy path is a second code path in the composer, the window, the history mapper and the response handler, gated
  on the feature and pinned by a conversation persisted by Camunda 8.10. A separate legacy composer was considered and
  rejected; the document handling stays in one composer.
* A rollback is not supported. A runtime of the earlier version ignores the new fields and drops them on its next write,
  and cannot read a conversation that holds a persisted reference block.

### History

The agent instance history records what the model sent and received, in a form suited to the history; process data
stays in the variables of the tool elements. It shows exactly what the model reads: a document is its handle as an object block of its own, in every message, so that
a consumer can recognise a block that is exactly one handle by its `type`. Where a document enters the conversation (the user prompt,
an event, a tool result) a Camunda or external document is followed by the document itself, which a consumer shows as a
chip; the chip of a document without a file name, and of an external document without a name, carries the agent document id, the same id the handle shows. A document
nested in the object of a tool result is shown as the handle object inside the object, followed by the documents. In the message
that follows the tool results a document that is delivered is its handle object and a chip. The sentences the agent writes
into a tool result about the content of a document are text. The arguments of a call are shown as the model sent them. It never shows the Camunda
document reference a tool received. Each message of a model request is covered by a history item, and a test reads the
history back after a document flow and requires it.

### Positive Consequences

* A conversation carries any number of documents at a few dozen tokens each, and documents survive the window and every
  conversation store.
* The model can hand a document to a tool and return one with the response, without handling bytes or store details.
* Content enters the request only through one guard, so a document that a provider cannot render never fails a model
  call.
* Persisted state and running instances of earlier versions load and behave as before.

### Negative Consequences

* The agent context grows with the agent documents, which are never pruned, and inline documents are held several times.
* The limits are global and byte-based, so they are as strict as the strictest provider and are a poor measure of
  tokens.
* The content reaches the model only in a user message, and stays in every request until its message leaves the window.
* The legacy path is a second code path that stays as long as instances without the feature exist.
* Every new instance is offered both document tools, which costs input tokens in every call and occupies two tool names.

## Follow-up

* **A dedicated content hierarchy for the providers.** The chat model providers read the messages of the conversation,
  so only the SPI contract, not the type system, says that an `AgentDocumentContent` never reaches them. A class
  hierarchy of messages and content for the model without that block, which the expansion produces and the providers
  consume, would make the guarantee a compile-time one and remove the unreachable branch from each provider. It changes
  every provider converter and the chat model SPI, so it belongs with the next change of that SPI.
* **`c8__list_documents` built-in.** A runtime tool that lists the known documents when the model needs them. It
  replaces the note about documents that left the window, which cost a handle per document in every request.
* **A switch for the built-in document tools.** A setting frozen at instance creation as part of the agent configuration
  (not a FEEL expression evaluated per job). Every new instance is offered both tools, which costs tokens and
  can change tool selection for agents that never use documents. ADR 016 lists the general allow and deny list.
* **Per-tool document scoping.** Allowed document sources per tool. Any agent document can be passed to any tool by
  handle, so text a tool returned can name another document.
* **Where the content goes.** The agent decides it when it composes the turn, and the decision is stored with the turn.
  A model that takes documents in tool results gets them there from the builder, in place of the message that follows.
  Replaying stored turns to another model would need the rendering step to move them, which belongs to the replay across
  providers.
* **Per-provider limits and native tool-result documents.** A capability matrix of the providers would
  reject a type before the load and replace the global limits, and would let the window place content in the tool
  result for models that accept it, derived from persisted data, with the handle and a note otherwise. A provider needs a
  wire format test for documents in tool results first.
* **A token-based guard, a per-turn budget or a removal mechanism.** Content is sticky and the limits are bytes.
* **Bounded download of external documents.** The SDK downloads the whole body of an external document, also to read its
  metadata, so the download is not bounded by the limits. A bounded download in the SDK would let the guard reject a
  document before downloading it.
* **History reporting of documents.** External document URLs are reported to the history and can expose a URL with a
  token. They should be reported as handles.
* **Response documents in the agent documents.** The selections of `c8__return_document` could become a marker on the
  agent documents, which removes a selection that points to no document. A document may be selected under several
  keys, and the order of selection differs from the order of registration.
* **Pruning and a size budget of the agent documents.** Documents nobody references could be pruned.
* **Compaction.** A compaction that rewrites or drops messages must keep the agent documents intact and decide what happens to
  references with the delivery `content` and the follow-up messages that hold them.
* **Ranges for `c8__load_document`.** Pages, lines or bytes, as an additive field of the reference and an optional
  argument.
* **MCP and A2A tools that take documents.** Needs a mapping from the remote schema and from a document reference into
  the format the remote side expects.
* **A reference-only agent document for inline documents.** It would replace the limits above and the several copies.
* **Content type sniffing** for documents of unknown type.
* **Expiry of a document in its store.** The agent does not notice it today. At every model call, the agent checks each
  document whose content is sent against its `expiresAt`, with a margin. An expired document is sent as its handle with a
  note and no incident. A not-found while fetching the content gives the same note and a warning in the log. The content
  guard rejects an expired document. Whether the handle shows `expiresAt` is open. A process tool stays responsible for
  dereferencing the document it receives. The prompt cache breaks once when a document expires.
* **Documents named only in the content of an evicted assistant message,** and handles the model writes in the final text,
  are not tracked or interpreted.
* **Modeler and template support.** The extension property of the delivery is set by hand, and the type `document` of
  `fromAi` has no Modeler or linting support.
* **Other LLM connectors.** The output of the ad-hoc tools schema resolver carries the handle schema, and such a connector
  has no agent documents to resolve a handle against, so the parameter is an object that nothing resolves.
