/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.agentinstance.AgentInstanceHistoryMapper;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ToolCallResultCompletedAtResolverTest {

  private static final OffsetDateTime ENGINE_TIMESTAMP =
      OffsetDateTime.parse("2026-07-02T10:00:00Z");
  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-07-03T10:00:00Z");

  private final ToolCallResultCompletedAtResolver resolver =
      new ToolCallResultCompletedAtResolver(() -> NOW);

  @Test
  void keepsEngineTimestampWhenPresent() {
    ToolCallResult result =
        ToolCallResult.builder().id("call-1").name("tool").completedAt(ENGINE_TIMESTAMP).build();

    var resolved = resolver.resolve(List.of(result));

    assertThat(resolved).singleElement().extracting("completedAt").isEqualTo(ENGINE_TIMESTAMP);
  }

  @Test
  void stampsNowWhenEngineTimestampMissing() {
    ToolCallResult result = ToolCallResult.builder().id("call-1").name("tool").build();

    var resolved = resolver.resolve(List.of(result));

    assertThat(resolved).singleElement().extracting("completedAt").isEqualTo(NOW);
  }

  @Test
  void stampsNowForEventResultsWithoutAnId() {
    ToolCallResult eventResult = ToolCallResult.builder().content("event data").build();

    var resolved = resolver.resolve(List.of(eventResult));

    assertThat(resolved).singleElement().extracting("completedAt").isEqualTo(NOW);
  }

  @Test
  void resolvesEachResultIndependently() {
    ToolCallResult withEngineTimestamp =
        ToolCallResult.builder().id("a").name("tool").completedAt(ENGINE_TIMESTAMP).build();
    ToolCallResult withoutEngineTimestamp = ToolCallResult.builder().id("b").name("tool").build();

    var resolved = resolver.resolve(List.of(withEngineTimestamp, withoutEngineTimestamp));

    assertThat(resolved.get(0).completedAt()).isEqualTo(ENGINE_TIMESTAMP);
    assertThat(resolved.get(1).completedAt()).isEqualTo(NOW);
  }

  @Test
  void handlesEmptyList() {
    var resolved = resolver.resolve(List.of());

    assertThat(resolved).isEmpty();
  }

  @Test
  void fallbackCompletedAtSurfacesAsHistoryProducedAt() {
    // a result without an engine timestamp gets the resolver's now() fallback, which must flow
    // through to the history item's producedAt rather than the turn ingestion timestamp (ADR 008)
    final var turnIngestionTimestamp = OffsetDateTime.parse("2026-07-05T10:00:00Z");
    final var mapper =
        new AgentInstanceHistoryMapper(
            new ObjectMapper(), Mockito.mock(GatewayToolHandlerRegistry.class));
    final var toolCall = ToolCall.builder().id("call-1").name("tool").build();
    final var rawResult =
        ToolCallResult.builder()
            .id("call-1")
            .name("tool")
            .elementId("tool")
            .content("done")
            .build();

    final var resolved = resolver.resolve(List.of(rawResult));
    final var message = ToolCallResultMessage.builder().results(resolved).build();
    final var items =
        mapper.inputHistoryItems(message, Map.of("call-1", toolCall), turnIngestionTimestamp);

    assertThat(items).singleElement().extracting("producedAt").isEqualTo(NOW);
  }
}
