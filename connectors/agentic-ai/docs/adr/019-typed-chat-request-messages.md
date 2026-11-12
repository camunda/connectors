# Typed chat request messages: a provider receives only the content it can send

* Deciders: Agentic AI Team
* Date: Nov 12, 2026

## Status

**Proposed**. Extends [ADR 009](009-chat-model-provider-spi.md), which defines one sealed content model for the
conversation and the chat request, and [ADR 018](018-document-flow.md), which expands the document blocks before the
model call.

## Context and Problem Statement

The sealed `Content` model is used for two jobs: the conversation that the stores persist, and the `ChatRequest` that a
`ChatModel` receives. Some blocks are valid for only one of the two jobs:

* `AgentDocumentContent` is the persisted form of a known document. The agent expands it before the model call
  ([ADR 018](018-document-flow.md)), so it never reaches a provider. Only a Javadoc says so. Each provider converter
  matches it in an exhaustive `switch` and throws: 12 arms, 10 in the native providers and 2 in LangChain4j. The change
  that added the block had to edit 6 provider files only to add these throws.
* `ReasoningContent` and `ProviderContent` are meant for assistant messages. The user message and tool result paths of
  each converter still match them, and the converters disagree: some drop them, some replay them, some write them as
  JSON, and Bedrock and LangChain4j throw. A runtime tool can return them in its result (`RuntimeToolResult` accepts any
  `Content`), so the tool result case is reachable.
* A system message holds only text. Bedrock throws for other content, the other providers drop it.

Compaction ([ADR 018](018-document-flow.md#follow-up)) will likely add more blocks that only the agent core uses. With one
content model, each such block adds an arm that can only throw to every converter. This breaks the rule that a new
sealed subtype is one that most `switch` sites handle, and it makes a typical next change touch every provider.

How can the type system guarantee that a provider never receives a block that only the agent core uses?

## Decision Drivers

* **Compile-time guarantee**: a provider cannot receive a block that it must not send. The compiler checks it, not a
  Javadoc.
* **A new domain-only block is cheap**: it touches the step that builds the request, not each provider.
* **One owner**: one function decides which block may appear in which message of a request.
* **Persisted shape unchanged**: no schema version, no migration and no agent instance feature.
* **Cheap for extensions now**: the API compatibility check skips `aiagent-api` until 8.11, so a source break for a
  custom `ChatModel` costs least before 8.11.
* **LangChain4j will be removed** ([#8408](https://github.com/camunda/connectors/issues/8408)): adapt it with the
  least code.

## Considered Options

1. Keep one content model, and each converter throws for the blocks it must not receive.
2. A guard (`requireExpanded`) that checks the `ChatRequest` before a provider converts it.
3. An intermediate sealed sub-interface of `Content` for the blocks that a provider may receive, while the
   `ChatRequest` keeps the persisted messages.
4. Request messages typed by role, built only by the expansion step.

## Decision Outcome

Chosen option: **Option 4, request messages typed by role**, because it is the only option where the compiler keeps a
domain-only block away from the providers, and a new domain-only block touches only the step that builds the request.

* `ChatRequest` carries a `ChatConversation`: the request messages and the tool definitions of one model call.
* The request messages are the sealed `ChatRequestMessage`, one record per role. A system message holds text. A user
  message holds portable content. An assistant message holds chat content, the tool calls, the provider message id and
  the metadata. A tool call result message holds results whose content is portable content.
* Portable content is the sealed `PortableContent`: text, a document and an object. Any provider can receive it. Chat
  content is the sealed `ChatContent`: portable content, reasoning and provider-native content. Reasoning and
  provider-native content carry the provider that made them, and a provider drops the blocks of a different provider.
* Both interfaces extend `Content`, and the existing records implement them. `AgentDocumentContent` implements only
  `Content`. The persisted JSON does not change.
* The two content interfaces are in the content package of `aiagent-model`, next to the records. A sealed interface on
  the class path can permit only classes of its own package. The request messages are in the chat model SPI package of
  `aiagent-api`.
* `ChatConversation.of(messages, toolDefinitions)` is the one translation from persisted messages. It fails for a block
  that has no form in its message of a request: an agent document block, reasoning or provider-native content outside an
  assistant message, and content other than text in a system message. The error names the message role and the block
  type. The agent writes reasoning and provider-native content only into assistant messages, and the system message
  only as text.
* A runtime tool returns portable content: `RuntimeToolResult` takes `PortableContent`, so a tool cannot put reasoning,
  provider-native content or an agent document block into a tool result. Runtime tools are new in 8.11, so no released
  version stores such a result. Dropping such a block hides a defect of the tool, and writing it as JSON text sends the
  model a block that only one provider understands, so neither is an option.
* `DocumentExpander` is the only production caller. It expands the document blocks of an agent instance with the
  `documents` feature and then builds the `ChatConversation`. For an instance without the feature it builds the
  `ChatConversation` from the messages as they are.
* The native providers switch over the request messages and the narrow content types. The arms that could only throw
  are gone.
* LangChain4j maps the `ChatConversation` back to the persisted messages inside its chat model and keeps its converters.
  They read `Content`, so they keep their arms for the agent document block and `UnexpandedAgentDocumentException`.

### Positive Consequences

* The compiler guarantees that no provider receives a block that only the agent core uses.
* A new domain-only block adds one arm in the translation, not one per provider.
* The providers handle a block that cannot occur in a position the same way: the translation rejects it.
* The providers keep their behavior for every block that a stored conversation can hold.

### Negative Consequences

* A source break for a custom `ChatModel`: it reads `request.conversation()` and switches over the request messages. A
  custom runtime tool returns `PortableContent`. Both are recorded in [`breaking-changes.md`](../breaking-changes.md).
* A second set of message types. A new field of a persisted message reaches the providers only when the request message
  gets it too.
* The content interfaces of the chat model SPI live in `aiagent-model`.
* A stored conversation with a block in a place where the agent never writes it fails the model call. Before, the
  provider decided.

## Pros and Cons of the Options

### Option 1: Each converter throws

* Good, because nothing changes.
* Bad, because each domain-only block adds an arm that can only throw to every converter, and the build does not find a
  converter that handles the block wrongly.
* Bad, because the converters disagree about reasoning and provider-native content outside assistant messages.

### Option 2: A `requireExpanded` guard

* Good, because it is one method.
* Bad, because the providers still switch over `Content`. An exhaustive `switch` still needs the arms that can only
  throw.
* Bad, because a provider that is called without the guard, such as in a test or a custom caller, gets no protection.

### Option 3: An intermediate sealed sub-interface

* Good, because the records state which blocks a provider may receive.
* Bad, because the request still holds `Content`. Each converter needs a cast or an arm that can only throw, so the
  sub-interface gives no guarantee.

### Option 4: Request messages typed by role (chosen)

* Good, because the guarantee is in the types of the SPI.
* Good, because one translation decides the position of each block.
* Bad, because it breaks the source of custom `ChatModel` implementations, and it adds message types.

## Follow-up

* **Compaction blocks.** A block that only the agent core uses implements `Content` and not `ChatContent`. The
  translation decides its form for the provider, or the expansion step replaces it first.
* **`ConversationSnapshot` outside the SPI.** The snapshot is the window of the persisted messages. Only the agent reads
  it, so it can move out of `aiagent-api` in a separate change.
* **LangChain4j mapping.** The mapping back to the persisted messages, and with it `UnexpandedAgentDocumentException`,
  goes away with the LangChain4j integration ([#8408](https://github.com/camunda/connectors/issues/8408)).
