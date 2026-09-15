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

import static io.camunda.connector.e2e.agenticai.aiagent.AgentTestFixtures.AI_AGENT_TASK_V2_ELEMENT_TEMPLATE_PATH;
import static io.camunda.process.test.api.CamundaAssert.assertThatProcessInstance;
import static io.camunda.process.test.api.CamundaAssert.setAssertionTimeout;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.process.test.api.CamundaProcessTestExtension;
import io.camunda.process.test.api.CamundaProcessTestRuntimeMode;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

/**
 * Opt-in smoke test for the real {@code qwen3:30b} model served by Ollama through its
 * OpenAI-compatible API. The AI Agent Connector runs in the repository's bundle container; only the
 * model server runs on the host.
 *
 * <p>Install Ollama 0.33.2, then start and verify the exact model:
 *
 * <pre>{@code
 * OLLAMA_HOST=0.0.0.0:11434 ollama serve
 * ollama pull qwen3:30b
 * ollama --version
 * ollama list
 *
 * ./mvnw package -pl apps/bundle/default-bundle -DskipTests
 * sed \
 *   -e 's|^FROM reg.mini.dev/1212/openjre-base:v26.0.2-dev$|FROM eclipse-temurin:25.0.1_8-jre|' \
 *   -e 's|RUN addgroup --gid 1001 camunda && adduser -S -G camunda -u 1001 --no-create-home camunda|RUN groupadd --gid 1001 camunda \&\& useradd --uid 1001 --gid 1001 --no-create-home --shell /usr/sbin/nologin camunda|' \
 *   apps/bundle/default-bundle/Dockerfile \
 *   | docker build -t camunda/connectors-bundle:local -f - apps/bundle/default-bundle
 *
 * RUN_LOCAL_QWEN_E2E=true \
 * CONNECTORS_IMAGE_NAME=camunda/connectors-bundle \
 * CONNECTORS_IMAGE_VERSION=local \
 * OLLAMA_URL=http://localhost:11434/v1 \
 * LOCAL_QWEN_API_KEY=ollama-local \
 * ./mvnw verify -pl connectors-e2e-test/connectors-e2e-test-agentic-ai \
 *   -Pit-real-llm -Dit.test=LocalQwenE2ETestIT
 * }</pre>
 *
 * <p>{@code OLLAMA_URL} is the host-side OpenAI-compatible endpoint and defaults to {@code
 * http://localhost:11434/v1}. The test rewrites a localhost host name to {@code
 * host.docker.internal} for the Connector container. Ollama appends neither API route here; the
 * Connector appends {@code /chat/completions}.
 *
 * <p>Before starting a process, the test verifies Ollama 0.33.2, the exact model tag and list ID,
 * and the underlying model blob SHA-256. It needs no credentials and does not use a model mock.
 */
@EnabledIfEnvironmentVariable(named = "RUN_LOCAL_QWEN_E2E", matches = "true")
public class LocalQwenE2ETestIT {

  private static final String MODEL = "qwen3:30b";
  private static final String OLLAMA_VERSION = "0.33.2";
  private static final String MODEL_LIST_ID = "ad815644918f";
  private static final String MODEL_BLOB_SHA256 =
      "58574f2e94b99fb9e4391408b57e5aeaaaec10f6384e9a699fc2cb43a5c8eabf";
  private static final String DEFAULT_OLLAMA_URL = "http://localhost:11434/v1";
  private static final String DEFAULT_CONNECTORS_IMAGE =
      "registry.camunda.cloud/team-connectors/connectors-bundle";
  private static final String SYSTEM_PROMPT_RESOURCE = "local-qwen-system-prompt.md";
  private static final String PROCESS_ID = "ai-agent-e2e";
  private static final String EXPECTED_DECISION = "automatic-approval";
  private static final Duration TIMEOUT = Duration.ofMinutes(5);
  private static final ObjectMapper OBJECT_MAPPER = ConnectorsObjectMapperSupplier.getCopy();

  @RegisterExtension
  static final CamundaProcessTestExtension EXTENSION =
      new CamundaProcessTestExtension()
          .withRuntimeMode(CamundaProcessTestRuntimeMode.SHARED)
          .withConnectorsEnabled(true)
          .withConnectorsDockerImageName(env("CONNECTORS_IMAGE_NAME", DEFAULT_CONNECTORS_IMAGE))
          .withConnectorsDockerImageVersion(env("CONNECTORS_IMAGE_VERSION", "SNAPSHOT"))
          .withConnectorsSecret("LOCAL_QWEN_API_KEY", env("LOCAL_QWEN_API_KEY", "ollama-local"))
          .withConnectorsEnv("LOGGING_LEVEL_IO_CAMUNDA_CONNECTOR_AGENTICAI", "TRACE");

  private CamundaClient camundaClient;
  @TempDir private File tempDir;

  @BeforeAll
  static void preflightOllama() {
    setAssertionTimeout(TIMEOUT);

    var apiBase = ollamaApiBase();
    var version = getJson(apiBase + "/api/version");
    assertThat(version.path("version").asText())
        .as("Ollama version at %s", apiBase)
        .isEqualTo(OLLAMA_VERSION);

    var model =
        getJson(apiBase + "/api/tags")
            .path("models")
            .valueStream()
            .filter(candidate -> MODEL.equals(candidate.path("name").asText()))
            .findFirst()
            .orElseThrow(
                () ->
                    new AssertionError(
                        "Ollama model %s is not installed at %s".formatted(MODEL, apiBase)));
    assertThat(model.path("digest").asText())
        .as("Ollama list ID for %s", MODEL)
        .startsWith(MODEL_LIST_ID);

    var modelDetails = postJson(apiBase + "/api/show", Map.of("model", MODEL));
    assertThat(modelDetails.path("modelfile").asText())
        .as("underlying blob SHA-256 for %s", MODEL)
        .contains("sha256-" + MODEL_BLOB_SHA256);
  }

  @Test
  void shouldUseLinkedSystemPromptThroughOllama() {
    camundaClient
        .newDeployResourceCommand()
        .addProcessModel(buildModel(), PROCESS_ID + ".bpmn")
        .addResourceFromClasspath(SYSTEM_PROMPT_RESOURCE)
        .send()
        .join();

    var processInstance =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .latestVersion()
            .variables(Map.of("inputText", "Apply the governed instructions. /no_think"))
            .send()
            .join();

    assertThatProcessInstance(processInstance).isCompleted();

    var captured = new AtomicReference<AgentResponse>();
    assertThatProcessInstance(processInstance)
        .hasVariableSatisfies("agent", AgentResponse.class, captured::set);
    assertThat(captured.get().responseJson())
        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
        .containsEntry("decision", EXPECTED_DECISION)
        .containsEntry("instructionVersion", "GOVERNED-V2");
    assertThat(captured.get().context().metrics().modelCalls()).isEqualTo(1);
    assertThat(captured.get().systemPrompt().type()).isEqualTo("linked");
    assertThat(captured.get().systemPrompt().promptId()).isEqualTo(SYSTEM_PROMPT_RESOURCE);
    assertThat(captured.get().systemPrompt().binding()).isEqualTo("latest");
    assertThat(captured.get().systemPrompt().version()).isEqualTo(1);
    assertThat(captured.get().systemPrompt().prompt())
        .isEqualTo(
            """
            # Local Qwen smoke instructions

            Return only a JSON object with decision, rationale, and instructionVersion.
            Set decision to automatic-approval, rationale to local Qwen smoke, and instructionVersion to GOVERNED-V2.
            """);
    System.out.printf(
        "Completed local Qwen process instance: %d%n", processInstance.getProcessInstanceKey());
  }

  private BpmnModelInstance buildModel() {
    var template =
        ElementTemplate.from(AI_AGENT_TASK_V2_ELEMENT_TEMPLATE_PATH)
            .property("data.agentContext", "=null")
            .property("instructionSource", "resource")
            .property("systemPrompt.resourceId", SYSTEM_PROMPT_RESOURCE)
            .property("systemPrompt.bindingType", "latest")
            .property("data.userPrompt.prompt", "=inputText")
            .property("data.userPrompt.documents", "=[]")
            .property("data.memory.storage.type", "in-process")
            .property("data.memory.contextWindowSize", "=20")
            .property("data.limits.maxModelCalls", "=1")
            .property("data.response.format.type", "text")
            .property("data.response.format.parseJson", "=true")
            .property("data.response.includeAssistantMessage", "=false")
            .property("retryBackoff", "PT0S")
            .property("provider.type", "openai")
            .property("provider.openai.api.type", "completions")
            .property("provider.openai.backend.type", "custom")
            .property("provider.openai.backend.custom.endpoint", connectorOllamaUrl())
            .property("provider.openai.backend.custom.authentication.type", "apiKey")
            .property(
                "provider.openai.backend.custom.authentication.apiKey",
                "{{secrets.LOCAL_QWEN_API_KEY}}")
            .property("provider.openai.model.model", MODEL);

    try {
      var templateFile = template.writeTo(new File(tempDir, "template.json"));
      var bpmnFile = new File(tempDir, "base.bpmn");
      Bpmn.writeModelToFile(
          bpmnFile,
          Bpmn.createExecutableProcess(PROCESS_ID)
              .startEvent()
              .serviceTask("AI_Agent")
              .endEvent()
              .done());
      return new io.camunda.connector.e2e.BpmnFile(bpmnFile)
          .apply(templateFile, "AI_Agent", new File(tempDir, "applied.bpmn"));
    } catch (Exception e) {
      throw new RuntimeException("Failed to build the local Qwen BPMN model", e);
    }
  }

  private static JsonNode getJson(String url) {
    return sendJson(HttpRequest.newBuilder(URI.create(url)).GET().build());
  }

  private static JsonNode postJson(String url, Object body) {
    try {
      return sendJson(
          HttpRequest.newBuilder(URI.create(url))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(body)))
              .build());
    } catch (Exception e) {
      throw new AssertionError("Ollama preflight request failed for " + url, e);
    }
  }

  private static JsonNode sendJson(HttpRequest request) {
    try (var client = HttpClient.newHttpClient()) {
      var response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() / 100 != 2) {
        throw new AssertionError(
            "Ollama preflight request to %s returned HTTP %d: %s"
                .formatted(request.uri(), response.statusCode(), response.body()));
      }
      return OBJECT_MAPPER.readTree(response.body());
    } catch (AssertionError e) {
      throw e;
    } catch (Exception e) {
      throw new AssertionError("Ollama preflight request failed for " + request.uri(), e);
    }
  }

  private static String ollamaApiBase() {
    var endpoint = env("OLLAMA_URL", DEFAULT_OLLAMA_URL);
    endpoint = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
    if (!endpoint.endsWith("/v1")) {
      throw new AssertionError("OLLAMA_URL must end in /v1: " + endpoint);
    }
    return endpoint.substring(0, endpoint.length() - 3);
  }

  private static String connectorOllamaUrl() {
    var endpoint = env("OLLAMA_URL", DEFAULT_OLLAMA_URL);
    return endpoint
        .replace("://localhost:", "://host.docker.internal:")
        .replace("://127.0.0.1:", "://host.docker.internal:");
  }

  private static String env(String name, String defaultValue) {
    var value = System.getenv(name);
    return value == null || value.isBlank() ? defaultValue : value;
  }
}
