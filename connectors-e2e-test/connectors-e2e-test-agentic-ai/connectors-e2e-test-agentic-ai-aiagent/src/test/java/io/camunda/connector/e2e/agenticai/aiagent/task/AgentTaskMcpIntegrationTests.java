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
package io.camunda.connector.e2e.agenticai.aiagent.task;

import static io.camunda.connector.e2e.agenticai.aiagent.AgentToolSpecifications.EXPECTED_MCP_TOOL_SPECIFICATIONS;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.assertHandleOf;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.parseDocumentHandle;
import static io.camunda.connector.e2e.agenticai.aiagent.fixtures.AgentTestFixtures.HAIKU_TEXT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import io.camunda.connector.agenticai.aiagent.agentinstance.AgentInstanceClient;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.connector.e2e.agenticai.McpTestServerConfiguration;
import io.camunda.connector.e2e.agenticai.McpTestServerConfiguration.McpTestServers;
import io.camunda.connector.e2e.agenticai.aiagent.common.AgentMcpIntegrationTestSupport;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.ToolCall;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.Turn;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsRecordedConversation;
import io.camunda.connector.e2e.agenticai.assertj.AgentResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SlowTest
@TestPropertySource(properties = {"camunda.connector.agenticai.mcp.client.enabled=true"})
@Import(McpTestServerConfiguration.class)
class AgentTaskMcpIntegrationTests extends BaseAgentTaskTest {

  @Value("classpath:agentic-ai-connectors-mcp.bpmn")
  protected Resource testProcessWithMcp;

  @Autowired private McpTestServers mcpTestServers;
  @MockitoSpyBean private AgentInstanceClient agentInstanceClient;

  private AgentMcpIntegrationTestSupport mcpSupport;

  @BeforeEach
  void setUpMcpSupport() {
    mcpSupport = new AgentMcpIntegrationTestSupport(mcpTestServers, objectMapper);
  }

  @Override
  protected ZeebeTest awaitProcessCompletion(ZeebeTest zeebeTest) {
    return awaitProcessCompletion(zeebeTest, AgentMcpIntegrationTestSupport.STDIO_START_TIMEOUT);
  }

  @Override
  protected List<ToolDefinition> expectedTools() {
    return EXPECTED_MCP_TOOL_SPECIFICATIONS;
  }

  @Override
  protected BpmnModelInstance customizeModel(BpmnModelInstance model) {
    return mcpSupport.setRemoteClientUrls(model);
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
            agentResponse -> {
              AgentResponseAssert.assertThat(agentResponse)
                  .hasResponseMessageText(HAIKU_TEXT)
                  .hasResponseText(HAIKU_TEXT)
                  .hasNoResponseJson();
              assertThat(agentResponse.context().toolDefinitions())
                  .containsExactlyInAnyOrderElementsOf(EXPECTED_MCP_TOOL_SPECIFICATIONS);
            });

    CamundaAssert.assertThat(zeebeTest.getProcessInstanceEvent())
        .hasCompletedElementsInOrder("AI_Agent", "AI_Agent", "User_Feedback");

    for (final var gateway : AgentMcpIntegrationTestSupport.GATEWAYS) {
      CamundaAssert.assertThat(zeebeTest.getProcessInstanceEvent())
          .hasCompletedElementsInOrder("AI_Agent", gateway, "AI_Agent");
    }

    mcpSupport.assertGatewayCompletions(zeebeTest.getProcessInstanceEvent(), Map.of());
    verify(agentInstanceClient).applyToolDiscoveryStart(any(), any());
  }

  @Test
  void handlesMcpToolCalls() throws IOException {
    final var initialUserPrompt = "Explore some of your MCP tools!";
    final var firstAiText = "The user asked me to call some of my MCP tools. I will call them all.";
    final var secondAiText = "I called some of my MCP tools.";
    final var finalAiText = "No.";

    OpenAiCompletionsChatModelStubs.stubConversation(
        Turn.toolCalls(
            firstAiText,
            10,
            20,
            ToolCall.of("stdio111", "MCP_A_MCP_Client___echoMeta", "{}"),
            ToolCall.of("http222", "MCP_A_HTTP_MCP_Client___add", "{\"a\": 5, \"b\": 3}"),
            ToolCall.of("remote333", "MCP_A_HTTP_Remote_MCP_Client___echoMeta", "{}"),
            ToolCall.of("remote444", "MCP_A_HTTP_Remote_MCP_Client___echoHeaders", "{}"),
            ToolCall.of("remote555", "MCP_A_SSE_Remote_MCP_Client___echoHeaders", "{}"),
            ToolCall.of(
                "sse666", "MCP_Filesystem_MCP_Flow___uppercase", "{\"message\": \"hello\"}")),
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
            "MCP_A_MCP_Client___echoMeta",
            "MCP_A_HTTP_MCP_Client___add",
            "MCP_A_HTTP_Remote_MCP_Client___echoMeta",
            "MCP_A_HTTP_Remote_MCP_Client___echoHeaders",
            "MCP_A_SSE_Remote_MCP_Client___echoHeaders",
            "MCP_Filesystem_MCP_Flow___uppercase"),
        ExpectedMessage.toolCallResult("stdio111", AgentMcpIntegrationTestSupport.ECHOED_META),
        ExpectedMessage.toolCallResult("http222", "8"),
        ExpectedMessage.toolCallResult("remote333", "{}"),
        ExpectedMessage.toolCallResult("remote444", null),
        ExpectedMessage.toolCallResult("remote555", null),
        ExpectedMessage.toolCallResult("sse666", "HELLO"),
        ExpectedMessage.assistant(secondAiText),
        ExpectedMessage.user("Ok thanks, anything else?"));
    mcpSupport.assertEchoedHeaders(recorded.lastRequest(), "remote444", "HTTP");
    mcpSupport.assertEchoedHeaders(recorded.lastRequest(), "remote555", "SSE");

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasNoToolCalls()
                .hasMetrics(new AgentMetrics(3, new AgentMetrics.TokenUsage(121, 242), 6))
                .hasResponseMessageText(finalAiText)
                .hasResponseText(finalAiText));

    mcpSupport.assertGatewayCompletions(
        zeebeTest.getProcessInstanceEvent(),
        Map.of(
            "A_MCP_Client", 1,
            "A_HTTP_MCP_Client", 1,
            "A_HTTP_Remote_MCP_Client", 2,
            "A_SSE_Remote_MCP_Client", 1,
            "Filesystem_MCP_Flow", 1));
    assertThat(userFeedbackJobWorkerCounter.get()).isEqualTo(2);
  }

  @Test
  void extractsDocumentsFromMcpImageToolCallResult() throws IOException {
    final var initialUserPrompt = "Get me an image from MCP!";
    final var aiFinalResponseText = "Here is the image I retrieved from MCP.";

    OpenAiCompletionsChatModelStubs.stubConversation(
        Turn.toolCalls(
            "I will call the MCP tool to get an image.",
            10,
            20,
            ToolCall.of("img111", "MCP_A_MCP_Client___image", "{}")),
        Turn.text(aiFinalResponseText, 100, 200));

    enqueueUserFeedback(userSatisfiedFeedback());

    final var zeebeTest =
        awaitProcessCompletion(
            createProcessInstance(
                testProcessWithMcp, e -> e, Map.of("userPrompt", initialUserPrompt)));

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(2);

    final var lastMessages = recorded.lastRequest().messages();
    assertThat(lastMessages).hasSize(4);

    assertThat(lastMessages.get(0).role()).isEqualTo("system");
    assertThat(lastMessages.get(1).role()).isEqualTo("user");
    assertThat(lastMessages.get(2).role()).isEqualTo("assistant");

    final var toolResultText = lastMessages.get(3).content();
    final var documentHandle = parseDocumentHandle(toolResultText);
    assertThat(lastMessages.get(3).role()).isEqualTo("tool");
    assertThat(lastMessages.get(3).toolCallId()).isEqualTo("img111");

    assertAgentResponse(
        zeebeTest,
        agentResponse -> {
          AgentResponseAssert.assertThat(agentResponse)
              .isReady()
              .hasNoToolCalls()
              .hasMetrics(new AgentMetrics(2, new AgentMetrics.TokenUsage(110, 220), 1))
              .hasResponseMessageText(aiFinalResponseText)
              .hasResponseText(aiFinalResponseText)
              .hasOnlyAgentDocumentSatisfying(
                  document -> assertHandleOf(document, documentHandle, null, "image/png", null));
        });

    mcpSupport.assertGatewayCompletions(
        zeebeTest.getProcessInstanceEvent(), Map.of("A_MCP_Client", 1));
  }
}
