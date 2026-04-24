# Azure AI Foundry Provider

* Deciders: Agentic AI team
* Date: 2026-04-24

## Status

**Implemented**.

Delivered in Milestone 2 on branch `agentic-ai/azure-foundry-provider`. Milestone 1 shipped the element-template UI
with a fail-fast stub at runtime; Milestone 2 added the full runtime described here.

## Context and Problem Statement

Enterprise customers on Azure-first procurement or EU data-residency constraints access Claude models exclusively via
Azure AI Foundry. The AI Agent connector's existing provider set could not target Foundry's Anthropic endpoint
correctly:

* The direct Anthropic provider speaks the Anthropic Messages API but with direct-Anthropic auth conventions
  (`x-api-key` header) and a fixed default URL shape. Even with a `baseUrl` override, Azure-specific expectations
  (header naming, Entra ID token injection, `anthropic-version` pinning) broke the round-trip.
* The Azure OpenAI provider speaks the OpenAI Chat Completions API, which Foundry's Claude endpoint
  (`/anthropic/v1/messages`) does not expose.

Customers forced to use Foundry could not use the AI Agent with Claude at all.

See issue [camunda/connectors#6993](https://github.com/camunda/connectors/issues/6993) and the design spec at
`connectors/agentic-ai/docs/superpowers/specs/2026-04-23-azure-ai-foundry-provider-design.md` for full context.

## Decision Drivers

* **Urgency**: first-class blocker for a significant enterprise segment (Azure-first / EU-resident customers).
* **Future-proofing**: design should survive a potential future replacement of langchain4j by an internal thin
  abstraction layer without rework of the SDK integration.
* **Consistency**: Azure's Foundry resource hosts both OpenAI and Anthropic deployments under the same auth and
  endpoint shape. A unified "Azure AI Foundry" provider option avoids forcing users to choose between two parallel
  Azure providers.
* **Proxy support**: the agentic-ai connector ships with authenticated-proxy support through its JDK-HttpClient-based
  transport. Any new provider must preserve this without regression.

## Considered Options

1. **Roll our own Anthropic Messages HTTP client** (original brainstorming proposal).
2. **Use Anthropic's official `anthropic-java-foundry` SDK** with its default OkHttp transport
   (`AnthropicOkHttpClient`).
3. **Use the Anthropic SDK with a custom JDK-backed `HttpClient` SPI implementation** (chosen).
4. **Contribute a `langchain4j-azure-anthropic` module upstream** (deferred).

## Decision Outcome

Chosen option: **Option 3 — Use the Anthropic SDK with a custom JDK-backed `HttpClient` SPI implementation**
because it delegates wire-format correctness and Foundry auth to Anthropic's own SDK while preserving the connector's
existing JDK-based proxy support, and because it keeps all Foundry-specific code cleanly isolated from the langchain4j
layer.

Depend on `com.anthropic:anthropic-java-core` plus `com.anthropic:anthropic-java-foundry` (2.26.0) for the Foundry
wire format, and implement a `com.anthropic.core.http.HttpClient` SPI backed by `java.net.http.HttpClient` so the
connector's existing proxy support (including authenticated proxies) applies to Foundry traffic.

The OpenAI family of models on Foundry (GPT, plus any OpenAI-compatible model Microsoft routes through
`/openai/v1/chat/completions`) is delegated to the existing `langchain4j-azure-open-ai` integration via a shared
`buildAzureOpenAiChatModel(...)` helper extracted from `ChatModelFactoryImpl`.

### Positive Consequences

* **Wire-format correctness is Anthropic's problem.** The SDK handles the Messages API (content blocks, tool use, stop
  reasons, usage counters), Foundry-specific auth (`api-key` header for key-based auth; `Authorization: Bearer` for
  Entra ID via `FoundryBackend.bearerTokenSupplier`), and any future protocol updates.
* **Authenticated proxy support preserved.** Because we use `java.net.http.HttpClient` under the hood — wired through
  the connector's existing `JdkHttpClientProxyConfigurator` / `JdkProxyAuthenticator` — `HTTP_PROXY` / `HTTPS_PROXY` /
  `NO_PROXY` env vars with `user:pass@host:port` auth syntax continue to work for Foundry traffic. Option 2 would have
  forfeited this because `AnthropicOkHttpClient.builder()` does not expose OkHttp's proxy authenticator.
* **Langchain4j-decoupled.** The SDK integration lives in packages that do not import `dev.langchain4j.*`. A future
  langchain4j replacement requires rewriting only the `azurefoundry.langchain4j.*` adapter subpackage.
* **OpenAI-on-Foundry reuses the existing path.** Delegation to `langchain4j-azure-open-ai` via a shared helper —
  same code, no duplication, no new dependency.
* **Unified provider UX.** The element template presents a single "Azure AI Foundry" option with a model-family
  dropdown. This simplifies the user mental model and leaves room for future model families (Mistral, Cohere, Llama —
  all OpenAI-compatible on Foundry today) without new provider entries.

### Negative Consequences

* **New runtime dependencies.** `anthropic-java-core` + `anthropic-java-foundry` (plus Kotlin stdlib transitive) add
  approximately 3 MB of additional JARs.
* **Custom transport code to maintain.** Approximately 150 lines implementing the `HttpClient` SPI, plus tests.
  Well-isolated and mechanical, but still surface area.
* **Backend/ClientOptions integration is not fully wired by the SDK.** `ClientOptions.Builder` has no
  `.backend(FoundryBackend)` method; the `Backend` interface's `prepareRequest` / `authorizeRequest` /
  `prepareResponse` hooks must be invoked explicitly by a transport-level wrapper (`BackendAwareAnthropicHttpClient`
  in this codebase). This is a design gap in the SDK that required a small workaround.
* **`FoundryBackend.Builder.resource()` and `.baseUrl()` are mutually exclusive.** We use `.baseUrl(endpoint)` so
  the user's configured URL is authoritative — this also enables private Foundry endpoints and simplifies test mocking.
* **Some Anthropic SDK API shapes had to be verified at implementation time.** Kotlin-to-Java bytecode translation
  has sharp edges. One-off discovery cost; well-captured in the code now.

## Pros and Cons of the Options

### Option 1: Roll our own Anthropic Messages HTTP client

Implement the Anthropic Messages API wire format and Foundry auth headers directly, without depending on the
Anthropic SDK.

* Good, because zero new runtime dependencies
* Good, because full control over every request detail
* Bad, because reimplements a non-trivial protocol (content blocks, tool use, streaming, error codes)
* Bad, because auth logic (API key vs. Entra ID bearer token) must be maintained manually as Foundry evolves
* Bad, because no leverage from Anthropic's own test coverage and future SDK updates

### Option 2: Use the Anthropic SDK with its default OkHttp transport

Depend on `anthropic-java-foundry` and use `AnthropicOkHttpClient` as the transport.

* Good, because SDK handles all protocol and auth details
* Good, because minimal integration code
* Bad, because `AnthropicOkHttpClient.builder()` does not expose OkHttp's proxy authenticator, breaking the
  connector's authenticated-proxy feature for Foundry traffic
* Bad, because OkHttp is an additional transitive dependency not used elsewhere in the connector

### Option 3: Use the Anthropic SDK with a custom JDK-backed `HttpClient` SPI implementation (chosen)

Implement `com.anthropic.core.http.HttpClient` backed by `java.net.http.HttpClient` and wire it through the
connector's existing JDK proxy infrastructure.

* Good, because SDK handles protocol and auth details
* Good, because reuses JDK HTTP client already present — no new HTTP dependency
* Good, because proxy support is fully preserved with no regression
* Good, because all Foundry-specific code is isolated from the langchain4j layer
* Bad, because approximately 150 lines of custom transport code to maintain
* Bad, because `Backend` integration gap requires an explicit wrapper (`BackendAwareAnthropicHttpClient`)

### Option 4: Contribute a `langchain4j-azure-anthropic` module upstream

Implement support for Azure AI Foundry's Anthropic endpoint directly inside the langchain4j project.

* Good, because the entire ecosystem benefits; no bespoke code in this repo
* Good, because follows the same pattern as the existing `langchain4j-azure-open-ai` module
* Bad, because blocked by upstream release cycle — not viable for an urgent enterprise blocker
* Bad, because increases dependency on langchain4j at a time when its long-term role in the connector is under review

## Architectural Enforcement

ArchUnit rules in
`connectors/agentic-ai/src/test/java/io/camunda/connector/agenticai/azurefoundry/ArchitectureTest.java` enforce the
isolation boundaries:

1. Only classes in `..azurefoundry.langchain4j..` may depend on `dev.langchain4j..`.
2. Classes in `..azurefoundry..` must not depend on agent-framework internals (`..aiagent.agent..`,
   `..aiagent.memory..`, `..adhoctoolsschema..`).

The first rule is the escape hatch for a future langchain4j replacement: only the `azurefoundry.langchain4j.*`
adapter subpackage needs rewriting. The second rule prevents accidental coupling between the Foundry provider and
the rest of the agent framework beyond the `ChatModel` integration point.

## Related

* Milestone 1 (branch `agentic-ai/azure-foundry-provider`) delivered the element-template UI only, with a fail-fast
  stub at runtime.
* Milestone 2 delivered the runtime (this ADR documents that decision).
* Spec: `connectors/agentic-ai/docs/superpowers/specs/2026-04-23-azure-ai-foundry-provider-design.md`
  (unstaged / not part of the PR — working reference only).
* Original issue: [camunda/connectors#6993](https://github.com/camunda/connectors/issues/6993).
