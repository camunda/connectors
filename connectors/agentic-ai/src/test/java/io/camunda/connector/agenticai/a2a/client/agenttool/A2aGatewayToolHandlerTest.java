/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.agenttool;

import static io.camunda.connector.agenticai.a2a.client.common.A2aConstants.PROPERTY_A2A_CLIENTS;
import static io.camunda.connector.agenticai.util.ObjectMapperConstants.STRING_OBJECT_MAP_TYPE_REFERENCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aArtifact;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aMessage;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aTask;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aTaskStatus;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.model.message.content.TextContent;
import io.camunda.connector.agenticai.model.tool.GatewayToolDefinition;
import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class A2aGatewayToolHandlerTest {

  public static final String A2A_CLIENT_GATEWAY_TYPE = "a2aClient";
  private final ObjectMapper objectMapper = new ObjectMapper();
  private A2aGatewayToolHandler handler;

  @BeforeEach
  void setUp() {
    handler = new A2aGatewayToolHandler(objectMapper);
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
    void returnsEmptyResult_whenNoA2aGatewayToolDefinitions() {
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
    void createsDiscoveryToolCalls_whenA2aGatewayToolDefinitionsPresent() {
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
    void filtersA2aGatewayTools_whenMixedGatewayToolDefinitions() {
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
  class AllToolDiscoveryResultsPresent {

    @Test
    void returnsTrue_whenAllA2aClientToolDiscoveryResultsPresent() {
      var agentContext =
          AgentContext.empty().withProperty(PROPERTY_A2A_CLIENTS, List.of("a2a1", "a2a2"));
      var toolCallResults =
          List.of(
              ToolCallResult.builder()
                  .id("A2A_fetchAgentCard_a2a1")
                  .name("a2a1")
                  .content("result1")
                  .build(),
              ToolCallResult.builder()
                  .id("A2A_fetchAgentCard_a2a2")
                  .name("a2a2")
                  .content("result2")
                  .build());

      assertThat(handler.allToolDiscoveryResultsPresent(agentContext, toolCallResults)).isTrue();
    }

    @Test
    void returnsFalse_whenSomeA2aClientToolDiscoveryResultsMissing() {
      var agentContext =
          AgentContext.empty().withProperty(PROPERTY_A2A_CLIENTS, List.of("a2a1", "a2a2"));
      var toolCallResults =
          List.of(
              ToolCallResult.builder()
                  .id("A2A_fetchAgentCard_a2a1")
                  .name("a2a1")
                  .content("result1")
                  .build());

      assertThat(handler.allToolDiscoveryResultsPresent(agentContext, toolCallResults)).isFalse();
    }

    @Test
    void returnsFalse_whenNoToolCallResultsProvided() {
      var agentContext =
          AgentContext.empty().withProperty(PROPERTY_A2A_CLIENTS, List.of("a2a1", "a2a2"));
      var toolCallResults = List.<ToolCallResult>of();

      assertThat(handler.allToolDiscoveryResultsPresent(agentContext, toolCallResults)).isFalse();
    }

    @Test
    void returnsTrue_whenNoA2aClientsConfigured() {
      var agentContext = AgentContext.empty();
      var toolCallResults = List.<ToolCallResult>of();

      assertThat(handler.allToolDiscoveryResultsPresent(agentContext, toolCallResults)).isTrue();
    }

    @Test
    void returnsTrue_whenEmptyA2aClientsList() {
      var agentContext = AgentContext.empty().withProperty(PROPERTY_A2A_CLIENTS, List.of());
      var toolCallResults = List.<ToolCallResult>of();

      assertThat(handler.allToolDiscoveryResultsPresent(agentContext, toolCallResults)).isTrue();
    }

    @Test
    void ignoresNonDiscoveryToolCallResults() {
      var agentContext = AgentContext.empty().withProperty(PROPERTY_A2A_CLIENTS, List.of("a2a1"));
      var toolCallResults =
          List.of(
              ToolCallResult.builder()
                  .id("A2A_fetchAgentCard_a2a1")
                  .name("a2a1")
                  .content("result1")
                  .build(),
              ToolCallResult.builder()
                  .id("regular_tool_call")
                  .name("regular_tool")
                  .content("result")
                  .build());

      assertThat(handler.allToolDiscoveryResultsPresent(agentContext, toolCallResults)).isTrue();
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
    void convertsA2aDiscoveryResults_toToolDefinitions() {
      var agentContext = AgentContext.empty();
      Map<String, Object> content = Map.of("title", "Agent 1", "version", 1);
      var toolDiscoveryResults =
          List.of(createToolCallResultWithContent("A2A_fetchAgentCard_a2a1", "a2a1", content));

      var result = handler.handleToolDiscoveryResults(agentContext, toolDiscoveryResults);

      assertThat(result).hasSize(1);
      assertThat(result)
          .satisfiesExactly(
              toolDefinition -> {
                assertThat(toolDefinition.name()).isEqualTo("A2A_a2a1");
                // description contains JSON string of the map; parse and compare
                var parsed = readDescriptionAsMap(toolDefinition.description());
                assertThat(parsed).isEqualTo(content);
                assertThat(toolDefinition.inputSchema())
                    .satisfies(
                        schema -> {
                          assertThat(schema).containsOnlyKeys("type", "properties", "required");
                          assertThat(schema).containsEntry("required", List.of("text"));
                          //noinspection unchecked
                          assertThat((Map<String, Object>) schema.get("properties"))
                              .containsOnlyKeys("text", "contextId", "taskId", "referenceTaskIds");
                        });
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
    void transformsA2aToolCalls_toA2aClientOperations() {
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
    void doesNotTransform_nonA2aToolCalls() {
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
    void transformsA2aClientResults_toA2aToolCallResults() {
      var agentContext =
          AgentContext.empty().withProperty(PROPERTY_A2A_CLIENTS, List.of("a2a1", "a2a2"));
      List<A2aArtifact> artifacts =
          List.of(
              A2aArtifact.builder()
                  .artifactId("artifact-1")
                  .contents(List.of(TextContent.textContent("Agent response as task")))
                  .build());
      var a2aTask =
          A2aTask.builder()
              .id("task-1")
              .status(A2aTaskStatus.builder().state(A2aTaskStatus.TaskState.COMPLETED).build())
              .contextId("context-1")
              .artifacts(artifacts)
              .build();
      var a2aMessage =
          A2aMessage.builder()
              .role(A2aMessage.Role.AGENT)
              .messageId("message-1")
              .contextId("context-1")
              .contents(List.of(TextContent.textContent("Agent response as message")))
              .build();
      var toolCallResults =
          List.of(
              createToolCallResultWithContent("call1", "a2a1", a2aTask),
              createToolCallResultWithContent("call2", "a2a2", a2aMessage),
              createToolCallResult("call3", "regular_tool"));

      var result = handler.transformToolCallResults(agentContext, toolCallResults);

      assertThat(result)
          .satisfiesExactly(
              toolCallResult -> {
                assertThat(toolCallResult.id()).isEqualTo("call1");
                assertThat(toolCallResult.name()).isEqualTo("A2A_a2a1");
                assertThat(toolCallResult.content()).isInstanceOf(A2aTask.class);
                assertThat(toolCallResult.content()).isEqualTo(a2aTask);
              },
              toolCallResult -> {
                assertThat(toolCallResult.id()).isEqualTo("call2");
                assertThat(toolCallResult.name()).isEqualTo("A2A_a2a2");
                assertThat(toolCallResult.content()).isInstanceOf(A2aMessage.class);
                assertThat(toolCallResult.content()).isEqualTo(a2aMessage);
              },
              toolCallResult -> {
                assertThat(toolCallResult.id()).isEqualTo("call3");
                assertThat(toolCallResult.name()).isEqualTo("regular_tool");
              });
    }

    @Test
    void preservesOriginalResult_whenNotA2aClient() {
      var agentContext = AgentContext.empty().withProperty(PROPERTY_A2A_CLIENTS, List.of("a2a1"));
      var toolCallResults = List.of(createToolCallResult("call1", "other_tool"));

      var result = handler.transformToolCallResults(agentContext, toolCallResults);

      assertThat(result).isEqualTo(toolCallResults);
    }

    @Test
    void handlesEmptyA2aClientsList() {
      var agentContext = AgentContext.empty();
      var toolCallResults = List.of(createToolCallResult("call1", "a2a1"));

      var result = handler.transformToolCallResults(agentContext, toolCallResults);

      assertThat(result).isEqualTo(toolCallResults);
    }
  }

  @SuppressWarnings("unchecked")
  @Nested
  class ResourceLoading {

    @Test
    void shouldThrowIllegalStateExceptionWhenResourceLoadingFails() throws IOException {
      ObjectMapper mockMapper = mock(ObjectMapper.class);
      when(mockMapper.readValue(any(InputStream.class), any(TypeReference.class)))
          .thenThrow(new IOException("Simulated resource loading failure"));

      assertThatThrownBy(() -> new A2aGatewayToolHandler(mockMapper))
          .isInstanceOf(IllegalStateException.class)
          .hasCauseInstanceOf(IOException.class)
          .cause()
          .hasMessageContaining("Simulated resource loading failure");
    }

    @Test
    void shouldThrowIllegalStateExceptionWhenJsonIsInvalid() throws IOException {
      ObjectMapper mockMapper = mock(ObjectMapper.class);
      when(mockMapper.readValue(any(InputStream.class), any(TypeReference.class)))
          .thenThrow(new JsonParseException(null, "Invalid JSON"));

      assertThatThrownBy(() -> new A2aGatewayToolHandler(mockMapper))
          .isInstanceOf(IllegalStateException.class)
          .hasCauseInstanceOf(JsonParseException.class)
          .cause()
          .hasMessageContaining("Invalid JSON");
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

  private Map<String, Object> readDescriptionAsMap(String description) {
    try {
      assertThat(description).startsWith("This tool allows interaction");
      final var startOfJson = description.indexOf("\n") + 1;
      final var descriptionJson = description.substring(startOfJson).trim();
      return objectMapper.readValue(descriptionJson, STRING_OBJECT_MAP_TYPE_REFERENCE);
    } catch (Exception e) {
      throw new RuntimeException("Failed to parse tool description JSON", e);
    }
  }
}
