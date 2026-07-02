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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.search.enums.AgentInstanceHistoryRole;
import io.camunda.client.api.search.response.AgentInstanceHistory;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.ToolCall;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.Turn;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for #7597: {@code TOOL_RESULT} history items must carry the completion time of
 * the individual tool, not the moment the whole turn's slowest tool finished.
 */
@SlowTest
class AiAgentJobWorkerToolResultTimestampTests extends BaseAiAgentJobWorkerTest {

  @Test
  void recordsDistinctProducedAtPerToolBasedOnActualCompletionTime() throws Exception {
    final var slowFileUrl = wireMock.getHttpBaseUrl() + "/slow-test.pdf";
    stubFor(
        get(urlPathEqualTo("/slow-test.pdf"))
            .atPriority(1)
            .willReturn(
                aResponse()
                    .withBodyFile("test.pdf")
                    .withHeader("Content-Type", "application/pdf")
                    .withFixedDelay(3000)));

    OpenAiCompletionsChatModelStubs.stubConversation(
        Turn.toolCalls(
            null,
            10,
            20,
            ToolCall.of("fast-001", "SuperfluxProduct", "{\"a\": 5, \"b\": 3}"),
            ToolCall.of("slow-001", "Download_A_File", "{\"url\": \"%s\"}".formatted(slowFileUrl))),
        Turn.text("Done.", 15, 25));

    enqueueUserFeedback(userSatisfiedFeedback());

    final var zeebeTest =
        awaitProcessCompletion(
            createProcessInstance(
                Map.of("userPrompt", "Calculate the superflux product and download a file")));

    final var agentInstanceKey = new AtomicLong();
    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            agentInstanceKey.set(agentResponse.context().metadata().agentInstanceKey()));

    final var toolResultHistoryItems =
        camundaClient
            .newAgentInstanceHistorySearchRequest(agentInstanceKey.get())
            .filter(f -> f.role(AgentInstanceHistoryRole.TOOL_RESULT))
            .send()
            .join()
            .items();
    assertThat(toolResultHistoryItems).hasSize(2);

    final var fastResultProducedAt = producedAtForToolCall(toolResultHistoryItems, "fast-001");
    final var slowResultProducedAt = producedAtForToolCall(toolResultHistoryItems, "slow-001");

    assertThat(fastResultProducedAt)
        .as(
            "the fast tool's producedAt must reflect its own (early) completion, not the slow "
                + "tool's completion that let the shared turn proceed")
        .isBefore(slowResultProducedAt.minusSeconds(2));
  }

  private static OffsetDateTime producedAtForToolCall(
      List<AgentInstanceHistory> historyItems, String toolCallId) {
    return historyItems.stream()
        .filter(
            item ->
                item.getToolCalls().stream()
                    .anyMatch(toolCall -> toolCallId.equals(toolCall.getToolCallId())))
        .findFirst()
        .orElseThrow(
            () -> new AssertionError("no TOOL_RESULT history item for tool call " + toolCallId))
        .getProducedAt();
  }
}
