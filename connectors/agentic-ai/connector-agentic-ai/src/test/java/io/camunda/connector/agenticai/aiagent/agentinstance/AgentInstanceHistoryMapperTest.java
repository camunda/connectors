/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agentinstance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.api.command.AgentInstanceHistoryContent;
import io.camunda.client.api.search.enums.AgentInstanceHistoryRole;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.MessageUtil;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.ProviderContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ReasoningContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AgentInstanceHistoryMapperTest {

  private static final OffsetDateTime TURN_INGESTION_TIMESTAMP =
      OffsetDateTime.parse("2026-07-02T10:00:00Z");
  private static final OffsetDateTime TOOL_RESULT_COMPLETED_AT =
      OffsetDateTime.parse("2026-07-02T09:59:55Z");

  private final AgentInstanceHistoryMapper mapper =
      new AgentInstanceHistoryMapper(Mockito.mock(GatewayToolHandlerRegistry.class));

  @Test
  void userMessageUsesThePassedInTurnIngestionTimestamp() {
    final var userMessage =
        UserMessage.builder().content(MessageUtil.singleTextContent("hi")).build();

    final var items = mapper.inputHistoryItems(userMessage, Map.of(), TURN_INGESTION_TIMESTAMP);

    assertThat(items).singleElement().extracting("producedAt").isEqualTo(TURN_INGESTION_TIMESTAMP);
  }

  @Test
  void toolResultUsesItsOwnCompletedAtNotTheTurnIngestionTimestamp() {
    final var toolCall = ToolCall.builder().id("call-1").name("getWeather").build();
    final var result =
        ToolCallResult.builder()
            .id("call-1")
            .name("getWeather")
            .elementId("getWeather")
            .content("sunny")
            .completedAt(TOOL_RESULT_COMPLETED_AT)
            .build();
    final var message = ToolCallResultMessage.builder().results(List.of(result)).build();

    final var items =
        mapper.inputHistoryItems(message, Map.of("call-1", toolCall), TURN_INGESTION_TIMESTAMP);

    assertThat(items)
        .singleElement()
        .satisfies(
            item -> {
              assertThat(item.role()).isEqualTo(AgentInstanceHistoryRole.TOOL_RESULT);
              assertThat(item.producedAt()).isEqualTo(TOOL_RESULT_COMPLETED_AT);
            });
  }

  @Test
  void distinguishesProducedAtAcrossTwoToolResultsInTheSameTurn() {
    final var fastCompletedAt = OffsetDateTime.parse("2026-07-02T09:59:50Z");
    final var slowCompletedAt = OffsetDateTime.parse("2026-07-02T09:59:58Z");
    final var fastToolCall = ToolCall.builder().id("fast").name("getWeather").build();
    final var slowToolCall = ToolCall.builder().id("slow").name("downloadFile").build();
    final var fastResult =
        ToolCallResult.builder()
            .id("fast")
            .name("getWeather")
            .elementId("getWeather")
            .content("sunny")
            .completedAt(fastCompletedAt)
            .build();
    final var slowResult =
        ToolCallResult.builder()
            .id("slow")
            .name("downloadFile")
            .elementId("downloadFile")
            .content("done")
            .completedAt(slowCompletedAt)
            .build();
    final var message =
        ToolCallResultMessage.builder().results(List.of(fastResult, slowResult)).build();

    final var items =
        mapper.inputHistoryItems(
            message, Map.of("fast", fastToolCall, "slow", slowToolCall), TURN_INGESTION_TIMESTAMP);

    assertThat(items).hasSize(2);
    assertThat(items.get(0).producedAt()).isEqualTo(fastCompletedAt);
    assertThat(items.get(1).producedAt()).isEqualTo(slowCompletedAt);
    assertThat(items.get(0).producedAt()).isNotEqualTo(items.get(1).producedAt());
  }

  @Test
  void throwsWhenToolResultHasNoCompletedAt() {
    // an invariant violation: ingestion normalization (ADR 008) must resolve completedAt on
    // every tool call result before it reaches the history mapper
    final var toolCall = ToolCall.builder().id("call-1").name("getWeather").build();
    final var result =
        ToolCallResult.builder()
            .id("call-1")
            .name("getWeather")
            .elementId("getWeather")
            .content("sunny")
            .build();
    final var message = ToolCallResultMessage.builder().results(List.of(result)).build();

    assertThatThrownBy(
            () ->
                mapper.inputHistoryItems(
                    message, Map.of("call-1", toolCall), TURN_INGESTION_TIMESTAMP))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("call-1")
        .hasMessageContaining("completedAt");
  }

  @Test
  void reasoningContentMapsToAnObjectHistoryBlockOfTheReasoningContentItself() {
    final var reasoningContent =
        new ReasoningContent(Map.of("signature", "abc123"), Map.of("foo", "bar"));
    final var assistantMessage =
        AssistantMessage.builder().content(List.of(reasoningContent)).build();

    final var content = mapper.assistantContent(assistantMessage);

    assertThat(content)
        .singleElement()
        .isInstanceOfSatisfying(
            AgentInstanceHistoryContent.ObjectContent.class,
            object -> assertThat(object.getObject()).isEqualTo(reasoningContent));
  }

  @Test
  void providerContentMapsToAnObjectHistoryBlockOfItsPayload() {
    final var payload = Map.of("id", "srvtoolu_01", "input", Map.of("query", "search term"));
    final var providerContent =
        ProviderContent.providerContent("anthropic", "server_tool_use", payload);
    final var assistantMessage =
        AssistantMessage.builder().content(List.of(providerContent)).build();

    final var content = mapper.assistantContent(assistantMessage);

    assertThat(content)
        .singleElement()
        .isInstanceOfSatisfying(
            AgentInstanceHistoryContent.ObjectContent.class,
            object -> assertThat(object.getObject()).isEqualTo(payload));
  }
}
