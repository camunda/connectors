# Native-Provider Real-API Acceptance IT — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a local-only, cross-provider real-API acceptance IT that exercises the native own-LLM-layer path end-to-end (BPMN → v2 template → agentic loop → `agent` output variable) across five behavioural scenarios, asserting on observable output only so the same scenarios port to OpenAI later.

**Architecture:** A new sibling IT (`NativeProviderAcceptanceIT`) modelled on `DocumentToolCallResultsIT`'s harness (standalone `@SpringBootTest(TestConnectorRuntimeApplication)` + `@CamundaSpringProcessTest` + `@WireMockTest` + `ElementTemplate`/`BpmnFile.apply`), but repointed to the **v2 native template** (`agenticai-ai-agent-subprocess.v2.json`) and `configuration.*` property namespace so it drives the native code, not the LangChain4j bridge. A test-local provider matrix (`NativeProvider` record + `Capability` enum) parameterizes each scenario; per-scenario knobs are applied via a `Consumer<ElementTemplate>`. Deterministic assertions (fabricated nonce facts, JSON-schema parse, `> 0` token metrics) are the hard gate; the Camunda process-test LLM judge is an optional backstop.

**Tech Stack:** Java 21, JUnit 5 (`@ParameterizedTest`/`@MethodSource`/`@EnabledIfEnvironmentVariable`/`Assumptions`), Camunda Process Test (`io.camunda.process.test`), WireMock, AssertJ, the Anthropic native connector path, Maven.

## Global Constraints

- **Native path only.** Use the v2 template `AiAgentTestFixtures.AI_AGENT_JOB_WORKER_V2_ELEMENT_TEMPLATE_PATH` (= `agenticai-ai-agent-subprocess.v2.json`) and the `configuration.*` property ids. NEVER the v1 `provider.*` ids (those route through the LangChain4j bridge). This is the correctness property of the whole test.
- **Local dev only, never CI.** The IT class is gated by `@EnabledIfEnvironmentVariable(named = "RUN_NATIVE_LLM_E2E", matches = "true")` and per-row API-key presence. Do NOT reference this class in any CI workflow file. Do NOT use `@Disabled` (it would require a code edit to run).
- **Provider matrix now:** Anthropic `direct` backend only, models `claude-sonnet-4-6` and `claude-sonnet-5`. No local / OpenAI-compatible rows.
- **Deterministic assertions are the hard gate.** The LLM judge is a secondary/optional backstop and MUST NOT be the sole gate for any scenario.
- **Nonce facts** are fabricated strings that cannot come from model training (e.g. `Zypherion-9`, `Onyx-7`, `Dr. Kael Thrennix`).
- **Module:** `connectors-e2e-test/connectors-e2e-test-agentic-ai` (separate module; needs `element-templates-cli`). Build affected modules with `-am`.
- **Run Maven/git with `dangerouslyDisableSandbox: true`** (sandbox breaks Mockito MockMaker and blocks the network needed for real API calls).
- **Do NOT modify `DocumentToolCallResultsIT`** — it still covers the v1/bridge document path.
- Real-API "green" is a MANUAL verification the developer runs locally with keys; automated task verification is limited to compile + self-skip-when-unset.

---

## File Structure

- **Modify** `connectors-e2e-test/connectors-e2e-test-agentic-ai/src/test/java/io/camunda/connector/e2e/agenticai/assertj/JobWorkerAgentResponseAssert.java` — add three `> 0` token-metric helpers (this is the assert flavour the v2 sub-process/job-worker template produces).
- **Create** `connectors-e2e-test/connectors-e2e-test-agentic-ai/src/test/java/io/camunda/connector/e2e/agenticai/assertj/JobWorkerAgentResponseAssertTest.java` — unit test for the new helpers (Mockito deep stubs; no live API).
- **Create** `connectors-e2e-test/connectors-e2e-test-agentic-ai/src/test/resources/native-provider-acceptance-agent.bpmn` — minimal template-applied agent with one mockable tool.
- **Create** `connectors-e2e-test/connectors-e2e-test-agentic-ai/src/test/java/io/camunda/connector/e2e/agenticai/e2e/NativeProviderAcceptanceIT.java` — the IT: harness + matrix + five scenarios.
- **Reuse** `connectors-e2e-test/connectors-e2e-test-agentic-ai/src/test/resources/document-tool-call-results.bpmn` and its WireMock `__files/document-tool-call-results/*.pdf` for the multimodal scenario.

All paths below are relative to the repo root `<repo-root>`.

---

### Task 1: Token-usage assert helpers

Add three per-field `> 0` metric assertions to `JobWorkerAgentResponseAssert`, since the existing `hasMetrics(...)` does exact whole-record equality (unusable against a live API where counts vary). These are consumed by Scenarios 4 and 5.

**Files:**
- Modify: `connectors-e2e-test/connectors-e2e-test-agentic-ai/src/test/java/io/camunda/connector/e2e/agenticai/assertj/JobWorkerAgentResponseAssert.java`
- Test: `connectors-e2e-test/connectors-e2e-test-agentic-ai/src/test/java/io/camunda/connector/e2e/agenticai/assertj/JobWorkerAgentResponseAssertTest.java`

**Interfaces:**
- Produces (consumed by Tasks for Scenarios 4 & 5):
  - `JobWorkerAgentResponseAssert hasReasoningTokensGreaterThanZero()`
  - `JobWorkerAgentResponseAssert hasCacheCreationTokensGreaterThanZero()`
  - `JobWorkerAgentResponseAssert hasCacheReadTokensGreaterThanZero()`
- Reads metrics via `actual.context().metrics().tokenUsage()` (`TokenUsage` has primitive `int` accessors `reasoningTokenCount()`, `cacheCreationTokenCount()`, `cacheReadTokenCount()`).

- [ ] **Step 1: Write the failing unit test**

Create `connectors-e2e-test/connectors-e2e-test-agentic-ai/src/test/java/io/camunda/connector/e2e/agenticai/assertj/JobWorkerAgentResponseAssertTest.java`:

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.e2e.agenticai.assertj;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.model.JobWorkerAgentResponse;
import org.junit.jupiter.api.Test;

class JobWorkerAgentResponseAssertTest {

  @Test
  void reasoningTokensGreaterThanZeroPassesWhenPositiveAndFailsWhenZero() {
    final var response = mock(JobWorkerAgentResponse.class, RETURNS_DEEP_STUBS);

    when(response.context().metrics().tokenUsage().reasoningTokenCount()).thenReturn(7);
    assertThatCode(
            () ->
                JobWorkerAgentResponseAssert.assertThat(response)
                    .hasReasoningTokensGreaterThanZero())
        .doesNotThrowAnyException();

    when(response.context().metrics().tokenUsage().reasoningTokenCount()).thenReturn(0);
    assertThatThrownBy(
            () ->
                JobWorkerAgentResponseAssert.assertThat(response)
                    .hasReasoningTokensGreaterThanZero())
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void cacheCreationTokensGreaterThanZeroPassesWhenPositiveAndFailsWhenZero() {
    final var response = mock(JobWorkerAgentResponse.class, RETURNS_DEEP_STUBS);

    when(response.context().metrics().tokenUsage().cacheCreationTokenCount()).thenReturn(1024);
    assertThatCode(
            () ->
                JobWorkerAgentResponseAssert.assertThat(response)
                    .hasCacheCreationTokensGreaterThanZero())
        .doesNotThrowAnyException();

    when(response.context().metrics().tokenUsage().cacheCreationTokenCount()).thenReturn(0);
    assertThatThrownBy(
            () ->
                JobWorkerAgentResponseAssert.assertThat(response)
                    .hasCacheCreationTokensGreaterThanZero())
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void cacheReadTokensGreaterThanZeroPassesWhenPositiveAndFailsWhenZero() {
    final var response = mock(JobWorkerAgentResponse.class, RETURNS_DEEP_STUBS);

    when(response.context().metrics().tokenUsage().cacheReadTokenCount()).thenReturn(512);
    assertThatCode(
            () ->
                JobWorkerAgentResponseAssert.assertThat(response)
                    .hasCacheReadTokensGreaterThanZero())
        .doesNotThrowAnyException();

    when(response.context().metrics().tokenUsage().cacheReadTokenCount()).thenReturn(0);
    assertThatThrownBy(
            () ->
                JobWorkerAgentResponseAssert.assertThat(response)
                    .hasCacheReadTokensGreaterThanZero())
        .isInstanceOf(AssertionError.class);
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run (outside sandbox):
```bash
cd <repo-root>
mvn -q -pl connectors-e2e-test/connectors-e2e-test-agentic-ai test -Dtest=JobWorkerAgentResponseAssertTest
```
Expected: FAIL to compile — `hasReasoningTokensGreaterThanZero()` / `hasCacheCreationTokensGreaterThanZero()` / `hasCacheReadTokensGreaterThanZero()` do not exist yet.

- [ ] **Step 3: Add the three helper methods**

In `JobWorkerAgentResponseAssert.java`, add these methods inside the class (e.g. right after the existing `hasMetrics(...)` method):

```java
  public JobWorkerAgentResponseAssert hasReasoningTokensGreaterThanZero() {
    isNotNull();
    Assertions.assertThat(actual.context().metrics().tokenUsage().reasoningTokenCount())
        .as("reasoning token count")
        .isPositive();
    return this;
  }

  public JobWorkerAgentResponseAssert hasCacheCreationTokensGreaterThanZero() {
    isNotNull();
    Assertions.assertThat(actual.context().metrics().tokenUsage().cacheCreationTokenCount())
        .as("cache creation token count")
        .isPositive();
    return this;
  }

  public JobWorkerAgentResponseAssert hasCacheReadTokensGreaterThanZero() {
    isNotNull();
    Assertions.assertThat(actual.context().metrics().tokenUsage().cacheReadTokenCount())
        .as("cache read token count")
        .isPositive();
    return this;
  }
```

(No new imports needed — `Assertions` is already imported.)

- [ ] **Step 4: Run the test to verify it passes**

Run (outside sandbox):
```bash
mvn -q -pl connectors-e2e-test/connectors-e2e-test-agentic-ai test -Dtest=JobWorkerAgentResponseAssertTest
```
Expected: PASS (3 tests green).

- [ ] **Step 5: Commit**

```bash
git add connectors-e2e-test/connectors-e2e-test-agentic-ai/src/test/java/io/camunda/connector/e2e/agenticai/assertj/JobWorkerAgentResponseAssert.java \
        connectors-e2e-test/connectors-e2e-test-agentic-ai/src/test/java/io/camunda/connector/e2e/agenticai/assertj/JobWorkerAgentResponseAssertTest.java
git commit -m "test(agentic-ai): add positive token-metric assertions for agent responses"
```

---

### Task 2: Harness + BPMN + Scenario 1 (tool-call loop)

Create the acceptance BPMN and the IT class with its full harness (matrix, gating, judge config, model builder, tool mock, response read-back) and the first scenario: the agent calls a mockable tool that returns a planted secret, and the planted nonce must surface in the answer. This task proves the whole harness end-to-end and therefore requires a **manual local green run** (Step 6).

**Files:**
- Create: `connectors-e2e-test/connectors-e2e-test-agentic-ai/src/test/resources/native-provider-acceptance-agent.bpmn`
- Create: `connectors-e2e-test/connectors-e2e-test-agentic-ai/src/test/java/io/camunda/connector/e2e/agenticai/e2e/NativeProviderAcceptanceIT.java`

**Interfaces:**
- Consumes: `JobWorkerAgentResponseAssert` (Task 1), `AiAgentTestFixtures.AI_AGENT_JOB_WORKER_V2_ELEMENT_TEMPLATE_PATH`, `AiAgentTestFixtures.AGENT_RESPONSE_VARIABLE`.
- Produces (consumed by Scenario Tasks 3–6):
  - `enum Capability { STRUCTURED_OUTPUT, REASONING, PROMPT_CACHING, MULTIMODAL_TOOL_RESULT }`
  - `record NativeProvider(String label, String requiredEnvVar, Map<String,String> properties, Set<Capability> capabilities)` with `boolean isEnabled()` and `boolean supports(Capability)`
  - `static Stream<NativeProvider> providers()`
  - `BpmnModelInstance buildModel(NativeProvider provider, String templatePath, String bpmnResource, Consumer<ElementTemplate> customize)`
  - `ProcessInstanceEvent startAgent(NativeProvider provider, BpmnModelInstance model, Map<String,Object> variables)`
  - `void assertAgentResponse(ProcessInstanceEvent instance, ThrowingConsumer<JobWorkerAgentResponse> assertions)`
  - constants `BPMN_RESOURCE`, `PROCESS_ID`, `TOOL_JOB_TYPE`, `PLANTED_SECRET`, `NONCE_CODE_NAME`, `DEFAULT_SYSTEM_PROMPT`.

- [ ] **Step 1: Create the acceptance BPMN**

Create `connectors-e2e-test/connectors-e2e-test-agentic-ai/src/test/resources/native-provider-acceptance-agent.bpmn`. It mirrors `document-tool-call-results.bpmn`'s template-applied ad-hoc sub-process (`dummy` task definition, overwritten by `BpmnFile.apply(..., "AI_Agent", ...)`), minus the download step, with a single mockable service-task tool `Lookup_Classified_Fact` using a bespoke job type `native-acceptance-tool` (no real connector handles it, so the job stays open for the test's `mockJobWorker`):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:zeebe="http://camunda.org/schema/zeebe/1.0" id="Definitions_NativeAcceptance" targetNamespace="http://bpmn.io/schema/bpmn" exporter="Camunda Modeler" exporterVersion="5.42.0" modeler:executionPlatform="Camunda Cloud" modeler:executionPlatformVersion="8.9.0" xmlns:modeler="http://camunda.org/schema/modeler/1.0">
  <bpmn:process id="native_provider_acceptance" name="Native Provider Acceptance" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1">
      <bpmn:outgoing>Flow_Start_To_Agent</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:sequenceFlow id="Flow_Start_To_Agent" sourceRef="StartEvent_1" targetRef="AI_Agent" />
    <bpmn:adHocSubProcess id="AI_Agent" name="AI Agent">
      <bpmn:documentation>Native provider acceptance agent</bpmn:documentation>
      <bpmn:extensionElements>
        <zeebe:taskDefinition type="dummy" retries="0" />
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_Start_To_Agent</bpmn:incoming>
      <bpmn:outgoing>Flow_Agent_To_End</bpmn:outgoing>
      <bpmn:serviceTask id="Lookup_Classified_Fact" name="Lookup Classified Fact">
        <bpmn:documentation>Looks up the classified internal fact sheet and returns the secret project code name and clearance level. Call this whenever the user asks for the internal/classified code name.</bpmn:documentation>
        <bpmn:extensionElements>
          <zeebe:taskDefinition type="native-acceptance-tool" retries="0" />
        </bpmn:extensionElements>
      </bpmn:serviceTask>
    </bpmn:adHocSubProcess>
    <bpmn:sequenceFlow id="Flow_Agent_To_End" sourceRef="AI_Agent" targetRef="EndEvent_1" />
    <bpmn:endEvent id="EndEvent_1">
      <bpmn:incoming>Flow_Agent_To_End</bpmn:incoming>
    </bpmn:endEvent>
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="native_provider_acceptance">
      <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1">
        <dc:Bounds x="152" y="182" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Activity_Agent_di" bpmnElement="AI_Agent" isExpanded="true">
        <dc:Bounds x="260" y="100" width="360" height="200" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Activity_Lookup_di" bpmnElement="Lookup_Classified_Fact">
        <dc:Bounds x="320" y="150" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="EndEvent_1_di" bpmnElement="EndEvent_1">
        <dc:Bounds x="692" y="182" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Flow_Start_To_Agent_di" bpmnElement="Flow_Start_To_Agent">
        <di:waypoint x="188" y="200" />
        <di:waypoint x="260" y="200" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_Agent_To_End_di" bpmnElement="Flow_Agent_To_End">
        <di:waypoint x="620" y="200" />
        <di:waypoint x="692" y="200" />
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>
```

- [ ] **Step 2: Create the IT class with harness + Scenario 1**

Create `connectors-e2e-test/connectors-e2e-test-agentic-ai/src/test/java/io/camunda/connector/e2e/agenticai/e2e/NativeProviderAcceptanceIT.java`:

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.e2e.agenticai.e2e;

import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.AGENT_RESPONSE_VARIABLE;
import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.AI_AGENT_JOB_WORKER_V2_ELEMENT_TEMPLATE_PATH;
import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat as coreAssertThat; // NOTE: Java has no import-alias; see Step 2a.

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.connector.agenticai.aiagent.model.JobWorkerAgentResponse;
import io.camunda.connector.e2e.BpmnFile;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.connector.e2e.agenticai.CamundaDocumentTestConfiguration;
import io.camunda.connector.e2e.agenticai.assertj.JobWorkerAgentResponseAssert;
import io.camunda.connector.e2e.app.TestConnectorRuntimeApplication;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.File;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowingConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ResourceLoader;

/**
 * Cross-provider real-API acceptance safety net for the native own-LLM-layer path. Local-only:
 * runs only when RUN_NATIVE_LLM_E2E=true and the row's API key is present. Asserts on observable
 * output (nonce facts, JSON schema, token metrics) so scenarios port across providers.
 */
@SpringBootTest(
    classes = {TestConnectorRuntimeApplication.class},
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "camunda.connector.webhook.enabled=false",
      "camunda.connector.polling.enabled=false",
      "camunda.connector.agenticai.tools.process-definition.cache.enabled=false",
      // Optional LLM judge (Anthropic direct Haiku, reuses ANTHROPIC_API_KEY). Deterministic
      // assertions remain the hard gate; the judge is a secondary backstop.
      "camunda.process-test.judge.chat-model.provider=anthropic",
      "camunda.process-test.judge.chat-model.model=claude-haiku-4-5-20251001",
      "camunda.process-test.judge.chat-model.api-key=${ANTHROPIC_API_KEY:NOT_SET}",
      "camunda.process-test.judge.threshold=0.6"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@CamundaSpringProcessTest
@Import(CamundaDocumentTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "RUN_NATIVE_LLM_E2E", matches = "true")
class NativeProviderAcceptanceIT {

  static final String BPMN_RESOURCE = "classpath:native-provider-acceptance-agent.bpmn";
  static final String PROCESS_ID = "native_provider_acceptance";
  static final String TOOL_JOB_TYPE = "native-acceptance-tool";
  static final Duration PROCESS_TIMEOUT = Duration.ofMinutes(3);

  // Fabricated nonce facts — cannot originate from model training, so their presence in the answer
  // proves the tool was actually invoked and consumed.
  static final String NONCE_CODE_NAME = "Zypherion-9";
  static final String NONCE_CLEARANCE = "Onyx-7";
  static final String PLANTED_SECRET =
      "CLASSIFIED FACT SHEET: The internal project code name is "
          + NONCE_CODE_NAME
          + " and its clearance level is "
          + NONCE_CLEARANCE
          + ".";

  static final String DEFAULT_SYSTEM_PROMPT =
      "You are a precise assistant. When the user asks for a classified or internal code name, "
          + "you MUST call the Lookup Classified Fact tool and quote its result verbatim.";

  private final com.fasterxml.jackson.databind.ObjectMapper objectMapper =
      ConnectorsObjectMapperSupplier.getCopy();

  @Autowired CamundaClient camundaClient;
  @Autowired CamundaProcessTestContext processTestContext;
  @Autowired ResourceLoader resourceLoader;
  @TempDir File tempDir;

  enum Capability {
    STRUCTURED_OUTPUT,
    REASONING,
    PROMPT_CACHING,
    MULTIMODAL_TOOL_RESULT
  }

  record NativeProvider(
      String label,
      String requiredEnvVar,
      Map<String, String> properties,
      Set<Capability> capabilities) {

    boolean isEnabled() {
      return System.getenv(requiredEnvVar) != null;
    }

    boolean supports(Capability capability) {
      return capabilities.contains(capability);
    }

    @Override
    public String toString() {
      return label;
    }
  }

  static NativeProvider anthropicDirect(String model) {
    return new NativeProvider(
        "anthropic-direct/" + model,
        "ANTHROPIC_API_KEY",
        Map.of(
            "configuration.type", "anthropic",
            "configuration.anthropic.backend.type", "direct",
            "configuration.anthropic.backend.apiKey", envOrPlaceholder("ANTHROPIC_API_KEY"),
            "configuration.anthropic.model.model", model),
        Set.of(
            Capability.STRUCTURED_OUTPUT,
            Capability.REASONING,
            Capability.PROMPT_CACHING,
            Capability.MULTIMODAL_TOOL_RESULT));
  }

  static Stream<NativeProvider> providers() {
    return Stream.of(anthropicDirect("claude-sonnet-4-6"), anthropicDirect("claude-sonnet-5"))
        .filter(NativeProvider::isEnabled);
  }

  private static String envOrPlaceholder(String envVar) {
    return System.getenv().getOrDefault(envVar, "NOT_SET");
  }

  @BeforeEach
  void mockClassifiedFactTool() {
    processTestContext
        .mockJobWorker(TOOL_JOB_TYPE)
        .withHandler(
            (jobClient, job) ->
                jobClient
                    .newCompleteCommand(job)
                    .variable("toolCallResult", PLANTED_SECRET)
                    .send()
                    .join());
  }

  /** Builds the applied BPMN for the given provider, with baseline props + per-scenario overrides. */
  BpmnModelInstance buildModel(
      NativeProvider provider,
      String templatePath,
      String bpmnResource,
      String systemPrompt,
      Consumer<ElementTemplate> customize) {
    var template = ElementTemplate.from(templatePath);

    template
        .property("agentContext", "=agent.context")
        .property("data.systemPrompt.prompt", "=\"" + systemPrompt.replace("\"", "\\\"") + "\"")
        .property(
            "data.userPrompt.prompt",
            "=if (is defined(followUpUserPrompt)) then followUpUserPrompt else userPrompt")
        .property("data.userPrompt.documents", "=if (is defined(downloadedFiles)) then downloadedFiles else []")
        .property("data.memory.storage.type", "in-process")
        .property("data.memory.contextWindowSize", "=50")
        .property("data.response.includeAssistantMessage", "=true")
        .property("data.response.includeAgentContext", "=true");

    provider.properties().forEach(template::property);
    customize.accept(template);

    try {
      var templateFile = template.writeTo(new File(tempDir, "template.json"));
      var bpmnFile = resourceLoader.getResource(bpmnResource).getFile();
      return new BpmnFile(bpmnFile).apply(templateFile, "AI_Agent", new File(tempDir, "applied.bpmn"));
    } catch (Exception e) {
      throw new RuntimeException("Failed to build BPMN model for " + provider.label(), e);
    }
  }

  ProcessInstanceEvent startAgent(
      BpmnModelInstance model, String processId, Map<String, Object> variables) {
    ZeebeTest.with(camundaClient).awaitCompleteTopology().deploy(model);
    return camundaClient
        .newCreateInstanceCommand()
        .bpmnProcessId(processId)
        .latestVersion()
        .variables(variables)
        .send()
        .join();
  }

  void assertAgentResponse(
      ProcessInstanceEvent instance, ThrowingConsumer<JobWorkerAgentResponse> assertions) {
    assertThat(instance)
        .withAssertionTimeout(PROCESS_TIMEOUT)
        .isCompleted()
        .hasVariableSatisfies(
            AGENT_RESPONSE_VARIABLE,
            Map.class,
            map -> {
              var response = objectMapper.convertValue(map, JobWorkerAgentResponse.class);
              assertions.accept(response);
            });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("providers")
  void toolCallLoopSurfacesPlantedFact(NativeProvider provider) {
    var model =
        buildModel(
            provider,
            AI_AGENT_JOB_WORKER_V2_ELEMENT_TEMPLATE_PATH,
            BPMN_RESOURCE,
            DEFAULT_SYSTEM_PROMPT,
            template -> {});

    var instance =
        startAgent(
            model,
            PROCESS_ID,
            Map.of("userPrompt", "What is the internal project code name? Use your lookup tool."));

    assertAgentResponse(
        instance,
        response ->
            JobWorkerAgentResponseAssert.assertThat(response)
                .isReady()
                .hasResponseTestSatisfying(text -> assertThat(text).contains(NONCE_CODE_NAME)));
  }
}
```

- [ ] **Step 2a: Fix the illegal import-alias line**

Java has no import aliasing. Remove the placeholder line `import static org.assertj.core.api.Assertions.assertThat as coreAssertThat;`. Inside `hasResponseTestSatisfying(...)`, the `assertThat(text)` call must resolve to AssertJ's `org.assertj.core.api.Assertions.assertThat`, but `io.camunda.process.test.api.CamundaAssert.assertThat` is already statically imported. Resolve the clash by calling AssertJ fully qualified inside the lambda:

```java
                .hasResponseTestSatisfying(
                    text -> org.assertj.core.api.Assertions.assertThat(text).contains(NONCE_CODE_NAME)));
```

Keep only `import static io.camunda.process.test.api.CamundaAssert.assertThat;` at the top (remove the aliased line entirely).

Note: the existing assert method is spelled `hasResponseTestSatisfying` (a pre-existing typo for "Text") — use it verbatim.

- [ ] **Step 3: Verify it compiles and self-skips when the flag is unset**

Run (outside sandbox, WITHOUT `RUN_NATIVE_LLM_E2E`):
```bash
cd <repo-root>
mvn -q -pl connectors-e2e-test/connectors-e2e-test-agentic-ai -am test-compile
mvn -q -pl connectors-e2e-test/connectors-e2e-test-agentic-ai test -Dtest=NativeProviderAcceptanceIT
```
Expected: test-compile succeeds; the test run reports the class as **skipped** (disabled by `@EnabledIfEnvironmentVariable`), NOT failed.

- [ ] **Step 4: Commit**

```bash
git add connectors-e2e-test/connectors-e2e-test-agentic-ai/src/test/resources/native-provider-acceptance-agent.bpmn \
        connectors-e2e-test/connectors-e2e-test-agentic-ai/src/test/java/io/camunda/connector/e2e/agenticai/e2e/NativeProviderAcceptanceIT.java
git commit -m "test(agentic-ai): native-provider real-API acceptance harness with tool-call scenario"
```

- [ ] **Step 5 (MANUAL, developer-run): prove the harness against a real API**

This cannot run in CI/review. With Anthropic credentials available (a local env loader populates `ANTHROPIC_API_KEY`), run outside the sandbox:
```bash
RUN_NATIVE_LLM_E2E=true mvn -pl connectors-e2e-test/connectors-e2e-test-agentic-ai test \
  -Dtest=NativeProviderAcceptanceIT#toolCallLoopSurfacesPlantedFact
```
Expected: two parameterized executions (`anthropic-direct/claude-sonnet-4-6`, `anthropic-direct/claude-sonnet-5`) complete and the answer contains `Zypherion-9`.

If the tool is never called (job type not exposed / grabbed by a connector), the de-risking fallback is to implement the tool as a `scriptTask` returning a planted process variable instead of a mocked service task: replace the `Lookup_Classified_Fact` service task in the BPMN with
`<bpmn:scriptTask id="Lookup_Classified_Fact" name="Lookup Classified Fact"><bpmn:documentation>…</bpmn:documentation><bpmn:extensionElements><zeebe:script expression="=plantedSecret" resultVariable="toolCallResult" /></bpmn:extensionElements></bpmn:scriptTask>`,
pass `plantedSecret=PLANTED_SECRET` as a process variable in `startAgent(...)`, and drop the `mockJobWorker` `@BeforeEach`. This is the proven pattern from `document-tool-call-results.bpmn`.

---

### Task 3: Scenario 3 — structured output

Add a scenario that requests a JSON-schema-constrained response and asserts the `agent` output parses to the schema shape with the planted values. Reuses the Task-2 BPMN and tool.

**Files:**
- Modify: `connectors-e2e-test/connectors-e2e-test-agentic-ai/src/test/java/io/camunda/connector/e2e/agenticai/e2e/NativeProviderAcceptanceIT.java`

**Interfaces:**
- Consumes: `buildModel(...)`, `startAgent(...)`, `assertAgentResponse(...)`, `providers()`, `PLANTED_SECRET`, `NONCE_CODE_NAME`, `NONCE_CLEARANCE`, `Capability.STRUCTURED_OUTPUT`.

- [ ] **Step 1: Add a capability-filtered provider source and the scenario**

Add to `NativeProviderAcceptanceIT`:

```java
  static Stream<NativeProvider> providersWithStructuredOutput() {
    return providers().filter(p -> p.supports(Capability.STRUCTURED_OUTPUT));
  }

  private static final String RESPONSE_SCHEMA =
      "{\"type\":\"object\","
          + "\"properties\":{\"codeName\":{\"type\":\"string\"},\"clearanceLevel\":{\"type\":\"string\"}},"
          + "\"required\":[\"codeName\",\"clearanceLevel\"],"
          + "\"additionalProperties\":false}";

  @ParameterizedTest(name = "{0}")
  @MethodSource("providersWithStructuredOutput")
  void structuredOutputReturnsSchemaConformingJson(NativeProvider provider) {
    var model =
        buildModel(
            provider,
            AI_AGENT_JOB_WORKER_V2_ELEMENT_TEMPLATE_PATH,
            BPMN_RESOURCE,
            DEFAULT_SYSTEM_PROMPT,
            template ->
                template
                    .property("data.response.format.type", "json")
                    .property("data.response.format.schema", "=" + RESPONSE_SCHEMA)
                    .property("data.response.format.schemaName", "ClassifiedFact"));

    var instance =
        startAgent(
            model,
            PROCESS_ID,
            Map.of(
                "userPrompt",
                "Look up the internal project code name and clearance level and return them."));

    assertAgentResponse(
        instance,
        response ->
            JobWorkerAgentResponseAssert.assertThat(response)
                .isReady()
                .hasResponseJsonSatisfying(
                    json -> {
                      @SuppressWarnings("unchecked")
                      var map = (Map<String, Object>) json;
                      org.assertj.core.api.Assertions.assertThat(map)
                          .containsKeys("codeName", "clearanceLevel");
                      org.assertj.core.api.Assertions.assertThat(String.valueOf(map.get("codeName")))
                          .contains(NONCE_CODE_NAME);
                      org.assertj.core.api.Assertions.assertThat(
                              String.valueOf(map.get("clearanceLevel")))
                          .contains(NONCE_CLEARANCE);
                    }));
  }
```

- [ ] **Step 2: Verify compile + self-skip**

Run (outside sandbox, flag unset):
```bash
mvn -q -pl connectors-e2e-test/connectors-e2e-test-agentic-ai -am test-compile
mvn -q -pl connectors-e2e-test/connectors-e2e-test-agentic-ai test -Dtest=NativeProviderAcceptanceIT
```
Expected: compiles; class reported skipped.

- [ ] **Step 3: Commit**

```bash
git add connectors-e2e-test/connectors-e2e-test-agentic-ai/src/test/java/io/camunda/connector/e2e/agenticai/e2e/NativeProviderAcceptanceIT.java
git commit -m "test(agentic-ai): add structured-output acceptance scenario"
```

- [ ] **Step 4 (MANUAL, developer-run):**
```bash
RUN_NATIVE_LLM_E2E=true mvn -pl connectors-e2e-test/connectors-e2e-test-agentic-ai test \
  -Dtest=NativeProviderAcceptanceIT#structuredOutputReturnsSchemaConformingJson
```
Expected: the `agent` output JSON contains `codeName` = `…Zypherion-9…` and `clearanceLevel` = `…Onyx-7…`.

---

### Task 4: Scenario 4 — reasoning enabled

Enable extended thinking and assert the run completes AND consumes reasoning tokens (`reasoningTokenCount > 0`). No tool needed. Capability-gated on `REASONING`.

**Files:**
- Modify: `connectors-e2e-test/connectors-e2e-test-agentic-ai/src/test/java/io/camunda/connector/e2e/agenticai/e2e/NativeProviderAcceptanceIT.java`

**Interfaces:**
- Consumes: `buildModel(...)`, `startAgent(...)`, `assertAgentResponse(...)`, `hasReasoningTokensGreaterThanZero()` (Task 1), `Capability.REASONING`.

- [ ] **Step 1: Add the scenario**

Add to `NativeProviderAcceptanceIT`:

```java
  static Stream<NativeProvider> providersWithReasoning() {
    return providers().filter(p -> p.supports(Capability.REASONING));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("providersWithReasoning")
  void reasoningEnabledProducesReasoningTokens(NativeProvider provider) {
    var model =
        buildModel(
            provider,
            AI_AGENT_JOB_WORKER_V2_ELEMENT_TEMPLATE_PATH,
            BPMN_RESOURCE,
            "You are a careful reasoner. Think step by step before answering.",
            template ->
                template
                    .property("configuration.anthropic.model.parameters.thinking.mode", "enabled")
                    .property(
                        "configuration.anthropic.model.parameters.thinking.budgetTokens", "2048"));

    var instance =
        startAgent(
            model,
            PROCESS_ID,
            Map.of(
                "userPrompt",
                "If it takes 5 machines 5 minutes to make 5 widgets, how many minutes do 100 "
                    + "machines take to make 100 widgets? Reply with just the number of minutes."));

    assertAgentResponse(
        instance,
        response ->
            JobWorkerAgentResponseAssert.assertThat(response)
                .isReady()
                .hasReasoningTokensGreaterThanZero()
                .hasResponseTestSatisfying(
                    text -> org.assertj.core.api.Assertions.assertThat(text).contains("5")));
  }
```

- [ ] **Step 2: Verify compile + self-skip**

Run (outside sandbox, flag unset):
```bash
mvn -q -pl connectors-e2e-test/connectors-e2e-test-agentic-ai -am test-compile
mvn -q -pl connectors-e2e-test/connectors-e2e-test-agentic-ai test -Dtest=NativeProviderAcceptanceIT
```
Expected: compiles; class reported skipped.

- [ ] **Step 3: Commit**

```bash
git add connectors-e2e-test/connectors-e2e-test-agentic-ai/src/test/java/io/camunda/connector/e2e/agenticai/e2e/NativeProviderAcceptanceIT.java
git commit -m "test(agentic-ai): add reasoning-enabled acceptance scenario"
```

- [ ] **Step 4 (MANUAL, developer-run):**
```bash
RUN_NATIVE_LLM_E2E=true mvn -pl connectors-e2e-test/connectors-e2e-test-agentic-ai test \
  -Dtest=NativeProviderAcceptanceIT#reasoningEnabledProducesReasoningTokens
```
Expected: run completes, answer contains `5`, and `reasoningTokenCount > 0`.

---

### Task 5: Scenario 5 — prompt caching

Enable Anthropic prompt caching with a system prompt long enough to clear the model's minimum cacheable prefix (~1024 tokens), force a second model call via the tool, and assert both cache-write and cache-read token counts are non-zero across the run. Capability-gated on `PROMPT_CACHING`.

**Files:**
- Modify: `connectors-e2e-test/connectors-e2e-test-agentic-ai/src/test/java/io/camunda/connector/e2e/agenticai/e2e/NativeProviderAcceptanceIT.java`

**Interfaces:**
- Consumes: `buildModel(...)`, `startAgent(...)`, `assertAgentResponse(...)`, `hasCacheCreationTokensGreaterThanZero()` / `hasCacheReadTokensGreaterThanZero()` (Task 1), `Capability.PROMPT_CACHING`, `PLANTED_SECRET`, `NONCE_CODE_NAME`.

- [ ] **Step 1: Add a long system prompt constant and the scenario**

Add to `NativeProviderAcceptanceIT`. The `.repeat(12)` guarantees the prefix clears the ~1024-token cache floor deterministically; the content is a stable, real instruction block so the prefix is byte-identical across turns:

```java
  // Long, stable system prompt so system+tools exceed the model's minimum cacheable prefix
  // (~1024 tokens). Without this, automatic caching is silently skipped and cache_* stays 0.
  private static final String LONG_SYSTEM_PROMPT =
      ("You are an assistant operating under a detailed classified-information handling protocol. "
              + "Always be precise, never fabricate facts, and when the user asks for an internal "
              + "or classified code name you must call the Lookup Classified Fact tool and quote "
              + "its result verbatim without paraphrasing. Follow every rule in this protocol "
              + "carefully and consistently across the whole conversation. ")
          .repeat(12);

  static Stream<NativeProvider> providersWithPromptCaching() {
    return providers().filter(p -> p.supports(Capability.PROMPT_CACHING));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("providersWithPromptCaching")
  void promptCachingReportsCacheReadAndWriteTokens(NativeProvider provider) {
    var model =
        buildModel(
            provider,
            AI_AGENT_JOB_WORKER_V2_ELEMENT_TEMPLATE_PATH,
            BPMN_RESOURCE,
            LONG_SYSTEM_PROMPT,
            template -> template.property("configuration.anthropic.enablePromptCaching", "true"));

    var instance =
        startAgent(
            model,
            PROCESS_ID,
            Map.of("userPrompt", "What is the internal project code name? Use your lookup tool."));

    // The tool call forces a second model call: turn 1 writes the cache (system+tools prefix),
    // turn 2 re-sends the byte-identical prefix and reads it. Cumulative run metrics carry both.
    assertAgentResponse(
        instance,
        response ->
            JobWorkerAgentResponseAssert.assertThat(response)
                .isReady()
                .hasCacheCreationTokensGreaterThanZero()
                .hasCacheReadTokensGreaterThanZero()
                .hasResponseTestSatisfying(
                    text ->
                        org.assertj.core.api.Assertions.assertThat(text).contains(NONCE_CODE_NAME)));
  }
```

- [ ] **Step 2: Verify compile + self-skip**

Run (outside sandbox, flag unset):
```bash
mvn -q -pl connectors-e2e-test/connectors-e2e-test-agentic-ai -am test-compile
mvn -q -pl connectors-e2e-test/connectors-e2e-test-agentic-ai test -Dtest=NativeProviderAcceptanceIT
```
Expected: compiles; class reported skipped.

- [ ] **Step 3: Commit**

```bash
git add connectors-e2e-test/connectors-e2e-test-agentic-ai/src/test/java/io/camunda/connector/e2e/agenticai/e2e/NativeProviderAcceptanceIT.java
git commit -m "test(agentic-ai): add prompt-caching acceptance scenario"
```

- [ ] **Step 4 (MANUAL, developer-run):**
```bash
RUN_NATIVE_LLM_E2E=true mvn -pl connectors-e2e-test/connectors-e2e-test-agentic-ai test \
  -Dtest=NativeProviderAcceptanceIT#promptCachingReportsCacheReadAndWriteTokens
```
Expected: run completes and both `cacheCreationTokenCount > 0` and `cacheReadTokenCount > 0`. If cache-read is 0, the prefix is under the model's floor — increase the `.repeat(...)` factor.

---

### Task 6: Scenario 2 — document-in-tool-result (multimodal)

Add the multimodal scenario, reusing the existing `document-tool-call-results.bpmn` (which downloads WireMock-served PDFs and returns them from a tool). Assert the model reads the PDF's planted facts. Capability-gated on `MULTIMODAL_TOOL_RESULT`.

**Files:**
- Modify: `connectors-e2e-test/connectors-e2e-test-agentic-ai/src/test/java/io/camunda/connector/e2e/agenticai/e2e/NativeProviderAcceptanceIT.java`

**Interfaces:**
- Consumes: `buildModel(...)`, `startAgent(...)`, `assertAgentResponse(...)`, `Capability.MULTIMODAL_TOOL_RESULT`. Uses the existing WireMock `__files/document-tool-call-results/*.pdf` fixtures whose text embeds the facts asserted below.
- Reuses BPMN `classpath:document-tool-call-results.bpmn` (process id `CPT_Document_Tool_Call_Results`, agent element `AI_Agent`, tool `Fetch_Report`).

- [ ] **Step 1: Add WireMock imports and the scenario**

At the top of `NativeProviderAcceptanceIT`, add the WireMock static imports and the runtime-info parameter type:

```java
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.util.List;
```

Add the `@WireMockTest` annotation to the class (alongside the existing annotations):

```java
@WireMockTest
```

Add the document constants, PDF stub, and scenario to the class body. The three planted facts (`Zypherion`, `847`, `Kael Thrennix`) live in the existing PDF fixtures and match `DocumentToolCallResultsIT`:

```java
  private static final String DOC_DIR = "document-tool-call-results/";
  private static final String DOC_PROJECT_LAUNCH = DOC_DIR + "project-launch.pdf";
  private static final String DOC_HEADCOUNT_REPORT = DOC_DIR + "headcount-report.pdf";
  private static final String DOC_AUTHOR_INFO = DOC_DIR + "author-info.pdf";
  private static final String DOCUMENT_BPMN_RESOURCE = "classpath:document-tool-call-results.bpmn";
  private static final String DOCUMENT_PROCESS_ID = "CPT_Document_Tool_Call_Results";
  private static final String DOCUMENT_SYSTEM_PROMPT =
      "You are a document analyst. Use the available tools to retrieve and analyze documents. "
          + "Always quote specific facts, numbers, dates, and names found in the documents.";

  static Stream<NativeProvider> providersWithMultimodalToolResult() {
    return providers().filter(p -> p.supports(Capability.MULTIMODAL_TOOL_RESULT));
  }

  private void stubPdfDownloads() {
    for (var doc : List.of(DOC_PROJECT_LAUNCH, DOC_HEADCOUNT_REPORT, DOC_AUTHOR_INFO)) {
      stubFor(
          get(urlPathEqualTo("/" + doc))
              .willReturn(
                  aResponse().withBodyFile(doc).withHeader("Content-Type", "application/pdf")));
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("providersWithMultimodalToolResult")
  void documentInToolResultIsReadByModel(NativeProvider provider, WireMockRuntimeInfo wireMock) {
    stubPdfDownloads();

    var model =
        buildModel(
            provider,
            AI_AGENT_JOB_WORKER_V2_ELEMENT_TEMPLATE_PATH,
            DOCUMENT_BPMN_RESOURCE,
            DOCUMENT_SYSTEM_PROMPT,
            template -> {});

    var instance =
        startAgent(
            model,
            DOCUMENT_PROCESS_ID,
            Map.of(
                "userPrompt",
                "Use the Fetch_Report tool to get the full report and describe the content of "
                    + "every document in it, including attachments and the cover page.",
                "downloadUrls",
                List.of(
                    wireMock.getHttpBaseUrl() + "/" + DOC_PROJECT_LAUNCH,
                    wireMock.getHttpBaseUrl() + "/" + DOC_HEADCOUNT_REPORT,
                    wireMock.getHttpBaseUrl() + "/" + DOC_AUTHOR_INFO)));

    assertAgentResponse(
        instance,
        response ->
            JobWorkerAgentResponseAssert.assertThat(response)
                .isReady()
                .hasResponseTestSatisfying(
                    text ->
                        org.assertj.core.api.Assertions.assertThat(text)
                            .contains("Zypherion")
                            .contains("847")
                            .contains("Kael Thrennix")));
  }
```

Note: the `buildModel(...)` baseline already sets `data.userPrompt.documents` to `downloadedFiles` when that variable is defined (the document BPMN's `Download_Files` task produces it), so no change to `buildModel` is required.

- [ ] **Step 2: Verify compile + self-skip**

Run (outside sandbox, flag unset):
```bash
mvn -q -pl connectors-e2e-test/connectors-e2e-test-agentic-ai -am test-compile
mvn -q -pl connectors-e2e-test/connectors-e2e-test-agentic-ai test -Dtest=NativeProviderAcceptanceIT
```
Expected: compiles; class reported skipped.

- [ ] **Step 3: Commit**

```bash
git add connectors-e2e-test/connectors-e2e-test-agentic-ai/src/test/java/io/camunda/connector/e2e/agenticai/e2e/NativeProviderAcceptanceIT.java
git commit -m "test(agentic-ai): add document-in-tool-result multimodal acceptance scenario"
```

- [ ] **Step 4 (MANUAL, developer-run):**
```bash
RUN_NATIVE_LLM_E2E=true mvn -pl connectors-e2e-test/connectors-e2e-test-agentic-ai test \
  -Dtest=NativeProviderAcceptanceIT#documentInToolResultIsReadByModel
```
Expected: answer contains `Zypherion`, `847`, and `Kael Thrennix`.

---

### Task 7: Full acceptance run (MANUAL)

Run the whole suite against both models locally and record the outcome. This is the actual acceptance of the safety net.

**Files:** none (verification only).

- [ ] **Step 1: Run the full IT against real Anthropic**

Run (outside sandbox, with `ANTHROPIC_API_KEY` present via a local env loader):
```bash
RUN_NATIVE_LLM_E2E=true mvn -pl connectors-e2e-test/connectors-e2e-test-agentic-ai test \
  -Dtest=NativeProviderAcceptanceIT
```
Expected: all five scenarios pass for both `claude-sonnet-4-6` and `claude-sonnet-5` (10 parameterized executions). Note the run cost/time.

- [ ] **Step 2: Confirm no CI wiring**

Verify the class is not referenced by any workflow:
```bash
grep -rn "NativeProviderAcceptanceIT\|RUN_NATIVE_LLM_E2E" .github/ || echo "not referenced in CI — good"
```
Expected: no matches under `.github/`.

- [ ] **Step 3: Record results**

Summarize which scenarios/models passed (and any prompt/threshold tuning needed) for the branch record. No commit required unless tuning changed test code (fold any tuning into the relevant scenario's commit).

---

## Self-Review

**1. Spec coverage:**
- New sibling IT on v2 native template + `configuration.*` → Task 2 (harness). ✅
- Provider matrix (Sonnet 4.6 + 5, Anthropic direct, capability-gated) → Task 2 (`providers()`, `NativeProvider`, `Capability`). ✅
- Scenario 1 tool loop → Task 2; Scenario 3 structured output → Task 3; Scenario 4 reasoning → Task 4; Scenario 5 caching → Task 5; Scenario 2 multimodal → Task 6. ✅
- Deterministic hard gate + optional judge → nonce `contains`, JSON schema, `> 0` metrics (Tasks 3–6); judge configured at class level as backstop. ✅
- `> 0` metric helpers (since `hasMetrics` is exact-equality) → Task 1. ✅
- Caching prefix floor gotcha → Task 5 `LONG_SYSTEM_PROMPT`. ✅
- Local-only gating, never CI → `@EnabledIfEnvironmentVariable(RUN_NATIVE_LLM_E2E)` + Task 7 Step 2. ✅
- Judge `provider=anthropic` support → confirmed; class-level config. ✅
- Don't modify `DocumentToolCallResultsIT` → only reuses its BPMN + PDF fixtures. ✅

**2. Placeholder scan:** No TBD/TODO. The one deliberate non-code fallback (Task 2 Step 5 scriptTask alternative) is a concrete, complete alternative, not a placeholder. The illegal import-alias line in Task 2 Step 2 is intentionally flagged and fixed in Step 2a (kept visible so the implementer understands the name clash).

**3. Type consistency:** `NativeProvider`/`Capability`/`buildModel`/`startAgent`/`assertAgentResponse` signatures defined in Task 2 are used verbatim in Tasks 3–6. Assert helpers named identically in Task 1 (definition) and Tasks 4–5 (use). Template path/constant `AI_AGENT_JOB_WORKER_V2_ELEMENT_TEMPLATE_PATH` and `AGENT_RESPONSE_VARIABLE` match `AiAgentTestFixtures`. The existing (typo'd) `hasResponseTestSatisfying` is used verbatim.

## Notes / risks for the executor

- **BPMN tool wiring is the main real-API risk.** Task 2 Step 5 is the harness-proving manual run; if the mocked service-task tool isn't exposed/left-open, switch to the documented `scriptTask` fallback (proven in `document-tool-call-results.bpmn`). Get Scenario 1 green locally before building Tasks 3–6.
- **Judge api-key property name.** If context startup fails on the judge config, confirm the exact property key for the Anthropic judge api-key against `application-it-real-llm.yml` (the OpenAI example) / `JudgeProperties`. The judge is a backstop — if needed, the config block can be dropped without affecting the deterministic gates.
- **PDF fixtures** for Task 6 must already exist under WireMock's `__files/document-tool-call-results/` (they back `DocumentToolCallResultsIT`); confirm before running Task 6's manual step.
