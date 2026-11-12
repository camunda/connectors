# A module for the chat model SPI, and the native providers stay in the AI Agent connector

* Deciders: Agentic AI Team
* Date: Nov 12, 2026

## Status

**Proposed**. Extends [ADR 015](015-split-agentic-ai-into-modules.md), which puts the chat model SPI into
`aiagent-api` (D1, D3) and keeps the providers in `connector-aiagent`, and [ADR 019](019-typed-chat-request-messages.md),
which adds the request messages to the chat model SPI package of `aiagent-api`.

## Context and Problem Statement

`aiagent-api` holds every extension point of the AI Agent: gateway tools, runtime tools, conversation stores, system
prompt contributors and the chat model SPI. ADR 019 added the request messages to the chat model SPI, so it is now the
largest part of the module. A custom chat model provider compiles against all of `aiagent-api`, although it uses only the
chat model SPI.

The native providers (Anthropic, Bedrock, Gemini, OpenAI, Mistral and their shared support) live in `connector-aiagent`,
and `AgenticAiConnectorsAutoConfiguration` imports their Spring configuration. ADR 015 names provider modules as a
follow-up.

Should the chat model SPI and the native providers each get a module of their own, so that `connector-aiagent` only
depends on them and the providers register their factories through their own auto-configuration?

## Decision Drivers

* **Dependency direction**: the SPI module, then the provider module, then `connector-aiagent`. No cycle.
* **Connectors own their element templates** ([ADR 015](015-split-agentic-ai-into-modules.md), D4 and its follow-up):
  the records annotated for template generation live in the connector modules, and the library modules do not use the
  generator.
* **Small API for extensions**: a custom chat model provider depends on the chat model SPI only.
* **A move, not a redesign**: the change moves classes and keeps their behavior, so a review can follow it.
* **No behavior change**: job types, element templates, configuration properties and persisted data stay the same.

## Considered Options

1. Keep the chat model SPI in `aiagent-api` and the providers in `connector-aiagent`.
2. Move the chat model SPI into its own module. The providers stay in `connector-aiagent`.
3. Move the chat model SPI and the native providers into their own modules, and move the v2 provider configuration
   records with the providers.
4. Move the chat model SPI and the native providers into their own modules, and give the providers a configuration type
   of their own that the connector translates the v2 provider configuration records into.

## Decision Outcome

Chosen option: **Option 2, a module for the chat model SPI**, because it is the only option that is a move and keeps the
records of the element templates in the connector. The native providers follow when the conditions in
[Follow-up](#follow-up) are met.

* **D1: The module `aiagent-chatmodel-api` holds the chat model SPI.** It is a library module with the package root
  `aiagent.api.chatmodel`. It depends on `aiagent-model` only. Its public types are gated by the API compatibility
  check, like `aiagent-api`.
* **D2: The package stays `aiagent.api.chatmodel`.** Extensions keep their imports and change only the artifact. The
  package root of `aiagent-api` is `aiagent.api` without `aiagent.api.chatmodel`, in the same way as the package root
  of `connector-aiagent` is `aiagent` without `aiagent.model` and `aiagent.api`. No package spans two modules.
* **D3: `aiagent-api` does not depend on `aiagent-chatmodel-api`.** No other SPI uses the chat model types. An extension
  that implements a chat model provider depends on `aiagent-chatmodel-api`. `connector-aiagent` depends on both.
* **D4: The native providers stay in `connector-aiagent` for now.** A provider module is not a move today:
  * The native providers read the v2 provider configuration records (`aiagent.request.v2`) directly: 28 of the 52
    provider classes import them. The records are the element template binding of the provider selection: about 80
    annotated types in 13 files, with the sealed provider union that lists the custom provider too. Moving them into the
    provider module (option 3) puts template records into a library module and makes the template generation of
    `connector-aiagent` scan it, which reverses D4 of ADR 015 and its follow-up. Keeping them in the connector (option 4)
    needs a provider configuration that the connector translates them into ([target architecture](../target-architecture.md),
    trait 2). That changes how each provider reads its configuration, so it is a redesign.
  * The providers share code with the rest of `connector-aiagent`: the chat model properties are
    nested in `AgenticAiConnectorsConfigurationProperties`, and `ChatModelTimeouts` reads them for the native providers,
    the LangChain4j factories and the v1 to v2 mapping. The AWS and Azure proxy support also serves the AgentCore
    conversation store and LangChain4j. `AssistantMessageMetadata` is used by the native providers and LangChain4j.
    `TimeoutConfiguration` and `ConnectorUtils` are used by the v1 records. The error codes of model calls are in
    `aiagent.agent.AgentErrorCodes`. The package `aiagent.chatmodel.provider` holds a class of the native providers and
    one of LangChain4j.
  * The provider tests use the test fixtures of `connector-aiagent` and its response format record. A provider module
    cannot use them, because Maven does not allow a test dependency of a module on a module that depends on it.

### Positive Consequences

* A custom chat model provider compiles against `aiagent-chatmodel-api` and `aiagent-model`, without the other SPIs.
* The provider module has a place to attach: it depends on `aiagent-chatmodel-api`, not on `aiagent-api`.
* No import changes for extensions.

### Negative Consequences

* A custom chat model provider changes its Maven dependency from `aiagent-api` to `aiagent-chatmodel-api`. This is
  recorded in the [breaking changes](../breaking-changes.md#artifacts).
* One more module and one more gated API.
* The package root `aiagent.api` spans two modules. The package still tells that a class is an extension point, and
  the architecture tests tell which module owns it.
* The native providers still ship inside `connector-aiagent`.

## Pros and Cons of the Options

### Option 1: Keep the layout

* Good, because nothing changes.
* Bad, because a custom chat model provider compiles against every SPI.

### Option 2: A module for the chat model SPI (chosen)

* Good, because it is a move of one package with its tests and its reference doc.
* Good, because the provider module can depend on it later.
* Bad, because custom chat model providers change their Maven dependency.

### Option 3: Move the providers with the v2 provider configuration records

* Good, because `connector-aiagent` registers no provider bean.
* Bad, because a library module holds element template records, and the template generation of the connector scans it.
* Bad, because the shared properties, timeouts, proxy support and metadata helpers must move first, and LangChain4j would
  depend on the provider module.

### Option 4: Move the providers with a provider configuration of their own

* Good, because the records of the element templates stay in the connector, and the providers read a valid domain
  configuration.
* Bad, because each provider changes how it reads its configuration, so the change is a redesign, not a move.

## Follow-up

* **A module for the native providers.** Move the native providers, their shared support and
  `AgenticAiNativeProvidersConfiguration` into a module that depends on `aiagent-chatmodel-api` and registers its own
  auto-configuration, after `AgenticAiCommonAutoConfiguration` and before `AgenticAiConnectorsAutoConfiguration`. The
  conditions:
  * The providers read a provider configuration instead of the v2 records, or the team decides that the provider module
    owns the records of its providers and the template generation of `connector-aiagent` scans it.
  * LangChain4j is removed ([#8408](https://github.com/camunda/connectors/issues/8408)). This removes the shared timeouts,
    properties, metadata helper and exception, and the LangChain4j use of the proxy support.
  * The error codes of model calls move to `aiagent-chatmodel-api` (the ADR 015 follow-up).
  * The chat model properties get their own `@ConfigurationProperties` class with the same prefix, and the AWS and Azure
    proxy support gets an owner that the AgentCore conversation store can also depend on.

  Done when `connector-aiagent` has no class below `aiagent.chatmodel.provider` and does not import the configuration of
  the providers.
