/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes;
import io.camunda.connector.agenticai.aiagent.memory.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.SystemMessage;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.request.AgentTaskResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.JsonResponseFormatConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.TextResponseFormatConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AwsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v2.BedrockConverseChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.BedrockConverseChatModelConfiguration.BedrockConverseConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.BedrockConverseChatModelConfiguration.BedrockConverseModel;
import io.camunda.connector.agenticai.aiagent.model.request.v2.BedrockConverseChatModelConfiguration.BedrockConverseModel.BedrockConverseModelParameters;
import io.camunda.connector.agenticai.aiagent.model.request.v2.BedrockConverseChatModelConfiguration.BedrockConverseModel.BedrockConversePromptCaching;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.api.error.ConnectorException;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.OutputFormatType;

class BedrockConverseRequestConverterTest {

  private static final String MODEL_ID = "us.amazon.nova-2-lite-v1:0";
  private static final String REGION = "eu-central-1";

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final BedrockConverseContentConverter contentConverter =
      new BedrockConverseContentConverter(objectMapper);
  private final BedrockConverseRequestConverter converter =
      new BedrockConverseRequestConverter(contentConverter, objectMapper);

  private static BedrockConverseChatModelConfiguration model(
      @Nullable BedrockConverseModelParameters parameters) {
    return new BedrockConverseChatModelConfiguration(
        new BedrockConverseConnection(
            REGION,
            null,
            new AwsAuthentication.AwsDefaultCredentialsChainAuthentication(),
            null,
            null,
            null,
            null,
            new BedrockConverseModel(MODEL_ID, parameters)));
  }

  /** Builds a model with only the given headers/query/body properties set on the connection. */
  private static BedrockConverseChatModelConfiguration connectionOverridesModel(
      @Nullable Map<String, String> headers,
      @Nullable Map<String, String> queryParameters,
      @Nullable Map<String, Object> bodyProperties) {
    return new BedrockConverseChatModelConfiguration(
        new BedrockConverseConnection(
            REGION,
            null,
            new AwsAuthentication.AwsDefaultCredentialsChainAuthentication(),
            headers,
            queryParameters,
            bodyProperties,
            null,
            new BedrockConverseModel(MODEL_ID, null)));
  }

  private static BedrockConverseModelParameters promptCachingParams(@Nullable Boolean enabled) {
    final var promptCaching = enabled == null ? null : new BedrockConversePromptCaching(enabled);
    return new BedrockConverseModelParameters(promptCaching, null, null, null);
  }

  @Nested
  class InferenceConfig {

    @Test
    void mapsMaxTokensTemperatureAndTopPWhenSet() {
      final var parameters = new BedrockConverseModelParameters(null, 2048, 0.5, 0.9);
      final var snapshot = new ConversationSnapshot(List.of(), List.of());

      final var request = converter.toConverseStreamRequest(model(parameters), null, snapshot);

      assertThat(request.inferenceConfig()).isNotNull();
      assertThat(request.inferenceConfig().maxTokens()).isEqualTo(2048);
      assertThat(request.inferenceConfig().temperature()).isEqualTo(0.5f);
      assertThat(request.inferenceConfig().topP()).isEqualTo(0.9f);
    }

    @Test
    void omitsInferenceConfigWhenParametersUnset() {
      final var snapshot = new ConversationSnapshot(List.of(), List.of());

      final var request = converter.toConverseStreamRequest(model(null), null, snapshot);

      assertThat(request.inferenceConfig()).isNull();
    }

    @Test
    void appliesMaxTokensIndependentlyOfTemperatureAndTopP() {
      final var parameters = new BedrockConverseModelParameters(null, 100, null, null);
      final var snapshot = new ConversationSnapshot(List.of(), List.of());

      final var request = converter.toConverseStreamRequest(model(parameters), null, snapshot);

      assertThat(request.inferenceConfig().maxTokens()).isEqualTo(100);
      assertThat(request.inferenceConfig().temperature()).isNull();
      assertThat(request.inferenceConfig().topP()).isNull();
    }

    @Test
    void appliesTemperatureIndependentlyOfMaxTokensAndTopP() {
      final var parameters = new BedrockConverseModelParameters(null, null, 0.3, null);
      final var snapshot = new ConversationSnapshot(List.of(), List.of());

      final var request = converter.toConverseStreamRequest(model(parameters), null, snapshot);

      assertThat(request.inferenceConfig().maxTokens()).isNull();
      assertThat(request.inferenceConfig().temperature()).isEqualTo(0.3f);
      assertThat(request.inferenceConfig().topP()).isNull();
    }

    @Test
    void appliesTopPIndependentlyOfMaxTokensAndTemperature() {
      final var parameters = new BedrockConverseModelParameters(null, null, null, 0.8);
      final var snapshot = new ConversationSnapshot(List.of(), List.of());

      final var request = converter.toConverseStreamRequest(model(parameters), null, snapshot);

      assertThat(request.inferenceConfig().maxTokens()).isNull();
      assertThat(request.inferenceConfig().temperature()).isNull();
      assertThat(request.inferenceConfig().topP()).isEqualTo(0.8f);
    }
  }

  @Nested
  class SystemPrompt {

    @Test
    void mapsLeadingSystemMessageToTopLevelSystemAndRemainingToMessages() {
      final var snapshot =
          new ConversationSnapshot(
              List.of(
                  SystemMessage.builder().content(List.of(TextContent.textContent("sys"))).build(),
                  UserMessage.builder().content(List.of(TextContent.textContent("hi"))).build()),
              List.of());

      final var request = converter.toConverseStreamRequest(model(null), null, snapshot);

      assertThat(request.system()).hasSize(1);
      assertThat(request.system().get(0).text()).isEqualTo("sys");

      assertThat(request.messages()).hasSize(1);
      assertThat(request.messages().get(0).role()).isEqualTo(ConversationRole.USER);
      assertThat(request.messages().get(0).content().get(0).text()).isEqualTo("hi");
    }

    @Test
    void omitsSystemWhenNoLeadingSystemMessage() {
      final var snapshot =
          new ConversationSnapshot(
              List.of(
                  UserMessage.builder().content(List.of(TextContent.textContent("hi"))).build()),
              List.of());

      final var request = converter.toConverseStreamRequest(model(null), null, snapshot);

      assertThat(request.hasSystem()).isFalse();
    }

    @Test
    void omitsSystemWhenLeadingSystemMessageHasNoTextContent() {
      final var snapshot =
          new ConversationSnapshot(
              List.of(SystemMessage.builder().content(List.of()).build()), List.of());

      final var request = converter.toConverseStreamRequest(model(null), null, snapshot);

      assertThat(request.hasSystem()).isFalse();
    }

    @Test
    void mapsEachTextContentBlockToItsOwnSystemContentBlock() {
      final var snapshot =
          new ConversationSnapshot(
              List.of(
                  SystemMessage.builder()
                      .content(
                          List.of(
                              TextContent.textContent("sys one"),
                              TextContent.textContent("sys two")))
                      .build()),
              List.of());

      final var request = converter.toConverseStreamRequest(model(null), null, snapshot);

      assertThat(request.system()).hasSize(2);
      assertThat(request.system().get(0).text()).isEqualTo("sys one");
      assertThat(request.system().get(1).text()).isEqualTo("sys two");
    }

    @Test
    void throwsOnNonTextContentInSystemMessage() {
      final var snapshot =
          new ConversationSnapshot(
              List.of(
                  SystemMessage.builder()
                      .content(List.of(ObjectContent.objectContent(Map.of("k", "v"))))
                      .build()),
              List.of());

      assertThatThrownBy(() -> converter.toConverseStreamRequest(model(null), null, snapshot))
          .isInstanceOf(ConnectorException.class)
          .extracting(e -> ((ConnectorException) e).getErrorCode())
          .isEqualTo(AgentErrorCodes.ERROR_CODE_UNSUPPORTED_MODEL_CONFIGURATION);
    }
  }

  @Nested
  class Messages {

    @Test
    void mapsUserAssistantToolCallsAndToolResultMessages() {
      final var snapshot =
          new ConversationSnapshot(
              List.of(
                  UserMessage.builder()
                      .content(List.of(TextContent.textContent("please call the tool")))
                      .build(),
                  AssistantMessage.builder()
                      .toolCalls(
                          List.of(
                              ToolCall.builder()
                                  .id("id")
                                  .name("name")
                                  .arguments(Map.of("a", 5))
                                  .build()))
                      .build(),
                  ToolCallResultMessage.builder()
                      .results(
                          List.of(
                              ToolCallResultContent.builder()
                                  .id("id")
                                  .name("name")
                                  .content(List.of(TextContent.textContent("result")))
                                  .build()))
                      .build()),
              List.of());

      final var request = converter.toConverseStreamRequest(model(null), null, snapshot);

      assertThat(request.messages()).hasSize(3);

      final var userMessage = request.messages().get(0);
      assertThat(userMessage.role()).isEqualTo(ConversationRole.USER);
      assertThat(userMessage.content().get(0).text()).isEqualTo("please call the tool");

      final var assistantMessage = request.messages().get(1);
      assertThat(assistantMessage.role()).isEqualTo(ConversationRole.ASSISTANT);
      final var toolUse = assistantMessage.content().get(0).toolUse();
      assertThat(toolUse.toolUseId()).isEqualTo("id");
      assertThat(toolUse.name()).isEqualTo("name");
      assertThat(toolUse.input().asMap().get("a").asNumber().intValue()).isEqualTo(5);

      final var toolResultMessage = request.messages().get(2);
      assertThat(toolResultMessage.role()).isEqualTo(ConversationRole.USER);
      final var toolResult = toolResultMessage.content().get(0).toolResult();
      assertThat(toolResult.toolUseId()).isEqualTo("id");
      assertThat(toolResult.content().get(0).text()).isEqualTo("result");
    }

    @Test
    void mapsMultipleToolCallsOnASingleAssistantMessage() {
      final var snapshot =
          new ConversationSnapshot(
              List.of(
                  AssistantMessage.builder()
                      .content(List.of(TextContent.textContent("working")))
                      .toolCalls(
                          List.of(
                              ToolCall.builder()
                                  .id("id1")
                                  .name("first")
                                  .arguments(Map.of())
                                  .build(),
                              ToolCall.builder()
                                  .id("id2")
                                  .name("second")
                                  .arguments(Map.of())
                                  .build()))
                      .build()),
              List.of());

      final var request = converter.toConverseStreamRequest(model(null), null, snapshot);

      final var content = request.messages().get(0).content();
      assertThat(content).hasSize(3);
      assertThat(content.get(0).text()).isEqualTo("working");
      assertThat(content.get(1).toolUse().toolUseId()).isEqualTo("id1");
      assertThat(content.get(2).toolUse().toolUseId()).isEqualTo("id2");
    }

    @Test
    void replaysBedrockToolUseTypeMetadataOnToolCall() {
      final var snapshot =
          new ConversationSnapshot(
              List.of(
                  AssistantMessage.builder()
                      .toolCalls(
                          List.of(
                              ToolCall.builder()
                                  .id("id")
                                  .name("code_execution")
                                  .arguments(Map.of())
                                  .metadata(Map.of("bedrock", Map.of("type", "server_tool_use")))
                                  .build()))
                      .build()),
              List.of());

      final var request = converter.toConverseStreamRequest(model(null), null, snapshot);

      final var toolUse = request.messages().get(0).content().get(0).toolUse();
      assertThat(toolUse.typeAsString()).isEqualTo("server_tool_use");
    }
  }

  @Nested
  class ToolConfig {

    @Test
    void mapsToolDefinitionsToToolConfig() {
      final Map<String, Object> schema =
          Map.of(
              "type",
              "object",
              "properties",
              Map.of("quantity", Map.of("type", "integer")),
              "required",
              List.of("quantity"));
      final var snapshot =
          new ConversationSnapshot(
              List.of(
                  UserMessage.builder().content(List.of(TextContent.textContent("hi"))).build()),
              List.of(
                  ToolDefinition.builder()
                      .name("SuperfluxProduct")
                      .description("desc")
                      .inputSchema(schema)
                      .build()));

      final var request = converter.toConverseStreamRequest(model(null), null, snapshot);

      assertThat(request.toolConfig()).isNotNull();
      assertThat(request.toolConfig().tools()).hasSize(1);

      final var toolSpec = request.toolConfig().tools().get(0).toolSpec();
      assertThat(toolSpec.name()).isEqualTo("SuperfluxProduct");
      assertThat(toolSpec.description()).isEqualTo("desc");

      final var schemaDoc = toolSpec.inputSchema().json();
      assertThat(schemaDoc.asMap().get("type").asString()).isEqualTo("object");
      assertThat(
              schemaDoc
                  .asMap()
                  .get("properties")
                  .asMap()
                  .get("quantity")
                  .asMap()
                  .get("type")
                  .asString())
          .isEqualTo("integer");
      assertThat(schemaDoc.asMap().get("required").asList())
          .extracting(software.amazon.awssdk.core.document.Document::asString)
          .containsExactly("quantity");
    }

    @Test
    void omitsToolConfigWhenNoToolDefinitions() {
      final var snapshot = new ConversationSnapshot(List.of(), List.of());

      final var request = converter.toConverseStreamRequest(model(null), null, snapshot);

      assertThat(request.toolConfig()).isNull();
    }
  }

  @Nested
  class OutputConfig {

    @Test
    void configuresStructuredOutputFromJsonSchema() {
      final Map<String, Object> schema =
          Map.of("type", "object", "properties", Map.of("answer", Map.of("type", "string")));
      final var response =
          new AgentTaskResponseConfiguration(
              new JsonResponseFormatConfiguration(schema, "Answer"), null);
      final var snapshot = new ConversationSnapshot(List.of(), List.of());

      final var request = converter.toConverseStreamRequest(model(null), response, snapshot);

      assertThat(request.outputConfig()).isNotNull();
      final var textFormat = request.outputConfig().textFormat();
      assertThat(textFormat.type()).isEqualTo(OutputFormatType.JSON_SCHEMA);

      final var jsonSchema = textFormat.structure().jsonSchema();
      assertThat(jsonSchema.name()).isEqualTo("Answer");

      final var schemaNode = readSchema(jsonSchema.schema());
      assertThat(schemaNode.path("type").asText()).isEqualTo("object");
      assertThat(schemaNode.path("properties").path("answer").path("type").asText())
          .isEqualTo("string");
    }

    @Test
    void jsonResponseFormatWithoutSchemaEmitsNoOutputConfig() {
      final var response =
          new AgentTaskResponseConfiguration(new JsonResponseFormatConfiguration(null, null), null);
      final var snapshot = new ConversationSnapshot(List.of(), List.of());

      final var request = converter.toConverseStreamRequest(model(null), response, snapshot);

      assertThat(request.outputConfig()).isNull();
    }

    @Test
    void jsonResponseFormatWithEmptySchemaEmitsNoOutputConfig() {
      final var response =
          new AgentTaskResponseConfiguration(
              new JsonResponseFormatConfiguration(Map.of(), null), null);
      final var snapshot = new ConversationSnapshot(List.of(), List.of());

      final var request = converter.toConverseStreamRequest(model(null), response, snapshot);

      assertThat(request.outputConfig()).isNull();
    }

    @Test
    void textResponseFormatEmitsNoOutputConfig() {
      final var response =
          new AgentTaskResponseConfiguration(new TextResponseFormatConfiguration(true), null);
      final var snapshot = new ConversationSnapshot(List.of(), List.of());

      final var request = converter.toConverseStreamRequest(model(null), response, snapshot);

      assertThat(request.outputConfig()).isNull();
    }

    @Test
    void nullResponseConfigurationEmitsNoOutputConfig() {
      final var snapshot = new ConversationSnapshot(List.of(), List.of());

      final var request = converter.toConverseStreamRequest(model(null), null, snapshot);

      assertThat(request.outputConfig()).isNull();
    }

    private com.fasterxml.jackson.databind.JsonNode readSchema(String schemaJson) {
      try {
        return objectMapper.readTree(schemaJson);
      } catch (Exception e) {
        throw new AssertionError(e);
      }
    }
  }

  @Nested
  class BodyProperties {

    @Test
    void bodyPropertiesMapToAdditionalModelRequestFields() {
      final var config =
          connectionOverridesModel(null, null, Map.of("thinking", Map.of("type", "enabled")));
      final var snapshot = new ConversationSnapshot(List.of(), List.of());

      final var request = converter.toConverseStreamRequest(config, null, snapshot);

      final var fields = request.additionalModelRequestFields();
      assertThat(fields).isNotNull();
      assertThat(fields.asMap().get("thinking").asMap().get("type").asString())
          .isEqualTo("enabled");
    }

    @Test
    void omitsAdditionalModelRequestFieldsWhenBodyPropertiesUnset() {
      final var snapshot = new ConversationSnapshot(List.of(), List.of());

      final var request = converter.toConverseStreamRequest(model(null), null, snapshot);

      assertThat(request.additionalModelRequestFields()).isNull();
    }
  }

  @Nested
  class OverrideConfiguration {

    @Test
    void headersReachOverrideConfiguration() {
      final var config =
          connectionOverridesModel(Map.of("X-Custom-Header", "custom-value"), null, null);

      final var request =
          converter.toConverseStreamRequest(
              config, null, new ConversationSnapshot(List.of(), List.of()));

      assertThat(request.overrideConfiguration()).isPresent();
      assertThat(request.overrideConfiguration().get().headers())
          .containsEntry("X-Custom-Header", List.of("custom-value"));
    }

    @Test
    void queryParametersReachOverrideConfiguration() {
      final var config = connectionOverridesModel(null, Map.of("api-version", "2026-01-01"), null);

      final var request =
          converter.toConverseStreamRequest(
              config, null, new ConversationSnapshot(List.of(), List.of()));

      assertThat(request.overrideConfiguration()).isPresent();
      assertThat(request.overrideConfiguration().get().rawQueryParameters())
          .containsEntry("api-version", List.of("2026-01-01"));
    }

    @Test
    void noOverrideConfigurationWhenNeitherHeadersNorQueryParametersPresent() {
      final var request =
          converter.toConverseStreamRequest(
              model(null), null, new ConversationSnapshot(List.of(), List.of()));

      assertThat(request.overrideConfiguration()).isEmpty();
    }
  }

  @Nested
  class PromptCachingPlacement {

    @Test
    void systemPresentPlacesCachePointAtEndOfSystemAndLastMessageOnlyNotTools() {
      final var parameters = promptCachingParams(true);
      final var tools =
          List.of(
              ToolDefinition.builder()
                  .name("getWeather")
                  .description("desc")
                  .inputSchema(Map.of("type", "object"))
                  .build());
      final var snapshot =
          new ConversationSnapshot(
              List.of(
                  SystemMessage.builder().content(List.of(TextContent.textContent("sys"))).build(),
                  UserMessage.builder().content(List.of(TextContent.textContent("hi"))).build()),
              tools);

      final var request = converter.toConverseStreamRequest(model(parameters), null, snapshot);

      // system[] ends with a cachePoint
      assertThat(request.system()).hasSize(2);
      assertThat(request.system().get(1).cachePoint()).isNotNull();
      assertThat(request.system().get(1).cachePoint().ttl()).isNull();

      // tools[] carries no cachePoint at all
      assertThat(request.toolConfig().tools()).hasSize(1);
      assertThat(request.toolConfig().tools().get(0).cachePoint()).isNull();

      // last message's content ends with a cachePoint
      final var lastMessageContent = request.messages().getLast().content();
      final var lastBlock = lastMessageContent.get(lastMessageContent.size() - 1);
      assertThat(lastBlock.cachePoint()).isNotNull();
      assertThat(lastBlock.cachePoint().ttl()).isNull();
    }

    @Test
    void noSystemButToolsPresentPlacesCachePointAtEndOfTools() {
      final var parameters = promptCachingParams(true);
      final var tools =
          List.of(
              ToolDefinition.builder()
                  .name("getWeather")
                  .description("desc")
                  .inputSchema(Map.of("type", "object"))
                  .build());
      final var snapshot =
          new ConversationSnapshot(
              List.of(
                  UserMessage.builder().content(List.of(TextContent.textContent("hi"))).build()),
              tools);

      final var request = converter.toConverseStreamRequest(model(parameters), null, snapshot);

      assertThat(request.hasSystem()).isFalse();

      assertThat(request.toolConfig().tools()).hasSize(2);
      final var lastTool = request.toolConfig().tools().get(1);
      assertThat(lastTool.cachePoint()).isNotNull();
      assertThat(lastTool.cachePoint().ttl()).isNull();

      final var lastMessageContent = request.messages().getLast().content();
      final var lastBlock = lastMessageContent.get(lastMessageContent.size() - 1);
      assertThat(lastBlock.cachePoint()).isNotNull();
      assertThat(lastBlock.cachePoint().ttl()).isNull();
    }

    @Test
    void neitherSystemNorToolsPlacesNoCachePointInEitherButStillAtLastMessage() {
      final var parameters = promptCachingParams(true);
      final var snapshot =
          new ConversationSnapshot(
              List.of(
                  UserMessage.builder().content(List.of(TextContent.textContent("hi"))).build()),
              List.of());

      final var request = converter.toConverseStreamRequest(model(parameters), null, snapshot);

      assertThat(request.hasSystem()).isFalse();
      assertThat(request.toolConfig()).isNull();

      final var lastMessageContent = request.messages().getLast().content();
      final var lastBlock = lastMessageContent.get(lastMessageContent.size() - 1);
      assertThat(lastBlock.cachePoint()).isNotNull();
      assertThat(lastBlock.cachePoint().ttl()).isNull();
    }

    @Test
    void noMessagesEmitsNoCachePointAtAllEvenWhenSystemPresent() {
      final var parameters = promptCachingParams(true);
      final var snapshot =
          new ConversationSnapshot(
              List.of(
                  SystemMessage.builder().content(List.of(TextContent.textContent("sys"))).build()),
              List.of());

      final var request = converter.toConverseStreamRequest(model(parameters), null, snapshot);

      assertThat(request.system()).hasSize(2);
      assertThat(request.system().get(1).cachePoint()).isNotNull();
      assertThat(request.messages()).isEmpty();
    }

    @Test
    void cachingDisabledOrUnsetOmitsCachePointEverywhere() {
      final var tools =
          List.of(
              ToolDefinition.builder()
                  .name("getWeather")
                  .description("desc")
                  .inputSchema(Map.of("type", "object"))
                  .build());

      for (final Boolean flag : new Boolean[] {null, Boolean.FALSE}) {
        final var parameters =
            flag == null
                ? null
                : new BedrockConverseModelParameters(
                    new BedrockConversePromptCaching(flag), null, null, null);
        final var snapshot =
            new ConversationSnapshot(
                List.of(
                    SystemMessage.builder()
                        .content(List.of(TextContent.textContent("sys")))
                        .build(),
                    UserMessage.builder().content(List.of(TextContent.textContent("hi"))).build()),
                tools);

        final var request = converter.toConverseStreamRequest(model(parameters), null, snapshot);

        assertThat(request.system()).as("system, flag=%s", flag).hasSize(1);
        assertThat(request.toolConfig().tools()).as("tools, flag=%s", flag).hasSize(1);
        final var lastMessageContent = request.messages().getLast().content();
        assertThat(lastMessageContent.stream().noneMatch(block -> block.cachePoint() != null))
            .as("no cachePoint in last message, flag=%s", flag)
            .isTrue();
      }
    }
  }
}
