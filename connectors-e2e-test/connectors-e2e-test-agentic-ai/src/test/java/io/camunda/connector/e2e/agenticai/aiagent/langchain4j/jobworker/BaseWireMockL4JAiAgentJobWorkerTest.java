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
package io.camunda.connector.e2e.agenticai.aiagent.langchain4j.jobworker;

import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentToolSpecifications.EXPECTED_TOOL_SPECIFICATIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.camunda.connector.agenticai.adhoctoolsschema.schema.AdHocToolsSchemaResolver;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.JobWorkerAgentResponse;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.connector.e2e.agenticai.aiagent.AiAgentToolSpecifications.ExpectedTool;
import io.camunda.connector.e2e.agenticai.aiagent.BaseAiAgentJobWorkerTest;
import io.camunda.connector.e2e.agenticai.aiagent.langchain4j.wiremock.OpenAiChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.langchain4j.wiremock.OpenAiChatModelStubs.ToolCall;
import io.camunda.connector.e2e.agenticai.aiagent.langchain4j.wiremock.OpenAiChatModelStubs.Turn;
import io.camunda.connector.e2e.agenticai.aiagent.langchain4j.wiremock.RecordedLlmConversation;
import io.camunda.connector.e2e.agenticai.aiagent.langchain4j.wiremock.RecordedLlmConversation.RecordedChatRequest;
import io.camunda.connector.e2e.agenticai.assertj.JobWorkerAgentResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.assertj.core.api.ThrowingConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.core.io.Resource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Base class for AI Agent (job-worker flavor) e2e tests that drive the conversation loop against a
 * WireMock-stubbed OpenAI-compatible model instead of a Mockito mock.
 *
 * <p>Mirror of {@link
 * io.camunda.connector.e2e.agenticai.aiagent.langchain4j.outboundconnector.BaseWireMockL4JAiAgentConnectorTest}
 * for the job-worker flavor.
 */
@SlowTest
abstract class BaseWireMockL4JAiAgentJobWorkerTest extends BaseAiAgentJobWorkerTest {

  protected static final String SYSTEM_PROMPT =
      "You are a helpful AI assistant. Answer all the questions, but always be nice. Explain your thinking.";

  /** Override to return a different system prompt for tests that inject additional instructions. */
  protected String expectedSystemPrompt() {
    return SYSTEM_PROMPT;
  }

  @MockitoSpyBean protected AdHocToolsSchemaResolver toolsSchemaResolver;

  protected WireMockRuntimeInfo wireMock;

  @BeforeEach
  void captureWireMockInfo(WireMockRuntimeInfo wireMock) {
    this.wireMock = wireMock;
  }

  /** Redirects the connector to the WireMock OpenAI-compatible endpoint. */
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

    final var recorded = RecordedLlmConversation.recorded();
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

  /**
   * Stubs a single text response and creates the process instance, without waiting for completion.
   * Used by tests that expect an incident instead of normal completion.
   */
  protected ZeebeTest setupBasicTestWithoutFeedbackLoop(
      Resource process,
      Function<ElementTemplate, ElementTemplate> elementTemplateModifier,
      Map<String, Object> extraProcessVariables,
      String responseText)
      throws Exception {
    final var initialUserPrompt = "Write a haiku about the sea";

    OpenAiChatModelStubs.stubConversation(Turn.text(responseText, 10, 20));
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

    OpenAiChatModelStubs.stubConversation(
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

    final var recorded = RecordedLlmConversation.recorded();
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

  protected List<ExpectedTool> expectedTools() {
    return EXPECTED_TOOL_SPECIFICATIONS;
  }

  /** Asserts the request's {@code tools} array matches the expected tool specifications by name. */
  protected void assertToolSpecifications(RecordedChatRequest request) {
    assertThat(request.toolNames())
        .containsExactlyInAnyOrderElementsOf(
            expectedTools().stream().map(ExpectedTool::name).toList());
  }

  /** Asserts the ordered conversation messages sent on the (last) request. */
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

  /** Lightweight expectation for an OpenAI-compatible chat message. */
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
