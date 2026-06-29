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

import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.HAIKU_TEXT;
import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentToolSpecifications.EXPECTED_MCP_TOOL_SPECIFICATIONS;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.assertExtractedDocumentsUserMessage;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.parseDocumentReference;
import static io.camunda.connector.e2e.agenticai.mcp.McpSdkToolSpecifications.MCP_TOOL_SPECIFICATIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.assertArg;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.mcp.client.McpClientRegistry;
import io.camunda.connector.agenticai.mcp.client.McpRemoteClientRegistry;
import io.camunda.connector.agenticai.mcp.client.McpRemoteClientRegistry.McpRemoteClientIdentifier;
import io.camunda.connector.agenticai.mcp.client.framework.mcpsdk.rpc.McpSdkMcpClientDelegate;
import io.camunda.connector.agenticai.mcp.client.model.McpRemoteClientTransportConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpRemoteClientTransportConfiguration.SseHttpMcpRemoteClientTransportConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpRemoteClientTransportConfiguration.StreamableHttpMcpRemoteClientTransportConfiguration;
import io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.ExtractedDocument;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.ToolCall;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.Turn;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsRecordedConversation;
import io.camunda.connector.e2e.agenticai.assertj.JobWorkerAgentResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import io.camunda.process.test.api.CamundaAssert;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SlowTest
@ExtendWith(MockitoExtension.class)
@TestPropertySource(properties = {"camunda.connector.agenticai.mcp.client.enabled=true"})
public class AiAgentJobWorkerMcpIntegrationTests extends BaseAiAgentJobWorkerTest {

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

    doReturn(new McpSdkMcpClientDelegate(MCP_CLIENT_ID, aMcpClient, objectMapper))
        .when(mcpClientRegistry)
        .getClient(MCP_CLIENT_ID);
    doReturn(new McpSdkMcpClientDelegate(MCP_CLIENT_ID, filesystemMcpClient, objectMapper))
        .when(mcpClientRegistry)
        .getClient("filesystem");

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
  protected List<ToolDefinition> expectedTools() {
    return EXPECTED_MCP_TOOL_SPECIFICATIONS;
  }

  @Test
  void executesMcpToolDiscovery() throws Exception {
    final var zeebeTest =
        testBasicExecutionWithoutFeedbackLoop(
            testProcessWithMcp,
            e -> e,
            Map.of(),
            HAIKU_TEXT,
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

    assertThat(OpenAiCompletionsRecordedConversation.recorded().modelCallCount()).isEqualTo(1);

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
        .thenReturn(mcpCallToolResult("A MCP Client result"));
    when(aHttpRemoteMcpClient.callTool(aHttpRemoteMcpClientToolExecutionRequestCaptor.capture()))
        .thenReturn(mcpCallToolResult("A HTTP Remote MCP Client result"));
    when(aSseRemoteMcpClient.callTool(aSseRemoteMcpClientToolExecutionRequestCaptor.capture()))
        .thenReturn(mcpCallToolResult("A SSE Remote MCP Client result"));

    final var initialUserPrompt = "Explore some of your MCP tools!";
    final var firstAiText =
        "The user asked me to call some of my MCP tools. I will call MCP_A_MCP_Client___toolA, MCP_A_HTTP_Remote_MCP_Client___toolC, and MCP_A_SSE_Remote_MCP_Client___toolA as they look interesting to me.";
    final var secondAiText =
        """
        I called some of my MCP tools and got the following results:
        MCP_A_MCP_Client___toolA: A MCP Client result
        MCP_A_HTTP_Remote_MCP_Client___toolC: A HTTP Remote MCP Client result
        MCP_A_SSE_Remote_MCP_Client___toolA: A SSE Remote MCP Client result""";
    final var finalAiText = "No.";

    OpenAiCompletionsChatModelStubs.stubConversation(
        Turn.toolCalls(
            firstAiText,
            10,
            20,
            ToolCall.of(
                "aaa111",
                "MCP_A_MCP_Client___toolA",
                "{\"paramA1\": \"someValue\", \"paramA2\": 3}"),
            ToolCall.of(
                "ccc222",
                "MCP_A_HTTP_Remote_MCP_Client___toolC",
                "{\"paramC1\": \"someOtherValue\"}"),
            ToolCall.of(
                "aaa333",
                "MCP_A_SSE_Remote_MCP_Client___toolA",
                "{\"paramA1\": \"someValue2\", \"paramA2\": 6}")),
        Turn.text(secondAiText, 100, 200),
        Turn.text(finalAiText, 11, 22));

    enqueueUserFeedback(userFollowUpFeedback("Ok thanks, anything else?"), userSatisfiedFeedback());

    final var zeebeTest =
        awaitProcessCompletion(
            createProcessInstance(
                testProcessWithMcp, e -> e, Map.of("userPrompt", initialUserPrompt)));

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(3);

    assertConversationMessages(
        recorded.lastRequest(),
        ExpectedMessage.system(SYSTEM_PROMPT),
        ExpectedMessage.user(initialUserPrompt),
        ExpectedMessage.assistantWithToolCalls(
            firstAiText,
            "MCP_A_MCP_Client___toolA",
            "MCP_A_HTTP_Remote_MCP_Client___toolC",
            "MCP_A_SSE_Remote_MCP_Client___toolA"),
        ExpectedMessage.toolCallResult("aaa111", "A MCP Client result"),
        ExpectedMessage.toolCallResult("ccc222", "A HTTP Remote MCP Client result"),
        ExpectedMessage.toolCallResult("aaa333", "A SSE Remote MCP Client result"),
        ExpectedMessage.assistant(secondAiText),
        ExpectedMessage.user("Ok thanks, anything else?"));

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            JobWorkerAgentResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasMetrics(new AgentMetrics(3, new AgentMetrics.TokenUsage(121, 242), 3))
                .hasResponseMessageText(finalAiText)
                .hasResponseText(finalAiText));

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
                      .containsExactly(entry("paramA1", "someValue"), entry("paramA2", 3));
                }));
    verify(aHttpRemoteMcpClient)
        .callTool(
            assertArg(
                toolExecutionRequest -> {
                  assertThat(toolExecutionRequest.name()).isEqualTo("toolC");
                  assertThat(toolExecutionRequest.arguments())
                      .containsExactly(entry("paramC1", "someOtherValue"));
                }));
    verify(aSseRemoteMcpClient)
        .callTool(
            assertArg(
                toolExecutionRequest -> {
                  assertThat(toolExecutionRequest.name()).isEqualTo("toolA");
                  assertThat(toolExecutionRequest.arguments())
                      .containsExactly(entry("paramA1", "someValue2"), entry("paramA2", 6));
                }));
  }

  @Test
  void extractsDocumentsFromMcpImageToolCallResult() throws IOException {
    final var imageBytes = "fake-png-image-data".getBytes();
    final var imageBase64 = Base64.getEncoder().encodeToString(imageBytes);

    when(aMcpClient.callTool(aMcpClientToolExecutionRequestCaptor.capture()))
        .thenReturn(mcpCallToolResultWithImage(imageBase64, "image/png"));

    final var initialUserPrompt = "Get me an image from MCP!";
    final var aiFinalResponseText = "Here is the image I retrieved from MCP.";

    OpenAiCompletionsChatModelStubs.stubConversation(
        Turn.toolCalls(
            "I will call the MCP tool to get an image.",
            10,
            20,
            ToolCall.of(
                "img111",
                "MCP_A_MCP_Client___toolA",
                "{\"paramA1\": \"getImage\", \"paramA2\": 1}")),
        Turn.text(aiFinalResponseText, 100, 200));

    enqueueUserFeedback(userSatisfiedFeedback());

    final var zeebeTest =
        awaitProcessCompletion(
            createProcessInstance(
                testProcessWithMcp, e -> e, Map.of("userPrompt", initialUserPrompt)));

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(2);

    final var lastMessages = recorded.lastRequest().messages();
    assertThat(lastMessages).hasSize(5);

    assertThat(lastMessages.get(0).role()).isEqualTo("system");
    assertThat(lastMessages.get(1).role()).isEqualTo("user");
    assertThat(lastMessages.get(2).role()).isEqualTo("assistant");

    final var toolResultText = lastMessages.get(3).content();
    final var documentReference = parseDocumentReference(toolResultText);
    assertThat(lastMessages.get(3).role()).isEqualTo("tool");
    assertThat(lastMessages.get(3).toolCallId()).isEqualTo("img111");
    assertThat(documentReference.metadata().contentType()).isEqualTo("image/png");

    assertExtractedDocumentsUserMessage(
        lastMessages.get(4),
        ExtractedDocument.forToolCall(
            "img111",
            "MCP_A_MCP_Client___toolA",
            documentReference,
            block -> {
              assertThat(block.path("type").asText()).isEqualTo("image_url");
              final var url = block.path("image_url").path("url").asText();
              assertThat(url).startsWith("data:image/png;base64,");
              assertThat(url.substring(url.indexOf(',') + 1)).isEqualTo(imageBase64);
            }));

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            JobWorkerAgentResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasMetrics(new AgentMetrics(2, new AgentMetrics.TokenUsage(110, 220), 1))
                .hasResponseMessageText(aiFinalResponseText)
                .hasResponseText(aiFinalResponseText));
  }

  protected McpSchema.CallToolResult mcpCallToolResult(String resultText) {
    return McpSchema.CallToolResult.builder()
        .addContent(new McpSchema.TextContent(resultText))
        .build();
  }

  protected McpSchema.CallToolResult mcpCallToolResultWithImage(
      String base64Data, String mimeType) {
    return McpSchema.CallToolResult.builder()
        .addContent(McpSchema.ImageContent.builder(base64Data, mimeType).build())
        .build();
  }
}
