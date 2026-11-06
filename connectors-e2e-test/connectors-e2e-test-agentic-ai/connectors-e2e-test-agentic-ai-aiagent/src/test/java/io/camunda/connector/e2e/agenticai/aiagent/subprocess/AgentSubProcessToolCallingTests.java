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
package io.camunda.connector.e2e.agenticai.aiagent.subprocess;

import static io.camunda.connector.e2e.agenticai.aiagent.AgentToolSpecifications.SUPERFLUX_PRODUCT_TOOL_NAME;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowAssertions.assertNoDocumentInternalsInLlmRequests;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.assertDocumentContentBlockJson;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.assertDocumentContentUserMessage;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.contentFollowsSentence;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.parseDocumentHandle;
import static io.camunda.connector.e2e.agenticai.aiagent.fixtures.AgentTestFixtures.FEEDBACK_LOOP_RESPONSE_TEXT;
import static io.camunda.connector.e2e.agenticai.aiagent.wiremock.DocumentIdPlaceholderTransformer.documentId;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures;
import io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.DocumentWithContent;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.ToolCall;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.Turn;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsRecordedConversation;
import io.camunda.connector.e2e.agenticai.assertj.AgentSubProcessResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import io.camunda.process.test.api.CamundaAssert;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@SlowTest
public class AgentSubProcessToolCallingTests extends BaseAgentSubProcessTest {

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
    final var zeebeTest =
        testInteractionWithToolsAndUserFeedbackLoops(
            e -> e,
            FEEDBACK_LOOP_RESPONSE_TEXT,
            true,
            agentResponse ->
                AgentSubProcessResponseAssert.assertThat(agentResponse)
                    .hasResponseMessageText(FEEDBACK_LOOP_RESPONSE_TEXT)
                    .hasResponseText(FEEDBACK_LOOP_RESPONSE_TEXT)
                    .hasNoResponseJson());

    // Inner-instance variables must not leak to the process-instance root scope when a tool with
    // a <zeebe:output> mapping is executed (regression camunda/camunda#51939). The connectors
    // BPMN's SuperfluxProduct uses such an output mapping, so this is a real regression detector.
    assertNoToolCallVariableLeakToProcessScope(zeebeTest);
  }

  @ParameterizedTest
  @CsvSource({"test.pdf,application/pdf", "test.txt,text/plain"})
  void supportsDocumentResponsesFromToolCalls(String filename, String mimeType) throws Exception {
    final var initialUserPrompt = "Go and download a document!";
    final var aiToolCallText =
        "The user asked me to download a document. I will call the Download_A_File tool to do so.";
    final var aiResponseAfterToolText =
        "I loaded a document and learned that it contains interesting data. Anything specific you want to know?";
    final var aiFinalResponseText = "The content type is '%s'".formatted(mimeType);

    OpenAiCompletionsChatModelStubs.stubConversation(
        Turn.toolCalls(
            aiToolCallText,
            10,
            20,
            ToolCall.of(
                "aaa111",
                "Download_A_File",
                "{\"url\": \"%s\"}".formatted(wireMock.getHttpBaseUrl() + "/" + filename))),
        Turn.text(aiResponseAfterToolText, 100, 200),
        Turn.text(aiFinalResponseText, 11, 22));

    enqueueUserFeedback(userFollowUpFeedback("What is the content type?"), userSatisfiedFeedback());

    final var zeebeTest =
        awaitProcessCompletion(
            createProcessInstance(
                elementTemplate ->
                    elementTemplate.property("retryCount", "3").property("retryBackoff", "PT2S"),
                Map.of("userPrompt", initialUserPrompt)));

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertNoDocumentInternalsInLlmRequests();
    assertThat(recorded.modelCallCount()).isEqualTo(3);

    final var lastMessages = recorded.lastRequest().messages();
    assertThat(lastMessages).hasSize(6);

    assertThat(lastMessages.get(0).role()).isEqualTo("system");
    assertThat(lastMessages.get(1).role()).isEqualTo("user");
    assertThat(lastMessages.get(2).role()).isEqualTo("assistant");

    // tool result: the document shown as its handle, its content is not part of the conversation
    final var toolResultText = lastMessages.get(3).content();
    final var documentHandle = parseDocumentHandle(toolResultText);
    assertThat(lastMessages.get(3).role()).isEqualTo("tool");
    assertThat(lastMessages.get(3).toolCallId()).isEqualTo("aaa111");
    assertThat(documentHandle.contentType()).isEqualTo(mimeType);

    assertThat(lastMessages.get(4).role()).isEqualTo("assistant");
    assertThat(lastMessages.get(5).role()).isEqualTo("user");
    assertThat(lastMessages.get(5).textContent()).isEqualTo("What is the content type?");

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentSubProcessResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasMetrics(new AgentMetrics(3, new AgentMetrics.TokenUsage(121, 242), 1))
                .hasResponseMessageText(aiFinalResponseText)
                .hasResponseText(aiFinalResponseText));

    assertThat(userFeedbackJobWorkerCounter.get()).isEqualTo(2);
  }

  @Test
  void loadsTheContentOfAnExternalDocumentOnRequest() throws Exception {
    final var docUrl = wireMock.getHttpBaseUrl() + "/test.txt";
    final var aiFinalResponseText = "Read the external document.";

    OpenAiCompletionsChatModelStubs.stubConversation(
        Turn.toolCalls(
            "I will reference an externally hosted file.",
            10,
            20,
            ToolCall.of(
                "ext111",
                "External_File_Reference",
                "{\"url\": \"%s\", \"name\": \"Notes\"}".formatted(docUrl))),
        DocumentFlowFixtures.getDocumentTurn("load111", documentId(0)),
        Turn.text(aiFinalResponseText, 11, 22));

    enqueueUserFeedback(userSatisfiedFeedback());

    awaitProcessCompletion(
        createProcessInstance(
            elementTemplate -> elementTemplate,
            Map.of("userPrompt", "Reference an external document and read it!")));

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertNoDocumentInternalsInLlmRequests();
    assertThat(recorded.modelCallCount()).isEqualTo(3);

    final var lastMessages = recorded.lastRequest().messages();
    assertThat(lastMessages)
        .extracting(m -> m.role())
        .containsExactly("system", "user", "assistant", "tool", "assistant", "tool", "user");

    final var externalHandle = parseDocumentHandle(lastMessages.get(3).content());
    assertThat(externalHandle.fileName()).isEqualTo("Notes");
    assertThat(lastMessages.get(5).content()).isEqualTo(contentFollowsSentence(externalHandle));

    // the content of the external document is loaded like the one of any other document
    assertDocumentContentUserMessage(
        lastMessages.get(6),
        DocumentWithContent.of(
            externalHandle, block -> assertDocumentContentBlockJson(block, "text", "text/plain")));
  }

  @Test
  void supportsExternalDocumentReferenceResponsesFromToolCalls() throws Exception {
    final var initialUserPrompt = "Reference an external document!";
    final var docUrl = wireMock.getHttpBaseUrl() + "/test.pdf";
    final var docName = "Quarterly Report";
    final var aiFinalResponseText = "Referenced the external document.";

    OpenAiCompletionsChatModelStubs.stubConversation(
        Turn.toolCalls(
            "I will reference an externally hosted file.",
            10,
            20,
            ToolCall.of(
                "ext111",
                "External_File_Reference",
                "{\"url\": \"%s\", \"name\": \"%s\"}".formatted(docUrl, docName))),
        Turn.text(aiFinalResponseText, 11, 22));

    enqueueUserFeedback(userSatisfiedFeedback());

    final var zeebeTest =
        awaitProcessCompletion(
            createProcessInstance(
                elementTemplate ->
                    elementTemplate.property("retryCount", "3").property("retryBackoff", "PT2S"),
                Map.of("userPrompt", initialUserPrompt)));

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertNoDocumentInternalsInLlmRequests();
    assertThat(recorded.modelCallCount()).isEqualTo(2);

    final var lastMessages = recorded.lastRequest().messages();
    assertThat(lastMessages).hasSize(4);

    assertThat(lastMessages.get(0).role()).isEqualTo("system");
    assertThat(lastMessages.get(1).role()).isEqualTo("user");
    assertThat(lastMessages.get(2).role()).isEqualTo("assistant");

    // tool result: external document shown as a handle with its name, never its URL
    final var toolResultText = lastMessages.get(3).content();
    final var externalHandle = parseDocumentHandle(toolResultText);
    assertThat(lastMessages.get(3).role()).isEqualTo("tool");
    assertThat(externalHandle.fileName()).isEqualTo(docName);
    assertThat(externalHandle.size()).isNull();
    assertThat(toolResultText).doesNotContain(docUrl);

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentSubProcessResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasMetrics(new AgentMetrics(2, new AgentMetrics.TokenUsage(21, 42), 1))
                .hasResponseMessageText(aiFinalResponseText)
                .hasResponseText(aiFinalResponseText));

    assertThat(userFeedbackJobWorkerCounter.get()).isEqualTo(1);
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
            AgentSubProcessResponseAssert.assertThat(agentResponse)
                .isReady()
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
            AgentSubProcessResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasMetrics(new AgentMetrics(2, new AgentMetrics.TokenUsage(21, 42), 1))
                .hasResponseText(AI_FINAL_TEXT));

    assertThat(userFeedbackJobWorkerCounter.get()).isEqualTo(1);
  }
}
