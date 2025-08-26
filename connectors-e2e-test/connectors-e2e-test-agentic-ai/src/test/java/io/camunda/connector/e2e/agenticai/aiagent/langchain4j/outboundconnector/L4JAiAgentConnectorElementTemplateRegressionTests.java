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
package io.camunda.connector.e2e.agenticai.aiagent.langchain4j.outboundconnector;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.e2e.agenticai.assertj.AgentResponseAssert;
import io.camunda.connector.test.SlowTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@SlowTest
public class L4JAiAgentConnectorElementTemplateRegressionTests extends BaseL4JAiAgentConnectorTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "ai-agent.bpmn",
        "ai-agent-8.8.0-alpha5.bpmn",
        "ai-agent-8.8.0-alpha6.bpmn",
        "ai-agent-8.8.0-alpha7.bpmn"
      })
  void executesAgentWithToolCallingAndUserFeedback(String processFile) throws Exception {
    final var initialUserPrompt = "Explore some of your tools!";
    final var expectedConversation =
        List.of(
            new SystemMessage(
                "You are a helpful AI assistant. Answer all the questions, but always be nice. Explain your thinking."),
            new UserMessage(initialUserPrompt),
            new AiMessage(
                "The user asked me to call some of my tools. I will call the superflux calculation and the task with a text input schema as they look interesting to me.",
                List.of(
                    ToolExecutionRequest.builder()
                        .id("aaa111")
                        .name("SuperfluxProduct")
                        .arguments("{\"a\": 5, \"b\": 3}")
                        .build(),
                    ToolExecutionRequest.builder()
                        .id("bbb222")
                        .name("Search_The_Web")
                        .arguments("{\"searchQuery\": \"Where does this data come from?\"}")
                        .build())),
            new ToolExecutionResultMessage("aaa111", "SuperfluxProduct", "24"),
            new ToolExecutionResultMessage(
                "bbb222", "Search_The_Web", "No results for 'Where does this data come from?'"),
            new AiMessage(
                "I played with the tools and learned that the data comes from the follow-up task and that a superflux calculation of 5 and 3 results in 24."),
            new UserMessage("So what is a superflux calculation anyway?"),
            new AiMessage(
                "A very complex calculation only the superflux calculation tool can do."));

    mockChatInteractions(
        ChatInteraction.of(
            ChatResponse.builder()
                .metadata(
                    ChatResponseMetadata.builder()
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .tokenUsage(new TokenUsage(10, 20))
                        .build())
                .aiMessage((AiMessage) expectedConversation.get(2))
                .build()),
        ChatInteraction.of(
            ChatResponse.builder()
                .metadata(
                    ChatResponseMetadata.builder()
                        .finishReason(FinishReason.STOP)
                        .tokenUsage(new TokenUsage(100, 200))
                        .build())
                .aiMessage((AiMessage) expectedConversation.get(5))
                .build(),
            userFollowUpFeedback("So what is a superflux calculation anyway?")),
        ChatInteraction.of(
            ChatResponse.builder()
                .metadata(
                    ChatResponseMetadata.builder()
                        .finishReason(FinishReason.STOP)
                        .tokenUsage(new TokenUsage(11, 22))
                        .build())
                .aiMessage((AiMessage) expectedConversation.get(7))
                .build(),
            userSatisfiedFeedback()));

    final var processResource = resourceLoader.getResource("classpath:regression/" + processFile);
    final var zeebeTest =
        deployModel(Bpmn.readModelFromStream(processResource.getInputStream()))
            .createInstance(Map.of("action", "executeAgent", "userPrompt", initialUserPrompt))
            .waitForProcessCompletion();

    assertLastChatRequest(3, expectedConversation, false);

    String expectedResponseText = ((AiMessage) expectedConversation.getLast()).text();
    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasNoToolCalls()
                .hasMetrics(new AgentMetrics(3, new AgentMetrics.TokenUsage(121, 242)))
                .hasResponseText(expectedResponseText)
                .hasNoResponseMessage()
                .hasNoResponseJson());

    assertThat(jobWorkerCounter.get()).isEqualTo(2);
  }
}
