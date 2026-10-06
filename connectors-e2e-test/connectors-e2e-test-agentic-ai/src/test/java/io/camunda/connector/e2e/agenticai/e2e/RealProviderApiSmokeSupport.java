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

import static io.camunda.connector.e2e.agenticai.aiagent.AgentTestFixtures.AGENT_RESPONSE_VARIABLE;
import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.enums.IncidentState;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.connector.agenticai.aiagent.model.AgentSubProcessResponse;
import io.camunda.connector.e2e.BpmnFile;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.connector.e2e.agenticai.assertj.AgentSubProcessResponseAssert;
import io.camunda.connector.e2e.app.TestConnectorRuntimeApplication;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.File;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.ThrowingConsumer;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ResourceLoader;

/** Shared native real-provider catalog and execution support for capability suites. */
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
abstract class RealProviderApiSmokeSupport {

  static final String BPMN_RESOURCE = "classpath:real-provider-api-smoke.bpmn";
  static final String FORM_RESOURCE = "ai-agent-chat-user-feedback.form";
  static final String PROCESS_ID = "real_provider_api_smoke";
  static final String TOOL_JOB_TYPE = "lookup-classified-fact";
  static final Duration PROCESS_TIMEOUT = Duration.ofMinutes(3);
  private static final Duration POLL_INTERVAL = Duration.ofSeconds(1);

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
          + "you MUST call the Lookup Classified Fact tool and quote its result verbatim. Never "
          + "guess or fabricate information that should come from a tool result.";

  protected static final String RESPONSE_SCHEMA =
      "{\"type\":\"object\","
          + "\"properties\":{\"codeName\":{\"type\":\"string\"},\"clearanceLevel\":{\"type\":\"string\"}},"
          + "\"required\":[\"codeName\",\"clearanceLevel\"]}";

  // Repeated to clear the largest minimum cacheable-prefix size among providers under test:
  // Anthropic needs ~1024 tokens (Sonnet-class models), Gemini needs ~4096. Each repeat is ~65
  // tokens, so 80 repeats (~5200 tokens) gives comfortable margin over both.
  protected static final String LONG_SYSTEM_PROMPT =
      """
      You are an assistant operating under a detailed classified-information handling protocol. \
      Always be precise, never fabricate facts, and when the user asks for an internal \
      or classified code name you must call the Lookup Classified Fact tool and quote \
      its result verbatim without paraphrasing. Follow every rule in this protocol \
      carefully and consistently across the whole conversation. \
      """
          .repeat(80);

  private final ObjectMapper objectMapper = ConnectorsObjectMapperSupplier.getCopy();

  @Autowired CamundaClient camundaClient;
  @Autowired CamundaProcessTestContext processTestContext;
  @Autowired ResourceLoader resourceLoader;
  @TempDir File tempDir;

  enum Capability {
    STRUCTURED_OUTPUT,
    STRUCTURED_OUTPUT_WITH_TOOLS,
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
  record ProviderConfig(
      String label,
      RealLlmProviderGroup providerGroup,
      List<String> requiredEnvVars,
      boolean enabled,
      Map<String, String> properties,
      Map<Capability, Map<String, String>> capabilityProperties,
      // Whether this row reports a distinct cache-creation (write) token count in addition to
      // cache-read; gates the cache-creation assertion in the prompt-caching scenario.
      boolean reportsCacheCreationTokens) {

    ProviderConfig(
        String label,
        RealLlmProviderGroup providerGroup,
        List<String> requiredEnvVars,
        Map<String, String> properties,
        Map<Capability, Map<String, String>> capabilityProperties,
        boolean reportsCacheCreationTokens) {
      this(
          label,
          providerGroup,
          requiredEnvVars,
          true,
          properties,
          capabilityProperties,
          reportsCacheCreationTokens);
    }

    ProviderConfig disabled() {
      return new ProviderConfig(
          label,
          providerGroup,
          requiredEnvVars,
          false,
          properties,
          capabilityProperties,
          reportsCacheCreationTokens);
    }

    boolean isEnabled() {
      // requiredEnvVars is empty for local providers that need no API key, just a URL.
      return enabled
          && providerGroup.isSelected()
          && RealLlmTestEnvironment.hasNonBlankValues(requiredEnvVars);
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

  static ProviderConfig anthropicV2(
      String model, Map<Capability, Map<String, String>> capabilityProperties) {
    return new ProviderConfig(
        "anthropic-v2/" + model,
        RealLlmProviderGroup.ANTHROPIC,
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
  static ProviderConfig anthropicBedrockMantleV2(
      String model, Map<Capability, Map<String, String>> capabilityProperties) {
    return new ProviderConfig(
        "anthropic-bedrock-mantle-v2/" + model,
        RealLlmProviderGroup.BEDROCK,
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

  // Same wire format as anthropic-api, just signed and routed through an Azure Foundry resource.
  static ProviderConfig anthropicFoundryV2(
      String model, Map<Capability, Map<String, String>> capabilityProperties) {
    return new ProviderConfig(
        "anthropic-foundry-v2/" + model,
        RealLlmProviderGroup.ANTHROPIC,
        List.of("ANTHROPIC_FOUNDRY_API_KEY", "ANTHROPIC_FOUNDRY_ENDPOINT"),
        Map.of(
            "provider.type",
            "anthropic",
            "provider.anthropic.backend.type",
            "foundry",
            "provider.anthropic.backend.foundry.endpoint",
            envOrPlaceholder("ANTHROPIC_FOUNDRY_ENDPOINT"),
            "provider.anthropic.backend.foundry.authentication.type",
            "apiKey",
            "provider.anthropic.backend.foundry.authentication.apiKey",
            envOrPlaceholder("ANTHROPIC_FOUNDRY_API_KEY"),
            "provider.anthropic.model.model",
            model),
        capabilityProperties,
        true);
  }

  // Same backend as anthropicFoundryV2, authenticated via Entra ID client credentials instead.
  // No capability matrix: the always-on scenarios already exercise the same request/response path.
  static ProviderConfig anthropicFoundryClientCredentialsV2(String model) {
    return new ProviderConfig(
        "anthropic-foundry-client-credentials-v2/" + model,
        RealLlmProviderGroup.ANTHROPIC,
        List.of(
            "ANTHROPIC_FOUNDRY_ENDPOINT",
            "ANTHROPIC_FOUNDRY_TENANT_ID",
            "ANTHROPIC_FOUNDRY_CLIENT_ID",
            "ANTHROPIC_FOUNDRY_CLIENT_SECRET"),
        Map.of(
            "provider.type",
            "anthropic",
            "provider.anthropic.backend.type",
            "foundry",
            "provider.anthropic.backend.foundry.endpoint",
            envOrPlaceholder("ANTHROPIC_FOUNDRY_ENDPOINT"),
            "provider.anthropic.backend.foundry.authentication.type",
            "clientCredentials",
            "provider.anthropic.backend.foundry.authentication.tenantId",
            envOrPlaceholder("ANTHROPIC_FOUNDRY_TENANT_ID"),
            "provider.anthropic.backend.foundry.authentication.clientId",
            envOrPlaceholder("ANTHROPIC_FOUNDRY_CLIENT_ID"),
            "provider.anthropic.backend.foundry.authentication.clientSecret",
            envOrPlaceholder("ANTHROPIC_FOUNDRY_CLIENT_SECRET"),
            "provider.anthropic.model.model",
            model),
        Map.of(),
        true);
  }

  static ProviderConfig bedrockConverseV2(
      String model, Map<Capability, Map<String, String>> capabilityProperties) {
    return new ProviderConfig(
        "bedrock-converse-v2/" + model,
        RealLlmProviderGroup.BEDROCK,
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
            model,
            "provider.bedrock.model.parameters.promptCaching.enabled",
            "false"),
        capabilityProperties,
        true);
  }

  // Always targets the openai-api backend, mirroring anthropicV2 above.
  static ProviderConfig openAiCompletionsV2(
      String model, Map<Capability, Map<String, String>> capabilityProperties) {
    return openAiV2("completions", model, capabilityProperties);
  }

  static ProviderConfig openAiResponsesV2(
      String model, Map<Capability, Map<String, String>> capabilityProperties) {
    return openAiV2("responses", model, capabilityProperties);
  }

  private static ProviderConfig openAiV2(
      String family, String model, Map<Capability, Map<String, String>> capabilityProperties) {
    return new ProviderConfig(
        "openai-" + family + "-v2/" + model,
        RealLlmProviderGroup.OPENAI,
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
  static ProviderConfig openAiFoundryCompletionsV2(
      String model, Map<Capability, Map<String, String>> capabilityProperties) {
    return openAiFoundryV2("completions", model, capabilityProperties);
  }

  static ProviderConfig openAiFoundryResponsesV2(
      String model, Map<Capability, Map<String, String>> capabilityProperties) {
    return openAiFoundryV2("responses", model, capabilityProperties);
  }

  private static ProviderConfig openAiFoundryV2(
      String family, String model, Map<Capability, Map<String, String>> capabilityProperties) {
    return new ProviderConfig(
        "openai-foundry-" + family + "-v2/" + model,
        RealLlmProviderGroup.OPENAI,
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

  // No "family" parameter here unlike openAiV2: Mistral has no API-family axis of its own.
  static ProviderConfig mistralV2(
      String model, Map<Capability, Map<String, String>> capabilityProperties) {
    return new ProviderConfig(
        "mistral-v2/" + model,
        RealLlmProviderGroup.MISTRAL,
        List.of("MISTRAL_API_KEY"),
        Map.of(
            "provider.type",
            "mistral",
            "provider.mistral.backend.type",
            "mistral-api",
            "provider.mistral.backend.mistral.apiKey",
            envOrPlaceholder("MISTRAL_API_KEY"),
            "provider.mistral.model.model",
            model),
        capabilityProperties,
        false);
  }

  static ProviderConfig googleGeminiV2(
      String model, Map<Capability, Map<String, String>> capabilityProperties) {
    return new ProviderConfig(
        "google-gemini-v2/" + model,
        RealLlmProviderGroup.VERTEX,
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

  static ProviderConfig googleGeminiVertexAiV2(
      String model, String region, Map<Capability, Map<String, String>> capabilityProperties) {
    return new ProviderConfig(
        "google-gemini-vertex-ai-v2/" + model,
        RealLlmProviderGroup.VERTEX,
        List.of("GOOGLE_VERTEX_AI_PROJECT_ID", "GOOGLE_VERTEX_AI_SERVICE_ACCOUNT_JSON"),
        Map.of(
            "provider.type",
            "google-gemini",
            "provider.googleGemini.backend.type",
            "google-vertex-ai",
            "provider.googleGemini.backend.googleVertexAi.projectId",
            envOrPlaceholder("GOOGLE_VERTEX_AI_PROJECT_ID"),
            "provider.googleGemini.backend.googleVertexAi.region",
            region,
            "provider.googleGemini.backend.googleVertexAi.authentication.type",
            "serviceAccountCredentials",
            "provider.googleGemini.backend.googleVertexAi.authentication.jsonKey",
            envOrPlaceholder("GOOGLE_VERTEX_AI_SERVICE_ACCOUNT_JSON"),
            "provider.googleGemini.model.model",
            model),
        capabilityProperties,
        false);
  }

  static Stream<ProviderConfig> providerCatalog() {
    return Stream.of(
        // claude-sonnet-4-6 only supports thinking mode "enabled" (explicit budget) — the model
        // always emits a thinking block regardless of prompt difficulty.
        anthropicV2(
            "claude-sonnet-4-6",
            Map.of(
                Capability.STRUCTURED_OUTPUT, Map.of(),
                Capability.STRUCTURED_OUTPUT_WITH_TOOLS, Map.of(),
                Capability.MULTIMODAL_USER_MESSAGE, Map.of(),
                Capability.PROMPT_CACHING,
                    Map.of("provider.anthropic.model.parameters.promptCaching.enabled", "true"),
                Capability.REASONING,
                    Map.of(
                        "provider.anthropic.model.parameters.thinking.mode", "enabled",
                        "provider.anthropic.model.parameters.thinking.budgetTokens", "2048"))),
        // claude-sonnet-5-5 does NOT accept "enabled"; it only allows "adaptive" (the model
        // decides whether to think). At effort "high" it reliably thinks on a genuinely
        // multi-step prompt, but this is model choice, not an API-level guarantee.
        anthropicV2(
            "claude-sonnet-5-5",
            Map.of(
                Capability.STRUCTURED_OUTPUT, Map.of(),
                Capability.STRUCTURED_OUTPUT_WITH_TOOLS, Map.of(),
                Capability.MULTIMODAL_USER_MESSAGE, Map.of(),
                Capability.PROMPT_CACHING,
                    Map.of("provider.anthropic.model.parameters.promptCaching.enabled", "true"),
                Capability.REASONING,
                    Map.of(
                        "provider.anthropic.model.parameters.thinking.mode", "adaptive",
                        "provider.anthropic.model.parameters.effort", "high"))),
        // Bedrock Mantle does not expose claude-sonnet-5-5 yet. It also rejects
        // output_config.format, so structured output remains undeclared:
        // https://docs.aws.amazon.com/bedrock/latest/userguide/claude-messages-structured-outputs.html
        anthropicBedrockMantleV2(
            "claude-sonnet-5",
            Map.of(
                Capability.MULTIMODAL_USER_MESSAGE, Map.of(),
                Capability.PROMPT_CACHING,
                    Map.of("provider.anthropic.model.parameters.promptCaching.enabled", "true"),
                Capability.REASONING,
                    Map.of(
                        "provider.anthropic.model.parameters.thinking.mode", "adaptive",
                        "provider.anthropic.model.parameters.effort", "high"))),
        anthropicFoundryV2(
            "claude-sonnet-5-5",
            Map.of(
                Capability.STRUCTURED_OUTPUT, Map.of(),
                Capability.STRUCTURED_OUTPUT_WITH_TOOLS, Map.of(),
                Capability.MULTIMODAL_USER_MESSAGE, Map.of(),
                Capability.PROMPT_CACHING,
                    Map.of("provider.anthropic.model.parameters.promptCaching.enabled", "true"),
                Capability.REASONING,
                    Map.of(
                        "provider.anthropic.model.parameters.thinking.mode", "adaptive",
                        "provider.anthropic.model.parameters.effort", "high"))),
        anthropicFoundryClientCredentialsV2("claude-sonnet-5-5"),
        // Amazon's own Nova 2 Lite Converse model (cheap tier): multimodal + prompt caching +
        // reasoning. STRUCTURED_OUTPUT is deliberately NOT declared: AWS rejects outputConfig
        // for this model ("This model doesn't support the outputConfig field"), matching its
        // model card ("Structured outputs" listed as Not Supported). Disabled for now: prone
        // to misspelling nonce words in its output.
        bedrockConverseV2(
                "us.amazon.nova-2-lite-v1:0",
                Map.of(
                    Capability.MULTIMODAL_USER_MESSAGE, Map.of(),
                    Capability.PROMPT_CACHING,
                        Map.of("provider.bedrock.model.parameters.promptCaching.enabled", "true"),
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
        bedrockConverseV2(
            "openai.gpt-oss-120b-1:0",
            Map.of(
                Capability.REASONING,
                Map.of("provider.bedrock.bodyProperties", "={reasoning_effort: \"medium\"}"))),
        // Claude via the native Converse path: a permanent cross-check that the generic
        // sdkFields() codec round-trips Anthropic's own block shapes correctly too. Global
        // cross-region inference ID (no in-region endpoint for this model). STRUCTURED_OUTPUT is
        // deliberately NOT declared: outputConfig.textFormat is a genuine Converse field
        // (confirmed via the SDK's own ConverseRequest.outputConfig()), but AWS's Converse
        // structured-output model
        // allow-list (docs.aws.amazon.com/bedrock/latest/userguide/structured-output.html) does
        // not yet include claude-sonnet-5-5 — the model itself rejects it with a 400
        // ("output_config.format: Extra inputs are not permitted"), confirmed against a real
        // API
        // call. REASONING is also undeclared because adaptive thinking did not yield a separate
        // reasoning-content block in the live response.
        bedrockConverseV2(
            "global.anthropic.claude-sonnet-5-5",
            Map.of(
                Capability.MULTIMODAL_USER_MESSAGE, Map.of(),
                Capability.PROMPT_CACHING,
                    Map.of("provider.bedrock.model.parameters.promptCaching.enabled", "true"))),
        // Responses mirrors Anthropic's reasoning pattern: it returns a ReasoningContent
        // domain block in addition to reasoning_tokens, so REASONING is exercisable here.
        openAiResponsesV2(
            "gpt-6.1-sol",
            Map.of(
                Capability.STRUCTURED_OUTPUT, Map.of(),
                Capability.STRUCTURED_OUTPUT_WITH_TOOLS, Map.of(),
                Capability.MULTIMODAL_USER_MESSAGE, Map.of(),
                Capability.PROMPT_CACHING, Map.of(),
                Capability.REASONING, Map.of("provider.openai.api.responses.effort", "high"))),
        // gpt-6-astra defaults to reasoning and rejects function tools on Chat Completions unless
        // reasoning_effort is "none". Keep the latest proven tool-capable model on this API family.
        openAiCompletionsV2(
            "gpt-5.5",
            Map.of(
                Capability.STRUCTURED_OUTPUT, Map.of(),
                Capability.STRUCTURED_OUTPUT_WITH_TOOLS, Map.of(),
                Capability.MULTIMODAL_USER_MESSAGE, Map.of(),
                Capability.PROMPT_CACHING, Map.of())),
        // An older model, on both API families, for completeness.
        openAiResponsesV2(
            "gpt-4.1",
            Map.of(
                Capability.STRUCTURED_OUTPUT, Map.of(),
                Capability.STRUCTURED_OUTPUT_WITH_TOOLS, Map.of(),
                Capability.MULTIMODAL_USER_MESSAGE, Map.of(),
                Capability.PROMPT_CACHING, Map.of())),
        openAiCompletionsV2(
            "gpt-4.1",
            Map.of(
                Capability.STRUCTURED_OUTPUT, Map.of(),
                Capability.STRUCTURED_OUTPUT_WITH_TOOLS, Map.of(),
                Capability.MULTIMODAL_USER_MESSAGE, Map.of(),
                Capability.PROMPT_CACHING, Map.of())),
        // Same models/capabilities as the openai-api rows above, via the foundry backend.
        openAiFoundryResponsesV2(
            "gpt-6.1-sol",
            Map.of(
                Capability.STRUCTURED_OUTPUT, Map.of(),
                Capability.STRUCTURED_OUTPUT_WITH_TOOLS, Map.of(),
                Capability.MULTIMODAL_USER_MESSAGE, Map.of(),
                Capability.PROMPT_CACHING, Map.of(),
                Capability.REASONING, Map.of("provider.openai.api.responses.effort", "high"))),
        openAiFoundryCompletionsV2(
            "gpt-5.5",
            Map.of(
                Capability.STRUCTURED_OUTPUT, Map.of(),
                Capability.STRUCTURED_OUTPUT_WITH_TOOLS, Map.of(),
                Capability.MULTIMODAL_USER_MESSAGE, Map.of(),
                Capability.PROMPT_CACHING, Map.of())),
        openAiFoundryResponsesV2(
            "gpt-4.1",
            Map.of(
                Capability.STRUCTURED_OUTPUT, Map.of(),
                Capability.STRUCTURED_OUTPUT_WITH_TOOLS, Map.of(),
                Capability.MULTIMODAL_USER_MESSAGE, Map.of(),
                Capability.PROMPT_CACHING, Map.of())),
        openAiFoundryCompletionsV2(
            "gpt-4.1",
            Map.of(
                Capability.STRUCTURED_OUTPUT, Map.of(),
                Capability.STRUCTURED_OUTPUT_WITH_TOOLS, Map.of(),
                Capability.MULTIMODAL_USER_MESSAGE, Map.of(),
                Capability.PROMPT_CACHING, Map.of())),
        // Mistral does not reliably report a cache hit within the scenario's two calls, and
        // mistral-medium-3-5 hangs on tool-history plus json_schema; keep only observed
        // capabilities.
        mistralV2(
            "mistral-large-2512",
            Map.of(
                Capability.STRUCTURED_OUTPUT, Map.of(),
                Capability.STRUCTURED_OUTPUT_WITH_TOOLS, Map.of(),
                Capability.MULTIMODAL_USER_MESSAGE, Map.of())),
        mistralV2(
            "mistral-medium-3-5",
            Map.of(
                Capability.REASONING, Map.of("provider.mistral.parameters.effort", "high"),
                Capability.MULTIMODAL_USER_MESSAGE, Map.of())),
        mistralV2(
            "ministral-14b-2512",
            Map.of(
                Capability.STRUCTURED_OUTPUT, Map.of(),
                Capability.STRUCTURED_OUTPUT_WITH_TOOLS, Map.of(),
                Capability.MULTIMODAL_USER_MESSAGE, Map.of())),
        // Gemini 3.8 produces schema-conforming JSON without tools, but can repeatedly reissue an
        // already-completed tool call when tools and a response schema are combined.
        googleGeminiV2(
            "gemini-3.8-flash",
            Map.of(
                Capability.STRUCTURED_OUTPUT,
                Map.of(),
                Capability.MULTIMODAL_USER_MESSAGE,
                Map.of(),
                Capability.PROMPT_CACHING,
                Map.of(),
                Capability.REASONING,
                Map.of("provider.googleGemini.model.parameters.thinking.thinkingLevel", "high"))),
        googleGeminiVertexAiV2(
            "gemini-3.8-flash",
            "global",
            Map.of(
                Capability.STRUCTURED_OUTPUT, Map.of(),
                Capability.MULTIMODAL_USER_MESSAGE, Map.of(),
                Capability.PROMPT_CACHING, Map.of(),
                Capability.REASONING,
                    Map.of(
                        "provider.googleGemini.model.parameters.thinking.thinkingLevel", "high"))));
  }

  static Stream<ProviderConfig> providers() {
    final var selectedProviders = providerCatalog().filter(ProviderConfig::isEnabled).toList();
    if (RealLlmTestEnvironment.isProviderRequired() && selectedProviders.isEmpty()) {
      throw new IllegalStateException(
          "No enabled real providers were selected; check provider credentials and "
              + "REAL_LLM_PROVIDER_GROUP");
    }
    return selectedProviders.stream();
  }

  static Stream<ProviderConfig> providersWithStructuredOutput() {
    return requireProviderSelection(
        providers().filter(p -> p.supports(Capability.STRUCTURED_OUTPUT)), "structured-output");
  }

  static Stream<ProviderConfig> providersWithStructuredOutputAndTools() {
    return providers().filter(p -> p.supports(Capability.STRUCTURED_OUTPUT_WITH_TOOLS));
  }

  static Stream<ProviderConfig> providersWithStructuredOutputWithoutTools() {
    return providers()
        .filter(
            p ->
                p.supports(Capability.STRUCTURED_OUTPUT)
                    && !p.supports(Capability.STRUCTURED_OUTPUT_WITH_TOOLS));
  }

  static Stream<ProviderConfig> providersWithReasoning() {
    return requireProviderSelection(
        providers().filter(p -> p.supports(Capability.REASONING)), "reasoning");
  }

  static Stream<ProviderConfig> providersWithPromptCaching() {
    return requireProviderSelection(
        providers()
            .filter(p -> p.supports(Capability.PROMPT_CACHING))
            // OpenAI and Gemini cache placement is opportunistic, so keep them manual rather than
            // requiring a positive cache hit in the provider-sharded PR workflow.
            .filter(
                p ->
                    (p.providerGroup() != RealLlmProviderGroup.OPENAI
                            && p.providerGroup() != RealLlmProviderGroup.VERTEX)
                        || !RealLlmProviderGroup.isShardedRun()),
        "prompt-caching");
  }

  static Stream<ProviderConfig> providersWithMultimodalUserMessage() {
    return requireProviderSelection(
        providers().filter(p -> p.supports(Capability.MULTIMODAL_USER_MESSAGE)),
        RealProviderCapabilityTags.MULTIMODAL);
  }

  private static Stream<ProviderConfig> requireProviderSelection(
      Stream<ProviderConfig> providers, String capability) {
    final var selectedProviders = providers.toList();
    if (RealLlmTestEnvironment.isProviderRequired() && selectedProviders.isEmpty()) {
      throw new IllegalStateException(
          "No enabled real provider supports the " + capability + " capability");
    }
    return selectedProviders.stream();
  }

  protected static String envOrPlaceholder(String envVar) {
    return RealLlmTestEnvironment.getOrDefault(envVar, "NOT_SET");
  }

  protected static String envOrDefault(String envVar, String defaultValue) {
    return RealLlmTestEnvironment.getOrDefault(envVar, defaultValue);
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

  // Helpers
  // ---------------------------------------------------------------------------

  protected BpmnModelInstance buildModel(
      ProviderConfig provider,
      String templatePath,
      String bpmnResource,
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
      return new BpmnFile(bpmnFile)
          .apply(templateFile, "AI_Agent", new File(tempDir, "applied.bpmn"));
    } catch (Exception e) {
      throw new RuntimeException("Failed to build BPMN model for " + provider.label(), e);
    }
  }

  protected ProcessInstanceEvent startAgent(
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
  protected void completeUserFeedback(
      ProcessInstanceEvent instance, Map<String, Object> variables) {
    final var taskKey = awaitCreatedUserTaskOrIncident(instance, "User_Feedback");
    camundaClient.newCompleteUserTaskCommand(taskKey).variables(variables).send().join();
  }

  protected AgentSubProcessResponse readAgentResponseAtUserFeedback(ProcessInstanceEvent instance) {
    awaitCreatedUserTaskOrIncident(instance, "User_Feedback");
    return readAgentResponse(instance);
  }

  private long awaitCreatedUserTaskOrIncident(ProcessInstanceEvent instance, String elementId) {
    return awaitOrFailOnIncident(
        instance,
        "created user task '" + elementId + "'",
        () ->
            camundaClient
                .newUserTaskSearchRequest()
                .filter(
                    f ->
                        f.processInstanceKey(instance.getProcessInstanceKey())
                            .elementId(elementId)
                            .state(UserTaskState.CREATED))
                .send()
                .join()
                .items()
                .stream()
                .map(task -> task.getUserTaskKey())
                .max(Long::compare));
  }

  /**
   * Waits for the process instance to complete, capturing its response, then asserts on it exactly
   * once. Deliberately not a single {@code hasVariableSatisfies} chain with the assertions inside:
   * that treats the whole consumer as a polling predicate, so an assertion failure in it is retried
   * for the full {@link #PROCESS_TIMEOUT} even though the instance is already completed and its
   * variable is fixed -- retrying can't change either. The {@code hasVariableSatisfies} lambda here
   * only captures the deserialized response; {@code assertions} runs once it returns.
   */
  protected void assertAgentResponse(
      ProcessInstanceEvent instance, ThrowingConsumer<AgentSubProcessResponse> assertions) {
    awaitCompletionOrIncident(instance);
    Assertions.assertThat(readAgentResponse(instance)).satisfies(assertions);
  }

  protected AgentSubProcessResponse readAgentResponse(ProcessInstanceEvent instance) {
    final var responseRef = new AtomicReference<AgentSubProcessResponse>();
    assertThat(instance)
        .hasVariableSatisfies(
            AGENT_RESPONSE_VARIABLE,
            Map.class,
            map -> responseRef.set(objectMapper.convertValue(map, AgentSubProcessResponse.class)));
    return responseRef.get();
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
  protected void assertResponseTextContains(
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
  protected static String normalizeDashes(String text) {
    // U+2010 hyphen, U+2011 non-breaking hyphen, U+2012 figure dash, U+2013 en dash,
    // U+2014 em dash, U+2212 minus sign.
    return text.replaceAll("[\u2010\u2011\u2012\u2013\u2014\u2212]", "-");
  }

  /**
   * Waits for the process instance to complete, but fails fast on an active incident instead of
   * waiting out the full {@link #PROCESS_TIMEOUT} for a completion that will never come - a job
   * failure (e.g. the model call itself throwing) surfaces as an incident, not as a completed
   * instance, and {@code isCompleted()} alone has no way to notice that and stop waiting early.
   * Polls both conditions on the test thread at a bounded interval and reports the process state
   * and incident messages on timeout.
   */
  protected void awaitCompletionOrIncident(ProcessInstanceEvent instance) {
    awaitOrFailOnIncident(
        instance,
        "completion",
        () -> isCompleted(instance) ? Optional.of(instance) : Optional.empty());
  }

  private <T> T awaitOrFailOnIncident(
      ProcessInstanceEvent instance, String awaited, Supplier<Optional<T>> outcome) {
    final Settled<T> settled;
    try {
      settled =
          await()
              .alias(awaited + " of process instance " + instance.getProcessInstanceKey())
              .pollInSameThread()
              .pollInterval(POLL_INTERVAL)
              .atMost(PROCESS_TIMEOUT)
              .until(
                  () -> new Settled<>(outcome.get(), activeIncidents(instance)),
                  Settled::isSettled);
    } catch (ConditionTimeoutException e) {
      throw new AssertionError(
          ("Timed out after %s waiting for %s of process instance %d. "
                  + "Current state: %s. Active incidents: %s")
              .formatted(
                  PROCESS_TIMEOUT,
                  awaited,
                  instance.getProcessInstanceKey(),
                  processInstanceState(instance),
                  describeIncidents(activeIncidents(instance))),
          e);
    }

    if (!settled.incidents().isEmpty()) {
      throw new AssertionError(
          "Process instance %d raised an incident instead of reaching %s: %s"
              .formatted(
                  instance.getProcessInstanceKey(),
                  awaited,
                  describeIncidents(settled.incidents())));
    }
    return settled.value().orElseThrow();
  }

  private record Settled<T>(Optional<T> value, List<String> incidents) {
    boolean isSettled() {
      return value.isPresent() || !incidents.isEmpty();
    }
  }

  private List<String> activeIncidents(ProcessInstanceEvent instance) {
    return camundaClient
        .newIncidentSearchRequest()
        .filter(
            f -> f.processInstanceKey(instance.getProcessInstanceKey()).state(IncidentState.ACTIVE))
        .send()
        .join()
        .items()
        .stream()
        .map(incident -> incident.getElementId() + ": " + incident.getErrorMessage())
        .toList();
  }

  private boolean isCompleted(ProcessInstanceEvent instance) {
    return !camundaClient
        .newProcessInstanceSearchRequest()
        .filter(
            f ->
                f.processInstanceKey(instance.getProcessInstanceKey())
                    .state(ProcessInstanceState.COMPLETED))
        .send()
        .join()
        .items()
        .isEmpty();
  }

  private String processInstanceState(ProcessInstanceEvent instance) {
    final var instances =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(f -> f.processInstanceKey(instance.getProcessInstanceKey()))
            .send()
            .join()
            .items();
    return instances.isEmpty()
        ? "not returned by process-instance search"
        : instances.stream().map(item -> item.getState().name()).distinct().toList().toString();
  }

  private static String describeIncidents(List<String> incidents) {
    return incidents.isEmpty() ? "none" : String.join("; ", incidents);
  }
}
