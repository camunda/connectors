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

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.ToolCall;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.Turn;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsRecordedConversation;
import io.camunda.connector.e2e.agenticai.assertj.JobWorkerAgentResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@SlowTest
public class AiAgentJobWorkerElementTemplateRegressionTests extends BaseAiAgentJobWorkerTest {

  @ParameterizedTest
  @ValueSource(strings = {"ai-agent-process.bpmn", "ai-agent-process-8.8.0.bpmn"})
  void executesAgentWithToolCallingAndUserFeedback(String processFile) throws Exception {
    final var initialUserPrompt = "Explore some of your tools!";
    final var firstAiMessage =
        "The user asked me to call some of my tools. I will call the superflux calculation and the task with a text input schema as they look interesting to me.";
    final var secondAiMessage =
        "I played with the tools and learned that the data comes from the follow-up task and that a superflux calculation of 5 and 3 results in 24.";
    final var followUpPrompt = "So what is a superflux calculation anyway?";
    final var finalAiMessage =
        "A very complex calculation only the superflux calculation tool can do.";

    OpenAiCompletionsChatModelStubs.stubConversation(
        Turn.toolCalls(
            firstAiMessage,
            10,
            20,
            ToolCall.of("aaa111", "SuperfluxProduct", "{\"a\": 5, \"b\": 3}"),
            ToolCall.of(
                "bbb222",
                "Search_The_Web",
                "{\"searchQuery\": \"Where does this data come from?\"}")),
        Turn.text(secondAiMessage, 100, 200),
        Turn.text(finalAiMessage, 11, 22));

    enqueueUserFeedback(userFollowUpFeedback(followUpPrompt), userSatisfiedFeedback());

    final var processResource = resourceLoader.getResource("classpath:regression/" + processFile);
    final var zeebeTest =
        createProcessInstance(
                processResource,
                Map.of(
                    "userPrompt",
                    initialUserPrompt,
                    "providerEndpoint",
                    wireMock.getHttpBaseUrl() + "/v1"))
            .waitForProcessCompletion(Duration.ofSeconds(30));

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(3);

    assertConversationMessages(
        recorded.lastRequest(),
        ExpectedMessage.system(SYSTEM_PROMPT),
        ExpectedMessage.user(initialUserPrompt),
        ExpectedMessage.assistantWithToolCalls(
            firstAiMessage, "SuperfluxProduct", "Search_The_Web"),
        ExpectedMessage.toolCallResult("aaa111", "24"),
        ExpectedMessage.toolCallResult(
            "bbb222", "No results for 'Where does this data come from?'"),
        ExpectedMessage.assistant(secondAiMessage),
        ExpectedMessage.user(followUpPrompt));

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            JobWorkerAgentResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasMetrics(new AgentMetrics(3, new AgentMetrics.TokenUsage(121, 242), 2))
                .hasResponseText(finalAiMessage)
                .hasNoResponseMessage()
                .hasNoResponseJson());

    assertThat(userFeedbackJobWorkerCounter.get()).isEqualTo(2);
  }
}
