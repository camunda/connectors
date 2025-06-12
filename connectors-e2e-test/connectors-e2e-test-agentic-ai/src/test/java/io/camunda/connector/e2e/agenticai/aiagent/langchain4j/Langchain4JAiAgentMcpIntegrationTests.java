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
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.assertArg;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.model.chat.request.ChatRequest;
import io.camunda.connector.agenticai.mcp.client.McpClientFactory;
import io.camunda.connector.agenticai.mcp.client.McpClientRegistry;
import io.camunda.connector.e2e.agenticai.assertj.AgentResponseAssert;
import io.camunda.connector.test.SlowTest;
import io.camunda.process.test.api.CamundaAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
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
  @MockitoBean private McpClientFactory<McpClient> mcpClientFactory;

  @Mock private McpClient aMcpClient;
  @Mock private McpClient aRemoteMcpClient;
  @Mock private McpClient filesystemMcpClient;

  @BeforeEach
  void mockMcpClients() {
    when(aMcpClient.listTools()).thenReturn(MCP_TOOL_SPECIFICATIONS);
    when(aRemoteMcpClient.listTools()).thenReturn(MCP_TOOL_SPECIFICATIONS);
    when(filesystemMcpClient.listTools()).thenReturn(MCP_TOOL_SPECIFICATIONS);

    // clients configured on the runtime
    doReturn(aMcpClient).when(mcpClientRegistry).getClient("a-mcp-client");
    doReturn(filesystemMcpClient).when(mcpClientRegistry).getClient("filesystem");

    // remote MCP client configured only on the process
    doReturn(aRemoteMcpClient)
        .when(mcpClientFactory)
        .createClient(
            assertArg(clientId -> assertThat(clientId).startsWith("A_Remote_MCP_Client_")),
            assertArg(
                config -> {
                  assertThat(config.enabled()).isTrue();
                  assertThat(config.stio()).isNull();
                  assertThat(config.http())
                      .isNotNull()
                      .satisfies(
                          httpConfig -> {
                            assertThat(httpConfig.sseUrl()).isEqualTo("http://localhost:1234/sse");
                            assertThat(httpConfig.headers())
                                .containsExactly(entry("Authorization", "dummy"));
                          });
                  assertThat(config.initializationTimeout()).isNull();
                  assertThat(config.toolExecutionTimeout()).isNull();
                  assertThat(config.reconnectInterval()).isNull();
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
