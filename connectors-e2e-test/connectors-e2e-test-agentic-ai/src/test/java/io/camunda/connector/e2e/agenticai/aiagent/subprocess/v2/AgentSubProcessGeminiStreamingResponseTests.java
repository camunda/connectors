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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.genai.types.FinishReason;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.message.content.ReasoningContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.gemini.GeminiResponseChunks;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.gemini.StreamingGeminiChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.gemini.StreamingGeminiChatModelStubs.GeminiTurnStub;
import io.camunda.connector.e2e.agenticai.assertj.AgentSubProcessResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Native-Gemini e2e coverage for {@code GeminiContentStreamAssemblerImpl}: proves its merge rules
 * hold against real (simulated) chunked SSE traffic, not just against hand-fed SDK objects in a
 * unit test. {@code GeminiChatModel} always calls {@code generateContentStream}, so <em>every</em>
 * Gemini request goes through the assembler — these are the cases where chunk boundaries are
 * actually visible in the result.
 */
@SlowTest
class AgentSubProcessGeminiStreamingResponseTests extends BaseGeminiNativeSubProcessTest {

  @Test
  void concatenatesTextArrivingAcrossSeveralSseChunks() throws Exception {
    final var userPrompt = "Write a haiku about the sea";

    // Gemini streams answer text incrementally and reports finishReason/usage only on the last
    // chunk - the assembler has to concatenate the run and take usage from the final chunk.
    StreamingGeminiChatModelStubs.stubTurns(
        GeminiTurnStub.of(
            GeminiResponseChunks.chunk().text("Endless waves whisper | ").build(),
            GeminiResponseChunks.chunk().text("moonlight dances on the tide | ").build(),
            GeminiResponseChunks.chunk()
                .text("secrets drift below.")
                .finishReason(FinishReason.Known.STOP)
                .usage(10, 20)
                .build()));
    enqueueUserFeedback(userSatisfiedFeedback());

    final var zeebeTest =
        awaitProcessCompletion(createProcessInstance(Map.of("userPrompt", userPrompt)));

    final var mergedText =
        "Endless waves whisper | moonlight dances on the tide | secrets drift below.";
    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentSubProcessResponseAssert.assertThat(agentResponse)
                .isReady()
                // One TextContent, not three: proves the run was merged rather than appended.
                .hasResponseMessageText(mergedText)
                .hasResponseText(mergedText)
                // Usage is the last chunk's, never a sum across chunks.
                .hasMetrics(new AgentMetrics(1, new AgentMetrics.TokenUsage(10, 20), 0)));
  }

  @Test
  void keepsThoughtAndAnswerTextRunsSeparateWhenBothStreamInOneTurn() throws Exception {
    final var userPrompt = "Write a haiku about the sea";

    // A thinking-enabled Gemini response streams its thoughts first (thought=true) and then the
    // answer (thought absent). Both are text parts, so the ONLY thing separating them is the
    // thought flag - the assembler must not collapse them into one blob.
    StreamingGeminiChatModelStubs.stubTurns(
        GeminiTurnStub.of(
            GeminiResponseChunks.chunk().thought("Let me think about ").build(),
            GeminiResponseChunks.chunk().thought("the sea for a moment.").build(),
            GeminiResponseChunks.chunk().text("Endless waves ").build(),
            GeminiResponseChunks.chunk()
                .text("whisper.")
                .finishReason(FinishReason.Known.STOP)
                .usage(10, 20, 30, 7)
                .build()));
    enqueueUserFeedback(userSatisfiedFeedback());

    final var zeebeTest =
        awaitProcessCompletion(createProcessInstance(Map.of("userPrompt", userPrompt)));

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentSubProcessResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasReasoningContent()
                .hasResponseMessageSatisfying(
                    message -> {
                      // Two runs, in order, each merged within itself.
                      assertThat(message.content())
                          .as("assistant content: one reasoning run then one text run")
                          .hasSize(2);
                      assertThat(message.content().get(0))
                          .isInstanceOfSatisfying(
                              ReasoningContent.class,
                              reasoning ->
                                  assertThat(reasoning.text())
                                      .isEqualTo("Let me think about the sea for a moment."));
                      assertThat(message.content().get(1))
                          .isInstanceOfSatisfying(
                              TextContent.class,
                              text -> assertThat(text.text()).isEqualTo("Endless waves whisper."));
                    })
                // responseText only ever reflects the answer text, never the reasoning.
                .hasResponseText("Endless waves whisper.")
                .metricsSatisfy(
                    metrics -> {
                      // thoughtsTokenCount maps onto the domain reasoning counter.
                      assertThat(metrics.tokenUsage().reasoningTokenCount())
                          .as("reasoning token count from usageMetadata.thoughtsTokenCount")
                          .isEqualTo(30);
                      // Gemini's implicit caching reports cache READS only, mapped onto
                      // cacheReadTokenCount; there is no cache-write counter, so
                      // cacheCreationTokenCount stays at its default of 0.
                      assertThat(metrics.tokenUsage().cacheReadTokenCount())
                          .as("cache read token count from usageMetadata.cachedContentTokenCount")
                          .isEqualTo(7);
                      assertThat(metrics.tokenUsage().cacheCreationTokenCount())
                          .as("Gemini reports no cache-write counter")
                          .isZero();
                    }));
  }

  @Test
  void closesTheTextRunAtAThoughtSignatureBoundary() throws Exception {
    final var userPrompt = "Write a haiku about the sea";
    final var signature = GeminiResponseChunks.encodeSignature("sig-e2e-stream-boundary");

    // A thoughtSignature belongs to exactly one part and must round-trip verbatim, so a part
    // carrying one closes its run: the following text starts a new part instead of being appended.
    StreamingGeminiChatModelStubs.stubTurns(
        GeminiTurnStub.of(
            GeminiResponseChunks.chunk().text("First half.", signature).build(),
            GeminiResponseChunks.chunk()
                .text("Second half.")
                .finishReason(FinishReason.Known.STOP)
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
                // the two TextContent blocks are joined with no separator into the response text
                // - it must not be truncated to just the first, signature-carrying block
                .hasResponseText("First half.Second half.")
                .hasResponseMessageSatisfying(
                    message -> {
                      assertThat(message.content())
                          .as("signature-carrying part must not absorb the following text")
                          .hasSize(2);
                      assertThat(message.content().get(0))
                          .isInstanceOfSatisfying(
                              TextContent.class,
                              text -> {
                                assertThat(text.text()).isEqualTo("First half.");
                                assertThat(text.metadata())
                                    .as("thoughtSignature preserved as base64 metadata")
                                    .containsEntry("thoughtSignature", signature);
                              });
                      assertThat(message.content().get(1))
                          .isInstanceOfSatisfying(
                              TextContent.class,
                              text -> assertThat(text.text()).isEqualTo("Second half."));
                    }));
  }
}
