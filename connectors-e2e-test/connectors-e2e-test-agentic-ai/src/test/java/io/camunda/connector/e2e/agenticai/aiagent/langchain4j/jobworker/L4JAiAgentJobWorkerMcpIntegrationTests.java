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
package io.camunda.connector.e2e.agenticai.aiagent.langchain4j.jobworker;

import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.HAIKU_TEXT;
import static io.camunda.connector.e2e.agenticai.aiagent.langchain4j.Langchain4JAiAgentToolSpecifications.EXPECTED_MCP_TOOL_SPECIFICATIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.e2e.agenticai.McpTestServerConfiguration;
import io.camunda.connector.e2e.agenticai.McpTestServerConfiguration.McpTestServers;
import io.camunda.connector.e2e.agenticai.aiagent.langchain4j.common.AgentMcpIntegrationTestSupport;
import io.camunda.connector.e2e.agenticai.aiagent.langchain4j.common.AgentMcpStdioWarmUp;
import io.camunda.connector.e2e.agenticai.assertj.JobWorkerAgentResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.test.context.TestPropertySource;

@SlowTest
@TestPropertySource(properties = {"camunda.connector.agenticai.mcp.client.enabled=true"})
@Import({McpTestServerConfiguration.class, AgentMcpStdioWarmUp.class})
public class L4JAiAgentJobWorkerMcpIntegrationTests extends BaseL4JAiAgentJobWorkerTest {

  private static final String SYSTEM_PROMPT =
      "You are a helpful AI assistant. Answer all the questions, but always be nice. Explain your thinking.";

  @Value("classpath:agentic-ai-ahsp-connectors-mcp.bpmn")
  protected Resource testProcessWithMcp;

  @Autowired private McpTestServers mcpTestServers;

  private AgentMcpIntegrationTestSupport mcpSupport;

  @BeforeEach
  void setUpMcpSupport() {
    mcpSupport = new AgentMcpIntegrationTestSupport(mcpTestServers, objectMapper);
  }

  @Override
  protected List<ToolSpecification> expectedToolSpecifications() {
    return EXPECTED_MCP_TOOL_SPECIFICATIONS;
  }

  @Override
  protected BpmnModelInstance modelWithModifications(File model, File elementTemplate) {
    return mcpSupport.configureRemoteClientUrls(
        super.modelWithModifications(model, elementTemplate));
  }

  @Test
  void executesMcpToolDiscovery() throws Exception {
    final var zeebeTest =
        testBasicExecutionWithoutFeedbackLoop(
            testProcessWithMcp,
            e -> e,
            HAIKU_TEXT,
            Map.of(),
            true,
            agentResponse ->
                JobWorkerAgentResponseAssert.assertThat(agentResponse)
                    .hasResponseMessageText(HAIKU_TEXT)
                    .hasResponseText(HAIKU_TEXT)
                    .hasNoResponseJson());

    CamundaAssert.assertThat(zeebeTest.getProcessInstanceEvent())
        .hasCompletedElements("AI_Agent", "User_Feedback");

    mcpSupport.assertGatewayCompletionsOfToolDiscoveryOnly(zeebeTest.getProcessInstanceEvent());
    verify(chatModel, times(1)).chat(any(ChatRequest.class));
  }

  @Test
  void handlesMcpToolCalls() throws IOException {
    final var initialUserPrompt = "Explore some of your MCP tools!";
    final var firstAiMessage =
        new AiMessage(
            "The user asked me to call some of my MCP tools. I will call some of them.",
            AgentMcpIntegrationTestSupport.toolExecutionRequests());
    final var secondAiMessage = new AiMessage("I called some of my MCP tools.");
    final var finalAiMessage = new AiMessage("No.");

    mockChatInteractions(
        ChatInteraction.of(chatResponse(FinishReason.TOOL_EXECUTION, 10, 20, firstAiMessage)),
        ChatInteraction.of(
            chatResponse(FinishReason.STOP, 100, 200, secondAiMessage),
            userFollowUpFeedback("Ok thanks, anything else?")),
        ChatInteraction.of(
            chatResponse(FinishReason.STOP, 11, 22, finalAiMessage), userSatisfiedFeedback()));

    final var zeebeTest =
        createProcessInstance(testProcessWithMcp, e -> e, Map.of("userPrompt", initialUserPrompt))
            .waitForProcessCompletion();

    final var lastMessages = chatRequestCaptor.getValue().messages();
    mcpSupport.assertEchoedHeaders(lastMessages);

    final List<ChatMessage> expectedConversation = new ArrayList<>();
    expectedConversation.add(new SystemMessage(SYSTEM_PROMPT));
    expectedConversation.add(new UserMessage(initialUserPrompt));
    expectedConversation.add(firstAiMessage);
    expectedConversation.addAll(
        AgentMcpIntegrationTestSupport.toolExecutionResultMessages(lastMessages));
    expectedConversation.add(secondAiMessage);
    expectedConversation.add(new UserMessage("Ok thanks, anything else?"));
    expectedConversation.add(finalAiMessage);
    assertLastChatRequest(expectedConversation);

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            JobWorkerAgentResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasMetrics(new AgentMetrics(3, new AgentMetrics.TokenUsage(121, 242)))
                .hasResponseMessageText(finalAiMessage.text())
                .hasResponseText(finalAiMessage.text()));

    mcpSupport.assertGatewayCompletions(
        zeebeTest.getProcessInstanceEvent(), AgentMcpIntegrationTestSupport.TOOL_CALLS_BY_GATEWAY);
    assertThat(userFeedbackJobWorkerCounter.get()).isEqualTo(2);
    verify(chatModel, times(3)).chat(any(ChatRequest.class));
  }

  @Test
  void passesTheImageOfAnMcpToolCallResultToTheModel() throws IOException {
    final var aiFinalResponseText = "Here is the image I retrieved from MCP.";
    final var firstAiMessage =
        new AiMessage(
            "I will call the MCP tool to get an image.",
            List.of(
                ToolExecutionRequest.builder()
                    .id("img111")
                    .name("MCP_A_MCP_Client___image")
                    .arguments("{}")
                    .build()));

    mockChatInteractions(
        ChatInteraction.of(chatResponse(FinishReason.TOOL_EXECUTION, 10, 20, firstAiMessage)),
        ChatInteraction.of(
            chatResponse(FinishReason.STOP, 100, 200, new AiMessage(aiFinalResponseText)),
            userSatisfiedFeedback()));

    final var zeebeTest =
        createProcessInstance(
                testProcessWithMcp, e -> e, Map.of("userPrompt", "Get me an image from MCP!"))
            .waitForProcessCompletion();

    final var lastMessages = chatRequestCaptor.getValue().messages();
    assertThat(lastMessages).hasSize(4);
    assertThat(lastMessages.get(3)).isInstanceOf(ToolExecutionResultMessage.class);
    final var toolResult = (ToolExecutionResultMessage) lastMessages.get(3);
    assertThat(toolResult.id()).isEqualTo("img111");
    mcpSupport.assertHoldsImageOfImageTool(toolResult);

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            JobWorkerAgentResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasMetrics(new AgentMetrics(2, new AgentMetrics.TokenUsage(110, 220)))
                .hasResponseMessageText(aiFinalResponseText)
                .hasResponseText(aiFinalResponseText));

    mcpSupport.assertGatewayCompletions(
        zeebeTest.getProcessInstanceEvent(), Map.of("A_MCP_Client", 1));
  }

  private static ChatResponse chatResponse(
      FinishReason finishReason, int inputTokens, int outputTokens, AiMessage aiMessage) {
    return ChatResponse.builder()
        .metadata(
            ChatResponseMetadata.builder()
                .finishReason(finishReason)
                .tokenUsage(new TokenUsage(inputTokens, outputTokens))
                .build())
        .aiMessage(aiMessage)
        .build();
  }
}
