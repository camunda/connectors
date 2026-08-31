/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agentinstance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.message.MessageUtil;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pins the {@code historyItemId} derivation rules for {@code USER}/{@code ASSISTANT} items (keyed
 * off the domain message's own id, ADR 012) and {@code TOOL_RESULT} items (keyed off the tool-call
 * id, so a cross-write retry dedups for free). {@code CONFIGURATION} items use {@link
 * io.camunda.connector.agenticai.aiagent.model.AgentConfiguration#fingerprint()} directly as their
 * id instead — see {@code AgentConfigurationTest}.
 */
class AgentInstanceHistoryItemIdsTest {

  @Nested
  class ForMessage {

    @Test
    void sameMessageYieldsSameId() {
      final var message =
          UserMessage.builder().content(MessageUtil.singleTextContent("hi")).build();

      final var first = AgentInstanceHistoryItemIds.forMessage(message);
      final var second = AgentInstanceHistoryItemIds.forMessage(message);

      assertThat(first).isEqualTo(second).isEqualTo(message.id().toString());
    }

    @Test
    void differentMessagesYieldDifferentIds() {
      final var a = UserMessage.builder().content(MessageUtil.singleTextContent("hi")).build();
      final var b = UserMessage.builder().content(MessageUtil.singleTextContent("hi")).build();

      assertThat(AgentInstanceHistoryItemIds.forMessage(a))
          .isNotEqualTo(AgentInstanceHistoryItemIds.forMessage(b));
    }
  }

  @Nested
  class ForToolCallResult {

    @Test
    void sameToolCallIdYieldsSameIdAcrossSeparateDerivations() {
      // the cross-write dedup property: two independently-built results correlating to the same
      // originating tool call (e.g. a streamed report and the later batch write) must dedup
      final var completedAt = OffsetDateTime.parse("2026-07-02T09:59:50Z");
      final var first =
          ToolCallResultContent.builder()
              .id("call-1")
              .name("getWeather")
              .elementId("getWeather")
              .content(List.of(TextContent.textContent("sunny")))
              .completedAt(completedAt)
              .build();
      final var second =
          ToolCallResultContent.builder()
              .id("call-1")
              .name("getWeather")
              .elementId("getWeather")
              .content(List.of(TextContent.textContent("still sunny")))
              .completedAt(completedAt.plusSeconds(5))
              .build();

      assertThat(AgentInstanceHistoryItemIds.forToolCallResult(first))
          .isEqualTo(AgentInstanceHistoryItemIds.forToolCallResult(second))
          .isEqualTo("call-1");
    }

    @Test
    void differentToolCallIdsYieldDifferentIds() {
      final var a =
          ToolCallResultContent.builder()
              .id("call-1")
              .name("getWeather")
              .content(List.of())
              .build();
      final var b =
          ToolCallResultContent.builder()
              .id("call-2")
              .name("getWeather")
              .content(List.of())
              .build();

      assertThat(AgentInstanceHistoryItemIds.forToolCallResult(a))
          .isNotEqualTo(AgentInstanceHistoryItemIds.forToolCallResult(b));
    }

    @Test
    void blankToolCallIdFallsBackToNonBlankDeterministicId() {
      // event results carry no id (AGENTS.md gotcha: "events have id = null"), but the SDK
      // rejects a blank historyItemId outright, so a fallback is required
      final var completedAt = OffsetDateTime.parse("2026-07-02T09:59:50Z");
      final var event =
          ToolCallResultContent.builder()
              .elementId("timerEvent")
              .content(List.of())
              .completedAt(completedAt)
              .build();

      final var id = AgentInstanceHistoryItemIds.forToolCallResult(event);

      assertThat(id).isNotBlank();
    }

    @Test
    void blankToolCallIdFallbackIsDeterministicForIdenticalEvents() {
      final var completedAt = OffsetDateTime.parse("2026-07-02T09:59:50Z");
      final var first =
          ToolCallResultContent.builder()
              .elementId("timerEvent")
              .content(List.of())
              .completedAt(completedAt)
              .build();
      final var second =
          ToolCallResultContent.builder()
              .elementId("timerEvent")
              .content(List.of())
              .completedAt(completedAt)
              .build();

      assertThat(AgentInstanceHistoryItemIds.forToolCallResult(first))
          .isEqualTo(AgentInstanceHistoryItemIds.forToolCallResult(second));
    }

    @Test
    void blankToolCallIdFallbackDiffersWhenCompletedAtDiffers() {
      final var first =
          ToolCallResultContent.builder()
              .elementId("timerEvent")
              .content(List.of())
              .completedAt(OffsetDateTime.parse("2026-07-02T09:59:50Z"))
              .build();
      final var second =
          ToolCallResultContent.builder()
              .elementId("timerEvent")
              .content(List.of())
              .completedAt(OffsetDateTime.parse("2026-07-02T10:00:00Z"))
              .build();

      assertThat(AgentInstanceHistoryItemIds.forToolCallResult(first))
          .isNotEqualTo(AgentInstanceHistoryItemIds.forToolCallResult(second));
    }

    @Test
    void blankToolCallIdFallbackIsACollisionResistantDigest() {
      // guards against a 32-bit Integer.hashCode()-based fallback: two distinct id-less events
      // could otherwise collide, causing the engine's per-item dedup to discard one as a duplicate
      final var event =
          ToolCallResultContent.builder()
              .elementId("timerEvent")
              .content(List.of())
              .completedAt(OffsetDateTime.parse("2026-07-02T09:59:50Z"))
              .build();

      final var id = AgentInstanceHistoryItemIds.forToolCallResult(event);

      assertThat(id).matches("[0-9a-f]{64}");
    }
  }
}
