# Document flow: a registry, short tags and content on demand

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

1. The model sees every document of the conversation as a short tag and can pass a document to a tool.
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
2. References by default, a document registry in the agent context, content on request or by opt-in (chosen).

Option 2 has several decisions of its own, each with alternatives, in the decision outcome.

## Decision Outcome

Chosen option: **Option 2**, because a tag costs a few dozen tokens, so a conversation can carry any number of
documents, and the cases in which the model needs the content are rare and known: it asks for it, or the process
designer knows it up front.

### Registry in the agent context, typed block in the conversation

A document is persisted in the conversation as a typed content block, `DocumentReferenceContent`, with its id, the
attributes of its tag, its source and its delivery. The complete Camunda document reference lives once, in a document
registry that is a field of the agent context, persisted by the same job completion as the rest of the context. The
source of a block is the same value as the source in the registry entry. A document has a stable id, a value type
`DocumentId`.

Alternatives:

* *Keep the document as content in the messages.* Every later call pays for the bytes, an inline document is stored
  several times, and an evicted message loses the document.
* *Derive the registry from the messages.* Documents nested in tool results or extracted by gateway handlers are hard to
  find again, and a window evicts messages.
* *A registry next to the messages in each conversation store.* Three stores would need an implementation, and the
  registry would not commit atomically with the job.
* *A tag as text.* The id and the delivery would have to be parsed back, and a tag a user typed would look like one the
  agent wrote.

Consequences accepted:

* The agent context grows with every document of the conversation, and the registry is never pruned. The bound is the
  message size of the broker for a job completion.
* The persisted conversation is not self-contained: the content comes from the registry when the conversation is
  windowed, so replaying needs the registry of the same agent context. Both are written by the same job completion.
* There is no schema version bump: a missing field binds to an empty registry, so earlier state loads.

### Documents of runtime tools

A runtime tool can return a document in its result, also nested in an object. The agent registers the documents of the
results of one assistant message after all its calls ran, in the order of the calls, with the call as their source, and
shows the model the tags of the documents in place of the documents. This happens in one place that the composer of a
mixed batch and the runtime tool round of a job share, so both give the same result. A result that is an error registers
nothing.

A runtime tool reads the documents the agent knows when the batch of calls starts. The calls of one message do not see
the documents the other calls returned, and the next turn knows them. This follows the contract of
[ADR 016](016-runtime-tools.md): the calls of one message may run concurrently and a call must not depend on another
call of the same message. The registration is a function of the agent context and the results only, so a job that runs
again from the same context computes the same registry.

Alternatives: *register the documents while the calls run* lets a call see the documents of an earlier call and ties the
contract to a sequential execution; *a method of the tool context that registers a document* gives a tool write access
to the state of the agent, which ADR 016 avoids.

### The model reads short tags only

The model sees a self-closing `<doc/>` tag with the id and a few attributes (file name, content type and size when
known, and where the document came from). Nothing else about a document reaches the model, a tool argument or an
error text.

Alternatives: *the Camunda document reference as JSON* leaks internals and is long to copy; *index numbers per
conversation* are not stable across windows, stores and instances, and a nested document has no natural number.

Consequence accepted: the tag is the only identity the model has, so a document without a name of its own is told apart
by type and origin only. The type of an external document is not in its tag, because its metadata is known only after a
download.

### Delivery: `reference` by default, `content` as an opt-in

Every document reference carries a delivery. With `reference` the model sees the tag only. With `content` it also
reads the content after the tag. The delivery is stored on each block, so an earlier block keeps its bytes when the same
document gets another delivery later; the cost is that the history can show one document with two deliveries.

The default is `reference` for the documents of a tool result and for the documents of the user prompt when the process
sets `reference`. A user prompt without a delivery, as in a process modeled before the option existed and in every v1
template, behaves as before: every document goes out as content, with no content guard, and a failure of the provider
stays a failure of the provider. An explicit `content` runs the guard. The v2 templates preset the dropdown to
`reference`, so a newly modeled process chooses it. The documents of an external URL in the user prompt always go out as
content, without the guard, whatever the delivery: the provider reads them.

Content enters the request in two ways, which share one guard and one persisted form:

* **On request.** The model calls the built-in tool `c8__get_document`. The model knows whether it needs to read a
  document, and the tool answers with a result the model can act on when it cannot load it. It loads the whole document.
  A range would be additive.
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
* **External documents are reference-only for content that a tool or a tool activity triggers.** The runtime downloads
  the whole body of an external document to learn its type and size, so no guard can bound the work. The model passes the
  tag to a tool that processes it. The user prompt keeps its behavior: the model provider reads the content of an
  external document in a user prompt whatever the delivery, as it reads every document of a user prompt.
* What the model reads about a rejection is a fixed sentence with the content type, the size and the limit at most. The
  text is persisted and a failure message can hold a URL with a token, so the cause goes to the log only.
* A rejection on request is an error result and no incident. A rejection in an opt-in leaves the document a reference and
  adds a note, because the model could not have read the document anyway.

Bytes are a poor proxy for tokens (a small PDF can cost many input tokens), and limits that are the same for every
provider are as strict as the strictest one. Both are accepted until a token-based guard and a provider capability
matrix exist.

### Only the tag identifies a document; document parameters

A tool declares a parameter that takes a document with the type `document` of `fromAi`. The stored schema is a plain
string schema with a format marker, so the persisted definition needs no custom type. The model providers get a derived
schema without the marker and with a hint appended to the description, derived where the provider tool definitions are
built. The stored definition and the configuration fingerprint therefore hold no hint wording.

A value resolves only when it is exactly one tag. A bare id is invalid for a plain document parameter, and the agent
answers the call with an error the model can act on, without the tool running. Alternatives: *accept an id or a tag* is
friendlier to a model that copies only the id; *accept an id found anywhere in the text* would replace a tool argument
that happens to contain an id. The model reads tags, so asking for what it reads is the least surprising contract.

Parameters that take text or a document are unions, declared in full: `anyOf`, `oneOf` and the type array shorthand.
Only a value that is exactly one tag is a document. A plain string stays text even when it equals a known id, so a word
that equals an id never turns into a document by accident. Documents are not searched for in the alternatives of a union
that are objects or arrays, because a value cannot be assigned to one alternative reliably.

A process tool receives the Camunda document reference of the registry entry, a runtime tool the `DocumentId`. Passing a
tag to a tool never adds content. The agent resolves the parameters once, when it receives the message of the model, on
the call the model made, before a gateway handler transforms it. A gateway handler that defines its tools itself can declare document
parameters and receives the references. The MCP and A2A handlers declare none: the schema of an MCP tool comes from the
remote server and has no document type, so passing documents needs a mapping into the format the remote side expects.

Alternatives for the schema: *a custom `type: document`* would force every consumer of persisted definitions to know a
non-standard type; *a separate field next to the schema* would have to stay in sync with it.

### The built-in tools

`c8__get_document` loads a document, as above. `c8__return_document` selects one document per call under a key, and the
final response carries all selected documents as `responseDocuments`. The selections live in the agent context, because
the tool can run in an earlier job than the final response, and stay for the lifetime of the instance, so a later final
response carries them too. A key used again replaces its entry. There is no retract.

Alternatives for returning: *interpret tags in the final text* returns documents nobody selected, because the model
writes tags in prose for other reasons; *an opt-in response option* is not needed while the field is omitted when empty.

Both tools are offered to every new instance and cannot be switched off yet (see Follow-up). They use only the runtime
tool SPI of ADR 016.

### Where content comes from, and how tool results render

The content of a document is never persisted in the conversation. One component expands the persisted blocks into what
the providers see, when the conversation is windowed: in a user message the tag and, for the delivery `content`, the
content after it; in a tool result the tag as text. Providers have no code for documents in tool results. The conversation
applies it to its window, and skips it for an instance without the `documents` feature.

Alternatives: *render the tag in every provider converter* means six converters with the same rule to keep in sync; *a
marker interface and a global serialization mode* is hidden state that is easy to get wrong for instances that must not
change.

Consequence accepted: no provider receives a document inside a tool result, so the content reaches the model only in a
user message after the tool results. Providers and models that accept documents in tool results would make this an
optimization, and the persisted form needs no change for it (see Follow-up).

### Documents outside the window

The window never announces a document whose message it hides. The registry still resolves the tag of such a document, so
a tag that is still in the model's context can be passed to a tool or loaded again within the guard.

A note that lists the hidden documents was considered and dropped. It costs one tag per document in every request for the
life of the instance, and it is not part of the history, which is the audit record of what the model reads. A tool the
model calls to list the known documents costs tokens only when needed, and is a follow-up.

### Inline documents are limited

An inline document holds its content in the registry, in the tool result and in the response documents, about three
times. The registry rejects an inline document above 1 MiB, and inline documents above 1 MiB in total, with an error
code for each, instead of letting the job completion fail with an opaque error of the broker. The total keeps the
content within about three quarters of the message size of the broker. There is no fallback to a reference-only entry.

### Instance features and the legacy path

The agent context lists the features an agent instance was created with: `runtimeTools` and `documents`. A feature is
assigned when the instance is created and never added later. An instance without `documents` keeps the handling it was
created with: documents are content in the conversation with the earlier tag format, there is no registry, no response
documents and no document parameters, and a document in a tool result renders as its reference JSON.

Alternatives: *migrate running instances* is not possible for an append-only store, and a conversation with messages in
two forms is hard to reason about; *a global switch* would change every running instance at once.

Consequences accepted:

* A running instance cannot use document parameters. It has to be started anew, and the tool schema it gets after a
  process migration still carries the hint that asks for a document it has no tag for.
* The legacy path is a second code path in the composer, the window, the history mapper and the response handler, gated
  on the feature and pinned by a conversation persisted by Camunda 8.10. A separate legacy composer was considered and
  rejected; the document handling stays in one composer.
* A rollback is not supported. A runtime of the earlier version ignores the new fields and drops them on its next write,
  and cannot read a conversation that holds a persisted reference block.

### History

The agent instance history records what the model sent and received, in a form suited to the history; process data
stays in the variables of the tool elements. It shows tags in backticks, so a markdown renderer shows them, with the
documents of the references next to them, and the arguments of a call as the model sent them. It never shows the Camunda
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

* The agent context grows with the registry, which is never pruned, and inline documents are held several times.
* The limits are global and byte-based, so they are as strict as the strictest provider and are a poor measure of
  tokens.
* The content reaches the model only in a user message, and stays in every request until its message leaves the window.
* The legacy path is a second code path that stays as long as instances without the feature exist.
* Every new instance is offered both document tools, which costs input tokens in every call and occupies two tool names.

## Follow-up

* **`c8__list_documents` built-in.** A runtime tool that lists the known documents when the model needs them. It
  replaces the note about documents that left the window, which cost a tag per document in every request.
* **A switch for the built-in document tools.** A setting frozen at instance creation as part of the agent configuration
  (not a FEEL expression evaluated per job). Every new instance is offered both tools, which costs tokens and
  can change tool selection for agents that never use documents. ADR 016 lists the general allow and deny list.
* **Per-tool document scoping.** Allowed document sources per tool. Any registry document can be passed to any tool by
  tag, so text a tool returned can name another document.
* **Per-provider limits and native tool-result documents.** A capability matrix of the providers (issue #7230) would
  reject a type before the load and replace the global limits, and would let the window place content in the tool
  result for models that accept it, derived from persisted data, with the tag and a note otherwise. A provider needs a
  wire format test for documents in tool results first.
* **A token-based guard, a per-turn budget or a removal mechanism.** Content is sticky and the limits are bytes.
* **Bounded download and content delivery for external documents.** The runtime downloads the whole body, so no guard
  can bound the work; external documents are reference-only on request of a tool.
* **History reporting of documents.** External document URLs are reported to the history and can expose a URL with a
  token, and an inline document in a user message makes the history mapper fail. Both should be reported as tags.
* **Registry pruning and a size budget.** Entries nobody references could be pruned.
* **Compaction.** A compaction that rewrites or drops messages must keep the registry intact and decide what happens to
  references with the delivery `content` and the follow-up messages that hold them.
* **Ranges for `c8__get_document`.** Pages, lines or bytes, as an additive field of the reference and an optional
  argument.
* **MCP and A2A tools that take documents.** Needs a mapping from the remote schema and from a document reference into
  the format the remote side expects.
* **A reference-only entry for inline documents.** It would replace the limits above and the several copies.
* **Content type sniffing** for documents of unknown type.
* **Expiry of a document in its store.** The agent does not notice it; whether it drops the entry and tells the model, or
  leaves it to the tool that dereferences the document, is open.
* **Documents named only in the content of an evicted assistant message,** and tags the model writes in the final text,
  are not tracked or interpreted.
* **Modeler and template support.** The extension property of the delivery is set by hand, and the type `document` of
  `fromAi` has no Modeler or linting support.
* **Other LLM connectors.** The output of the ad-hoc tools schema resolver carries the format marker; a connector that
  sends it to its provider needs the same rewrite.
