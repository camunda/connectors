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
package io.camunda.connector.e2e.agenticai.aiagent.task;

import static io.camunda.connector.e2e.agenticai.aiagent.AgentToolSpecifications.SUPERFLUX_PRODUCT_TOOL_NAME;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowAssertions.assertNoDocumentInternalsInLlmRequests;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.parseDocumentHandle;
import static io.camunda.connector.e2e.agenticai.aiagent.fixtures.AgentTestFixtures.AI_AGENT_ELEMENT_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.fixtures.AgentTestFixtures.FEEDBACK_LOOP_RESPONSE_TEXT;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.search.enums.IncidentErrorType;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.ToolCall;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.Turn;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsRecordedConversation;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsRecordedConversation.RecordedMessage;
import io.camunda.connector.e2e.agenticai.assertj.AgentResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import io.camunda.process.test.api.CamundaAssert;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@SlowTest
public class AgentTaskToolCallingTests extends BaseAgentTaskTest {

  private static final String USER_PROMPT = "Call a tool that does not exist";
  private static final String AI_TOOL_CALL_TEXT = "I will call my tools.";
  private static final String AI_FINAL_TEXT = "I got the results.";
  private static final String UNKNOWN_TOOL_NAME = "does_not_exist";
  private static final ToolCall UNKNOWN_CALL = ToolCall.of("unknown-1", UNKNOWN_TOOL_NAME, "{}");
  private static final String UNKNOWN_TOOL_ERROR = "The tool 'does_not_exist' does not exist.";
  private static final ToolCall SUPERFLUX_CALL =
      ToolCall.of("flux-1", SUPERFLUX_PRODUCT_TOOL_NAME, "{\"a\": 5, \"b\": 3}");

  @Test
  void executesAgentWithToolCallingAndUserFeedback() throws Exception {
    testInteractionWithToolsAndUserFeedbackLoops(
        e -> e,
        FEEDBACK_LOOP_RESPONSE_TEXT,
        true,
        agentResponse ->
            AgentResponseAssert.assertThat(agentResponse)
                .hasResponseMessageText(FEEDBACK_LOOP_RESPONSE_TEXT)
                .hasResponseText(FEEDBACK_LOOP_RESPONSE_TEXT)
                .hasNoResponseJson());
  }

  @ParameterizedTest
  @CsvSource({"test.pdf,application/pdf", "test.txt,text/plain"})
  void supportsDocumentResponsesFromToolCalls(String filename, String mimeType) throws Exception {
    final var initialUserPrompt = "Go and download a document!";
    final var fileUrl = wireMock.getHttpBaseUrl() + "/" + filename;
    final var aiFinalResponseText = "The content type is '%s'".formatted(mimeType);

    OpenAiCompletionsChatModelStubs.stubConversation(
        Turn.toolCalls(
            "The user asked me to download a document. I will call the Download_A_File tool to do so.",
            10,
            20,
            ToolCall.of("aaa111", "Download_A_File", "{\"url\": \"%s\"}".formatted(fileUrl))),
        Turn.text(
            "I loaded a document and learned that it contains interesting data. Anything specific you want to know?",
            100,
            200),
        Turn.text(aiFinalResponseText, 11, 22));

    enqueueUserFeedback(userFollowUpFeedback("What is the content type?"), userSatisfiedFeedback());

    final var zeebeTest =
        awaitProcessCompletion(
            createProcessInstance(e -> e, Map.of("userPrompt", initialUserPrompt)));

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertNoDocumentInternalsInLlmRequests();
    assertThat(recorded.modelCallCount()).isEqualTo(3);

    final var lastMessages = recorded.lastRequest().messages();
    assertThat(lastMessages).hasSize(6);

    assertRole(lastMessages.get(0), "system");
    assertRole(lastMessages.get(1), "user"); // initial prompt
    assertRole(lastMessages.get(2), "assistant"); // tool call

    // tool result: the document shown as its handle (parsed from the tool message text)
    assertRole(lastMessages.get(3), "tool");
    assertThat(lastMessages.get(3).toolCallId()).isEqualTo("aaa111");
    final var documentHandle = parseDocumentHandle(lastMessages.get(3).content());
    assertThat(documentHandle.contentType()).isEqualTo(mimeType);

    // the content of the document is not part of the conversation
    assertRole(lastMessages.get(4), "assistant"); // response after tool
    assertRole(lastMessages.get(5), "user"); // follow-up prompt
    assertThat(lastMessages.get(5).textContent()).isEqualTo("What is the content type?");

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasNoToolCalls()
                .hasMetrics(new AgentMetrics(3, new AgentMetrics.TokenUsage(121, 242), 1))
                .hasResponseMessageText(aiFinalResponseText)
                .hasResponseText(aiFinalResponseText));

    assertThat(userFeedbackJobWorkerCounter.get()).isEqualTo(2);
  }

  @Test
  void raisesAnIncidentWhenAToolReturnsAnExternalDocument() throws Exception {
    final var docUrl = wireMock.getHttpBaseUrl() + "/test.pdf";
    final var message =
        "Only Camunda documents can be passed to the AI Agent, but the result of the tool 'External_File_Reference' contains an external document.";

    OpenAiCompletionsChatModelStubs.stubConversation(
        Turn.toolCalls(
            "I will reference an externally hosted file.",
            10,
            20,
            ToolCall.of(
                "ext111",
                "External_File_Reference",
                "{\"url\": \"%s\", \"name\": \"Quarterly Report\"}".formatted(docUrl))));

    final var zeebeTest =
        awaitActiveIncidents(
            createProcessInstance(e -> e, Map.of("userPrompt", "Reference an external document!")));

    assertIncident(
        zeebeTest,
        incident -> {
          assertThat(incident.getElementId()).isEqualTo(AI_AGENT_ELEMENT_ID);
          assertThat(incident.getErrorType()).isEqualTo(IncidentErrorType.JOB_NO_RETRIES);
          // the incident message ends with the variables of the error, in no fixed order
          assertThat(incident.getErrorMessage())
              .startsWith(message + " | Error variables: {error={")
              .contains(
                  "message=" + message,
                  "code=UNSUPPORTED_DOCUMENT_REFERENCE",
                  "type=io.camunda.connector.api.error.ConnectorException");
        });
    assertThat(OpenAiCompletionsRecordedConversation.recorded().modelCallCount()).isEqualTo(1);
  }

  @Test
  void answersACallOfAnUnknownToolWithAnErrorAndCallsTheModelAgain() throws Exception {
    assertUnknownToolIsAnswered(Map.of("userPrompt", USER_PROMPT));
  }

  /**
   * An agent instance created without the runtime tools feature has no features in its persisted
   * context: the agent starts with that context instead of creating a new one.
   */
  @Test
  void answersACallOfAnUnknownToolForAnAgentInstanceWithoutTheRuntimeToolsFeature()
      throws Exception {
    assertUnknownToolIsAnswered(
        Map.of(
            "userPrompt",
            USER_PROMPT,
            "agent",
            Map.of("context", Map.of("state", "INITIALIZING"))));
  }

  @Test
  void answersAnUnknownToolNextToAToolOfTheProcessInCallOrder() throws Exception {
    OpenAiCompletionsChatModelStubs.stubConversation(
        Turn.toolCalls(AI_TOOL_CALL_TEXT, 10, 20, UNKNOWN_CALL, SUPERFLUX_CALL),
        Turn.text(AI_FINAL_TEXT, 11, 22));
    enqueueUserFeedback(userSatisfiedFeedback());

    final var zeebeTest =
        awaitProcessCompletion(createProcessInstance(Map.of("userPrompt", USER_PROMPT)));

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(2);

    assertConversationMessages(
        recorded.lastRequest(),
        ExpectedMessage.system(expectedSystemPrompt()),
        ExpectedMessage.user(USER_PROMPT),
        ExpectedMessage.assistantWithToolCalls(
            AI_TOOL_CALL_TEXT, UNKNOWN_TOOL_NAME, SUPERFLUX_PRODUCT_TOOL_NAME),
        ExpectedMessage.toolCallResult("unknown-1", UNKNOWN_TOOL_ERROR),
        ExpectedMessage.toolCallResult("flux-1", "24"));

    CamundaAssert.assertThat(zeebeTest.getProcessInstanceEvent())
        .hasNoActiveIncidents()
        .hasCompletedElement(SUPERFLUX_PRODUCT_TOOL_NAME, 1)
        .hasNotActivatedElements(UNKNOWN_TOOL_NAME);

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasNoToolCalls()
                .hasLastIterationKey(2)
                .hasResponseText(AI_FINAL_TEXT));

    assertThat(userFeedbackJobWorkerCounter.get()).isEqualTo(1);
  }

  /**
   * The agent answers the call in the same job and calls the model again. The call never reaches
   * the process: no element of that name is requested for activation, so no incident is raised.
   */
  private void assertUnknownToolIsAnswered(Map<String, Object> processVariables) throws Exception {
    OpenAiCompletionsChatModelStubs.stubConversation(
        Turn.toolCalls(AI_TOOL_CALL_TEXT, 10, 20, UNKNOWN_CALL), Turn.text(AI_FINAL_TEXT, 11, 22));
    enqueueUserFeedback(userSatisfiedFeedback());

    final var zeebeTest = awaitProcessCompletion(createProcessInstance(processVariables));

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(2);

    assertConversationMessages(
        recorded.lastRequest(),
        ExpectedMessage.system(expectedSystemPrompt()),
        ExpectedMessage.user(USER_PROMPT),
        ExpectedMessage.assistantWithToolCalls(AI_TOOL_CALL_TEXT, UNKNOWN_TOOL_NAME),
        ExpectedMessage.toolCallResult("unknown-1", UNKNOWN_TOOL_ERROR));

    CamundaAssert.assertThat(zeebeTest.getProcessInstanceEvent())
        .hasNoActiveIncidents()
        .hasNotActivatedElements(UNKNOWN_TOOL_NAME);

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasNoToolCalls()
                .hasMetrics(new AgentMetrics(2, new AgentMetrics.TokenUsage(21, 42), 1))
                .hasResponseText(AI_FINAL_TEXT));

    assertThat(userFeedbackJobWorkerCounter.get()).isEqualTo(1);
  }

  // ---------------------------------------------------------------------------
  // OpenAI-compatible multimodal message assertions
  // ---------------------------------------------------------------------------

  private static void assertRole(RecordedMessage message, String expectedRole) {
    assertThat(message.role()).isEqualTo(expectedRole);
  }
}
