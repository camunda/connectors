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
 * Cross-provider real-API acceptance safety net for the native own-LLM-layer path. Local-only: runs
 * only when RUN_NATIVE_LLM_E2E=true and the row's API key is present. Asserts on observable output
 * (nonce facts, JSON schema, token metrics) so scenarios port across providers.
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
            "configuration.type",
            "anthropic",
            "configuration.anthropic.backend.type",
            "direct",
            "configuration.anthropic.backend.apiKey",
            envOrPlaceholder("ANTHROPIC_API_KEY"),
            "configuration.anthropic.model.model",
            model),
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

  /**
   * Builds the applied BPMN for the given provider, with baseline props + per-scenario overrides.
   */
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
        .property(
            "data.userPrompt.documents",
            "=if (is defined(downloadedFiles)) then downloadedFiles else []")
        .property("data.memory.storage.type", "in-process")
        .property("data.memory.contextWindowSize", "=50")
        .property("data.response.includeAssistantMessage", "=true")
        .property("data.response.includeAgentContext", "=true");

    provider.properties().forEach(template::property);
    customize.accept(template);

    try {
      var templateFile = template.writeTo(new File(tempDir, "template.json"));
      var bpmnFile = resourceLoader.getResource(bpmnResource).getFile();
      return new BpmnFile(bpmnFile)
          .apply(templateFile, "AI_Agent", new File(tempDir, "applied.bpmn"));
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
                .hasResponseTestSatisfying(
                    text ->
                        org.assertj.core.api.Assertions.assertThat(text)
                            .contains(NONCE_CODE_NAME)));
  }
}
