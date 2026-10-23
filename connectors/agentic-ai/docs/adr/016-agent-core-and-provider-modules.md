# Provider-neutral agent core, provider modules and a domain model behind the connector

* Deciders: Agentic AI Team
* Date: Oct 21, 2026

## Status

**Proposed**. Extends [ADR 014](014-route-v1-requests-through-native-providers.md) (D1 and D3) and
[ADR 015](015-split-agentic-ai-into-modules.md) (the follow-ups to extract the chat model providers, to move the
element template records out of the library modules and to break the package cycles). The persistence of the
conversation turn structure is a separate decision.

## Context and Problem Statement

[ADR 015](015-split-agentic-ai-into-modules.md) split the agentic AI connectors into layered modules but left the AI
Agent implementation in one module, `connector-aiagent`. It holds the agent loop, the five native chat model
providers, the LangChain4j bridge, the conversation stores, the Camunda adapters and the connector's configuration
surface, the element template records. The architecture review of 2026-09-28 (issue #9532) found the cost of
that shape:

* One change often touches many packages, because policy sits in the wrong place. Each provider factory resolves
  timeout, proxy, endpoint and token again, and the factories drift. The core reads the nullable element template
  records and resolves the same defaults several times.
* A provider cannot be released, versioned, tested or owned on its own. Every provider imports the element template
  records, the Spring configuration properties and error codes of the agent loop.
* Anyone who builds an agent on the framework with another configuration surface, other providers or other memory
  backends has to take the whole connector.
* [ADR 014](014-route-v1-requests-through-native-providers.md) D3 removes LangChain4j. The reusable bridge, the
  converters between the framework's message model and LangChain4j, is the only way to put a LangChain4j backed
  model behind the chat model SPI. It must stay available without shipping any vendor factory.

How should the AI Agent be divided so that providers and memory backends are independent, the core carries no
provider-specific type, and the connector the product ships is one assembly of these parts?

## Decision Drivers

* **Independent providers**: a provider is a module that owns its configuration, its wire conversion and its failure
  mapping, and depends on the extension API and on small support libraries only.
* **A provider-neutral core**: no provider configuration, vendor SDK or vendor-specific type reaches the agent loop.
* **A flexible extension API**: custom extensions keep one consistent set of types, and the API must not limit use
  cases that need the execution context or the persisted agent context.
* **The element template generator needs closed, sealed types**: the connector's configuration surface stays sealed
  and in one place.
* **No behavior change for deployed processes**, except the changes listed in D9.
* **Nullability is enforced**: the model is non-null by default, and a nullable value has a reason.

## Considered Options

1. Keep one module and enforce conventions with architecture tests.
2. Providers own their configuration surface, the element template records included, and the core discovers them.
3. One provider-neutral core module, provider and memory backend modules that own their own configuration, and the
   connector as the configuration surface and the glue between them.

## Decision Outcome

Chosen option: **Option 3**. Option 1 leaves the dependencies of the providers on the connector in place. Option 2
needs the element template generator to build a union from non-sealed, discovered types, which it does not
support, and makes every provider part of the connector's configuration surface.

* **D1 — Modules and dependency direction.** The AI Agent is split into these modules, in addition to those of
  [ADR 015](015-split-agentic-ai-into-modules.md) D1:

  | Module                         | Kind      | Contents                                                                                                   | Depends on (agentic AI modules and Camunda runtime)                       |
  | ------------------------------ | --------- | ---------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------- |
  | `aiagent-core`                 | library   | Agent loop, agent instance reporting, conversation, memory runtime, in-process and Camunda document stores | tools-schema, api, model, common, connector core and runtime              |
  | `aiagent-provider-support`     | library   | Vendor-neutral provider code: connection settings, model call envelope, defaults                           | api, model, common                                                        |
  | `aiagent-azure-support`        | library   | Entra ID and Foundry credentials, Azure HTTP proxy support                                                 | none                                                                      |
  | `aiagent-aws-support`          | library   | AWS SDK HTTP proxy support                                                                                 | none                                                                      |
  | `aiagent-provider-<vendor>`    | library   | One native provider each: OpenAI, Anthropic, Mistral, Gemini, Bedrock                                      | provider-support, api, model, common, vendor support modules, connector core |
  | `aiagent-provider-langchain4j` | library   | The generic LangChain4j bridge (D6). Not part of the bundle                                                | provider-support, api, model, common, connector core and runtime          |
  | `aiagent-memory-agentcore`     | library   | The AWS AgentCore conversation store                                                                       | aws-support, api, model, common, connector core                           |
  | `aiagent-test-support`         | test only | Fixtures and fakes that the tests of the modules above share. Never deployed                               | provider-support, api, model, connector runtime                           |
  | `connector-aiagent`            | connector | Configuration surface, connector functions, translation, out-of-the-box wiring                             | core, all modules above except LangChain4j and test support, tools-schema, api, model, common |

  `aiagent-provider-mistral` also depends on `aiagent-provider-openai`: the API of Mistral is OpenAI-compatible, and the
  Mistral provider reuses the OpenAI Chat Completions wire mapping. This is the only dependency between two providers.
  `aiagent-provider-anthropic` depends on both vendor support modules, because it runs on Foundry and on Bedrock.

  The tests of a module live in the module. A test that needs a fixture takes it from `aiagent-test-support`, which is
  a regular module that the build does not deploy, so no test code is published. Because `aiagent-test-support` depends on
  `aiagent-model`, the tests of the model types run in `aiagent-core`. `connector-aiagent` tests the translation, the
  element templates and the contracts that every provider fulfils, and it takes no test dependency on the
  LangChain4j module.

  The package names do not change. A module owns the package roots it is listed with, nested below the roots of other
  modules the way `aiagent.model` and `aiagent.api` sit below `aiagent`, and a package belongs to exactly one module.
  Moving code into a module is a move of files, not a rename. `agentic-ai-bundle` depends on every module except
  `aiagent-provider-langchain4j` and `aiagent-test-support`.

* **D2 — The core.** `aiagent-core` is the agent loop with the Camunda engine adapters behind ports: the agent instance
  reporting and the document based conversation store stay in the core, so a custom connector runs on Camunda
  without reimplementing them. The core knows no provider and no configuration surface. It reads the provider's
  configuration only through the model interface `ChatModelConfiguration` (provider, model name and a descriptive
  provider name). The entry points a custom connector calls are an **experimental public API**: they may change in a
  minor release, and the intent is to promote them once they have settled.

* **D3 — The connector is the configuration surface and the glue.** `connector-aiagent` owns everything that the
  element templates and the process variables show: the sealed request records for the v1 and v2 provider unions,
  the connector functions, the element templates, the Spring configuration and the v1-to-v2 mapping. It depends on
  the core and on every out-of-the-box provider and memory backend, and it translates its request records into the
  domain types of the core and of the providers once per job. The record per provider stays separate, so each
  provider has its own labels and help texts in the template. Equal records of two providers are not merged. A custom
  connector brings its own configuration surface and maps it to the same domain types.

* **D4 — The domain model, and where it lives.**
  * The agent configuration (`AgentConfiguration` with memory, limits, events, response and response format) is a
    non-null domain model in `aiagent-model`, because the extension API exposes it through the execution context.
  * Each provider owns the configuration type its factory and converters read. It is not sealed, implements
    `ChatModelConfiguration`, and contains what the provider needs, not a copy of the template record. No
    provider module imports a request record.
  * The translation in the connector is an anti-corruption layer. It applies the defaults once, with this rule: a
    field is non-null and filled by the translation; a field is nullable only when absence is a valid state, such as
    "use the default of the provider"; and a value whose absence meant something different from the default, for
    example the behavior of a process modeled before an option existed, gets its own explicit value instead of the
    default of the template.
  * The v1 requests are translated to the same domain types, with the defaults the v1 path always had. The
    configuration fingerprint of an unchanged configuration stays identical.

* **D5 — Vendor-neutral support, and vendor-specific support modules.**
  * `aiagent-provider-support` contains no vendor name. It resolves the connection settings of a chat model call once:
    timeout, connect timeout, proxy with the non-proxy hosts check, endpoint and bearer token (a token source, such as
    OAuth client credentials). It provides the model call envelope as a decorator: timing, mapping of SDK failures
    to a connector error and pass-through of already coded exceptions. Each provider declares only how to read status,
    type and message from its SDK exceptions. Each provider factory applies the envelope to its own model, so the
    registry and the SPI do not change. The wire payload traces stay in the providers, because only the provider
    knows its SDK objects.
  * Vendor-specific code that two modules need lives in a vendor support module: Azure for OpenAI and Anthropic on
    Foundry, AWS for Bedrock, Anthropic on Bedrock and the AgentCore store.

* **D6 — LangChain4j is an opt-in bridge, not a provider stack.** This amends
  [ADR 014](014-route-v1-requests-through-native-providers.md) D3. The module `aiagent-provider-langchain4j` holds the
  generic code only: the converters between the message model and LangChain4j, the chat model adapter and the
  framework wiring. It ships no vendor factory. A runtime that needs a LangChain4j backed model adds the module and registers its own
  `ChatModelFactory`. The transitional switch is removed: v1 requests are translated to native providers
  unconditionally. The module is not part of the bundle, so the default runtime carries no `dev.langchain4j`
  dependency.

* **D7 — The extension points keep one consistent context.** The SPIs that need the execution state continue to
  receive the execution context together with the persisted agent context, and an extension that changes state
  returns an updated agent context. The agent context keeps its extension properties, which gateway tools already use
  to keep state between jobs. The execution context keeps the connector job context. The extension API does not
  narrow per SPI, because that would limit extensions that need more than one of these views.

* **D8 — Interfaces only where they pay.** Use an interface for an extension point of the extension API, for a type
  of the extension API that the framework implements and extension authors call (so that members can be added
  without breaking them), and for a seam with at least two real adapters, such as a port with a production adapter and
  an in-memory fake. Use a concrete class in all other cases, and do not add an interface with a single `*Impl`. An
  existing pair stays until its class changes for another reason.

* **D9 — Behavior changes of this decision.** Each is listed in the
  [breaking changes](../breaking-changes.md) or the release notes.
  * The LangChain4j fallback and its switch are removed (D6).
  * One default connect timeout for every native provider; the value of the shared constant replaces the Gemini value.
  * One bearer token path for OAuth and Entra ID.
  * The failure text of a model call has one format that contains HTTP status and type where the SDK provides them,
    and an exception that a provider's request conversion throws is now reported as a failed model call.
  * The extension API types listed in D4 and D7 change: `AgentConfiguration` and the types of the moved packages.

* **D10 — Requirements of the document flow.** The runtime tool facility and the document flow are built on these
  boundaries. They constrain the design above, and the domain model must be able to express them:
  * a document delivery that a process left unset keeps its meaning, and is an explicit value of the domain model
    (the explicit-value rule of D4);
  * the agent instance features, set when an instance is created, remain explicit in the model, and an instance
    without them keeps the earlier behavior;
  * the types a runtime tool sees are domain types, never request records or persisted types;
  * the inputs of the configuration fingerprint do not change again.

### Non-goals

* Enforcing module isolation beyond the Maven dependencies and architecture tests of
  [ADR 015](015-split-agentic-ai-into-modules.md), and extracting the other conversation stores.
* The persistence of the turn structure, the agent instance reporter contract and the response adapters.
* Typed, namespaced keys for the extension properties of the agent context.

### Positive Consequences

* A provider changes, is tested and can be versioned alone, and the next cross-cutting provider change touches one
  module.
* The core compiles without any provider, vendor SDK or configuration surface, so a custom connector can assemble its
  own agent.
* Defaults are applied once, in one place, and tests target one translator and one provider contract.
* The default runtime carries no LangChain4j dependency.

### Negative Consequences

* More modules, and two type sets per provider (the request record and the domain type). The translation code is the
  price of the anti-corruption layer.
* Adding a native provider means a new module and a case in the translation.
* The experimental core API and the changed extension types are breaking changes for custom extensions.
* A native provider cannot be left out of the bundle without regenerating the element template.

## Follow-up

* Extract the modules of D1 one at a time. A module is extracted when its code no longer imports the connector's
  packages. The error codes of model calls move to `aiagent-api`, `AssistantMessageMetadata` to `aiagent-model`,
  and the chat model properties to `aiagent-provider-support`, under the same property prefix. The architecture tests
  keep the package roots of each module and the dependency direction of D1.
* Decide the persistence of the turn structure and the agent instance reporter contract before the core API is
  promoted from experimental.
* Add an API compatibility check for the core, the API and the model modules, as announced in
  [ADR 015](015-split-agentic-ai-into-modules.md).

## Pros and Cons of the Options

### Option 1: Keep one module, enforce conventions

* Good, because it needs no build changes.
* Bad, because providers stay bound to the connector, and a custom connector takes everything.

### Option 2: Providers own their configuration surface

* Good, because a provider is complete in one module.
* Bad, because the element template generator does not support a union of discovered, non-sealed types.
* Bad, because the connector's configuration surface is spread over the provider modules.

### Option 3: Core, provider modules, and the connector as configuration surface (chosen)

* Good, because providers and the core are independent, and the surface the generator needs stays in one place.
* Good, because a custom connector can reuse the core and the providers with its own surface.
* Bad, because every provider needs a translation from the request record.
