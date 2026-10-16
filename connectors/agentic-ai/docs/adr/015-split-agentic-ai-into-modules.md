# Split the agentic AI module into libraries, connectors and integrations

* Deciders: Agentic AI Team
* Date: Oct 15, 2026

## Status

**Accepted**.

## Context and Problem Statement

The agentic AI connectors shipped as a single Maven module, `connector-agentic-ai`. It contained the
AI Agent connectors, the ad-hoc tools schema connector, the MCP client connectors, the A2A client
connectors, every chat model provider, the conversation stores, and the glue that lets the AI Agent
call MCP and A2A as gateway tools.

Several needs pull these parts apart:

* The MCP and A2A client connectors are standalone connectors. They must evolve on their own
  schedule, for example to adopt the A2A 1.0 protocol and its authentication schemes, without
  touching the AI Agent.
* Custom extensions (conversation stores, chat model factories, gateway tools) compile against the
  whole connector today. They have no smaller, stable API to depend on.
* Chat model providers and the LangChain4j integration are candidates for their own modules later.
  That needs a home for the provider SPI and the agent model that does not include the agent
  implementation.
* Module boundaries existed only by convention, and the convention had already eroded: the A2A
  client built its results from the AI Agent's message content model, and the MCP and A2A client
  connectors referenced constants from the tools schema resolver.

Should the module stay as one, or be split, and along which lines?

## Decision Drivers

* **Independent evolution**: client connectors change without rebuilding or re-releasing the agent
  internals they do not use.
* **Stable extension API**: custom extensions depend on a small set of SPI and model types, not on
  the connector implementation.
* **No behavior change**: job types, element template IDs and content, Spring property names, JSON
  shapes of process variables and stored conversation data stay identical. The default bundle keeps
  registering the same connectors. The exceptions are listed in the
  [breaking changes](../breaking-changes.md): the renamed MCP gateway tool switch (D7), the
  prototype scope of all outbound connector function beans, and the HTTP URL validation of the MCP
  and A2A server URLs.
* **Enforced boundaries**: a dependency in the wrong direction fails the build instead of a review.
* **Room for provider modules**: a future provider or LangChain4j module depends on the API, not on
  the AI Agent connector.

## Considered Options

1. Keep one module and enforce package boundaries with architecture tests only.
2. One module per connector, with all shared code (model, SPI and agent implementation) in a single
   core library.
3. Layered libraries for the shared code, one module per connector, and separate integration modules
   that connect the MCP and A2A clients to the AI Agent.

## Decision Outcome

Chosen option: **Option 3 — layered libraries, connector modules and integration modules**, because
it is the only option that lets the client connectors depend on nothing agent-specific while the AI
Agent still uses them as gateway tools, and it gives extensions a small API to compile against.

The decision comprises:

* **D1 — Ten modules with a fixed dependency direction.**

  | Module                          | Kind        | Package root                         | Depends on (agentic AI modules)   |
  | ------------------------------- | ----------- | ------------------------------------ | --------------------------------- |
  | `agentic-ai-common`             | library     | `common`                             | none                              |
  | `aiagent-model`                 | library     | `aiagent.model`                      | common                            |
  | `aiagent-api`                   | library     | `aiagent.api`                        | model, common                     |
  | `aiagent-tools-schema`          | library     | `tools.schema`                       | api, model, common                |
  | `connector-ad-hoc-tools-schema` | connector   | `adhoctoolsschema`                   | tools-schema                      |
  | `connector-mcp`                 | connector   | `mcp.client`                         | common                            |
  | `aiagent-mcp-integration`       | integration | `mcp.integration`                    | connector-mcp, api, model, common |
  | `connector-a2a`                 | connector   | `a2a.client`                         | common                            |
  | `aiagent-a2a-integration`       | integration | `a2a.integration`                    | connector-a2a, api, model, common |
  | `connector-aiagent`             | connector   | `aiagent` (except `model` and `api`) | tools-schema, api, model, common  |

  Package roots are relative to `io.camunda.connector.agenticai`.

* **D2 — Client connectors know nothing about the AI Agent.** The MCP and A2A client connectors
  depend on the common library only. The code that turns them into AI Agent gateway tools lives in
  integration modules. Integration modules never depend on the AI Agent connector, and the AI Agent
  connector never depends on the clients or integrations. It discovers gateway tool handlers as
  beans contributed through the API.

* **D3 — The model is exactly what the API needs.** `aiagent-api` holds the extension points: the
  gateway tool SPI, the gateway tool definition resolver SPI, system prompt contributors, the chat
  model SPI and the conversation storage SPI. `aiagent-model` holds exactly the types these SPIs
  reference, so the API is self-contained. The name `aiagent-core` is reserved for a future library
  holding the agent loop itself.

* **D4 — Generic interfaces in the model, element template types in the connector.** Configuration
  that the API exposes is modeled as plain interfaces (memory configuration, conversation store
  configuration, custom chat model configuration). The records annotated for element template
  generation implement them and stay in the connector, so the model carries no template layout.
  The prompt, limits, event handling and response format configurations do not follow this yet
  (see [Follow-up](#follow-up)).

* **D5 — Conversation context subtypes are discovered, not listed.** The persisted conversation
  context is polymorphic. Its subtypes are contributed through a `java.util.ServiceLoader` provider
  instead of a closed list on the base type, so the base type can live in the model while the
  implementations live in the connector or in an extension. Registering subtypes on each
  `ObjectMapper` was rejected because the runtime has several mappers that Spring customizers do not
  reach. Subtypes registered on a mapper keep working.

* **D6 — A2A results carry their own part types.** A2A messages and artifacts use A2A-owned part
  records instead of the AI Agent's message content model. Their JSON shape is identical to before.

* **D7 — Each module owns its runtime wiring.** Every module registers its own auto-configuration,
  configuration properties, element templates and icons. Every auto-configuration is switched by
  the global `camunda.connector.agenticai.enabled` flag. Single connectors and features inside it
  keep their own flags. The gateway tool integrations are switched by
  `camunda.connector.agenticai.<protocol>.client.agentic.tool.enabled`, for example
  `camunda.connector.agenticai.mcp.client.agentic.tool.enabled`. The previous MCP property stays as a
  deprecated alias. Auto-configuration class names end with `AutoConfiguration`. The AI Agent
  collects gateway tool handlers and system prompt contributors as lists of beans and does not
  reference the integration modules.

* **D8 — Each module owns its packages.** Every module has its own package root (see D1), and no
  package spans two modules. The package of a class tells which module it belongs to and whether it
  is an extension point, a model type or an implementation. Boundaries are enforced by the Maven
  dependencies and by the architecture tests in `agentic-ai-archunit-tests`, a test-only module that
  is not deployed and has the production classes of all modules on its class path. Its rules describe
  the layering by package root, check that each package root is loaded from the module owning it, and
  check that every package is `@NullMarked`.
  Keeping the existing packages to spare extensions an import change was rejected: it leaves
  packages split across modules, which hides module membership and blocks the Java module system.

* **D9 — One group, a bundle, and a renamed AI Agent artifact.** All artifacts use the group
  `io.camunda.connector.agenticai`, which keeps generic names such as `aiagent-api` apart from the
  other connectors. After the split the AI Agent module holds only the AI Agent, so its artifact is
  `connector-aiagent`. The `agentic-ai-bundle` artifact (`pom` packaging) depends on every
  connector and gateway tool integration. Runtimes that ship all agentic AI connectors, such as the
  default bundle, depend on it instead of listing the modules.

* **D10 — Dependencies follow the kind of the module.** A library depends on libraries only. A
  connector depends on libraries and on the connector SDK and runtime, never on another connector.
  A gateway tool integration depends on libraries and on the one client connector it makes usable.
  No module depends on a connector outside Agentic AI, except for named exceptions. The package based
  architecture rules cannot see such dependencies, so a test over the poms checks the policy.
  Allowing connectors to depend on each other was rejected: it would bring back the coupling the
  split removes, and the build of one connector would pull in the others.

### Non-goals

* The A2A 1.0 protocol upgrade.
* Separate modules for chat model providers or LangChain4j.

### Positive Consequences

* MCP and A2A client connectors build and evolve without the agent, its LLM SDKs or its stores.
* Extensions can compile against `aiagent-api` and `aiagent-model` only.
* A dependency in the wrong direction fails compilation.
* Provider and LangChain4j modules have a clear place to attach.
* Packages show module membership; extension points live under `aiagent.api` and model types under
  `aiagent.model`.

### Negative Consequences

* Ten modules instead of one: more poms to maintain. Runtimes depend on `agentic-ai-bundle`, so the
  module layout does not leak into them.
* Extensions must switch to the group `io.camunda.connector.agenticai` and the artifact
  `connector-aiagent` (or `agentic-ai-bundle`), and update their imports, since most
  extension point and model types changed packages. The details are in the
  [breaking changes](../breaking-changes.md).
* Element templates moved to the modules of their connectors, so their repository paths changed.

## Follow-up

* Extract the chat model providers and LangChain4j into their own modules, when a provider must be
  released, versioned or excluded on its own. This is blocked by the provider code in
  `connector-aiagent` that the chat model API does not cover:
  * The providers use the error codes in `aiagent.agent.AgentErrorCodes`. Move the error codes of
    model calls to `aiagent-api`, next to the chat model exceptions.
  * `AgentExecutionContext` exposes the connector `JobContext` and the full agent configuration to
    the chat model, conversation store and system prompt SPIs. Decide which parts belong to the API.
  * The cycles of `aiagent.chatmodel` with `aiagent.agent`, `aiagent.configuration` and
    `aiagent.request` (see below).
* Break the package cycles in `connector-aiagent` and `connector-mcp`. The cycles block extracting
  the agent loop and the providers. The architecture tests allow exactly these cycles, and the list
  must only shrink:
  * `aiagent.agentinstance` and `aiagent.conversation`.
  * `aiagent.chatmodel` and `aiagent.configuration`.
  * `aiagent.chatmodel` and `aiagent.request`.
  * `aiagent.configuration` and `aiagent.request`.
  * `aiagent.memory` and `aiagent.request`.
  * `mcp.client.configuration` and `mcp.client.framework`.

  In addition, `aiagent.chatmodel`, `aiagent.agentinstance` and `aiagent.document` use the error code
  constants in `aiagent.agent.AgentErrorCodes`, while `aiagent.agent` uses these packages. The
  compiler inlines the constants, so the architecture tests do not see these dependencies, but they
  still tie the packages together at source level. Done when `KNOWN_CYCLES` in `PackageCyclesArchTest`
  is empty and the error codes of the providers, the agent instance client and the document handle
  live outside `aiagent.agent`.
* Apply D4 to the remaining annotated records in `aiagent.model.request`: `PromptConfiguration`
  (with its system and user prompt records), `LimitsConfiguration`, `EventHandlingConfiguration` and
  `ResponseFormatConfiguration` (with its nested records). Model them as interfaces in the model and
  move the annotated records to the connector. The architecture tests allow element template
  annotations in the library modules only for these records and for `AdHocToolsSchemaRequest`, and the
  list must only shrink. Done when the allow list in `ElementTemplateAnnotationsArchTest` is empty.
* Make the element template generation follow the module boundaries. Today the generator of
  `connector-aiagent` and of `connector-ad-hoc-tools-schema` scans the classes of its library
  dependencies (`includeDependencies`) to find the annotated records. When the annotated records live
  in the connector modules, each connector generates its templates from its own classes only.
* Split the e2e tests in `connectors-e2e-test-agentic-ai` along the modules, so that each connector
  and integration has its own e2e module. The tests share runtime and agent fixtures, which must move
  to a shared test library first. The split also adds checks that a runtime with only the AI Agent,
  only the MCP connectors or only the A2A connectors starts and runs.
* Remove the dependency of `connector-a2a` on the webhook connector. The A2A webhook uses the webhook
  authorization and HMAC verification of `connector-webhook`, so anyone who depends on the A2A client
  gets the webhook connector and its registration, and the A2A template generation includes its
  classes. The webhook connector is maintained by another team. Ask them to extract the authorization
  and HMAC API with its model types into a library without connector registration, switch the A2A
  client to it, and drop the webhook entry of the template generation. Done when the named exception
  in `ModuleDependencyPolicyTest` is gone.
* Split `agentic-ai-common` into a plain library and the Spring and HTTP support. Until then, the
  model and the API exclude the Spring and HTTP dependencies of the common library, and the build
  fails if they come back. The common auto-configuration skips itself when the HTTP client is
  missing, so a Spring application that depends only on the model or the API still starts.
* Make each gateway tool integration depend on its client connector being enabled. Today the
  integration can stay enabled while the client connector that runs its tool calls is disabled.
* Add an API compatibility check for `aiagent-api` and `aiagent-model` after the first release with
  the split, as part of the build verification harness (#7621).

## Pros and Cons of the Options

### Option 1: Keep one module, enforce boundaries with architecture tests

* Good, because it needs no build changes.
* Bad, because the client connectors still ship with the agent and all its dependencies.
* Bad, because extensions still compile against the whole connector.

### Option 2: One module per connector, one shared core library

* Good, because each connector is separate.
* Bad, because the client connectors would depend on the core library to be usable as gateway
  tools, which pulls in the agent model and implementation.
* Bad, because the core library mixes the extension API with its implementation.

### Option 3: Layered libraries, connectors and integrations (chosen)

* Good, because the client connectors depend on the common library only.
* Good, because extensions get a small, self-contained API.
* Good, because the layering prepares provider and LangChain4j modules.
* Bad, because it adds the most modules and build wiring.
