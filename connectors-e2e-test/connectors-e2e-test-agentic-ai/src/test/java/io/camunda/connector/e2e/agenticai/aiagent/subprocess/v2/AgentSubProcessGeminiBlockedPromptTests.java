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
package io.camunda.connector.e2e.agenticai.aiagent.subprocess.v2;

import static io.camunda.connector.e2e.agenticai.aiagent.AgentTestFixtures.AI_AGENT_ELEMENT_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.genai.types.BlockedReason;
import com.google.genai.types.FinishReason;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.message.StopReason;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.gemini.GeminiResponseChunks;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.gemini.StreamingGeminiChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.gemini.StreamingGeminiChatModelStubs.GeminiTurnStub;
import io.camunda.connector.e2e.agenticai.assertj.AgentSubProcessResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Native-Gemini e2e coverage for content filtering, both input-side (a blocked prompt) and
 * output-side (a filtered candidate).
 *
 * <p>A <b>blocked prompt</b> is the interesting shape: the response carries <b>no {@code
 * candidates} key at all</b>, only {@code promptFeedback.blockReason}. That is why {@code
 * GeminiContentResponseConverter} keys its blocked-prompt handling on the absence of a candidate
 * rather than on a finish reason. The stub emits exactly one chunk with no candidate — an empty
 * stream would instead trip the assembler's "contained no chunks" guard, a different failure.
 *
 * <p>Both filtered cases end in an <b>incident</b>, not in a completed process: the converter maps
 * them to {@link StopReason#CONTENT_FILTERED} without throwing, and {@code
 * BaseAgentRequestHandler#throwIfTerminalStopReason} then treats that stop reason as terminal
 * ({@code MODEL_RESPONSE_CONTENT_FILTERED}). That handler behavior is provider-agnostic; what these
 * tests pin down is that Gemini's two filtered wire shapes reach it <em>as</em> {@code
 * CONTENT_FILTERED} rather than crashing the converter first. A converter crash would surface as a
 * different message entirely ({@code "Model call failed: ..."} from {@code GeminiChatModel}'s
 * catch-all), so the asserted message is what discriminates the two outcomes.
 *
 * <p>{@link #completesNormallyWhenTruncatedByMaxTokens()} is the deliberate control: it proves the
 * filtered cases fail <em>because</em> they are filtered, not merely because the finish reason was
 * something other than {@code STOP}.
 */
@SlowTest
class AgentSubProcessGeminiBlockedPromptTests extends BaseGeminiNativeSubProcessTest {

  private static final String CONTENT_FILTERED_MESSAGE =
      "Model response was blocked by provider content filtering.";

  /**
   * Fails the job into an incident on the first attempt instead of burning the template's default 3
   * retries with a 30s backoff, which no reasonable assertion timeout would outlive.
   */
  private static final Function<ElementTemplate, ElementTemplate> FAIL_FAST =
      template -> template.property("retryCount", "1").property("retryBackoff", "PT1S");

  @Test
  void raisesContentFilteredIncidentWhenThePromptWasBlockedAndNoCandidateWasReturned()
      throws Exception {
    final var userPrompt = "Write a haiku about the sea";

    StreamingGeminiChatModelStubs.stubTurns(
        GeminiTurnStub.of(
            GeminiResponseChunks.chunk()
                .blockReason(BlockedReason.Known.SAFETY)
                .blockReasonMessage("Blocked by safety filters.")
                .usage(10, 0)
                .build()));

    final var zeebeTest =
        awaitActiveIncidents(createProcessInstance(FAIL_FAST, Map.of("userPrompt", userPrompt)));

    // Not "Model call failed: ..." - the candidate-less response was converted cleanly and only
    // then rejected as terminal.
    assertContentFilteredIncident(zeebeTest);

    assertThat(recordedLoggedRequests())
        .as("a blocked prompt must not be retried at the model-call level")
        .hasSize(1);
  }

  @Test
  void raisesContentFilteredIncidentWhenTheCandidateFinishReasonIsSafety() throws Exception {
    final var userPrompt = "Write a haiku about the sea";

    // Distinct from the blocked-prompt case: here a candidate DOES exist, with content, and the
    // finish reason is what has to map to CONTENT_FILTERED. Reaching this at all also exercises why
    // the converter reads candidates()[0].content().parts() directly instead of the SDK's
    // text()/parts() convenience accessors - those call checkFinishReason() internally and throw
    // for
    // every finish reason outside {UNSPECIFIED, STOP, MAX_TOKENS}, i.e. for exactly this response.
    StreamingGeminiChatModelStubs.stubTurns(
        GeminiTurnStub.of(
            GeminiResponseChunks.chunk()
                .text("Partial answer before filtering.")
                .finishReason(FinishReason.Known.SAFETY)
                .usage(10, 5)
                .build()));

    final var zeebeTest =
        awaitActiveIncidents(createProcessInstance(FAIL_FAST, Map.of("userPrompt", userPrompt)));

    assertContentFilteredIncident(zeebeTest);
  }

  /**
   * The incident message is the connector exception's message with the serialized error variables
   * appended, so it is matched by prefix; the appended {@code MODEL_RESPONSE_CONTENT_FILTERED}
   * error code is the part that actually discriminates this failure from any other model-call
   * failure.
   */
  private void assertContentFilteredIncident(ZeebeTest zeebeTest) {
    assertIncident(
        zeebeTest,
        incident -> {
          assertThat(incident.getElementId()).isEqualTo(AI_AGENT_ELEMENT_ID);
          assertThat(incident.getErrorMessage())
              .startsWith(CONTENT_FILTERED_MESSAGE)
              .contains("MODEL_RESPONSE_CONTENT_FILTERED");
        });
  }

  @Test
  void completesNormallyWhenTruncatedByMaxTokens() throws Exception {
    final var userPrompt = "Write a haiku about the sea";
    final var truncatedText = "Endless waves whisper";

    // Control case: MAX_TOKENS is also a non-STOP finish reason, but it maps to LENGTH rather than
    // CONTENT_FILTERED, so it is NOT terminal and the agent finishes normally.
    StreamingGeminiChatModelStubs.stubTurns(
        GeminiTurnStub.of(
            GeminiResponseChunks.chunk()
                .text(truncatedText)
                .finishReason(FinishReason.Known.MAX_TOKENS)
                .usage(10, 20)
                .build()));
    enqueueUserFeedback(userSatisfiedFeedback());

    final var zeebeTest =
        awaitProcessCompletion(createProcessInstance(Map.of("userPrompt", userPrompt)));

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentSubProcessResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasResponseMessageText(truncatedText)
                .hasMetrics(new AgentMetrics(1, new AgentMetrics.TokenUsage(10, 20), 0))
                .hasResponseMessageSatisfying(
                    message -> {
                      assertThat(message.stopReason())
                          .as("MAX_TOKENS maps to LENGTH, which is not terminal")
                          .isEqualTo(StopReason.LENGTH);
                      // The raw vendor value is preserved under the provider metadata namespace
                      // regardless of how it was mapped.
                      assertThat(message.metadata())
                          .extractingByKey("google-gemini")
                          .asInstanceOf(
                              org.assertj.core.api.InstanceOfAssertFactories.map(
                                  String.class, Object.class))
                          .containsEntry("finishReason", "MAX_TOKENS");
                    }));
  }
}
