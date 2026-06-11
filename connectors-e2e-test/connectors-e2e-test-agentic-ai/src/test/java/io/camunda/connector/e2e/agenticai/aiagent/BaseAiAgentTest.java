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
import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.AI_AGENT_TASK_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentToolSpecifications.EXPECTED_TOOL_SPECIFICATIONS;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import io.camunda.connector.e2e.BpmnFile;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.connector.e2e.agenticai.BaseAgenticAiTest;
import io.camunda.connector.e2e.agenticai.CamundaDocumentTestConfiguration;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsRecordedConversation;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.assertions.JobSelectors;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;

@WireMockTest
@Import(CamundaDocumentTestConfiguration.class)
public abstract class BaseAiAgentTest extends BaseAgenticAiTest {

  @Autowired private CamundaProcessTestContext processTestContext;

  protected final AtomicInteger userFeedbackJobWorkerCounter = new AtomicInteger(0);

  protected WireMockRuntimeInfo wireMock;

  @BeforeAll
  static void setCamundaAssertDefaultTimeout() {
    CamundaAssert.setAssertionTimeout(Duration.ofSeconds(30));
  }

  @BeforeEach
  void clearDocumentStore() {
    InMemoryDocumentStore.INSTANCE.clear();
  }

  @BeforeEach
  void setupWireMock(WireMockRuntimeInfo wm) {
    wireMock = wm;
    // WireMock returns the content type for the YAML file as application/json, so
    // we need to override the stub manually
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

    return createProcessInstance(updatedModel, variables);
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
    final var builder =
        processTestContext
            .when(
                () -> {
                  final ProcessInstanceEvent pi = currentProcess;
                  if (pi == null) throw new AssertionError("process not yet created");
                  CamundaAssert.assertThat(pi).hasActiveElements("User_Feedback");
                })
            .as("user-feedback");
    for (final Map<String, Object> f : feedback) {
      builder.then(
          () -> {
            userFeedbackJobWorkerCounter.incrementAndGet();
            processTestContext.completeJob(JobSelectors.byElementId("User_Feedback"), f);
          });
    }
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
