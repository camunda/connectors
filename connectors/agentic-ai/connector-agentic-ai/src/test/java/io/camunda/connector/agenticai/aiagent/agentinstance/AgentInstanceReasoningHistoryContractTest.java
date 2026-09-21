/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agentinstance;

import static io.camunda.connector.agenticai.aiagent.chatmodel.provider.gemini.GeminiContentConverter.THOUGHT_SIGNATURE_METADATA_KEY;
import static org.assertj.core.api.Assertions.assertThat;

import com.anthropic.core.ObjectMappers;
import com.anthropic.models.messages.Message;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.FinishReason;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.openai.models.responses.Response;
import io.camunda.client.api.command.AgentInstanceHistoryContent;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.AnthropicMessageResponseConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.bedrock.BedrockConverseResponseConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.gemini.GeminiContentResponseConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.responses.OpenAiResponsesResponseConverter;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.ReasoningContent;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ReasoningContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ReasoningTextBlock;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;

class AgentInstanceReasoningHistoryContractTest {

  private static final Duration EXECUTION_TIME = Duration.ofMillis(42);
  private static final byte[] THOUGHT_SIGNATURE = "signature".getBytes(StandardCharsets.UTF_8);

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final AgentInstanceHistoryMapper historyMapper =
      new AgentInstanceHistoryMapper(Mockito.mock(GatewayToolHandlerRegistry.class));

  @Test
  void mapsAnthropicReadableReasoningAndReplayPayloadToAgentInstanceHistory() {
    final var assistantMessage =
        new AnthropicMessageResponseConverter(objectMapper)
            .toResult(
                anthropicMessage(
                    """
                    {
                      "id": "msg_1",
                      "model": "claude-sonnet-4-6",
                      "role": "assistant",
                      "type": "message",
                      "content": [
                        {
                          "type": "thinking",
                          "thinking": "Let me think it through",
                          "signature": "sig-123"
                        },
                        {"type": "text", "text": "the answer"}
                      ],
                      "stop_reason": "end_turn",
                      "usage": {"input_tokens": 1, "output_tokens": 1}
                    }
                    """),
                EXECUTION_TIME)
            .assistantMessage();

    assertReasoningHistory(
        assistantMessage,
        "Let me think it through",
        Map.of("type", "thinking", "signature", "sig-123"));
    assertTrailingTextHistory(assistantMessage, "the answer");
  }

  @Test
  void mapsAnthropicRedactedReasoningToOpaqueAgentInstanceHistory() {
    final Map<String, Object> payload =
        Map.of("type", "redacted_thinking", "data", "encrypted-blob");
    final var assistantMessage =
        new AnthropicMessageResponseConverter(objectMapper)
            .toResult(
                anthropicMessage(
                    """
                    {
                      "id": "msg_1",
                      "model": "claude-sonnet-4-6",
                      "role": "assistant",
                      "type": "message",
                      "content": [
                        {"type": "redacted_thinking", "data": "encrypted-blob"}
                      ],
                      "stop_reason": "end_turn",
                      "usage": {"input_tokens": 1, "output_tokens": 1}
                    }
                    """),
                EXECUTION_TIME)
            .assistantMessage();

    assertReasoningHistory(assistantMessage, null, payload);
  }

  @Test
  void mapsBedrockReadableReasoningAndSignatureToAgentInstanceHistory() {
    final var assistantMessage =
        new BedrockConverseResponseConverter()
            .toResult(
                bedrockResponse(
                    ContentBlock.fromReasoningContent(
                        ReasoningContentBlock.fromReasoningText(
                            ReasoningTextBlock.builder()
                                .text("Let me think it through")
                                .signature("sig-123")
                                .build())),
                    ContentBlock.fromText("the answer")),
                EXECUTION_TIME)
            .assistantMessage();

    final var payload = reasoningPayload(assistantMessage);
    assertThat(payload)
        .extractingByKey("reasoningText", InstanceOfAssertFactories.MAP)
        .containsEntry("signature", "sig-123")
        .doesNotContainKey("text");
    assertReasoningHistory(assistantMessage, "Let me think it through", payload);
    assertTrailingTextHistory(assistantMessage, "the answer");
  }

  @Test
  void mapsBedrockRedactedReasoningToOpaqueAgentInstanceHistory() {
    final var assistantMessage =
        new BedrockConverseResponseConverter()
            .toResult(
                bedrockResponse(
                    ContentBlock.fromReasoningContent(
                        ReasoningContentBlock.fromRedactedContent(
                            SdkBytes.fromUtf8String("opaque-encrypted-reasoning-payload")))),
                EXECUTION_TIME)
            .assistantMessage();

    final var payload = reasoningPayload(assistantMessage);
    assertThat(payload).containsOnlyKeys("redactedContent");
    assertReasoningHistory(assistantMessage, null, payload);
  }

  @Test
  void mapsGeminiReadableReasoningAndThoughtSignatureToAgentInstanceHistory() {
    final var assistantMessage =
        new GeminiContentResponseConverter()
            .toResult(
                geminiResponse(
                    Part.builder()
                        .thought(true)
                        .text("Let me think it through")
                        .thoughtSignature(THOUGHT_SIGNATURE)
                        .build(),
                    Part.fromText("the answer")),
                EXECUTION_TIME)
            .assistantMessage();

    final var reasoningContent = (ReasoningContent) assistantMessage.content().getFirst();
    assertThat(reasoningContent.metadata())
        .containsEntry(
            THOUGHT_SIGNATURE_METADATA_KEY, Base64.getEncoder().encodeToString(THOUGHT_SIGNATURE));
    final var payload = reasoningPayload(assistantMessage);
    assertThat(payload).containsEntry(THOUGHT_SIGNATURE_METADATA_KEY, THOUGHT_SIGNATURE);
    assertReasoningHistory(assistantMessage, "Let me think it through", payload);
    assertTrailingTextHistory(assistantMessage, "the answer");
  }

  @Test
  void mapsGeminiSignatureOnlyReasoningToOpaqueAgentInstanceHistory() {
    final var assistantMessage =
        new GeminiContentResponseConverter()
            .toResult(
                geminiResponse(
                    Part.builder().thought(true).thoughtSignature(THOUGHT_SIGNATURE).build()),
                EXECUTION_TIME)
            .assistantMessage();

    final var payload = reasoningPayload(assistantMessage);
    assertThat(payload).containsEntry(THOUGHT_SIGNATURE_METADATA_KEY, THOUGHT_SIGNATURE);
    assertReasoningHistory(assistantMessage, null, payload);
  }

  @Test
  void mapsOpenAiReadableReasoningAndEncryptedPayloadToAgentInstanceHistory() {
    final var assistantMessage =
        new OpenAiResponsesResponseConverter(objectMapper)
            .toResult(
                openAiResponse(
                    """
                    [
                      {
                        "type": "reasoning",
                        "id": "rs_1",
                        "summary": [
                          {"type": "summary_text", "text": "First part"},
                          {"type": "summary_text", "text": "Second part"}
                        ],
                        "encrypted_content": "encrypted-blob"
                      },
                      {
                        "type": "message",
                        "id": "msg_1",
                        "role": "assistant",
                        "status": "completed",
                        "content": [
                          {"type": "output_text", "text": "the answer", "annotations": []}
                        ]
                      }
                    ]
                    """),
                EXECUTION_TIME)
            .assistantMessage();

    final var payload = reasoningPayload(assistantMessage);
    assertThat(payload)
        .containsEntry("id", "rs_1")
        .containsEntry("encrypted_content", "encrypted-blob")
        .containsKey("summary");
    assertReasoningHistory(assistantMessage, "First part\nSecond part", payload);
    assertTrailingTextHistory(assistantMessage, "the answer");
  }

  @Test
  void mapsOpenAiOpaqueReasoningToAgentInstanceHistory() {
    final var assistantMessage =
        new OpenAiResponsesResponseConverter(objectMapper)
            .toResult(
                openAiResponse(
                    """
                    [
                      {"type": "reasoning", "id": "rs_2", "summary": []}
                    ]
                    """),
                EXECUTION_TIME)
            .assistantMessage();

    final var payload = reasoningPayload(assistantMessage);
    assertThat(payload).containsEntry("id", "rs_2");
    assertReasoningHistory(assistantMessage, null, payload);
  }

  private void assertReasoningHistory(
      AssistantMessage assistantMessage, String text, Map<String, Object> payload) {
    assertThat(historyMapper.assistantContent(assistantMessage).getFirst())
        .isInstanceOfSatisfying(
            AgentInstanceHistoryContent.ObjectContent.class,
            object ->
                assertThat(object.getObject()).isEqualTo(agentInstanceReasoning(text, payload)));
  }

  private void assertTrailingTextHistory(AssistantMessage assistantMessage, String text) {
    assertThat(historyMapper.assistantContent(assistantMessage).get(1))
        .isInstanceOfSatisfying(
            AgentInstanceHistoryContent.TextContent.class,
            historyText -> assertThat(historyText.getText()).isEqualTo(text));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> reasoningPayload(AssistantMessage assistantMessage) {
    return (Map<String, Object>)
        ((ReasoningContent) assistantMessage.content().getFirst()).payload();
  }

  private static Map<String, Object> agentInstanceReasoning(String text, Object payload) {
    if (text == null || text.isBlank()) {
      return Map.of("camunda.agenticai.content.type", "reasoning", "payload", payload);
    }
    return Map.of("camunda.agenticai.content.type", "reasoning", "text", text, "payload", payload);
  }

  private static Message anthropicMessage(String json) {
    try {
      return ObjectMappers.jsonMapper().readValue(json, Message.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to parse Anthropic fixture", e);
    }
  }

  private static ConverseResponse bedrockResponse(ContentBlock... content) {
    return ConverseResponse.builder()
        .output(
            ConverseOutput.fromMessage(
                software.amazon.awssdk.services.bedrockruntime.model.Message.builder()
                    .role(ConversationRole.ASSISTANT)
                    .content(content)
                    .build()))
        .stopReason(StopReason.END_TURN)
        .usage(TokenUsage.builder().inputTokens(1).outputTokens(1).build())
        .build();
  }

  private static GenerateContentResponse geminiResponse(Part... parts) {
    return GenerateContentResponse.builder()
        .candidates(
            List.of(
                Candidate.builder()
                    .finishReason(FinishReason.Known.STOP)
                    .content(Content.builder().role("model").parts(List.of(parts)).build())
                    .build()))
        .build();
  }

  private static Response openAiResponse(String output) {
    try {
      return com.openai.core.ObjectMappers.jsonMapper()
          .readValue(
              """
              {
                "id": "resp_123",
                "object": "response",
                "created_at": 0,
                "model": "gpt-5",
                "output": %s,
                "parallel_tool_calls": true,
                "tool_choice": "auto",
                "tools": []
              }
              """
                  .formatted(output),
              Response.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to parse OpenAI fixture", e);
    }
  }
}
