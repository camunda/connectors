/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.discovery;

import static io.camunda.connector.agenticai.a2a.discovery.A2AClientGatewayToolHandler.PROPERTY_A2A_CLIENTS;
import static io.camunda.connector.agenticai.util.ObjectMapperConstants.STRING_OBJECT_MAP_TYPE_REFERENCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.a2a.client.model.result.A2AClientSendMessageResult;
import io.camunda.connector.agenticai.a2a.client.model.result.A2AClientSendMessageResult.TaskState;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.model.message.content.TextContent;
import io.camunda.connector.agenticai.model.tool.GatewayToolDefinition;
import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class A2AClientGatewayToolHandlerTest {

  public static final String A2A_CLIENT_GATEWAY_TYPE = "a2aClient";
  private final ObjectMapper objectMapper = new ObjectMapper();
  private A2AClientGatewayToolHandler handler;

  @BeforeEach
  void setUp() {
    handler = new A2AClientGatewayToolHandler(objectMapper);
  }

  @Nested
  class TypeIdentification {

    @Test
    void returnsCorrectType() {
      assertThat(handler.type()).isEqualTo(A2A_CLIENT_GATEWAY_TYPE);
    }
  }

  @Nested
  class ToolDiscoveryInitiation {

    @Test
    void returnsEmptyResult_whenNoA2AGatewayToolDefinitions() {
      var agentContext = AgentContext.empty();
      var gatewayToolDefinitions =
          List.of(
              createGatewayToolDefinition("other", "tool1"),
              createGatewayToolDefinition("different", "tool2"));

      var result = handler.initiateToolDiscovery(agentContext, gatewayToolDefinitions);

      assertThat(result.agentContext()).isEqualTo(agentContext);
      assertThat(result.toolDiscoveryToolCalls()).isEmpty();
    }

    @Test
    void createsDiscoveryToolCalls_whenA2AGatewayToolDefinitionsPresent() {
      var agentContext = AgentContext.empty();
      var gatewayToolDefinitions =
          List.of(
              createGatewayToolDefinition(A2A_CLIENT_GATEWAY_TYPE, "a2a1"),
              createGatewayToolDefinition(A2A_CLIENT_GATEWAY_TYPE, "a2a2"));

      var result = handler.initiateToolDiscovery(agentContext, gatewayToolDefinitions);

      assertThat(result.agentContext().properties())
          .containsEntry(PROPERTY_A2A_CLIENTS, List.of("a2a1", "a2a2"));
      assertThat(result.toolDiscoveryToolCalls()).hasSize(2);
      assertThat(result.toolDiscoveryToolCalls())
          .satisfiesExactly(
              toolCall -> {
                assertThat(toolCall.id()).isEqualTo("A2A_fetchAgentCard_a2a1");
                assertThat(toolCall.name()).isEqualTo("a2a1");
                assertThat(toolCall.arguments())
                    .containsExactly(Map.entry("operation", "fetchAgentCard"));
              },
              toolCall -> {
                assertThat(toolCall.id()).isEqualTo("A2A_fetchAgentCard_a2a2");
                assertThat(toolCall.name()).isEqualTo("a2a2");
                assertThat(toolCall.arguments())
                    .containsExactly(Map.entry("operation", "fetchAgentCard"));
              });
    }

    @Test
    void filtersA2AGatewayTools_whenMixedGatewayToolDefinitions() {
      var agentContext = AgentContext.empty();
      var gatewayToolDefinitions =
          List.of(
              createGatewayToolDefinition(A2A_CLIENT_GATEWAY_TYPE, "a2a1"),
              createGatewayToolDefinition("other", "other1"),
              createGatewayToolDefinition(A2A_CLIENT_GATEWAY_TYPE, "a2a2"));

      var result = handler.initiateToolDiscovery(agentContext, gatewayToolDefinitions);

      assertThat(result.agentContext().properties())
          .containsEntry(PROPERTY_A2A_CLIENTS, List.of("a2a1", "a2a2"));
      assertThat(result.toolDiscoveryToolCalls()).hasSize(2);
    }
  }

  @Nested
  class ToolDiscoveryResultHandling {

    @ParameterizedTest
    @MethodSource("toolDiscoveryResultScenarios")
    void handlesToolDiscoveryResult_correctly(String toolCallId, boolean expected) {
      var toolCallResult =
          ToolCallResult.builder().id(toolCallId).name("a2a1").content("result").build();

      assertThat(handler.handlesToolDiscoveryResult(toolCallResult)).isEqualTo(expected);
    }

    @Test
    void returnsFalse_whenToolCallResultIdIsBlank() {
      var toolCallResult = ToolCallResult.builder().id("").name("a2a1").content("result").build();

      assertThat(handler.handlesToolDiscoveryResult(toolCallResult)).isFalse();
    }

    @Test
    void convertsA2ADiscoveryResults_toToolDefinitions() {
      var agentContext = AgentContext.empty();
      Map<String, Object> content = Map.of("title", "Agent 1", "version", 1);
      var toolDiscoveryResults =
          List.of(createToolCallResultWithContent("A2A_fetchAgentCard_a2a1", "a2a1", content));

      var result = handler.handleToolDiscoveryResults(agentContext, toolDiscoveryResults);

      Map<String, Object> expectedSchema =
          Map.of(
              "type",
              "object",
              "properties",
              Map.of(
                  "message",
                  Map.of(
                      "type",
                      "string",
                      "description",
                      "The instruction or the follow-up message to send to the agent.")),
              "required",
              List.of("message"));

      assertThat(result).hasSize(1);
      assertThat(result)
          .satisfiesExactly(
              toolDefinition -> {
                assertThat(toolDefinition.name()).isEqualTo("A2A_a2a1");
                // description is JSON string of the map; parse and compare
                var parsed = readDescriptionAsMap(toolDefinition.description());
                assertThat(parsed).isEqualTo(content);
                assertThat(toolDefinition.inputSchema()).isEqualTo(expectedSchema);
              });
    }

    @Test
    void handlesMultipleDiscoveryResults() {
      Map<String, Object> content1 = Map.of("title", "Desc 1");
      Map<String, Object> content2 = Map.of("title", "Desc 2");
      var agentContext = AgentContext.empty();
      var toolDiscoveryResults =
          List.of(
              createToolCallResultWithContent("A2A_fetchAgentCard_a2a1", "a2a1", content1),
              createToolCallResultWithContent("A2A_fetchAgentCard_a2a2", "a2a2", content2));

      var result = handler.handleToolDiscoveryResults(agentContext, toolDiscoveryResults);

      assertThat(result).hasSize(2);
      assertThat(result)
          .satisfiesExactly(
              td -> {
                assertThat(td.name()).isEqualTo("A2A_a2a1");
                var parsed = readDescriptionAsMap(td.description());
                assertThat(parsed).isEqualTo(content1);
              },
              td -> {
                assertThat(td.name()).isEqualTo("A2A_a2a2");
                var parsed = readDescriptionAsMap(td.description());
                assertThat(parsed).isEqualTo(content2);
              });
    }

    static Stream<Arguments> toolDiscoveryResultScenarios() {
      return Stream.of(
          arguments("A2A_fetchAgentCard_a2a1", true),
          arguments("A2A_fetchAgentCard_client", true),
          arguments("A2A_fetchAgentCard_client_1", true),
          arguments("regular_tool_call", false),
          arguments("A2A_different_prefix", false),
          arguments(null, false));
    }

    @Test
    void throwsException_whenDiscoveryResultContentIsNull() {
      var agentContext = AgentContext.empty();
      var toolDiscoveryResults =
          List.of(ToolCallResult.builder().id("A2A_fetchAgentCard_a2a1").name("a2a1").build());

      assertThatThrownBy(
              () -> handler.handleToolDiscoveryResults(agentContext, toolDiscoveryResults))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Tool call result content for A2A client tool discovery is null.");
    }

    @Test
    void throwsException_whenDiscoveryResultContentIsNotAMap() {
      var agentContext = AgentContext.empty();
      var toolDiscoveryResults =
          List.of(createToolCallResultWithContent("A2A_fetchAgentCard_a2a1", "a2a1", "not a map"));

      assertThatThrownBy(
              () -> handler.handleToolDiscoveryResults(agentContext, toolDiscoveryResults))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Tool call result content for A2A client tool discovery is not a map.");
    }
  }

  @Nested
  class ToolCallTransformation {

    @Test
    void transformsA2AToolCalls_toA2AClientOperations() {
      var agentContext = AgentContext.empty();
      var toolCalls =
          List.of(
              new ToolCall("call1", "A2A_a2a1", Map.of("message", "hello")),
              new ToolCall("call2", "regular_tool", Map.of("arg", "value")));

      var result = handler.transformToolCalls(agentContext, toolCalls);

      assertThat(result).hasSize(2);
      assertThat(result)
          .satisfiesExactly(
              toolCall -> {
                assertThat(toolCall.id()).isEqualTo("call1");
                assertThat(toolCall.name()).isEqualTo("a2a1");
                assertThat(toolCall.arguments()).containsKeys("operation", "params");
                assertThat(toolCall.arguments().get("operation")).isEqualTo("sendMessage");
                assertThat(toolCall.arguments().get("params"))
                    .isEqualTo(Map.of("message", "hello"));
              },
              toolCall -> {
                assertThat(toolCall.id()).isEqualTo("call2");
                assertThat(toolCall.name()).isEqualTo("regular_tool");
                assertThat(toolCall.arguments()).containsEntry("arg", "value");
              });
    }

    @Test
    void doesNotTransform_nonA2AToolCalls() {
      var agentContext = AgentContext.empty();
      var toolCalls =
          List.of(
              new ToolCall("call1", "regular_tool1", Map.of("arg1", "value1")),
              new ToolCall("call2", "regular_tool2", Map.of("arg2", "value2")));

      var result = handler.transformToolCalls(agentContext, toolCalls);

      assertThat(result).isEqualTo(toolCalls);
    }
  }

  @Nested
  class ToolCallResultTransformation {

    @Test
    void transformsA2AClientResults_toA2AToolCallResults() {
      var agentContext =
          AgentContext.empty().withProperty(PROPERTY_A2A_CLIENTS, List.of("a2a1", "a2a2"));
      var sendMessageResult =
          new A2AClientSendMessageResult(
              "rid", List.of(new TextContent("Agent response")), TaskState.COMPLETED);
      var toolCallResults =
          List.of(
              createToolCallResultWithContent("call1", "a2a1", sendMessageResult),
              createToolCallResult("call2", "regular_tool"));

      var result = handler.transformToolCallResults(agentContext, toolCallResults);

      assertThat(result).hasSize(2);
      assertThat(result)
          .satisfiesExactly(
              toolCallResult -> {
                assertThat(toolCallResult.id()).isEqualTo("call1");
                assertThat(toolCallResult.name()).isEqualTo("A2A_a2a1");
                assertThat(toolCallResult.content()).isEqualTo("Agent response");
              },
              toolCallResult -> {
                assertThat(toolCallResult.id()).isEqualTo("call2");
                assertThat(toolCallResult.name()).isEqualTo("regular_tool");
              });
    }

    @Test
    void retainsListOfContentBlocksIfResultIsNotASingleTextBlock() {
      var agentContext = AgentContext.empty().withProperty(PROPERTY_A2A_CLIENTS, List.of("a2a1"));
      var sendMessageResult =
          new A2AClientSendMessageResult(
              "rid",
              List.of(new TextContent("First content"), new TextContent("Second content")),
              TaskState.COMPLETED);
      var toolCallResults =
          List.of(createToolCallResultWithContent("call1", "a2a1", sendMessageResult));

      var result = handler.transformToolCallResults(agentContext, toolCallResults);

      assertThat(result).hasSize(1);
      assertThat(result.getFirst().content())
          .isEqualTo(List.of(new TextContent("First content"), new TextContent("Second content")));
    }

    @Test
    void preservesOriginalResult_whenNotA2AClient() {
      var agentContext = AgentContext.empty().withProperty(PROPERTY_A2A_CLIENTS, List.of("a2a1"));
      var toolCallResults = List.of(createToolCallResult("call1", "other_tool"));

      var result = handler.transformToolCallResults(agentContext, toolCallResults);

      assertThat(result).isEqualTo(toolCallResults);
    }

    @Test
    void handlesEmptyA2AClientsList() {
      var agentContext = AgentContext.empty();
      var toolCallResults = List.of(createToolCallResult("call1", "a2a1"));

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

  private Map<String, Object> readDescriptionAsMap(String descriptionJson) {
    try {
      return objectMapper.readValue(descriptionJson, STRING_OBJECT_MAP_TYPE_REFERENCE);
    } catch (Exception e) {
      throw new RuntimeException("Failed to parse tool description JSON", e);
    }
  }
}
