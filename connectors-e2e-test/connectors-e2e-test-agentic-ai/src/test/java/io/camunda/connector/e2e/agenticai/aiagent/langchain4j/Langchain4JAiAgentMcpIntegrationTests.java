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
package io.camunda.connector.e2e.agenticai.aiagent.langchain4j;

import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.HAIKU_TEXT;
import static io.camunda.connector.e2e.agenticai.aiagent.langchain4j.McpTestFixtures.MCP_TOOL_SPECIFICATIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.assertArg;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.mcp.client.McpClientRegistry;
import io.camunda.connector.agenticai.mcp.client.McpRemoteClientRegistry;
import io.camunda.connector.agenticai.mcp.client.McpRemoteClientRegistry.McpRemoteClientIdentifier;
import io.camunda.connector.e2e.agenticai.assertj.AgentResponseAssert;
import io.camunda.connector.test.SlowTest;
import io.camunda.process.test.api.CamundaAssert;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.skyscreamer.jsonassert.JSONAssert;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SlowTest
@TestPropertySource(properties = {"camunda.connector.agenticai.mcp.client.enabled=true"})
public class Langchain4JAiAgentMcpIntegrationTests extends BaseLangchain4JAiAgentTests {

  @Value("classpath:agentic-ai-connectors-mcp.bpmn")
  protected Resource testProcessWithMcp;

  @MockitoBean private McpClientRegistry<McpClient> mcpClientRegistry;
  @MockitoBean private McpRemoteClientRegistry<McpClient> remoteMcpClientRegistry;

  @Mock private McpClient aMcpClient;
  @Mock private McpClient aRemoteMcpClient;
  @Mock private McpClient filesystemMcpClient;

  @Captor private ArgumentCaptor<ToolExecutionRequest> aMcpClientToolExecutionRequestCaptor;
  @Captor private ArgumentCaptor<ToolExecutionRequest> aRemoteMcpClientToolExecutionRequestCaptor;

  @BeforeEach
  void mockMcpClients() {
    when(aMcpClient.key()).thenReturn("a-mcp-client");
    when(aMcpClient.listTools()).thenReturn(MCP_TOOL_SPECIFICATIONS);

    when(aRemoteMcpClient.listTools()).thenReturn(MCP_TOOL_SPECIFICATIONS);

    when(filesystemMcpClient.key()).thenReturn("filesystem");
    when(filesystemMcpClient.listTools()).thenReturn(MCP_TOOL_SPECIFICATIONS);

    // clients configured on the runtime
    doReturn(aMcpClient).when(mcpClientRegistry).getClient("a-mcp-client");
    doReturn(filesystemMcpClient).when(mcpClientRegistry).getClient("filesystem");

    // remote MCP client configured only on the process
    doAnswer(
            i -> {
              final McpRemoteClientIdentifier clientId = i.getArgument(0);
              when(aRemoteMcpClient.key()).thenReturn(clientId.toString());
              return aRemoteMcpClient;
            })
        .when(remoteMcpClientRegistry)
        .getClient(
            assertArg(
                clientId -> assertThat(clientId.elementId()).isEqualTo("A_Remote_MCP_Client")),
            assertArg(
                connection -> {
                  assertThat(connection.sseUrl()).isEqualTo("http://localhost:1234/sse");
                  assertThat(connection.timeout()).isNull();
                }));
  }

  @Test
  void executesMcpToolDiscovery() throws Exception {
    final var zeebeTest =
        testBasicExecutionWithoutFeedbackLoop(
            testProcessWithMcp,
            e -> e,
            HAIKU_TEXT,
            true,
            (agentResponse) ->
                AgentResponseAssert.assertThat(agentResponse)
                    .hasResponseMessageText(HAIKU_TEXT)
                    .hasResponseText(HAIKU_TEXT)
                    .hasNoResponseJson());

    CamundaAssert.assertThat(zeebeTest.getProcessInstanceEvent())
        .hasCompletedElementsInOrder(
            "AI_Agent",

            // MCP tool discovery start
            "A_MCP_Client",
            "A_Remote_MCP_Client",
            "Filesystem_MCP_Flow",
            // MCP tool discovery end

            "AI_Agent",
            "User_Feedback");

    verify(aMcpClient).listTools();
    verify(aRemoteMcpClient).listTools();
    verify(filesystemMcpClient).listTools();
  }

  @Test
  void handlesMcpToolCalls() throws IOException {
    when(aMcpClient.executeTool(aMcpClientToolExecutionRequestCaptor.capture()))
        .thenReturn("A MCP Client result");
    when(aRemoteMcpClient.executeTool(aRemoteMcpClientToolExecutionRequestCaptor.capture()))
        .thenReturn("A Remote MCP Client result");

    final var initialUserPrompt = "Explore some of your MCP tools!";
    final var expectedConversation =
        List.of(
            new SystemMessage(
                "You are a helpful AI assistant. Answer all the questions, but always be nice. Explain your thinking."),
            new UserMessage(initialUserPrompt),
            new AiMessage(
                "The user asked me to call some of my MCP tools. I will call MCP_A_MCP_Client___toolA and MCP_A_Remote_MCP_Client___toolC as they look interesting to me.",
                List.of(
                    ToolExecutionRequest.builder()
                        .id("aaa111")
                        .name("MCP_A_MCP_Client___toolA")
                        .arguments("{\"paramA1\": \"someValue\", \"paramA2\": 3}")
                        .build(),
                    ToolExecutionRequest.builder()
                        .id("ccc222")
                        .name("MCP_A_Remote_MCP_Client___toolC")
                        .arguments("{\"paramC1\": \"someOtherValue\"}")
                        .build())),
            new ToolExecutionResultMessage(
                "aaa111", "MCP_A_MCP_Client___toolA", "A MCP Client result"),
            new ToolExecutionResultMessage(
                "ccc222", "MCP_A_Remote_MCP_Client___toolC", "A Remote MCP Client result"),
            new AiMessage(
                """
                      I called some of my MCP tools and got the following results:
                      MCP_A_MCP_Client___toolA: A MCP Client result
                      MCP_A_Remote_MCP_Client___toolC: A Remote MCP Client result"""),
            new UserMessage("Ok thanks, anything else?"),
            new AiMessage("No."));

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
            userFollowUpFeedback("Ok thanks, anything else?")),
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

    final var zeebeTest =
        createProcessInstance(
                testProcessWithMcp,
                e -> e,
                Map.of("action", "executeAgent", "userPrompt", initialUserPrompt))
            .waitForProcessCompletion();

    assertLastChatRequest(3, expectedConversation);

    final var agentResponse = getAgentResponse(zeebeTest);
    String expectedResponseText = ((AiMessage) expectedConversation.getLast()).text();
    AgentResponseAssert.assertThat(agentResponse)
        .isReady()
        .hasNoToolCalls()
        .hasMetrics(new AgentMetrics(3, new AgentMetrics.TokenUsage(121, 242)))
        .hasResponseMessageText(expectedResponseText)
        .hasResponseText(expectedResponseText);

    assertThat(jobWorkerCounter.get()).isEqualTo(2);

    verify(aMcpClient).listTools();
    verify(aRemoteMcpClient).listTools();
    verify(filesystemMcpClient).listTools();

    verify(aMcpClient)
        .executeTool(
            assertArg(
                toolExecutionRequest -> {
                  assertThat(toolExecutionRequest.name()).isEqualTo("toolA");
                  JSONAssert.assertEquals(
                      "{\"paramA1\": \"someValue\", \"paramA2\": 3}",
                      toolExecutionRequest.arguments(),
                      true);
                }));
    verify(aRemoteMcpClient)
        .executeTool(
            assertArg(
                toolExecutionRequest -> {
                  assertThat(toolExecutionRequest.name()).isEqualTo("toolC");
                  JSONAssert.assertEquals(
                      "{\"paramC1\": \"someOtherValue\"}", toolExecutionRequest.arguments(), true);
                }));
  }

  @Override
  protected void assertToolSpecifications(ChatRequest chatRequest) {
    assertThat(chatRequest.toolSpecifications())
        .extracting(ToolSpecification::name)
        .containsExactlyInAnyOrder(
            "GetDateAndTime",
            "SuperfluxProduct",
            "Search_The_Web",
            "Download_A_File",
            "MCP_A_MCP_Client___toolA",
            "MCP_A_MCP_Client___toolC",
            "MCP_A_Remote_MCP_Client___toolA",
            "MCP_A_Remote_MCP_Client___toolC",
            "MCP_Filesystem_MCP_Flow___toolA",
            "MCP_Filesystem_MCP_Flow___toolB",
            "MCP_Filesystem_MCP_Flow___toolC");

    assertThat(chatRequest.toolSpecifications())
        .filteredOn(
            toolSpecification -> toolSpecification.name().equals("MCP_A_MCP_Client___toolA"))
        .hasSize(1)
        .first()
        .usingRecursiveComparison()
        .ignoringFields("name")
        .isEqualTo(MCP_TOOL_SPECIFICATIONS.get(0));
    assertThat(chatRequest.toolSpecifications())
        .filteredOn(
            toolSpecification -> toolSpecification.name().equals("MCP_Filesystem_MCP_Flow___toolB"))
        .hasSize(1)
        .first()
        .usingRecursiveComparison()
        .ignoringFields("name")
        .isEqualTo(MCP_TOOL_SPECIFICATIONS.get(1));
    assertThat(chatRequest.toolSpecifications())
        .filteredOn(
            toolSpecification -> toolSpecification.name().equals("MCP_A_Remote_MCP_Client___toolC"))
        .hasSize(1)
        .first()
        .usingRecursiveComparison()
        .ignoringFields("name")
        .isEqualTo(MCP_TOOL_SPECIFICATIONS.get(2));
  }
}
