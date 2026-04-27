# Azure AI Foundry Provider — Design Spec

**Date:** 2026-04-23
**Status:** Design approved; ready for implementation plan.
**Issue:** [camunda/connectors#6993](https://github.com/camunda/connectors/issues/6993)
**Branch:** `agentic-ai/azure-foundry-provider`

## Summary

Add a new "Azure AI Foundry" LLM provider to the AI Agent connector that supports Claude (Anthropic) models on Foundry — the gap motivating issue #6993 — and, under the same provider, OpenAI-family models on Foundry by delegation to the existing `langchain4j-azure-open-ai` integration. A single provider with a model-family dropdown (Anthropic / OpenAI, extensible) replaces the need for users to choose between two parallel Azure providers.

The Anthropic-on-Foundry surface is implemented via Anthropic's official `anthropic-java-foundry` SDK, configured with a custom JDK-backed `HttpClient` SPI implementation to preserve the connector's existing authenticated-proxy support. The OpenAI-on-Foundry surface routes through the same `AzureOpenAiChatModel` builder the existing `Azure OpenAI` provider uses — verified by research as the identical wire format on the same physical Foundry resource.

## Motivation

Enterprise customers on Azure-first procurement or EU-data-residency constraints can only access Claude models via Azure AI Foundry. The Camunda AI Agent connector's current providers cannot target Foundry's Anthropic endpoint correctly:

- The direct Anthropic provider hits `https://api.anthropic.com/v1/messages`; even when `baseUrl` is overridden to Foundry, subtle auth-header and endpoint-path assumptions cause failures.
- The Azure OpenAI provider speaks OpenAI Chat Completions, which Foundry Anthropic endpoints do not expose.

Customers forced to use Foundry cannot use Camunda's AI Agent with Claude at all. This is a first-class blocker for a significant enterprise segment.

## Research summary

Key findings that shaped the design (verified against Microsoft Learn, Anthropic docs, and the anthropic-sdk-java repository during brainstorming):

- **Azure OpenAI Service and OpenAI-on-Foundry are the same resource.** Post-upgrade, one resource exposes three FQDNs (`openai.azure.com`, `services.ai.azure.com`, `cognitiveservices.azure.com`) with identical auth and wire format. `langchain4j-azure-open-ai` works against Foundry unchanged. No deprecation of `openai.azure.com`.
- **Anthropic on Foundry accepts both `api-key` and `x-api-key`.** No header rewriting needed for key auth. Entra ID uses `Authorization: Bearer <token>` with scope `https://cognitiveservices.azure.com/.default`.
- **Anthropic ships an official Java SDK with first-class Foundry support**: `com.anthropic:anthropic-java-foundry:2.26.0`. Provides `FoundryBackend` (with API-key and `bearerTokenSupplier` flows) that plugs into `AnthropicClient`. Pairs with `com.azure.identity.AuthenticationUtil.getBearerTokenSupplier(...)` for Entra ID.
- **Non-Anthropic non-OpenAI models on Foundry** (Mistral, Cohere, Llama, DeepSeek, xAI) are routed through `/openai/v1/chat/completions` — OpenAI-compatible wire format. No per-vendor endpoints for them today.

## Design decisions

The following decisions were settled during brainstorming, in order. Each shapes the design below.

| # | Decision |
|---|---|
| 1 | New "Azure AI Foundry" provider will **supersede** the existing "Azure OpenAI" provider conceptually. Graceful migration: existing configs keep working byte-for-byte. Deprecation labeling / UX changes deferred to a follow-up PR. |
| 2 | **Package-scoped SDK** (not a separate Maven module). Enforced via ArchUnit. |
| 3 | v1 protocol scope: **Anthropic Messages natively**; OpenAI family delegates to existing `langchain4j-azure-open-ai` via a shared helper in `ChatModelFactoryImpl`. Responses API deferred. |
| 4 | ~~Custom SDK~~ **Use `anthropic-java-core` + `anthropic-java-foundry`** with a custom JDK-backed `HttpClient` SPI implementation. (Revised from the original "roll our own" plan after discovering the official SDK.) |
| 5 | Anthropic feature scope wired up at v1: **core chat + tool use + stop reason + usage counters**. Vision / prompt caching / thinking blocks are left for extension (anthropic-java SDK supports them; we simply don't populate them in the adapter today). No streaming. |
| 6 | Auth methods: **API key + client credentials**, reusing the shared `AzureAuthentication` sealed type extracted from `AzureOpenAiProviderConfiguration`. Managed Identity and ROPC are non-breaking additions later. |
| 7 | Provider config: **sealed `AzureAiFoundryModel` interface** with `AnthropicModel` / `OpenAiModel` variants. Family dropdown lives inside the "model" group. Endpoint and auth at provider level (unchanged across families). |
| 8 | Existing `Azure OpenAI` provider: **no template changes in this PR**. Code-level `AzureAuthentication` extracted to `shared/` (backward-compatible; same JSON subtype IDs). |
| 9 | Error handling: map each `AnthropicException` subtype to `ConnectorInputException` (terminal) vs `ConnectorException` (retryable) in the adapter. SDK's exception hierarchy is the error-type enum. |
| 10 | Testing: unit (ArchUnit + component-level) + e2e in `connectors-e2e-test-agentic-ai`; no live Foundry test in CI; manual smoke before merge. |
| 11 | Endpoint field accepts resource-base URL (`https://<resource>.services.ai.azure.com`); SDK composes the subpath. Provider label: **"Azure AI Foundry"**; JSON type ID: `azureAiFoundry`. |

## Architecture

### Module topology

All code lives inside the existing `agentic-ai/` Maven module. Two new packages:

```
io.camunda.connector.agenticai.azurefoundry/
├── AnthropicOnFoundryClientFactory.java        ← builds AnthropicClient from config
├── http/
│   └── JdkAnthropicHttpClient.java             ← implements com.anthropic.core.http.HttpClient
└── langchain4j/
    └── AnthropicOnFoundryChatModel.java        ← adapter: dev.langchain4j.ChatModel ↔ AnthropicClient
```

Touched existing code:

```
io.camunda.connector.agenticai.aiagent.model.request.provider/
├── AzureFoundryProviderConfiguration.java      ← NEW (sealed record tree)
├── AzureOpenAiProviderConfiguration.java       ← references shared AzureAuthentication
├── ProviderConfiguration.java                  ← new sealed subtype registered
└── shared/
    └── AzureAuthentication.java                ← EXTRACTED from AzureOpenAiProviderConfiguration

io.camunda.connector.agenticai.aiagent.framework.langchain4j/
├── ChatModelFactoryImpl.java                   ← new case branch + shared OpenAI helper
└── ChatModelHttpProxySupport.java              ← unchanged (JdkHttpClientProxyConfigurator already suffices)

io.camunda.connector.agenticai.autoconfigure/
└── AgenticAiConnectorsAutoConfiguration.java   ← wires AnthropicOnFoundryClientFactory
```

### Dependency flow

```
ChatModelFactoryImpl
  └── AzureFoundryProviderConfiguration (case)
      ├── AnthropicModel  → AnthropicOnFoundryClientFactory
      │                      → AnthropicClient.builder()
      │                          .httpClient(JdkAnthropicHttpClient)
      │                          .backend(FoundryBackend)
      │                          .build()
      │                      → wrap in AnthropicOnFoundryChatModel (langchain4j ChatModel)
      └── OpenAiModel     → [shared helper] buildAzureOpenAiChatModel(...)
                                 → AzureOpenAiChatModel (existing path, unchanged)
```

### Invariants

1. **No `dev.langchain4j.*` imports outside `azurefoundry.langchain4j.*`** — enforced by ArchUnit.
2. **No imports of agent-framework internals from inside `azurefoundry.*`** — specifically no `aiagent.agent..`, `aiagent.memory..`, or `adhoctoolsschema..`. The factory legitimately imports provider-config types from `aiagent.model.request.provider..`; that package is not a framework internal. Enforced by ArchUnit.
3. **OpenAI-on-Foundry goes through exactly the same code path** as the existing `Azure OpenAI` provider. Any behavior drift is a test failure, not a design question.
4. **Existing `Azure OpenAI` BPMN configs deserialize and execute unchanged** — the `AzureAuthentication` extraction preserves Jackson subtype IDs (`apiKey`, `clientCredentials`).

### Extension points (not wired up today)

- **Native OpenAI-on-Foundry** (when we want to escape `langchain4j-azure-open-ai`): add `openai/` subpackage under `azurefoundry/`, new adapter under `azurefoundry/langchain4j/`, second case branch in `createAzureFoundryChatModel(...)`.
- **Additional model families** (e.g., Mistral): add sealed-interface variants to `AzureAiFoundryModel`. Anthropic SDK unaffected; most routes resolve to the OpenAI-compatible wire format.
- **Vision / prompt caching / extended thinking**: anthropic-java SDK already supports all three; the adapter simply starts populating the relevant fields on `MessageCreateParams`. No SDK-layer changes.
- **Managed Identity / ROPC auth**: new sealed variants on `shared/AzureAuthentication`. Both Foundry and existing Azure OpenAI providers pick them up automatically.
- **Langchain4j replacement**: rewrite `azurefoundry/langchain4j/` only; the client factory + HttpClient SPI impl are langchain4j-free.

## Provider configuration

### Extracted shared auth type (prep refactor)

```java
// io.camunda.connector.agenticai.aiagent.model.request.provider.shared.AzureAuthentication
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = AzureApiKeyAuthentication.class, name = "apiKey"),
    @JsonSubTypes.Type(value = AzureClientCredentialsAuthentication.class, name = "clientCredentials")
})
@TemplateDiscriminatorProperty(
    label = "Authentication",
    group = "provider",
    name = "type",
    defaultValue = "apiKey",
    description = "Specify the Azure authentication strategy.")
public sealed interface AzureAuthentication
    permits AzureApiKeyAuthentication, AzureClientCredentialsAuthentication {

  @TemplateSubType(id = "apiKey", label = "API key")
  record AzureApiKeyAuthentication(@NotBlank @TemplateProperty(...) String apiKey)
      implements AzureAuthentication { /* toString redacts apiKey */ }

  @TemplateSubType(id = "clientCredentials", label = "Client credentials")
  record AzureClientCredentialsAuthentication(
      @NotBlank String clientId,
      @NotBlank String clientSecret,
      @NotBlank String tenantId,
      String authorityHost                 // optional
  ) implements AzureAuthentication { /* toString redacts clientSecret */ }
}
```

`AzureOpenAiProviderConfiguration.AzureAuthentication` and its nested records are removed; the file now imports the shared type. Jackson subtype IDs remain identical — existing `type: "azureOpenAi"` BPMN configs deserialize unchanged.

### New provider configuration

```java
@TemplateSubType(id = AZURE_AI_FOUNDRY_ID, label = "Azure AI Foundry")
public record AzureFoundryProviderConfiguration(
    @Valid @NotNull AzureAiFoundryConnection azureAiFoundry)
    implements ProviderConfiguration {

  @TemplateProperty(ignore = true)
  public static final String AZURE_AI_FOUNDRY_ID = "azureAiFoundry";

  public record AzureAiFoundryConnection(
      @NotBlank @HttpUrl @FEEL
          @TemplateProperty(
              group = "provider",
              description =
                  "Azure AI Foundry resource endpoint (e.g. <code>https://&lt;resource&gt;.services.ai.azure.com</code>). "
                      + "Anthropic models require the <code>services.ai.azure.com</code> FQDN.",
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String endpoint,
      @Valid @NotNull AzureAuthentication authentication,
      @Valid TimeoutConfiguration timeouts,
      @Valid @NotNull AzureAiFoundryModel model) {}

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "family")
  @JsonSubTypes({
      @JsonSubTypes.Type(value = AnthropicModel.class, name = "anthropic"),
      @JsonSubTypes.Type(value = OpenAiModel.class, name = "openai")
  })
  @TemplateDiscriminatorProperty(
      label = "Model family",
      group = "model",
      name = "family",
      defaultValue = "anthropic",
      description = "Select which model family is deployed behind this Foundry endpoint.")
  public sealed interface AzureAiFoundryModel permits AnthropicModel, OpenAiModel {

    @TemplateSubType(id = "anthropic", label = "Anthropic (Claude)")
    record AnthropicModel(
        @NotBlank String deploymentName,
        @Valid AnthropicModelParameters parameters)
        implements AzureAiFoundryModel {

      public record AnthropicModelParameters(
          Integer maxTokens, Double temperature, Double topP, Integer topK) {}
    }

    @TemplateSubType(id = "openai", label = "OpenAI (GPT)")
    record OpenAiModel(
        @NotBlank String deploymentName,
        @Valid OpenAiModelParameters parameters)
        implements AzureAiFoundryModel {

      public record OpenAiModelParameters(
          Integer maxTokens, Double temperature, Double topP) {}
    }
  }
}
```

Registration in `ProviderConfiguration` sealed interface: new `@JsonSubTypes` entry + new `permits` entry.

### Form shape (generated from annotations)

1. Provider: Azure AI Foundry
2. Endpoint (resource-base URL; template hint notes Anthropic-FQDN requirement)
3. Authentication (sub-dropdown: API key / Client credentials)
4. Timeouts
5. Model family (sub-dropdown: Anthropic / OpenAI)
6. Deployment name
7. Family-specific parameters (maxTokens, temperature, topP always; topK Anthropic only)

## SDK integration

### Dependencies

```xml
<dependency>
  <groupId>com.anthropic</groupId>
  <artifactId>anthropic-java-core</artifactId>
  <version>2.26.0</version>
</dependency>
<dependency>
  <groupId>com.anthropic</groupId>
  <artifactId>anthropic-java-foundry</artifactId>
  <version>2.26.0</version>
</dependency>
```

Version property in parent `pom.xml`: `<version.anthropic-java>2.26.0</version.anthropic-java>`.

No OkHttp transitive dependency (verified: `anthropic-java-foundry` depends only on `anthropic-java-core`, and `anthropic-java-core` does not pull OkHttp).

### `JdkAnthropicHttpClient`

Custom implementation of `com.anthropic.core.http.HttpClient` backed by JDK `java.net.http.HttpClient`. Satisfies four SPI methods:

- `execute(HttpRequest, RequestOptions): HttpResponse`
- `execute(HttpRequest): HttpResponse` (delegates)
- `executeAsync(HttpRequest, RequestOptions): CompletableFuture<HttpResponse>`
- `executeAsync(HttpRequest): CompletableFuture<HttpResponse>` (delegates)
- `close()` (no-op; JDK client doesn't require explicit close)

Conversion responsibilities:
- Anthropic `HttpRequest` method + URL + headers + body → JDK `HttpRequest.Builder`
- JDK `HttpResponse<byte[]>` status + headers + body → Anthropic `HttpResponse`
- Timeout from `TimeoutConfiguration` applied via `HttpRequest.Builder.timeout(...)`

Benefits of this approach:
- Full authenticated-proxy support via existing `JdkHttpClientProxyConfigurator` + `JdkProxyAuthenticator` (reads `http://user:pass@proxy:port` env-var syntax used by the rest of the connector).
- No OkHttp dependency; consistent with the rest of the agentic-ai module's HTTP stack.
- Unit-testable with WireMock (already a test dep).

### `AnthropicOnFoundryClientFactory`

Constructs `AnthropicClient` instances from provider configuration. Responsibilities:
- Strip trailing slash from endpoint; extract resource name from the FQDN.
- Build JDK `HttpClient` with proxy configuration from `ChatModelHttpProxySupport`.
- Wrap in `JdkAnthropicHttpClient`.
- Build `FoundryBackend`:
  - For `AzureApiKeyAuthentication`: `FoundryBackend.builder().resource(...).apiKey(...).build()`.
  - For `AzureClientCredentialsAuthentication`: build `ClientSecretCredential` via `com.azure.identity.ClientSecretCredentialBuilder`; wrap with `com.azure.identity.AuthenticationUtil.getBearerTokenSupplier(credential, "https://cognitiveservices.azure.com/.default")`; pass to `FoundryBackend.builder().bearerTokenSupplier(...)`.
- Compose: `AnthropicClient.builder().httpClient(anthropicHttp).backend(backend).build()`.

The exact public-API entry point for constructing `AnthropicClient` with custom `httpClient(...)` + `backend(...)` is confirmed to exist (per anthropic-java-core's `ClientOptions.Builder.httpClient(...)` docstring: *"Use the one published in anthropic-java-client-okhttp or implement your own"*). Exact shape of the client-side builder chain to be confirmed at implementation time; worst case requires a ~10-line helper.

### `AnthropicOnFoundryChatModel` (langchain4j adapter)

Implements `dev.langchain4j.model.chat.ChatModel`. Single public method `chat(ChatRequest) -> ChatResponse`. Private conversions:

- **Outgoing**: langchain4j `ChatRequest` → Anthropic `MessageCreateParams`
  - System messages → `system(String)` (or block form if composite)
  - User / Assistant / ToolExecutionResult messages → Anthropic message `content` blocks
  - Tool results use `tool_result` blocks inside a user message, keyed by `tool_use_id`
  - Tool specifications → Anthropic `ToolDefinition` with `input_schema` (JSON schema derived from langchain4j's `ToolSpecification.parameters()`)
  - Parameters (maxTokens, temperature, topP, topK, stopSequences) from the `AnthropicModel.parameters()` record
  - Model name = `AnthropicModel.deploymentName()` (Foundry deployment name is what the Anthropic `model` body field expects)

- **Incoming**: Anthropic `Message` → langchain4j `ChatResponse`
  - Text content blocks → `AiMessage` text
  - `tool_use` blocks → `ToolExecutionRequest`s on the `AiMessage`
  - `StopReason` → langchain4j `FinishReason`: `END_TURN` → `STOP`, `TOOL_USE` → `TOOL_EXECUTION`, `MAX_TOKENS` → `LENGTH`, others → `OTHER`
  - `Usage` → `TokenUsage` (including cache read/create tokens when present)

- **Error translation**: each `AnthropicException` subtype → appropriate `ConnectorException` / `ConnectorInputException`:
  - `AuthenticationException`, `PermissionDeniedException`, `BadRequestException`, `NotFoundException`, `UnprocessableEntityException` → `ConnectorInputException` (terminal; configuration/request issue)
  - `RateLimitException`, `InternalServerException` → `ConnectorException` (retryable at connector-runtime level)
  - Default → `ConnectorException` with SDK's status code and message

### Factory wiring in `ChatModelFactoryImpl`

```java
case AzureFoundryProviderConfiguration af -> createAzureFoundryChatModel(af);
```

```java
private ChatModel createAzureFoundryChatModel(AzureFoundryProviderConfiguration configuration) {
  var conn = configuration.azureAiFoundry();
  return switch (conn.model()) {
    case AnthropicModel anthropic -> anthropicOnFoundryClientFactory
        .create(conn.endpoint(), conn.authentication(), conn.timeouts(), anthropic);
    case OpenAiModel openai -> buildAzureOpenAiChatModel(
        conn.endpoint(),
        conn.authentication(),
        conn.timeouts(),
        openai.deploymentName(),
        params(openai));
  };
}
```

The existing `createAzureOpenAiChatModelBuilder` is refactored to an unwrap+delegate over the same shared helper. No behavior change on the existing `Azure OpenAI` provider's code path.

Spring wiring: `AnthropicOnFoundryClientFactory` registered as a `@Bean` in `AgenticAiConnectorsAutoConfiguration`, constructor-injected into `ChatModelFactoryImpl`.

## Architectural enforcement

### ArchUnit rules

Test at `azurefoundry/ArchitectureTest.java`:

```java
@AnalyzeClasses(packages = "io.camunda.connector.agenticai.azurefoundry")
class ArchitectureTest {

  @ArchTest
  static final ArchRule sdk_layer_must_not_depend_on_langchain4j =
      noClasses()
          .that().resideInAPackage("..azurefoundry..")
          .and().resideOutsideOfPackage("..azurefoundry.langchain4j..")
          .should().dependOnClassesThat().resideInAnyPackage("dev.langchain4j..")
          .because("Only the adapter subpackage may depend on langchain4j; the rest must "
                 + "survive a future langchain4j replacement without modification.");

  @ArchTest
  static final ArchRule azurefoundry_must_not_depend_on_agent_framework_internals =
      noClasses()
          .that().resideInAPackage("..azurefoundry..")
          .should().dependOnClassesThat()
            .resideInAnyPackage("..aiagent.agent..", "..aiagent.memory..", "..adhoctoolsschema..")
          .because("The Foundry packages must stay decoupled from agent framework internals; "
                 + "the only integration point is ChatModel.");
}
```

Test deps add (with a new `<version.archunit>` property introduced in parent `pom.xml`, currently tracking the latest 1.x ArchUnit release):

```xml
<dependency>
  <groupId>com.tngtech.archunit</groupId>
  <artifactId>archunit-junit5</artifactId>
  <version>${version.archunit}</version>
  <scope>test</scope>
</dependency>
```

## Testing strategy

### TDD discipline

Red-green-refactor at every implementation phase. Public-interface (e2e) tests written first and fail until the implementation catches up. Internal component tests precede their implementations.

### Unit tests

| File | What it verifies |
|---|---|
| `azurefoundry/http/JdkAnthropicHttpClientTest.java` | Request/response conversion SDK↔JDK (headers, body, methods), timeout propagation, sync + async paths, error surfacing. Uses WireMock. |
| `azurefoundry/AnthropicOnFoundryClientFactoryTest.java` | Resource-name extraction; API-key vs client-credentials branches; bearer-token supplier scope; `TokenCredential` construction; proxy wiring. |
| `azurefoundry/langchain4j/AnthropicOnFoundryChatModelTest.java` | `ChatRequest` → `MessageCreateParams` conversion (messages, tools, system, params). `Message` → `ChatResponse` conversion (text, tool_use, StopReason, Usage). Each `AnthropicException` subtype → correct `ConnectorException`/`ConnectorInputException`. |
| `azurefoundry/ArchitectureTest.java` | ArchUnit rules above. |
| `aiagent/framework/langchain4j/ChatModelFactoryTest.java` (update) | Dispatch on `AzureFoundryProviderConfiguration` with each model family. |
| `aiagent/model/request/provider/AzureFoundryProviderConfigurationDeserializationTest.java` | JSON fixtures roundtrip for both families. |
| `aiagent/model/request/provider/AzureOpenAiProviderConfigurationDeserializationTest.java` (update) | Legacy `azureOpenAi` JSON still roundtrips after shared-type extraction. |

### E2E tests (in `connectors-e2e-test-agentic-ai`)

| Test class | Scenario |
|---|---|
| `AzureFoundryAnthropicAgentE2ETest` | Full agent loop: system prompt + user message + tool call round-trip + final response. Mocked Anthropic Messages wire-format responses. |
| `AzureFoundryOpenAiAgentE2ETest` | Same shape with `modelFamily=openai`; verifies delegation through `langchain4j-azure-open-ai`. |
| `AzureOpenAiLegacyCompatibilityE2ETest` | Loads a BPMN referencing the old `azureOpenAi` provider type; confirms end-to-end flow works after the `AzureAuthentication` extraction. Safety net against backward-compat regressions. |

No live CI test. Manual smoke test against a real Foundry deployment before merge, performed by the PR author.

## Implementation sequence (milestone-staged, TDD inside Milestone 2)

### Milestone 1 — Element template UX (demo-first, no runtime behavior)

**Goal:** ship the new "Azure AI Foundry" provider's element template ahead of any runtime implementation, so the form can be demonstrated in the Camunda Modeler — letting the team see the dropdown, model-family discriminator, and field set before code is written.

**Why this works:** the element template JSON is generated from annotated Java records (`@TemplateSubType`, `@TemplateProperty`, `@TemplateDiscriminatorProperty`) by the `gmavenplus-plugin` during `mvn compile`. Adding the annotated record skeleton + a stub factory branch is enough for the template to regenerate; the Modeler can load it and render the new form. The Java compiles without runtime logic.

**Steps:**

1. Prep refactor: extract `AzureAuthentication` from `AzureOpenAiProviderConfiguration` into `shared/AzureAuthentication.java`. All existing tests stay green (existing `Azure OpenAI` tests are the safety net for this refactor).
2. Add `AzureFoundryProviderConfiguration` record with the full annotation tree:
   - `AzureAiFoundryConnection` (endpoint, shared `AzureAuthentication`, timeouts, model)
   - Sealed `AzureAiFoundryModel` with `AnthropicModel` and `OpenAiModel` variants
   - Per-family parameter records (`AnthropicModelParameters` with `topK`; `OpenAiModelParameters` without)
   - All template annotations matching Section "Provider configuration" above.
3. Register in `ProviderConfiguration` sealed interface: new `@JsonSubTypes` entry + new `permits` entry. No other changes to existing providers.
4. Add a case branch in `ChatModelFactoryImpl.createChatModel(...)` that throws `UnsupportedOperationException("Azure AI Foundry runtime not yet implemented")`. Compiles; existing provider dispatch unchanged.
5. Regenerate element templates: `mvn clean compile -pl connectors/agentic-ai`.
6. Per `element-templates/README.md` versioning rules: bump the AI Agent Task + Sub-process template versions; move the superseded templates to `versioned/`; update both tables in the README.
7. Verification: `mvn test -pl connectors/agentic-ai` — all existing tests must remain green. The new annotated record is exercised implicitly by the template generator.

**Deliverable:** branch with regenerated `agenticai-aiagent-outbound-connector.json` and `agenticai-aiagent-job-worker.json` showing the new "Azure AI Foundry" provider option. **Demo checkpoint** — user loads into Modeler, demonstrates the form to the team. Feedback gathered here can shape Milestone 2 (e.g., field labels, descriptions, default values) before code is written against the contract.

**Important:** at this milestone, attempting to actually run a process configured with the new provider raises `UnsupportedOperationException` at job execution. Intentional — runtime work follows in Milestone 2. Documented in the commit message and the milestone's PR description.

### Milestone 2 — Runtime implementation (TDD-driven)

After Milestone 1's demo + feedback, implement the runtime behind the contract.

#### Phase 1 — Contract tests first (red)

1. Write `AzureFoundryAnthropicAgentE2ETest` — red. Encodes the user-visible contract for Anthropic on Foundry: full agent loop (system prompt + user message + tool call + final response) with mocked Anthropic Messages wire-format responses.
2. Write `AzureFoundryOpenAiAgentE2ETest` — red. Same shape with `modelFamily=openai`; verifies delegation through `langchain4j-azure-open-ai`.
3. Write `AzureOpenAiLegacyCompatibilityE2ETest` — passes from day one (existing provider already works post-refactor). Safety net against backward-compat regressions during M2.
4. The two Foundry e2e tests fail at invoke because the factory branch from M1 still throws `UnsupportedOperationException`. Failure point is intentional; clear error message names the gap.

#### Phase 2 — Dependencies + architectural guard

5. Add `anthropic-java-core` + `anthropic-java-foundry` dependencies (+ managed version in parent `pom.xml`).
6. Add `archunit-junit5` test dependency (+ new `<version.archunit>` property in parent `pom.xml`).
7. Add `azurefoundry/ArchitectureTest.java` — passes immediately (no code in the forbidden packages yet); guards all subsequent implementation.

#### Phase 3 — Provider configuration tests (post-hoc validation of M1 record)

8. Write `AzureFoundryProviderConfigurationDeserializationTest` covering both family variants + legacy `azureOpenAi` compat. Red if any annotation typos slipped through M1 — green confirms the record from M1 is sound.
9. Refactor record annotations only if tests show issues.

#### Phase 4 — Refactor: extract shared OpenAI builder helper

10. Extract `buildAzureOpenAiChatModel(...)` helper in `ChatModelFactoryImpl`. Existing `Azure OpenAI` path delegates. All existing tests stay green. (This refactor is deferred from M1 because the new helper is only consumed by the Foundry-OpenAI dispatch path that lands in Phase 8.)

#### Phase 5 — JDK-backed HttpClient SPI (red → green → refactor)

11. Write `JdkAnthropicHttpClientTest` using WireMock. Red.
12. Implement `JdkAnthropicHttpClient`. Green.
13. Refactor.

#### Phase 6 — Client factory (red → green → refactor)

14. Write `AnthropicOnFoundryClientFactoryTest`. Red.
15. Implement `AnthropicOnFoundryClientFactory`. Green.
16. Refactor.

#### Phase 7 — Adapter (red → green → refactor)

17. Write `AnthropicOnFoundryChatModelTest`. Red.
18. Implement `AnthropicOnFoundryChatModel`. Green.
19. Refactor.

#### Phase 8 — Factory dispatch + Spring wiring (red → green)

20. Update `ChatModelFactoryTest` for Foundry dispatch. Red (M1 stub still throws).
21. Replace the M1 stub branch with real dispatch (Anthropic → factory; OpenAI → shared helper). Spring wiring for `AnthropicOnFoundryClientFactory`. Green.
22. **Phase 1's Foundry e2e tests now pass.** Contract loop closes.

#### Phase 9 — Docs + ADR

23. Update `docs/reference/ai-agent.md` with the Foundry provider section.
24. Update `AGENTS.md` provider list.
25. Author ADR `00N-azure-ai-foundry-provider.md`.

#### Phase 10 — Final verification

26. `mvn test -pl connectors/agentic-ai` → all green.
27. `mvn test -pl connectors-e2e-test/connectors-e2e-test-agentic-ai -Dtest=AzureFoundry*,AzureOpenAiLegacy*` → all green.
28. Manual smoke test against a real Foundry deployment with a Claude model.

## Documentation impact

| Doc | Change |
|---|---|
| `agentic-ai/AGENTS.md` | Add Azure AI Foundry to the provider list; note the anthropic-java SDK integration pattern. |
| `agentic-ai/docs/reference/ai-agent.md` | New subsection: Foundry provider dispatch (Anthropic native via SDK, OpenAI delegated), sealed model family, custom `HttpClient` SPI integration, ArchUnit decoupling. |
| `agentic-ai/element-templates/README.md` | Bump AI Agent Task + Sub-process tables per the version-index rules; move superseded templates to `versioned/`. |
| `agentic-ai/docs/adr/00N-azure-ai-foundry-provider.md` | **New ADR.** Context (issue #6993), decision drivers (Foundry customer demand, langchain4j gap, future extensibility), options evaluated (roll-our-own / wrap langchain4j / Anthropic SDK direct), decision outcome, trade-offs (OkHttp avoidance via custom HttpClient SPI; ArchUnit-enforced boundary). |

## Scope

### In scope (this PR)

- New `AzureFoundryProviderConfiguration` with Anthropic and OpenAI model families.
- Native Anthropic-on-Foundry via `anthropic-java-foundry` SDK with custom JDK-backed `HttpClient` SPI.
- OpenAI-on-Foundry via delegation to existing `langchain4j-azure-open-ai` (shared builder helper).
- Extracted shared `AzureAuthentication` type in `shared/`.
- ArchUnit rules enforcing SDK-layer decoupling.
- Element template regeneration, version bump, README update.
- Unit + e2e test coverage per the TDD sequence.
- ADR documenting the architectural decision.

### Explicitly deferred

- Azure OpenAI provider deprecation labels / template description changes (separate follow-up PR).
- OpenAI Responses API native support.
- Vision / prompt caching / extended thinking adapter wiring (anthropic-java SDK already supports; adapter doesn't populate today).
- Managed Identity, ROPC auth methods (extensible via the sealed `AzureAuthentication` hierarchy).
- Live Foundry integration test in CI (QA can add in a separate project).
- Contribution to langchain4j for a native Foundry module.
- Authenticated-proxy support for any OkHttp-based connector path (not applicable here — we use JDK HttpClient).

## Open items at implementation time

- Confirm the exact public-API builder chain for `AnthropicClient.builder().httpClient(...).backend(...).build()`. The `ClientOptions.Builder.httpClient(...)` method is documented as public and designed for custom implementations, so this should be straightforward; worst case is a ~10-line helper that uses `ClientOptions.builder()` directly.
- Confirm `AnthropicException` subclass hierarchy matches the mapping in the adapter; adjust the `switch` pattern match as needed when the implementation lands.
- Verify element-template generation picks up the new provider subtype annotations correctly and that the model-family nested dropdown renders as expected in the Modeler.

## References

- GitHub issue: [camunda/connectors#6993](https://github.com/camunda/connectors/issues/6993)
- Anthropic docs: [Claude in Microsoft Foundry](https://platform.claude.com/docs/en/build-with-claude/claude-in-microsoft-foundry)
- Microsoft docs: [Deploy and use Claude models in Microsoft Foundry](https://learn.microsoft.com/en-us/azure/foundry/foundry-models/how-to/use-foundry-models-claude)
- Microsoft docs: [Upgrade Azure OpenAI to Microsoft Foundry](https://learn.microsoft.com/en-us/azure/foundry/how-to/upgrade-azure-openai)
- Microsoft docs: [Endpoints for Microsoft Foundry Models](https://learn.microsoft.com/en-us/azure/foundry/foundry-models/concepts/endpoints)
- Anthropic Java SDK: [anthropic-sdk-java](https://github.com/anthropics/anthropic-sdk-java)
