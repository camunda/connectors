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
package io.camunda.connector.e2e.agenticai.realllm;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.camunda.connector.e2e.agenticai.aiagent.fixtures.AgentTestFixtures.AI_AGENT_SUB_PROCESS_V1_ELEMENT_TEMPLATE_PATH;
import static io.camunda.connector.e2e.agenticai.aiagent.fixtures.AgentTestFixtures.AI_AGENT_SUB_PROCESS_V2_ELEMENT_TEMPLATE_PATH;
import static io.camunda.process.test.api.CamundaAssert.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.connector.e2e.BpmnFile;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.connector.e2e.agenticai.CamundaDocumentTestConfiguration;
import io.camunda.connector.e2e.agenticai.aiagent.fixtures.DocumentSetup;
import io.camunda.connector.e2e.app.TestConnectorRuntimeApplication;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.AdHocSubProcess;
import io.camunda.zeebe.model.bpmn.instance.ServiceTask;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.ResourceLoader;

/**
 * Cross-provider viability test for document handling in tool call results, covering both the v1
 * (LangChain4j-backed) and v2 (native) provider configurations.
 *
 * <p>Validates that real LLM providers can reason about PDF documents returned by tool calls. A v2
 * row creates an agent instance with the {@code documents} feature: a tool result shows the handle
 * of each document and the model loads the content it needs with {@code c8__load_document}. A v1
 * row creates an agent instance without features: the model has no {@code c8__load_document} tool,
 * and the content of the documents follows the tool results in a message of the agent. The
 * assertions are the same for both, because they check what the model reports about the documents.
 *
 * <p>A v2 row also runs the nested documents with "Use document references" cleared, which sends
 * the content of the documents after the tool results as a v1 row does. With document references, a
 * v2 row passes a document to a tool with a document parameter and returns a document with {@code
 * c8__return_document}.
 *
 * <p>Local-only: runs only when {@code RUN_NATIVE_LLM_E2E=true} and the row's API key is present.
 * Configure API keys via environment variables:
 *
 * <ul>
 *   <li>{@code OPENAI_API_KEY} - OpenAI API key
 *   <li>{@code ANTHROPIC_API_KEY} - Anthropic API key
 *   <li>{@code ANTHROPIC_BEDROCK_API_KEY} / {@code ANTHROPIC_BEDROCK_REGION} - Anthropic's native
 *       AWS Bedrock Mantle backend credentials (region defaults to us-east-1)
 *   <li>{@code AWS_BEDROCK_ACCESS_KEY} / {@code AWS_BEDROCK_SECRET_KEY} - AWS Bedrock credentials
 *   <li>{@code OPENROUTER_API_KEY} - OpenRouter API key
 *   <li>{@code DOCKER_MODEL_RUNNER_URL} - OpenAI-compatible endpoint (default:
 *       http://localhost:12434/engines/llama.cpp/v1)
 *   <li>{@code OLLAMA_URL} - Ollama OpenAI-compatible endpoint (default: http://localhost:11434/v1)
 * </ul>
 */
@SpringBootTest(
    classes = {TestConnectorRuntimeApplication.class},
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "camunda.connector.webhook.enabled=false",
      "camunda.connector.polling.enabled=false",
      "camunda.connector.agenticai.tools.process-definition.cache.enabled=false",
      "logging.level.io.camunda.connector.agenticai=TRACE"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@CamundaSpringProcessTest
@WireMockTest
@Import(CamundaDocumentTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "RUN_NATIVE_LLM_E2E", matches = "true")
@Tag(RealProviderCapabilityTags.DOCUMENT_TOOL_CALL_RESULTS)
class DocumentToolCallResultsIT {

  private static final Logger LOG = LoggerFactory.getLogger(DocumentToolCallResultsIT.class);

  private static final String BPMN_RESOURCE = "classpath:document-tool-call-results.bpmn";

  private static final String DOC_DIR = "document-tool-call-results/";
  private static final String DOC_PROJECT_LAUNCH = DOC_DIR + "project-launch.pdf";
  private static final String DOC_HEADCOUNT_REPORT = DOC_DIR + "headcount-report.pdf";
  private static final String DOC_AUTHOR_INFO = DOC_DIR + "author-info.pdf";

  /** The document the {@code Fetch_Report} tool returns as the cover page. */
  private static final String DOC_COVER_PAGE = DOC_AUTHOR_INFO;

  private static final String ARCHIVE_TOOL = "Archive_Document";
  private static final String ARCHIVE_PATH = "/archive";
  private static final String COVER_PAGE_KEY = "cover_page";

  private static final String SYSTEM_PROMPT =
      "You are a document analyst. Use the available tools to retrieve and analyze documents. "
          + "When reporting findings, always quote specific facts, numbers, dates, and names "
          + "exactly as written in the documents. Never correct or respell proper names. Be "
          + "concise.";

  private static final Duration PROCESS_TIMEOUT = Duration.ofMinutes(3);
  private static final Duration INCIDENT_POLL_TIMEOUT = Duration.ofSeconds(1);

  @Autowired private CamundaClient camundaClient;
  @Autowired private ResourceLoader resourceLoader;
  @TempDir private File tempDir;

  @BeforeEach
  void clearDocumentStore() {
    InMemoryDocumentStore.INSTANCE.clear();
  }

  @AfterEach
  void clearDocumentStoreAfterTest() {
    InMemoryDocumentStore.INSTANCE.clear();
  }

  @BeforeEach
  void setupPdfStubs() {
    for (var doc : List.of(DOC_PROJECT_LAUNCH, DOC_HEADCOUNT_REPORT, DOC_AUTHOR_INFO)) {
      stubFor(
          get(urlPathEqualTo("/" + doc))
              .willReturn(
                  aResponse().withBodyFile(doc).withHeader("Content-Type", "application/pdf")));
    }
  }

  @ParameterizedTest(name = "{0}", allowZeroInvocations = true)
  @MethodSource({"providers", "v2ProvidersWithoutDocumentReferences"})
  void nestedStructureDocumentsFromToolCallResult(
      ProviderConfig provider, WireMockRuntimeInfo wireMock) {
    var processInstance =
        startProcess(
            provider,
            "Use the Fetch_Report tool to get the full report and describe the content "
                + "of every document in it, including attachments and the cover page.",
            List.of(
                wireMock.getHttpBaseUrl() + "/" + DOC_PROJECT_LAUNCH,
                wireMock.getHttpBaseUrl() + "/" + DOC_HEADCOUNT_REPORT,
                wireMock.getHttpBaseUrl() + "/" + DOC_AUTHOR_INFO));

    awaitCompletionOrIncident(processInstance);
    assertThat(processInstance)
        .hasVariableSatisfies(
            "agent",
            Object.class,
            agent -> {
              logAgentResponse(provider, "nestedStructure", agent);
              RealProviderResponseAssertions.assertNestedDocumentResponse(agent);
            });
  }

  /**
   * The model archives the cover page with a tool that has a document parameter. The model passes
   * the handle of the document, and the tool receives the bytes of the document.
   */
  @ParameterizedTest(name = "{0}", allowZeroInvocations = true)
  @MethodSource("v2Providers")
  void documentParameterOfToolReceivesTheDocument(
      ProviderConfig provider, WireMockRuntimeInfo wireMock) throws IOException {
    stubFor(post(urlPathEqualTo(ARCHIVE_PATH)).willReturn(aResponse().withStatus(200)));

    var processInstance =
        startProcess(
            withArchiveTool(buildModel(provider)),
            "Get the full report, then archive its cover page once. Archive no other document.",
            reportUrls(wireMock),
            Map.of("archiveUrl", wireMock.getHttpBaseUrl() + ARCHIVE_PATH));

    awaitCompletionOrIncident(processInstance);

    var uploads = findAll(postRequestedFor(urlPathEqualTo(ARCHIVE_PATH)));
    Assertions.assertThat(uploads).as("requests to the archive endpoint").hasSize(1);
    var file = uploads.getFirst().getPart("file");
    Assertions.assertThat(file).as("file part of the archive request").isNotNull();
    Assertions.assertThat(file.getBody().asBytes())
        .as("archived bytes")
        .isEqualTo(new ClassPathResource("__files/" + DOC_COVER_PAGE).getContentAsByteArray());
  }

  /**
   * The model returns the cover page with its final response. The response of the agent holds the
   * document under the key the prompt names.
   */
  @ParameterizedTest(name = "{0}", allowZeroInvocations = true)
  @MethodSource("v2Providers")
  void returnedDocumentIsPartOfTheResponse(ProviderConfig provider, WireMockRuntimeInfo wireMock) {
    var processInstance =
        startProcess(
            provider,
            "Get the full report, then return its cover page with your final response under the "
                + "key "
                + COVER_PAGE_KEY
                + ". Do not return any other document.",
            reportUrls(wireMock));

    awaitCompletionOrIncident(processInstance);
    var coverPage = new AtomicReference<Map<?, ?>>();
    assertThat(processInstance)
        .hasVariableSatisfies(
            "downloadedFiles", List.class, files -> coverPage.set((Map<?, ?>) files.get(2)))
        .hasVariableSatisfies(
            "agent",
            Map.class,
            agent -> {
              logAgentResponse(provider, "returnDocument", agent);
              var documents = (Map<?, ?>) agent.get("responseDocuments");
              Assertions.assertThat(documents).as("response documents").isNotNull();
              Assertions.assertThat(documents.keySet().stream().map(String::valueOf))
                  .as("keys of the response documents")
                  .containsExactly(COVER_PAGE_KEY);
              var returned =
                  (Map<?, ?>) ((Map<?, ?>) documents.get(COVER_PAGE_KEY)).get("document");
              Assertions.assertThat(returned).as("returned document").isEqualTo(coverPage.get());
            });
  }

  // ---------------------------------------------------------------------------
  // Provider configurations
  // ---------------------------------------------------------------------------

  static Stream<ProviderConfig> providers() {
    final var selectedProviders = providerCatalog().filter(ProviderConfig::isEnabled).toList();
    if (RealLlmTestEnvironment.isProviderRequired() && selectedProviders.isEmpty()) {
      throw new IllegalStateException(
          "No enabled real document providers were selected; check provider credentials and "
              + "REAL_LLM_PROVIDER_GROUP");
    }
    return selectedProviders.stream();
  }

  /** The selected v2 rows: only their agent instances can use document references. */
  static Stream<ProviderConfig> v2Providers() {
    return providers().filter(ProviderConfig::isV2);
  }

  /** The selected v2 rows with "Use document references" cleared. */
  static Stream<ProviderConfig> v2ProvidersWithoutDocumentReferences() {
    return v2Providers().map(ProviderConfig::withoutDocumentReferences);
  }

  private static Stream<ProviderConfig> providerCatalog() {
    return Stream.of(
        // OpenAI (v1)
        openAiV1("gpt-4.1"),
        openAiV1("gpt-5.5"),
        // OpenAI (v2)
        openAiResponsesV2("gpt-4.1"),
        openAiResponsesV2("gpt-6.1-sol"),
        openAiCompletionsV2("gpt-4.1"),
        openAiCompletionsV2("gpt-5.5"),
        // Anthropic (v1)
        anthropicV1("claude-sonnet-5-5"),
        anthropicV1("claude-haiku-4-5-20251001"),
        // Anthropic (v2)
        anthropicV2("claude-sonnet-5-5"),
        anthropicV2("claude-haiku-4-5-20251001"),
        // Anthropic (v2), AWS Bedrock Mantle backend
        anthropicBedrockMantleV2("claude-sonnet-5"),
        anthropicBedrockMantleV2("claude-haiku-4-5"),
        // AWS Bedrock, v1 (Anthropic models via cross-region inference)
        bedrockV1("global.anthropic.claude-sonnet-5-5"),
        bedrockV1("eu.anthropic.claude-haiku-4-5-20251001-v1:0"),
        // AWS Bedrock, v2 (native Converse API); Anthropic models via cross-region inference
        bedrockV2("global.anthropic.claude-sonnet-5-5"),
        bedrockV2("eu.anthropic.claude-haiku-4-5-20251001-v1:0"),
        // AWS Bedrock, v2 (native Converse API); Amazon's own multimodal Converse model.
        // Disabled for now: it can load a document twice, and Bedrock Converse rejects a request
        // that holds a document twice (#10620).
        bedrockV2("eu.amazon.nova-2-lite-v1:0").disabled(),
        // Google Gemini, v2, via Vertex AI
        googleGeminiVertexAiV2("gemini-3.8-flash", "global"),
        // Small open-weight models via OpenRouter, one per model family
        openRouterV2("openai/gpt-oss-20b"),
        openRouterV2("qwen/qwen3.8-27b"),
        openRouterV2("google/gemma-4-31b-it"),
        openRouterV2("z-ai/glm-5.3-flash"),
        // Docker Model Runner (OpenAI-compatible)
        dockerModelRunnerV1("ai/gemma4:latest").disabled(),
        dockerModelRunnerV1("ai/qwen3.6:latest").disabled(),
        // Ollama (OpenAI-compatible)
        ollamaV1("qwen3.6:latest").disabled(),
        ollamaV1("llama3.1:8b").disabled());
  }

  /** OpenAI, v1 (LangChain4j-backed). */
  static ProviderConfig openAiV1(String model) {
    return new ProviderConfig(
        "openai-v1/" + model,
        RealLlmProviderGroup.OPENAI,
        List.of("OPENAI_API_KEY"),
        AI_AGENT_SUB_PROCESS_V1_ELEMENT_TEMPLATE_PATH,
        Map.of(
            "provider.type",
            "openai",
            "provider.openai.authentication.apiKey",
            envOrPlaceholder("OPENAI_API_KEY"),
            "provider.openai.model.model",
            model));
  }

  /** OpenAI, v2, Responses family. */
  static ProviderConfig openAiResponsesV2(String model) {
    return new ProviderConfig(
        "openai-responses-v2/" + model,
        RealLlmProviderGroup.OPENAI,
        List.of("OPENAI_API_KEY"),
        AI_AGENT_SUB_PROCESS_V2_ELEMENT_TEMPLATE_PATH,
        Map.of(
            "provider.type",
            "openai",
            "provider.openai.backend.type",
            "openai-api",
            "provider.openai.backend.openai.apiKey",
            envOrPlaceholder("OPENAI_API_KEY"),
            "provider.openai.api.type",
            "responses",
            "provider.openai.model.model",
            model));
  }

  /** OpenAI, v2, Completions family. */
  static ProviderConfig openAiCompletionsV2(String model) {
    return new ProviderConfig(
        "openai-completions-v2/" + model,
        RealLlmProviderGroup.OPENAI,
        List.of("OPENAI_API_KEY"),
        AI_AGENT_SUB_PROCESS_V2_ELEMENT_TEMPLATE_PATH,
        Map.of(
            "provider.type",
            "openai",
            "provider.openai.backend.type",
            "openai-api",
            "provider.openai.backend.openai.apiKey",
            envOrPlaceholder("OPENAI_API_KEY"),
            "provider.openai.api.type",
            "completions",
            "provider.openai.model.model",
            model));
  }

  /** Anthropic, v1 (LangChain4j-backed). */
  static ProviderConfig anthropicV1(String model) {
    return new ProviderConfig(
        "anthropic-v1/" + model,
        RealLlmProviderGroup.ANTHROPIC,
        List.of("ANTHROPIC_API_KEY"),
        AI_AGENT_SUB_PROCESS_V1_ELEMENT_TEMPLATE_PATH,
        Map.of(
            "provider.type",
            "anthropic",
            "provider.anthropic.authentication.apiKey",
            envOrPlaceholder("ANTHROPIC_API_KEY"),
            "provider.anthropic.model.model",
            model));
  }

  /** Anthropic, v2. */
  static ProviderConfig anthropicV2(String model) {
    return new ProviderConfig(
        "anthropic-v2/" + model,
        RealLlmProviderGroup.ANTHROPIC,
        List.of("ANTHROPIC_API_KEY"),
        AI_AGENT_SUB_PROCESS_V2_ELEMENT_TEMPLATE_PATH,
        Map.of(
            "provider.type",
            "anthropic",
            "provider.anthropic.backend.type",
            "anthropic-api",
            "provider.anthropic.backend.anthropic.apiKey",
            envOrPlaceholder("ANTHROPIC_API_KEY"),
            "provider.anthropic.model.model",
            model));
  }

  /**
   * Anthropic, v2, via the AWS Bedrock Mantle backend: the same Messages API wire format as
   * anthropicV2, just SigV4-signed and sent to a Bedrock Mantle endpoint instead of
   * api.anthropic.com.
   */
  static ProviderConfig anthropicBedrockMantleV2(String model) {
    return new ProviderConfig(
        "anthropic-bedrock-mantle-v2/" + model,
        RealLlmProviderGroup.BEDROCK,
        List.of("ANTHROPIC_BEDROCK_API_KEY"),
        AI_AGENT_SUB_PROCESS_V2_ELEMENT_TEMPLATE_PATH,
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
            "anthropic." + model));
  }

  /** AWS Bedrock, v1 (LangChain4j-backed); Anthropic models via cross-region inference. */
  static ProviderConfig bedrockV1(String model) {
    return new ProviderConfig(
        "bedrock-v1/" + model,
        RealLlmProviderGroup.BEDROCK,
        List.of("AWS_BEDROCK_ACCESS_KEY", "AWS_BEDROCK_SECRET_KEY"),
        AI_AGENT_SUB_PROCESS_V1_ELEMENT_TEMPLATE_PATH,
        Map.of(
            "provider.type",
            "bedrock",
            "provider.bedrock.authentication.type",
            "credentials",
            "provider.bedrock.authentication.accessKey",
            envOrPlaceholder("AWS_BEDROCK_ACCESS_KEY"),
            "provider.bedrock.authentication.secretKey",
            envOrPlaceholder("AWS_BEDROCK_SECRET_KEY"),
            "provider.bedrock.region",
            "eu-central-1",
            "provider.bedrock.model.model",
            model));
  }

  /** AWS Bedrock, v2 (native Converse API); Anthropic models via cross-region inference. */
  static ProviderConfig bedrockV2(String model) {
    return new ProviderConfig(
        "bedrock-converse-v2/" + model,
        RealLlmProviderGroup.BEDROCK,
        List.of("AWS_BEDROCK_ACCESS_KEY", "AWS_BEDROCK_SECRET_KEY"),
        AI_AGENT_SUB_PROCESS_V2_ELEMENT_TEMPLATE_PATH,
        Map.of(
            "provider.type",
            "bedrock",
            "provider.bedrock.authentication.type",
            "credentials",
            "provider.bedrock.authentication.accessKey",
            envOrPlaceholder("AWS_BEDROCK_ACCESS_KEY"),
            "provider.bedrock.authentication.secretKey",
            envOrPlaceholder("AWS_BEDROCK_SECRET_KEY"),
            "provider.bedrock.region",
            "eu-central-1",
            "provider.bedrock.model.model",
            model,
            "provider.bedrock.model.parameters.promptCaching.enabled",
            "false"));
  }

  /** Google Gemini, v2, via Vertex AI. */
  static ProviderConfig googleGeminiVertexAiV2(String model, String region) {
    return new ProviderConfig(
        "google-gemini-vertex-ai-v2/" + model,
        RealLlmProviderGroup.VERTEX,
        List.of("GOOGLE_VERTEX_AI_PROJECT_ID", "GOOGLE_VERTEX_AI_SERVICE_ACCOUNT_JSON"),
        AI_AGENT_SUB_PROCESS_V2_ELEMENT_TEMPLATE_PATH,
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
            model));
  }

  /**
   * OpenRouter, v2, via the OpenAI custom backend. The file-parser plugin parses PDFs for models
   * without native PDF input with the mistral-ocr engine, which the EU host does not accept.
   */
  static ProviderConfig openRouterV2(String model) {
    return new ProviderConfig(
        "openrouter-v2/" + model,
        RealLlmProviderGroup.OPENROUTER,
        List.of("OPENROUTER_API_KEY"),
        AI_AGENT_SUB_PROCESS_V2_ELEMENT_TEMPLATE_PATH,
        Map.of(
            "provider.type",
            "openai",
            "provider.openai.backend.type",
            "custom",
            "provider.openai.backend.custom.endpoint",
            "https://us.openrouter.ai/api/v1",
            "provider.openai.backend.custom.authentication.type",
            "apiKey",
            "provider.openai.backend.custom.authentication.apiKey",
            envOrPlaceholder("OPENROUTER_API_KEY"),
            "provider.openai.backend.custom.bodyProperties",
            """
            ={
              provider: {require_parameters: true, data_collection: "deny", zdr: true},
              plugins: [{id: "file-parser", pdf: {engine: "mistral-ocr"}}]
            }
            """,
            "provider.openai.api.type",
            "completions",
            "provider.openai.model.model",
            model));
  }

  /** Docker Model Runner, v1 (LangChain4j-backed; OpenAI-compatible). */
  static ProviderConfig dockerModelRunnerV1(String model) {
    var url =
        System.getenv()
            .getOrDefault("DOCKER_MODEL_RUNNER_URL", "http://localhost:12434/engines/llama.cpp/v1");
    return new ProviderConfig(
        "docker-model-runner-v1/" + model,
        RealLlmProviderGroup.LOCAL,
        List.of(), // local endpoint, no API key env var required
        AI_AGENT_SUB_PROCESS_V1_ELEMENT_TEMPLATE_PATH,
        Map.of(
            "provider.type", "openaiCompatible",
            "provider.openaiCompatible.endpoint", url,
            "provider.openaiCompatible.model.model", model));
  }

  /** Ollama, v1 (LangChain4j-backed; OpenAI-compatible). */
  static ProviderConfig ollamaV1(String model) {
    var url = System.getenv().getOrDefault("OLLAMA_URL", "http://localhost:11434/v1");
    return new ProviderConfig(
        "ollama-v1/" + model,
        RealLlmProviderGroup.LOCAL,
        List.of(), // local endpoint, no API key env var required
        AI_AGENT_SUB_PROCESS_V1_ELEMENT_TEMPLATE_PATH,
        Map.of(
            "provider.type", "openaiCompatible",
            "provider.openaiCompatible.endpoint", url,
            "provider.openaiCompatible.model.model", model));
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

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

  private ProcessInstanceEvent startProcess(
      ProviderConfig provider, String userPrompt, List<String> downloadUrls) {
    return startProcess(buildModel(provider), userPrompt, downloadUrls, Map.of());
  }

  private ProcessInstanceEvent startProcess(
      BpmnModelInstance model,
      String userPrompt,
      List<String> downloadUrls,
      Map<String, Object> processVariables) {
    var variables = new HashMap<String, Object>(processVariables);
    variables.put("userPrompt", userPrompt);
    variables.put("downloadUrls", downloadUrls);

    // deploy and wait for process definition to be available
    ZeebeTest.with(camundaClient).awaitCompleteTopology().deploy(model);

    return camundaClient
        .newCreateInstanceCommand()
        .bpmnProcessId("CPT_Document_Tool_Call_Results")
        .latestVersion()
        .variables(variables)
        .send()
        .join();
  }

  private BpmnModelInstance buildModel(ProviderConfig provider) {
    var template = ElementTemplate.from(provider.elementTemplatePath());

    // base properties
    template
        .property("agentContext", "=agent.context")
        .property("data.systemPrompt.prompt", "=\"" + SYSTEM_PROMPT + "\"")
        .property(
            "data.userPrompt.prompt",
            "=if (is defined(followUpUserPrompt)) then followUpUserPrompt else userPrompt")
        .property("data.userPrompt.documents", "=[]")
        .property("data.memory.storage.type", "in-process")
        .property("data.memory.contextWindowSize", "=50")
        .property("data.response.includeAssistantMessage", "=true")
        .property("data.response.includeAgentContext", "=true");

    // provider-specific properties
    provider.properties().forEach(template::property);

    try {
      var templateFile = template.writeTo(new File(tempDir, "template.json"));
      var bpmnFile = resourceLoader.getResource(BPMN_RESOURCE).getFile();
      return new BpmnFile(bpmnFile)
          .apply(templateFile, "AI_Agent", new File(tempDir, "applied.bpmn"));
    } catch (Exception e) {
      throw new RuntimeException("Failed to build BPMN model for " + provider.label(), e);
    }
  }

  private static List<String> reportUrls(WireMockRuntimeInfo wireMock) {
    return Stream.of(DOC_PROJECT_LAUNCH, DOC_HEADCOUNT_REPORT, DOC_AUTHOR_INFO)
        .map(doc -> wireMock.getHttpBaseUrl() + "/" + doc)
        .toList();
  }

  /**
   * Moves the {@code Archive_Document} tool of the process into the ad-hoc sub-process of the
   * agent. The tool takes a document as its {@code document} parameter, which an agent without
   * document references rejects, so only the v2 scenario moves it.
   */
  private static BpmnModelInstance withArchiveTool(BpmnModelInstance model) {
    final ServiceTask archive = model.getModelElementById(ARCHIVE_TOOL);
    archive.getParentElement().removeChildElement(archive);
    final AdHocSubProcess agent = model.getModelElementById("AI_Agent");
    agent.addChildElement(archive);
    return model;
  }

  private void logAgentResponse(ProviderConfig provider, String scenario, Object agent) {
    Object responseText = agent;
    if (agent instanceof Map<?, ?> map && map.containsKey("responseText")) {
      responseText = map.get("responseText");
    }
    LOG.info(
        "\n========== [{} / {}] ==========\n{}\n==========",
        provider.label(),
        scenario,
        responseText);
  }

  private static String envOrPlaceholder(String envVar) {
    return RealLlmTestEnvironment.getOrDefault(envVar, "NOT_SET");
  }

  private static String envOrDefault(String envVar, String defaultValue) {
    return RealLlmTestEnvironment.getOrDefault(envVar, defaultValue);
  }

  // ---------------------------------------------------------------------------
  // Provider config record
  // ---------------------------------------------------------------------------

  record ProviderConfig(
      String label,
      RealLlmProviderGroup providerGroup,
      List<String> requiredEnvVars,
      boolean enabled,
      String elementTemplatePath,
      Map<String, String> properties) {

    ProviderConfig(
        String label,
        RealLlmProviderGroup providerGroup,
        List<String> requiredEnvVars,
        String elementTemplatePath,
        Map<String, String> properties) {
      this(label, providerGroup, requiredEnvVars, true, elementTemplatePath, properties);
    }

    ProviderConfig disabled() {
      return new ProviderConfig(
          label, providerGroup, requiredEnvVars, false, elementTemplatePath, properties);
    }

    boolean isV2() {
      return AI_AGENT_SUB_PROCESS_V2_ELEMENT_TEMPLATE_PATH.equals(elementTemplatePath);
    }

    /** The row with "Use document references" cleared. */
    ProviderConfig withoutDocumentReferences() {
      var withoutReferences = new HashMap<>(properties);
      withoutReferences.putAll(DocumentSetup.REFERENCES_OFF.properties());
      return new ProviderConfig(
          label + " without document references",
          providerGroup,
          requiredEnvVars,
          enabled,
          elementTemplatePath,
          Map.copyOf(withoutReferences));
    }

    boolean isEnabled() {
      // requiredEnvVars is empty for local providers that need no API key, just a URL.
      return enabled
          && providerGroup.isSelected()
          && RealLlmTestEnvironment.hasNonBlankValues(requiredEnvVars);
    }

    @Override
    public String toString() {
      return label;
    }
  }
}
