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

import static io.camunda.connector.e2e.agenticai.aiagent.AgentTestFixtures.AI_AGENT_ELEMENT_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.AgentTestFixtures.HAIKU_TEXT;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.DOWNLOAD_CALL_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.RETURN_CALL_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.RETURN_DESCRIPTION;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.RETURN_KEY;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.assertReturnedDocument;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.assertReturnedDocumentEntry;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.documentIdOfToolResult;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.downloadTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.returnDocumentTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.parseDocumentTag;
import static io.camunda.connector.e2e.agenticai.aiagent.wiremock.DocumentIdPlaceholderTransformer.documentId;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.search.enums.IncidentErrorType;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.Turn;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsRecordedConversation;
import io.camunda.connector.e2e.agenticai.assertj.AgentSubProcessResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

@SlowTest
public class AgentSubProcessFeedbackLoopTests extends BaseAgentSubProcessTest {

  private static final String NOTES_KEY = "notes";
  private static final String NOTES_DESCRIPTION = "The notes";
  private static final String UPDATED_DESCRIPTION = "The updated report";
  private static final String SECOND_DOWNLOAD_CALL_ID = "call_download_notes";
  private static final String SECOND_RETURN_CALL_ID = "call_return_notes";
  private static final String THIRD_DOWNLOAD_CALL_ID = "call_download_report";
  private static final String THIRD_RETURN_CALL_ID = "call_return_report";

  @Test
  void executesAgentWithoutUserFeedback() throws Exception {
    testBasicExecutionWithoutFeedbackLoop(
        e -> e,
        HAIKU_TEXT,
        true,
        (agentResponse) ->
            AgentSubProcessResponseAssert.assertThat(agentResponse)
                .hasResponseMessageText(HAIKU_TEXT)
                .hasResponseText(HAIKU_TEXT)
                .hasNoResponseJson());
  }

  @Test
  void basicExecutionWorksWithoutOptionalConfiguration() throws Exception {
    testBasicExecutionWithoutFeedbackLoop(
        elementTemplate -> {
          for (String prefix :
              List.of(
                  "data.memory.",
                  "data.limits.",
                  "data.response.includeAssistantMessage",
                  "data.response.format.")) {
            elementTemplate = elementTemplate.withoutPropertyValueStartingWith(prefix);
          }
          return elementTemplate;
        },
        HAIKU_TEXT,
        false,
        (agentResponse) ->
            // defaults to text
            AgentSubProcessResponseAssert.assertThat(agentResponse)
                .hasNoResponseMessage()
                .hasResponseText(HAIKU_TEXT)
                .hasNoResponseJson());
  }

  @Test
  void executesAgentWithUserFeedback() throws Exception {
    final var initialUserPrompt = "Write a haiku about the sea";
    final var emojiResponse =
        "Endless waves whisper 🌊 | moonlight dances on the tide 🌕 | secrets drift below 🌌";

    OpenAiCompletionsChatModelStubs.stubConversation(
        Turn.text(HAIKU_TEXT, 10, 20), Turn.text(emojiResponse, 11, 22));

    enqueueUserFeedback(userFollowUpFeedback("Add emojis!"), userSatisfiedFeedback());

    final var zeebeTest =
        awaitProcessCompletion(createProcessInstance(Map.of("userPrompt", initialUserPrompt)));

    assertConversationMessages(
        OpenAiCompletionsRecordedConversation.recorded().lastRequest(),
        ExpectedMessage.system(SYSTEM_PROMPT),
        ExpectedMessage.user(initialUserPrompt),
        ExpectedMessage.assistant(HAIKU_TEXT),
        ExpectedMessage.user("Add emojis!"));

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentSubProcessResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasMetrics(new AgentMetrics(2, new AgentMetrics.TokenUsage(21, 42), 0))
                .hasResponseMessageText(emojiResponse)
                .hasResponseText(emojiResponse));

    assertThat(userFeedbackJobWorkerCounter.get()).isEqualTo(2);
  }

  @Test
  void keepsTheReturnedDocumentOnTheFinalResponseOfALaterTurn() throws Exception {
    final var firstAnswer = "Here is the file.";
    final var secondAnswer = "Glad to help.";
    OpenAiCompletionsChatModelStubs.stubConversation(
        downloadTurn(wireMock.getHttpBaseUrl()),
        returnDocumentTurn(),
        Turn.text(firstAnswer, 10, 20),
        Turn.text(secondAnswer, 10, 20));

    enqueueUserFeedback(userFollowUpFeedback("Thanks, anything else?"), userSatisfiedFeedback());

    final var zeebeTest =
        awaitProcessCompletion(
            createProcessInstance(Map.of("userPrompt", "Download the file and give it to me")));

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(4);
    final var downloadResult = recorded.requests().get(1).messages().get(3);
    assertThat(downloadResult.toolCallId()).isEqualTo(DOWNLOAD_CALL_ID);
    final var documentId = parseDocumentTag(downloadResult.content()).id();

    // the second final response did not return the document again, it is still attached
    assertAgentResponse(
        zeebeTest,
        agentResponse -> {
          AgentSubProcessResponseAssert.assertThat(agentResponse)
              .isReady()
              .hasResponseText(secondAnswer);
          assertReturnedDocument(agentResponse.responseDocuments(), documentId);
          assertThat(agentResponse.context().returnDocuments()).containsOnlyKeys(RETURN_KEY);
        });
    assertThat(userFeedbackJobWorkerCounter.get()).isEqualTo(2);
  }

  @Test
  void accumulatesTheReturnedDocumentsOfSeveralTurnsOnTheFinalResponse() throws Exception {
    final var secondAnswer = "Here are the notes too.";
    OpenAiCompletionsChatModelStubs.stubConversation(
        downloadTurn(wireMock.getHttpBaseUrl(), "test.pdf", DOWNLOAD_CALL_ID),
        returnDocumentTurn(RETURN_CALL_ID, RETURN_KEY, documentId(0), RETURN_DESCRIPTION),
        Turn.text("Here is the report.", 10, 20),
        downloadTurn(wireMock.getHttpBaseUrl(), "test.txt", SECOND_DOWNLOAD_CALL_ID),
        returnDocumentTurn(SECOND_RETURN_CALL_ID, NOTES_KEY, documentId(1), NOTES_DESCRIPTION),
        Turn.text(secondAnswer, 10, 20));

    enqueueUserFeedback(userFollowUpFeedback("Add the notes as well."), userSatisfiedFeedback());

    final var zeebeTest =
        awaitProcessCompletion(
            createProcessInstance(Map.of("userPrompt", "Download the report and give it to me")));

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(6);
    final var reportId = documentIdOfToolResult(recorded, DOWNLOAD_CALL_ID);
    final var notesId = documentIdOfToolResult(recorded, SECOND_DOWNLOAD_CALL_ID);

    // the second final response holds the document of the first turn and the one of its own
    assertAgentResponse(
        zeebeTest,
        agentResponse -> {
          AgentSubProcessResponseAssert.assertThat(agentResponse)
              .isReady()
              .hasResponseText(secondAnswer);
          assertThat(agentResponse.responseDocuments().keySet())
              .containsExactly(RETURN_KEY, NOTES_KEY);
          assertReturnedDocumentEntry(
              agentResponse.responseDocuments(), RETURN_KEY, reportId, RETURN_DESCRIPTION);
          assertReturnedDocumentEntry(
              agentResponse.responseDocuments(), NOTES_KEY, notesId, NOTES_DESCRIPTION);
          assertThat(agentResponse.context().returnDocuments().keySet())
              .containsExactly(RETURN_KEY, NOTES_KEY);
        });
    assertThat(userFeedbackJobWorkerCounter.get()).isEqualTo(2);
  }

  @Test
  void replacesTheReturnedDocumentOfAKeyInPlaceWhenALaterTurnReturnsTheKeyAgain() throws Exception {
    final var thirdAnswer = "I updated the report.";
    OpenAiCompletionsChatModelStubs.stubConversation(
        downloadTurn(wireMock.getHttpBaseUrl(), "test.pdf", DOWNLOAD_CALL_ID),
        returnDocumentTurn(RETURN_CALL_ID, RETURN_KEY, documentId(0), RETURN_DESCRIPTION),
        Turn.text("Here is the report.", 10, 20),
        downloadTurn(wireMock.getHttpBaseUrl(), "test.txt", SECOND_DOWNLOAD_CALL_ID),
        returnDocumentTurn(SECOND_RETURN_CALL_ID, NOTES_KEY, documentId(1), NOTES_DESCRIPTION),
        Turn.text("Here are the notes too.", 10, 20),
        downloadTurn(wireMock.getHttpBaseUrl(), "test.csv", THIRD_DOWNLOAD_CALL_ID),
        returnDocumentTurn(THIRD_RETURN_CALL_ID, RETURN_KEY, documentId(2), UPDATED_DESCRIPTION),
        Turn.text(thirdAnswer, 10, 20));

    enqueueUserFeedback(
        userFollowUpFeedback("Add the notes as well."),
        userFollowUpFeedback("Use another file for the report."),
        userSatisfiedFeedback());

    final var zeebeTest =
        awaitProcessCompletion(
            createProcessInstance(Map.of("userPrompt", "Download the report and give it to me")));

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(9);
    final var notesId = documentIdOfToolResult(recorded, SECOND_DOWNLOAD_CALL_ID);
    final var updatedReportId = documentIdOfToolResult(recorded, THIRD_DOWNLOAD_CALL_ID);

    // the report keeps its position, the other key is untouched
    assertAgentResponse(
        zeebeTest,
        agentResponse -> {
          AgentSubProcessResponseAssert.assertThat(agentResponse)
              .isReady()
              .hasResponseText(thirdAnswer);
          assertThat(agentResponse.responseDocuments().keySet())
              .containsExactly(RETURN_KEY, NOTES_KEY);
          assertReturnedDocumentEntry(
              agentResponse.responseDocuments(), RETURN_KEY, updatedReportId, UPDATED_DESCRIPTION);
          assertReturnedDocumentEntry(
              agentResponse.responseDocuments(), NOTES_KEY, notesId, NOTES_DESCRIPTION);
          assertThat(agentResponse.context().returnDocuments().keySet())
              .containsExactly(RETURN_KEY, NOTES_KEY);
        });
    assertThat(userFeedbackJobWorkerCounter.get()).isEqualTo(3);
  }

  @Test
  void raisesIncidentWhenUserPromptIsEmpty() throws Exception {
    final var zeebeTest = awaitActiveIncidents(createProcessInstance(Map.of("userPrompt", "")));

    assertIncident(
        zeebeTest,
        incident -> {
          assertThat(incident.getElementId()).isEqualTo(AI_AGENT_ELEMENT_ID);
          assertThat(incident.getErrorType())
              .isEqualTo(IncidentErrorType.AD_HOC_SUB_PROCESS_NO_RETRIES);
          assertThat(incident.getErrorMessage())
              .contains(
                  "Property: data.userPrompt.prompt: Validation failed. Original message: must not be blank");
        });
  }

  @Test
  void mapsIncidentToJobError() throws Exception {
    final var zeebeTest =
        awaitActiveIncidents(
            createProcessInstance(
                elementTemplate ->
                    elementTemplate.property(
                        "errorExpression", "=jobError(\"Job error: \" + error.message)"),
                Map.of("userPrompt", "")));

    assertIncident(
        zeebeTest,
        incident -> {
          assertThat(incident.getElementId()).isEqualTo(AI_AGENT_ELEMENT_ID);
          assertThat(incident.getErrorType())
              .isEqualTo(IncidentErrorType.AD_HOC_SUB_PROCESS_NO_RETRIES);
          assertThat(incident.getErrorMessage())
              .startsWith("Job error: ")
              .contains(
                  "Property: data.userPrompt.prompt: Validation failed. Original message: must not be blank");
        });
  }
}
