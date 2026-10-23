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
import static io.camunda.connector.e2e.agenticai.aiagent.common.RuntimeToolTestConfiguration.EchoRuntimeTool;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.e2e.agenticai.aiagent.common.RuntimeToolTestConfiguration;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.ToolCall;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.Turn;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsRecordedConversation;
import io.camunda.connector.e2e.agenticai.assertj.AgentSubProcessResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import io.camunda.process.test.api.CamundaAssert;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Import;

@SlowTest
@Import(RuntimeToolTestConfiguration.class)
public class AgentSubProcessRuntimeToolTests extends BaseAgentSubProcessTest {

  private static final String USER_PROMPT = "Echo hello and calculate the superflux product";
  private static final String AI_TOOL_CALL_TEXT = "I will call my tools.";
  private static final String AI_FINAL_TEXT = "I got the results.";

  private static final ToolCall ECHO_CALL =
      ToolCall.of("echo-1", EchoRuntimeTool.NAME, "{\"text\": \"hello\"}");
  private static final ToolCall SUPERFLUX_CALL =
      ToolCall.of("flux-1", SUPERFLUX_PRODUCT_TOOL_NAME, "{\"a\": 5, \"b\": 3}");
  private static final Map<String, String> EXPECTED_RESULT_BY_CALL_ID =
      Map.of("echo-1", "echo: hello", "flux-1", "24");

  @Test
  void runsTheRuntimeToolInTheAgentJobAndCallsTheModelAgain() throws Exception {
    OpenAiCompletionsChatModelStubs.stubConversation(
        Turn.toolCalls(AI_TOOL_CALL_TEXT, 10, 20, ECHO_CALL), Turn.text(AI_FINAL_TEXT, 11, 22));
    enqueueUserFeedback(userSatisfiedFeedback());

    final var zeebeTest =
        awaitProcessCompletion(createProcessInstance(Map.of("userPrompt", USER_PROMPT)));

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(2);
    assertThat(recorded.requests().getFirst().toolDefinitions())
        .extracting(ToolDefinition::name)
        .contains(EchoRuntimeTool.NAME, SUPERFLUX_PRODUCT_TOOL_NAME);

    assertConversationMessages(
        recorded.lastRequest(),
        ExpectedMessage.system(expectedSystemPrompt()),
        ExpectedMessage.user(USER_PROMPT),
        ExpectedMessage.assistantWithToolCalls(AI_TOOL_CALL_TEXT, EchoRuntimeTool.NAME),
        ExpectedMessage.toolCallResult("echo-1", "echo: hello"));

    CamundaAssert.assertThat(zeebeTest.getProcessInstanceEvent())
        .hasCompletedElement(SUPERFLUX_PRODUCT_TOOL_NAME, 0);

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentSubProcessResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasMetrics(new AgentMetrics(2, new AgentMetrics.TokenUsage(21, 42), 1))
                .hasLastIterationKey(2)
                .hasResponseText(AI_FINAL_TEXT));

    assertThat(userFeedbackJobWorkerCounter.get()).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void activatesOnlyTheProcessToolOfAMixedBatchAndReturnsBothResultsInCallOrder(
      boolean runtimeToolFirst) throws Exception {
    final var calls =
        runtimeToolFirst ? List.of(ECHO_CALL, SUPERFLUX_CALL) : List.of(SUPERFLUX_CALL, ECHO_CALL);
    OpenAiCompletionsChatModelStubs.stubConversation(
        Turn.toolCalls(AI_TOOL_CALL_TEXT, 10, 20, calls.toArray(ToolCall[]::new)),
        Turn.text(AI_FINAL_TEXT, 11, 22));
    enqueueUserFeedback(userSatisfiedFeedback());

    final var zeebeTest =
        awaitProcessCompletion(createProcessInstance(Map.of("userPrompt", USER_PROMPT)));

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(2);

    final var expectedMessages = new ArrayList<ExpectedMessage>();
    expectedMessages.add(ExpectedMessage.system(expectedSystemPrompt()));
    expectedMessages.add(ExpectedMessage.user(USER_PROMPT));
    expectedMessages.add(
        ExpectedMessage.assistantWithToolCalls(
            AI_TOOL_CALL_TEXT, calls.stream().map(ToolCall::name).toArray(String[]::new)));
    calls.forEach(
        call ->
            expectedMessages.add(
                ExpectedMessage.toolCallResult(
                    call.id(), EXPECTED_RESULT_BY_CALL_ID.get(call.id()))));
    assertConversationMessages(
        recorded.lastRequest(), expectedMessages.toArray(ExpectedMessage[]::new));

    CamundaAssert.assertThat(zeebeTest.getProcessInstanceEvent())
        .hasCompletedElement(SUPERFLUX_PRODUCT_TOOL_NAME, 1);

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentSubProcessResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasMetrics(new AgentMetrics(2, new AgentMetrics.TokenUsage(21, 42), 2))
                .hasLastIterationKey(2)
                .hasResponseText(AI_FINAL_TEXT));

    assertThat(userFeedbackJobWorkerCounter.get()).isEqualTo(1);
  }
}
