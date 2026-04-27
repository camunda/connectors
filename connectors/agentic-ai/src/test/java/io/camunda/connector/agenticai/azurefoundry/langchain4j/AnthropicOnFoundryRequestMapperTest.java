/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.azurefoundry.langchain4j;

import static org.assertj.core.api.Assertions.assertThat;

import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlockParam;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.jsonschema.JsonSchemaConverter;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureFoundryProviderConfiguration.AzureAiFoundryModel.AnthropicModel;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureFoundryProviderConfiguration.AzureAiFoundryModel.AnthropicModel.AnthropicModelParameters;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnthropicOnFoundryRequestMapperTest {

  private final AnthropicModel modelConfig =
      new AnthropicModel(
          "claude-3-5-sonnet-20241022", new AnthropicModelParameters(1024, 0.7, 0.9, 10));
  private final JsonSchemaConverter jsonSchemaConverter =
      new JsonSchemaConverter(new ObjectMapper());
  private final AnthropicOnFoundryRequestMapper mapper =
      new AnthropicOnFoundryRequestMapper(modelConfig, jsonSchemaConverter);

  @Test
  void passes_system_message_to_anthropic_system_field() {
    // given — mapper passes system text as a plain String to builder.system(String)
    var request =
        ChatRequest.builder()
            .messages(SystemMessage.from("You are a careful assistant."), UserMessage.from("hi"))
            .build();

    // when
    MessageCreateParams params = mapper.toMessageCreateParams(request);

    // then
    // The mapper calls builder.system(String), so the System sealed type is in isString() variant
    assertThat(params.system()).isPresent();
    MessageCreateParams.System system = params.system().get();
    assertThat(system.isString()).isTrue();
    assertThat(system.asString()).isEqualTo("You are a careful assistant.");
  }

  @Test
  void groups_tool_result_messages_into_single_user_message() {
    // given — two consecutive tool-result messages must be merged into one user-role MessageParam
    // containing two ToolResultBlockParam content blocks (Anthropic API requirement)
    var request =
        ChatRequest.builder()
            .messages(
                UserMessage.from("Get my order status"),
                AiMessage.builder()
                    .toolExecutionRequests(
                        List.of(
                            ToolExecutionRequest.builder()
                                .id("call_1")
                                .name("get_order")
                                .arguments("{}")
                                .build(),
                            ToolExecutionRequest.builder()
                                .id("call_2")
                                .name("get_user")
                                .arguments("{}")
                                .build()))
                    .build(),
                ToolExecutionResultMessage.from("call_1", "get_order", "{\"status\":\"shipped\"}"),
                ToolExecutionResultMessage.from("call_2", "get_user", "{\"name\":\"Alice\"}"))
            .build();

    // when
    MessageCreateParams params = mapper.toMessageCreateParams(request);

    // then
    List<MessageParam> messages = params.messages();
    // Expected order: user("Get my order status"), assistant(tool-use blocks), user(merged tool
    // results)
    assertThat(messages).hasSize(3);

    MessageParam mergedToolResults = messages.get(2);
    assertThat(mergedToolResults.role()).isEqualTo(MessageParam.Role.USER);
    assertThat(mergedToolResults.content().isBlockParams()).isTrue();

    List<ContentBlockParam> blocks = mergedToolResults.content().asBlockParams();
    assertThat(blocks).hasSize(2);

    // First block: tool result for call_1
    assertThat(blocks.get(0).isToolResult()).isTrue();
    ToolResultBlockParam result1 = blocks.get(0).asToolResult();
    assertThat(result1.toolUseId()).isEqualTo("call_1");

    // Second block: tool result for call_2
    assertThat(blocks.get(1).isToolResult()).isTrue();
    ToolResultBlockParam result2 = blocks.get(1).asToolResult();
    assertThat(result2.toolUseId()).isEqualTo("call_2");
  }

  @Test
  void translates_assistant_tool_calls_into_tool_use_blocks() {
    // given — an AiMessage with a tool execution request must produce an assistant-role
    // MessageParam whose content contains a ToolUseBlockParam with the right id, name, and parsed
    // input
    var request =
        ChatRequest.builder()
            .messages(
                UserMessage.from("Get the date"),
                AiMessage.builder()
                    .toolExecutionRequests(
                        List.of(
                            ToolExecutionRequest.builder()
                                .id("toolu_01")
                                .name("GetDateAndTime")
                                .arguments("{\"timezone\":\"UTC\"}")
                                .build()))
                    .build())
            .build();

    // when
    MessageCreateParams params = mapper.toMessageCreateParams(request);

    // then
    List<MessageParam> messages = params.messages();
    // Expected: user message, assistant message
    assertThat(messages).hasSize(2);

    MessageParam assistantMsg = messages.get(1);
    assertThat(assistantMsg.role()).isEqualTo(MessageParam.Role.ASSISTANT);
    assertThat(assistantMsg.content().isBlockParams()).isTrue();

    List<ContentBlockParam> blocks = assistantMsg.content().asBlockParams();
    // Expect exactly one ToolUseBlockParam (no text prefix since AiMessage has no text)
    assertThat(blocks).hasSize(1);
    assertThat(blocks.get(0).isToolUse()).isTrue();

    ToolUseBlockParam toolUse = blocks.get(0).asToolUse();
    assertThat(toolUse.id()).isEqualTo("toolu_01");
    assertThat(toolUse.name()).isEqualTo("GetDateAndTime");

    // Input is stored as additional properties on ToolUseBlockParam.Input
    assertThat(toolUse.input()._additionalProperties()).containsKey("timezone");
    assertThat(toolUse.input()._additionalProperties().get("timezone").toString()).contains("UTC");
  }

  @Test
  void default_max_tokens_when_neither_config_nor_request_sets_one() {
    // given — model with null parameters → should fall back to 1024
    var mapperNoParams =
        new AnthropicOnFoundryRequestMapper(
            new AnthropicModel("claude-sonnet-4-6", null), jsonSchemaConverter);

    var request = ChatRequest.builder().messages(UserMessage.from("hi")).build();

    // when
    MessageCreateParams params = mapperNoParams.toMessageCreateParams(request);

    // then
    assertThat(params.maxTokens()).isEqualTo(1024L);
  }

  @Test
  void chat_request_max_tokens_overrides_model_config() {
    // given — modelConfig has maxTokens=1024; ChatRequest overrides to 4096
    var request =
        ChatRequest.builder().messages(UserMessage.from("hi")).maxOutputTokens(4096).build();

    // when
    MessageCreateParams params = mapper.toMessageCreateParams(request);

    // then
    assertThat(params.maxTokens()).isEqualTo(4096L);
  }

  @Test
  void translates_tool_specifications_with_object_schema() {
    // given — a tool with a JsonObjectSchema that has required and optional string properties
    JsonObjectSchema schema =
        JsonObjectSchema.builder()
            .addStringProperty("city", "City name")
            .addStringProperty("country", "Optional country code")
            .required("city")
            .build();

    ToolSpecification spec =
        ToolSpecification.builder()
            .name("get_weather")
            .description("Get the weather for a city")
            .parameters(schema)
            .build();

    ChatRequest request =
        ChatRequest.builder()
            .messages(UserMessage.from("What's the weather?"))
            .toolSpecifications(spec)
            .build();

    // when
    MessageCreateParams params = mapper.toMessageCreateParams(request);

    // then — the tool must appear in the params with correctly populated schema
    assertThat(params.tools()).isPresent();
    assertThat(params.tools().get()).hasSize(1);

    assertThat(params.tools().get().get(0).isTool()).isTrue();
    Tool tool = params.tools().get().get(0).asTool();
    assertThat(tool.name()).isEqualTo("get_weather");
    assertThat(tool.description()).isPresent();
    assertThat(tool.description().get()).isEqualTo("Get the weather for a city");

    Tool.InputSchema inputSchema = tool.inputSchema();

    // properties must contain both "city" and "country"
    assertThat(inputSchema.properties()).isPresent();
    assertThat(inputSchema.properties().get()._additionalProperties())
        .containsKey("city")
        .containsKey("country");

    // required list must contain "city"
    assertThat(inputSchema.required()).isPresent();
    assertThat(inputSchema.required().get()).contains("city");
  }
}
