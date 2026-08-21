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
import static io.camunda.connector.e2e.agenticai.aiagent.AgentTestFixtures.AGENT_RESPONSE_VARIABLE;
import static io.camunda.connector.e2e.agenticai.aiagent.AgentTestFixtures.AI_AGENT_SUB_PROCESS_V2_ELEMENT_TEMPLATE_PATH;
import static io.camunda.process.test.api.CamundaAssert.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.connector.agenticai.aiagent.model.AgentSubProcessResponse;
import io.camunda.connector.e2e.BpmnFile;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.connector.e2e.agenticai.BpmnUtil;
import io.camunda.connector.e2e.agenticai.CamundaDocumentTestConfiguration;
import io.camunda.connector.e2e.agenticai.assertj.AgentSubProcessResponseAssert;
import io.camunda.connector.e2e.app.TestConnectorRuntimeApplication;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
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
 * Cross-provider real-API acceptance safety net for the native (v2) provider path. Local-only: runs
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
      "camunda.connector.agenticai.aiagent.chat-model.api.default-timeout=PT2M",
      "logging.level.io.camunda.connector.agenticai=TRACE"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@CamundaSpringProcessTest
@Import(CamundaDocumentTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "RUN_NATIVE_LLM_E2E", matches = "true")
@WireMockTest
class RealProviderApiSmokeIT {

  static final String BPMN_RESOURCE = "classpath:real-provider-api-smoke.bpmn";
  static final String FORM_RESOURCE = "ai-agent-chat-user-feedback.form";
  static final String PROCESS_ID = "real_provider_api_smoke";
  static final String TOOL_JOB_TYPE = "lookup-classified-fact";
  static final Duration PROCESS_TIMEOUT = Duration.ofMinutes(3);
  private static final Duration INCIDENT_POLL_TIMEOUT = Duration.ofSeconds(1);

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

  private static final String RESPONSE_SCHEMA =
      "{\"type\":\"object\","
          + "\"properties\":{\"codeName\":{\"type\":\"string\"},\"clearanceLevel\":{\"type\":\"string\"}},"
          + "\"required\":[\"codeName\",\"clearanceLevel\"]}";

  // Repeated to clear the largest minimum cacheable-prefix size among providers under test:
  // Anthropic needs ~1024 tokens (Sonnet-class models), Gemini needs ~4096. Each repeat is ~65
  // tokens, so 80 repeats (~5200 tokens) gives comfortable margin over both.
  private static final String LONG_SYSTEM_PROMPT =
      """
      You are an assistant operating under a detailed classified-information handling protocol. \
      Always be precise, never fabricate facts, and when the user asks for an internal \
      or classified code name you must call the Lookup Classified Fact tool and quote \
      its result verbatim without paraphrasing. Follow every rule in this protocol \
      carefully and consistently across the whole conversation. \
      """
          .repeat(80);

  private static final String DOC_DIR = "document-tool-call-results/";
  private static final String DOC_PROJECT_LAUNCH = DOC_DIR + "project-launch.pdf";
  private static final String DOC_HEADCOUNT_REPORT = DOC_DIR + "headcount-report.pdf";
  private static final String DOC_AUTHOR_INFO = DOC_DIR + "author-info.pdf";
  private static final String DOCUMENT_BPMN_RESOURCE = "classpath:document-tool-call-results.bpmn";
  private static final String DOCUMENT_PROCESS_ID = "CPT_Document_Tool_Call_Results";
  private static final String DOCUMENT_SYSTEM_PROMPT =
      "You are a document analyst. Use the available tools to retrieve and analyze documents. "
          + "Always quote specific facts, numbers, dates, and names found in the documents.";

  private final ObjectMapper objectMapper = ConnectorsObjectMapperSupplier.getCopy();

  @Autowired CamundaClient camundaClient;
  @Autowired CamundaProcessTestContext processTestContext;
  @Autowired ResourceLoader resourceLoader;
  @TempDir File tempDir;

  enum Capability {
    STRUCTURED_OUTPUT,
    REASONING,
    PROMPT_CACHING,
    MULTIMODAL_USER_MESSAGE
  }

  /**
   * A provider row in the acceptance matrix. {@code capabilityProperties} maps each capability this
   * row supports to the MODEL-SPECIFIC element-template properties that enable that capability for
   * this model. The map is empty when the capability needs no provider-specific enablement
   * (structured output is enabled by the shared {@code data.response.format.*} props the scenario
   * sets; multimodal just needs the document BPMN). Reasoning and prompt caching are enabled
   * differently per model, so their enablement lives HERE rather than being hard-coded in the
   * scenario. A capability absent from the map means the row does not support it, so its scenario
   * is skipped for this row.
   */
  record Provider(
      String label,
      List<String> requiredEnvVars,
      boolean enabled,
      Map<String, String> properties,
      Map<Capability, Map<String, String>> capabilityProperties,
      // Whether this row reports a distinct cache-creation (write) token count in addition to
      // cache-read; gates the cache-creation assertion in the prompt-caching scenario.
      boolean reportsCacheCreationTokens) {

    Provider(
        String label,
        List<String> requiredEnvVars,
        Map<String, String> properties,
        Map<Capability, Map<String, String>> capabilityProperties,
        boolean reportsCacheCreationTokens) {
      this(
          label,
          requiredEnvVars,
          true,
          properties,
          capabilityProperties,
          reportsCacheCreationTokens);
    }

    Provider disabled() {
      return new Provider(
          label,
          requiredEnvVars,
          false,
          properties,
          capabilityProperties,
          reportsCacheCreationTokens);
    }

    boolean isEnabled() {
      // requiredEnvVars is empty for local providers that need no API key, just a URL.
      return enabled
          && (requiredEnvVars.isEmpty()
              || requiredEnvVars.stream().allMatch(v -> System.getenv(v) != null));
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

  static Provider anthropicApi(
      String model, Map<Capability, Map<String, String>> capabilityProperties) {
    return new Provider(
        "anthropic-api/" + model,
        List.of("ANTHROPIC_API_KEY"),
        Map.of(
            "provider.type",
            "anthropic",
            "provider.anthropic.backend.type",
            "anthropic-api",
            "provider.anthropic.backend.anthropic.apiKey",
            envOrPlaceholder("ANTHROPIC_API_KEY"),
            "provider.anthropic.model.model",
            model),
        capabilityProperties,
        true);
  }

  // Bedrock Mantle is Anthropic's own Messages API (the same wire format as anthropic-api),
  // hosted on/by AWS: requests are SigV4-signed and sent to a Bedrock Mantle endpoint instead of
  // api.anthropic.com, but the connector performs no body/path/response translation between the
  // two.
  static Provider anthropicBedrockMantle(
      String model, Map<Capability, Map<String, String>> capabilityProperties) {
    return new Provider(
        "anthropic-bedrock-mantle/" + model,
        List.of("ANTHROPIC_BEDROCK_API_KEY"),
        Map.of(
            "provider.type",
            "anthropic",
            "provider.anthropic.backend.type",
            "aws-bedrock-mantle",
            "provider.anthropic.backend.awsBedrockMantle.region",
            envOrDefault("ANTHROPIC_BEDROCK_REGION", "us-east-1"),
            "provider.anthropic.backend.awsBedrockMantle.authentication.type",
            "apiKey",
            "provider.anthropic.backend.awsBedrockMantle.authentication.apiKey",
            envOrPlaceholder("ANTHROPIC_BEDROCK_API_KEY"),
            "provider.anthropic.model.model",
            "anthropic." + model),
        capabilityProperties,
        true);
  }

  static Provider bedrockConverse(
      String model, Map<Capability, Map<String, String>> capabilityProperties) {
    return new Provider(
        "bedrock-converse/" + model,
        List.of("AWS_BEDROCK_API_KEY"),
        Map.of(
            "provider.type",
            "bedrock",
            "provider.bedrock.region",
            envOrDefault("AWS_BEDROCK_REGION", "us-east-1"),
            "provider.bedrock.authentication.type",
            "apiKey",
            "provider.bedrock.authentication.apiKey",
            envOrPlaceholder("AWS_BEDROCK_API_KEY"),
            "provider.bedrock.model.model",
            model),
        capabilityProperties,
        true);
  }

  // Always targets the openai-api backend, mirroring anthropicApi above.
  static Provider openAiCompletionsApi(
      String model, Map<Capability, Map<String, String>> capabilityProperties) {
    return openAiApi("completions", model, capabilityProperties);
  }

  static Provider openAiResponsesApi(
      String model, Map<Capability, Map<String, String>> capabilityProperties) {
    return openAiApi("responses", model, capabilityProperties);
  }

  private static Provider openAiApi(
      String family, String model, Map<Capability, Map<String, String>> capabilityProperties) {
    return new Provider(
        "openai-api/" + family + "/" + model,
        List.of("OPENAI_API_KEY"),
        Map.of(
            "provider.type",
            "openai",
            "provider.openai.backend.type",
            "openai-api",
            "provider.openai.backend.openai.apiKey",
            envOrPlaceholder("OPENAI_API_KEY"),
            "provider.openai.api.type",
            family,
            "provider.openai.model.model",
            model),
        capabilityProperties,
        // OpenAI reports a cache-read token count but no distinct cache-creation (write) metric
        // for either API family, unlike Anthropic.
        false);
  }

  // Foundry proxies the exact same OpenAI Responses/Completions wire format behind an Azure
  // resource, so capabilities and reported usage metrics mirror openai-api -- only auth/endpoint
  // differ. `model` doubles as the Azure deployment name (see native-providers.md), so this
  // requires a deployment literally named after each model string below to exist on the
  // configured resource.
  static Provider openAiFoundryCompletionsApi(
      String model, Map<Capability, Map<String, String>> capabilityProperties) {
    return openAiFoundryApi("completions", model, capabilityProperties);
  }

  static Provider openAiFoundryResponsesApi(
      String model, Map<Capability, Map<String, String>> capabilityProperties) {
    return openAiFoundryApi("responses", model, capabilityProperties);
  }

  private static Provider openAiFoundryApi(
      String family, String model, Map<Capability, Map<String, String>> capabilityProperties) {
    return new Provider(
        "openai-foundry/" + family + "/" + model,
        List.of("OPENAI_FOUNDRY_API_KEY", "OPENAI_FOUNDRY_ENDPOINT"),
        Map.of(
            "provider.type",
            "openai",
            "provider.openai.backend.type",
            "foundry",
            "provider.openai.backend.foundry.endpoint",
            envOrPlaceholder("OPENAI_FOUNDRY_ENDPOINT"),
            "provider.openai.backend.foundry.authentication.type",
            "apiKey",
            "provider.openai.backend.foundry.authentication.apiKey",
            envOrPlaceholder("OPENAI_FOUNDRY_API_KEY"),
            "provider.openai.api.type",
            family,
            "provider.openai.model.model",
            model),
        capabilityProperties,
        // Same wire format and usage-reporting shape as openai-api: a cache-read count, no
        // distinct cache-creation (write) metric.
        false);
  }

  static Provider googleGeminiApi(
      String model, Map<Capability, Map<String, String>> capabilityProperties) {
    return new Provider(
        "google-gemini-api/" + model,
        List.of("GOOGLE_GEMINI_API_KEY"),
        Map.of(
            "provider.type",
            "google-gemini",
            "provider.googleGemini.backend.type",
            "google-gemini-api",
            "provider.googleGemini.backend.googleGeminiApi.apiKey",
            envOrPlaceholder("GOOGLE_GEMINI_API_KEY"),
            "provider.googleGemini.model.model",
            model),
        capabilityProperties,
        false);
  }

  static Provider googleVertexAi(
      String model, Map<Capability, Map<String, String>> capabilityProperties) {
    return new Provider(
        "google-vertex-ai/" + model,
        List.of(
            "GOOGLE_VERTEX_AI_PROJECT_ID",
            "GOOGLE_VERTEX_AI_REGION",
            "GOOGLE_VERTEX_AI_SERVICE_ACCOUNT_JSON"),
        Map.of(
            "provider.type",
            "google-gemini",
            "provider.googleGemini.backend.type",
            "google-vertex-ai",
            "provider.googleGemini.backend.googleVertexAi.projectId",
            envOrPlaceholder("GOOGLE_VERTEX_AI_PROJECT_ID"),
            "provider.googleGemini.backend.googleVertexAi.region",
            envOrPlaceholder("GOOGLE_VERTEX_AI_REGION"),
            "provider.googleGemini.backend.googleVertexAi.authentication.type",
            "serviceAccountCredentials",
            "provider.googleGemini.backend.googleVertexAi.authentication.jsonKey",
            envOrPlaceholder("GOOGLE_VERTEX_AI_SERVICE_ACCOUNT_JSON"),
            "provider.googleGemini.model.model",
            model),
        capabilityProperties,
        false);
  }

  static Stream<Provider> providers() {
    return Stream.of(
            // claude-sonnet-4-6 only supports thinking mode "enabled" (explicit budget) — the model
            // always emits a thinking block regardless of prompt difficulty.
            anthropicApi(
                "claude-sonnet-4-6",
                Map.of(
                    Capability.STRUCTURED_OUTPUT, Map.of(),
                    Capability.MULTIMODAL_USER_MESSAGE, Map.of(),
                    Capability.PROMPT_CACHING,
                        Map.of("provider.anthropic.model.parameters.promptCaching.enabled", "true"),
                    Capability.REASONING,
                        Map.of(
                            "provider.anthropic.model.parameters.thinking.mode", "enabled",
                            "provider.anthropic.model.parameters.thinking.budgetTokens", "2048"))),
            // claude-sonnet-5 does NOT accept "enabled"; it only allows "adaptive" (the model
            // decides whether to think). At effort "high" it reliably thinks on a genuinely
            // multi-step prompt, but this is model choice, not an API-level guarantee.
            anthropicApi(
                "claude-sonnet-5",
                Map.of(
                    Capability.STRUCTURED_OUTPUT, Map.of(),
                    Capability.MULTIMODAL_USER_MESSAGE, Map.of(),
                    Capability.PROMPT_CACHING,
                        Map.of("provider.anthropic.model.parameters.promptCaching.enabled", "true"),
                    Capability.REASONING,
                        Map.of(
                            "provider.anthropic.model.parameters.thinking.mode", "adaptive",
                            "provider.anthropic.model.parameters.effort", "high"))),
            // Same model/capability config as the anthropic-api claude-sonnet-5 row above, minus
            // structured output: Bedrock Mantle rejects output_config.format with a 400. AWS docs
            // confirm this endpoint doesn't support it:
            // https://docs.aws.amazon.com/bedrock/latest/userguide/claude-messages-structured-outputs.html
            anthropicBedrockMantle(
                "claude-sonnet-5",
                Map.of(
                    Capability.MULTIMODAL_USER_MESSAGE, Map.of(),
                    Capability.PROMPT_CACHING,
                        Map.of("provider.anthropic.model.parameters.promptCaching.enabled", "true"),
                    Capability.REASONING,
                        Map.of(
                            "provider.anthropic.model.parameters.thinking.mode", "adaptive",
                            "provider.anthropic.model.parameters.effort", "high"))),
            // Amazon's own Nova 2 Lite Converse model (cheap tier): multimodal + prompt caching +
            // reasoning. STRUCTURED_OUTPUT is deliberately NOT declared: AWS rejects outputConfig
            // for this model ("This model doesn't support the outputConfig field"), matching its
            // model card ("Structured outputs" listed as Not Supported). Disabled for now: prone
            // to misspelling nonce words in its output.
            bedrockConverse(
                    "us.amazon.nova-2-lite-v1:0",
                    Map.of(
                        Capability.MULTIMODAL_USER_MESSAGE, Map.of(),
                        Capability.PROMPT_CACHING,
                            Map.of(
                                "provider.bedrock.model.parameters.promptCaching.enabled", "true"),
                        Capability.REASONING,
                            Map.of(
                                "provider.bedrock.bodyProperties",
                                "={reasoningConfig: {type: \"enabled\", maxReasoningEffort: \"medium\"}}")))
                .disabled(),
            // A non-Amazon Converse model: gpt-oss-120b's model card lists text-only input
            // modalities, and neither structured output nor explicit prompt caching is documented
            // for it, so those capabilities are left undeclared. Its reasoning uses a
            // "reasoning_effort" shape (no "type", no budget), proving a third incompatible
            // reasoning request shape works through the same provider-agnostic scenario.
            bedrockConverse(
                "openai.gpt-oss-120b-1:0",
                Map.of(
                    Capability.REASONING,
                    Map.of("provider.bedrock.bodyProperties", "={reasoning_effort: \"medium\"}"))),
            // Claude via the native Converse path: a permanent cross-check that the generic
            // sdkFields() codec round-trips Anthropic's own block shapes correctly too. Global
            // cross-region inference ID (no in-region endpoint for this model). claude-sonnet-5
            // only
            // allows thinking type "adaptive", not "enabled". STRUCTURED_OUTPUT is deliberately NOT
            // declared: outputConfig.textFormat is a genuine Converse field (confirmed via the
            // SDK's
            // own ConverseRequest.outputConfig()), but AWS's Converse structured-output model
            // allow-list (docs.aws.amazon.com/bedrock/latest/userguide/structured-output.html) does
            // not yet include claude-sonnet-5 — the model itself rejects it with a 400
            // ("output_config.format: Extra inputs are not permitted"), confirmed against a real
            // API
            // call.
            bedrockConverse(
                "global.anthropic.claude-sonnet-5",
                Map.of(
                    Capability.MULTIMODAL_USER_MESSAGE, Map.of(),
                    Capability.PROMPT_CACHING,
                        Map.of("provider.bedrock.model.parameters.promptCaching.enabled", "true"),
                    Capability.REASONING,
                        Map.of(
                            "provider.bedrock.bodyProperties",
                            "={thinking: {type: \"adaptive\"}}"))),
            // Responses mirrors Anthropic's reasoning pattern: it returns a ReasoningContent
            // domain block in addition to reasoning_tokens, so REASONING is exercisable here.
            openAiResponsesApi(
                "gpt-5.5",
                Map.of(
                    Capability.STRUCTURED_OUTPUT, Map.of(),
                    Capability.MULTIMODAL_USER_MESSAGE, Map.of(),
                    Capability.PROMPT_CACHING, Map.of(),
                    Capability.REASONING, Map.of("provider.openai.api.responses.effort", "high"))),
            // REASONING omitted: Completions never returns a ReasoningContent block to assert on.
            openAiCompletionsApi(
                "gpt-5.5",
                Map.of(
                    Capability.STRUCTURED_OUTPUT, Map.of(),
                    Capability.MULTIMODAL_USER_MESSAGE, Map.of(),
                    Capability.PROMPT_CACHING, Map.of())),
            // An older model, on both API families, for completeness.
            openAiResponsesApi(
                "gpt-4.1",
                Map.of(
                    Capability.STRUCTURED_OUTPUT, Map.of(),
                    Capability.MULTIMODAL_USER_MESSAGE, Map.of(),
                    Capability.PROMPT_CACHING, Map.of())),
            openAiCompletionsApi(
                "gpt-4.1",
                Map.of(
                    Capability.STRUCTURED_OUTPUT, Map.of(),
                    Capability.MULTIMODAL_USER_MESSAGE, Map.of(),
                    Capability.PROMPT_CACHING, Map.of())),
            // Same models/capabilities as the openai-api rows above, via the foundry backend.
            openAiFoundryResponsesApi(
                "gpt-5.5",
                Map.of(
                    Capability.STRUCTURED_OUTPUT, Map.of(),
                    Capability.MULTIMODAL_USER_MESSAGE, Map.of(),
                    Capability.PROMPT_CACHING, Map.of(),
                    Capability.REASONING, Map.of("provider.openai.api.responses.effort", "high"))),
            openAiFoundryCompletionsApi(
                "gpt-5.5",
                Map.of(
                    Capability.STRUCTURED_OUTPUT, Map.of(),
                    Capability.MULTIMODAL_USER_MESSAGE, Map.of(),
                    Capability.PROMPT_CACHING, Map.of())),
            openAiFoundryResponsesApi(
                "gpt-4.1",
                Map.of(
                    Capability.STRUCTURED_OUTPUT, Map.of(),
                    Capability.MULTIMODAL_USER_MESSAGE, Map.of(),
                    Capability.PROMPT_CACHING, Map.of())),
            openAiFoundryCompletionsApi(
                "gpt-4.1",
                Map.of(
                    Capability.STRUCTURED_OUTPUT, Map.of(),
                    Capability.MULTIMODAL_USER_MESSAGE, Map.of(),
                    Capability.PROMPT_CACHING, Map.of())),
            // Gemini 3.x models use a qualitative thinkingLevel rather than a token budget.
            // No PROMPT_CACHING claim, since it has not been manually verified against a live
            // Gemini endpoint yet.
            googleGeminiApi(
                "gemini-3.7-flash",
                Map.of(
                    Capability.STRUCTURED_OUTPUT,
                    Map.of(),
                    Capability.MULTIMODAL_USER_MESSAGE,
                    Map.of(),
                    Capability.PROMPT_CACHING,
                    Map.of(),
                    Capability.REASONING,
                    Map.of(
                        "provider.googleGemini.model.parameters.thinking.thinkingLevel", "high"))),
            // No PROMPT_CACHING claim, since it has not been manually verified against a live
            // Vertex endpoint yet. STRUCTURED_OUTPUT, REASONING and the multimodal capabilities are
            // claimed because the request/response converters contain no backend branching at all
            // (only GeminiChatModelFactory branches on backend, for client/auth/base-URL
            // construction), so this claim is expected to hold. Model id matches the existing
            // Vertex AI e2e precedent on this branch (BaseGeminiVertexAiNativeTaskV2Test).
            googleVertexAi(
                "gemini-3.7-flash",
                Map.of(
                    Capability.STRUCTURED_OUTPUT, Map.of(),
                    Capability.MULTIMODAL_USER_MESSAGE, Map.of(),
                    Capability.PROMPT_CACHING, Map.of(),
                    Capability.REASONING,
                        Map.of(
                            "provider.googleGemini.model.parameters.thinking.thinkingLevel",
                            "high"))),
            // Gemini 2.5 models use a numeric thinkingBudget rather than a qualitative level.
            // No STRUCTURED_OUTPUT claim: the Gemini API rejects a JSON response mime type
            // whenever function declarations are also present in the request, and this scenario
            // always wires the Lookup_Classified_Fact tool via the ad-hoc sub-process BPMN
            // (confirmed with a live 400 INVALID_ARGUMENT: "Function calling with a response mime
            // type: 'application/json' is unsupported" on gemini-2.5-pro).
            googleGeminiApi(
                "gemini-2.5-pro",
                Map.of(
                    Capability.MULTIMODAL_USER_MESSAGE, Map.of(),
                    Capability.PROMPT_CACHING, Map.of(),
                    Capability.REASONING,
                        Map.of(
                            "provider.googleGemini.model.parameters.thinking.thinkingBudget",
                            "24576"))),
            googleVertexAi(
                "gemini-2.5-pro",
                Map.of(
                    Capability.MULTIMODAL_USER_MESSAGE, Map.of(),
                    Capability.PROMPT_CACHING, Map.of(),
                    Capability.REASONING,
                        Map.of(
                            "provider.googleGemini.model.parameters.thinking.thinkingBudget",
                            "24576"))))
        .filter(Provider::isEnabled);
  }

  static Stream<Provider> providersWithStructuredOutput() {
    return providers().filter(p -> p.supports(Capability.STRUCTURED_OUTPUT));
  }

  static Stream<Provider> providersWithReasoning() {
    return providers().filter(p -> p.supports(Capability.REASONING));
  }

  static Stream<Provider> providersWithPromptCaching() {
    return providers().filter(p -> p.supports(Capability.PROMPT_CACHING));
  }

  static Stream<Provider> providersWithMultimodalUserMessage() {
    return providers().filter(p -> p.supports(Capability.MULTIMODAL_USER_MESSAGE));
  }

  private static String envOrPlaceholder(String envVar) {
    return System.getenv().getOrDefault(envVar, "NOT_SET");
  }

  private static String envOrDefault(String envVar, String defaultValue) {
    return System.getenv().getOrDefault(envVar, defaultValue);
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

  @ParameterizedTest(name = "{0}", allowZeroInvocations = true)
  @MethodSource("providers")
  void toolCallLoopSurfacesPlantedFact(Provider provider) {
    var model =
        buildModel(
            provider,
            AI_AGENT_SUB_PROCESS_V2_ELEMENT_TEMPLATE_PATH,
            BPMN_RESOURCE,
            DEFAULT_SYSTEM_PROMPT,
            template -> {});

    var instance =
        startAgent(
            model,
            PROCESS_ID,
            DEFAULT_SYSTEM_PROMPT,
            Map.of("userPrompt", "What is the internal project code name? Use your lookup tool."));
    completeUserFeedback(instance, Map.of("userSatisfied", true));

    assertAgentResponse(
        instance,
        response ->
            AgentSubProcessResponseAssert.assertThat(response)
                .isReady()
                .hasResponseTextSatisfying(
                    text ->
                        Assertions.assertThat(normalizeDashes(text)).contains(NONCE_CODE_NAME)));
  }

  @ParameterizedTest(name = "{0}", allowZeroInvocations = true)
  @MethodSource("providersWithStructuredOutput")
  void structuredOutputReturnsSchemaConformingJson(Provider provider) {
    var model =
        buildModel(
            provider,
            AI_AGENT_SUB_PROCESS_V2_ELEMENT_TEMPLATE_PATH,
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
            DEFAULT_SYSTEM_PROMPT,
            Map.of(
                "userPrompt",
                "Look up the internal project code name and clearance level and return them."));
    completeUserFeedback(instance, Map.of("userSatisfied", true));

    assertAgentResponse(
        instance,
        response ->
            AgentSubProcessResponseAssert.assertThat(response)
                .isReady()
                .hasResponseJsonSatisfying(
                    json -> {
                      @SuppressWarnings("unchecked")
                      var map = (Map<String, Object>) json;
                      Assertions.assertThat(map).containsKeys("codeName", "clearanceLevel");
                      Assertions.assertThat(normalizeDashes(String.valueOf(map.get("codeName"))))
                          .contains(NONCE_CODE_NAME);
                      Assertions.assertThat(
                              normalizeDashes(String.valueOf(map.get("clearanceLevel"))))
                          .contains(NONCE_CLEARANCE);
                    }));
  }

  @ParameterizedTest(name = "{0}", allowZeroInvocations = true)
  @MethodSource("providersWithReasoning")
  void reasoningEnabledProducesReasoningContent(Provider provider) {
    var model =
        buildModel(
            provider,
            AI_AGENT_SUB_PROCESS_V2_ELEMENT_TEMPLATE_PATH,
            BPMN_RESOURCE,
            "You are a careful reasoner. Think step by step before answering. Before providing your final answer, break down your reasoning step-by-step.",
            template -> provider.propertiesFor(Capability.REASONING).forEach(template::property));

    var instance =
        startAgent(
            model,
            PROCESS_ID,
            "You are a careful reasoner. Think step by step before answering. Before providing your final answer, break down your reasoning step-by-step.",
            Map.of(
                "userPrompt",
                "A farmer has chickens and rabbits. Together they have 35 heads and 94 legs. How "
                    + "many chickens are there? Reply with just the number."));
    completeUserFeedback(instance, Map.of("userSatisfied", true));

    assertAgentResponse(
        instance,
        response ->
            AgentSubProcessResponseAssert.assertThat(response)
                .isReady()
                .hasReasoningContent()
                .hasResponseTextSatisfying(text -> Assertions.assertThat(text).contains("23")));
  }

  @ParameterizedTest(name = "{0}", allowZeroInvocations = true)
  @MethodSource("providersWithPromptCaching")
  void promptCachingReportsCacheReadAndWriteTokens(Provider provider) {
    var model =
        buildModel(
            provider,
            AI_AGENT_SUB_PROCESS_V2_ELEMENT_TEMPLATE_PATH,
            BPMN_RESOURCE,
            DEFAULT_SYSTEM_PROMPT,
            template -> {
              provider.propertiesFor(Capability.PROMPT_CACHING).forEach(template::property);
              template.property("data.systemPrompt.prompt", "=longSystemPrompt");
            });

    var instance =
        startAgent(
            model,
            PROCESS_ID,
            DEFAULT_SYSTEM_PROMPT,
            Map.of(
                "userPrompt",
                "What is the internal project code name? Use your lookup tool.",
                "longSystemPrompt",
                LONG_SYSTEM_PROMPT));
    completeUserFeedback(instance, Map.of("userSatisfied", true));

    // The tool call forces a second model call: turn 1 writes the cache, turn 2 reads it.
    assertAgentResponse(
        instance,
        response -> {
          var agentAssert = AgentSubProcessResponseAssert.assertThat(response).isReady();
          if (provider.reportsCacheCreationTokens()) {
            agentAssert.metricsSatisfy(
                metrics ->
                    Assertions.assertThat(metrics.tokenUsage().cacheCreationTokenCount())
                        .as("cache creation token count")
                        .isPositive());
          }
          agentAssert
              .metricsSatisfy(
                  metrics ->
                      Assertions.assertThat(metrics.tokenUsage().cacheReadTokenCount())
                          .as("cache read token count")
                          .isPositive())
              .hasResponseTextSatisfying(
                  text -> Assertions.assertThat(normalizeDashes(text)).contains(NONCE_CODE_NAME));
        });
  }

  /** Re-entry test: catches a completed assistant text turn getting replayed incorrectly. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("providers")
  void userFeedbackLoopReplaysAssistantTextOnFollowUp(Provider provider) {
    var model =
        buildModel(
            provider,
            AI_AGENT_SUB_PROCESS_V2_ELEMENT_TEMPLATE_PATH,
            BPMN_RESOURCE,
            DEFAULT_SYSTEM_PROMPT,
            template ->
                template.property(
                    "data.userPrompt.prompt",
                    "=if (is defined(followUpInput)) then followUpInput else userPrompt"));

    var instance =
        startAgent(
            model,
            PROCESS_ID,
            DEFAULT_SYSTEM_PROMPT,
            Map.of("userPrompt", "What is the internal project code name? Use your lookup tool."));

    // Turn 1 completes with a plain text answer - no follow-up tool call.
    completeUserFeedback(
        instance,
        Map.of(
            "userSatisfied",
            false,
            "followUpInput",
            "Also tell me the clearance level you just found, in one short sentence."));

    // Turn 2's request replays turn 1's completed assistant text message from history.
    completeUserFeedback(instance, Map.of("userSatisfied", true));

    assertAgentResponse(
        instance,
        response ->
            AgentSubProcessResponseAssert.assertThat(response)
                .isReady()
                .hasResponseTextSatisfying(
                    text ->
                        Assertions.assertThat(normalizeDashes(text)).contains(NONCE_CLEARANCE)));
  }

  @ParameterizedTest(name = "{0}", allowZeroInvocations = true)
  @MethodSource("providersWithMultimodalUserMessage")
  void documentInUserMessageIsReadByModel(Provider provider, WireMockRuntimeInfo wireMock) {
    stubPdfDownloads();

    final var systemPrompt =
        "You are a document analyst. A document is attached directly to the user's message. "
            + "Answer using only that attached document and do not call any tools. Always "
            + "quote specific facts, numbers, dates, and names found in the document.";

    // Reuses the document BPMN (which downloads downloadUrls into `downloadedFiles` before the
    // agent) but routes the single downloaded PDF into the user message instead of a tool result,
    // so this exercises the user-message multimodal path rather than the tool-result path.
    var model =
        buildModel(
            provider,
            AI_AGENT_SUB_PROCESS_V2_ELEMENT_TEMPLATE_PATH,
            DOCUMENT_BPMN_RESOURCE,
            systemPrompt,
            template -> template.property("data.userPrompt.documents", "=downloadedFiles"));

    var instance =
        startAgent(
            model,
            DOCUMENT_PROCESS_ID,
            systemPrompt,
            Map.of(
                "userPrompt",
                "What is the internal project code name mentioned in the attached document? "
                    + "Quote it exactly.",
                "downloadUrls",
                List.of(wireMock.getHttpBaseUrl() + "/" + DOC_PROJECT_LAUNCH)));

    assertResponseTextContains(instance, "Zypherion");
  }

  @ParameterizedTest(name = "{0}", allowZeroInvocations = true)
  @MethodSource("providersWithMultimodalUserMessage")
  void documentInToolResultIsReadByModel(Provider provider, WireMockRuntimeInfo wireMock) {
    stubPdfDownloads();

    var model =
        buildModel(
            provider,
            AI_AGENT_SUB_PROCESS_V2_ELEMENT_TEMPLATE_PATH,
            DOCUMENT_BPMN_RESOURCE,
            DOCUMENT_SYSTEM_PROMPT,
            template -> {});

    var instance =
        startAgent(
            model,
            DOCUMENT_PROCESS_ID,
            DOCUMENT_SYSTEM_PROMPT,
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

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private BpmnModelInstance buildModel(
      Provider provider,
      String templatePath,
      String bpmnResource,
      String systemPrompt,
      Consumer<ElementTemplate> customize) {
    var template = ElementTemplate.from(templatePath);

    template
        .property("agentContext", "=agent.context")
        .property("data.systemPrompt.prompt", "=systemPrompt")
        .property("data.userPrompt.prompt", "=userPrompt")
        .property("data.memory.storage.type", "in-process")
        .property("data.memory.contextWindowSize", "=50")
        .property("data.response.includeAssistantMessage", "=true")
        .property("data.response.includeAgentContext", "=true");

    provider.properties().forEach(template::property);
    customize.accept(template);

    try {
      var templateFile = template.writeTo(new File(tempDir, "template.json"));
      var bpmnFile = resourceLoader.getResource(bpmnResource).getFile();
      var modelInstance =
          new BpmnFile(bpmnFile).apply(templateFile, "AI_Agent", new File(tempDir, "applied.bpmn"));
      return BpmnUtil.withAgentDefinitionMarker(modelInstance, "AI_Agent", "aiAgentSubProcess");
    } catch (Exception e) {
      throw new RuntimeException("Failed to build BPMN model for " + provider.label(), e);
    }
  }

  private ProcessInstanceEvent startAgent(
      BpmnModelInstance model,
      String processId,
      String systemPrompt,
      Map<String, Object> variables) {
    ZeebeTest.with(camundaClient).awaitCompleteTopology().deploy(model);
    camundaClient.newDeployResourceCommand().addResourceFromClasspath(FORM_RESOURCE).send().join();
    final var allVariables = new HashMap<>(variables);
    allVariables.put("systemPrompt", systemPrompt);
    return camundaClient
        .newCreateInstanceCommand()
        .bpmnProcessId(processId)
        .latestVersion()
        .variables(allVariables)
        .send()
        .join();
  }

  /**
   * Completes the currently active {@code User_Feedback} user task with the given variables. Picks
   * the task with the highest key - user task keys are monotonically increasing, so this is always
   * the most recently created (and only still-active) one for the instance, even after a prior
   * feedback-loop iteration already completed an earlier task on the same instance.
   */
  private void completeUserFeedback(ProcessInstanceEvent instance, Map<String, Object> variables) {
    assertThat(instance).withAssertionTimeout(PROCESS_TIMEOUT).hasActiveElements("User_Feedback");

    final var tasks =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.processInstanceKey(instance.getProcessInstanceKey()))
            .send()
            .join();
    final var taskKey =
        tasks.items().stream()
            .max((a, b) -> Long.compare(a.getUserTaskKey(), b.getUserTaskKey()))
            .orElseThrow()
            .getUserTaskKey();

    camundaClient.newCompleteUserTaskCommand(taskKey).variables(variables).send().join();
  }

  /**
   * Waits for the process instance to complete, capturing its response, then asserts on it exactly
   * once. Deliberately not a single {@code hasVariableSatisfies} chain with the assertions inside:
   * that treats the whole consumer as a polling predicate, so an assertion failure in it is retried
   * for the full {@link #PROCESS_TIMEOUT} even though the instance is already completed and its
   * variable is fixed -- retrying can't change either. The {@code hasVariableSatisfies} lambda here
   * only captures the deserialized response; {@code assertions} runs once it returns.
   */
  private void assertAgentResponse(
      ProcessInstanceEvent instance, ThrowingConsumer<AgentSubProcessResponse> assertions) {
    awaitCompletionOrIncident(instance);

    final var responseRef = new AtomicReference<AgentSubProcessResponse>();
    assertThat(instance)
        .hasVariableSatisfies(
            AGENT_RESPONSE_VARIABLE,
            Map.class,
            map -> responseRef.set(objectMapper.convertValue(map, AgentSubProcessResponse.class)));

    Assertions.assertThat(responseRef.get()).satisfies(assertions);
  }

  /**
   * Same completion-wait/one-shot-assertion split as {@link #assertAgentResponse}, but reads {@code
   * responseText} directly off the raw output map instead of deserializing the whole response: the
   * multimodal scenario's persisted agent context contains a {@link
   * io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent} whose abstract
   * {@code Document} the plain test {@code ObjectMapper} (no document-deserialization module
   * registered) cannot reconstruct, so going through {@link AgentSubProcessResponseAssert} here
   * isn't an option.
   */
  private void assertResponseTextContains(
      ProcessInstanceEvent instance, String... expectedSubstrings) {
    awaitCompletionOrIncident(instance);

    final var responseTextRef = new AtomicReference<String>();
    assertThat(instance)
        .hasVariableSatisfies(
            AGENT_RESPONSE_VARIABLE,
            Map.class,
            map -> responseTextRef.set(String.valueOf(map.get("responseText"))));

    Assertions.assertThat(normalizeDashes(responseTextRef.get())).contains(expectedSubstrings);
  }

  /**
   * Normalizes Unicode dash/hyphen variants (e.g. U+2011 non-breaking hyphen, which models
   * sometimes substitute for a plain ASCII '-' when markdown-formatting a nonce fact) to a plain
   * '-', so a model's typographic choice doesn't break a literal {@code contains} check.
   */
  private static String normalizeDashes(String text) {
    // U+2010 hyphen, U+2011 non-breaking hyphen, U+2012 figure dash, U+2013 en dash,
    // U+2014 em dash, U+2212 minus sign.
    return text.replaceAll("[\u2010\u2011\u2012\u2013\u2014\u2212]", "-");
  }

  /**
   * Waits for the process instance to complete, but fails fast on an active incident instead of
   * waiting out the full {@link #PROCESS_TIMEOUT} for a completion that will never come - a job
   * failure (e.g. the model call itself throwing) surfaces as an incident, not as a completed
   * instance, and {@code isCompleted()} alone has no way to notice that and stop waiting early.
   * Polls both conditions on this thread with a short per-check timeout: {@code CamundaAssert}'s
   * data source is bound to the test thread, so checking off a background thread (e.g. racing two
   * {@code CompletableFuture}s) fails with "No data source is set".
   */
  private void awaitCompletionOrIncident(ProcessInstanceEvent instance) {
    final Instant deadline = Instant.now().plus(PROCESS_TIMEOUT);
    while (Instant.now().isBefore(deadline)) {
      if (hasActiveIncident(instance)) {
        throw new AssertionError(
            ("Process instance %d raised an incident instead of completing - failing fast "
                    + "instead of waiting out the remaining timeout")
                .formatted(instance.getProcessInstanceKey()));
      }
      if (isCompleted(instance)) {
        return;
      }
    }

    throw new AssertionError(
        "Timed out waiting for process instance %d to complete"
            .formatted(instance.getProcessInstanceKey()));
  }

  private static boolean hasActiveIncident(ProcessInstanceEvent instance) {
    try {
      assertThat(instance).withAssertionTimeout(INCIDENT_POLL_TIMEOUT).hasActiveIncidents();
      return true;
    } catch (AssertionError e) {
      return false;
    }
  }

  private static boolean isCompleted(ProcessInstanceEvent instance) {
    try {
      assertThat(instance).withAssertionTimeout(INCIDENT_POLL_TIMEOUT).isCompleted();
      return true;
    } catch (AssertionError e) {
      return false;
    }
  }

  private void stubPdfDownloads() {
    for (var doc : List.of(DOC_PROJECT_LAUNCH, DOC_HEADCOUNT_REPORT, DOC_AUTHOR_INFO)) {
      stubFor(
          get(urlPathEqualTo("/" + doc))
              .willReturn(
                  aResponse().withBodyFile(doc).withHeader("Content-Type", "application/pdf")));
    }
  }
}
