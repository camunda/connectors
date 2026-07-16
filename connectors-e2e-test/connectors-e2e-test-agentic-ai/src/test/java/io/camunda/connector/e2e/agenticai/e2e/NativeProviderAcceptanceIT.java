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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.AGENT_RESPONSE_VARIABLE;
import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.AI_AGENT_JOB_WORKER_V2_ELEMENT_TEMPLATE_PATH;
import static io.camunda.process.test.api.CamundaAssert.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
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
import java.util.List;
import java.util.Map;
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
@WireMockTest
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
    MULTIMODAL_USER_MESSAGE,
    MULTIMODAL_TOOL_RESULT,
    WEB_SEARCH,
    CODE_INTERPRETER
  }

  /**
   * A provider row in the acceptance matrix. {@code capabilityProperties} maps each capability this
   * row supports to the MODEL-SPECIFIC element-template properties that enable that capability for
   * this model. The map is empty when the capability needs no provider-specific enablement
   * (structured output is enabled by the shared {@code data.response.format.*} props the scenario
   * sets; multimodal just needs the document BPMN). Reasoning and prompt caching are enabled
   * differently per model/provider, so their enablement lives HERE rather than being hard-coded in
   * the scenario — which is what lets a future OpenAI row join the same scenarios with its own
   * property ids (e.g. an OpenAI reasoning-effort id, or no caching key at all since OpenAI caches
   * automatically). A capability absent from the map means the row does not support it, so its
   * scenario is skipped for this row.
   */
  record NativeProvider(
      String label,
      String requiredEnvVar,
      Map<String, String> properties,
      Map<Capability, Map<String, String>> capabilityProperties,
      // Whether this row's reasoning config FORCES the model to emit reasoning tokens (a forcing
      // mode like Anthropic "enabled"), so the reasoning scenario can additionally assert
      // reasoningTokenCount > 0. For "adaptive"/effort-only modes the model may answer without
      // billable thinking, so only completion + a correct answer is asserted.
      boolean forcesReasoningTokens,
      // Whether this provider family reports a distinct cache-creation (write) token count.
      // Anthropic reports `cache_creation_input_tokens` for the turn that writes the cache.
      // OpenAI's prompt caching is automatic and reports only cache-READ tokens
      // (`cached_tokens`), with no creation/write metric at all — so the cache-creation
      // assertion in the prompt-caching scenario is gated on this flag.
      boolean reportsCacheCreationTokens,
      // Manual on/off switch (independent of the env-var gate) so a single row can be muted while
      // iterating locally; mirrors DocumentToolCallResultsIT.ProviderConfig#disabled().
      boolean enabled) {

    NativeProvider(
        String label,
        String requiredEnvVar,
        Map<String, String> properties,
        Map<Capability, Map<String, String>> capabilityProperties,
        boolean forcesReasoningTokens,
        boolean reportsCacheCreationTokens) {
      this(
          label,
          requiredEnvVar,
          properties,
          capabilityProperties,
          forcesReasoningTokens,
          reportsCacheCreationTokens,
          true);
    }

    NativeProvider disabled() {
      return new NativeProvider(
          label,
          requiredEnvVar,
          properties,
          capabilityProperties,
          forcesReasoningTokens,
          reportsCacheCreationTokens,
          false);
    }

    boolean isEnabled() {
      return enabled && System.getenv(requiredEnvVar) != null;
    }

    boolean supports(Capability capability) {
      return capabilityProperties.containsKey(capability);
    }

    Map<String, String> propertiesFor(Capability capability) {
      return capabilityProperties.getOrDefault(capability, Map.of());
    }

    @Override
    public String toString() {
      return label;
    }
  }

  static NativeProvider anthropicDirect(
      String model,
      Map<Capability, Map<String, String>> capabilityProperties,
      boolean forcesReasoningTokens) {
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
        capabilityProperties,
        forcesReasoningTokens,
        true);
  }

  static NativeProvider openaiDirect(
      String apiFamily,
      String model,
      Map<Capability, Map<String, String>> capabilityProperties,
      boolean forcesReasoningTokens) {
    return new NativeProvider(
        "openai-" + apiFamily + "/" + model,
        "OPENAI_API_KEY",
        Map.of(
            "configuration.type",
            "openai",
            "configuration.openai.apiFamily",
            apiFamily,
            "configuration.openai.backend.type",
            "direct",
            "configuration.openai.backend.apiKey",
            envOrPlaceholder("OPENAI_API_KEY"),
            "configuration.openai.model.model",
            model),
        capabilityProperties,
        forcesReasoningTokens,
        false);
  }

  static Stream<NativeProvider> providers() {
    return Stream.of(
            // claude-sonnet-4-6 supports thinking mode "enabled" (explicit budget) — forced
            // thinking, so reasoning tokens are guaranteed.
            anthropicDirect(
                "claude-sonnet-4-6",
                Map.of(
                    Capability.STRUCTURED_OUTPUT, Map.of(),
                    Capability.MULTIMODAL_USER_MESSAGE, Map.of(),
                    Capability.MULTIMODAL_TOOL_RESULT, Map.of(),
                    Capability.PROMPT_CACHING,
                        Map.of("configuration.anthropic.enablePromptCaching", "true"),
                    Capability.REASONING,
                        Map.of(
                            "configuration.anthropic.model.parameters.thinking.mode", "enabled",
                            "configuration.anthropic.model.parameters.thinking.budgetTokens",
                                "2048")),
                true),
            // claude-sonnet-5 does NOT accept "enabled"; its matrix allows "adaptive" (the model
            // decides whether to think), so reasoning tokens are not guaranteed.
            anthropicDirect(
                "claude-sonnet-5",
                Map.of(
                    Capability.STRUCTURED_OUTPUT, Map.of(),
                    Capability.MULTIMODAL_USER_MESSAGE, Map.of(),
                    Capability.MULTIMODAL_TOOL_RESULT, Map.of(),
                    Capability.PROMPT_CACHING,
                        Map.of("configuration.anthropic.enablePromptCaching", "true"),
                    Capability.REASONING,
                        Map.of(
                            "configuration.anthropic.model.parameters.thinking.mode", "adaptive",
                            "configuration.anthropic.model.parameters.effort", "high")),
                false),
            // gpt-5 on the Responses API family supports the full capability set, including the
            // Responses-only server tools (web search, code interpreter). Reasoning effort "high"
            // forces the model to emit reasoning tokens.
            openaiDirect(
                "responses",
                "gpt-5",
                Map.of(
                    Capability.STRUCTURED_OUTPUT, Map.of(),
                    Capability.MULTIMODAL_USER_MESSAGE, Map.of(),
                    Capability.MULTIMODAL_TOOL_RESULT, Map.of(),
                    Capability.PROMPT_CACHING, Map.of(),
                    Capability.REASONING,
                        Map.of("configuration.openai.model.parameters.effort", "high"),
                    Capability.WEB_SEARCH, Map.of("configuration.openai.enableWebSearch", "true"),
                    Capability.CODE_INTERPRETER,
                        Map.of("configuration.openai.enableCodeInterpreter", "true")),
                true),
            // gpt-5 on the Completions API family: reasoning via reasoning_effort (input-only; no
            // server tools, no reasoning content in the response). Effort "high" forces reasoning
            // tokens.
            openaiDirect(
                "completions",
                "gpt-5",
                Map.of(
                    Capability.STRUCTURED_OUTPUT, Map.of(),
                    Capability.MULTIMODAL_USER_MESSAGE, Map.of(),
                    Capability.MULTIMODAL_TOOL_RESULT, Map.of(),
                    Capability.PROMPT_CACHING, Map.of(),
                    Capability.REASONING,
                        Map.of("configuration.openai.model.parameters.effort", "high")),
                true),
            // gpt-4o on the Completions API family supports only the provider-agnostic subset; no
            // reasoning and no server tools (Responses-only).
            openaiDirect(
                "completions",
                "gpt-4o",
                Map.of(
                    Capability.STRUCTURED_OUTPUT, Map.of(),
                    Capability.MULTIMODAL_USER_MESSAGE, Map.of(),
                    Capability.MULTIMODAL_TOOL_RESULT, Map.of(),
                    Capability.PROMPT_CACHING, Map.of()),
                false))
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
        // Empty by default so tool-result scenarios don't leak documents via the user message;
        // the user-message scenario overrides this in its customizer.
        .property("data.userPrompt.documents", "=[]")
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

  /**
   * Asserts substrings on the agent's {@code responseText} read directly from the raw output map,
   * WITHOUT deserializing the whole response. Use this when the persisted agent context contains a
   * {@link io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent} whose
   * abstract {@code Document} the plain test ObjectMapper cannot reconstruct (the multimodal
   * scenario): a full {@code convertValue(..., JobWorkerAgentResponse.class)} would fail on the
   * Document reference, not on the text we actually want to assert.
   */
  void assertResponseTextContains(ProcessInstanceEvent instance, String... expectedSubstrings) {
    assertThat(instance)
        .withAssertionTimeout(PROCESS_TIMEOUT)
        .isCompleted()
        .hasVariableSatisfies(
            AGENT_RESPONSE_VARIABLE,
            Map.class,
            map -> {
              final var responseText = String.valueOf(map.get("responseText"));
              final var textAssert = org.assertj.core.api.Assertions.assertThat(responseText);
              for (final String expected : expectedSubstrings) {
                textAssert.contains(expected);
              }
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
                      org.assertj.core.api.Assertions.assertThat(
                              String.valueOf(map.get("codeName")))
                          .contains(NONCE_CODE_NAME);
                      org.assertj.core.api.Assertions.assertThat(
                              String.valueOf(map.get("clearanceLevel")))
                          .contains(NONCE_CLEARANCE);
                    }));
  }

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
            // Reasoning enablement is model-specific (sonnet-4-6 uses "enabled"+budget, sonnet-5
            // uses "adaptive"+effort) and comes from the provider row, so this scenario stays
            // provider-agnostic and a future OpenAI row can supply its own reasoning props.
            template -> provider.propertiesFor(Capability.REASONING).forEach(template::property));

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
        response -> {
          var responseAssert = JobWorkerAgentResponseAssert.assertThat(response).isReady();
          // Only rows with a forcing thinking mode guarantee reasoning tokens; for adaptive/effort
          // modes the model may answer without billable thinking, so completion + a correct answer
          // is the universal bar (it also proves the reasoning config was accepted by the API).
          if (provider.forcesReasoningTokens()) {
            responseAssert.hasReasoningTokensGreaterThanZero();
          }
          responseAssert.hasResponseTestSatisfying(
              text -> org.assertj.core.api.Assertions.assertThat(text).contains("5"));
        });
  }

  // Long, stable system prompt whose token count must clear OpenAI's automatic-caching minimum
  // (1024 tokens) on its own. The cacheable prefix is the turn-to-turn identical head of the
  // request (system + first user message); OpenAI reports cached_tokens only once that prefix
  // reaches 1024. Each repeated segment below is ~65 tokens, so 24 repeats (~1.5k tokens) clears
  // the threshold with margin. This matters most for the Completions family: unlike Responses
  // (which replays encrypted reasoning items on turn 2, inflating the prefix), Completions sends
  // only system+user, so the system prompt itself has to be large enough — otherwise cached_tokens
  // stays 0 and the cache-read assertion can never pass.
  private static final String LONG_SYSTEM_PROMPT =
      ("You are an assistant operating under a detailed classified-information handling protocol. "
              + "Always be precise, never fabricate facts, and when the user asks for an internal "
              + "or classified code name you must call the Lookup Classified Fact tool and quote "
              + "its result verbatim without paraphrasing. Follow every rule in this protocol "
              + "carefully and consistently across the whole conversation. ")
          .repeat(24);

  static Stream<NativeProvider> providersWithPromptCaching() {
    return providers().filter(p -> p.supports(Capability.PROMPT_CACHING));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("providersWithPromptCaching")
  void promptCachingReportsCacheReadAndWriteTokens(NativeProvider provider) {
    // Pass the long system prompt as a process VARIABLE referenced by FEEL rather than baking a
    // ~5KB string literal into the element template (baking it produced a deploy-time
    // ConnectionClosedException). The resolved system prompt is identical every turn, so the
    // cacheable prefix stays byte-stable.
    var model =
        buildModel(
            provider,
            AI_AGENT_JOB_WORKER_V2_ELEMENT_TEMPLATE_PATH,
            BPMN_RESOURCE,
            DEFAULT_SYSTEM_PROMPT,
            template -> {
              // Caching enablement is provider-specific (Anthropic toggles a flag; OpenAI caches
              // automatically) and comes from the provider row.
              provider.propertiesFor(Capability.PROMPT_CACHING).forEach(template::property);
              template.property("data.systemPrompt.prompt", "=longSystemPrompt");
            });

    var instance =
        startAgent(
            model,
            PROCESS_ID,
            Map.of(
                "userPrompt",
                "What is the internal project code name? Use your lookup tool.",
                "longSystemPrompt",
                LONG_SYSTEM_PROMPT));

    // The tool call forces a second model call: turn 1 writes the cache (system+tools prefix),
    // turn 2 re-sends the byte-identical prefix and reads it. Cumulative run metrics carry both.
    assertAgentResponse(
        instance,
        response -> {
          var agentAssert = JobWorkerAgentResponseAssert.assertThat(response).isReady();
          // Cache-creation (write) tokens are only reported by providers that expose a distinct
          // write metric (Anthropic); OpenAI's automatic caching reports read-only, so this
          // assertion would never pass for it.
          if (provider.reportsCacheCreationTokens()) {
            agentAssert.hasCacheCreationTokensGreaterThanZero();
          }
          agentAssert
              .hasCacheReadTokensGreaterThanZero()
              .hasResponseTestSatisfying(
                  text ->
                      org.assertj.core.api.Assertions.assertThat(text).contains(NONCE_CODE_NAME));
        });
  }

  private static final String DOC_DIR = "document-tool-call-results/";
  private static final String DOC_PROJECT_LAUNCH = DOC_DIR + "project-launch.pdf";
  private static final String DOC_HEADCOUNT_REPORT = DOC_DIR + "headcount-report.pdf";
  private static final String DOC_AUTHOR_INFO = DOC_DIR + "author-info.pdf";
  private static final String DOCUMENT_BPMN_RESOURCE = "classpath:document-tool-call-results.bpmn";
  private static final String DOCUMENT_PROCESS_ID = "CPT_Document_Tool_Call_Results";
  private static final String DOCUMENT_SYSTEM_PROMPT =
      "You are a document analyst. Use the available tools to retrieve and analyze documents. "
          + "Always quote specific facts, numbers, dates, and names found in the documents.";

  static Stream<NativeProvider> providersWithMultimodalUserMessage() {
    return providers().filter(p -> p.supports(Capability.MULTIMODAL_USER_MESSAGE));
  }

  static Stream<NativeProvider> providersWithMultimodalToolResult() {
    return providers().filter(p -> p.supports(Capability.MULTIMODAL_TOOL_RESULT));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("providersWithMultimodalUserMessage")
  void documentInUserMessageIsReadByModel(NativeProvider provider, WireMockRuntimeInfo wireMock) {
    stubPdfDownloads();

    // Reuses the document BPMN (which downloads downloadUrls into `downloadedFiles` before the
    // agent) but routes the single downloaded PDF into the user message instead of a tool result,
    // so this exercises the user-message multimodal path rather than the tool-result path.
    var model =
        buildModel(
            provider,
            AI_AGENT_JOB_WORKER_V2_ELEMENT_TEMPLATE_PATH,
            DOCUMENT_BPMN_RESOURCE,
            "You are a document analyst. A document is attached directly to the user's message. "
                + "Answer using only that attached document and do not call any tools. Always "
                + "quote specific facts, numbers, dates, and names found in the document.",
            template -> template.property("data.userPrompt.documents", "=downloadedFiles"));

    var instance =
        startAgent(
            model,
            DOCUMENT_PROCESS_ID,
            Map.of(
                "userPrompt",
                "What is the internal project code name mentioned in the attached document? "
                    + "Quote it exactly.",
                "downloadUrls",
                List.of(wireMock.getHttpBaseUrl() + "/" + DOC_PROJECT_LAUNCH)));

    assertResponseTextContains(instance, "Zypherion");
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

    assertResponseTextContains(instance, "Zypherion", "847", "Kael Thrennix");
  }

  static Stream<NativeProvider> providersWithCodeInterpreter() {
    return providers().filter(p -> p.supports(Capability.CODE_INTERPRETER));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("providersWithCodeInterpreter")
  void codeInterpreterComputesDeterministicResult(NativeProvider provider) {
    var model =
        buildModel(
            provider,
            AI_AGENT_JOB_WORKER_V2_ELEMENT_TEMPLATE_PATH,
            BPMN_RESOURCE,
            "You may run code to compute exact answers.",
            template ->
                provider.propertiesFor(Capability.CODE_INTERPRETER).forEach(template::property));

    var instance =
        startAgent(
            model,
            PROCESS_ID,
            Map.of(
                "userPrompt",
                "Compute 987654321 * 123456789 exactly using code. Reply with just the number."));

    // Deterministic text is the hard gate; the provider content block additionally proves the
    // code_interpreter_call output item was captured as a structural round-trip witness.
    assertAgentResponse(
        instance,
        response ->
            JobWorkerAgentResponseAssert.assertThat(response)
                .isReady()
                .hasResponseTestSatisfying(
                    text ->
                        org.assertj.core.api.Assertions.assertThat(text)
                            .contains("121932631112635269"))
                .hasProviderContentBlockOfType("openai", "code_interpreter_call"));
  }

  static Stream<NativeProvider> providersWithWebSearch() {
    return providers().filter(p -> p.supports(Capability.WEB_SEARCH));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("providersWithWebSearch")
  void webSearchCompletesAndRoundTrips(NativeProvider provider) {
    var model =
        buildModel(
            provider,
            AI_AGENT_JOB_WORKER_V2_ELEMENT_TEMPLATE_PATH,
            BPMN_RESOURCE,
            "Use web search when you need current information.",
            template -> provider.propertiesFor(Capability.WEB_SEARCH).forEach(template::property));

    var instance =
        startAgent(
            model,
            PROCESS_ID,
            Map.of(
                "userPrompt",
                "Search the web for the current stable version of the Camunda 8 documentation "
                    + "and briefly state what you found."));

    // Content is non-deterministic (live web search), so completion itself is the primary
    // round-trip witness: a failed server-tool block replay would 400 and the process would not
    // complete. The provider content block additionally proves the web_search_call output item
    // was captured (no DocumentContent is involved here, so the full typed response, unlike the
    // multimodal scenario, deserializes safely via assertAgentResponse).
    assertAgentResponse(
        instance,
        response ->
            JobWorkerAgentResponseAssert.assertThat(response)
                .isReady()
                .hasProviderContentBlockOfType("openai", "web_search_call"));
  }
}
