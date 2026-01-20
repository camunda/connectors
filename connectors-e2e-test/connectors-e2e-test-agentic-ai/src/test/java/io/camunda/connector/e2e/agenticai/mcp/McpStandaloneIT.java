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

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientGetPromptResult;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListPromptsResult;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListResourceTemplatesResult;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListResourcesResult;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListToolsResult;
import io.camunda.connector.agenticai.mcp.client.model.result.PromptDescription;
import io.camunda.connector.agenticai.mcp.client.model.result.ResourceDescription;
import io.camunda.connector.agenticai.mcp.client.model.result.ResourceTemplate;
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
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

@SlowTest
@TestPropertySource(properties = {"camunda.connector.agenticai.mcp.client.enabled=true"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class McpStandaloneIT extends BaseAgenticAiTest {

  private static final WireMockServer wireMock = setupWireMockServer();

  private static WireMockServer setupWireMockServer() {
    var server = new WireMockServer(options().globalTemplating(true));
    server.start();

    return server;
  }

  @DynamicPropertySource
  static void mcpClientUrl(DynamicPropertyRegistry registry) {
    registry.add(
        "camunda.connector.agenticai.mcp.client.clients.a-mcp-client.http.url",
        () -> wireMock.baseUrl() + "/mcp");
  }

  @AfterAll
  void teardown() {
    wireMock.stop();
  }

  @BeforeEach
  void resetWireMock() {
    wireMock.resetAll();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"regression/mcp-connectors-standalone-v0.bpmn", "mcp-connectors-standalone.bpmn"})
  void toolsListAndCall(String processDefinitionFilePath) throws IOException {
    BpmnModelInstance bpmnModel = bootstrapTestProcess(processDefinitionFilePath);

    executeProcessAndVerify(
        bpmnModel,
        Map.of("path", "tools"),
        processInstanceEvent -> {
          CamundaAssert.assertThat(processInstanceEvent)
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

          wireMock.verify(
              postRequestedFor(urlEqualTo("/mcp"))
                  .withRequestBody(matchingJsonPath("$.method", equalTo("tools/list"))));

          wireMock.verify(
              postRequestedFor(urlEqualTo("/mcp"))
                  .withRequestBody(matchingJsonPath("$.method", equalTo("tools/call")))
                  .withRequestBody(matchingJsonPath("$.params.name", equalTo("toolA")))
                  .withRequestBody(
                      matchingJsonPath("$.params.arguments.paramA1", equalTo("someValue")))
                  .withRequestBody(matchingJsonPath("$.params.arguments.paramA2", equalTo("3"))));

          wireMock.verify(
              postRequestedFor(urlEqualTo("/mcp"))
                  .withRequestBody(matchingJsonPath("$.method", equalTo("tools/call")))
                  .withRequestBody(matchingJsonPath("$.params.name", equalTo("toolC")))
                  .withRequestBody(
                      matchingJsonPath("$.params.arguments.paramC1", equalTo("someOtherValue"))));
        });
  }

  @Test
  void resourcesListAndRead() throws IOException {
    BpmnModelInstance bpmnModel = bootstrapTestProcess("mcp-connectors-standalone.bpmn");

    executeProcessAndVerify(
        bpmnModel,
        Map.of("path", "resources"),
        processInstanceEvent -> {
          CamundaAssert.assertThat(processInstanceEvent)
              .hasVariableSatisfies(
                  "clientCallListResourcesResult",
                  McpClientListResourcesResult.class,
                  listResourcesResult ->
                      assertThat(listResourcesResult.resources())
                          .hasSize(2)
                          .extracting(ResourceDescription::uri)
                          .containsExactly("resourceA", "resourceC"))
              .hasVariable(
                  "clientCallReadResourceResult",
                  Map.of(
                      "contents",
                      List.of(
                          Map.of(
                              "uri",
                              "resourceA",
                              "mimeType",
                              "text/plain",
                              "text",
                              "This is the content of Resource A."))))
              .hasVariableSatisfies(
                  "remoteClientListResourcesResult",
                  McpClientListResourcesResult.class,
                  listResourcesResult ->
                      assertThat(listResourcesResult.resources())
                          .hasSize(2)
                          .extracting(ResourceDescription::uri)
                          .containsExactly("resourceB", "resourceC"))
              .hasVariable(
                  "remoteClientReadResourceResult",
                  Map.of(
                      "contents",
                      List.of(
                          Map.of(
                              "uri",
                              "resourceC",
                              "mimeType",
                              "text/markdown",
                              "text",
                              "# This is the content of Resource C."))));

          wireMock.verify(
              2,
              postRequestedFor(urlEqualTo("/mcp"))
                  .withRequestBody(matchingJsonPath("$.method", equalTo("resources/list"))));

          wireMock.verify(
              postRequestedFor(urlEqualTo("/mcp"))
                  .withRequestBody(matchingJsonPath("$.method", equalTo("resources/read")))
                  .withRequestBody(matchingJsonPath("$.params.uri", equalTo("resourceA"))));

          wireMock.verify(
              postRequestedFor(urlEqualTo("/mcp"))
                  .withRequestBody(matchingJsonPath("$.method", equalTo("resources/read")))
                  .withRequestBody(matchingJsonPath("$.params.uri", equalTo("resourceC"))));
        });
  }

  @Test
  void resourceTemplatesListAndRead() throws IOException {
    BpmnModelInstance bpmnModel = bootstrapTestProcess("mcp-connectors-standalone.bpmn");

    executeProcessAndVerify(
        bpmnModel,
        Map.of("path", "resourceTemplates"),
        processInstanceEvent -> {
          CamundaAssert.assertThat(processInstanceEvent)
              .hasVariableSatisfies(
                  "clientCallListResourceTemplatesResult",
                  McpClientListResourceTemplatesResult.class,
                  listResourceTemplatesResult ->
                      assertThat(listResourceTemplatesResult.resourceTemplates())
                          .hasSize(2)
                          .extracting(ResourceTemplate::uriTemplate)
                          .containsExactly("resource-a-{number}", "resource-c-{number}"))
              .hasVariable(
                  "clientCallReadResourceResult",
                  Map.of(
                      "contents",
                      List.of(
                          Map.of(
                              "uri",
                              "resource-a-1",
                              "mimeType",
                              "text/plain",
                              "text",
                              "This is the content of Resource A number 1."))))
              .hasVariableSatisfies(
                  "remoteClientListResourceTemplatesResult",
                  McpClientListResourceTemplatesResult.class,
                  listResourceTemplatesResult ->
                      assertThat(listResourceTemplatesResult.resourceTemplates())
                          .hasSize(2)
                          .extracting(ResourceTemplate::uriTemplate)
                          .containsExactly("resource-a-{number}", "resource-b-{number}"))
              .hasVariable(
                  "remoteClientReadResourceResult",
                  Map.of(
                      "contents",
                      List.of(
                          Map.of(
                              "uri",
                              "resource-a-1",
                              "mimeType",
                              "text/plain",
                              "text",
                              "This is the content of Resource A number 1."))));

          wireMock.verify(
              2,
              postRequestedFor(urlEqualTo("/mcp"))
                  .withRequestBody(
                      matchingJsonPath("$.method", equalTo("resources/templates/list"))));

          wireMock.verify(
              2,
              postRequestedFor(urlEqualTo("/mcp"))
                  .withRequestBody(matchingJsonPath("$.method", equalTo("resources/read")))
                  .withRequestBody(matchingJsonPath("$.params.uri", equalTo("resource-a-1"))));
        });
  }

  @Test
  void promptsListAndGet() throws IOException {
    BpmnModelInstance bpmnModel = bootstrapTestProcess("mcp-connectors-standalone.bpmn");

    executeProcessAndVerify(
        bpmnModel,
        Map.of("path", "prompts"),
        processInstanceEvent -> {
          CamundaAssert.assertThat(processInstanceEvent)
              .hasVariableSatisfies(
                  "clientCallListPromptsResult",
                  McpClientListPromptsResult.class,
                  listPromptsResult ->
                      assertThat(listPromptsResult.promptDescriptions())
                          .hasSize(2)
                          .extracting(PromptDescription::name)
                          .containsExactly("promptA", "promptC"))
              .hasVariableSatisfies(
                  "clientCallGetPromptResult",
                  McpClientGetPromptResult.class,
                  getPromptResult -> {
                    assertThat(getPromptResult.description()).isEqualTo("Prompt A");
                    assertThat(getPromptResult.messages())
                        .hasSize(1)
                        .containsExactly(
                            new McpClientGetPromptResult.PromptMessage(
                                "user",
                                new McpClientGetPromptResult.TextMessage("Please do task A")));
                  })
              .hasVariableSatisfies(
                  "remoteClientListPromptsResult",
                  McpClientListPromptsResult.class,
                  listPromptsResult ->
                      assertThat(listPromptsResult.promptDescriptions())
                          .hasSize(2)
                          .extracting(PromptDescription::name)
                          .containsExactly("promptA", "promptB"))
              .hasVariableSatisfies(
                  "remoteClientGetPromptResult",
                  McpClientGetPromptResult.class,
                  getPromptResult -> {
                    assertThat(getPromptResult.description()).isEqualTo("Prompt C");
                    assertThat(getPromptResult.messages())
                        .hasSize(1)
                        .containsExactly(
                            new McpClientGetPromptResult.PromptMessage(
                                "user",
                                new McpClientGetPromptResult.TextMessage("Please do task C")));
                  });

          wireMock.verify(
              2,
              postRequestedFor(urlEqualTo("/mcp"))
                  .withRequestBody(matchingJsonPath("$.method", equalTo("prompts/list"))));

          wireMock.verify(
              postRequestedFor(urlEqualTo("/mcp"))
                  .withRequestBody(matchingJsonPath("$.method", equalTo("prompts/get")))
                  .withRequestBody(matchingJsonPath("$.params.name", equalTo("promptA")))
                  .withRequestBody(matchingJsonPath("$.params.arguments.aName", equalTo("nameA"))));

          wireMock.verify(
              postRequestedFor(urlEqualTo("/mcp"))
                  .withRequestBody(matchingJsonPath("$.method", equalTo("prompts/get")))
                  .withRequestBody(matchingJsonPath("$.params.name", equalTo("promptC")))
                  .withRequestBody(matchingJsonPath("$.params.arguments.cName", equalTo("nameC"))));
        });
  }

  private BpmnModelInstance bootstrapTestProcess(String processDefinitionFilePath)
      throws IOException {
    BpmnModelInstance bpmnModel =
        Bpmn.readModelFromStream(new ClassPathResource(processDefinitionFilePath).getInputStream());
    setRemoteMcpClientUrl(bpmnModel, wireMock.baseUrl() + "/mcp");
    return bpmnModel;
  }

  private void executeProcessAndVerify(
      BpmnModelInstance model,
      Map<String, Object> variables,
      Consumer<ProcessInstanceEvent> assertion) {
    ZeebeTest zeebeTest = createProcessInstance(model, variables).waitForProcessCompletion();

    assertion.accept(zeebeTest.getProcessInstanceEvent());
  }

  private void setRemoteMcpClientUrl(BpmnModelInstance bpmnModel, String url) {
    updateServiceTaskInput(bpmnModel, url);
  }

  private void updateServiceTaskInput(BpmnModelInstance bpmnModel, String value) {
    var serviceTasks =
        bpmnModel.getModelElementsByType(ServiceTask.class).stream()
            .filter(
                st -> {
                  var modelerTemplate =
                      st.getAttributeValueNs(
                          "http://camunda.org/schema/zeebe/1.0", "modelerTemplate");
                  return modelerTemplate != null
                      && (modelerTemplate.contains(
                              "io.camunda.connectors.agenticai.mcp.remoteclient")
                          || modelerTemplate.contains("io.camunda.agenticai:mcpremoteclient"));
                })
            .toList();
    assertThat(serviceTasks).isNotEmpty();

    serviceTasks.forEach(
        serviceTask -> {
          ZeebeIoMapping ioMapping = serviceTask.getSingleExtensionElement(ZeebeIoMapping.class);
          assertThat(ioMapping).isNotNull();

          final var inputMapping =
              ioMapping.getChildElementsByType(ZeebeInput.class).stream()
                  .filter(input -> "data.transport.http.url".equals(input.getTarget()))
                  .findFirst()
                  .orElseGet(
                      () -> {
                        final var im = bpmnModel.newInstance(ZeebeInput.class);
                        im.setTarget("data.transport.http.url");
                        ioMapping.addChildElement(im);
                        return im;
                      });

          inputMapping.setSource(value);
        });
  }
}
