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
import static io.camunda.connector.e2e.agenticai.mcp.McpSdkToolSpecifications.MCP_TOOL_SPECIFICATIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.assertArg;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.mcp.client.McpClientRegistry;
import io.camunda.connector.agenticai.mcp.client.McpRemoteClientRegistry;
import io.camunda.connector.agenticai.mcp.client.McpRemoteClientRegistry.McpRemoteClientIdentifier;
import io.camunda.connector.agenticai.mcp.client.framework.mcpsdk.rpc.McpSdkMcpClientDelegate;
import io.camunda.connector.agenticai.mcp.client.model.McpRemoteClientTransportConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpRemoteClientTransportConfiguration.SseHttpMcpRemoteClientTransportConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpRemoteClientTransportConfiguration.StreamableHttpMcpRemoteClientTransportConfiguration;
import io.camunda.connector.e2e.agenticai.assertj.JobWorkerAgentResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import io.camunda.process.test.api.CamundaAssert;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SlowTest
@TestPropertySource(properties = {"camunda.connector.agenticai.mcp.client.enabled=true"})
public class L4JAiAgentJobWorkerMcpIntegrationTests extends BaseL4JAiAgentJobWorkerTest {

  public static final String MCP_CLIENT_ID = "a-mcp-client";

  @Value("classpath:agentic-ai-ahsp-connectors-mcp.bpmn")
  protected Resource testProcessWithMcp;

  @MockitoBean private McpClientRegistry mcpClientRegistry;
  @MockitoBean private McpRemoteClientRegistry remoteMcpClientRegistry;

  @Mock private McpSyncClient aMcpClient;
  @Mock private McpSyncClient aHttpRemoteMcpClient;
  @Mock private McpSyncClient aSseRemoteMcpClient;
  @Mock private McpSyncClient filesystemMcpClient;

  @Captor private ArgumentCaptor<McpSchema.CallToolRequest> aMcpClientToolExecutionRequestCaptor;

  @Captor
  private ArgumentCaptor<McpSchema.CallToolRequest> aHttpRemoteMcpClientToolExecutionRequestCaptor;

  @Captor
  private ArgumentCaptor<McpSchema.CallToolRequest> aSseRemoteMcpClientToolExecutionRequestCaptor;

  private final Map<String, McpRemoteClientTransportConfiguration> requestedRemoteMcpClients =
      new LinkedHashMap<>();

  @BeforeEach
  void mockMcpClients() {
    when(aMcpClient.listTools()).thenReturn(MCP_TOOL_SPECIFICATIONS);

    when(aHttpRemoteMcpClient.listTools()).thenReturn(MCP_TOOL_SPECIFICATIONS);
    when(aSseRemoteMcpClient.listTools()).thenReturn(MCP_TOOL_SPECIFICATIONS);

    when(filesystemMcpClient.listTools()).thenReturn(MCP_TOOL_SPECIFICATIONS);

    // clients configured on the runtime
    doReturn(new McpSdkMcpClientDelegate(MCP_CLIENT_ID, aMcpClient, objectMapper))
        .when(mcpClientRegistry)
        .getClient(MCP_CLIENT_ID);
    doReturn(new McpSdkMcpClientDelegate(MCP_CLIENT_ID, filesystemMcpClient, objectMapper))
        .when(mcpClientRegistry)
        .getClient("filesystem");

    // remote MCP clients configured only on the process
    requestedRemoteMcpClients.clear();
    doAnswer(
            i -> {
              final McpRemoteClientIdentifier clientId = i.getArgument(0);
              final McpRemoteClientTransportConfiguration transport = i.getArgument(1);
              requestedRemoteMcpClients.put(clientId.elementId(), transport);

              var internalClient =
                  switch (clientId.elementId()) {
                    case "A_HTTP_Remote_MCP_Client" -> aHttpRemoteMcpClient;
                    case "A_SSE_Remote_MCP_Client" -> aSseRemoteMcpClient;
                    default ->
                        throw new IllegalArgumentException(
                            "Unexpected remote MCP client ID: " + clientId.elementId());
                  };

              return new McpSdkMcpClientDelegate(MCP_CLIENT_ID, internalClient, objectMapper);
            })
        .when(remoteMcpClientRegistry)
        .getClient(
            any(McpRemoteClientIdentifier.class),
            any(McpRemoteClientTransportConfiguration.class),
            anyBoolean());
  }

  @Override
  protected List<ToolSpecification> expectedToolSpecifications() {
    return EXPECTED_MCP_TOOL_SPECIFICATIONS;
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
            (agentResponse) ->
                JobWorkerAgentResponseAssert.assertThat(agentResponse)
                    .hasResponseMessageText(HAIKU_TEXT)
                    .hasResponseText(HAIKU_TEXT)
                    .hasNoResponseJson());

    CamundaAssert.assertThat(zeebeTest.getProcessInstanceEvent())
        .hasCompletedElements(
            // entering the AI Agent
            "AI_Agent",

            // MCP tool discovery start
            "A_MCP_Client",
            "A_HTTP_Remote_MCP_Client",
            "A_SSE_Remote_MCP_Client",
            "Filesystem_MCP_Flow",
            // MCP tool discovery end

            "User_Feedback");

    verify(aMcpClient).listTools();
    verify(aHttpRemoteMcpClient).listTools();
    verify(aSseRemoteMcpClient).listTools();
    verify(filesystemMcpClient).listTools();

    verify(chatModel, times(1)).chat(any(ChatRequest.class));

    assertThat(requestedRemoteMcpClients)
        .hasSize(2)
        .hasEntrySatisfying(
            "A_HTTP_Remote_MCP_Client",
            transport ->
                assertThat(transport)
                    .isInstanceOfSatisfying(
                        StreamableHttpMcpRemoteClientTransportConfiguration.class,
                        httpTransport -> {
                          assertThat(httpTransport.http().url())
                              .isEqualTo("http://localhost:1234/mcp");
                          assertThat(httpTransport.http().headers())
                              .containsExactly(entry("X-Dummy", "HTTP"));
                          assertThat(httpTransport.http().timeout()).isNull();
                        }))
        .hasEntrySatisfying(
            "A_SSE_Remote_MCP_Client",
            transport ->
                assertThat(transport)
                    .isInstanceOfSatisfying(
                        SseHttpMcpRemoteClientTransportConfiguration.class,
                        sseTransport -> {
                          assertThat(sseTransport.sse().url())
                              .isEqualTo("http://localhost:1234/sse");
                          assertThat(sseTransport.sse().headers())
                              .containsExactly(entry("X-Dummy", "SSE"));
                          assertThat(sseTransport.sse().timeout()).isNull();
                        }));
  }

  @Test
  void handlesMcpToolCalls() throws IOException {
    when(aMcpClient.callTool(aMcpClientToolExecutionRequestCaptor.capture()))
        .thenReturn(toolExecutionResult("A MCP Client result"));
    when(aHttpRemoteMcpClient.callTool(aHttpRemoteMcpClientToolExecutionRequestCaptor.capture()))
        .thenReturn(toolExecutionResult("A HTTP Remote MCP Client result"));
    when(aSseRemoteMcpClient.callTool(aSseRemoteMcpClientToolExecutionRequestCaptor.capture()))
        .thenReturn(toolExecutionResult("A SSE Remote MCP Client result"));

    final var initialUserPrompt = "Explore some of your MCP tools!";
    final var expectedConversation =
        List.of(
            new SystemMessage(
                "You are a helpful AI assistant. Answer all the questions, but always be nice. Explain your thinking."),
            new UserMessage(initialUserPrompt),
            new AiMessage(
                "The user asked me to call some of my MCP tools. I will call MCP_A_MCP_Client___toolA, MCP_A_HTTP_Remote_MCP_Client___toolC, and MCP_A_SSE_Remote_MCP_Client___toolA as they look interesting to me.",
                List.of(
                    ToolExecutionRequest.builder()
                        .id("aaa111")
                        .name("MCP_A_MCP_Client___toolA")
                        .arguments("{\"paramA1\": \"someValue\", \"paramA2\": 3}")
                        .build(),
                    ToolExecutionRequest.builder()
                        .id("ccc222")
                        .name("MCP_A_HTTP_Remote_MCP_Client___toolC")
                        .arguments("{\"paramC1\": \"someOtherValue\"}")
                        .build(),
                    ToolExecutionRequest.builder()
                        .id("aaa333")
                        .name("MCP_A_SSE_Remote_MCP_Client___toolA")
                        .arguments("{\"paramA1\": \"someValue2\", \"paramA2\": 6}")
                        .build())),
            new ToolExecutionResultMessage(
                "aaa111", "MCP_A_MCP_Client___toolA", "A MCP Client result"),
            new ToolExecutionResultMessage(
                "ccc222",
                "MCP_A_HTTP_Remote_MCP_Client___toolC",
                "A HTTP Remote MCP Client result"),
            new ToolExecutionResultMessage(
                "aaa333", "MCP_A_SSE_Remote_MCP_Client___toolA", "A SSE Remote MCP Client result"),
            new AiMessage(
                """
                      I called some of my MCP tools and got the following results:
                      MCP_A_MCP_Client___toolA: A MCP Client result
                      MCP_A_HTTP_Remote_MCP_Client___toolC: A HTTP Remote MCP Client result
                      MCP_A_SSE_Remote_MCP_Client___toolA: A SSE Remote MCP Client result"""),
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
                .aiMessage((AiMessage) expectedConversation.get(6))
                .build(),
            userFollowUpFeedback("Ok thanks, anything else?")),
        ChatInteraction.of(
            ChatResponse.builder()
                .metadata(
                    ChatResponseMetadata.builder()
                        .finishReason(FinishReason.STOP)
                        .tokenUsage(new TokenUsage(11, 22))
                        .build())
                .aiMessage((AiMessage) expectedConversation.get(8))
                .build(),
            userSatisfiedFeedback()));

    final var zeebeTest =
        createProcessInstance(testProcessWithMcp, e -> e, Map.of("userPrompt", initialUserPrompt))
            .waitForProcessCompletion();

    assertLastChatRequest(expectedConversation);

    String expectedResponseText = ((AiMessage) expectedConversation.getLast()).text();
    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            JobWorkerAgentResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasMetrics(new AgentMetrics(3, new AgentMetrics.TokenUsage(121, 242)))
                .hasResponseMessageText(expectedResponseText)
                .hasResponseText(expectedResponseText));

    assertThat(userFeedbackJobWorkerCounter.get()).isEqualTo(2);

    verify(aMcpClient).listTools();
    verify(aHttpRemoteMcpClient).listTools();
    verify(aSseRemoteMcpClient).listTools();
    verify(filesystemMcpClient).listTools();

    verify(aMcpClient)
        .callTool(
            assertArg(
                toolExecutionRequest -> {
                  assertThat(toolExecutionRequest.name()).isEqualTo("toolA");
                  assertThat(toolExecutionRequest.arguments())
                      .containsEntry("paramA1", "someValue")
                      .containsEntry("paramA2", 3);
                }));
    verify(aHttpRemoteMcpClient)
        .callTool(
            assertArg(
                toolExecutionRequest -> {
                  assertThat(toolExecutionRequest.name()).isEqualTo("toolC");
                  assertThat(toolExecutionRequest.arguments())
                      .containsEntry("paramC1", "someOtherValue");
                }));
    verify(aSseRemoteMcpClient)
        .callTool(
            assertArg(
                toolExecutionRequest -> {
                  assertThat(toolExecutionRequest.name()).isEqualTo("toolA");
                  assertThat(toolExecutionRequest.arguments())
                      .containsEntry("paramA1", "someValue2")
                      .containsEntry("paramA2", 6);
                }));

    verify(chatModel, times(3)).chat(any(ChatRequest.class));
  }
}
