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
package io.camunda.connector.e2e.agenticai.mcp.authentication;

import static io.camunda.connector.agenticai.mcp.client.model.content.McpTextContent.textContent;
import static io.camunda.connector.e2e.agenticai.BpmnUtil.serviceTasksByType;
import static io.camunda.connector.e2e.agenticai.BpmnUtil.updateInputMappings;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.search.response.Incident;
import io.camunda.connector.agenticai.mcp.client.model.McpToolDefinition;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientCallToolResult;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListToolsResult;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.connector.e2e.agenticai.BaseAgenticAiTest;
import io.camunda.connector.e2e.agenticai.mcp.authentication.McpAuthenticationTestConfiguration.McpRemoteClientInputMappingsProvider;
import io.camunda.connector.test.utils.annotation.SlowTest;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SlowTest
@TestPropertySource(
    properties = {
      "camunda.connector.agenticai.mcp.client.enabled=true",
      "camunda.connector.agenticai.mcp.client.clients.an-unauthenticated-mcp-client.enabled=true",
      "camunda.connector.agenticai.mcp.client.clients.an-unauthenticated-mcp-client.type=HTTP"
    })
@ActiveProfiles("mcp-standalone-test")
@Import(McpAuthenticationTestConfiguration.class)
abstract class BaseMcpAuthenticationTest extends BaseAgenticAiTest {

  @Autowired private McpRemoteClientInputMappingsProvider remoteClientProperties;

  @Value("classpath:mcp-connectors-standalone.bpmn")
  private Resource testProcess;

  @Test
  void shouldRaiseIncidentWhenAuthenticationFails() throws IOException {
    BpmnModelInstance bpmnModel = Bpmn.readModelFromStream(testProcess.getInputStream());

    // MCP Client
    serviceTasksByType(bpmnModel, type -> type.startsWith("io.camunda.agenticai:mcpclient"))
        .forEach(
            st ->
                updateInputMappings(
                    bpmnModel,
                    st,
                    Map.of("data.client.clientId", "an-unauthenticated-mcp-client")));

    // MCP Remote Client
    serviceTasksByType(bpmnModel, type -> type.startsWith("io.camunda.agenticai:mcpremoteclient"))
        .forEach(
            st ->
                updateInputMappings(
                    bpmnModel,
                    st,
                    remoteClientProperties.mcpRemoteClientInputMappings(Map.of(), false)));

    ZeebeTest zeebeTest = createProcessInstance(bpmnModel, Map.of("path", "tools"));

    Awaitility.with()
        .pollInSameThread()
        .await()
        .atMost(20, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              final var incidents =
                  camundaClient
                      .newIncidentSearchRequest()
                      .filter(
                          filter ->
                              filter.processInstanceKey(
                                  zeebeTest.getProcessInstanceEvent().getProcessInstanceKey()))
                      .send()
                      .join();

              assertThat(incidents.items())
                  .hasSize(2)
                  .extracting(Incident::getElementId)
                  .containsExactlyInAnyOrder("Client_List_Tools", "Remote_Client_List_Tools");

              assertThat(incidents.items())
                  .extracting(Incident::getErrorMessage)
                  .allSatisfy(
                      errorMessage ->
                          assertThat(errorMessage)
                              .startsWith("Client failed to initialize listing tools"));
            });
  }

  @Test
  void shouldListAndCallTools() throws IOException {
    BpmnModelInstance bpmnModel = Bpmn.readModelFromStream(testProcess.getInputStream());

    // MCP Client
    serviceTasksByType(bpmnModel, type -> type.startsWith("io.camunda.agenticai:mcpclient"))
        .forEach(
            st ->
                updateInputMappings(
                    bpmnModel,
                    st,
                    Map.of(
                        "data.connectorMode.standaloneModeFilters.tools.excluded",
                        "=[\"add\", \"base64Encode\", \"base64Decode\"]")));

    updateInputMappings(
        bpmnModel,
        "Client_Call_Tool",
        Map.ofEntries(
            Map.entry("data.connectorMode.operation.toolName", "uppercase"),
            Map.entry("data.connectorMode.operation.toolArguments", "={ message: \"hello\" }")));

    // MCP Remote Client
    serviceTasksByType(bpmnModel, type -> type.startsWith("io.camunda.agenticai:mcpremoteclient"))
        .forEach(
            st ->
                updateInputMappings(
                    bpmnModel,
                    st,
                    remoteClientProperties.mcpRemoteClientInputMappings(
                        Map.of(
                            "data.connectorMode.standaloneModeFilters.tools.excluded",
                            "=[\"uppercase\", \"lowercase\"]"))));

    updateInputMappings(
        bpmnModel,
        "Remote_Client_Call_Tool",
        Map.ofEntries(
            Map.entry("data.connectorMode.operation.toolName", "add"),
            Map.entry("data.connectorMode.operation.toolArguments", "={ a: 5, b: 3 }")));

    ZeebeTest zeebeTest =
        createProcessInstance(bpmnModel, Map.of("path", "tools")).waitForProcessCompletion();

    CamundaAssert.assertThat(zeebeTest.getProcessInstanceEvent())
        .hasVariableSatisfies(
            "clientListToolsResult",
            McpClientListToolsResult.class,
            listToolsResult ->
                assertThat(listToolsResult.toolDefinitions())
                    .isNotEmpty()
                    .extracting(McpToolDefinition::name)
                    .containsExactlyInAnyOrder("uppercase", "lowercase", "echo", "greet"))
        .hasVariableSatisfies(
            "clientCallToolResult",
            McpClientCallToolResult.class,
            toolCallResult -> {
              assertThat(toolCallResult.name()).isEqualTo("uppercase");
              assertThat(toolCallResult.content()).hasSize(1).containsExactly(textContent("HELLO"));
              assertThat(toolCallResult.isError()).isFalse();
            })
        .hasVariableSatisfies(
            "remoteClientListToolsResult",
            McpClientListToolsResult.class,
            listToolsResult ->
                assertThat(listToolsResult.toolDefinitions())
                    .isNotEmpty()
                    .extracting(McpToolDefinition::name)
                    .containsExactlyInAnyOrder(
                        "add", "echo", "base64Encode", "base64Decode", "greet"))
        .hasVariableSatisfies(
            "remoteClientCallToolResult",
            McpClientCallToolResult.class,
            toolCallResult -> {
              assertThat(toolCallResult.name()).isEqualTo("add");
              assertThat(toolCallResult.content()).hasSize(1).containsExactly(textContent("8"));
              assertThat(toolCallResult.isError()).isFalse();
            });
  }
}
