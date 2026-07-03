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
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.AI_AGENT_TASK_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentToolSpecifications.EXPECTED_TOOL_SPECIFICATIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.e2e.BpmnFile;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.connector.e2e.agenticai.BaseAgenticAiTest;
import io.camunda.connector.e2e.agenticai.CamundaDocumentTestConfiguration;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsRecordedConversation;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;

@Import(CamundaDocumentTestConfiguration.class)
public abstract class BaseAiAgentTest extends BaseAgenticAiTest {

  public static final String HTTPS_KEYSTORE_PASSWORD = "changeit";

  /**
   * Self-signed keystore backing the WireMock HTTPS port. Also usable as a truststore by clients
   * (e.g. Azure's SDK) that need to trust this same self-signed certificate, since it's a
   * self-signed cert with no separate CA.
   */
  public static java.nio.file.Path httpsKeystoreFile() {
    final var resource =
        BaseAiAgentTest.class.getResource("/wiremock-https/azure-wiremock-https-keystore.p12");
    if (resource == null) {
      throw new IllegalStateException(
          "Missing test resource /wiremock-https/azure-wiremock-https-keystore.p12");
    }
    try {
      return java.nio.file.Path.of(resource.toURI());
    } catch (java.net.URISyntaxException e) {
      throw new IllegalStateException(
          "Invalid URI for test resource /wiremock-https/azure-wiremock-https-keystore.p12", e);
    }
  }

  // Programmatic registration (not @WireMockTest) so we can set a verbose notifier that logs the
  // request journal. We use ConsoleNotifier (stdout) because wiremock-standalone's Slf4jNotifier
  // is bound to its shaded SLF4J and never reaches our logback.
  //
  // The HTTPS port (self-signed keystore under wiremock-https/) is only used by
  // ProviderWireFormatSmokeTests' AzureOpenAiCompletions row — Azure's SDK unconditionally
  // rejects non-HTTPS endpoints for API-key auth. All other tests keep using the HTTP port.
  @RegisterExtension
  static WireMockExtension wireMockExtension =
      WireMockExtension.newInstance()
          .options(
              options()
                  .dynamicPort()
                  .dynamicHttpsPort()
                  .keystorePath(httpsKeystoreFile().toString())
                  .keystorePassword(HTTPS_KEYSTORE_PASSWORD)
                  .keyManagerPassword(HTTPS_KEYSTORE_PASSWORD)
                  .keystoreType("PKCS12")
                  .notifier(new ConsoleNotifier(true)))
          .configureStaticDsl(true)
          .build();

  @Autowired private CamundaProcessTestContext processTestContext;

  protected final LinkedList<Map<String, Object>> userFeedback = new LinkedList<>();

  protected final AtomicInteger userFeedbackJobWorkerCounter = new AtomicInteger(0);

  protected WireMockRuntimeInfo wireMock;

  @BeforeAll
  static void setCamundaAssertDefaultTimeout() {
    CamundaAssert.setAssertionTimeout(Duration.ofSeconds(30));
  }

  @BeforeEach
  void mockUserFeedbackJobWorker() {
    processTestContext
        .mockJobWorker("user_feedback")
        .withHandler(
            (client, job) -> {
              var nextFeedback =
                  userFeedback.isEmpty() ? Collections.emptyMap() : userFeedback.poll();
              userFeedbackJobWorkerCounter.incrementAndGet();
              client.newCompleteCommand(job.getKey()).variables(nextFeedback).execute();
            });
  }

  @BeforeEach
  void clearDocumentStore() {
    InMemoryDocumentStore.INSTANCE.clear();
  }

  @BeforeEach
  void setupWireMock() {
    wireMock = wireMockExtension.getRuntimeInfo();
    // WireMock returns the content type for the YAML file as application/json, so
    // we need to override the stub manually
    WireMock.resetAllScenarios();
    WireMock.reset();
    stubFor(
        get(urlPathEqualTo("/test.yaml"))
            .atPriority(1)
            .willReturn(
                aResponse()
                    .withBodyFile("test.yaml")
                    .withHeader("Content-Type", "application/yaml")));
  }

  @BeforeEach
  void resetFeedbackState() {
    currentProcess = null;
    userFeedbackJobWorkerCounter.set(0);
  }

  protected abstract Resource testProcess();

  protected abstract String elementTemplatePath();

  protected abstract Map<String, String> elementTemplateProperties();

  protected ZeebeTest createProcessInstance(Map<String, Object> variables) throws IOException {
    return createProcessInstance(e -> e, variables);
  }

  protected ZeebeTest createProcessInstance(Resource process, Map<String, Object> variables)
      throws IOException {
    return createProcessInstance(process, e -> e, variables);
  }

  protected ZeebeTest createProcessInstance(
      Function<ElementTemplate, ElementTemplate> elementTemplateModifier,
      Map<String, Object> variables)
      throws IOException {
    return createProcessInstance(testProcess(), elementTemplateModifier, variables);
  }

  protected ZeebeTest createProcessInstance(
      Resource process,
      Function<ElementTemplate, ElementTemplate> elementTemplateModifier,
      Map<String, Object> variables)
      throws IOException {
    final var updatedElementTemplate =
        elementTemplateWithModifications(elementTemplatePath(), elementTemplateModifier);
    final var updatedElementTemplateFile =
        updatedElementTemplate.writeTo(new File(tempDir, "template.json"));
    final var updatedModel = modelWithModifications(process.getFile(), updatedElementTemplateFile);

    return createProcessInstance(customizeModel(updatedModel), variables);
  }

  /**
   * Hook to mutate the built process model just before deployment. Default is a no-op; overridden
   * by tests that need a per-test model tweak (e.g. a unique inbound webhook context to avoid
   * cross-test "context already in use" collisions in the shared per-class runtime).
   */
  protected BpmnModelInstance customizeModel(BpmnModelInstance model) {
    return model;
  }

  protected ElementTemplate elementTemplateWithModifications(
      String elementTemplatePath,
      Function<ElementTemplate, ElementTemplate> elementTemplateModifier) {
    final var elementTemplate = ElementTemplate.from(elementTemplatePath);
    elementTemplateProperties().forEach(elementTemplate::property);
    return elementTemplateModifier.apply(elementTemplate);
  }

  protected BpmnModelInstance modelWithModifications(File model, File elementTemplate) {
    return new BpmnFile(model)
        .apply(elementTemplate, AI_AGENT_TASK_ID, new File(tempDir, "updated.bpmn"));
  }

  /**
   * Registers a conditional behavior that completes {@code user_feedback} jobs in the order given.
   * The last entry repeats indefinitely once all preceding entries are consumed. Behaviors are
   * cleared automatically after each test by CPT.
   */
  @SafeVarargs
  protected final void enqueueUserFeedback(Map<String, Object>... feedback) {
    if (feedback.length == 0) {
      return;
    }
    userFeedback.addAll(List.of(feedback));
  }

  protected Map<String, Object> userSatisfiedFeedback() {
    return Map.of("userSatisfied", true);
  }

  protected Map<String, Object> userFollowUpFeedback(String followUp) {
    return Map.of("userSatisfied", false, "followUpUserPrompt", followUp);
  }

  protected List<ToolDefinition> expectedTools() {
    return EXPECTED_TOOL_SPECIFICATIONS;
  }

  protected void assertToolSpecifications(
      OpenAiCompletionsRecordedConversation.RecordedChatRequest request) {
    assertThat(request.toolDefinitions()).containsExactlyInAnyOrderElementsOf(expectedTools());
  }

  protected void assertConversationMessages(
      OpenAiCompletionsRecordedConversation.RecordedChatRequest request,
      ExpectedMessage... expectedMessages) {
    final var messages = request.messages();
    assertThat(messages)
        .as("number of messages sent to the model")
        .hasSize(expectedMessages.length);

    for (int i = 0; i < expectedMessages.length; i++) {
      expectedMessages[i].assertMatches(i, messages.get(i));
    }
  }

  /**
   * Verifies on the engine that the agent instance is retrievable by key from secondary storage
   * (RDBMS, eventually consistent) with its create-time definition, proving the {@code create}
   * command landed on the broker and was indexed. Accumulated metrics / final status are verified
   * via the {@code agentInstanceClient} spy, not here.
   */
  protected void assertAgentInstanceCreatedOnEngine(long agentInstanceKey, String expectedModel) {
    await()
        .alias("agent instance via REST get-by-key")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              final var agentInstance =
                  camundaClient.newAgentInstanceGetRequest(agentInstanceKey).execute();
              assertThat(agentInstance.getAgentInstanceKey()).isEqualTo(agentInstanceKey);
              assertThat(agentInstance.getDefinition().getModel()).isEqualTo(expectedModel);
              assertThat(agentInstance.getStatus()).isNotNull();
            });
  }

  // ---------------------------------------------------------------------------
  // ExpectedMessage inner record
  // ---------------------------------------------------------------------------

  protected record ExpectedMessage(
      String role, String text, List<String> toolCallNames, String toolCallId) {

    public static ExpectedMessage system(String text) {
      return new ExpectedMessage("system", text, null, null);
    }

    public static ExpectedMessage user(String text) {
      return new ExpectedMessage("user", text, null, null);
    }

    public static ExpectedMessage assistant(String text) {
      return new ExpectedMessage("assistant", text, null, null);
    }

    public static ExpectedMessage assistantWithToolCalls(String text, String... toolCallNames) {
      return new ExpectedMessage("assistant", text, List.of(toolCallNames), null);
    }

    public static ExpectedMessage toolCallResult(String toolCallId, String text) {
      return new ExpectedMessage("tool", text, null, toolCallId);
    }

    public void assertMatches(
        int index, OpenAiCompletionsRecordedConversation.RecordedMessage message) {
      assertThat(message.role()).as("role of message %d", index).isEqualTo(role);

      if (text != null) {
        assertThat(message.textContent()).as("text content of message %d", index).isEqualTo(text);
      }

      if (toolCallNames != null) {
        final var actualNames =
            message.toolCalls().stream()
                .map(OpenAiCompletionsRecordedConversation.RecordedMessage.RecordedToolCall::name)
                .toList();
        assertThat(actualNames)
            .as("tool call names of message %d", index)
            .containsExactlyElementsOf(toolCallNames);
      }

      if (toolCallId != null) {
        assertThat(message.toolCallId())
            .as("tool_call_id of message %d", index)
            .isEqualTo(toolCallId);
      }
    }
  }
}
