# Native OpenAI Provider Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a native OpenAI provider (`framework/openai/**`) at feature parity with the native Anthropic provider, covering both the Responses API (full) and Chat Completions (subset), graded by `NativeProviderAcceptanceIT`.

**Architecture:** Mirror the Anthropic framework package exactly. One `OpenAiChatModelApi implements ChatModelApi` dispatches by `OpenAiApiFamily` to a per-family strategy (Responses / Completions), each owning its request/response converter + stream assembler. The config layer, neutral SPI, content model, and capability-matrix infrastructure already exist and are reused unchanged; only the runtime, the OpenAI SDK dependency, three new config fields, and the OpenAI capability-projection classes are new.

**Tech Stack:** Java 21, Spring Boot, `com.openai:openai-java` (official SDK, new dependency), Jackson, JUnit 5 + AssertJ + Mockito, Camunda Process Test (acceptance IT), element-template-generator (annotation-driven templates).

**Reference implementation to mirror throughout:** `connectors/agentic-ai/connector-agentic-ai/src/main/java/io/camunda/connector/agenticai/aiagent/framework/anthropic/**`. Every OpenAI class has an Anthropic sibling named identically with `Anthropic`→`OpenAi`. Read the sibling before writing each class.

## Global Constraints

- **Module root (main):** `connectors/agentic-ai/connector-agentic-ai/`. Java source root: `src/main/java/io/camunda/connector/agenticai/`. Abbreviated below as `…/agenticai/`.
- **Package for all new runtime code:** `io.camunda.connector.agenticai.aiagent.framework.openai`.
- **License headers:** main module (`connector-agentic-ai`) files use the **proprietary** Camunda header ("Licensed under a proprietary license. See the License.txt"); the **e2e-test module** files use the **Apache 2.0** header. Copy the header from an existing file in the same module — never mix them.
- **Never `mvn install` to `~/.m2`** — another worktree iterates the same `8.10.0-SNAPSHOT`. Use `mvn test` / `mvn test-compile` only. `-am` (build upstream modules in-reactor) is fine.
- **Run Maven and git with the sandbox disabled** (Mockito MockMaker + network): every `Bash` call that runs `mvn` or `git` sets `dangerouslyDisableSandbox: true`.
- **Do NOT run real-API acceptance tests** (`RUN_NATIVE_LLM_E2E=true` / real `OPENAI_API_KEY`) without explicit user permission — cost-sensitive. Compile-only and gated-skip runs are fine. The final real run is a manual, permission-gated step (Task 16).
- **`ProviderContent.payload` must be plain JSON** (a `Map`/`List`/scalar at runtime), never a live vendor SDK object. Round-trip via `objectMapper.convertValue(payload, VendorType.class)`.
- **`ChatModelApi.call()` is exactly ONE provider round-trip** — no internal tool loop. It consumes the vendor API **streamably** (SDK accumulator), like the Anthropic path.
- **Factory registration order:** `ORDER = 100` (same as Anthropic — safe, disjoint `supports()`).
- Every new class gets a `package-info.java`-consistent proprietary header; `framework/openai/package-info.java` and `framework/openai/configuration/package-info.java` mirror the Anthropic ones.

---

## File Structure

**New (main module, `…/agenticai/aiagent/framework/openai/`):**
- `OpenAiChatModelApi.java` — `ChatModelApi` impl; dispatches by api-family to a strategy.
- `OpenAiChatModelApiFactory.java` — `ChatModelApiFactory`; `ORDER=100`; `supports()` OpenAI direct+compatible.
- `OpenAiClientFactory.java` / `OpenAiOkHttpClientFactory.java` — build the `OpenAIClient`.
- `OpenAiContentConverter.java` — neutral `Content` ↔ OpenAI content parts (shared across families).
- `OpenAiModelCapabilities.java` / `OpenAiModelCapabilitiesData.java` / `OpenAiProviderCapabilities.java` / `OpenAiReasoningCapabilities.java` — capability projection.
- `OpenAiRequestValidator.java` — fail-fast effort + server-tool/family validation.
- `family/OpenAiApiFamilyStrategy.java` — strategy interface.
- `family/responses/OpenAiResponsesRequestConverter.java` / `OpenAiResponsesResponseConverter.java` / `OpenAiResponsesStreamAssembler.java` / `OpenAiResponsesStrategy.java`.
- `family/completions/OpenAiCompletionsRequestConverter.java` / `OpenAiCompletionsResponseConverter.java` / `OpenAiCompletionsStreamAssembler.java` / `OpenAiCompletionsStrategy.java`.
- `configuration/AgenticAiOpenAiFrameworkConfiguration.java` — Spring `@Bean`.
- `package-info.java` (×2).

**New (main module, `…/agenticai/aiagent/framework/openai/` — enum on config side is under `model/request/chatmodel`):**
- `model/request/chatmodel/OpenAiEffort.java` — reasoning effort enum.

**Modified (main module):**
- `model/request/chatmodel/OpenAiChatModel.java` — add `effort` to `OpenAiModelParameters`; add `enableWebSearch`/`enableCodeInterpreter` to `OpenAiConnection`.
- `src/main/resources/capabilities/model-capabilities.yaml` — add `provider.reasoning` to `openai-responses` models.
- `autoconfigure/AgenticAiConnectorsAutoConfiguration.java` — `@Import` the new Spring config.
- `pom.xml` — add `com.openai:openai-java` + okhttp + `<ignoredDependency>` entries.
- `element-templates/agenticai-ai-agent-task.v2.json` (+ generated subprocess variant) — regenerated.

**New/modified (e2e-test module, `connectors-e2e-test/connectors-e2e-test-agentic-ai/`, Apache header):**
- `src/test/java/.../e2e/NativeProviderAcceptanceIT.java` — add OpenAI rows + 2 scenarios.
- `src/test/java/.../assertj/JobWorkerAgentResponseAssert.java` — add `hasProviderContentBlockOfType`.

---

## Task 1: OpenAI SDK dependency + surface-pinning test

Adds the SDK and **pins the exact vendor method names** every later task depends on. The SDK method names below are best-effort; the deliverable of this task is a **compiling** test that fixes them to the real SDK, with the confirmed names recorded in a comment block later tasks quote.

**Files:**
- Modify: `connectors/agentic-ai/connector-agentic-ai/pom.xml` (property ~line 22; `<dependencies>` after the anthropic block ~line 142; `<ignoredDependency>` list ~line 446)
- Test: `…/agenticai/aiagent/framework/openai/OpenAiSdkSurfaceTest.java`

**Interfaces:**
- Produces: a documented `// SDK-SURFACE` comment block in `OpenAiSdkSurfaceTest` listing the confirmed fully-qualified types + builder methods for: `OpenAIClient` construction, `client.responses().createStreaming(...)`, `ResponseAccumulator`, `client.chat().completions().createStreaming(...)`, `ChatCompletionAccumulator`, reasoning (`Reasoning`/`ReasoningEffort`), `include`/`store`, web-search tool, code-interpreter tool, function tool, and structured-output (`text.format` / `responseFormat`). Every later task references this block.

- [ ] **Step 1: Add the Maven dependency**

In `pom.xml`, add the version property next to `version.anthropic-java`:
```xml
    <version.openai-java>4.6.0</version.openai-java>
```
(Verify the latest 4.x on Maven Central during this step; pin the exact resolved version.)

Add after the anthropic dependency block (and its `okhttp-jvm` companion):
```xml
    <dependency>
      <groupId>com.openai</groupId>
      <artifactId>openai-java</artifactId>
      <version>${version.openai-java}</version>
    </dependency>
```

Add to the `maven-dependency-plugin` `<ignoredDependencies>`/`<ignoredUnusedDeclaredDependencies>` list (mirror the three anthropic entries):
```xml
                <ignoredDependency>com.openai:openai-java</ignoredDependency>
                <ignoredDependency>com.openai:openai-java-core</ignoredDependency>
                <ignoredDependency>com.openai:openai-java-client-okhttp</ignoredDependency>
```

- [ ] **Step 2: Write the surface-pinning test (best-effort method names)**

```java
// Apache/proprietary header per module (this is main module → proprietary)
package io.camunda.connector.agenticai.aiagent.framework.openai;

import static org.assertj.core.api.Assertions.assertThat;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
// ... vendor imports filled in to match the real SDK during this task ...
import org.junit.jupiter.api.Test;

/**
 * Pins the OpenAI SDK surface every later task relies on. No network calls — only builds
 * params/clients so the compiler validates the method names.
 *
 * SDK-SURFACE (fill in with the CONFIRMED names once this compiles):
 *   client build:            OpenAIOkHttpClient.builder().apiKey(..).organization(..).project(..).baseUrl(..).build()
 *   responses stream:        client.responses().createStreaming(ResponseCreateParams) -> StreamResponse<ResponseStreamEvent>
 *   responses accumulate:    ResponseAccumulator (accumulate(event) / response())
 *   completions stream:      client.chat().completions().createStreaming(ChatCompletionCreateParams) -> StreamResponse<ChatCompletionChunk>
 *   completions accumulate:  ChatCompletionAccumulator.create() (accumulate(chunk) / chatCompletion())
 *   reasoning:               Reasoning.builder().effort(ReasoningEffort.HIGH).build(); params.reasoning(..)
 *   include encrypted:       params.addInclude(ResponseIncludable.REASONING_ENCRYPTED_CONTENT); params.store(false)
 *   web_search tool:         <confirm Tool factory name>
 *   code_interpreter tool:   <confirm Tool factory + container(auto) name>
 *   function tool:           <confirm function tool builder + JSON-schema params>
 *   structured output:       responses: params.text(<format json_schema>); completions: params.responseFormat(<json_schema>)
 *   additional properties:   <confirm _additionalProperties() getter + putAdditionalProperty(..) on builders>
 */
class OpenAiSdkSurfaceTest {

  @Test
  void buildsClient() {
    OpenAIClient client =
        OpenAIOkHttpClient.builder().apiKey("test-key").build();
    assertThat(client).isNotNull();
  }

  @Test
  void buildsResponsesParamsWithReasoningAndTools() {
    // Build a ResponseCreateParams that sets: model, input, instructions,
    // reasoning(effort=HIGH), include(reasoning.encrypted_content), store(false),
    // a function tool, a web_search tool, a code_interpreter tool, and text json_schema.
    // Assert it builds (compiler validates the method names).
  }

  @Test
  void buildsCompletionsParamsWithToolAndResponseFormat() {
    // Build a ChatCompletionCreateParams with a function tool + responseFormat(json_schema). Assert it builds.
  }

  @Test
  void accumulatorsExist() {
    // Instantiate ResponseAccumulator + ChatCompletionAccumulator.create(); assert non-null.
  }
}
```

- [ ] **Step 3: Compile-run and fix method names to the real SDK**

Run:
```bash
cd connectors/agentic-ai/connector-agentic-ai && \
  mvn -q -o test -Dtest=OpenAiSdkSurfaceTest -Dsurefire.failIfNoSpecifiedTests=false
```
Expected: initially FAILS to compile on unknown methods. Fix each import/method to match the resolved `openai-java` version until it compiles and the 4 tests pass. **Update the `SDK-SURFACE` comment block with the confirmed names.** (Drop `-o` if the dependency isn't cached yet.)

- [ ] **Step 4: Commit**
```bash
git add connectors/agentic-ai/connector-agentic-ai/pom.xml \
        connectors/agentic-ai/connector-agentic-ai/src/test/java/io/camunda/connector/agenticai/aiagent/framework/openai/OpenAiSdkSurfaceTest.java
git commit -m "feat(agentic-ai): add openai-java SDK dependency and pin its API surface"
```

---

## Task 2: Config-record additions (`effort`, server-tool toggles) + `OpenAiEffort` enum

**Files:**
- Create: `…/agenticai/aiagent/model/request/chatmodel/OpenAiEffort.java`
- Modify: `…/agenticai/aiagent/model/request/chatmodel/OpenAiChatModel.java` (`OpenAiModelParameters` record; `OpenAiConnection` record)
- Test: `…/agenticai/aiagent/model/request/chatmodel/OpenAiChatModelTest.java` (create)

**Interfaces:**
- Produces: `OpenAiEffort { MINIMAL, LOW, MEDIUM, HIGH }` (lowercase `@JsonProperty`); `OpenAiModelParameters.effort()` → `@Nullable OpenAiEffort`; `OpenAiConnection.enableWebSearch()` / `.enableCodeInterpreter()` → `@Nullable Boolean`.

- [ ] **Step 1: Write the failing test**
```java
// proprietary header
package io.camunda.connector.agenticai.aiagent.model.request.chatmodel;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class OpenAiChatModelTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void deserializesEffortAndServerToolToggles() throws Exception {
    var json = """
        {
          "type": "openai",
          "openai": {
            "apiFamily": "responses",
            "backend": { "type": "direct", "apiKey": "k" },
            "model": { "model": "gpt-5", "parameters": { "effort": "high" } },
            "enableWebSearch": true,
            "enableCodeInterpreter": true
          }
        }
        """;
    var model = (OpenAiChatModel) mapper.readValue(json, LlmProviderConfiguration.class);
    assertThat(model.openai().model().parameters().effort()).isEqualTo(OpenAiEffort.HIGH);
    assertThat(model.openai().enableWebSearch()).isTrue();
    assertThat(model.openai().enableCodeInterpreter()).isTrue();
  }

  @Test
  void effortJsonValueIsLowercase() throws Exception {
    assertThat(mapper.writeValueAsString(OpenAiEffort.MINIMAL)).isEqualTo("\"minimal\"");
  }
}
```

- [ ] **Step 2: Run it to verify it fails**
```bash
cd connectors/agentic-ai/connector-agentic-ai && \
  mvn -q -o test -Dtest=OpenAiChatModelTest -Dsurefire.failIfNoSpecifiedTests=false
```
Expected: compile failure (`effort()`, `enableWebSearch()` don't exist yet).

- [ ] **Step 3: Create `OpenAiEffort`** (mirror `framework/anthropic/AnthropicEffort.java` shape, values `MINIMAL/LOW/MEDIUM/HIGH`):
```java
// proprietary header
package io.camunda.connector.agenticai.aiagent.model.request.chatmodel;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum OpenAiEffort {
  @JsonProperty("minimal") MINIMAL,
  @JsonProperty("low") LOW,
  @JsonProperty("medium") MEDIUM,
  @JsonProperty("high") HIGH
}
```

- [ ] **Step 4: Add `effort` to `OpenAiModelParameters`** — mirror the Anthropic effort `@TemplateProperty` (group `model`, Dropdown, `optional`), choices minimal/low/medium/high, condition `configuration.openai.apiFamily == responses`:
```java
    public record OpenAiModelParameters(
        @Min(0) @Nullable Integer maxCompletionTokens,
        @Min(0) @Nullable Double temperature,
        @Min(0) @Nullable Double topP,
        @TemplateProperty(
                group = "model",
                label = "Reasoning effort",
                tooltip =
                    "Reasoning effort for reasoning-capable models (Responses API only). "
                        + "Unset ⇒ model default.",
                type = TemplateProperty.PropertyType.Dropdown,
                choices = {
                  @DropdownPropertyChoice(value = "minimal", label = "minimal"),
                  @DropdownPropertyChoice(value = "low", label = "low"),
                  @DropdownPropertyChoice(value = "medium", label = "medium"),
                  @DropdownPropertyChoice(value = "high", label = "high")
                },
                optional = true,
                condition =
                    @TemplateProperty.PropertyCondition(
                        property = "configuration.openai.apiFamily", equals = "responses"))
            @Nullable OpenAiEffort effort) {}
```
Add imports: `TemplateProperty.DropdownPropertyChoice`.

- [ ] **Step 5: Add server-tool toggles to `OpenAiConnection`** — Boolean checkboxes, group `provider`, condition `apiFamily == responses`:
```java
  public record OpenAiConnection(
      @NotNull OpenAiApiFamily apiFamily,
      @Valid @NotNull OpenAiBackend backend,
      @Valid @NotNull OpenAiModel model,
      @TemplateProperty(
              group = "provider",
              label = "Enable web search",
              tooltip = "Enable the OpenAI web_search server tool (Responses API only).",
              type = TemplateProperty.PropertyType.Boolean,
              optional = true,
              condition =
                  @TemplateProperty.PropertyCondition(
                      property = "configuration.openai.apiFamily", equals = "responses"))
          @Nullable Boolean enableWebSearch,
      @TemplateProperty(
              group = "provider",
              label = "Enable code interpreter",
              tooltip = "Enable the OpenAI code_interpreter server tool (Responses API only).",
              type = TemplateProperty.PropertyType.Boolean,
              optional = true,
              condition =
                  @TemplateProperty.PropertyCondition(
                      property = "configuration.openai.apiFamily", equals = "responses"))
          @Nullable Boolean enableCodeInterpreter,
      @Valid @Nullable TimeoutConfiguration timeouts,
      @Valid @Nullable ModelCapabilitiesOverride capabilityOverride) {}
```
(If the `@TemplateProperty` enum lacks `Boolean`, use the Checkbox type used elsewhere in this file — grep `PropertyType.` in the module for the exact constant.)

- [ ] **Step 6: Run the test to verify it passes**
```bash
mvn -q -o test -Dtest=OpenAiChatModelTest -Dsurefire.failIfNoSpecifiedTests=false
```
Expected: PASS (2/2).

- [ ] **Step 7: Commit**
```bash
git add connectors/agentic-ai/connector-agentic-ai/src/main/java/io/camunda/connector/agenticai/aiagent/model/request/chatmodel/OpenAiEffort.java \
        connectors/agentic-ai/connector-agentic-ai/src/main/java/io/camunda/connector/agenticai/aiagent/model/request/chatmodel/OpenAiChatModel.java \
        connectors/agentic-ai/connector-agentic-ai/src/test/java/io/camunda/connector/agenticai/aiagent/model/request/chatmodel/OpenAiChatModelTest.java
git commit -m "feat(agentic-ai): add reasoning effort and server-tool toggles to OpenAI config"
```

---

## Task 3: OpenAI capability-projection types

Mirror `framework/anthropic/AnthropicModelCapabilities*`, dropping the `thinking-modes` axis (OpenAI has only `effort-levels`).

**Files:**
- Create: `…/agenticai/aiagent/framework/openai/OpenAiReasoningCapabilities.java`, `OpenAiProviderCapabilities.java`, `OpenAiModelCapabilities.java`, `OpenAiModelCapabilitiesData.java`
- Test: `…/agenticai/aiagent/framework/openai/OpenAiModelCapabilitiesDataTest.java`

**Interfaces:**
- Consumes: `CoreModelCapabilities`, `ModelCapabilities`, `ModelCapabilities.Modality`, `ModelCapabilitiesData<T>` (from `framework/capabilities/`); `OpenAiEffort` (Task 2).
- Produces: `OpenAiModelCapabilities(CoreModelCapabilities core, @Nullable OpenAiReasoningCapabilities reasoning)` with `supportsReasoning()`; `OpenAiModelCapabilitiesData implements ModelCapabilitiesData<OpenAiModelCapabilities>`; `OpenAiReasoningCapabilities(List<OpenAiEffort> effortLevels)`.

- [ ] **Step 1: Write the failing test**
```java
// proprietary header
package io.camunda.connector.agenticai.aiagent.framework.openai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.OpenAiEffort;
import org.junit.jupiter.api.Test;

class OpenAiModelCapabilitiesDataTest {

  private final ObjectMapper mapper = new ObjectMapper()
      .findAndRegisterModules(); // snake_case handled by @JsonNaming on the record

  @Test
  void projectsReasoningEffortLevels() throws Exception {
    var yaml = """
        { "context_window": 128000, "max_output_tokens": 16384,
          "provider": { "reasoning": { "effort-levels": ["low","medium","high"] } } }
        """;
    var data = mapper.readValue(yaml, OpenAiModelCapabilitiesData.class);
    var caps = data.toModelCapabilities();
    assertThat(caps.supportsReasoning()).isTrue();
    assertThat(caps.reasoning().effortLevels())
        .containsExactly(OpenAiEffort.LOW, OpenAiEffort.MEDIUM, OpenAiEffort.HIGH);
  }

  @Test
  void noReasoningWhenProviderAbsent() throws Exception {
    var data = mapper.readValue("{ \"context_window\": 128000 }", OpenAiModelCapabilitiesData.class);
    assertThat(data.toModelCapabilities().supportsReasoning()).isFalse();
  }
}
```

- [ ] **Step 2: Run to verify it fails** — `mvn -q -o test -Dtest=OpenAiModelCapabilitiesDataTest -Dsurefire.failIfNoSpecifiedTests=false` → compile failure.

- [ ] **Step 3: Create the four classes** (mirror the Anthropic siblings verbatim, minus `thinking-modes`):
```java
// OpenAiReasoningCapabilities.java
public record OpenAiReasoningCapabilities(
    @JsonProperty("effort-levels") List<OpenAiEffort> effortLevels) {
  public OpenAiReasoningCapabilities {
    effortLevels = effortLevels == null ? List.of() : List.copyOf(effortLevels);
  }
}
```
```java
// OpenAiProviderCapabilities.java
public record OpenAiProviderCapabilities(@Nullable OpenAiReasoningCapabilities reasoning) {}
```
```java
// OpenAiModelCapabilities.java  (implements ModelCapabilities exactly like the Anthropic sibling)
public record OpenAiModelCapabilities(
    CoreModelCapabilities core, @Nullable OpenAiReasoningCapabilities reasoning)
    implements ModelCapabilities {
  public boolean supportsReasoning() { return reasoning != null; }
  @Override public List<Modality> userMessageModalities() { return core.userMessageModalities(); }
  @Override public List<Modality> toolResultModalities() { return core.toolResultModalities(); }
  @Override public List<Modality> assistantMessageModalities() { return core.assistantMessageModalities(); }
}
```
```java
// OpenAiModelCapabilitiesData.java  (mirror AnthropicModelCapabilitiesData: @JsonNaming SnakeCase,
// @JsonInclude ALWAYS, InputModalities/OutputModalities nested records, toModelCapabilities()
// building CoreModelCapabilities the same way, and provider() -> OpenAiProviderCapabilities)
public record OpenAiModelCapabilitiesData(
    @Nullable InputModalities inputModalities,
    @Nullable OutputModalities outputModalities,
    @Nullable Integer contextWindow,
    @Nullable Integer maxOutputTokens,
    @Nullable OpenAiProviderCapabilities provider)
    implements ModelCapabilitiesData<OpenAiModelCapabilities> {

  public record InputModalities(@Nullable List<Modality> userMessage, @Nullable List<Modality> toolResult) {}
  public record OutputModalities(@Nullable List<Modality> assistantMessage) {}

  @Override
  public OpenAiModelCapabilities toModelCapabilities() {
    // identical to AnthropicModelCapabilitiesData.toModelCapabilities(), returning
    // new OpenAiModelCapabilities(core, provider == null ? null : provider.reasoning());
  }
}
```

- [ ] **Step 4: Run to verify it passes** — same command → PASS (2/2).

- [ ] **Step 5: Commit**
```bash
git add connectors/agentic-ai/connector-agentic-ai/src/main/java/io/camunda/connector/agenticai/aiagent/framework/openai/OpenAiReasoningCapabilities.java \
        .../OpenAiProviderCapabilities.java .../OpenAiModelCapabilities.java .../OpenAiModelCapabilitiesData.java \
        connectors/agentic-ai/connector-agentic-ai/src/test/java/io/camunda/connector/agenticai/aiagent/framework/openai/OpenAiModelCapabilitiesDataTest.java
git commit -m "feat(agentic-ai): add OpenAI model-capability projection types"
```

---

## Task 4: Capability-matrix reasoning entries (`openai-responses`)

**Files:**
- Modify: `connectors/agentic-ai/connector-agentic-ai/src/main/resources/capabilities/model-capabilities.yaml` (the `openai-responses` `models:` block)
- Test: `…/agenticai/aiagent/framework/openai/OpenAiCapabilityResolutionTest.java` (create)

**Interfaces:**
- Consumes: `ModelCapabilitiesResolver` (bean from `AgenticAiCapabilitiesConfiguration`), `OpenAiModelCapabilitiesData` (Task 3), `OpenAiApiFamily.RESPONSES.familyKey()` = `"openai-responses"`, `COMPLETIONS.familyKey()` = `"openai-completions"`.

- [ ] **Step 1: Write the failing test** (a lightweight resolver test — load the yaml via the same mechanism `AgenticAiCapabilitiesConfiguration` uses; if that needs Spring, use `@SpringBootTest(classes = AgenticAiCapabilitiesConfiguration.class)` and inject `ModelCapabilitiesResolver`):
```java
// proprietary header
package io.camunda.connector.agenticai.aiagent.framework.openai;

import static org.assertj.core.api.Assertions.assertThat;
// ... Spring test wiring mirroring the existing Anthropic capability resolution test ...

class OpenAiCapabilityResolutionTest {
  // inject ModelCapabilitiesResolver resolver;

  @Test
  void gpt5OnResponsesDeclaresReasoning() {
    var caps = resolver.resolve("openai-responses", "gpt-5", "direct",
        java.util.Optional.empty(), OpenAiModelCapabilitiesData.class);
    assertThat(caps.supportsReasoning()).isTrue();
    assertThat(caps.reasoning().effortLevels()).isNotEmpty();
  }

  @Test
  void gpt5OnCompletionsHasNoReasoning() {
    var caps = resolver.resolve("openai-completions", "gpt-5", "direct",
        java.util.Optional.empty(), OpenAiModelCapabilitiesData.class);
    assertThat(caps.supportsReasoning()).isFalse();
  }
}
```
(Find the existing Anthropic resolver test — likely `ModelCapabilitiesResolverImplTest` or an `AnthropicModelCapabilities*Test` — and copy its exact wiring for loading the yaml.)

- [ ] **Step 2: Run to verify it fails** → `gpt5OnResponsesDeclaresReasoning` fails (reasoning null).

- [ ] **Step 3: Add `provider.reasoning` to `openai-responses` gpt-5 (and o-series)** in the yaml, mirroring the `claude-sonnet-5` shape but effort-levels only:
```yaml
                gpt-5:
                  pattern: gpt-5*
                  capabilities:
                    context-window: 128000
                    max-output-tokens: 16384
                    provider:
                      reasoning:
                        effort-levels: [minimal, low, medium, high]
```
Do the same for `o1`/`o3`/`o4` under `openai-responses` (effort-levels `[low, medium, high]` — o-series predates `minimal`). Leave **`openai-completions` untouched** (no reasoning — enforces the deferral).

- [ ] **Step 4: Run to verify it passes** → PASS (2/2).

- [ ] **Step 5: Commit**
```bash
git add connectors/agentic-ai/connector-agentic-ai/src/main/resources/capabilities/model-capabilities.yaml \
        connectors/agentic-ai/connector-agentic-ai/src/test/java/io/camunda/connector/agenticai/aiagent/framework/openai/OpenAiCapabilityResolutionTest.java
git commit -m "feat(agentic-ai): declare OpenAI Responses reasoning effort-levels in capability matrix"
```

---

## Task 5: Fail-fast request validator

Mirror `AnthropicReasoningValidator`, replacing thinking-mode rules with effort-only rules, and add the server-tools-require-Responses check.

**Files:**
- Create: `…/agenticai/aiagent/framework/openai/OpenAiRequestValidator.java`
- Test: `…/agenticai/aiagent/framework/openai/OpenAiRequestValidatorTest.java`

**Interfaces:**
- Consumes: `OpenAiChatModel.OpenAiConnection`, `OpenAiModelParameters` (`effort()`), `OpenAiReasoningCapabilities`, `OpenAiApiFamily`, `AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL`, `ConnectorException`.
- Produces: `static void OpenAiRequestValidator.validate(OpenAiConnection connection, @Nullable OpenAiReasoningCapabilities reasoning, boolean modelMatched, String modelId)`.

- [ ] **Step 1: Write the failing test** (one test per rule):
```java
// proprietary header
package io.camunda.connector.agenticai.aiagent.framework.openai;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
// imports for OpenAiChatModel.* records, OpenAiEffort, OpenAiApiFamily, OpenAiReasoningCapabilities
import org.junit.jupiter.api.Test;

class OpenAiRequestValidatorTest {

  // helper to build an OpenAiConnection(apiFamily, effort, enableWebSearch, enableCodeInterpreter)

  @Test
  void passesThroughUnmatchedModel() {
    assertThatCode(() -> OpenAiRequestValidator.validate(
        conn(OpenAiApiFamily.RESPONSES, OpenAiEffort.HIGH, false, false), null, false, "unknown"))
        .doesNotThrowAnyException();
  }

  @Test
  void failsEffortWithoutReasoningCapability() {
    assertThatThrownBy(() -> OpenAiRequestValidator.validate(
        conn(OpenAiApiFamily.RESPONSES, OpenAiEffort.HIGH, false, false), null, true, "gpt-4o"))
        .isInstanceOf(io.camunda.connector.api.error.ConnectorException.class);
  }

  @Test
  void failsEffortNotInEffortLevels() {
    var reasoning = new OpenAiReasoningCapabilities(java.util.List.of(OpenAiEffort.LOW));
    assertThatThrownBy(() -> OpenAiRequestValidator.validate(
        conn(OpenAiApiFamily.RESPONSES, OpenAiEffort.HIGH, false, false), reasoning, true, "gpt-5"))
        .isInstanceOf(io.camunda.connector.api.error.ConnectorException.class);
  }

  @Test
  void passesEffortInEffortLevels() {
    var reasoning = new OpenAiReasoningCapabilities(java.util.List.of(OpenAiEffort.HIGH));
    assertThatCode(() -> OpenAiRequestValidator.validate(
        conn(OpenAiApiFamily.RESPONSES, OpenAiEffort.HIGH, false, false), reasoning, true, "gpt-5"))
        .doesNotThrowAnyException();
  }

  @Test
  void failsServerToolOnCompletions() {
    assertThatThrownBy(() -> OpenAiRequestValidator.validate(
        conn(OpenAiApiFamily.COMPLETIONS, null, true, false), null, true, "gpt-4o"))
        .isInstanceOf(io.camunda.connector.api.error.ConnectorException.class)
        .hasMessageContaining("Responses API");
  }
}
```

- [ ] **Step 2: Run to verify it fails** → compile failure.

- [ ] **Step 3: Implement** (package-private final class, private ctor, mirroring the Anthropic validator control flow):
```java
final class OpenAiRequestValidator {
  private OpenAiRequestValidator() {}

  static void validate(
      OpenAiChatModel.OpenAiConnection connection,
      @Nullable OpenAiReasoningCapabilities reasoning,
      boolean modelMatched,
      String modelId) {

    final boolean serverToolsRequested =
        Boolean.TRUE.equals(connection.enableWebSearch())
            || Boolean.TRUE.equals(connection.enableCodeInterpreter());
    if (serverToolsRequested && connection.apiFamily() != OpenAiApiFamily.RESPONSES) {
      throw new ConnectorException(
          ERROR_CODE_FAILED_MODEL_CALL, "Server tools require the Responses API.");
    }

    final var params = connection.model().parameters();
    final var effort = params == null ? null : params.effort();
    if (effort == null) return;
    if (!modelMatched) return; // unknown/custom models unchecked

    if (reasoning == null) {
      throw new ConnectorException(
          ERROR_CODE_FAILED_MODEL_CALL,
          "Model '" + modelId + "' does not support reasoning effort.");
    }
    if (!reasoning.effortLevels().contains(effort)) {
      throw new ConnectorException(
          ERROR_CODE_FAILED_MODEL_CALL,
          "Model '" + modelId + "' does not support effort '" + effort
              + "'; supported: " + reasoning.effortLevels());
    }
  }
}
```

- [ ] **Step 4: Run to verify it passes** → PASS (5/5).
- [ ] **Step 5: Commit**
```bash
git add .../framework/openai/OpenAiRequestValidator.java .../framework/openai/OpenAiRequestValidatorTest.java
git commit -m "feat(agentic-ai): add fail-fast validator for OpenAI reasoning and server tools"
```

---

## Task 6: OpenAI client factory (direct + compatible)

Mirror `AnthropicClientFactory` / `AnthropicOkHttpClientFactory`, supporting both backends.

**Files:**
- Create: `…/agenticai/aiagent/framework/openai/OpenAiClientFactory.java`, `OpenAiOkHttpClientFactory.java`
- Test: `…/agenticai/aiagent/framework/openai/OpenAiOkHttpClientFactoryTest.java`

**Interfaces:**
- Consumes: `OpenAiChatModel.OpenAiBackend` (`OpenAiDirectBackend`, `OpenAiCompatibleBackend`, `CompatibleAuthentication`), `HttpTransportSupport`, SDK `OpenAIOkHttpClient` (SDK-SURFACE from Task 1).
- Produces: `interface OpenAiClientFactory { OpenAIClient create(); }`; `OpenAiOkHttpClientFactory(OpenAiBackend backend, @Nullable Duration timeout, HttpTransportSupport transport)`.

- [ ] **Step 1: Write the failing test** (builds a client for each backend, no network):
```java
// proprietary header
package io.camunda.connector.agenticai.aiagent.framework.openai;

import static org.assertj.core.api.Assertions.assertThat;
// imports: OpenAiChatModel.OpenAiBackend.*, CompatibleAuthentication.*, HttpTransportSupport, Duration, Mockito
import org.junit.jupiter.api.Test;

class OpenAiOkHttpClientFactoryTest {

  private final HttpTransportSupport transport = org.mockito.Mockito.mock(HttpTransportSupport.class);

  @Test
  void buildsDirectClient() {
    var backend = new OpenAiChatModel.OpenAiBackend.OpenAiDirectBackend("k", "org", "proj");
    var client = new OpenAiOkHttpClientFactory(backend, Duration.ofSeconds(30), transport).create();
    assertThat(client).isNotNull();
  }

  @Test
  void buildsCompatibleClientWithEndpoint() {
    var backend = new OpenAiChatModel.OpenAiBackend.OpenAiCompatibleBackend(
        "https://example.test/v1", null, null, null,
        new OpenAiChatModel.CompatibleAuthentication.CompatibleApiKeyAuthentication("k"));
    var client = new OpenAiOkHttpClientFactory(backend, null, transport).create();
    assertThat(client).isNotNull();
  }
}
```

- [ ] **Step 2: Run to verify it fails** → compile failure.

- [ ] **Step 3: Implement.** `OpenAiClientFactory` = single-method interface. `OpenAiOkHttpClientFactory.create()` switches on backend type: direct → `OpenAIOkHttpClient.builder().apiKey(direct.apiKey())` + optional `.organization(...)`/`.project(...)`; compatible → `.baseUrl(endpoint)` + apiKey from `CompatibleApiKeyAuthentication` (or none) + headers/queryParameters if the SDK builder supports them (else document as a known compatible-mode gap). Resolve proxy via `transport.okHttpProxy(scheme)` and apply `.timeout(...)` when non-null — mirror `AnthropicOkHttpClientFactory` exactly. Use the confirmed SDK method names from Task 1.

- [ ] **Step 4: Run to verify it passes** → PASS (2/2).
- [ ] **Step 5: Commit**
```bash
git add .../framework/openai/OpenAiClientFactory.java .../framework/openai/OpenAiOkHttpClientFactory.java .../framework/openai/OpenAiOkHttpClientFactoryTest.java
git commit -m "feat(agentic-ai): add OpenAI client factory for direct and compatible backends"
```

---

## Task 7: Shared content converter

Neutral `Content` ↔ OpenAI content parts (text, image, document). Mirror `AnthropicContentConverter` structure; the two families call it for message bodies and tool-result bodies.

**Files:**
- Create: `…/agenticai/aiagent/framework/openai/OpenAiContentConverter.java`
- Test: `…/agenticai/aiagent/framework/openai/OpenAiContentConverterTest.java`

**Interfaces:**
- Consumes: `Content` subtypes (`TextContent`, `DocumentContent`, `ObjectContent`), `DocumentModality` (`framework/multimodal/`), `ObjectMapper`, SDK content-part types.
- Produces: methods to map a `List<Content>` to the SDK's input content parts for **each family** (Responses input items vs. Completions content parts). Exact SDK types per Task 1. Provide `toResponsesContentParts(List<Content>)` and `toCompletionsContentParts(List<Content>)` (naming per the SDK unions confirmed in Task 1).

- [ ] **Step 1: Write the failing test** — assert a `TextContent` maps to a text part and a `DocumentContent` (image mime) maps to an image part, for both families. Use a small in-memory `Document` test double (grep the module for an existing document test helper, e.g. `CamundaDocumentTestConfiguration` or `InMemoryDocumentStore`).
- [ ] **Step 2: Run to verify it fails** → compile failure.
- [ ] **Step 3: Implement**, delegating document mime→modality decisions to `DocumentModality` exactly as `AnthropicContentConverter` does. `ObjectContent` → serialize to text via `objectMapper.writeValueAsString(...)`. Use confirmed SDK part builders.
- [ ] **Step 4: Run to verify it passes.**
- [ ] **Step 5: Commit**
```bash
git commit -m "feat(agentic-ai): add OpenAI content converter for text and document parts"
```

---

## Task 8: Responses request converter

**Files:**
- Create: `…/agenticai/aiagent/framework/openai/family/responses/OpenAiResponsesRequestConverter.java`
- Test: `…/…/family/responses/OpenAiResponsesRequestConverterTest.java`

**Interfaces:**
- Consumes: `AgentExecutionContext` (`configuration()` → `AgentConfiguration.systemPrompt()`, `.response()`), `ConversationSnapshot` (`messages()`, `toolDefinitions()`), `OpenAiChatModel` (cast from `configuration().chatModelApiConfiguration()`), `OpenAiModelCapabilities`, `OpenAiContentConverter` (Task 7), `OpenAiRequestValidator` (Task 5), `ResponseFormatConfiguration.JsonResponseFormatConfiguration` (`schema()`, `schemaName()`), `ReasoningContent.providerPayload()`, `ProviderContent.payload()`.
- Produces: `ResponseCreateParams toResponseCreateParams(AgentExecutionContext ctx, ConversationSnapshot snapshot, OpenAiModelCapabilities capabilities, boolean modelMatched)`.

- [ ] **Step 1: Write failing tests** — one test each:
  1. `SystemMessage` → `instructions`.
  2. `UserMessage` text → input item.
  3. `AssistantMessage.toolCalls()` → `function_call` item; `ToolCallResultMessage` → `function_call_output` item.
  4. `snapshot.toolDefinitions()` → function tool defs (JSON schema).
  5. `JsonResponseFormatConfiguration` → `text.format` json_schema (strict).
  6. effort set → `reasoning(effort)` + include `reasoning.encrypted_content` + `store(false)`; and `OpenAiRequestValidator` is invoked (unsupported effort throws).
  7. `AssistantMessage` containing `ReasoningContent` with non-null `providerPayload` → replayed as an input item (via `objectMapper.convertValue(payload, <ResponsesInputItem>.class)`); null payload skipped.
  8. `enableWebSearch`/`enableCodeInterpreter` → web_search / code_interpreter tool defs added.

  Each test builds a minimal `AgentExecutionContext`/`ConversationSnapshot` (mock `AgentExecutionContext`; construct a real `ConversationSnapshot(List.of(...messages...), List.of(...toolDefs...))`), calls the converter, and asserts on the built `ResponseCreateParams` (inspect via SDK getters or serialize with the SDK's mapper and assert on JSON).

- [ ] **Step 2: Run to verify they fail** → compile failure.
- [ ] **Step 3: Implement** the orchestration (mirror `AnthropicMessageRequestConverter.toMessageCreateParams`): validate → model/params → reasoning (effort+include+store) → instructions → messages (delegate bodies to `OpenAiContentConverter`) → tools → structured output → server tools → reasoning replay. The cast-back to `OpenAiChatModel`: `((LlmProviderChatModelApiConfiguration) ctx.configuration().chatModelApiConfiguration()).configuration()`.
- [ ] **Step 4: Run to verify they pass.**
- [ ] **Step 5: Commit**
```bash
git commit -m "feat(agentic-ai): add OpenAI Responses request converter"
```

---

## Task 9: Responses response converter + stream assembler

**Files:**
- Create: `…/…/family/responses/OpenAiResponsesResponseConverter.java`, `OpenAiResponsesStreamAssembler.java`
- Test: `…/…/family/responses/OpenAiResponsesResponseConverterTest.java`, `OpenAiResponsesStreamAssemblerTest.java`

**Interfaces:**
- Consumes: SDK `Response` + `ResponseAccumulator` (Task 1), `AgentMetrics`, `AgentMetrics.TokenUsage`, `AssistantMessage.builder()`, `ToolCall.builder()`, content factories `TextContent.textContent`, `new ReasoningContent(text, providerPayload, null)`, `ProviderContent.providerContent(provider, blockType, payload)`.
- Produces: `ChatModelResult toResult(Response response, Duration executionTime)`; `@FunctionalInterface OpenAiResponsesStreamAssembler { Response assemble(StreamResponse<ResponseStreamEvent> stream); }` with a default `accumulating()` backed by `ResponseAccumulator`.

- [ ] **Step 1: Write failing tests:**
  1. `output_text` item → `TextContent`.
  2. `function_call` item → `ToolCall` (id/name/arguments as `Map`).
  3. reasoning item → `ReasoningContent` with `providerPayload` = the raw item as a `Map` (round-trip-safe; use `objectMapper.convertValue(item, Map.class)`), `text` = summary if present.
  4. `web_search_call` / `code_interpreter_call` items → `ProviderContent` with `provider="openai"`, `blockType` = the item's type string, `payload` = the raw item as a `Map`.
  5. usage → `AgentMetrics.TokenUsage` (input/output/cached/reasoning); result is `Completed`.
  6. Assembler: feeding a small list of `ResponseStreamEvent`s yields the assembled `Response` (mock the `StreamResponse` to return a `Stream` of events).
- [ ] **Step 2: Run to verify they fail.**
- [ ] **Step 3: Implement**, mirroring `AnthropicMessageResponseConverter.toResult`. Map usage fields to `TokenUsage.builder()...build()` (cached → `cacheReadTokenCount`; reasoning → `reasoningTokenCount`). Responses has no `pause_turn` → always `Completed`.
- [ ] **Step 4: Run to verify they pass.**
- [ ] **Step 5: Commit**
```bash
git commit -m "feat(agentic-ai): add OpenAI Responses response converter and stream assembler"
```

---

## Task 10: Completions request converter (subset — no reasoning)

**Files:**
- Create: `…/…/family/completions/OpenAiCompletionsRequestConverter.java`
- Test: `…/…/family/completions/OpenAiCompletionsRequestConverterTest.java`

**Interfaces:**
- Consumes: same as Task 8 minus reasoning; `OpenAiContentConverter.toCompletionsContentParts`.
- Produces: `ChatCompletionCreateParams toChatCompletionCreateParams(AgentExecutionContext ctx, ConversationSnapshot snapshot, OpenAiModelCapabilities capabilities, boolean modelMatched)`.

- [ ] **Step 1: Write failing tests:** system→system/developer message; user text→message; assistant tool_calls; tool result→`tool` message; tool defs→function defs; `JsonResponseFormatConfiguration`→`responseFormat` json_schema; **assert reasoning effort is NOT sent** (even if set) and **`OpenAiRequestValidator` still runs** (server-tool-on-completions throws — but effort on a completions model already fails validation via Task 5 since completions declares no reasoning).
- [ ] **Step 2: Run to verify they fail.**
- [ ] **Step 3: Implement.** Multimodal tool results rely on the capability-aware fallback (matrix says completions tool-result = text-only → `<doc/>` XML). Call `OpenAiRequestValidator.validate(...)` at the top. Do **not** map `ReasoningContent` (deferred).
- [ ] **Step 4: Run to verify they pass.**
- [ ] **Step 5: Commit**
```bash
git commit -m "feat(agentic-ai): add OpenAI Chat Completions request converter"
```

---

## Task 11: Completions response converter + stream assembler

**Files:**
- Create: `…/…/family/completions/OpenAiCompletionsResponseConverter.java`, `OpenAiCompletionsStreamAssembler.java`
- Test: `…/…/family/completions/OpenAiCompletionsResponseConverterTest.java`, `OpenAiCompletionsStreamAssemblerTest.java`

**Interfaces:**
- Consumes: SDK `ChatCompletion` + `ChatCompletionAccumulator` (Task 1).
- Produces: `ChatModelResult toResult(ChatCompletion completion, Duration executionTime)`; `OpenAiCompletionsStreamAssembler { ChatCompletion assemble(StreamResponse<ChatCompletionChunk> stream); }` default `accumulating()` backed by `ChatCompletionAccumulator`.

- [ ] **Step 1: Write failing tests:** content→`TextContent`; `tool_calls`→`ToolCall`; **reasoning text dropped** (no `ReasoningContent` emitted even if `message.thinking()` present); usage→`TokenUsage` (cached via `prompt_tokens_details.cached_tokens`; `reasoningTokenCount == 0`); `Completed`. Assembler test mirrors Task 9.
- [ ] **Step 2: Run to verify they fail.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run to verify they pass.**
- [ ] **Step 5: Commit**
```bash
git commit -m "feat(agentic-ai): add OpenAI Chat Completions response converter and stream assembler"
```

---

## Task 12: Family strategy + `OpenAiChatModelApi`

**Files:**
- Create: `…/agenticai/aiagent/framework/openai/family/OpenAiApiFamilyStrategy.java`, `family/responses/OpenAiResponsesStrategy.java`, `family/completions/OpenAiCompletionsStrategy.java`, `OpenAiChatModelApi.java`
- Test: `…/agenticai/aiagent/framework/openai/OpenAiChatModelApiTest.java`

**Interfaces:**
- Consumes: `OpenAiClientFactory`, the family converters + assemblers (Tasks 8–11), `OpenAiModelCapabilities`, `ChatModelRequest`, `ChatModelResult`.
- Produces:
  - `interface OpenAiApiFamilyStrategy { ChatModelResult call(OpenAIClient client, ChatModelRequest request, OpenAiModelCapabilities capabilities, boolean modelMatched); }`
  - `OpenAiResponsesStrategy(OpenAiResponsesRequestConverter, OpenAiResponsesResponseConverter, OpenAiResponsesStreamAssembler)` and the completions analog.
  - `OpenAiChatModelApi implements ChatModelApi` — constructor `(OpenAiClientFactory, OpenAiApiFamilyStrategy strategy, OpenAiModelCapabilities capabilities, boolean modelMatched)`; `call()` = build client (try/finally close), delegate to the strategy; `capabilities()` returns the `OpenAiModelCapabilities`.

- [ ] **Step 1: Write the failing test** — mock `OpenAiClientFactory` + `OpenAiApiFamilyStrategy`; assert `OpenAiChatModelApi.call(request)` builds a client, delegates to the strategy, returns its `ChatModelResult`, and closes the client. Assert `capabilities()` returns the injected capabilities.
- [ ] **Step 2: Run to verify it fails** → compile failure.
- [ ] **Step 3: Implement** `OpenAiChatModelApi.call()` mirroring `AnthropicChatModelApi.call()` (try/finally `client.close()`; wrap exceptions in `ConnectorException(ERROR_CODE_FAILED_MODEL_CALL, …)`). Each strategy: `converter.toXxxParams(...)` → `client.<family>().createStreaming(params)` inside try-with-resources → `assembler.assemble(stream)` → `responseConverter.toResult(...)`.
- [ ] **Step 4: Run to verify it passes.**
- [ ] **Step 5: Commit**
```bash
git commit -m "feat(agentic-ai): add OpenAI chat-model API with per-family strategy dispatch"
```

---

## Task 13: Factory, Spring config, and registration

**Files:**
- Create: `…/agenticai/aiagent/framework/openai/OpenAiChatModelApiFactory.java`, `configuration/AgenticAiOpenAiFrameworkConfiguration.java`, `framework/openai/package-info.java`, `framework/openai/configuration/package-info.java`
- Modify: `…/agenticai/autoconfigure/AgenticAiConnectorsAutoConfiguration.java` (add import + `@Import` entry)
- Test: `…/agenticai/aiagent/framework/openai/OpenAiChatModelApiFactoryTest.java`

**Interfaces:**
- Consumes: `ChatModelApiFactory`, `LlmProviderChatModelApiConfiguration`, `OpenAiChatModel`, `ModelCapabilitiesResolver`, `HttpTransportSupport`, `@ConnectorsObjectMapper ObjectMapper`, `OpenAiApiFamily`.
- Produces: `OpenAiChatModelApiFactory` (`ORDER=100`; `API_FAMILY_RESPONSES="openai-responses"`, `..._COMPLETIONS="openai-completions"`); `supports()` = config is `LlmProviderChatModelApiConfiguration` wrapping an `OpenAiChatModel` (both backends); `create()` resolves capabilities by `apiFamilyKey()`, picks the strategy by `apiFamily()`, wires converters/assemblers/client factory (mirror `AnthropicChatModelApiFactory.create()`). Spring bean gated by `camunda.connector.agenticai.aiagent.framework.openai.enabled` (`matchIfMissing=true`).

- [ ] **Step 1: Write the failing test:**
```java
@Test void supportsOpenAiDirectAndCompatible() { ... assertThat(factory.supports(llm(openAiDirect))).isTrue();
    assertThat(factory.supports(llm(openAiCompatible))).isTrue(); }
@Test void doesNotSupportAnthropic() { assertThat(factory.supports(llm(anthropicModel))).isFalse(); }
@Test void createReturnsOpenAiChatModelApi() { assertThat(factory.create(llm(openAiResponsesDirect)))
    .isInstanceOf(OpenAiChatModelApi.class); }
@Test void orderIs100() { assertThat(factory.getOrder()).isEqualTo(100); }
```
(Mock `ModelCapabilitiesResolver.resolve(...)`/`matches(...)`; mock `HttpTransportSupport`.)
- [ ] **Step 2: Run to verify it fails.**
- [ ] **Step 3: Implement** the factory + Spring config (copy `AgenticAiAnthropicFrameworkConfiguration` verbatim, rename), add the `@Import` entry + import line in `AgenticAiConnectorsAutoConfiguration` next to the Anthropic one, and create both `package-info.java` files.
- [ ] **Step 4: Run to verify it passes**, then a Spring wiring smoke test:
```bash
mvn -q -o test -Dtest=OpenAiChatModelApiFactoryTest -Dsurefire.failIfNoSpecifiedTests=false
```
- [ ] **Step 5: Commit**
```bash
git commit -m "feat(agentic-ai): register native OpenAI provider factory and Spring config"
```

---

## Task 14: Captured-fixture round-trip witnesses

The data-model validation goal: real payloads round-trip through the neutral model byte-identically.

**Files:**
- Create test resources: `src/test/resources/openai/fixtures/responses-reasoning.json`, `responses-code-interpreter.json`, `responses-web-search.json` (captured or hand-authored minimal-but-realistic Responses `output` items — reasoning w/ `encrypted_content`, a `code_interpreter_call` + output, a `web_search_call` + output).
- Test: `…/agenticai/aiagent/framework/openai/OpenAiProviderContentRoundTripTest.java`

**Interfaces:**
- Consumes: `OpenAiResponsesResponseConverter` (Task 9), `OpenAiResponsesRequestConverter` (Task 8) reasoning/provider replay, `objectMapper`.

- [ ] **Step 1: Write the failing test:** for each fixture, deserialize to the SDK `Response`, run `toResult(...)` → neutral `AssistantMessage`, assert the expected `ReasoningContent`/`ProviderContent` is produced with `payload`/`providerPayload` equal (as `Map`) to the source item; then feed that neutral content back through the request converter's replay path and assert the re-serialized input item JSON equals the original item JSON (byte-identical via `objectMapper.readTree(a).equals(objectMapper.readTree(b))`).
- [ ] **Step 2: Run to verify it fails.**
- [ ] **Step 3: Author the fixtures** (minimal, realistic) and adjust the converters only if a real gap surfaces (do not weaken the assertion).
- [ ] **Step 4: Run to verify it passes.**
- [ ] **Step 5: Commit**
```bash
git commit -m "test(agentic-ai): byte-identical round-trip witnesses for OpenAI reasoning and server-tool blocks"
```

---

## Task 15: Regenerate the v2 element template

The `@TemplateProperty` annotations were added in Task 2; this task regenerates the templates and verifies the generated JSON.

**Files:**
- Modify (generated): `element-templates/agenticai-ai-agent-task.v2.json`, `element-templates/agenticai-ai-agent-subprocess.v2.json` (and any versioned copy the transform makes)
- Test: `…/agenticai/aiagent/…/ElementTemplateGenerationTest.java` if one exists (grep for the existing template-generation/verification test) — else a small JSON assertion test in the e2e path is added in Task 16.

- [ ] **Step 1: Regenerate** (element-template-generator runs via the module build; the groovy transform is `bin/transform-ai-agent-subprocess-v2-template.groovy`). **Run outside the sandbox** (needs node/asdf for element-templates-cli):
```bash
cd connectors/agentic-ai/connector-agentic-ai && mvn -q -o generate-resources
```
Expected: `agenticai-ai-agent-task.v2.json` now contains properties `configuration.openai.model.parameters.effort` (Dropdown, choices minimal/low/medium/high), `configuration.openai.enableWebSearch`, `configuration.openai.enableCodeInterpreter`, each with a generated `condition` `allMatch` including `configuration.openai.apiFamily == responses` (and the `configuration.type == openai` discriminator guard).
- [ ] **Step 2: Verify the generated JSON** — grep the template for the three new property ids and confirm the `condition` blocks:
```bash
grep -n "configuration.openai.model.parameters.effort\|enableWebSearch\|enableCodeInterpreter" \
  connectors/agentic-ai/connector-agentic-ai/element-templates/agenticai-ai-agent-task.v2.json
```
Expected: each id present with an `apiFamily`/`responses` condition. If a `condition` is missing, fix the `@TemplateProperty(condition = …)` in `OpenAiChatModel` (Task 2) and regenerate.
- [ ] **Step 3: Commit**
```bash
git add connectors/agentic-ai/connector-agentic-ai/element-templates/agenticai-ai-agent-task.v2.json \
        connectors/agentic-ai/connector-agentic-ai/element-templates/agenticai-ai-agent-subprocess.v2.json
git commit -m "chore(agentic-ai): regenerate v2 template with OpenAI effort and server-tool toggles"
```

---

## Task 16: Acceptance-IT rows, server-tool scenarios, and assert helper

**Files (e2e-test module — Apache header):**
- Modify: `connectors-e2e-test/connectors-e2e-test-agentic-ai/src/test/java/io/camunda/connector/e2e/agenticai/assertj/JobWorkerAgentResponseAssert.java`
- Modify: `connectors-e2e-test/connectors-e2e-test-agentic-ai/src/test/java/io/camunda/connector/e2e/agenticai/e2e/NativeProviderAcceptanceIT.java`

**Interfaces:**
- Consumes: existing `NativeProvider` record, `Capability` enum, `providers()`, `buildModel`, `assertAgentResponse`/`assertResponseTextContains`, `JobWorkerAgentResponseAssert`.
- Produces: `Capability.WEB_SEARCH`, `Capability.CODE_INTERPRETER`; `openaiDirect(...)` helper; two OpenAI rows in `providers()`; `hasProviderContentBlockOfType(String provider, String blockType)` on the assert; two Responses-only scenario methods.

- [ ] **Step 1: Add `hasProviderContentBlockOfType` to the assert** (mirror the existing content-reading methods):
```java
  public JobWorkerAgentResponseAssert hasProviderContentBlockOfType(String provider, String blockType) {
    isNotNull();
    Assertions.assertThat(actual.responseMessage()).isNotNull();
    Assertions.assertThat(actual.responseMessage().content())
        .anySatisfy(c -> {
          Assertions.assertThat(c).isInstanceOf(ProviderContent.class);
          var pc = (ProviderContent) c;
          Assertions.assertThat(pc.provider()).isEqualTo(provider);
          Assertions.assertThat(pc.blockType()).isEqualTo(blockType);
        });
    return this;
  }
```
Add import `io.camunda.connector.agenticai.aiagent.model.message.content.ProviderContent;`.

- [ ] **Step 2: Add the enum values + `openaiDirect` helper + rows.** Extend `Capability` with `WEB_SEARCH, CODE_INTERPRETER`. Add:
```java
  static NativeProvider openaiDirect(
      String apiFamily,
      String model,
      Map<Capability, Map<String, String>> capabilityProperties,
      boolean forcesReasoningTokens) {
    return new NativeProvider(
        "openai-" + apiFamily + "/" + model,
        "OPENAI_API_KEY",
        Map.of(
            "configuration.type", "openai",
            "configuration.openai.apiFamily", apiFamily,
            "configuration.openai.backend.type", "direct",
            "configuration.openai.backend.apiKey", envOrPlaceholder("OPENAI_API_KEY"),
            "configuration.openai.model.model", model),
        capabilityProperties,
        forcesReasoningTokens);
  }
```
Append to `providers()` (before `.filter`):
```java
            openaiDirect(
                "responses", "gpt-5",
                Map.of(
                    Capability.STRUCTURED_OUTPUT, Map.of(),
                    Capability.MULTIMODAL_TOOL_RESULT, Map.of(),
                    Capability.PROMPT_CACHING, Map.of(),
                    Capability.REASONING,
                        Map.of("configuration.openai.model.parameters.effort", "high"),
                    Capability.WEB_SEARCH,
                        Map.of("configuration.openai.enableWebSearch", "true"),
                    Capability.CODE_INTERPRETER,
                        Map.of("configuration.openai.enableCodeInterpreter", "true")),
                true),
            openaiDirect(
                "completions", "gpt-4o",
                Map.of(
                    Capability.STRUCTURED_OUTPUT, Map.of(),
                    Capability.MULTIMODAL_TOOL_RESULT, Map.of(),
                    Capability.PROMPT_CACHING, Map.of()),
                false)
```
(Pin the exact model ids you have access to; `gpt-5`/`gpt-4o` shown.)

- [ ] **Step 3: Add the two server-tool scenarios** (mirror `reasoningEnabledProducesReasoningTokens`'s structure; source only providers that `supports(...)`):
```java
  static Stream<NativeProvider> providersWithCodeInterpreter() {
    return providers().filter(p -> p.supports(Capability.CODE_INTERPRETER));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("providersWithCodeInterpreter")
  void codeInterpreterComputesDeterministicResult(NativeProvider provider) {
    var model = buildModel(
        provider, AI_AGENT_JOB_WORKER_V2_ELEMENT_TEMPLATE_PATH, BPMN_RESOURCE,
        "You may run code to compute exact answers.",
        template -> provider.propertiesFor(Capability.CODE_INTERPRETER).forEach(template::property));
    var instance = startAgent(model, PROCESS_ID, Map.of(
        "userPrompt",
        "Compute 987654321 * 123456789 exactly using code. Reply with just the number."));
    assertAgentResponse(instance, response ->
        JobWorkerAgentResponseAssert.assertThat(response).isReady()
            .hasResponseTestSatisfying(text ->
                org.assertj.core.api.Assertions.assertThat(text).contains("121932631112635269")));
    // Structural round-trip witness: a code_interpreter provider block was captured.
    // (Assert via a second read if includeAssistantMessage exposes it; else rely on completion,
    //  since a failed replay would 400 and the process would not complete.)
  }

  static Stream<NativeProvider> providersWithWebSearch() {
    return providers().filter(p -> p.supports(Capability.WEB_SEARCH));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("providersWithWebSearch")
  void webSearchCompletesAndRoundTrips(NativeProvider provider) {
    var model = buildModel(
        provider, AI_AGENT_JOB_WORKER_V2_ELEMENT_TEMPLATE_PATH, BPMN_RESOURCE,
        "Use web search when you need current information.",
        template -> provider.propertiesFor(Capability.WEB_SEARCH).forEach(template::property));
    var instance = startAgent(model, PROCESS_ID, Map.of(
        "userPrompt", "Search the web for the current stable version of the Camunda 8 documentation "
            + "and briefly state what you found."));
    // Non-deterministic content: assert completion (a failed server-tool replay would 400).
    assertResponseTextContains(instance);
  }
```
(`987654321 * 123456789 = 121932631112635269`. If you prefer a `hasProviderContentBlockOfType` assertion here, confirm the persisted final assistant message includes the server-tool block for the model used; otherwise the completion-across-continuation check is the round-trip witness.)

- [ ] **Step 4: Compile + gated-skip run (NO real API)**
```bash
cd connectors-e2e-test/connectors-e2e-test-agentic-ai && \
  mvn -q -o -am test-compile && \
  mvn -q -o test -Dtest=NativeProviderAcceptanceIT -Dsurefire.failIfNoSpecifiedTests=false
```
Expected: test-compile SUCCESS; the IT **self-skips** (`RUN_NATIVE_LLM_E2E` unset) — 0 run, 0 failures.

- [ ] **Step 5: Commit**
```bash
git add connectors-e2e-test/connectors-e2e-test-agentic-ai/src/test/java/io/camunda/connector/e2e/agenticai/assertj/JobWorkerAgentResponseAssert.java \
        connectors-e2e-test/connectors-e2e-test-agentic-ai/src/test/java/io/camunda/connector/e2e/agenticai/e2e/NativeProviderAcceptanceIT.java
git commit -m "test(agentic-ai): add OpenAI provider rows and server-tool acceptance scenarios"
```

- [ ] **Step 6: MANUAL — real-API acceptance run (developer, permission-gated)**

Do **not** run without explicit user permission (cost). When approved:
```bash
cd connectors-e2e-test/connectors-e2e-test-agentic-ai && \
  <with-LLM-credentials-in-env> \
  env RUN_NATIVE_LLM_E2E=true mvn -o -am test \
  -Dtest=NativeProviderAcceptanceIT -Dsurefire.failIfNoSpecifiedTests=false
```
Expected: Responses row green on all 7 scenarios; Completions row green on its 4. Fix test-side issues only; product changes go back through the relevant task. **Never read the secrets file.**

---

## Self-Review

**Spec coverage:**
- Both families (Responses full / Completions subset) → Tasks 8–12, 16. ✓
- Direct + Compatible backends → Task 6, factory `supports()` Task 13. ✓
- Streaming both families → assemblers Tasks 9/11, strategies Task 12. ✓
- Effort `{MINIMAL,LOW,MEDIUM,HIGH}` nullable, no budget/adaptive, `NONE` deferred → Task 2. ✓
- Reasoning allowed only on Responses (matrix + validator) → Tasks 4, 5. ✓
- Server tools web_search + code_interpreter, Responses-only, toggles + provisioning + ProviderContent → Tasks 2, 8, 9, 14, 16. ✓
- code_interpreter file/image output held opaquely (no materialization) → Task 9 (`ProviderContent`), fixtures Task 14. ✓
- Capability projection types + fail-fast validation → Tasks 3, 5. ✓
- Acceptance IT rows + new scenarios + assert helper → Task 16. ✓
- Element template effort + toggles + apiFamily-gated visibility → Tasks 2 (annotations), 15 (regen). ✓
- Captured-fixture byte-identical round-trip witnesses → Task 14. ✓
- SDK spike first → Task 1. ✓

**Placeholder scan:** SDK method names are explicitly pinned in Task 1 and referenced as "SDK-SURFACE"; converter bodies point to the exact Anthropic sibling to mirror (existing-codebase pattern), not "implement later". Fixture contents (Task 14) are authored in-task. No `TBD`.

**Type consistency:** `OpenAiEffort` (config pkg), `OpenAiModelCapabilities`/`Data`/`OpenAiReasoningCapabilities`/`OpenAiProviderCapabilities` (framework pkg), `OpenAiRequestValidator.validate(OpenAiConnection, OpenAiReasoningCapabilities, boolean, String)`, `OpenAiChatModelApi(OpenAiClientFactory, OpenAiApiFamilyStrategy, OpenAiModelCapabilities, boolean)`, `hasProviderContentBlockOfType(String, String)` — names are consistent across tasks.

**Deferred (recorded, not built):** generic Completions reasoning round-trip; `NONE` effort; output-document materialization (#7781); full server-tool depth; Compatible+Responses real-API coverage; per-model server-tool matrix gating.
