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

import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.DOWNLOAD_CALL_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.RETURN_CALL_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.RETURN_DESCRIPTION;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.RETURN_KEY;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.assertResponseDocumentEntry;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.documentIdOfToolResult;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.downloadTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.returnDocumentTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.fixtures.AgentTestFixtures.HAIKU_TEXT;
import static io.camunda.connector.e2e.agenticai.aiagent.wiremock.DocumentIdPlaceholderTransformer.documentId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.document.ResponseDocumentSelection;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.Turn;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsRecordedConversation;
import io.camunda.connector.e2e.agenticai.assertj.AgentResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

@SlowTest
public class AgentTaskFeedbackLoopTests extends BaseAgentTaskTest {

  private static final String NOTES_KEY = "notes";
  private static final String NOTES_DESCRIPTION = "The notes";
  private static final String SECOND_DOWNLOAD_CALL_ID = "call_download_notes";
  private static final String SECOND_RETURN_CALL_ID = "call_return_notes";

  private static final String EMOJI_HAIKU_TEXT =
      "Endless waves whisper 🌊 | moonlight dances on the tide 🌕 | secrets drift below 🌌";

  @Test
  void executesAgentWithoutUserFeedback() throws Exception {
    testBasicExecutionWithoutFeedbackLoop(
        e -> e,
        HAIKU_TEXT,
        true,
        (agentResponse) ->
            AgentResponseAssert.assertThat(agentResponse)
                .hasResponseMessageText(HAIKU_TEXT)
                .hasResponseText(HAIKU_TEXT)
                .hasNoResponseJson());
  }

  @Test
  void basicExecutionWorksWithoutOptionalConfiguration() throws Exception {
    testBasicExecutionWithoutFeedbackLoop(
        elementTemplate -> {
          for (String prefix :
              List.of("data.tools.", "data.memory.", "data.limits.", "data.response.")) {
            elementTemplate = elementTemplate.withoutPropertyValueStartingWith(prefix);
          }
          return elementTemplate;
        },
        HAIKU_TEXT,
        false,
        (agentResponse) ->
            // defaults to text
            AgentResponseAssert.assertThat(agentResponse)
                .hasNoResponseMessage()
                .hasResponseText(HAIKU_TEXT)
                .hasNoResponseJson());

    // verify that tool schema resolver was called with an empty list (no tools configured)
    verify(toolsSchemaResolver).resolveAdHocToolsSchema(List.of());
  }

  @Test
  void executesAgentWithUserFeedback() throws Exception {
    final var initialUserPrompt = "Write a haiku about the sea";

    OpenAiCompletionsChatModelStubs.stubConversation(
        Turn.text(HAIKU_TEXT, 10, 20), Turn.text(EMOJI_HAIKU_TEXT, 11, 22));

    enqueueUserFeedback(userFollowUpFeedback("Add emojis!"), userSatisfiedFeedback());

    final var zeebeTest =
        awaitProcessCompletion(createProcessInstance(Map.of("userPrompt", initialUserPrompt)));

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(2);

    assertConversationMessages(
        recorded.lastRequest(),
        ExpectedMessage.system(SYSTEM_PROMPT),
        ExpectedMessage.user(initialUserPrompt),
        ExpectedMessage.assistant(HAIKU_TEXT),
        ExpectedMessage.user("Add emojis!"));

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasNoToolCalls()
                .hasMetrics(new AgentMetrics(2, new AgentMetrics.TokenUsage(21, 42), 0))
                .hasResponseMessageText(EMOJI_HAIKU_TEXT)
                .hasResponseText(EMOJI_HAIKU_TEXT));

    assertThat(userFeedbackJobWorkerCounter.get()).isEqualTo(2);
  }

  @Test
  void accumulatesTheResponseDocumentsOfSeveralTurnsOnTheFinalResponse() throws Exception {
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
          AgentResponseAssert.assertThat(agentResponse).isReady().hasResponseText(secondAnswer);
          assertThat(agentResponse.responseDocuments()).containsOnlyKeys(RETURN_KEY, NOTES_KEY);
          assertResponseDocumentEntry(
              agentResponse.responseDocuments(), RETURN_KEY, reportId, RETURN_DESCRIPTION);
          assertResponseDocumentEntry(
              agentResponse.responseDocuments(), NOTES_KEY, notesId, NOTES_DESCRIPTION);
          assertThat(agentResponse.context().responseDocuments())
              .extracting(ResponseDocumentSelection::key)
              .containsExactly(RETURN_KEY, NOTES_KEY);
        });
    assertThat(userFeedbackJobWorkerCounter.get()).isEqualTo(2);
  }
}
