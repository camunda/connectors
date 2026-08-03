/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.localtoolbox.discovery;

import static io.camunda.connector.agenticai.localtoolbox.discovery.LocalToolboxGatewayToolHandler.PROPERTY_LOCAL_TOOLBOX_CLIENTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.tool.GatewayToolDefinition;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.localtoolbox.client.model.result.LocalToolboxCallToolResult;
import io.camunda.connector.agenticai.localtoolbox.client.model.result.LocalToolboxListToolsResult;
import io.camunda.connector.api.error.ConnectorException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class LocalToolboxGatewayToolHandlerTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private LocalToolboxGatewayToolHandler handler;

  @BeforeEach
  void setUp() {
    handler = new LocalToolboxGatewayToolHandler(objectMapper);
  }

  @Nested
  class TypeIdentification {

    @Test
    void returnsCorrectType() {
      assertThat(handler.type()).isEqualTo("localToolbox");
    }

    @Test
    void resolvesElementIdFromNamespacedToolName() {
      assertThat(handler.resolveElementId("LOCALTOOLBOX_myElement___myTool"))
          .isEqualTo("myElement");
    }
  }

  @Nested
  class ToolDiscoveryInitiation {

    @Test
    void returnsEmptyResult_whenNoLocalToolboxGatewayToolDefinitions() {
      var agentContext = AgentContext.empty();
      var gatewayToolDefinitions =
          List.of(
              createGatewayToolDefinition("other", "tool1"),
              createGatewayToolDefinition("mcpClient", "tool2"));

      var result = handler.initiateToolDiscovery(agentContext, gatewayToolDefinitions);

      assertThat(result.agentContext()).isEqualTo(agentContext);
      assertThat(result.toolDiscoveryToolCalls()).isEmpty();
    }

    @Test
    void createsDiscoveryToolCalls_whenLocalToolboxGatewayToolDefinitionsPresent() {
      var agentContext = AgentContext.empty();
      var gatewayToolDefinitions =
          List.of(
              createGatewayToolDefinition("localToolbox", "toolbox1"),
              createGatewayToolDefinition("localToolbox", "toolbox2"));

      var result = handler.initiateToolDiscovery(agentContext, gatewayToolDefinitions);

      assertThat(result.agentContext().properties())
          .containsEntry(PROPERTY_LOCAL_TOOLBOX_CLIENTS, List.of("toolbox1", "toolbox2"));
      assertThat(result.toolDiscoveryToolCalls()).hasSize(2);
      assertThat(result.toolDiscoveryToolCalls())
          .satisfiesExactly(
              toolCall -> {
                assertThat(toolCall.id()).isEqualTo("LOCALTOOLBOX_toolsList_toolbox1");
                assertThat(toolCall.name()).isEqualTo("toolbox1");
                assertThat(toolCall.arguments()).containsExactly(Map.entry("method", "tools/list"));
              },
              toolCall -> {
                assertThat(toolCall.id()).isEqualTo("LOCALTOOLBOX_toolsList_toolbox2");
                assertThat(toolCall.name()).isEqualTo("toolbox2");
              });
    }

    @ParameterizedTest
    @ValueSource(strings = {"toolbox___client", "name___", "___name"})
    void throwsException_whenGatewayToolDefinitionNameContainsSeparator(String invalidName) {
      var agentContext = AgentContext.empty();
      var gatewayToolDefinitions =
          List.of(createGatewayToolDefinition("localToolbox", invalidName));

      assertThatThrownBy(() -> handler.initiateToolDiscovery(agentContext, gatewayToolDefinitions))
          .isInstanceOf(ConnectorException.class)
          .asInstanceOf(InstanceOfAssertFactories.type(ConnectorException.class))
          .satisfies(
              e ->
                  assertThat(e.getErrorCode()).isEqualTo("LOCAL_TOOLBOX_INVALID_TOOL_DEFINITIONS"));
    }
  }

  @Nested
  class AllToolDiscoveryResultsPresent {

    @Test
    void returnsTrue_whenAllDiscoveryResultsPresent() {
      var agentContext =
          AgentContext.empty()
              .withProperty(PROPERTY_LOCAL_TOOLBOX_CLIENTS, List.of("toolbox1", "toolbox2"));
      var toolCallResults =
          List.of(
              ToolCallResult.builder()
                  .id("LOCALTOOLBOX_toolsList_toolbox1")
                  .name("toolbox1")
                  .content("result1")
                  .build(),
              ToolCallResult.builder()
                  .id("LOCALTOOLBOX_toolsList_toolbox2")
                  .name("toolbox2")
                  .content("result2")
                  .build());

      assertThat(handler.allToolDiscoveryResultsPresent(agentContext, toolCallResults)).isTrue();
    }

    @Test
    void returnsFalse_whenSomeDiscoveryResultsMissing() {
      var agentContext =
          AgentContext.empty()
              .withProperty(PROPERTY_LOCAL_TOOLBOX_CLIENTS, List.of("toolbox1", "toolbox2"));
      var toolCallResults =
          List.of(
              ToolCallResult.builder()
                  .id("LOCALTOOLBOX_toolsList_toolbox1")
                  .name("toolbox1")
                  .content("result1")
                  .build());

      assertThat(handler.allToolDiscoveryResultsPresent(agentContext, toolCallResults)).isFalse();
    }

    @Test
    void returnsTrue_whenNoClientsConfigured() {
      assertThat(handler.allToolDiscoveryResultsPresent(AgentContext.empty(), List.of())).isTrue();
    }
  }

  @Nested
  class ToolDiscoveryResultHandling {

    @ParameterizedTest
    @MethodSource("toolDiscoveryResultScenarios")
    void handlesToolDiscoveryResult_correctly(String toolCallId, boolean expected) {
      var toolCallResult =
          ToolCallResult.builder().id(toolCallId).name("toolbox1").content("result").build();

      assertThat(handler.handlesToolDiscoveryResult(toolCallResult)).isEqualTo(expected);
    }

    static Stream<Arguments> toolDiscoveryResultScenarios() {
      return Stream.of(
          arguments("LOCALTOOLBOX_toolsList_toolbox1", true),
          arguments("regular_tool_call", false),
          arguments((Object) null, false));
    }

    @Test
    void convertsDiscoveryResults_toToolDefinitions() {
      var agentContext = AgentContext.empty();
      var listToolsResult =
          new LocalToolboxListToolsResult(
              List.of(
                  ToolDefinition.builder()
                      .name("tool1")
                      .description("Tool 1 description")
                      .inputSchema(Map.of("type", "object"))
                      .build(),
                  ToolDefinition.builder()
                      .name("tool2")
                      .description("Tool 2 description")
                      .inputSchema(Map.of("type", "object"))
                      .build()));
      var toolDiscoveryResults =
          List.of(
              createToolCallResultWithContent(
                  "LOCALTOOLBOX_toolsList_toolbox1", "toolbox1", listToolsResult));

      var result = handler.handleToolDiscoveryResults(agentContext, toolDiscoveryResults);

      assertThat(result)
          .satisfiesExactly(
              toolDefinition -> {
                assertThat(toolDefinition.name()).isEqualTo("LOCALTOOLBOX_toolbox1___tool1");
                assertThat(toolDefinition.description()).isEqualTo("Tool 1 description");
              },
              toolDefinition ->
                  assertThat(toolDefinition.name()).isEqualTo("LOCALTOOLBOX_toolbox1___tool2"));
    }
  }

  @Nested
  class ToolCallTransformation {

    @Test
    void transformsLocalToolboxToolCalls_toOperations() {
      var agentContext = AgentContext.empty();
      var toolCalls =
          List.of(
              new ToolCall("call1", "LOCALTOOLBOX_toolbox1___tool1", Map.of("arg1", "value1")),
              new ToolCall("call2", "regular_tool", Map.of("arg2", "value2")));

      var result = handler.transformToolCalls(agentContext, toolCalls);

      assertThat(result)
          .satisfiesExactly(
              toolCall -> {
                assertThat(toolCall.id()).isEqualTo("call1");
                assertThat(toolCall.name()).isEqualTo("toolbox1");
                assertThat(toolCall.arguments().get("method")).isEqualTo("tools/call");
                assertThat(toolCall.arguments().get("params"))
                    .isEqualTo(Map.of("name", "tool1", "arguments", Map.of("arg1", "value1")));
              },
              toolCall -> assertThat(toolCall).isEqualTo(toolCalls.get(1)));
    }
  }

  @Nested
  class ToolCallResultTransformation {

    @Test
    void transformsResults_toFullyQualifiedToolCallResults() {
      var agentContext =
          AgentContext.empty().withProperty(PROPERTY_LOCAL_TOOLBOX_CLIENTS, List.of("toolbox1"));
      var callToolResult = new LocalToolboxCallToolResult("tool1", "Tool result");
      var toolCallResults =
          List.of(createToolCallResultWithContent("call1", "toolbox1", callToolResult));

      var result = handler.transformToolCallResults(agentContext, toolCallResults);

      assertThat(result)
          .singleElement()
          .satisfies(
              toolCallResult -> {
                assertThat(toolCallResult.id()).isEqualTo("call1");
                assertThat(toolCallResult.name()).isEqualTo("LOCALTOOLBOX_toolbox1___tool1");
                assertThat(toolCallResult.content()).isEqualTo("Tool result");
              });
    }

    @Test
    void preservesCompletedAtFromTheOriginalResult() {
      var agentContext =
          AgentContext.empty().withProperty(PROPERTY_LOCAL_TOOLBOX_CLIENTS, List.of("toolbox1"));
      var callToolResult = new LocalToolboxCallToolResult("tool1", "Tool result");
      var completedAt = OffsetDateTime.parse("2026-07-02T10:00:00Z");
      var toolCallResult =
          createToolCallResultWithContent("call1", "toolbox1", callToolResult)
              .withCompletedAt(completedAt);

      var result = handler.transformToolCallResults(agentContext, List.of(toolCallResult));

      assertThat(result).singleElement().extracting("completedAt").isEqualTo(completedAt);
    }

    @Test
    void preservesOriginalResult_whenNotLocalToolboxClient() {
      var agentContext =
          AgentContext.empty().withProperty(PROPERTY_LOCAL_TOOLBOX_CLIENTS, List.of("toolbox1"));
      var toolCallResults = List.of(createToolCallResult("call1", "other_tool"));

      var result = handler.transformToolCallResults(agentContext, toolCallResults);

      assertThat(result).isEqualTo(toolCallResults);
    }
  }

  private GatewayToolDefinition createGatewayToolDefinition(String type, String name) {
    return GatewayToolDefinition.builder()
        .type(type)
        .name(name)
        .description("Description for " + name)
        .properties(Map.of())
        .build();
  }

  private ToolCallResult createToolCallResult(String id, String name) {
    return ToolCallResult.builder().id(id).name(name).content("result content").build();
  }

  private ToolCallResult createToolCallResultWithContent(String id, String name, Object content) {
    return ToolCallResult.builder().id(id).name(name).content(content).build();
  }
}
