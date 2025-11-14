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
package io.camunda.connector.e2e.agenticai.mcp;

import static io.camunda.connector.e2e.agenticai.aiagent.langchain4j.Langchain4JAiAgentToolSpecifications.MCP_TOOL_SPECIFICATIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.assertArg;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolExecutionResult;
import io.camunda.connector.agenticai.mcp.client.McpClientRegistry;
import io.camunda.connector.agenticai.mcp.client.McpRemoteClientRegistry;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListToolsResult;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.connector.e2e.agenticai.BaseAgenticAiTest;
import io.camunda.connector.test.utils.annotation.SlowTest;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
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
public class McpStandaloneTests extends BaseAgenticAiTest {

  @Value("classpath:mcp-connectors-standalone.bpmn")
  protected Resource testProcess;

  @MockitoBean private McpClientRegistry<McpClient> mcpClientRegistry;
  @MockitoBean private McpRemoteClientRegistry<McpClient> remoteMcpClientRegistry;

  @Mock private McpClient aMcpClient;
  @Mock private McpClient aRemoteMcpClient;

  @Captor private ArgumentCaptor<ToolExecutionRequest> aMcpClientToolExecutionRequestCaptor;

  @Captor private ArgumentCaptor<ToolExecutionRequest> aRemoteMcpClientToolExecutionRequestCaptor;

  @BeforeEach
  void mockMcpClients() {
    when(aMcpClient.key()).thenReturn("a-mcp-client");
    when(aMcpClient.listTools()).thenReturn(MCP_TOOL_SPECIFICATIONS);
    when(aMcpClient.executeTool(aMcpClientToolExecutionRequestCaptor.capture()))
        .thenReturn(toolExecutionResult("A MCP Client result"));
    doReturn(aMcpClient).when(mcpClientRegistry).getClient(any());

    when(aRemoteMcpClient.key()).thenReturn("a-remote-mcp-client");
    when(aRemoteMcpClient.listTools()).thenReturn(MCP_TOOL_SPECIFICATIONS);
    when(aRemoteMcpClient.executeTool(aRemoteMcpClientToolExecutionRequestCaptor.capture()))
        .thenReturn(toolExecutionResult("A Remote MCP Client result"));
    doReturn(aRemoteMcpClient).when(remoteMcpClientRegistry).getClient(any(), any());
  }

  @Test
  void executesStandaloneMcpClients() throws IOException {
    BpmnModelInstance bpmnModel = Bpmn.readModelFromStream(testProcess.getInputStream());
    ZeebeTest zeebeTest = createProcessInstance(bpmnModel, Map.of()).waitForProcessCompletion();

    CamundaAssert.assertThat(zeebeTest.getProcessInstanceEvent())
        .hasVariableSatisfies(
            "clientListToolsResult",
            McpClientListToolsResult.class,
            listToolsResult ->
                assertThat(listToolsResult.toolDefinitions())
                    .hasSize(2)
                    .extracting(ToolDefinition::name)
                    .containsExactly("toolA", "toolC"))
        .hasVariable(
            "clientCallToolResult",
            Map.of(
                "name",
                "toolA",
                "content",
                List.of(Map.of("type", "text", "text", "A MCP Client result")),
                "isError",
                false))
        .hasVariableSatisfies(
            "remoteClientListToolsResult",
            McpClientListToolsResult.class,
            listToolsResult ->
                assertThat(listToolsResult.toolDefinitions())
                    .hasSize(2)
                    .extracting(ToolDefinition::name)
                    .containsExactly("toolB", "toolC"))
        .hasVariable(
            "remoteClientCallToolResult",
            Map.of(
                "name",
                "toolC",
                "content",
                List.of(Map.of("type", "text", "text", "A Remote MCP Client result")),
                "isError",
                false));

    verify(aMcpClient).listTools();
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

    verify(aRemoteMcpClient).listTools();
    verify(aRemoteMcpClient)
        .executeTool(
            assertArg(
                toolExecutionRequest -> {
                  assertThat(toolExecutionRequest.name()).isEqualTo("toolC");
                  JSONAssert.assertEquals(
                      "{\"paramC1\": \"someOtherValue\"}", toolExecutionRequest.arguments(), true);
                }));
  }

  private ToolExecutionResult toolExecutionResult(String resultText) {
    return ToolExecutionResult.builder().resultText(resultText).build();
  }
}
