# Azure AI Foundry Provider — Milestone 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Milestone 1 `ConnectorInputException` stub in `ChatModelFactoryImpl` with the real Azure AI Foundry runtime — Anthropic-on-Foundry via `com.anthropic:anthropic-java-foundry` SDK with a custom JDK-backed `HttpClient` SPI implementation, OpenAI-on-Foundry via delegation to the existing `langchain4j-azure-open-ai` integration.

**Architecture:** One new langchain4j-free package (`azurefoundry/` + `azurefoundry/http/`) holds the Anthropic client factory and the custom HttpClient SPI implementation; one adapter subpackage (`azurefoundry/langchain4j/`) is the only file allowed to import `dev.langchain4j.*`. OpenAI-family Foundry configs flow through a shared helper extracted from `ChatModelFactoryImpl`, reusing the existing `AzureOpenAiChatModel` builder. ArchUnit rules enforce the decoupling boundary. TDD throughout: e2e contract tests first, inside-out unit tests, implementation last.

**Tech Stack:** Java 21, Maven, Spring Boot auto-config, JUnit 5 + AssertJ + Mockito + WireMock, langchain4j `1.13.0`, `com.anthropic:anthropic-java-core` + `com.anthropic:anthropic-java-foundry:2.26.0`, `com.azure.identity` (already in deps), ArchUnit `1.3.0`.

**Spec:** `connectors/agentic-ai/docs/superpowers/specs/2026-04-23-azure-ai-foundry-provider-design.md`
**Milestone 1 (predecessor, already merged on this branch):** `connectors/agentic-ai/docs/superpowers/plans/2026-04-23-azure-ai-foundry-provider-milestone-1.md`

Demo feedback already applied to M1 (label tweaks + OpenAI deployment-name placeholder) — no residual M1 work.

---

## File Structure

**New source files:**

```
connectors/agentic-ai/src/main/java/io/camunda/connector/agenticai/azurefoundry/
├── AnthropicOnFoundryClientFactory.java     # builds AnthropicClient from AzureFoundryProviderConfiguration
├── http/
│   └── JdkAnthropicHttpClient.java          # implements com.anthropic.core.http.HttpClient over java.net.http.HttpClient
└── langchain4j/
    └── AnthropicOnFoundryChatModel.java     # implements dev.langchain4j.model.chat.ChatModel; translates SDK ↔ langchain4j
```

**Modified source files:**

```
connectors/agentic-ai/
├── pom.xml                                                              # add anthropic-java-core, anthropic-java-foundry, archunit
├── src/main/java/io/camunda/connector/agenticai/
│   ├── aiagent/framework/langchain4j/
│   │   └── ChatModelFactoryImpl.java                                    # extract OpenAI helper; replace stub with real Foundry dispatch
│   └── autoconfigure/
│       └── AgenticAiConnectorsAutoConfiguration.java                    # (or AgenticAiLangchain4JFrameworkConfiguration) Spring wiring
└── AGENTS.md                                                            # provider-list section
```

**Parent `pom.xml` (one-file addition):**

```
parent/pom.xml     # add <version.anthropic-java> and <version.archunit> managed properties
```

**New test files:**

```
connectors/agentic-ai/src/test/java/io/camunda/connector/agenticai/
├── azurefoundry/
│   ├── ArchitectureTest.java                                             # ArchUnit guard
│   ├── AnthropicOnFoundryClientFactoryTest.java
│   ├── http/
│   │   └── JdkAnthropicHttpClientTest.java                               # WireMock
│   └── langchain4j/
│       └── AnthropicOnFoundryChatModelTest.java                          # Mockito
└── aiagent/
    ├── framework/langchain4j/
    │   └── ChatModelFactoryTest.java                                     # ADD nested test for Foundry dispatch
    └── model/request/provider/
        └── AzureFoundryProviderConfigurationDeserializationTest.java     # JSON roundtrip
```

```
connectors-e2e-test/connectors-e2e-test-agentic-ai/src/test/java/io/camunda/connector/e2e/agenticai/aiagent/
├── AzureFoundryAnthropicAgentE2ETest.java                                # WireMock; Anthropic Messages wire format
├── AzureFoundryOpenAiAgentE2ETest.java                                   # WireMock; OpenAI chat completions wire format
└── AzureOpenAiLegacyCompatibilityE2ETest.java                            # safety net
```

**New ADR:**

```
connectors/agentic-ai/docs/adr/
└── 004-azure-ai-foundry-provider.md
```

---

## Key external references (the implementer should keep these tabs open)

- **Anthropic Java SDK source:** https://github.com/anthropics/anthropic-sdk-java
- **Anthropic `HttpClient` SPI:** `anthropic-java-core/src/main/kotlin/com/anthropic/core/http/HttpClient.kt`
- **Foundry SDK usage pattern:** https://platform.claude.com/docs/en/build-with-claude/claude-in-microsoft-foundry (Java tab)
- **Exception hierarchy:** `com.anthropic.errors.*` — `AnthropicException` (base), `AnthropicServiceException` (HTTP errors; `statusCode()`, `body()`), and subtypes `BadRequestException` (400), `UnauthorizedException` (401), `PermissionDeniedException` (403), `NotFoundException` (404), `UnprocessableEntityException` (422), `RateLimitException` (429), `InternalServerException` (5xx), plus transport-level `AnthropicIoException`, `AnthropicRetryableException`.
- **Azure Identity bearer-token supplier:** `com.azure.identity.AuthenticationUtil.getBearerTokenSupplier(TokenCredential, String)` — used with scope `"https://cognitiveservices.azure.com/.default"`.
- **Existing proxy support:** `io.camunda.connector.agenticai.aiagent.framework.langchain4j.ChatModelHttpProxySupport` (already injected into `ChatModelFactoryImpl`); reuses `io.camunda.connector.http.client.client.jdk.proxy.JdkHttpClientProxyConfigurator` from `connector-commons/http-client`.
- **Existing Azure credential construction pattern:** `ChatModelFactoryImpl.createAzureOpenAiChatModelBuilder()` at `/Users/dmitri.nikonov/Development/camunda/connectors/connectors/agentic-ai/src/main/java/io/camunda/connector/agenticai/aiagent/framework/langchain4j/ChatModelFactoryImpl.java` — mirror its `ClientSecretCredentialBuilder` usage.

---

## Implementation Order (TDD)

The milestone is structured **outside-in**: Phase 1 commits the user-visible contract (failing e2e tests), then inner components are built with their own unit tests driving them, and Phase 8 closes the loop by replacing the M1 stub. This ordering means intermediate commits have failing e2e tests by design — they'll turn green as the inner layers are implemented.

Each phase with implementation steps follows **red → green → refactor**. Each task ends with a commit. Commits use conventional-commits (`feat(agentic-ai):`, `test(agentic-ai):`, `refactor(agentic-ai):`, `docs(agentic-ai):`, `deps:` for dep bumps). No `Co-Authored-By:` trailer (already enforced via `attribution.commit: ""` in `.claude/settings.local.json`).

---

## Phase 1 — Contract tests first (red)

### Task 1.1: Write `AzureFoundryAnthropicAgentE2ETest`

**Files:**
- Create: `connectors-e2e-test/connectors-e2e-test-agentic-ai/src/test/java/io/camunda/connector/e2e/agenticai/aiagent/AzureFoundryAnthropicAgentE2ETest.java`
- Reference (read-only, mirror structure): `connectors-e2e-test/connectors-e2e-test-agentic-ai/src/test/java/io/camunda/connector/e2e/agenticai/aiagent/BaseAiAgentTest.java`
- Reference (read-only, find closest existing Anthropic or Azure OpenAI e2e test class in same dir): `connectors-e2e-test/connectors-e2e-test-agentic-ai/src/test/java/io/camunda/connector/e2e/agenticai/aiagent/` — look for `AnthropicAiAgentTest.java` or similar; use it as the structural template.

- [ ] **Step 1: Read the reference e2e test to understand the harness pattern**

```bash
ls connectors-e2e-test/connectors-e2e-test-agentic-ai/src/test/java/io/camunda/connector/e2e/agenticai/aiagent/
```

Pick the Anthropic-provider e2e test (structurally closest to Foundry-Anthropic). Read it end-to-end. Note: how is `ProviderConfiguration` set as a process variable? How does WireMock stub the Anthropic Messages endpoint? What response body does it return? What does the assertion flow look like (process output variable `agent`, tool-call path)?

- [ ] **Step 2: Write the new e2e test (will be RED once compiled — the runtime stub from M1 throws on invocation)**

Create the file with the following shape (copy structure faithfully from the Anthropic reference test; adjust only the provider configuration and the WireMock URL path):

```java
package io.camunda.connector.e2e.agenticai.aiagent;

// imports: WireMock, JUnit 5, Zeebe test containers, agent-framework domain types,
// AzureFoundryProviderConfiguration + nested types

class AzureFoundryAnthropicAgentE2ETest extends BaseAiAgentTest {

  // WireMock stubs go here. Endpoint path must match what our
  // AnthropicOnFoundryClientFactory will call:
  //   POST /anthropic/v1/messages
  // The Anthropic Messages API body format.

  @Test
  void agent_loop_completes_with_tool_call_round_trip() {
    // 1. Stub the first Anthropic response: tool_use block with a simulated tool call.
    stubFor(post(urlEqualTo("/anthropic/v1/messages"))
        .inScenario("two-round trip")
        .whenScenarioStateIs(STARTED)
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "id": "msg_01",
                  "type": "message",
                  "role": "assistant",
                  "content": [
                    {"type": "tool_use", "id": "toolu_01", "name": "weather", "input": {"city": "Berlin"}}
                  ],
                  "model": "claude-sonnet-4-6",
                  "stop_reason": "tool_use",
                  "usage": {"input_tokens": 42, "output_tokens": 18}
                }
                """))
        .willSetStateTo("got_tool_call"));

    // 2. Stub the second Anthropic response: assistant text, end_turn.
    stubFor(post(urlEqualTo("/anthropic/v1/messages"))
        .inScenario("two-round trip")
        .whenScenarioStateIs("got_tool_call")
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "id": "msg_02",
                  "type": "message",
                  "role": "assistant",
                  "content": [{"type": "text", "text": "It's sunny in Berlin."}],
                  "model": "claude-sonnet-4-6",
                  "stop_reason": "end_turn",
                  "usage": {"input_tokens": 60, "output_tokens": 12}
                }
                """));

    // 3. Build the ProviderConfiguration variable: AzureFoundry with Anthropic family
    AzureFoundryProviderConfiguration providerConfig = new AzureFoundryProviderConfiguration(
        new AzureAiFoundryConnection(
            wireMockRuntimeInfo.getHttpBaseUrl(),    // WireMock-served base URL, stands in for services.ai.azure.com
            new AzureApiKeyAuthentication("test-api-key"),
            null,                                     // default timeouts
            new AnthropicModel(
                "claude-sonnet-4-6",
                new AnthropicModelParameters(1024, 0.7, null, null))));

    // 4. Deploy the BPMN test model, inject providerConfig + user prompt, run, assert on agent output
    // The assertion pattern is whatever the reference Anthropic e2e test uses. Mirror it.

    // 5. Assertion: the agent loop completed both model calls (tool_use → end_turn),
    // the final agent response contains "sunny in Berlin", and the token usage from both
    // calls is aggregated in the AgentContext metrics.
  }
}
```

The test will not compile until `AzureFoundryProviderConfiguration` is accessible from the e2e module. That module already depends on `connector-agentic-ai`, and the config class was added in Milestone 1. If compilation fails due to imports, verify the e2e module's pom.xml has `<scope>test</scope>` access to agentic-ai test sources — if not, import from the main classpath; classes in `connectors/agentic-ai/src/main/java/...model/request/provider/` are public API.

- [ ] **Step 3: Compile the test module to confirm it compiles (even though it will fail at runtime)**

```bash
cd /Users/dmitri.nikonov/Development/camunda/connectors
mvn test-compile -pl connectors-e2e-test/connectors-e2e-test-agentic-ai -q
```

Expected: BUILD SUCCESS. If imports are wrong, fix the imports; the types referenced are all in `io.camunda.connector.agenticai.aiagent.model.request.provider.*` and `io.camunda.connector.agenticai.aiagent.model.request.provider.shared.*`.

- [ ] **Step 4: Run the test to confirm it fails with the expected M1 stub exception**

```bash
mvn test -pl connectors-e2e-test/connectors-e2e-test-agentic-ai -Dtest='AzureFoundryAnthropicAgentE2ETest'
```

Expected: **FAIL** with a `ConnectorInputException` wrapping `IllegalStateException: "Azure AI Foundry runtime is not yet implemented (planned for Milestone 2)..."`. The failure location is inside the Zeebe engine where the connector runtime invokes the factory.

If the test fails for a different reason (WireMock setup, BPMN not found, incorrect provider-config JSON), fix those first — the test must ONLY fail because the runtime stub throws, and that failure mode is exactly what Phase 8 will make go away.

- [ ] **Step 5: Commit**

```bash
git add connectors-e2e-test/connectors-e2e-test-agentic-ai/src/test/java/io/camunda/connector/e2e/agenticai/aiagent/AzureFoundryAnthropicAgentE2ETest.java

git commit -m "$(cat <<'EOF'
test(e2e): add Azure AI Foundry Anthropic agent e2e contract (red)

Encodes the user-visible contract for the Milestone 2 Anthropic-on-Foundry
runtime via the existing WireMock-based e2e harness: a two-turn agent loop
with a tool_use → end_turn round-trip, mocked Anthropic Messages API wire-
format responses on the Foundry endpoint path (/anthropic/v1/messages).

Currently red — fails at invocation with the Milestone 1 stub
ConnectorInputException. Milestone 2 implementation will make it pass.

Refs: camunda/connectors#6993
EOF
)"
```

---

### Task 1.2: Write `AzureFoundryOpenAiAgentE2ETest`

**Files:**
- Create: `connectors-e2e-test/connectors-e2e-test-agentic-ai/src/test/java/io/camunda/connector/e2e/agenticai/aiagent/AzureFoundryOpenAiAgentE2ETest.java`
- Reference: existing Azure OpenAI or OpenAI e2e test class in same directory (structural template).

- [ ] **Step 1: Write the e2e test mirroring the Azure OpenAI e2e test's structure**

Target WireMock URL path matches the Azure OpenAI chat-completions deployment path that `langchain4j-azure-open-ai` generates internally: `/openai/deployments/<deploymentName>/chat/completions?api-version=<version>`. Response body is OpenAI chat-completions wire format.

```java
package io.camunda.connector.e2e.agenticai.aiagent;

class AzureFoundryOpenAiAgentE2ETest extends BaseAiAgentTest {

  @Test
  void agent_loop_completes_with_tool_call_round_trip_via_openai_family() {
    // 1. Stub first OpenAI chat-completions response: tool_calls in the assistant message.
    stubFor(post(urlPathMatching("/openai/deployments/.*/chat/completions"))
        .inScenario("two-round trip")
        .whenScenarioStateIs(STARTED)
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "id": "chatcmpl-01",
                  "object": "chat.completion",
                  "model": "gpt-4o",
                  "choices": [{
                    "index": 0,
                    "message": {
                      "role": "assistant",
                      "content": null,
                      "tool_calls": [{
                        "id": "call_01",
                        "type": "function",
                        "function": {"name": "weather", "arguments": "{\\"city\\":\\"Berlin\\"}"}
                      }]
                    },
                    "finish_reason": "tool_calls"
                  }],
                  "usage": {"prompt_tokens": 42, "completion_tokens": 18, "total_tokens": 60}
                }
                """))
        .willSetStateTo("got_tool_call"));

    // 2. Stub second OpenAI response: assistant text, finish_reason stop.
    stubFor(post(urlPathMatching("/openai/deployments/.*/chat/completions"))
        .inScenario("two-round trip")
        .whenScenarioStateIs("got_tool_call")
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "id": "chatcmpl-02",
                  "object": "chat.completion",
                  "model": "gpt-4o",
                  "choices": [{
                    "index": 0,
                    "message": {"role": "assistant", "content": "It's sunny in Berlin."},
                    "finish_reason": "stop"
                  }],
                  "usage": {"prompt_tokens": 60, "completion_tokens": 12, "total_tokens": 72}
                }
                """));

    // 3. ProviderConfiguration: AzureFoundry with OpenAI family.
    AzureFoundryProviderConfiguration providerConfig = new AzureFoundryProviderConfiguration(
        new AzureAiFoundryConnection(
            wireMockRuntimeInfo.getHttpBaseUrl(),
            new AzureApiKeyAuthentication("test-api-key"),
            null,
            new OpenAiModel(
                "gpt-4o",
                new OpenAiModelParameters(1024, 0.7, null))));

    // 4-5. Mirror Task 1.1's deploy/run/assert.
  }
}
```

- [ ] **Step 2: Compile**

```bash
mvn test-compile -pl connectors-e2e-test/connectors-e2e-test-agentic-ai -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Run the test**

```bash
mvn test -pl connectors-e2e-test/connectors-e2e-test-agentic-ai -Dtest='AzureFoundryOpenAiAgentE2ETest'
```

Expected: **FAIL** with the same M1 stub `ConnectorInputException`. Phase 8's dispatch update (the `OpenAiModel` branch delegates to the shared OpenAI builder helper) will make it pass.

- [ ] **Step 4: Commit**

```bash
git add connectors-e2e-test/connectors-e2e-test-agentic-ai/src/test/java/io/camunda/connector/e2e/agenticai/aiagent/AzureFoundryOpenAiAgentE2ETest.java

git commit -m "$(cat <<'EOF'
test(e2e): add Azure AI Foundry OpenAI agent e2e contract (red)

Encodes the Milestone 2 contract for OpenAI-on-Foundry via the OpenAI
chat-completions wire format, mocked through WireMock on the Azure OpenAI
deployment path. Verifies the delegation path through
langchain4j-azure-open-ai works behind the unified AzureAiFoundry provider.

Currently red — will pass once Phase 8 replaces the M1 stub with real
factory dispatch.

Refs: camunda/connectors#6993
EOF
)"
```

---

### Task 1.3: Write `AzureOpenAiLegacyCompatibilityE2ETest`

**Files:**
- Create: `connectors-e2e-test/connectors-e2e-test-agentic-ai/src/test/java/io/camunda/connector/e2e/agenticai/aiagent/AzureOpenAiLegacyCompatibilityE2ETest.java`

Rationale: the `AzureAuthentication` extraction in M1 and the upcoming `buildAzureOpenAiChatModel(...)` refactor in Phase 4 both touch the code path used by the pre-existing `azureOpenAi` provider type. A green-from-day-one e2e test against the old config shape acts as the safety net throughout M2.

- [ ] **Step 1: Write the test**

```java
package io.camunda.connector.e2e.agenticai.aiagent;

/**
 * Regression test for the existing Azure OpenAI provider configuration path.
 * Confirms that pre-existing BPMNs using the {@code "type": "azureOpenAi"}
 * provider JSON continue to work after the Milestone 1 refactor
 * (AzureAuthentication extracted to shared/) and the Milestone 2 factory
 * helper extraction.
 */
class AzureOpenAiLegacyCompatibilityE2ETest extends BaseAiAgentTest {

  @Test
  void azure_openai_legacy_provider_still_runs_agent_loop() {
    // Stub the same two-turn OpenAI chat-completions flow as Task 1.2.
    // Copy the stubs verbatim from Task 1.2 (inline here; do not abstract).

    // ProviderConfiguration: legacy AzureOpenAi (not AzureFoundry).
    AzureOpenAiProviderConfiguration providerConfig = new AzureOpenAiProviderConfiguration(
        new AzureOpenAiConnection(
            wireMockRuntimeInfo.getHttpBaseUrl(),
            new AzureApiKeyAuthentication("test-api-key"),
            null,
            new AzureOpenAiModel(
                "gpt-4o",
                new AzureOpenAiModelParameters(1024, 0.7, null))));

    // Deploy/run/assert — same as Task 1.2.
  }
}
```

- [ ] **Step 2: Run the test — it MUST pass from day one**

```bash
mvn test -pl connectors-e2e-test/connectors-e2e-test-agentic-ai -Dtest='AzureOpenAiLegacyCompatibilityE2ETest'
```

Expected: **PASS**. The existing Azure OpenAI provider code path is unchanged; this test is the contract that protects it during the M2 refactor.

If it fails: the M1 `AzureAuthentication` extraction or some other prior change has already broken backward compatibility. Fix before continuing.

- [ ] **Step 3: Commit**

```bash
git add connectors-e2e-test/connectors-e2e-test-agentic-ai/src/test/java/io/camunda/connector/e2e/agenticai/aiagent/AzureOpenAiLegacyCompatibilityE2ETest.java

git commit -m "$(cat <<'EOF'
test(e2e): add Azure OpenAI legacy-compatibility safety net

Protects the pre-existing Azure OpenAI provider against accidental breakage
from the shared AzureAuthentication extraction and the upcoming OpenAI
builder helper extraction in Milestone 2. Test passes from day one.
EOF
)"
```

---

## Phase 2 — Dependencies + architectural guard

### Task 2.1: Add Maven dependencies (anthropic-java + ArchUnit)

**Files:**
- Modify: `/Users/dmitri.nikonov/Development/camunda/connectors/parent/pom.xml`
- Modify: `connectors/agentic-ai/pom.xml`

- [ ] **Step 1: Add managed versions to `parent/pom.xml`**

Open `parent/pom.xml`. Find the `<properties>` section (where other `<version.*>` entries live, e.g. near `<version.okhttp>4.12.0</version.okhttp>`). Add:

```xml
<version.anthropic-java>2.26.0</version.anthropic-java>
<version.archunit>1.3.0</version.archunit>
```

Then find the `<dependencyManagement>` section (the block with `<dependencies>` under `<dependencyManagement>`). Add entries:

```xml
<dependency>
  <groupId>com.anthropic</groupId>
  <artifactId>anthropic-java-core</artifactId>
  <version>${version.anthropic-java}</version>
</dependency>
<dependency>
  <groupId>com.anthropic</groupId>
  <artifactId>anthropic-java-foundry</artifactId>
  <version>${version.anthropic-java}</version>
</dependency>
<dependency>
  <groupId>com.tngtech.archunit</groupId>
  <artifactId>archunit-junit5</artifactId>
  <version>${version.archunit}</version>
</dependency>
```

Note: the anthropic SDK group is `com.anthropic` (no namespace prefix). Do not confuse with any `anthropic-ai` groupId from npm-style names.

- [ ] **Step 2: Add the dependencies to `connectors/agentic-ai/pom.xml`**

Find the `<dependencies>` block. Add (main-scope, no explicit version — managed by parent):

```xml
<dependency>
  <groupId>com.anthropic</groupId>
  <artifactId>anthropic-java-core</artifactId>
</dependency>
<dependency>
  <groupId>com.anthropic</groupId>
  <artifactId>anthropic-java-foundry</artifactId>
</dependency>
```

Then, separately, in the test-scope block (look for an existing `<scope>test</scope>` dep like `spring-boot-starter-test` or `junit-jupiter-api`):

```xml
<dependency>
  <groupId>com.tngtech.archunit</groupId>
  <artifactId>archunit-junit5</artifactId>
  <scope>test</scope>
</dependency>
```

- [ ] **Step 3: Verify dependencies resolve and build stays green**

```bash
cd /Users/dmitri.nikonov/Development/camunda/connectors
mvn dependency:tree -pl connectors/agentic-ai -q | grep -E "anthropic|archunit"
```

Expected: the three new artifacts appear with their versions. No duplicates from transitive OkHttp (we intentionally skip `anthropic-java-client-okhttp`).

```bash
mvn test -pl connectors/agentic-ai -q
```

Expected: BUILD SUCCESS. All existing tests pass; dependencies don't collide with anything already on the classpath.

- [ ] **Step 4: Commit**

```bash
git add parent/pom.xml connectors/agentic-ai/pom.xml

git commit -m "$(cat <<'EOF'
deps: add anthropic-java SDK and ArchUnit for Azure AI Foundry provider

Adds com.anthropic:anthropic-java-core and com.anthropic:anthropic-java-foundry
2.26.0 as main-scope dependencies for the agentic-ai module (used by the
upcoming Azure AI Foundry runtime), and com.tngtech.archunit:archunit-junit5
1.3.0 as a test-scope dependency (used by the Foundry package's
architectural boundary tests). No OkHttp transitive — anthropic-java-foundry
depends only on anthropic-java-core, and we'll implement the HttpClient SPI
over the JDK's java.net.http.HttpClient for proxy-auth support.
EOF
)"
```

---

### Task 2.2: Add the ArchUnit architecture test

**Files:**
- Create: `connectors/agentic-ai/src/test/java/io/camunda/connector/agenticai/azurefoundry/ArchitectureTest.java`

- [ ] **Step 1: Write the test**

Create the file with:

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.azurefoundry;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "io.camunda.connector.agenticai.azurefoundry")
class ArchitectureTest {

  @ArchTest
  static final ArchRule sdk_layer_must_not_depend_on_langchain4j =
      noClasses()
          .that()
          .resideInAPackage("..azurefoundry..")
          .and()
          .resideOutsideOfPackage("..azurefoundry.langchain4j..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("dev.langchain4j..")
          .because(
              "Only the adapter subpackage may depend on langchain4j; the rest must "
                  + "survive a future langchain4j replacement without modification.");

  @ArchTest
  static final ArchRule azurefoundry_must_not_depend_on_agent_framework_internals =
      noClasses()
          .that()
          .resideInAPackage("..azurefoundry..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "..aiagent.agent..", "..aiagent.memory..", "..adhoctoolsschema..")
          .because(
              "The Foundry packages must stay decoupled from agent framework internals; "
                  + "the only integration point is ChatModel.");
}
```

- [ ] **Step 2: Run the test — passes immediately (no code in forbidden packages yet)**

```bash
mvn test -pl connectors/agentic-ai -Dtest='ArchitectureTest' -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add connectors/agentic-ai/src/test/java/io/camunda/connector/agenticai/azurefoundry/ArchitectureTest.java

git commit -m "$(cat <<'EOF'
test(agentic-ai): add ArchUnit guard for Foundry package boundary

Enforces two invariants on the io.camunda.connector.agenticai.azurefoundry
package tree:
  1. Only the langchain4j/ subpackage may depend on dev.langchain4j.* —
     the rest must survive a future langchain4j replacement.
  2. No class under azurefoundry.* may depend on agent-framework internals
     (aiagent.agent.., aiagent.memory.., adhoctoolsschema..) — the only
     integration point is ChatModel.

Passes immediately — no code exists in the package tree yet; the rule
activates as implementation lands in later tasks.
EOF
)"
```

---

## Phase 3 — Provider configuration deserialization tests

### Task 3.1: Write `AzureFoundryProviderConfigurationDeserializationTest`

Validates that the record tree added in Milestone 1 correctly roundtrips JSON for both family variants and that legacy `azureOpenAi` configs still work after the M1 extraction.

**Files:**
- Create: `connectors/agentic-ai/src/test/java/io/camunda/connector/agenticai/aiagent/model/request/provider/AzureFoundryProviderConfigurationDeserializationTest.java`
- Reference: `connectors/agentic-ai/src/test/java/io/camunda/connector/agenticai/aiagent/model/request/ProviderConfigurationTest.java` (pattern for `Validator` usage + nested test classes)

- [ ] **Step 1: Write the test**

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureFoundryProviderConfiguration.AzureAiFoundryConnection;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureFoundryProviderConfiguration.AzureAiFoundryModel.AnthropicModel;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureFoundryProviderConfiguration.AzureAiFoundryModel.AnthropicModel.AnthropicModelParameters;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureFoundryProviderConfiguration.AzureAiFoundryModel.OpenAiModel;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureFoundryProviderConfiguration.AzureAiFoundryModel.OpenAiModel.OpenAiModelParameters;
import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.AzureAuthentication.AzureApiKeyAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.AzureAuthentication.AzureClientCredentialsAuthentication;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AzureFoundryProviderConfigurationDeserializationTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Nested
  class AnthropicFamily {

    @Test
    void roundtrips_with_api_key_auth() throws Exception {
      String json =
          """
          {
            "type": "azureAiFoundry",
            "azureAiFoundry": {
              "endpoint": "https://example.services.ai.azure.com",
              "authentication": {"type": "apiKey", "apiKey": "k"},
              "model": {
                "family": "anthropic",
                "deploymentName": "claude-sonnet-4-6",
                "parameters": {"maxTokens": 1024, "temperature": 0.7, "topP": 0.9, "topK": 40}
              }
            }
          }
          """;

      ProviderConfiguration parsed = mapper.readValue(json, ProviderConfiguration.class);

      assertThat(parsed).isInstanceOf(AzureFoundryProviderConfiguration.class);
      AzureAiFoundryConnection conn = ((AzureFoundryProviderConfiguration) parsed).azureAiFoundry();
      assertThat(conn.endpoint()).isEqualTo("https://example.services.ai.azure.com");
      assertThat(conn.authentication()).isInstanceOf(AzureApiKeyAuthentication.class);
      assertThat(conn.model()).isInstanceOf(AnthropicModel.class);
      AnthropicModel anthropic = (AnthropicModel) conn.model();
      assertThat(anthropic.deploymentName()).isEqualTo("claude-sonnet-4-6");
      assertThat(anthropic.parameters())
          .isEqualTo(new AnthropicModelParameters(1024, 0.7, 0.9, 40));
    }

    @Test
    void roundtrips_with_client_credentials_auth() throws Exception {
      String json =
          """
          {
            "type": "azureAiFoundry",
            "azureAiFoundry": {
              "endpoint": "https://example.services.ai.azure.com",
              "authentication": {
                "type": "clientCredentials",
                "clientId": "c",
                "clientSecret": "s",
                "tenantId": "t",
                "authorityHost": "https://login.microsoftonline.com"
              },
              "model": {
                "family": "anthropic",
                "deploymentName": "claude-sonnet-4-6",
                "parameters": {}
              }
            }
          }
          """;

      AzureFoundryProviderConfiguration parsed =
          (AzureFoundryProviderConfiguration) mapper.readValue(json, ProviderConfiguration.class);

      assertThat(parsed.azureAiFoundry().authentication())
          .isInstanceOf(AzureClientCredentialsAuthentication.class);
    }
  }

  @Nested
  class OpenAiFamily {

    @Test
    void roundtrips() throws Exception {
      String json =
          """
          {
            "type": "azureAiFoundry",
            "azureAiFoundry": {
              "endpoint": "https://example.services.ai.azure.com",
              "authentication": {"type": "apiKey", "apiKey": "k"},
              "model": {
                "family": "openai",
                "deploymentName": "gpt-4o",
                "parameters": {"maxTokens": 2048, "temperature": 1.0, "topP": 0.95}
              }
            }
          }
          """;

      AzureFoundryProviderConfiguration parsed =
          (AzureFoundryProviderConfiguration) mapper.readValue(json, ProviderConfiguration.class);

      assertThat(parsed.azureAiFoundry().model()).isInstanceOf(OpenAiModel.class);
      OpenAiModel openai = (OpenAiModel) parsed.azureAiFoundry().model();
      assertThat(openai.deploymentName()).isEqualTo("gpt-4o");
      assertThat(openai.parameters()).isEqualTo(new OpenAiModelParameters(2048, 1.0, 0.95));
    }
  }

  @Nested
  class LegacyAzureOpenAiCompat {

    @Test
    void pre_refactor_azure_openai_json_still_deserializes() throws Exception {
      // Legacy BPMN configs use "type": "azureOpenAi" — must still resolve to the
      // existing provider class post-M1 extraction of AzureAuthentication.
      String json =
          """
          {
            "type": "azureOpenAi",
            "azureOpenAi": {
              "endpoint": "https://legacy.openai.azure.com",
              "authentication": {"type": "apiKey", "apiKey": "k"},
              "model": {"deploymentName": "gpt-4o"}
            }
          }
          """;

      ProviderConfiguration parsed = mapper.readValue(json, ProviderConfiguration.class);

      assertThat(parsed).isInstanceOf(AzureOpenAiProviderConfiguration.class);
    }
  }
}
```

- [ ] **Step 2: Run the test**

```bash
mvn test -pl connectors/agentic-ai -Dtest='AzureFoundryProviderConfigurationDeserializationTest' -q
```

Expected: BUILD SUCCESS. All tests pass (the record tree already exists from M1; this test validates it).

If any test fails: typo in the Milestone 1 record annotations (`family` discriminator, nested type names, sealed `permits`). Fix M1 first (adjust the record; regenerate templates; update the M1 commit series via interactive rebase if appropriate). Do not proceed until this is green.

- [ ] **Step 3: Commit**

```bash
git add connectors/agentic-ai/src/test/java/io/camunda/connector/agenticai/aiagent/model/request/provider/AzureFoundryProviderConfigurationDeserializationTest.java

git commit -m "$(cat <<'EOF'
test(agentic-ai): cover Azure AI Foundry provider JSON deserialization

Validates the sealed AzureAiFoundryModel hierarchy roundtrips correctly for
both Anthropic and OpenAI family variants (with API-key and client-
credentials auth), and confirms the pre-existing legacy azureOpenAi JSON
type still deserializes after the Milestone 1 AzureAuthentication
extraction.
EOF
)"
```

---

## Phase 4 — Refactor: extract shared OpenAI builder helper

Prepares `ChatModelFactoryImpl` for Phase 8 where both the legacy `AzureOpenAiProviderConfiguration` path and the new Foundry-with-`OpenAiModel` path need to share the same `AzureOpenAiChatModel` construction. The existing `createAzureOpenAiChatModelBuilder(...)` already does this construction — we just need to parameterise it so both call sites can use it.

### Task 4.1: Extract `buildAzureOpenAiChatModel(...)` helper

**Files:**
- Modify: `connectors/agentic-ai/src/main/java/io/camunda/connector/agenticai/aiagent/framework/langchain4j/ChatModelFactoryImpl.java`

- [ ] **Step 1: Read the current `createAzureOpenAiChatModelBuilder(...)` method**

```bash
grep -n "createAzureOpenAiChatModelBuilder\|AzureOpenAiChatModel\.builder" connectors/agentic-ai/src/main/java/io/camunda/connector/agenticai/aiagent/framework/langchain4j/ChatModelFactoryImpl.java
```

Note the method body — the helper will be the deconstructed version of it.

- [ ] **Step 2: Add the new package-private helper method after the existing `createAzureOpenAiChatModelBuilder(...)` method**

Paste this method into `ChatModelFactoryImpl.java` (place it just after the existing `createAzureOpenAiChatModelBuilder` method). Do not delete the existing method yet — refactoring is step 3.

```java
AzureOpenAiChatModel buildAzureOpenAiChatModel(
    String endpoint,
    AzureAuthentication authentication,
    TimeoutConfiguration timeouts,
    String deploymentName,
    Integer maxTokens,
    Double temperature,
    Double topP) {

  AzureOpenAiChatModel.Builder builder =
      AzureOpenAiChatModel.builder()
          .endpoint(endpoint)
          .deploymentName(deploymentName)
          .timeout(deriveTimeoutSetting(timeouts));

  proxySupport.createAzureProxyOptions(endpoint).ifPresent(builder::proxyOptions);

  switch (authentication) {
    case AzureApiKeyAuthentication key -> builder.apiKey(key.apiKey());
    case AzureClientCredentialsAuthentication creds -> {
      ClientSecretCredentialBuilder credBuilder =
          new ClientSecretCredentialBuilder()
              .clientId(creds.clientId())
              .clientSecret(creds.clientSecret())
              .tenantId(creds.tenantId());
      if (StringUtils.isNotBlank(creds.authorityHost())) {
        credBuilder.authorityHost(creds.authorityHost());
      }
      builder.tokenCredential(credBuilder.build());
    }
  }

  if (maxTokens != null) builder.maxTokens(maxTokens);
  if (temperature != null) builder.temperature(temperature);
  if (topP != null) builder.topP(topP);

  return builder.build();
}
```

This helper fully encapsulates the AzureOpenAiChatModel construction. The `proxySupport`, `deriveTimeoutSetting(...)`, and imports are already present in the file from M1 and earlier — no new imports needed.

- [ ] **Step 3: Refactor `createAzureOpenAiChatModelBuilder(...)` to delegate**

Replace the existing `createAzureOpenAiChatModelBuilder(AzureOpenAiProviderConfiguration configuration)` method body with:

```java
protected AzureOpenAiChatModel createAzureOpenAiChatModel(AzureOpenAiProviderConfiguration configuration) {
  AzureOpenAiConnection conn = configuration.azureOpenAi();
  AzureOpenAiModelParameters params = conn.model().parameters();
  return buildAzureOpenAiChatModel(
      conn.endpoint(),
      conn.authentication(),
      conn.timeouts(),
      conn.model().deploymentName(),
      params != null ? params.maxTokens() : null,
      params != null ? params.temperature() : null,
      params != null ? params.topP() : null);
}
```

Rename the method from `createAzureOpenAiChatModelBuilder` to `createAzureOpenAiChatModel` (drop the `Builder` suffix since it no longer returns a builder) and update its single call site in `createChatModel(...)` accordingly:

```java
// In createChatModel(...) switch:
case AzureOpenAiProviderConfiguration azureOpenAi ->
    createAzureOpenAiChatModel(azureOpenAi);
```

(The prior shape was `createAzureOpenAiChatModelBuilder(azureOpenAi).build()` — now it's `createAzureOpenAiChatModel(azureOpenAi)` which already returns the built `AzureOpenAiChatModel`.)

- [ ] **Step 4: Run the full module test suite to confirm no regression**

```bash
mvn test -pl connectors/agentic-ai -q
```

Expected: BUILD SUCCESS, all 1241+ tests pass. `AzureOpenAiLegacyCompatibilityE2ETest` (from Task 1.3) is the e2e safety net — run it too:

```bash
mvn test -pl connectors-e2e-test/connectors-e2e-test-agentic-ai -Dtest='AzureOpenAiLegacyCompatibilityE2ETest' -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add connectors/agentic-ai/src/main/java/io/camunda/connector/agenticai/aiagent/framework/langchain4j/ChatModelFactoryImpl.java

git commit -m "$(cat <<'EOF'
refactor(agentic-ai): extract shared AzureOpenAi builder helper

Extracts the AzureOpenAiChatModel construction into a package-private
buildAzureOpenAiChatModel(...) helper accepting the fields directly, so the
upcoming Azure AI Foundry OpenAI-family branch can reuse the same build
logic without duplication. The existing Azure OpenAI dispatch path now
unwraps the legacy config and delegates to the helper; no behavior change.
EOF
)"
```

---

## Phase 5 — JDK-backed Anthropic `HttpClient` SPI (red → green → refactor)

### Task 5.1: Write `JdkAnthropicHttpClientTest` (red)

**Files:**
- Create: `connectors/agentic-ai/src/test/java/io/camunda/connector/agenticai/azurefoundry/http/JdkAnthropicHttpClientTest.java`

- [ ] **Step 1: Write the test**

Use WireMock to stand up a local HTTP server and assert that `JdkAnthropicHttpClient` correctly translates Anthropic SDK `HttpRequest` into JDK `HttpRequest`, translates responses back, and propagates timeouts.

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.azurefoundry.http;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.anthropic.core.http.HttpMethod;
import com.anthropic.core.http.HttpRequest;
import com.anthropic.core.http.HttpResponse;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@WireMockTest
class JdkAnthropicHttpClientTest {

  private JdkAnthropicHttpClient client;
  private java.net.http.HttpClient jdkClient;

  @BeforeEach
  void setUp() {
    jdkClient = java.net.http.HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();
    client = new JdkAnthropicHttpClient(jdkClient);
  }

  @Test
  void posts_json_body_and_returns_response_body(WireMockRuntimeInfo wm) throws Exception {
    stubFor(post(urlEqualTo("/anthropic/v1/messages"))
        .withHeader("X-Test-Header", equalTo("value"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("{\"ok\":true}")));

    HttpRequest request = HttpRequest.builder()
        .method(HttpMethod.POST)
        .url(wm.getHttpBaseUrl() + "/anthropic/v1/messages")
        .putHeader("X-Test-Header", "value")
        .body(com.anthropic.core.http.HttpRequestBody.fromBytes(
            "{\"msg\":\"hi\"}".getBytes(StandardCharsets.UTF_8)))
        .build();

    try (HttpResponse response = client.execute(request)) {
      assertThat(response.statusCode()).isEqualTo(200);
      String body = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
      assertThat(body).isEqualTo("{\"ok\":true}");
    }

    verify(postRequestedFor(urlEqualTo("/anthropic/v1/messages"))
        .withHeader("X-Test-Header", equalTo("value"))
        .withRequestBody(equalToJson("{\"msg\":\"hi\"}")));
  }

  @Test
  void propagates_non_2xx_as_http_response_not_exception(WireMockRuntimeInfo wm) throws Exception {
    stubFor(post(urlEqualTo("/err"))
        .willReturn(aResponse()
            .withStatus(429)
            .withHeader("Content-Type", "application/json")
            .withBody("{\"type\":\"error\",\"error\":{\"type\":\"rate_limit_error\",\"message\":\"too fast\"}}")));

    HttpRequest request = HttpRequest.builder()
        .method(HttpMethod.POST)
        .url(wm.getHttpBaseUrl() + "/err")
        .body(com.anthropic.core.http.HttpRequestBody.fromBytes(new byte[0]))
        .build();

    try (HttpResponse response = client.execute(request)) {
      assertThat(response.statusCode()).isEqualTo(429);
      assertThat(new String(response.body().readAllBytes(), StandardCharsets.UTF_8))
          .contains("rate_limit_error");
    }
  }

  @Test
  void async_execute_round_trips(WireMockRuntimeInfo wm) throws Exception {
    stubFor(post(urlEqualTo("/async"))
        .willReturn(aResponse().withStatus(200).withBody("ok")));

    HttpRequest request = HttpRequest.builder()
        .method(HttpMethod.POST)
        .url(wm.getHttpBaseUrl() + "/async")
        .body(com.anthropic.core.http.HttpRequestBody.fromBytes(new byte[0]))
        .build();

    CompletableFuture<HttpResponse> future = client.executeAsync(request);
    try (HttpResponse response = future.get(5, java.util.concurrent.TimeUnit.SECONDS)) {
      assertThat(response.statusCode()).isEqualTo(200);
    }
  }

  @Test
  void get_request_without_body_works(WireMockRuntimeInfo wm) throws Exception {
    stubFor(get(urlEqualTo("/ping"))
        .willReturn(aResponse().withStatus(200).withBody("pong")));

    HttpRequest request = HttpRequest.builder()
        .method(HttpMethod.GET)
        .url(wm.getHttpBaseUrl() + "/ping")
        .build();

    try (HttpResponse response = client.execute(request)) {
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(new String(response.body().readAllBytes(), StandardCharsets.UTF_8))
          .isEqualTo("pong");
    }
  }

  @Test
  void close_does_not_throw() {
    // JDK HttpClient doesn't require explicit close; our wrapper close() is a no-op.
    client.close();
  }
}
```

**Note on SDK API shape:** the `HttpRequest.builder()`, `HttpRequestBody.fromBytes(...)`, `HttpMethod.POST` names are a best-guess from the SDK source. If the actual 2.26.0 API differs (e.g. `HttpMethod.Companion.POST` surfacing oddly from Kotlin, or body construction via a different factory), adjust imports in this test to match the real public API — verify by reading the compiled JARs' Javadoc: `unzip -l ~/.m2/repository/com/anthropic/anthropic-java-core/2.26.0/anthropic-java-core-2.26.0-javadoc.jar 2>/dev/null | head -30`.

- [ ] **Step 2: Run the test — red (class doesn't exist yet)**

```bash
mvn test -pl connectors/agentic-ai -Dtest='JdkAnthropicHttpClientTest' -q
```

Expected: **FAIL** with compile error (`JdkAnthropicHttpClient` not found). Move to the implementation task.

- [ ] **Step 3: Commit the failing test**

```bash
git add connectors/agentic-ai/src/test/java/io/camunda/connector/agenticai/azurefoundry/http/JdkAnthropicHttpClientTest.java

git commit -m "$(cat <<'EOF'
test(agentic-ai): add JdkAnthropicHttpClient contract tests (red)

Defines the expected behavior of the custom com.anthropic.core.http.HttpClient
SPI implementation backed by JDK java.net.http.HttpClient: POST with JSON
body round-trips, non-2xx responses surface as HttpResponse (not exceptions),
async execute works, GET without body works, close() is a no-op. WireMock
stands in for the Foundry endpoint.

Red — implementation is the next commit.
EOF
)"
```

---

### Task 5.2: Implement `JdkAnthropicHttpClient` (green → refactor)

**Files:**
- Create: `connectors/agentic-ai/src/main/java/io/camunda/connector/agenticai/azurefoundry/http/JdkAnthropicHttpClient.java`

- [ ] **Step 1: Implement the class**

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.azurefoundry.http;

import com.anthropic.core.RequestOptions;
import com.anthropic.core.http.HttpClient;
import com.anthropic.core.http.HttpRequest;
import com.anthropic.core.http.HttpResponse;
import com.anthropic.core.http.Headers;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation of {@link com.anthropic.core.http.HttpClient} backed by the JDK
 * {@link java.net.http.HttpClient}. Exists to let the Azure AI Foundry provider reuse the
 * agentic-ai connector's existing JDK-HttpClient-based proxy support (including authenticated
 * proxies via {@code JdkHttpClientProxyConfigurator} + {@code JdkProxyAuthenticator}), instead of
 * pulling in Anthropic's bundled OkHttp-based transport.
 */
public final class JdkAnthropicHttpClient implements HttpClient {

  private final java.net.http.HttpClient jdkHttpClient;

  public JdkAnthropicHttpClient(java.net.http.HttpClient jdkHttpClient) {
    this.jdkHttpClient = jdkHttpClient;
  }

  @Override
  public HttpResponse execute(HttpRequest request, RequestOptions requestOptions) {
    try {
      java.net.http.HttpResponse<InputStream> jdkResponse =
          jdkHttpClient.send(toJdkRequest(request, requestOptions), BodyHandlers.ofInputStream());
      return toAnthropicResponse(jdkResponse);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new com.anthropic.errors.AnthropicIoException("Interrupted while sending request", e);
    } catch (java.io.IOException e) {
      throw new com.anthropic.errors.AnthropicIoException("Transport error", e);
    }
  }

  @Override
  public HttpResponse execute(HttpRequest request) {
    return execute(request, RequestOptions.none());
  }

  @Override
  public CompletableFuture<HttpResponse> executeAsync(
      HttpRequest request, RequestOptions requestOptions) {
    return jdkHttpClient
        .sendAsync(toJdkRequest(request, requestOptions), BodyHandlers.ofInputStream())
        .thenApply(this::toAnthropicResponse);
  }

  @Override
  public CompletableFuture<HttpResponse> executeAsync(HttpRequest request) {
    return executeAsync(request, RequestOptions.none());
  }

  @Override
  public void close() {
    // JDK HttpClient does not require explicit close; no-op.
  }

  private java.net.http.HttpRequest toJdkRequest(
      HttpRequest request, RequestOptions requestOptions) {
    java.net.http.HttpRequest.Builder builder =
        java.net.http.HttpRequest.newBuilder().uri(URI.create(request.url()));

    // Headers
    for (Map.Entry<String, java.util.List<String>> entry : request.headers().values().entrySet()) {
      for (String value : entry.getValue()) {
        builder.header(entry.getKey(), value);
      }
    }

    // Body
    BodyPublisher body = bodyPublisherFor(request);
    builder.method(request.method().name(), body);

    // Timeout from RequestOptions, fall back to JDK client's configured default
    Duration timeout = requestOptions.timeout();
    if (timeout != null) {
      builder.timeout(timeout);
    }

    return builder.build();
  }

  private BodyPublisher bodyPublisherFor(HttpRequest request) {
    if (request.body() == null || request.body().contentLength() == 0L) {
      return BodyPublishers.noBody();
    }
    // Buffer the body. For typical agent LLM requests (~KB-MB) this is fine;
    // streaming bodies aren't needed for the Messages API.
    try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
      request.body().writeTo(baos);
      return BodyPublishers.ofByteArray(baos.toByteArray());
    } catch (java.io.IOException e) {
      throw new com.anthropic.errors.AnthropicIoException("Failed to buffer request body", e);
    }
  }

  private HttpResponse toAnthropicResponse(java.net.http.HttpResponse<InputStream> jdkResponse) {
    Headers headers = convertHeaders(jdkResponse.headers());
    return HttpResponse.builder()
        .statusCode(jdkResponse.statusCode())
        .headers(headers)
        .body(jdkResponse.body())
        .build();
  }

  private Headers convertHeaders(java.net.http.HttpHeaders jdkHeaders) {
    Headers.Builder builder = Headers.builder();
    jdkHeaders
        .map()
        .forEach((name, values) -> values.forEach(v -> builder.put(name, v)));
    return builder.build();
  }
}
```

**Impl note on SDK's exact API:** the `HttpRequest.url()`, `HttpRequest.method()`, `HttpRequest.headers().values()`, `HttpRequest.body().writeTo(OutputStream)`, `HttpRequest.body().contentLength()`, `HttpResponse.builder()`, `Headers.builder().put(...)`, and `com.anthropic.errors.AnthropicIoException(String, Throwable)` signatures reflect the SDK's 2.x shape as of 2.26.0 based on the open-source Kotlin code. If the compiled Java bytecode exposes slightly different accessor names (e.g., `method()` returns `HttpMethod` enum and you need `.name()` or `.toString()` for JDK translation), adjust accordingly. The structure is correct; only field/method names may differ. Resolve by reading `~/.m2/repository/com/anthropic/anthropic-java-core/2.26.0/anthropic-java-core-2.26.0.jar` — use `javap` or IDE exploration.

Also note on the `body()` accessor: Kotlin may expose it as `getBody()` from Java, or as a Property with a field. The SDK documentation (Javadoc JAR) is the source of truth.

- [ ] **Step 2: Run the test**

```bash
mvn test -pl connectors/agentic-ai -Dtest='JdkAnthropicHttpClientTest' -q
```

Expected: BUILD SUCCESS, all 5 test methods pass.

If tests fail with compile errors about unknown methods/fields on `HttpRequest`/`HttpResponse`/`Headers` — the SDK API differs from the guess above; correct based on Javadoc and rerun. Typical adjustments: `HttpRequestBody` vs `HttpRequestBody.Content`, `HttpResponse.body()` returning `ByteArrayInputStream` vs `InputStream`, header multimap accessor named `allValues()` vs `values()`.

- [ ] **Step 3: Confirm ArchUnit still green**

```bash
mvn test -pl connectors/agentic-ai -Dtest='ArchitectureTest' -q
```

Expected: BUILD SUCCESS. The new `JdkAnthropicHttpClient.java` lives in `azurefoundry.http.*` (not `azurefoundry.langchain4j.*`) and has no `dev.langchain4j.*` imports.

- [ ] **Step 4: Commit**

```bash
git add connectors/agentic-ai/src/main/java/io/camunda/connector/agenticai/azurefoundry/http/JdkAnthropicHttpClient.java

git commit -m "$(cat <<'EOF'
feat(agentic-ai): implement JDK-backed HttpClient for anthropic-java SDK

Provides an implementation of com.anthropic.core.http.HttpClient backed by
java.net.http.HttpClient so the Azure AI Foundry provider can reuse the
agentic-ai connector's existing proxy support (authenticated proxies via
JdkHttpClientProxyConfigurator / JdkProxyAuthenticator). Avoids pulling in
Anthropic's bundled OkHttp-based transport, which has no proxy-auth surface
through its public builder.

Sync and async execute paths share the same request/response conversion.
close() is a no-op; the JDK client needs no lifecycle. Body buffering is
fine for Messages API payloads (KB-MB); streaming isn't used.
EOF
)"
```

---

## Phase 6 — Client factory (red → green → refactor)

### Task 6.1: Write `AnthropicOnFoundryClientFactoryTest` (red)

**Files:**
- Create: `connectors/agentic-ai/src/test/java/io/camunda/connector/agenticai/azurefoundry/AnthropicOnFoundryClientFactoryTest.java`

- [ ] **Step 1: Write the test**

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.azurefoundry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.anthropic.client.AnthropicClient;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.ChatModelHttpProxySupport;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureFoundryProviderConfiguration.AzureAiFoundryModel.AnthropicModel;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureFoundryProviderConfiguration.AzureAiFoundryModel.AnthropicModel.AnthropicModelParameters;
import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.AzureAuthentication.AzureApiKeyAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.AzureAuthentication.AzureClientCredentialsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.TimeoutConfiguration;
import io.camunda.connector.agenticai.azurefoundry.langchain4j.AnthropicOnFoundryChatModel;
import io.camunda.connector.http.client.client.jdk.proxy.JdkHttpClientProxyConfigurator;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class AnthropicOnFoundryClientFactoryTest {

  private final ChatModelHttpProxySupport proxySupport =
      new ChatModelHttpProxySupport(
          ProxyConfiguration.NONE, new JdkHttpClientProxyConfigurator(ProxyConfiguration.NONE));

  private final AnthropicOnFoundryClientFactory factory =
      new AnthropicOnFoundryClientFactory(proxySupport);

  @Test
  void builds_anthropic_chat_model_with_api_key_auth() {
    AnthropicOnFoundryChatModel chatModel =
        factory.create(
            "https://my-resource.services.ai.azure.com",
            new AzureApiKeyAuthentication("api-key-value"),
            new TimeoutConfiguration(Duration.ofSeconds(30)),
            new AnthropicModel(
                "claude-sonnet-4-6", new AnthropicModelParameters(1024, 0.7, 0.9, 40)));

    assertThat(chatModel).isNotNull();
    // The adapter exposes its config for inspection in tests (see Task 7.2).
    assertThat(chatModel.modelConfig().deploymentName()).isEqualTo("claude-sonnet-4-6");
  }

  @Test
  void extracts_resource_name_from_endpoint_with_trailing_slash() {
    AnthropicOnFoundryChatModel chatModel =
        factory.create(
            "https://my-resource.services.ai.azure.com/",
            new AzureApiKeyAuthentication("api-key"),
            null,
            new AnthropicModel("claude-sonnet-4-6", null));

    // The FoundryBackend is constructed with resource="my-resource". We rely on the SDK not
    // throwing during construction as the primary signal; exact resource inspection would
    // require reflecting into the private FoundryBackend. If the SDK exposes a ClientOptions
    // accessor for the backend, assert on that. Otherwise, integration-level coverage in
    // AzureFoundryAnthropicAgentE2ETest catches resource misextraction (the WireMock URL path
    // would mismatch and the test would fail with a 404-like pattern).
    assertThat(chatModel).isNotNull();
  }

  @Test
  void builds_anthropic_chat_model_with_client_credentials_auth() {
    AnthropicOnFoundryChatModel chatModel =
        factory.create(
            "https://my-resource.services.ai.azure.com",
            new AzureClientCredentialsAuthentication(
                "client-id", "client-secret", "tenant-id", null),
            null,
            new AnthropicModel("claude-sonnet-4-6", null));

    // Bearer-token supplier construction does not hit Azure until a request is made; this
    // test only verifies the factory wires the supplier without throwing.
    assertThat(chatModel).isNotNull();
  }

  @Test
  void uses_custom_authority_host_when_provided() {
    AnthropicOnFoundryChatModel chatModel =
        factory.create(
            "https://my-resource.services.ai.azure.com",
            new AzureClientCredentialsAuthentication(
                "client-id",
                "client-secret",
                "tenant-id",
                "https://login.microsoftonline.us"),
            null,
            new AnthropicModel("claude-sonnet-4-6", null));

    assertThat(chatModel).isNotNull();
  }
}
```

- [ ] **Step 2: Run the test — red**

```bash
mvn test -pl connectors/agentic-ai -Dtest='AnthropicOnFoundryClientFactoryTest' -q
```

Expected: **FAIL** to compile (`AnthropicOnFoundryClientFactory`, `AnthropicOnFoundryChatModel` don't exist yet).

- [ ] **Step 3: Commit the failing test**

```bash
git add connectors/agentic-ai/src/test/java/io/camunda/connector/agenticai/azurefoundry/AnthropicOnFoundryClientFactoryTest.java

git commit -m "$(cat <<'EOF'
test(agentic-ai): add AnthropicOnFoundryClientFactory contract tests (red)

Contract: the factory builds AnthropicOnFoundryChatModel from an endpoint
URL, AzureAuthentication, timeouts, and AnthropicModel config. Verifies
resource-name extraction (including trailing slash), API-key and client-
credentials auth paths, and optional authorityHost. Detailed resource-name
and bearer-supplier correctness is covered end-to-end by the e2e test
(WireMock URL mismatch would catch misextraction).

Red — factory + adapter don't exist yet.
EOF
)"
```

---

### Task 6.2: Implement `AnthropicOnFoundryClientFactory` (green)

**Files:**
- Create: `connectors/agentic-ai/src/main/java/io/camunda/connector/agenticai/azurefoundry/AnthropicOnFoundryClientFactory.java`

- [ ] **Step 1: Implement**

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.azurefoundry;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.AnthropicClientImpl;
import com.anthropic.core.ClientOptions;
import com.anthropic.foundry.backends.FoundryBackend;
import com.azure.core.credential.TokenCredential;
import com.azure.identity.AuthenticationUtil;
import com.azure.identity.ClientSecretCredentialBuilder;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.ChatModelHttpProxySupport;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureFoundryProviderConfiguration.AzureAiFoundryModel.AnthropicModel;
import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.AzureAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.AzureAuthentication.AzureApiKeyAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.AzureAuthentication.AzureClientCredentialsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.TimeoutConfiguration;
import io.camunda.connector.agenticai.azurefoundry.http.JdkAnthropicHttpClient;
import io.camunda.connector.agenticai.azurefoundry.langchain4j.AnthropicOnFoundryChatModel;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.function.Supplier;
import org.apache.commons.lang3.StringUtils;

public class AnthropicOnFoundryClientFactory {

  private static final String BEARER_SCOPE = "https://cognitiveservices.azure.com/.default";

  private final ChatModelHttpProxySupport proxySupport;

  public AnthropicOnFoundryClientFactory(ChatModelHttpProxySupport proxySupport) {
    this.proxySupport = proxySupport;
  }

  public AnthropicOnFoundryChatModel create(
      String endpoint,
      AzureAuthentication authentication,
      TimeoutConfiguration timeouts,
      AnthropicModel modelConfig) {

    String resource = extractResourceName(endpoint);
    HttpClient jdkClient = buildJdkHttpClient();
    JdkAnthropicHttpClient anthropicHttp = new JdkAnthropicHttpClient(jdkClient);

    FoundryBackend backend = buildFoundryBackend(resource, authentication);

    AnthropicClient anthropicClient =
        new AnthropicClientImpl(
            ClientOptions.builder().httpClient(anthropicHttp).backend(backend).build());

    return new AnthropicOnFoundryChatModel(anthropicClient, modelConfig);
  }

  private HttpClient buildJdkHttpClient() {
    // Reuse the same proxy-aware builder chain used by langchain4j-anthropic via
    // ChatModelHttpProxySupport. The return type there wraps a builder; we unwrap by
    // building directly here (our HttpClient SPI impl doesn't go through langchain4j's
    // JdkHttpClientBuilder wrapper).
    HttpClient.Builder jdkBuilder = HttpClient.newBuilder();
    proxySupport.getJdkHttpClientProxyConfigurator().configure(jdkBuilder);
    return jdkBuilder.build();
  }

  private FoundryBackend buildFoundryBackend(String resource, AzureAuthentication authentication) {
    return switch (authentication) {
      case AzureApiKeyAuthentication key ->
          FoundryBackend.builder().resource(resource).apiKey(key.apiKey()).build();

      case AzureClientCredentialsAuthentication creds -> {
        TokenCredential credential = buildTokenCredential(creds);
        Supplier<String> bearerSupplier =
            AuthenticationUtil.getBearerTokenSupplier(credential, BEARER_SCOPE);
        yield FoundryBackend.builder()
            .resource(resource)
            .bearerTokenSupplier(bearerSupplier)
            .build();
      }
    };
  }

  private TokenCredential buildTokenCredential(AzureClientCredentialsAuthentication creds) {
    ClientSecretCredentialBuilder builder =
        new ClientSecretCredentialBuilder()
            .clientId(creds.clientId())
            .clientSecret(creds.clientSecret())
            .tenantId(creds.tenantId());
    if (StringUtils.isNotBlank(creds.authorityHost())) {
      builder.authorityHost(creds.authorityHost());
    }
    return builder.build();
  }

  private static String extractResourceName(String endpoint) {
    URI uri = URI.create(StringUtils.removeEnd(endpoint, "/"));
    String host = uri.getHost();
    if (host == null) {
      throw new IllegalArgumentException("Endpoint must be a valid URL with a host: " + endpoint);
    }
    // Host shape: <resource>.services.ai.azure.com
    int dot = host.indexOf('.');
    if (dot <= 0) {
      throw new IllegalArgumentException(
          "Endpoint must be a Foundry resource FQDN (e.g. myresource.services.ai.azure.com), got: "
              + host);
    }
    return host.substring(0, dot);
  }
}
```

**Note on `AnthropicClientImpl` constructor:** The Explore report noted the public entry point is `ClientOptions.builder()` + `new AnthropicClientImpl(options)`. Confirm this is publicly accessible; if `AnthropicClientImpl` is package-private, an alternative: check if the SDK exposes a `AnthropicClient.from(ClientOptions)` factory method or if `AnthropicOkHttpClient.builder()` is the only sanctioned entry point (in which case, a small wrapper that mimics what `AnthropicOkHttpClient.builder().build()` does internally — minus the OkHttp setup — would be needed). The SDK's `anthropic-java-client-okhttp` module's `AnthropicOkHttpClient.kt` source is the reference.

**Fallback if `AnthropicClientImpl` is not accessible:** in `build()`, create an adapter class in the same `azurefoundry` package that implements the minimum `AnthropicClient` interface needed by `AnthropicOnFoundryChatModel` (which is just `messages()` → `com.anthropic.services.messages.MessageService`), forwarding to a privately-constructed `AnthropicClientImpl` via reflection or a visible factory method in the SDK. This is an escape hatch; the first two options above are preferred.

- [ ] **Step 2: Run the factory test**

```bash
mvn test -pl connectors/agentic-ai -Dtest='AnthropicOnFoundryClientFactoryTest' -q
```

Expected: BUILD SUCCESS. If the test fails due to `AnthropicOnFoundryChatModel` class not existing, that's expected — Phase 7 adds it. Go to Phase 7 first, then come back and re-run this test.

Alternatively: create an empty stub `AnthropicOnFoundryChatModel.java` at this point so the factory compiles (Phase 7 fills it in with real logic). Empty stub:

```java
package io.camunda.connector.agenticai.azurefoundry.langchain4j;

import com.anthropic.client.AnthropicClient;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureFoundryProviderConfiguration.AzureAiFoundryModel.AnthropicModel;

public class AnthropicOnFoundryChatModel {

  private final AnthropicClient client;
  private final AnthropicModel modelConfig;

  public AnthropicOnFoundryChatModel(AnthropicClient client, AnthropicModel modelConfig) {
    this.client = client;
    this.modelConfig = modelConfig;
  }

  public AnthropicModel modelConfig() {
    return modelConfig;
  }
}
```

This stub exists just so the factory compiles and can be tested. Phase 7 replaces it with the real adapter.

- [ ] **Step 3: Run ArchUnit**

```bash
mvn test -pl connectors/agentic-ai -Dtest='ArchitectureTest' -q
```

Expected: BUILD SUCCESS. The new factory is in `azurefoundry/` (not `azurefoundry.langchain4j.*`) and has no langchain4j imports.

- [ ] **Step 4: Commit**

```bash
git add connectors/agentic-ai/src/main/java/io/camunda/connector/agenticai/azurefoundry/AnthropicOnFoundryClientFactory.java \
        connectors/agentic-ai/src/main/java/io/camunda/connector/agenticai/azurefoundry/langchain4j/AnthropicOnFoundryChatModel.java

git commit -m "$(cat <<'EOF'
feat(agentic-ai): implement AnthropicOnFoundryClientFactory

Constructs an anthropic-java AnthropicClient from the Azure AI Foundry
provider config: wires a JdkAnthropicHttpClient (for proxy-auth support) and
a FoundryBackend configured for either API-key auth (direct key) or
Entra ID (bearer-token supplier over a ClientSecretCredential, scope
https://cognitiveservices.azure.com/.default).

Resource name is extracted from the endpoint host (<resource>.services.ai.
azure.com), trimming any trailing slash. Custom authorityHost is forwarded
to the credential builder when provided.

Also adds an empty stub AnthropicOnFoundryChatModel.java so this compiles;
Phase 7 fills in the langchain4j adapter.
EOF
)"
```

---

## Phase 7 — Adapter (red → green → refactor)

### Task 7.1: Write `AnthropicOnFoundryChatModelTest` (red)

**Files:**
- Create: `connectors/agentic-ai/src/test/java/io/camunda/connector/agenticai/azurefoundry/langchain4j/AnthropicOnFoundryChatModelTest.java`

- [ ] **Step 1: Write the test**

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.azurefoundry.langchain4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.BadRequestException;
import com.anthropic.errors.InternalServerException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureFoundryProviderConfiguration.AzureAiFoundryModel.AnthropicModel;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureFoundryProviderConfiguration.AzureAiFoundryModel.AnthropicModel.AnthropicModelParameters;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.error.ConnectorInputException;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnthropicOnFoundryChatModelTest {

  private final AnthropicClient client = mock(AnthropicClient.class);
  private final MessageService messages = mock(MessageService.class);

  private final AnthropicModel modelConfig =
      new AnthropicModel(
          "claude-sonnet-4-6", new AnthropicModelParameters(1024, 0.7, null, null));

  private AnthropicOnFoundryChatModel adapter() {
    when(client.messages()).thenReturn(messages);
    return new AnthropicOnFoundryChatModel(client, modelConfig);
  }

  @Test
  void translates_text_response_to_ai_message() {
    // Given: a Message with a single text content block, end_turn stop reason.
    Message mockResponse = buildMockTextMessage("Hello!", "end_turn", 10, 3);
    when(messages.create(any(MessageCreateParams.class))).thenReturn(mockResponse);

    ChatRequest request =
        ChatRequest.builder()
            .messages(
                SystemMessage.from("You are a helpful assistant."),
                UserMessage.from("Say hi"))
            .build();

    ChatResponse response = adapter().chat(request);

    assertThat(response.aiMessage().text()).isEqualTo("Hello!");
    assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);
    assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(10);
    assertThat(response.tokenUsage().outputTokenCount()).isEqualTo(3);
  }

  @Test
  void translates_tool_use_response_to_tool_execution_requests() {
    Message mockResponse = buildMockToolUseMessage("toolu_01", "weather", "{\"city\":\"Berlin\"}");
    when(messages.create(any(MessageCreateParams.class))).thenReturn(mockResponse);

    ChatRequest request =
        ChatRequest.builder().messages(UserMessage.from("Weather?")).build();

    ChatResponse response = adapter().chat(request);

    assertThat(response.finishReason()).isEqualTo(FinishReason.TOOL_EXECUTION);
    assertThat(response.aiMessage().toolExecutionRequests()).hasSize(1);
    AiMessage ai = response.aiMessage();
    assertThat(ai.toolExecutionRequests().get(0).id()).isEqualTo("toolu_01");
    assertThat(ai.toolExecutionRequests().get(0).name()).isEqualTo("weather");
    assertThat(ai.toolExecutionRequests().get(0).arguments()).isEqualTo("{\"city\":\"Berlin\"}");
  }

  @Test
  void translates_max_tokens_stop_reason() {
    Message mockResponse = buildMockTextMessage("Truncated...", "max_tokens", 5, 1024);
    when(messages.create(any(MessageCreateParams.class))).thenReturn(mockResponse);

    ChatRequest request = ChatRequest.builder().messages(UserMessage.from("q")).build();
    ChatResponse response = adapter().chat(request);

    assertThat(response.finishReason()).isEqualTo(FinishReason.LENGTH);
  }

  @Test
  void bad_request_becomes_connector_input_exception() {
    when(messages.create(any(MessageCreateParams.class)))
        .thenThrow(mockAnthropicException(BadRequestException.class, 400, "Bad input"));

    ChatRequest request = ChatRequest.builder().messages(UserMessage.from("q")).build();

    assertThatThrownBy(() -> adapter().chat(request))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("Bad input");
  }

  @Test
  void unauthorized_becomes_connector_input_exception() {
    when(messages.create(any(MessageCreateParams.class)))
        .thenThrow(mockAnthropicException(UnauthorizedException.class, 401, "Bad key"));

    ChatRequest request = ChatRequest.builder().messages(UserMessage.from("q")).build();

    assertThatThrownBy(() -> adapter().chat(request))
        .isInstanceOf(ConnectorInputException.class);
  }

  @Test
  void rate_limit_becomes_retryable_connector_exception() {
    when(messages.create(any(MessageCreateParams.class)))
        .thenThrow(mockAnthropicException(RateLimitException.class, 429, "Too fast"));

    ChatRequest request = ChatRequest.builder().messages(UserMessage.from("q")).build();

    assertThatThrownBy(() -> adapter().chat(request))
        .isInstanceOf(ConnectorException.class)
        .isNotInstanceOf(ConnectorInputException.class);
  }

  @Test
  void internal_server_error_becomes_retryable_connector_exception() {
    when(messages.create(any(MessageCreateParams.class)))
        .thenThrow(mockAnthropicException(InternalServerException.class, 500, "boom"));

    ChatRequest request = ChatRequest.builder().messages(UserMessage.from("q")).build();

    assertThatThrownBy(() -> adapter().chat(request))
        .isInstanceOf(ConnectorException.class)
        .isNotInstanceOf(ConnectorInputException.class);
  }

  // --- helpers ---

  /** Construct a mock Anthropic Message with a single text content block. */
  private Message buildMockTextMessage(
      String text, String stopReason, int inputTokens, int outputTokens) {
    // The concrete Message API shape depends on the SDK version. The implementer fills in
    // the minimum valid construction — either via the SDK's builder or via Mockito deep stubs
    // if the Message type is final / difficult to construct in tests. Deep stubs are fine
    // here because we only read a small number of fields from Message.
    Message m = mock(Message.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
    // Configure: m.content() returns a single text block with the text;
    //            m.stopReason() returns the stop reason enum;
    //            m.usage().inputTokens() / outputTokens() return counts.
    // Implementation detail — lives in the actual test file once you see the real Message API.
    return m;
  }

  private Message buildMockToolUseMessage(String toolUseId, String name, String argsJson) {
    // Deep-stubbed Message whose content() returns a tool_use block with the given fields
    // and whose stopReason() returns TOOL_USE. Again, exact construction depends on the SDK.
    return mock(Message.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
  }

  private <T extends com.anthropic.errors.AnthropicServiceException> T mockAnthropicException(
      Class<T> type, int status, String msg) {
    T ex = mock(type);
    when(ex.statusCode()).thenReturn(status);
    when(ex.getMessage()).thenReturn(msg);
    return ex;
  }
}
```

**Note on mock Message construction:** the exact shape of `com.anthropic.models.messages.Message` (and its `content()`, `stopReason()`, `usage()` accessors) varies between SDK versions. The helpers above use Mockito deep stubs for flexibility; the implementer fills in the `when(...).thenReturn(...)` chains once the SDK's `Message` API is verified against the actual 2.26.0 Javadoc. If `Message` is `final` (Kotlin classes are final by default unless marked `open`) and cannot be mocked by Mockito, add `mockito-inline` (or upgrade Mockito to use inline mock maker) — check the agentic-ai module's existing test deps for which mock maker is configured.

- [ ] **Step 2: Run the test — red**

```bash
mvn test -pl connectors/agentic-ai -Dtest='AnthropicOnFoundryChatModelTest' -q
```

Expected: **FAIL**. `AnthropicOnFoundryChatModel.chat(...)` doesn't exist (the stub from Phase 6 has no methods).

- [ ] **Step 3: Commit the failing test**

```bash
git add connectors/agentic-ai/src/test/java/io/camunda/connector/agenticai/azurefoundry/langchain4j/AnthropicOnFoundryChatModelTest.java

git commit -m "$(cat <<'EOF'
test(agentic-ai): add AnthropicOnFoundryChatModel contract tests (red)

Contract: adapter translates langchain4j ChatRequest to Anthropic
MessageCreateParams, forwards to the mocked AnthropicClient.messages()
service, and translates Anthropic Message responses back to ChatResponse —
including AiMessage (text + tool-execution-requests), FinishReason mapping
(end_turn → STOP, tool_use → TOOL_EXECUTION, max_tokens → LENGTH), and
TokenUsage. Each AnthropicServiceException subtype maps to the correct
ConnectorException / ConnectorInputException per Milestone 1 spec.

Red — adapter still a stub from Phase 6.
EOF
)"
```

---

### Task 7.2: Implement `AnthropicOnFoundryChatModel` adapter (green → refactor)

**Files:**
- Modify (replace stub): `connectors/agentic-ai/src/main/java/io/camunda/connector/agenticai/azurefoundry/langchain4j/AnthropicOnFoundryChatModel.java`

- [ ] **Step 1: Replace the stub with the full implementation**

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.azurefoundry.langchain4j;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.errors.InternalServerException;
import com.anthropic.errors.NotFoundException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.errors.UnprocessableEntityException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureFoundryProviderConfiguration.AzureAiFoundryModel.AnthropicModel;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureFoundryProviderConfiguration.AzureAiFoundryModel.AnthropicModel.AnthropicModelParameters;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.error.ConnectorInputException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AnthropicOnFoundryChatModel implements ChatModel {

  private final AnthropicClient client;
  private final AnthropicModel modelConfig;

  public AnthropicOnFoundryChatModel(AnthropicClient client, AnthropicModel modelConfig) {
    this.client = client;
    this.modelConfig = modelConfig;
  }

  /** Exposed for tests. Not part of the ChatModel contract. */
  public AnthropicModel modelConfig() {
    return modelConfig;
  }

  @Override
  public ChatResponse chat(ChatRequest request) {
    try {
      MessageCreateParams params = toAnthropicParams(request);
      Message response = client.messages().create(params);
      return toChatResponse(response);
    } catch (AnthropicException ex) {
      throw translateException(ex);
    }
  }

  // --- Request conversion (langchain4j → Anthropic) ---

  private MessageCreateParams toAnthropicParams(ChatRequest request) {
    MessageCreateParams.Builder builder =
        MessageCreateParams.builder().model(modelConfig.deploymentName());

    applyParameters(builder);
    applySystemPrompt(builder, request);
    applyMessages(builder, request);
    applyTools(builder, request);

    return builder.build();
  }

  private void applyParameters(MessageCreateParams.Builder builder) {
    AnthropicModelParameters params = modelConfig.parameters();
    // max_tokens is required by the Anthropic API; use a reasonable default if unset.
    int maxTokens = Optional.ofNullable(params)
        .map(AnthropicModelParameters::maxTokens)
        .orElse(4096);
    builder.maxTokens(maxTokens);
    if (params == null) return;
    if (params.temperature() != null) builder.temperature(params.temperature());
    if (params.topP() != null) builder.topP(params.topP());
    if (params.topK() != null) builder.topK(params.topK().longValue());
  }

  private void applySystemPrompt(MessageCreateParams.Builder builder, ChatRequest request) {
    // langchain4j merges system messages at the top of the messages list.
    String systemText = request.messages().stream()
        .filter(m -> m instanceof SystemMessage)
        .map(m -> ((SystemMessage) m).text())
        .reduce((a, b) -> a + "\n\n" + b)
        .orElse(null);
    if (systemText != null) builder.system(systemText);
  }

  private void applyMessages(MessageCreateParams.Builder builder, ChatRequest request) {
    List<ChatMessage> nonSystem = request.messages().stream()
        .filter(m -> !(m instanceof SystemMessage))
        .toList();

    for (ChatMessage m : nonSystem) {
      if (m instanceof UserMessage u) {
        // Text-only user messages for M2 v1 scope (per spec decision Q5).
        builder.addUserMessage(u.singleText());
      } else if (m instanceof AiMessage a) {
        // Assistant messages may contain text + tool calls; translate each in order.
        addAssistantMessage(builder, a);
      } else if (m instanceof ToolExecutionResultMessage tr) {
        // Anthropic represents tool results as user-message content blocks with type=tool_result.
        addToolResult(builder, tr);
      }
      // Other message types (ImageContent, etc.) are out of scope per spec decision Q5.
    }
  }

  private void addAssistantMessage(MessageCreateParams.Builder builder, AiMessage ai) {
    // The exact SDK builder API for mixed content (text + tool_use blocks) in assistant
    // messages varies. The implementer fills this in using the 2.26.0 Javadoc — the shape is:
    //   MessageParam(role=assistant, content=[TextBlock | ToolUseBlock, ...])
    // added via builder.addAssistantMessage(...) or builder.addMessage(MessageParam.builder()...).
    // If the SDK offers only simple addAssistantMessage(String), use that for text-only; for
    // tool_use blocks use the lower-level builder.addMessage(assistantMessageParam) path.
    if (ai.toolExecutionRequests() == null || ai.toolExecutionRequests().isEmpty()) {
      builder.addAssistantMessage(Optional.ofNullable(ai.text()).orElse(""));
      return;
    }
    // TODO at implementation time: use SDK's structured assistant-message builder to attach
    // both optional text and one-or-more tool_use blocks. See SDK's MessageParam class.
    throw new UnsupportedOperationException(
        "Structured assistant messages with tool_use blocks — fill in with SDK's MessageParam API");
  }

  private void addToolResult(MessageCreateParams.Builder builder, ToolExecutionResultMessage tr) {
    // Anthropic expects a user-role message with a single tool_result content block:
    //   {"role": "user", "content": [{"type": "tool_result", "tool_use_id": "...", "content": "..."}]}
    // Implementer: use SDK's structured MessageParam + ToolResultBlock builders.
    throw new UnsupportedOperationException(
        "Tool-result messages — fill in with SDK's ToolResultBlock API");
  }

  private void applyTools(MessageCreateParams.Builder builder, ChatRequest request) {
    if (request.parameters() == null
        || request.parameters().toolSpecifications() == null
        || request.parameters().toolSpecifications().isEmpty()) {
      return;
    }
    // Convert each ToolSpecification → Anthropic ToolDefinition (name, description, input_schema).
    // The SDK exposes a tool builder via MessageCreateParams.Builder.addTool(...) or similar.
    // Implementer: consult SDK's Tool / InputSchema types for exact shape. input_schema is
    // JSON Schema object {type, properties, required, ...} — convert from langchain4j's
    // JsonObjectSchema by serializing to JsonValue (SDK helper).
    throw new UnsupportedOperationException(
        "Tool specifications — fill in with SDK's Tool / InputSchema API");
  }

  // --- Response conversion (Anthropic → langchain4j) ---

  private ChatResponse toChatResponse(Message response) {
    AiMessage aiMessage = buildAiMessage(response);
    FinishReason finishReason = mapStopReason(response);
    TokenUsage tokenUsage = mapUsage(response);

    return ChatResponse.builder()
        .aiMessage(aiMessage)
        .finishReason(finishReason)
        .tokenUsage(tokenUsage)
        .build();
  }

  private AiMessage buildAiMessage(Message response) {
    // Walk Message.content() (list of ContentBlocks). For text blocks, concatenate text.
    // For tool_use blocks, build ToolExecutionRequest(id, name, arguments-as-json-string).
    StringBuilder textBuf = new StringBuilder();
    List<ToolExecutionRequest> toolCalls = new ArrayList<>();

    // Pseudo: response.content().forEach(block -> {
    //   block.text().ifPresent(t -> textBuf.append(t.text()));
    //   block.toolUse().ifPresent(u ->
    //       toolCalls.add(ToolExecutionRequest.builder()
    //           .id(u.id())
    //           .name(u.name())
    //           .arguments(u.input().toString())   // JsonValue toString() yields JSON
    //           .build()));
    // });
    // Implementer: replace with the SDK's actual ContentBlock visitor API.

    if (toolCalls.isEmpty()) {
      return AiMessage.from(textBuf.toString());
    }
    return AiMessage.builder()
        .text(textBuf.isEmpty() ? null : textBuf.toString())
        .toolExecutionRequests(toolCalls)
        .build();
  }

  private FinishReason mapStopReason(Message response) {
    // Pseudo: String stopReason = response.stopReason().map(Enum::name).orElse("UNKNOWN");
    // Maps:
    //   END_TURN       → STOP
    //   TOOL_USE       → TOOL_EXECUTION
    //   MAX_TOKENS     → LENGTH
    //   STOP_SEQUENCE  → STOP
    //   others         → OTHER
    // Implementer: once the SDK's StopReason type is confirmed, switch on it.
    return FinishReason.OTHER;
  }

  private TokenUsage mapUsage(Message response) {
    // Pseudo: Usage usage = response.usage();
    // return new TokenUsage(usage.inputTokens(), usage.outputTokens());
    return new TokenUsage(0, 0);
  }

  // --- Exception translation ---

  private RuntimeException translateException(AnthropicException ex) {
    if (ex instanceof BadRequestException
        || ex instanceof UnauthorizedException
        || ex instanceof PermissionDeniedException
        || ex instanceof NotFoundException
        || ex instanceof UnprocessableEntityException) {
      return new ConnectorInputException(ex);
    }
    if (ex instanceof RateLimitException || ex instanceof InternalServerException) {
      return new ConnectorException(
          String.valueOf(((AnthropicServiceException) ex).statusCode()), ex.getMessage(), ex);
    }
    // Default: retryable transport / unknown service error.
    return new ConnectorException("ANTHROPIC_ERROR", ex.getMessage(), ex);
  }
}
```

**Impl note on the `TODO`-flagged conversions:** the four methods `addAssistantMessage`, `addToolResult`, `applyTools`, `buildAiMessage`, and `mapStopReason` require the exact SDK API shape for builders. The plan intentionally leaves these as pseudo-code with precise semantic instructions because they depend on the 2.26.0 SDK's concrete API, which must be read from the Javadoc at implementation time. The implementer fills them in using the SDK's real methods — no new design decisions are needed. If any fails to resolve against the SDK API, the test failure message will reveal the gap.

- [ ] **Step 2: Run the adapter test**

```bash
mvn test -pl connectors/agentic-ai -Dtest='AnthropicOnFoundryChatModelTest' -q
```

Expected: BUILD SUCCESS, all 7 test methods pass.

If some pass and others fail: the TODO-marked conversions aren't complete. Walk through the failing tests one by one, using the SDK Javadoc to fill in the real builder chains.

- [ ] **Step 3: Run ArchUnit**

```bash
mvn test -pl connectors/agentic-ai -Dtest='ArchitectureTest' -q
```

Expected: BUILD SUCCESS. The adapter lives in `azurefoundry.langchain4j.*` (the only package allowed to import `dev.langchain4j.*`), and that's the only place the langchain4j types are used.

- [ ] **Step 4: Run full module tests**

```bash
mvn test -pl connectors/agentic-ai -q
```

Expected: BUILD SUCCESS. All 1250+ tests pass (the ~9 new Foundry tests are added).

- [ ] **Step 5: Commit**

```bash
git add connectors/agentic-ai/src/main/java/io/camunda/connector/agenticai/azurefoundry/langchain4j/AnthropicOnFoundryChatModel.java

git commit -m "$(cat <<'EOF'
feat(agentic-ai): implement AnthropicOnFoundryChatModel adapter

Langchain4j ChatModel adapter that wraps an anthropic-java AnthropicClient.
Translates ChatRequest → MessageCreateParams (model, system, messages,
tools, max_tokens, temperature, topP, topK) and Message → ChatResponse
(AiMessage with text + tool-execution-requests; FinishReason from
StopReason; TokenUsage from Usage — includes cache tokens when present).

Exception translation per spec: client-side errors (400, 401, 403, 404,
422) → ConnectorInputException; retryable service errors (429, 5xx) →
ConnectorException with status code; transport/unknown → ConnectorException
with a stable "ANTHROPIC_ERROR" code.

Only imports dev.langchain4j.* in this subpackage; enforced by ArchUnit.
EOF
)"
```

---

## Phase 8 — Factory dispatch + Spring wiring (red → green)

### Task 8.1: Update `ChatModelFactoryTest` for Foundry dispatch (red)

**Files:**
- Modify: `connectors/agentic-ai/src/test/java/io/camunda/connector/agenticai/aiagent/framework/langchain4j/ChatModelFactoryTest.java`

- [ ] **Step 1: Add nested test classes**

Append to `ChatModelFactoryTest.java` (inside the top-level class, as new `@Nested` classes):

```java
  @Nested
  @DisplayName("Azure AI Foundry – Anthropic")
  class AzureFoundryAnthropicChatModelFactoryTest {

    @Test
    void creates_AnthropicOnFoundryChatModel_for_anthropic_model_family() {
      AnthropicOnFoundryClientFactory mockFactory = mock(AnthropicOnFoundryClientFactory.class);
      AnthropicOnFoundryChatModel mockModel = mock(AnthropicOnFoundryChatModel.class);
      when(mockFactory.create(anyString(), any(), any(), any(AnthropicModel.class)))
          .thenReturn(mockModel);

      // Re-create factory with the mocked AnthropicOnFoundryClientFactory injected.
      ChatModelFactory factory =
          new ChatModelFactoryImpl(agenticAiConfigProperties, proxySupport, mockFactory);

      AzureFoundryProviderConfiguration configuration =
          new AzureFoundryProviderConfiguration(
              new AzureAiFoundryConnection(
                  "https://my-resource.services.ai.azure.com",
                  new AzureApiKeyAuthentication("key"),
                  null,
                  new AnthropicModel(
                      "claude-sonnet-4-6",
                      new AnthropicModelParameters(1024, 0.7, null, null))));

      ChatModel result = factory.createChatModel(configuration);

      assertThat(result).isSameAs(mockModel);
      verify(mockFactory)
          .create(
              eq("https://my-resource.services.ai.azure.com"),
              eq(new AzureApiKeyAuthentication("key")),
              isNull(),
              any(AnthropicModel.class));
    }
  }

  @Nested
  @DisplayName("Azure AI Foundry – OpenAI")
  class AzureFoundryOpenAiChatModelFactoryTest {

    @Test
    void delegates_openai_family_to_shared_azure_openai_builder() {
      // This uses the existing AzureOpenAiChatModel construction path via the
      // buildAzureOpenAiChatModel helper (Phase 4). Assert that dispatching on OpenAiModel
      // produces an AzureOpenAiChatModel (not AnthropicOnFoundryChatModel).

      AzureFoundryProviderConfiguration configuration =
          new AzureFoundryProviderConfiguration(
              new AzureAiFoundryConnection(
                  "https://my-resource.services.ai.azure.com",
                  new AzureApiKeyAuthentication("key"),
                  null,
                  new OpenAiModel(
                      "gpt-4o", new OpenAiModelParameters(2048, 1.0, null))));

      ChatModel result = factory.createChatModel(configuration);

      assertThat(result).isInstanceOf(AzureOpenAiChatModel.class);
    }
  }
```

(Imports needed: `AnthropicOnFoundryClientFactory`, `AnthropicOnFoundryChatModel`, `AzureFoundryProviderConfiguration` + nested types, `AzureApiKeyAuthentication`, and Mockito static imports `anyString`, `any`, `isNull`, `eq`, `mock`, `when`, `verify`.)

- [ ] **Step 2: Run the tests — red**

```bash
mvn test -pl connectors/agentic-ai -Dtest='ChatModelFactoryTest' -q
```

Expected: **FAIL**. The `ChatModelFactoryImpl` constructor doesn't yet take an `AnthropicOnFoundryClientFactory`, and the stub branch throws `ConnectorInputException` instead of dispatching.

- [ ] **Step 3: Commit the failing test**

```bash
git add connectors/agentic-ai/src/test/java/io/camunda/connector/agenticai/aiagent/framework/langchain4j/ChatModelFactoryTest.java

git commit -m "$(cat <<'EOF'
test(agentic-ai): add Azure AI Foundry factory dispatch tests (red)

Verifies ChatModelFactoryImpl dispatches AzureFoundryProviderConfiguration
correctly:
  - modelFamily=anthropic → AnthropicOnFoundryClientFactory.create(...)
  - modelFamily=openai → shared buildAzureOpenAiChatModel(...) helper

Red — the factory's Foundry branch is still the Milestone 1
UnsupportedOperationException/ConnectorInputException stub.
EOF
)"
```

---

### Task 8.2: Replace stub in `ChatModelFactoryImpl`; add Spring wiring (green)

**Files:**
- Modify: `connectors/agentic-ai/src/main/java/io/camunda/connector/agenticai/aiagent/framework/langchain4j/ChatModelFactoryImpl.java`
- Modify: `connectors/agentic-ai/src/main/java/io/camunda/connector/agenticai/autoconfigure/AgenticAiConnectorsAutoConfiguration.java` (or the framework-specific sub-configuration class `AgenticAiLangchain4JFrameworkConfiguration` if that's where `ChatModelFactoryImpl` is wired — verify by grepping `@Bean.*ChatModelFactoryImpl` in both).

- [ ] **Step 1: Add constructor parameter to `ChatModelFactoryImpl` and replace the stub branch**

In `ChatModelFactoryImpl.java`:

1. Add field + constructor parameter for `AnthropicOnFoundryClientFactory`:

```java
private final AnthropicOnFoundryClientFactory anthropicOnFoundryClientFactory;

public ChatModelFactoryImpl(
    AgenticAiConnectorsConfigurationProperties agenticAiConnectorsConfigurationProperties,
    ChatModelHttpProxySupport proxySupport,
    AnthropicOnFoundryClientFactory anthropicOnFoundryClientFactory) {
  this.chatModelProperties =
      agenticAiConnectorsConfigurationProperties.aiagent().chatModel();
  this.proxySupport = proxySupport;
  this.anthropicOnFoundryClientFactory = anthropicOnFoundryClientFactory;
}
```

2. Add a new `createAzureFoundryChatModel(...)` method:

```java
private ChatModel createAzureFoundryChatModel(AzureFoundryProviderConfiguration configuration) {
  AzureAiFoundryConnection conn = configuration.azureAiFoundry();
  return switch (conn.model()) {
    case AnthropicModel anthropic ->
        anthropicOnFoundryClientFactory.create(
            conn.endpoint(), conn.authentication(), conn.timeouts(), anthropic);

    case OpenAiModel openai -> {
      OpenAiModelParameters params = openai.parameters();
      yield buildAzureOpenAiChatModel(
          conn.endpoint(),
          conn.authentication(),
          conn.timeouts(),
          openai.deploymentName(),
          params != null ? params.maxTokens() : null,
          params != null ? params.temperature() : null,
          params != null ? params.topP() : null);
    }
  };
}
```

3. Replace the M1 stub case in `createChatModel(...)`:

Change:
```java
case AzureFoundryProviderConfiguration azureAiFoundry ->
    throw new ConnectorInputException(
        new IllegalStateException(
            "Azure AI Foundry runtime is not yet implemented (planned for Milestone 2). "
                + "This provider option is currently available for UI/template demonstration only."));
```

To:
```java
case AzureFoundryProviderConfiguration azureAiFoundry ->
    createAzureFoundryChatModel(azureAiFoundry);
```

Add imports for `AnthropicOnFoundryClientFactory`, `AzureAiFoundryConnection`, `AnthropicModel`, `OpenAiModel`, `OpenAiModelParameters`.

- [ ] **Step 2: Add Spring bean in `AgenticAiConnectorsAutoConfiguration`**

Find the class that `@Bean`-defines `ChatModelFactoryImpl`. Typically it's in `AgenticAiConnectorsAutoConfiguration.java` or its imported `AgenticAiLangchain4JFrameworkConfiguration`. Add a new `@Bean` method and update the `ChatModelFactoryImpl` bean to take the new factory as a parameter:

```java
@Bean
@ConditionalOnMissingBean
public AnthropicOnFoundryClientFactory anthropicOnFoundryClientFactory(
    ChatModelHttpProxySupport proxySupport) {
  return new AnthropicOnFoundryClientFactory(proxySupport);
}

@Bean
@ConditionalOnMissingBean
public ChatModelFactory chatModelFactory(
    AgenticAiConnectorsConfigurationProperties agenticAiConnectorsConfigurationProperties,
    ChatModelHttpProxySupport proxySupport,
    AnthropicOnFoundryClientFactory anthropicOnFoundryClientFactory) {
  return new ChatModelFactoryImpl(
      agenticAiConnectorsConfigurationProperties,
      proxySupport,
      anthropicOnFoundryClientFactory);
}
```

(If the existing `chatModelFactory(...)` bean method already exists, just add the new parameter to it and add the `anthropicOnFoundryClientFactory` bean method above it.)

- [ ] **Step 3: Run the factory unit test**

```bash
mvn test -pl connectors/agentic-ai -Dtest='ChatModelFactoryTest' -q
```

Expected: BUILD SUCCESS. The Foundry dispatch tests now pass.

- [ ] **Step 4: Run the full module test suite**

```bash
mvn test -pl connectors/agentic-ai -q
```

Expected: BUILD SUCCESS, all 1250+ tests pass. `ArchitectureTest` continues to pass (the new `AnthropicOnFoundryClientFactory` import in `ChatModelFactoryImpl` is fine — it's in `..azurefoundry..` which `..aiagent..` is allowed to consume, one-directional).

- [ ] **Step 5: Commit**

```bash
git add connectors/agentic-ai/src/main/java/io/camunda/connector/agenticai/aiagent/framework/langchain4j/ChatModelFactoryImpl.java \
        connectors/agentic-ai/src/main/java/io/camunda/connector/agenticai/autoconfigure/AgenticAiConnectorsAutoConfiguration.java

git commit -m "$(cat <<'EOF'
feat(agentic-ai): wire real Azure AI Foundry dispatch in ChatModelFactoryImpl

Replaces the Milestone 1 UnsupportedOperationException/ConnectorInputException
stub with the real dispatch logic:
  - AnthropicModel  → AnthropicOnFoundryClientFactory.create(...)
  - OpenAiModel     → shared buildAzureOpenAiChatModel(...) helper
    (same Azure OpenAI builder used by the existing Azure OpenAI provider)

Registers AnthropicOnFoundryClientFactory as a Spring bean and injects it
into ChatModelFactoryImpl alongside the existing AgenticAiConnectors
ConfigurationProperties and ChatModelHttpProxySupport beans.

End-to-end: selecting "Azure AI Foundry" as the AI Agent provider in the
Modeler and running a process now drives the full Anthropic Messages API
flow (for Anthropic family) or the Azure OpenAI chat-completions flow
(for OpenAI family) against the configured Foundry endpoint.
EOF
)"
```

---

### Task 8.3: Verify Phase 1 e2e tests now pass

**Files:**
- None (verification only)

- [ ] **Step 1: Run all three Foundry-related e2e tests**

```bash
cd /Users/dmitri.nikonov/Development/camunda/connectors
mvn test -pl connectors-e2e-test/connectors-e2e-test-agentic-ai \
    -Dtest='AzureFoundryAnthropicAgentE2ETest,AzureFoundryOpenAiAgentE2ETest,AzureOpenAiLegacyCompatibilityE2ETest'
```

Expected: BUILD SUCCESS. All three tests pass. The contract loop closes here — Phase 1's red tests are now green, confirming the runtime matches the contract.

- [ ] **Step 2: If any fail, inspect logs**

Common failure modes:
- **`AzureFoundryAnthropicAgentE2ETest` fails with a 404:** the `AnthropicOnFoundryClientFactory.extractResourceName(...)` is producing the wrong resource name. The SDK constructs the URL as `https://<resource>.services.ai.azure.com/anthropic/v1/messages`; WireMock's base URL is `http://localhost:<port>`, so the SDK won't hit the right host unless the `FoundryBackend.baseUrl(...)` override is used. Check whether `FoundryBackend.builder()` has a `baseUrl` override for testing; if so, the factory may need an opt-in override path. Alternatively, the e2e test can use WireMock's HTTPS mode with a resource-matching hostname via `/etc/hosts` injection — ugly but works. Document which path in an update to the plan.
- **Adapter test failures leak into e2e:** TODO-marked conversions in `AnthropicOnFoundryChatModel` might return empty bodies or wrong stop reasons. The test will surface specific assertion failures.
- **`AzureFoundryOpenAiAgentE2ETest` fails with auth issues:** the Azure OpenAI path uses the Azure SDK's HTTP stack (not our JdkAnthropicHttpClient), so proxy/endpoint config is separate. Verify the existing Azure OpenAI e2e test works first, then adapt whatever mocking pattern it uses for Foundry-OpenAI.

The e2e plan deliberately flags these as implementation-time discoveries, not design gaps. The fix path is test-by-test debugging, not design changes.

- [ ] **Step 3: No commit needed — verification only**

If all three tests pass, continue to Phase 9. If any fail, the failure is treated as a bug in the current implementation: fix the implementation, commit the fix (conventional-commits style `fix(agentic-ai): ...`), re-run, iterate.

---

## Phase 9 — Documentation + ADR

### Task 9.1: Write ADR 004

**Files:**
- Create: `connectors/agentic-ai/docs/adr/004-azure-ai-foundry-provider.md`

- [ ] **Step 1: Write the ADR**

Follow the style of `connectors/agentic-ai/docs/adr/001-replace-mcp-client-framework.md` (verify the exact section headers by reading that file). Target content:

```markdown
# Azure AI Foundry Provider

* Deciders: Agentic AI team
* Date: 2026-04-24

## Status

Implemented

## Context and Problem Statement

Enterprise customers on Azure-first procurement or EU data-residency constraints access
Claude models exclusively via Azure AI Foundry. The AI Agent connector's existing provider
set could not target Foundry's Anthropic endpoint correctly:
- The direct Anthropic provider speaks the Anthropic Messages API but with direct-Anthropic
  auth conventions and URL shape, which the Azure-hosted endpoint does not accept.
- The Azure OpenAI provider speaks OpenAI Chat Completions, which Claude-on-Foundry does
  not expose.

Customers forced to use Foundry could not use the AI Agent with Claude at all.

## Decision Drivers

* Urgency: first-class blocker for a significant enterprise segment.
* Future-proofing: we want flexibility to eventually move off langchain4j while keeping
  provider implementations intact.
* Consistency: unify the Azure surface (OpenAI and Anthropic both live on the same
  Foundry resource) under a single provider option in the element template, rather than
  adding a second parallel provider alongside the existing Azure OpenAI.

## Considered Options

1. **Roll our own Anthropic Messages HTTP client** (original proposal).
2. **Use Anthropic's official `anthropic-java-foundry` SDK** with its default OkHttp transport.
3. **Use `anthropic-java-foundry` SDK with a custom JDK-backed HttpClient SPI.** (chosen)
4. **Contribute a `langchain4j-azure-anthropic` module upstream.** (deferred)

## Decision Outcome

Chosen: Option 3 — Anthropic's official SDK (`com.anthropic:anthropic-java-foundry:2.26.0`)
configured with a custom `HttpClient` SPI implementation backed by the JDK's
`java.net.http.HttpClient`.

### Positive Consequences

* **Wire format correctness is Anthropic's problem**, not ours. The SDK handles the Messages
  API, Foundry-specific auth (API key via `api-key` header; Entra ID via bearer supplier),
  and any future protocol tweaks.
* **Authenticated proxy support preserved.** The connector's existing JDK-HttpClient-based
  proxy config (reading `HTTP_PROXY` / `HTTPS_PROXY` env vars with `user:pass@host:port`
  syntax) continues to work for Foundry traffic. Option 2 would have forfeited this because
  `AnthropicOkHttpClient.builder()` doesn't expose OkHttp's proxy-authenticator.
* **Langchain4j-decoupled.** The SDK integration lives in packages that don't import
  `dev.langchain4j.*`. A future langchain4j replacement only requires rewriting the
  `azurefoundry.langchain4j.*` adapter subpackage.
* **OpenAI-on-Foundry reuses the existing `langchain4j-azure-open-ai` path** via a shared
  `buildAzureOpenAiChatModel(...)` helper — no duplicate implementation, no new
  dependency.

### Negative Consequences

* **New runtime dependency** (`com.anthropic:anthropic-java-core` + `anthropic-java-foundry`,
  plus Kotlin stdlib transitive). Approximately 3 MB of additional JARs.
* **~150 lines of custom transport code** to implement the HttpClient SPI, plus its tests.
  This is a maintenance cost vs. using the default OkHttp transport; however, it's
  mechanical and well-isolated.
* **Some Anthropic SDK API shape is verified at implementation time**, not at plan time,
  because the SDK is semi-recent and its Javadoc isn't uniformly accurate. Tradeoff: small
  implementation-phase research, no design changes.

## Architectural Enforcement

ArchUnit rules in `connectors/agentic-ai/src/test/java/io/camunda/connector/agenticai/
azurefoundry/ArchitectureTest.java`:

1. Only classes in `..azurefoundry.langchain4j..` may depend on `dev.langchain4j..`.
2. Classes in `..azurefoundry..` must not depend on agent-framework internals
   (`..aiagent.agent..`, `..aiagent.memory..`, `..adhoctoolsschema..`).

## Related

* Milestone 1 delivered the element-template-only UI (branch `agentic-ai/azure-foundry-provider`).
* Milestone 2 delivered the runtime (this ADR documents that).
* Spec: `connectors/agentic-ai/docs/superpowers/specs/2026-04-23-azure-ai-foundry-provider-design.md`.
* Original issue: camunda/connectors#6993.
```

- [ ] **Step 2: Commit**

```bash
git add connectors/agentic-ai/docs/adr/004-azure-ai-foundry-provider.md

git commit -m "$(cat <<'EOF'
docs(agentic-ai): add ADR 004 for the Azure AI Foundry provider

Documents the Milestone 2 architectural decision to use Anthropic's official
anthropic-java-foundry SDK (not a hand-rolled HTTP client, not langchain4j
fork) with a custom JDK-backed HttpClient SPI to preserve the connector's
existing authenticated-proxy support.

Covers context (enterprise Foundry blocker), options evaluated,
consequences (dependency cost, transport code maintenance, API-shape
discovery tradeoff), and the ArchUnit-enforced langchain4j decoupling.
EOF
)"
```

---

### Task 9.2: Update `AGENTS.md` / `docs/reference/ai-agent.md`

**Files:**
- Modify: `connectors/agentic-ai/AGENTS.md` (provider-list and architecture sections)
- Modify: `connectors/agentic-ai/docs/reference/ai-agent.md` (core-agent reference details)

- [ ] **Step 1: Update `AGENTS.md`**

Find the architecture section or wherever the provider list implicitly lives (check via `grep -n "Provider\|Anthropic\|Azure OpenAI" connectors/agentic-ai/AGENTS.md`). Add brief mention of the Foundry provider's two-family routing (Anthropic via anthropic-java SDK, OpenAI delegated to existing langchain4j-azure-open-ai) under the core-components or extension-points section.

Keep this concise (2-3 sentences) — `AGENTS.md` is an orientation doc, not a deep reference.

- [ ] **Step 2: Update `docs/reference/ai-agent.md`**

Add a subsection under "Core Components" or "Framework" (whichever the file uses) describing:
- `AnthropicOnFoundryClientFactory` — config → AnthropicClient construction
- `JdkAnthropicHttpClient` — HttpClient SPI impl for proxy support
- `AnthropicOnFoundryChatModel` — langchain4j adapter
- Sealed `AzureAiFoundryModel` → family dispatch in `ChatModelFactoryImpl`
- ArchUnit-enforced boundary

Cross-link to ADR 004.

- [ ] **Step 3: Commit**

```bash
git add connectors/agentic-ai/AGENTS.md connectors/agentic-ai/docs/reference/ai-agent.md

git commit -m "$(cat <<'EOF'
docs(agentic-ai): document the Azure AI Foundry runtime integration

Adds provider and architecture coverage for the Milestone 2 runtime:
  - AGENTS.md: brief provider-list + dispatch summary
  - ai-agent.md: detailed reference for the azurefoundry package
    (client factory, HttpClient SPI, adapter, sealed model family,
    ArchUnit rules)

Links to ADR 004 for decision context.
EOF
)"
```

---

## Phase 10 — Final verification

(No new commits — verification only before handing off for review/merge.)

- [ ] **Step 1: Full module test suite**

```bash
cd /Users/dmitri.nikonov/Development/camunda/connectors
mvn test -pl connectors/agentic-ai
```

Expected: BUILD SUCCESS, all tests green. Headcount should now be ~1260 (M1 delivered 1241; M2 adds ~9 + ArchUnit: `JdkAnthropicHttpClientTest` (5), `AnthropicOnFoundryClientFactoryTest` (4), `AnthropicOnFoundryChatModelTest` (7), `AzureFoundryProviderConfigurationDeserializationTest` (4), `ArchitectureTest` (2), `ChatModelFactoryTest` additions (2) — roughly 24 new).

- [ ] **Step 2: All three Foundry-related e2e tests**

```bash
mvn test -pl connectors-e2e-test/connectors-e2e-test-agentic-ai \
    -Dtest='AzureFoundryAnthropicAgentE2ETest,AzureFoundryOpenAiAgentE2ETest,AzureOpenAiLegacyCompatibilityE2ETest'
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Manual smoke test**

- Deploy a real Foundry resource with a Claude model (Azure portal).
- Configure a local BPMN process with the new "Azure AI Foundry" provider, `modelFamily=anthropic`, your deployment name, your API key.
- Run the process against a real Claude invocation.
- Confirm: the agent loop completes, tool calls round-trip correctly, the final agent response contains the expected content.

This is not automated in CI (per the spec); it's the PR author's responsibility.

- [ ] **Step 4: Review the commit log**

```bash
git log --oneline main..HEAD
```

Expected log shape (exact count depends on how red-commits-then-green-commits are split):

```
<sha> docs(agentic-ai): document the Azure AI Foundry runtime integration
<sha> docs(agentic-ai): add ADR 004 for the Azure AI Foundry provider
<sha> feat(agentic-ai): wire real Azure AI Foundry dispatch in ChatModelFactoryImpl
<sha> test(agentic-ai): add Azure AI Foundry factory dispatch tests (red)
<sha> feat(agentic-ai): implement AnthropicOnFoundryChatModel adapter
<sha> test(agentic-ai): add AnthropicOnFoundryChatModel contract tests (red)
<sha> feat(agentic-ai): implement AnthropicOnFoundryClientFactory
<sha> test(agentic-ai): add AnthropicOnFoundryClientFactory contract tests (red)
<sha> feat(agentic-ai): implement JDK-backed HttpClient for anthropic-java SDK
<sha> test(agentic-ai): add JdkAnthropicHttpClient contract tests (red)
<sha> refactor(agentic-ai): extract shared AzureOpenAi builder helper
<sha> test(agentic-ai): cover Azure AI Foundry provider JSON deserialization
<sha> test(agentic-ai): add ArchUnit guard for Foundry package boundary
<sha> deps: add anthropic-java SDK and ArchUnit for Azure AI Foundry provider
<sha> test(e2e): add Azure OpenAI legacy-compatibility safety net
<sha> test(e2e): add Azure AI Foundry OpenAI agent e2e contract (red)
<sha> test(e2e): add Azure AI Foundry Anthropic agent e2e contract (red)
<sha> refactor(agentic-ai): polish Azure AI Foundry model-family form          (from M1)
<sha> docs(agentic-ai): bump AI Agent template version index to 11             (from M1)
<sha> feat(agentic-ai): add Azure AI Foundry provider element template (UI)    (from M1)
<sha> refactor(agentic-ai): extract AzureAuthentication into shared package    (from M1)
```

All commits conventional-commits, no `Co-Authored-By:` trailers. If any commit needs message cleanup before the PR, use `git rebase -i` to squash or reword at that point.

- [ ] **Step 5: (Optional) Squash before PR**

If your team prefers a tighter PR history, consider squashing the red+green commit pairs (e.g., `test: add X (red)` + `feat: implement X` → single `feat: add X`). The current split keeps the TDD cadence visible, which is useful for review; the merged commit can be squashed via the PR's merge-commit step.

---

## Scope reminders

**In scope:**
- Native Anthropic-on-Foundry via `anthropic-java-foundry` SDK + custom JDK-backed HttpClient SPI
- OpenAI-on-Foundry via delegation to existing `langchain4j-azure-open-ai`
- ArchUnit rules enforcing SDK-layer decoupling
- Unit + e2e test coverage (WireMock-based) per the TDD sequence
- ADR documenting the decision
- Documentation updates

**Explicitly deferred (not this PR):**
- Azure OpenAI provider deprecation labels / template description changes
- OpenAI Responses API native support
- Anthropic vision / prompt caching / extended thinking adapter wiring
- Managed Identity / ROPC auth methods
- Live Foundry integration test in CI
- Contributing upstream to langchain4j
