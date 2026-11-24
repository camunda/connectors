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

import static io.camunda.connector.agenticai.mcp.client.model.content.McpTextContent.textContent;
import static io.camunda.connector.e2e.agenticai.BpmnUtil.serviceTasksByType;
import static io.camunda.connector.e2e.agenticai.BpmnUtil.updateInputMappings;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.connector.agenticai.mcp.client.model.McpToolDefinition;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientCallToolResult;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientGetPromptResult;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientGetPromptResult.PromptMessage;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientGetPromptResult.TextMessage;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListPromptsResult;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListResourceTemplatesResult;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListResourcesResult;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListToolsResult;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientReadResourceResult;
import io.camunda.connector.agenticai.mcp.client.model.result.PromptDescription;
import io.camunda.connector.agenticai.mcp.client.model.result.PromptDescription.PromptArgument;
import io.camunda.connector.agenticai.mcp.client.model.result.ResourceData.TextResourceData;
import io.camunda.connector.agenticai.mcp.client.model.result.ResourceDescription;
import io.camunda.connector.agenticai.mcp.client.model.result.ResourceTemplate;
import io.camunda.connector.e2e.agenticai.BaseAgenticAiTest;
import io.camunda.connector.e2e.agenticai.McpTestServerConfiguration;
import io.camunda.connector.e2e.agenticai.McpTestServerConfiguration.McpTestServers;
import io.camunda.connector.e2e.agenticai.McpTestServerTransport;
import io.camunda.connector.test.utils.annotation.SlowTest;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.ServiceTask;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.TestPropertySource;

@SlowTest
@TestPropertySource(properties = {"camunda.connector.agenticai.mcp.client.enabled=true"})
@Import({McpTestServerConfiguration.class, McpStdioWarmUp.class})
public class McpStandaloneTests extends BaseAgenticAiTest {

  private static final String JSON_SCHEMA = "https://json-schema.org/draft/2020-12/schema";

  private static final McpToolDefinition ECHO_META_TOOL =
      new McpToolDefinition(
          "echoMeta",
          "echoMeta",
          "Echo the MCP _meta data provided with the tool call request",
          Map.of(
              "$schema",
              JSON_SCHEMA,
              "type",
              "object",
              "properties",
              Map.of(),
              "required",
              List.of()));

  private static final McpToolDefinition ECHO_TOOL =
      new McpToolDefinition(
          "echo",
          "echo",
          "Echo the provided message",
          Map.of(
              "$schema",
              JSON_SCHEMA,
              "type",
              "object",
              "properties",
              Map.of("message", Map.of("type", "string", "description", "The message to return")),
              "required",
              List.of("message")));

  private static final McpToolDefinition ADD_TOOL =
      new McpToolDefinition(
          "add",
          "add",
          "Add two numbers",
          Map.of(
              "$schema",
              JSON_SCHEMA,
              "type",
              "object",
              "properties",
              Map.of(
                  "a", Map.of("type", "integer", "format", "int32"),
                  "b", Map.of("type", "integer", "format", "int32")),
              "required",
              List.of("a", "b")));

  private static final McpToolDefinition GREET_TOOL =
      new McpToolDefinition(
          "greet",
          "greet",
          "Return a nice greeting message",
          Map.of(
              "$schema",
              JSON_SCHEMA,
              "type",
              "object",
              "properties",
              Map.of(
                  "name",
                  Map.of("type", "string", "description", "The name to greet"),
                  "language",
                  Map.of("type", "string", "enum", List.of("EN", "DE", "ES", "IT"))),
              "required",
              List.of("name")));

  private static final ResourceDescription JOKES_RESOURCE =
      new ResourceDescription(
          "file://jokes-guide.md",
          "Jokes Guideline",
          null,
          "How to Write Jokes: A Guide for LLMs",
          "text/markdown");

  private static final ResourceDescription COOKBOOK_RESOURCE =
      new ResourceDescription(
          "file://cookbook-essentials.pdf",
          "Cookbook Essentials",
          null,
          "A comprehensive guide to essential cooking techniques and recipes.",
          "application/pdf");

  private static final ResourceTemplate ECHO_META_RESOURCE_TEMPLATE =
      new ResourceTemplate(
          "echo-meta://{name}",
          "echo-meta",
          null,
          "Echo the MCP _meta data provided with the resource read request",
          "application/json");

  private static final PromptDescription ECHO_META_PROMPT =
      new PromptDescription(
          "echo-meta", "", "Echo the MCP _meta data provided with the prompt request", List.of());

  private static final PromptDescription GREETING_PROMPT =
      new PromptDescription(
          "get-greeting",
          "",
          "Generate a personalized greeting message based on the user's name and preferred greeting language.",
          List.of(
              new PromptArgument("name", "User's name", true),
              new PromptArgument(
                  "preferredLanguage", "User's preferred greeting language", false)));

  @Autowired private McpTestServers mcpTestServers;

  static Stream<Arguments> localAllFixtureRows() {
    return localRows(Fixture.values());
  }

  static Stream<Arguments> localV2AndV3FixtureRows() {
    return localRows(Fixture.V2, Fixture.V3);
  }

  static Stream<Arguments> remoteAllFixtureRows() {
    return remoteRows(Fixture.values());
  }

  static Stream<Arguments> remoteV2AndV3FixtureRows() {
    return remoteRows(Fixture.V2, Fixture.V3);
  }

  private static Stream<Arguments> localRows(Fixture... fixtures) {
    return rows(
        List.of(
            McpTestServerTransport.HTTP, McpTestServerTransport.SSE, McpTestServerTransport.STDIO),
        fixtures);
  }

  private static Stream<Arguments> remoteRows(Fixture... fixtures) {
    return rows(List.of(McpTestServerTransport.HTTP, McpTestServerTransport.SSE), fixtures);
  }

  private static Stream<Arguments> rows(
      List<McpTestServerTransport> transports, Fixture... fixtures) {
    return Arrays.stream(fixtures)
        .flatMap(fixture -> transports.stream().map(transport -> Arguments.of(fixture, transport)));
  }

  @ParameterizedTest(name = "{0} {1}")
  @MethodSource("localAllFixtureRows")
  void localToolsListAndCall(Fixture fixture, McpTestServerTransport transport) throws IOException {
    BpmnModelInstance bpmnModel = bootstrapTestProcess(fixture, transport, ClientKind.LOCAL);

    CamundaAssert.assertThat(executeProcess(bpmnModel, "tools", ClientKind.LOCAL))
        .hasVariableSatisfies(
            "clientListToolsResult",
            McpClientListToolsResult.class,
            result ->
                assertThat(result)
                    .isEqualTo(new McpClientListToolsResult(List.of(ECHO_META_TOOL, ECHO_TOOL))))
        .hasVariableSatisfies(
            "clientCallToolResult",
            McpClientCallToolResult.class,
            result ->
                assertThat(result)
                    .isEqualTo(
                        new McpClientCallToolResult(
                            "echoMeta", List.of(textContent(fixture.echoedMeta())), false)));
  }

  @ParameterizedTest(name = "{0} {1}")
  @MethodSource("remoteAllFixtureRows")
  void remoteToolsListAndCall(Fixture fixture, McpTestServerTransport transport)
      throws IOException {
    BpmnModelInstance bpmnModel = bootstrapTestProcess(fixture, transport, ClientKind.REMOTE);

    CamundaAssert.assertThat(executeProcess(bpmnModel, "tools", ClientKind.REMOTE))
        .hasVariableSatisfies(
            "remoteClientListToolsResult",
            McpClientListToolsResult.class,
            result ->
                assertThat(result)
                    .isEqualTo(new McpClientListToolsResult(List.of(ADD_TOOL, GREET_TOOL))))
        .hasVariableSatisfies(
            "remoteClientCallToolResult",
            McpClientCallToolResult.class,
            result ->
                assertThat(result)
                    .isEqualTo(
                        new McpClientCallToolResult("add", List.of(textContent("8")), false)));
  }

  @ParameterizedTest(name = "{0} {1}")
  @MethodSource("localV2AndV3FixtureRows")
  void localResourcesListAndRead(Fixture fixture, McpTestServerTransport transport)
      throws IOException {
    BpmnModelInstance bpmnModel = bootstrapTestProcess(fixture, transport, ClientKind.LOCAL);

    CamundaAssert.assertThat(executeProcess(bpmnModel, "resources", ClientKind.LOCAL))
        .hasVariableSatisfies(
            "clientCallListResourcesResult",
            McpClientListResourcesResult.class,
            result ->
                assertThat(result)
                    .isEqualTo(new McpClientListResourcesResult(List.of(JOKES_RESOURCE))))
        .hasVariableSatisfies(
            "clientCallReadResourceResult",
            McpClientReadResourceResult.class,
            result ->
                assertThat(result)
                    .isEqualTo(
                        new McpClientReadResourceResult(
                            List.of(
                                new TextResourceData(
                                    "echo-meta://client",
                                    "application/json",
                                    fixture.echoedMeta())))));
  }

  @ParameterizedTest(name = "{0} {1}")
  @MethodSource("remoteV2AndV3FixtureRows")
  void remoteResourcesListAndRead(Fixture fixture, McpTestServerTransport transport)
      throws IOException {
    BpmnModelInstance bpmnModel = bootstrapTestProcess(fixture, transport, ClientKind.REMOTE);

    CamundaAssert.assertThat(executeProcess(bpmnModel, "resources", ClientKind.REMOTE))
        .hasVariableSatisfies(
            "remoteClientListResourcesResult",
            McpClientListResourcesResult.class,
            result ->
                assertThat(result)
                    .isEqualTo(new McpClientListResourcesResult(List.of(COOKBOOK_RESOURCE))))
        .hasVariableSatisfies(
            "remoteClientReadResourceResult",
            McpClientReadResourceResult.class,
            result ->
                assertThat(result)
                    .isEqualTo(
                        new McpClientReadResourceResult(
                            List.of(
                                new TextResourceData(
                                    "echo-meta://remote-client", "application/json", "{}")))));
  }

  @ParameterizedTest(name = "{0} {1}")
  @MethodSource("localV2AndV3FixtureRows")
  void localResourceTemplatesListAndRead(Fixture fixture, McpTestServerTransport transport)
      throws IOException {
    BpmnModelInstance bpmnModel = bootstrapTestProcess(fixture, transport, ClientKind.LOCAL);

    CamundaAssert.assertThat(executeProcess(bpmnModel, "resourceTemplates", ClientKind.LOCAL))
        .hasVariableSatisfies(
            "clientCallListResourceTemplatesResult",
            McpClientListResourceTemplatesResult.class,
            result ->
                assertThat(result)
                    .isEqualTo(
                        new McpClientListResourceTemplatesResult(
                            List.of(ECHO_META_RESOURCE_TEMPLATE))))
        .hasVariableSatisfies(
            "clientCallReadResourceResult",
            McpClientReadResourceResult.class,
            result ->
                assertThat(result)
                    .isEqualTo(
                        new McpClientReadResourceResult(
                            List.of(
                                new TextResourceData(
                                    "echo-meta://client-template",
                                    "application/json",
                                    fixture.echoedMeta())))));
  }

  @ParameterizedTest(name = "{0} {1}")
  @MethodSource("remoteV2AndV3FixtureRows")
  void remoteResourceTemplatesListAndRead(Fixture fixture, McpTestServerTransport transport)
      throws IOException {
    BpmnModelInstance bpmnModel = bootstrapTestProcess(fixture, transport, ClientKind.REMOTE);

    CamundaAssert.assertThat(executeProcess(bpmnModel, "resourceTemplates", ClientKind.REMOTE))
        .hasVariableSatisfies(
            "remoteClientListResourceTemplatesResult",
            McpClientListResourceTemplatesResult.class,
            result ->
                assertThat(result)
                    .isEqualTo(
                        new McpClientListResourceTemplatesResult(
                            List.of(ECHO_META_RESOURCE_TEMPLATE))))
        .hasVariableSatisfies(
            "remoteClientReadResourceResult",
            McpClientReadResourceResult.class,
            result ->
                assertThat(result)
                    .isEqualTo(
                        new McpClientReadResourceResult(
                            List.of(
                                new TextResourceData(
                                    "echo-meta://remote-client-template",
                                    "application/json",
                                    "{}")))));
  }

  @ParameterizedTest(name = "{0} {1}")
  @MethodSource("localV2AndV3FixtureRows")
  void localPromptsListAndGet(Fixture fixture, McpTestServerTransport transport)
      throws IOException {
    BpmnModelInstance bpmnModel = bootstrapTestProcess(fixture, transport, ClientKind.LOCAL);

    CamundaAssert.assertThat(executeProcess(bpmnModel, "prompts", ClientKind.LOCAL))
        .hasVariableSatisfies(
            "clientCallListPromptsResult",
            McpClientListPromptsResult.class,
            result ->
                assertThat(result)
                    .isEqualTo(new McpClientListPromptsResult(List.of(ECHO_META_PROMPT))))
        .hasVariableSatisfies(
            "clientCallGetPromptResult",
            McpClientGetPromptResult.class,
            result ->
                assertThat(result)
                    .isEqualTo(
                        new McpClientGetPromptResult(
                            "Echo Meta Prompt",
                            List.of(
                                new PromptMessage(
                                    "user", new TextMessage(fixture.echoedMeta()))))));
  }

  @ParameterizedTest(name = "{0} {1}")
  @MethodSource("remoteV2AndV3FixtureRows")
  void remotePromptsListAndGet(Fixture fixture, McpTestServerTransport transport)
      throws IOException {
    BpmnModelInstance bpmnModel = bootstrapTestProcess(fixture, transport, ClientKind.REMOTE);

    CamundaAssert.assertThat(executeProcess(bpmnModel, "prompts", ClientKind.REMOTE))
        .hasVariableSatisfies(
            "remoteClientListPromptsResult",
            McpClientListPromptsResult.class,
            result ->
                assertThat(result)
                    .isEqualTo(new McpClientListPromptsResult(List.of(GREETING_PROMPT))))
        .hasVariableSatisfies(
            "remoteClientGetPromptResult",
            McpClientGetPromptResult.class,
            result ->
                assertThat(result)
                    .isEqualTo(
                        new McpClientGetPromptResult(
                            "Greeting Prompt",
                            List.of(
                                new PromptMessage(
                                    "user",
                                    new TextMessage(
                                        """
                                        Greet me in a nice way! My name is Jane. My preferred greeting language is English.

                                        Instructions:
                                        You can use the greet tool to generate a nice greeting message, if you do not know how to generate a nice greeting yourself.
                                        """))))));
  }

  private ProcessInstanceEvent executeProcess(
      BpmnModelInstance bpmnModel, String path, ClientKind clientKind) {
    final var processInstanceEvent =
        awaitProcessCompletion(createProcessInstance(bpmnModel, clientKind.startVariables(path)))
            .getProcessInstanceEvent();

    final var skippedTaskIds =
        serviceTasksByType(bpmnModel, type -> type.startsWith(clientKind.other().taskTypePrefix))
            .stream()
            .map(ServiceTask::getId)
            .toArray(String[]::new);
    CamundaAssert.assertThat(processInstanceEvent).hasNotActivatedElements(skippedTaskIds);

    return processInstanceEvent;
  }

  private BpmnModelInstance bootstrapTestProcess(
      Fixture fixture, McpTestServerTransport transport, ClientKind clientKind) throws IOException {
    BpmnModelInstance bpmnModel =
        Bpmn.readModelFromStream(new ClassPathResource(fixture.resource).getInputStream());

    final var inputMappings =
        switch (clientKind) {
          case LOCAL -> Map.of("data.client.clientId", McpTestServers.clientId(transport));
          case REMOTE -> mcpTestServers.remoteClientInputMappings(transport);
        };
    serviceTasksByType(bpmnModel, type -> type.startsWith(clientKind.taskTypePrefix))
        .forEach(serviceTask -> updateInputMappings(bpmnModel, serviceTask, inputMappings));

    return bpmnModel;
  }

  /** The client chain that a process instance runs. The other chain MUST stay untouched. */
  enum ClientKind {
    LOCAL("io.camunda.agenticai:mcpclient"),
    REMOTE("io.camunda.agenticai:mcpremoteclient");

    private final String taskTypePrefix;

    ClientKind(String taskTypePrefix) {
      this.taskTypePrefix = taskTypePrefix;
    }

    ClientKind other() {
      return this == LOCAL ? REMOTE : LOCAL;
    }

    Map<String, Object> startVariables(String path) {
      return Map.of("path", path, "runLocal", this == LOCAL, "runRemote", this == REMOTE);
    }
  }

  /**
   * A BPMN fixture per element template version 1, 2 and 3 of the MCP client and the MCP remote
   * client (constants V0, V2 and V3). The resource, resource template and prompt paths need
   * template version 2 or 3. Only the V3 fixture sets {@code meta} on its local client tasks.
   */
  enum Fixture {
    V0("regression/mcp-connectors-standalone-v0.bpmn", false),
    V2("regression/mcp-connectors-standalone-v2.bpmn", false),
    V3("mcp-connectors-standalone.bpmn", true);

    private final String resource;
    private final boolean setsMeta;

    Fixture(String resource, boolean setsMeta) {
      this.resource = resource;
      this.setsMeta = setsMeta;
    }

    /** The {@code _meta} that the echo tool, resource and prompt return for a local client task. */
    String echoedMeta() {
      return setsMeta ? "{\"exampleMetaKey\":\"exampleMetaValue\"}" : "{}";
    }
  }
}
