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

import static io.camunda.connector.agenticai.model.message.content.TextContent.textContent;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.mcp.client.model.result.McpClientCallToolResult;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListToolsResult;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.connector.e2e.agenticai.BaseAgenticAiTest;
import io.camunda.connector.test.utils.annotation.SlowTest;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.ServiceTask;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeInput;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeIoMapping;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SlowTest
@TestPropertySource(properties = {"camunda.connector.agenticai.mcp.client.enabled=true"})
@ActiveProfiles("mcp-standalone-test")
@Import(McpAuthenticationTestConfiguration.class)
abstract class BaseMcpAuthenticationTest extends BaseAgenticAiTest {

  @Autowired private McpRemoteClientConnectorPropertiesProvider remoteClientProperties;

  @Value("classpath:mcp-connectors-standalone.bpmn")
  private Resource testProcess;

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
                    remoteClientProperties.mcpRemoteClientConnnectorProperties(
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
                    .extracting(ToolDefinition::name)
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
                    .extracting(ToolDefinition::name)
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

  private List<ServiceTask> serviceTasksByType(
      BpmnModelInstance bpmnModel, Predicate<String> typePredicate) {
    return bpmnModel.getModelElementsByType(ServiceTask.class).stream()
        .filter(
            st -> {
              var taskDefinition = st.getSingleExtensionElement(ZeebeTaskDefinition.class);
              var type = taskDefinition.getType();
              return type != null && typePredicate.test(type);
            })
        .toList();
  }

  private void updateInputMappings(
      BpmnModelInstance bpmnModel, String serviceTaskId, Map<String, String> inputMappings) {
    updateInputMappings(
        bpmnModel, (ServiceTask) bpmnModel.getModelElementById(serviceTaskId), inputMappings);
  }

  private void updateInputMappings(
      BpmnModelInstance bpmnModel, ServiceTask serviceTask, Map<String, String> inputMappings) {
    ZeebeIoMapping ioMapping = serviceTask.getSingleExtensionElement(ZeebeIoMapping.class);
    inputMappings.forEach(
        (target, source) -> {
          getOrCreateInputMapping(bpmnModel, ioMapping, target, source).setSource(source);
        });
  }

  private ZeebeInput getOrCreateInputMapping(
      BpmnModelInstance bpmnModel, ZeebeIoMapping ioMapping, String target, String source) {
    return ioMapping.getChildElementsByType(ZeebeInput.class).stream()
        .filter(input -> target.equals(input.getTarget()))
        .findFirst()
        .orElseGet(
            () -> {
              final var im = bpmnModel.newInstance(ZeebeInput.class);
              im.setTarget(target);
              ioMapping.addChildElement(im);
              return im;
            });
  }
}
