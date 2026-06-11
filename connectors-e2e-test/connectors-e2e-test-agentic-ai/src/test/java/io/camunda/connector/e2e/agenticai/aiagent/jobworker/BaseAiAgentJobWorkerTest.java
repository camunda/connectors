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
package io.camunda.connector.e2e.agenticai.aiagent.jobworker;

import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.AGENT_RESPONSE_VARIABLE;
import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.AI_AGENT_JOB_WORKER_ELEMENT_TEMPLATE_PATH;
import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.AI_AGENT_JOB_WORKER_ELEMENT_TEMPLATE_PROPERTIES;
import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentToolSpecifications.EXPECTED_TOOL_SPECIFICATIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.agenticai.adhoctoolsschema.schema.AdHocToolsSchemaResolver;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.JobWorkerAgentResponse;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.connector.e2e.agenticai.aiagent.BaseAiAgentTest;
import io.camunda.connector.e2e.agenticai.aiagent.outboundconnector.BaseAiAgentConnectorTest;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.ToolCall;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.Turn;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsRecordedConversation;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsRecordedConversation.RecordedChatRequest;
import io.camunda.connector.e2e.agenticai.assertj.JobWorkerAgentResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import io.camunda.process.test.api.CamundaAssert;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.assertj.core.api.ThrowingConsumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Base class for AI Agent (job-worker flavor) e2e tests. Drives the conversation loop against a
 * WireMock-stubbed OpenAI-compatible model and provides framework-agnostic assertion helpers.
 *
 * <p>Mirror of {@link BaseAiAgentConnectorTest} for the job-worker flavor.
 */
@SlowTest
public abstract class BaseAiAgentJobWorkerTest extends BaseAiAgentTest {

  protected static final String SYSTEM_PROMPT =
      "You are a helpful AI assistant. Answer all the questions, but always be nice. Explain your thinking.";

  /** Override to inject a different system prompt (e.g., for A2A tests). */
  protected String expectedSystemPrompt() {
    return SYSTEM_PROMPT;
  }

  @MockitoSpyBean protected AdHocToolsSchemaResolver toolsSchemaResolver;

  @Value("classpath:agentic-ai-ahsp-connectors.bpmn")
  protected Resource testProcess;

  @Override
  protected Resource testProcess() {
    return testProcess;
  }

  @Override
  protected String elementTemplatePath() {
    return AI_AGENT_JOB_WORKER_ELEMENT_TEMPLATE_PATH;
  }

  @Override
  protected Map<String, String> elementTemplateProperties() {
    return AI_AGENT_JOB_WORKER_ELEMENT_TEMPLATE_PROPERTIES;
  }

  // ---------------------------------------------------------------------------
  // Provider redirect
  // ---------------------------------------------------------------------------

  protected ElementTemplate withOpenAiCompatibleProvider(ElementTemplate template) {
    return template
        .property("provider.type", "openaiCompatible")
        .property("provider.openaiCompatible.endpoint", wireMock.getHttpBaseUrl() + "/v1")
        .property("provider.openaiCompatible.authentication.apiKey", "dummy")
        .property("provider.openaiCompatible.model.model", "test-model");
  }

  @Override
  protected ZeebeTest createProcessInstance(
      Resource process,
      Function<ElementTemplate, ElementTemplate> elementTemplateModifier,
      Map<String, Object> variables)
      throws IOException {
    final Function<ElementTemplate, ElementTemplate> composed =
        ((Function<ElementTemplate, ElementTemplate>) this::withOpenAiCompatibleProvider)
            .andThen(elementTemplateModifier);
    return super.createProcessInstance(process, composed, variables);
  }

  // ---------------------------------------------------------------------------
  // Basic single-turn execution
  // ---------------------------------------------------------------------------

  protected ZeebeTest testBasicExecutionWithoutFeedbackLoop(
      Function<ElementTemplate, ElementTemplate> elementTemplateModifier,
      String responseText,
      boolean assertToolSpecifications,
      ThrowingConsumer<JobWorkerAgentResponse> agentResponseAssertions)
      throws Exception {
    return testBasicExecutionWithoutFeedbackLoop(
        testProcess,
        elementTemplateModifier,
        Map.of(),
        responseText,
        assertToolSpecifications,
        agentResponseAssertions);
  }

  protected ZeebeTest testBasicExecutionWithoutFeedbackLoop(
      Resource process,
      Function<ElementTemplate, ElementTemplate> elementTemplateModifier,
      Map<String, Object> extraProcessVariables,
      String responseText,
      boolean assertToolSpecifications,
      ThrowingConsumer<JobWorkerAgentResponse> agentResponseAssertions)
      throws Exception {
    final var zeebeTest =
        setupBasicTestWithoutFeedbackLoop(
            process, elementTemplateModifier, extraProcessVariables, responseText);
    zeebeTest.waitForProcessCompletion();

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(1);

    final var lastRequest = recorded.lastRequest();
    assertConversationMessages(
        lastRequest,
        ExpectedMessage.system(expectedSystemPrompt()),
        ExpectedMessage.user("Write a haiku about the sea"));

    if (assertToolSpecifications) {
      assertToolSpecifications(lastRequest);
    }

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            JobWorkerAgentResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasAgentInstanceKey()
                .hasMetrics(new AgentMetrics(1, new AgentMetrics.TokenUsage(10, 20), 0))
                .satisfies(agentResponseAssertions));

    assertThat(userFeedbackJobWorkerCounter.get()).isEqualTo(1);

    return zeebeTest;
  }

  protected ZeebeTest setupBasicTestWithoutFeedbackLoop(
      Resource process,
      Function<ElementTemplate, ElementTemplate> elementTemplateModifier,
      Map<String, Object> extraProcessVariables,
      String responseText)
      throws Exception {
    final var initialUserPrompt = "Write a haiku about the sea";

    OpenAiCompletionsChatModelStubs.stubConversation(Turn.text(responseText, 10, 20));
    enqueueUserFeedback(userSatisfiedFeedback());

    final Map<String, Object> processVariables = new HashMap<>();
    processVariables.put("userPrompt", initialUserPrompt);
    processVariables.putAll(extraProcessVariables);

    return createProcessInstance(process, elementTemplateModifier, processVariables);
  }

  // ---------------------------------------------------------------------------
  // Multi-turn execution with tool calls and user feedback loops
  // ---------------------------------------------------------------------------

  protected ZeebeTest testInteractionWithToolsAndUserFeedbackLoops(
      Function<ElementTemplate, ElementTemplate> elementTemplateModifier,
      String responseText,
      boolean assertToolSpecifications,
      ThrowingConsumer<JobWorkerAgentResponse> agentResponseAssertions)
      throws Exception {
    final var initialUserPrompt = "Explore some of your tools!";
    final var firstAiMessage =
        "The user asked me to call some of my tools. I will call the superflux calculation and the task with a text input schema as they look interesting to me.";
    final var secondAiMessage =
        "I played with the tools and learned that the data comes from the follow-up task and that a superflux calculation of 5 and 3 results in 24 and 6 and 4 in 30.";
    final var followUpPrompt = "So what is a superflux calculation anyway?";

    OpenAiCompletionsChatModelStubs.stubConversation(
        Turn.toolCalls(
            firstAiMessage,
            10,
            20,
            ToolCall.of("aaa111", "SuperfluxProduct", "{\"a\": 5, \"b\": 3}"),
            ToolCall.of(
                "bbb222",
                "Search_The_Web",
                "{\"searchQuery\": \"Where does this data come from?\"}"),
            ToolCall.of("ccc333", "SuperfluxProduct", "{\"a\": 6, \"b\": 4}")),
        Turn.text(secondAiMessage, 100, 200),
        Turn.text(responseText, 11, 22));

    enqueueUserFeedback(userFollowUpFeedback(followUpPrompt), userSatisfiedFeedback());

    final var zeebeTest =
        createProcessInstance(
                testProcess, elementTemplateModifier, Map.of("userPrompt", initialUserPrompt))
            .waitForProcessCompletion();

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(3);

    final var lastRequest = recorded.lastRequest();
    assertConversationMessages(
        lastRequest,
        ExpectedMessage.system(expectedSystemPrompt()),
        ExpectedMessage.user(initialUserPrompt),
        ExpectedMessage.assistantWithToolCalls(
            firstAiMessage, "SuperfluxProduct", "Search_The_Web", "SuperfluxProduct"),
        ExpectedMessage.toolResult("aaa111", "24"),
        ExpectedMessage.toolResult("bbb222", "No results for 'Where does this data come from?'"),
        ExpectedMessage.toolResult("ccc333", "30"),
        ExpectedMessage.assistant(secondAiMessage),
        ExpectedMessage.user(followUpPrompt));

    if (assertToolSpecifications) {
      assertToolSpecifications(lastRequest);
    }

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            JobWorkerAgentResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasMetrics(new AgentMetrics(3, new AgentMetrics.TokenUsage(121, 242), 3))
                .satisfies(agentResponseAssertions));

    assertThat(userFeedbackJobWorkerCounter.get()).isEqualTo(2);

    return zeebeTest;
  }

  // ---------------------------------------------------------------------------
  // Regression guard
  // ---------------------------------------------------------------------------

  /**
   * Asserts that the {@code toolCall} variable does not exist at the root (process instance) scope.
   * Regression guard for camunda/camunda#51939.
   */
  protected void assertNoToolCallVariableLeakToProcessScope(ZeebeTest zeebeTest) {
    final long processInstanceKey = zeebeTest.getProcessInstanceEvent().getProcessInstanceKey();

    await()
        .alias("toolCall variable does not leak to root scope")
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () -> {
              final var leaked =
                  camundaClient
                      .newVariableSearchRequest()
                      .filter(
                          f ->
                              f.processInstanceKey(processInstanceKey)
                                  .scopeKey(processInstanceKey)
                                  .name("toolCall"))
                      .send()
                      .join()
                      .items();
              assertThat(leaked)
                  .as(
                      "toolCall should only exist on inner-instance scopes — leak to root scope detected")
                  .isEmpty();
            });
  }

  // ---------------------------------------------------------------------------
  // Assertions on the recorded request
  // ---------------------------------------------------------------------------

  protected List<ToolDefinition> expectedTools() {
    return EXPECTED_TOOL_SPECIFICATIONS;
  }

  protected void assertToolSpecifications(RecordedChatRequest request) {
    assertThat(request.toolDefinitions()).containsExactlyInAnyOrderElementsOf(expectedTools());
  }

  protected void assertConversationMessages(
      RecordedChatRequest request, ExpectedMessage... expectedMessages) {
    final var messages = request.messages();
    assertThat(messages)
        .as("number of messages sent to the model")
        .hasSize(expectedMessages.length);

    for (int i = 0; i < expectedMessages.length; i++) {
      expectedMessages[i].assertMatches(i, messages.get(i));
    }
  }

  // ---------------------------------------------------------------------------
  // Agent response assertion
  // ---------------------------------------------------------------------------

  protected void assertAgentResponse(
      ZeebeTest zeebeTest, ThrowingConsumer<JobWorkerAgentResponse> assertions) {
    CamundaAssert.assertThat(zeebeTest.getProcessInstanceEvent())
        .withAssertionTimeout(Duration.ofSeconds(15))
        .hasVariableSatisfies(
            AGENT_RESPONSE_VARIABLE,
            Map.class,
            agentResponseMap -> {
              final var agentResponse =
                  objectMapper.convertValue(agentResponseMap, JobWorkerAgentResponse.class);
              assertions.accept(agentResponse);
            });
  }

  // ---------------------------------------------------------------------------
  // ExpectedMessage inner record
  // ---------------------------------------------------------------------------

  protected record ExpectedMessage(
      String role, String text, List<String> toolCallNames, String toolCallId) {

    static ExpectedMessage system(String text) {
      return new ExpectedMessage("system", text, null, null);
    }

    static ExpectedMessage user(String text) {
      return new ExpectedMessage("user", text, null, null);
    }

    static ExpectedMessage assistant(String text) {
      return new ExpectedMessage("assistant", text, null, null);
    }

    static ExpectedMessage assistantWithToolCalls(String text, String... toolCallNames) {
      return new ExpectedMessage("assistant", text, List.of(toolCallNames), null);
    }

    static ExpectedMessage toolResult(String toolCallId, String text) {
      return new ExpectedMessage("tool", text, null, toolCallId);
    }

    void assertMatches(int index, JsonNode message) {
      assertThat(message.path("role").asText()).as("role of message %d", index).isEqualTo(role);

      if (text != null) {
        assertThat(textContent(message)).as("text content of message %d", index).isEqualTo(text);
      }

      if (toolCallNames != null) {
        final var actualNames = toolCallNamesOf(message);
        assertThat(actualNames)
            .as("tool call names of message %d", index)
            .containsExactlyElementsOf(toolCallNames);
      }

      if (toolCallId != null) {
        assertThat(message.path("tool_call_id").asText())
            .as("tool_call_id of message %d", index)
            .isEqualTo(toolCallId);
      }
    }

    private static String textContent(JsonNode message) {
      final JsonNode content = message.get("content");
      if (content == null || content.isNull()) {
        return null;
      }
      if (content.isTextual()) {
        return content.asText();
      }
      final StringBuilder sb = new StringBuilder();
      content.forEach(
          part -> {
            if ("text".equals(part.path("type").asText())) {
              sb.append(part.path("text").asText());
            }
          });
      return sb.toString();
    }

    private static List<String> toolCallNamesOf(JsonNode message) {
      final JsonNode toolCalls = message.get("tool_calls");
      if (toolCalls == null || !toolCalls.isArray()) {
        return List.of();
      }
      final var names = new java.util.ArrayList<String>();
      toolCalls.forEach(tc -> names.add(tc.path("function").path("name").asText()));
      return names;
    }
  }
}
