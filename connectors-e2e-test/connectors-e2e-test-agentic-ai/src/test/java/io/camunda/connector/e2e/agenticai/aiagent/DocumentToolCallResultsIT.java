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
package io.camunda.connector.e2e.agenticai.aiagent;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.camunda.process.test.api.CamundaAssert.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.client.CamundaClient;
import io.camunda.connector.e2e.BpmnFile;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.agenticai.CamundaDocumentTestConfiguration;
import io.camunda.connector.e2e.app.TestConnectorRuntimeApplication;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ResourceLoader;

/**
 * Cross-provider viability test for document handling in tool call results.
 *
 * <p>Validates that real LLM providers can receive and reason about PDF documents extracted from
 * tool call results via the synthetic UserMessage with XML correlation tags.
 *
 * <p>This test is NOT part of the CI suite. Run it manually to assess provider compatibility.
 * Configure API keys via environment variables:
 *
 * <ul>
 *   <li>{@code OPENAI_API_KEY} - OpenAI API key
 *   <li>{@code ANTHROPIC_API_KEY} - Anthropic API key
 *   <li>{@code AWS_BEDROCK_ACCESS_KEY} / {@code AWS_BEDROCK_SECRET_KEY} - AWS Bedrock credentials
 *       (also used for the judge LLM)
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
      // Judge LLM configuration (uses Bedrock Haiku for cost efficiency)
      "camunda.process-test.judge.chat-model.provider=amazon-bedrock",
      "camunda.process-test.judge.chat-model.model=eu.anthropic.claude-haiku-4-5-20251001-v1:0",
      "camunda.process-test.judge.chat-model.region=eu-central-1",
      "camunda.process-test.judge.chat-model.credentials.access-key=${AWS_BEDROCK_ACCESS_KEY:NOT_SET}",
      "camunda.process-test.judge.chat-model.credentials.secret-key=${AWS_BEDROCK_SECRET_KEY:NOT_SET}",
      "camunda.process-test.judge.threshold=0.6"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@CamundaSpringProcessTest
@WireMockTest
@Import(CamundaDocumentTestConfiguration.class)
@Disabled("Manual viability test - requires real LLM API keys via environment variables")
class DocumentToolCallResultsIT {

  private static final Logger LOG = LoggerFactory.getLogger(DocumentToolCallResultsIT.class);

  private static final String ELEMENT_TEMPLATE_PATH =
      "../../connectors/agentic-ai/element-templates/agenticai-aiagent-job-worker.json";

  private static final String BPMN_RESOURCE = "classpath:document-tool-call-results.bpmn";

  private static final String DOC_DIR = "document-tool-call-results/";
  private static final String DOC_PROJECT_LAUNCH = DOC_DIR + "project-launch.pdf";
  private static final String DOC_HEADCOUNT_REPORT = DOC_DIR + "headcount-report.pdf";
  private static final String DOC_AUTHOR_INFO = DOC_DIR + "author-info.pdf";

  private static final String SYSTEM_PROMPT =
      "You are a document analyst. Use the available tools to retrieve and analyze documents. "
          + "When reporting findings, always quote specific facts, numbers, dates, and names "
          + "found in the documents. Be concise.";

  private static final Duration PROCESS_TIMEOUT = Duration.ofMinutes(3);

  @Autowired private CamundaClient camundaClient;
  @Autowired private ResourceLoader resourceLoader;
  @TempDir private File tempDir;

  @BeforeEach
  void clearDocumentStore() {
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

  // ---------------------------------------------------------------------------
  // Scenario 1: Single document from tool call result
  // ---------------------------------------------------------------------------

  @ParameterizedTest(name = "{0}")
  @MethodSource("providers")
  @Disabled
  void singleDocumentFromToolCallResult(ProviderConfig provider, WireMockRuntimeInfo wireMock) {
    var processInstance =
        startProcess(
            provider,
            "Use the Analyze_Single_Document tool to retrieve a document, then tell me "
                + "what project it mentions and when it launched.",
            List.of(wireMock.getHttpBaseUrl() + "/" + DOC_PROJECT_LAUNCH));

    assertThat(processInstance)
        .withAssertionTimeout(PROCESS_TIMEOUT)
        .isCompleted()
        .hasVariableSatisfies(
            "agent", Object.class, agent -> logAgentResponse(provider, "singleDocument", agent))
        .hasVariableSatisfiesJudge(
            "agent",
            """
					The agent called Analyze_Single_Document, received a PDF document, and produced
					a response that mentions Project Zypherion and the launch date March 15, 2026.""");
  }

  // ---------------------------------------------------------------------------
  // Scenario 2: Multiple documents from tool call result
  // ---------------------------------------------------------------------------

  @ParameterizedTest(name = "{0}")
  @MethodSource("providers")
  @Disabled
  void multipleDocumentsFromToolCallResult(ProviderConfig provider, WireMockRuntimeInfo wireMock) {
    var processInstance =
        startProcess(
            provider,
            "Use the Search_Documents tool to find documents and summarize what each one says.",
            List.of(
                wireMock.getHttpBaseUrl() + "/" + DOC_PROJECT_LAUNCH,
                wireMock.getHttpBaseUrl() + "/" + DOC_HEADCOUNT_REPORT));

    assertThat(processInstance)
        .withAssertionTimeout(PROCESS_TIMEOUT)
        .isCompleted()
        .hasVariableSatisfies(
            "agent", Object.class, agent -> logAgentResponse(provider, "multipleDocuments", agent))
        .hasVariableSatisfiesJudge(
            "agent",
            """
					The agent called Search_Documents, received two PDF documents, and produced
					a response that mentions both: Project Zypherion launching on March 15, 2026,
					and a headcount of 847 employees across 12 offices.""");
  }

  // ---------------------------------------------------------------------------
  // Scenario 3: Documents in nested structure from tool call result
  // ---------------------------------------------------------------------------

  @ParameterizedTest(name = "{0}")
  @MethodSource("providers")
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

    assertThat(processInstance)
        .withAssertionTimeout(PROCESS_TIMEOUT)
        .isCompleted()
        .hasVariableSatisfies(
            "agent", Object.class, agent -> logAgentResponse(provider, "nestedStructure", agent))
        .hasVariableSatisfiesJudge(
            "agent",
            """
					The agent called Fetch_Report, received documents embedded in a nested structure,
					and produced a response that references all three documents:
					1. Project Zypherion launching on March 15, 2026
					2. A headcount of 847 employees across 12 offices
					3. The report was prepared by Dr. Kael Thrennix, Chief Analytics Officer""");
  }

  // ---------------------------------------------------------------------------
  // Provider configurations
  // ---------------------------------------------------------------------------

  static Stream<ProviderConfig> providers() {
    List<Predicate<ProviderConfig>> modelFilters = new ArrayList<>();
    // modelFilters.add(p -> p.label().contains("gpt-4.1"));

    return Stream.of(
            // OpenAI
            openai("gpt-4.1"),
            openai("gpt-5.4"),
            // Anthropic
            anthropic("claude-sonnet-4-6"),
            anthropic("claude-haiku-4-5-20251001"),
            // AWS Bedrock (Anthropic models via cross-region inference)
            bedrock("eu.anthropic.claude-sonnet-4-20250514-v1:0"),
            bedrock("global.anthropic.claude-sonnet-4-6"),
            bedrock("eu.anthropic.claude-haiku-4-5-20251001-v1:0"),
            // Docker Model Runner (OpenAI-compatible)
            dockerModelRunner("ai/gemma4:latest").disabled(),
            dockerModelRunner("ai/qwen3.6:latest").disabled(),
            // Ollama (OpenAI-compatible)
            ollama("qwen3.6:latest").disabled(),
            ollama("llama3.1:8b").disabled())
        .filter(
            providerConfig ->
                modelFilters.isEmpty()
                    || modelFilters.stream().anyMatch(f -> f.test(providerConfig)))
        .filter(ProviderConfig::isEnabled);
  }

  // -- OpenAI --

  static ProviderConfig openai(String model) {
    return new ProviderConfig(
        "openai/" + model,
        "OPENAI_API_KEY",
        Map.of(
            "provider.type",
            "openai",
            "provider.openai.authentication.apiKey",
            envOrPlaceholder("OPENAI_API_KEY"),
            "provider.openai.model.model",
            model));
  }

  // -- Anthropic --

  static ProviderConfig anthropic(String model) {
    return new ProviderConfig(
        "anthropic/" + model,
        "ANTHROPIC_API_KEY",
        Map.of(
            "provider.type",
            "anthropic",
            "provider.anthropic.authentication.apiKey",
            envOrPlaceholder("ANTHROPIC_API_KEY"),
            "provider.anthropic.model.model",
            model));
  }

  // -- AWS Bedrock --

  static ProviderConfig bedrock(String model) {
    return new ProviderConfig(
        "bedrock/" + model,
        "AWS_BEDROCK_ACCESS_KEY",
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

  // -- Docker Model Runner (OpenAI-compatible) --

  static ProviderConfig dockerModelRunner(String model) {
    var url =
        System.getenv()
            .getOrDefault("DOCKER_MODEL_RUNNER_URL", "http://localhost:12434/engines/llama.cpp/v1");
    return new ProviderConfig(
        "docker-model-runner/" + model,
        "DOCKER_MODEL_RUNNER_URL",
        Map.of(
            "provider.type", "openaiCompatible",
            "provider.openaiCompatible.endpoint", url,
            "provider.openaiCompatible.model.model", model));
  }

  // -- Ollama (OpenAI-compatible) --

  static ProviderConfig ollama(String model) {
    var url = System.getenv().getOrDefault("OLLAMA_URL", "http://localhost:11434/v1");
    return new ProviderConfig(
        "ollama/" + model,
        "OLLAMA_URL",
        Map.of(
            "provider.type", "openaiCompatible",
            "provider.openaiCompatible.endpoint", url,
            "provider.openaiCompatible.model.model", model));
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private io.camunda.client.api.response.ProcessInstanceEvent startProcess(
      ProviderConfig provider, String userPrompt, List<String> downloadUrls) {
    var model = buildModel(provider);

    // deploy and wait for process definition to be available
    io.camunda.connector.e2e.ZeebeTest.with(camundaClient).awaitCompleteTopology().deploy(model);

    return camundaClient
        .newCreateInstanceCommand()
        .bpmnProcessId("CPT_Document_Tool_Call_Results")
        .latestVersion()
        .variables(
            Map.of(
                "userPrompt", userPrompt,
                "downloadUrls", downloadUrls))
        .send()
        .join();
  }

  private BpmnModelInstance buildModel(ProviderConfig provider) {
    var template = ElementTemplate.from(ELEMENT_TEMPLATE_PATH);

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
    return System.getenv().getOrDefault(envVar, "NOT_SET");
  }

  // ---------------------------------------------------------------------------
  // Provider config record
  // ---------------------------------------------------------------------------

  record ProviderConfig(
      String label, String requiredEnvVar, Map<String, String> properties, boolean enabled) {

    ProviderConfig(String label, String requiredEnvVar, Map<String, String> properties) {
      this(label, requiredEnvVar, properties, true);
    }

    ProviderConfig disabled() {
      return new ProviderConfig(label, requiredEnvVar, properties, false);
    }

    boolean isEnabled() {
      if (!enabled) {
        return false;
      }
      // Local providers don't need an API key env var, just the URL
      if (requiredEnvVar.equals("DOCKER_MODEL_RUNNER_URL") || requiredEnvVar.equals("OLLAMA_URL")) {
        return true;
      }
      return System.getenv(requiredEnvVar) != null;
    }

    @Override
    public String toString() {
      return label;
    }
  }
}
