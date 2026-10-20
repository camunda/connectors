/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.otel;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics.TokenUsage;
import io.camunda.connector.agenticai.aiagent.model.chatmodel.ChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.StopReason;
import io.camunda.connector.agenticai.aiagent.model.message.SystemMessage;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.api.error.ConnectorException;
import io.opentelemetry.api.common.Attributes;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GenAiAttributeMapperTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  private static final ChatModelConfiguration ANTHROPIC =
      new ChatModelConfiguration() {
        @Override
        public String provider() {
          return "anthropic";
        }

        @Override
        public String model() {
          return "claude-x";
        }
      };

  @Test
  void errorTypeIsTheFirstCauseThatIsNoConnectorException() {
    final Throwable sdkFailure = new java.net.SocketTimeoutException("secret prompt text");
    final Throwable wrapped =
        new ConnectorException(
            "FAILED_MODEL_CALL",
            "wrapper message",
            new ConnectorException("INNER", "inner", sdkFailure));

    assertThat(GenAiAttributeMapper.errorTypeOf(wrapped, "FAILED_MODEL_CALL"))
        .isEqualTo("java.net.SocketTimeoutException")
        .doesNotContain("secret");
  }

  @Test
  void errorTypeOfConnectorExceptionsOnlyIsTheErrorCodeElseTheClass() {
    final Throwable coded = new ConnectorException("MAX_MODEL_CALLS_EXCEEDED", "limit");

    assertThat(GenAiAttributeMapper.errorTypeOf(coded, "MAX_MODEL_CALLS_EXCEEDED"))
        .isEqualTo("MAX_MODEL_CALLS_EXCEEDED");
    assertThat(GenAiAttributeMapper.errorTypeOf(coded, null))
        .isEqualTo("io.camunda.connector.api.error.ConnectorException");
  }

  @Test
  void errorTypeOfAnUnwrappedFailureIsItsCanonicalClassName() {
    assertThat(GenAiAttributeMapper.errorTypeOf(new IllegalStateException("x"), "IGNORED"))
        .isEqualTo("java.lang.IllegalStateException");
    assertThat(GenAiAttributeMapper.errorTypeOf(new Nested(), null))
        .isEqualTo(Nested.class.getCanonicalName());
    assertThat(new Object() {}.getClass().getCanonicalName()).isNull();
    assertThat(GenAiAttributeMapper.errorTypeOf(new RuntimeException() {}, null))
        .startsWith(GenAiAttributeMapperTest.class.getName() + "$");
  }

  private static final class Nested extends RuntimeException {}

  @Test
  void usageTotalIncludesCachedTokensAndReportsSubsets() {
    final Attributes attributes =
        GenAiAttributeMapper.usageAttributes(new TokenUsage(100, 50, 40, 10, 20));

    assertThat(attributes.get(GenAiAttributes.USAGE_INPUT_TOKENS)).isEqualTo(150L);
    assertThat(attributes.get(GenAiAttributes.USAGE_CACHE_READ_INPUT_TOKENS)).isEqualTo(40L);
    assertThat(attributes.get(GenAiAttributes.USAGE_CACHE_WRITE_INPUT_TOKENS)).isEqualTo(10L);
    assertThat(attributes.get(GenAiAttributes.USAGE_CACHE_CREATION_INPUT_TOKENS)).isEqualTo(10L);
    assertThat(attributes.get(GenAiAttributes.USAGE_OUTPUT_TOKENS)).isEqualTo(50L);
    assertThat(attributes.get(GenAiAttributes.USAGE_REASONING_OUTPUT_TOKENS)).isEqualTo(20L);
  }

  @Test
  void mapsStopReasonsAndPassesUnknownOnesThrough() {
    assertThat(GenAiAttributeMapper.finishReason(StopReason.STOP)).isEqualTo("stop");
    assertThat(GenAiAttributeMapper.finishReason(StopReason.LENGTH)).isEqualTo("length");
    assertThat(GenAiAttributeMapper.finishReason(StopReason.TOOL_USE)).isEqualTo("tool_call");
    assertThat(GenAiAttributeMapper.finishReason(StopReason.of("refusal"))).isEqualTo("refusal");
  }

  @Test
  void mapsProviderNames() {
    assertThat(GenAiAttributeMapper.providerName(ANTHROPIC)).isEqualTo("anthropic");
    assertThat(GenAiAttributeMapper.providerName(provider("bedrock"))).isEqualTo("aws.bedrock");
    assertThat(GenAiAttributeMapper.providerName(provider("mistral"))).isEqualTo("mistral");
    assertThat(GenAiAttributeMapper.providerName(provider("my-custom"))).isEqualTo("my-custom");
  }

  @Test
  void capturesNoContentUnlessEnabled() {
    final var messages = conversation();
    final var message = AssistantMessage.builder().content(List.of(text("answer"))).build();

    final Attributes request =
        GenAiAttributeMapper.chatRequestAttributes(ANTHROPIC, messages, tools(), false);
    final Attributes response =
        GenAiAttributeMapper.chatResponseAttributes(message, new TokenUsage(1, 1), false);
    final Attributes tool =
        GenAiAttributeMapper.toolContentAttributes(
            new ToolCall("c1", "search", Map.of("q", "x")),
            ToolCallResultContent.builder().id("c1").content(List.of(text("r"))).build(),
            false);

    assertThat(request.get(GenAiAttributes.REQUEST_MODEL)).isEqualTo("claude-x");
    assertThat(request.get(GenAiAttributes.INPUT_MESSAGES)).isNull();
    assertThat(request.get(GenAiAttributes.SYSTEM_INSTRUCTIONS)).isNull();
    assertThat(request.get(GenAiAttributes.TOOL_DEFINITIONS)).isNull();
    assertThat(response.get(GenAiAttributes.OUTPUT_MESSAGES)).isNull();
    assertThat(tool.isEmpty()).isTrue();
  }

  @Test
  void capturesContentAsSemanticConventionParts() throws Exception {
    final Attributes request =
        GenAiAttributeMapper.chatRequestAttributes(ANTHROPIC, conversation(), tools(), true);

    final JsonNode instructions = JSON.readTree(request.get(GenAiAttributes.SYSTEM_INSTRUCTIONS));
    assertThat(instructions.get(0).get("type").asText()).isEqualTo("text");
    assertThat(instructions.get(0).get("content").asText()).isEqualTo("be helpful");

    final JsonNode input = JSON.readTree(request.get(GenAiAttributes.INPUT_MESSAGES));
    assertThat(input).hasSize(3);
    assertThat(input.get(0).get("role").asText()).isEqualTo("user");
    assertThat(input.get(0).get("parts").get(0).get("content").asText()).isEqualTo("hi");
    final JsonNode toolCall = input.get(1).get("parts").get(0);
    assertThat(input.get(1).get("role").asText()).isEqualTo("assistant");
    assertThat(toolCall.get("type").asText()).isEqualTo("tool_call");
    assertThat(toolCall.get("name").asText()).isEqualTo("search");
    assertThat(toolCall.get("arguments").get("q").asText()).isEqualTo("x");
    final JsonNode toolResponse = input.get(2).get("parts").get(0);
    assertThat(input.get(2).get("role").asText()).isEqualTo("tool");
    assertThat(toolResponse.get("type").asText()).isEqualTo("tool_call_response");
    assertThat(toolResponse.get("id").asText()).isEqualTo("c1");
    assertThat(toolResponse.get("response").asText()).isEqualTo("found");

    final JsonNode definitions = JSON.readTree(request.get(GenAiAttributes.TOOL_DEFINITIONS));
    assertThat(definitions.get(0).get("name").asText()).isEqualTo("search");

    final var answer =
        AssistantMessage.builder().content(List.of(text("done"))).stopReason(StopReason.STOP);
    final Attributes response =
        GenAiAttributeMapper.chatResponseAttributes(answer.build(), new TokenUsage(1, 1), true);
    final JsonNode output = JSON.readTree(response.get(GenAiAttributes.OUTPUT_MESSAGES));
    assertThat(output.get(0).get("role").asText()).isEqualTo("assistant");
    assertThat(output.get(0).has("finish_reason")).isFalse();
  }

  @Test
  void responseIdPrefersTheEnvelopeIdFromTheMessageMetadata() {
    final var withMetadata =
        AssistantMessage.builder()
            .messageId("msg_1")
            .metadata(Map.of("openai", Map.of("responseId", "resp_1")))
            .build();
    final var withoutMetadata = AssistantMessage.builder().messageId("msg_1").build();
    final var withOtherMetadata =
        AssistantMessage.builder()
            .messageId("msg_1")
            .metadata(Map.of("openai", "not a map", "other", Map.of("responseId", 42)))
            .build();

    assertThat(response(withMetadata).get(GenAiAttributes.RESPONSE_ID)).isEqualTo("resp_1");
    assertThat(response(withoutMetadata).get(GenAiAttributes.RESPONSE_ID)).isEqualTo("msg_1");
    assertThat(response(withOtherMetadata).get(GenAiAttributes.RESPONSE_ID)).isEqualTo("msg_1");
  }

  private static Attributes response(AssistantMessage message) {
    return GenAiAttributeMapper.chatResponseAttributes(message, new TokenUsage(1, 1), false);
  }

  private static ChatModelConfiguration provider(String id) {
    return new ChatModelConfiguration() {
      @Override
      public String provider() {
        return id;
      }

      @Override
      public String model() {
        return "m";
      }
    };
  }

  private static TextContent text(String text) {
    return TextContent.textContent(text);
  }

  private static List<ToolDefinition> tools() {
    return List.of(
        ToolDefinition.builder()
            .name("search")
            .description("Search")
            .inputSchema(Map.of("type", "object"))
            .build());
  }

  private static List<Message> conversation() {
    return List.of(
        SystemMessage.builder().content(List.of(text("be helpful"))).build(),
        UserMessage.builder().content(List.of(text("hi"))).build(),
        AssistantMessage.builder()
            .toolCalls(List.of(new ToolCall("c1", "search", Map.of("q", "x"))))
            .build(),
        ToolCallResultMessage.builder()
            .results(
                List.of(
                    ToolCallResultContent.builder()
                        .id("c1")
                        .name("search")
                        .content(List.of(text("found")))
                        .build()))
            .build());
  }
}
