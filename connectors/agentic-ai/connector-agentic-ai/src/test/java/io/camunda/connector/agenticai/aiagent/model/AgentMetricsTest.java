/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics.TokenUsage;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

class AgentMetricsTest {
  private static final AgentMetrics EMPTY_METRICS = AgentMetrics.empty();
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void emptyMetrics() {
    final var metrics = AgentMetrics.empty();
    assertThat(metrics.modelCalls()).isEqualTo(0);
    assertThat(metrics.tokenUsage()).isEqualTo(TokenUsage.empty());
    assertThat(metrics).isNotSameAs(EMPTY_METRICS).isEqualTo(EMPTY_METRICS);
  }

  @Test
  void withModelCalls() {
    final var initialMetrics = AgentMetrics.empty();
    final var updatedMetrics = initialMetrics.withModelCalls(1);

    assertThat(updatedMetrics).isNotEqualTo(initialMetrics);
    assertThat(initialMetrics.modelCalls()).isEqualTo(EMPTY_METRICS.modelCalls());

    assertThat(updatedMetrics.modelCalls()).isEqualTo(1);
  }

  @Test
  void incrementModelCalls() {
    final var initialMetrics = AgentMetrics.empty().withModelCalls(1);
    final var updatedMetrics = initialMetrics.incrementModelCalls(3);

    assertThat(updatedMetrics).isNotEqualTo(initialMetrics);
    assertThat(initialMetrics.modelCalls()).isEqualTo(1);

    assertThat(updatedMetrics.modelCalls()).isEqualTo(4);
  }

  @Test
  void withTokenUsage() {
    final var initialMetrics = AgentMetrics.empty();
    final var updatedMetrics = initialMetrics.withTokenUsage(new TokenUsage(10, 20));

    assertThat(updatedMetrics).isNotEqualTo(initialMetrics);
    assertThat(initialMetrics.tokenUsage()).isEqualTo(EMPTY_METRICS.tokenUsage());

    assertThat(updatedMetrics.tokenUsage()).isEqualTo(new TokenUsage(10, 20));
    assertThat(updatedMetrics.tokenUsage().inputTokenCount()).isEqualTo(10);
    assertThat(updatedMetrics.tokenUsage().outputTokenCount()).isEqualTo(20);
  }

  @Test
  void incrementTokenUsage() {
    final var initialMetrics = AgentMetrics.empty().withTokenUsage(new TokenUsage(10, 20));
    final var updatedMetrics = initialMetrics.incrementTokenUsage(new TokenUsage(1, 2));

    assertThat(updatedMetrics).isNotEqualTo(initialMetrics);
    assertThat(initialMetrics.tokenUsage()).isEqualTo(new TokenUsage(10, 20));

    assertThat(updatedMetrics.tokenUsage()).isEqualTo(new TokenUsage(11, 22));
    assertThat(updatedMetrics.tokenUsage().inputTokenCount()).isEqualTo(11);
    assertThat(updatedMetrics.tokenUsage().outputTokenCount()).isEqualTo(22);
  }

  @Test
  void withToolCalls() {
    final var initialMetrics = AgentMetrics.empty();
    final var updatedMetrics = initialMetrics.withToolCalls(5);

    assertThat(updatedMetrics).isNotEqualTo(initialMetrics);
    assertThat(initialMetrics.toolCalls()).isEqualTo(0);

    assertThat(updatedMetrics.toolCalls()).isEqualTo(5);
  }

  @Test
  void incrementToolCalls() {
    final var initialMetrics = AgentMetrics.empty().withToolCalls(3);
    final var updatedMetrics = initialMetrics.incrementToolCalls(4);

    assertThat(updatedMetrics).isNotEqualTo(initialMetrics);
    assertThat(initialMetrics.toolCalls()).isEqualTo(3);

    assertThat(updatedMetrics.toolCalls()).isEqualTo(7);
  }

  @Test
  void addAgentMetrics() {
    final var a = new AgentMetrics(2, new TokenUsage(30, 70), 1);
    final var b = new AgentMetrics(3, new TokenUsage(100, 200), 2);

    final var sum = a.add(b);

    assertThat(sum.modelCalls()).isEqualTo(5);
    assertThat(sum.tokenUsage()).isEqualTo(new TokenUsage(130, 270));
    assertThat(sum.toolCalls()).isEqualTo(3);
    // operands are left untouched
    assertThat(a).isEqualTo(new AgentMetrics(2, new TokenUsage(30, 70), 1));
  }

  @Test
  void tokenUsageAddAggregatesCacheAndReasoningAsAuxiliaryDetail() {
    final var a = new TokenUsage(10, 20, 1, 2, 3);
    final var b = new TokenUsage(100, 200, 10, 20, 30);

    final var sum = a.add(b);

    assertThat(sum.inputTokenCount()).isEqualTo(110);
    assertThat(sum.outputTokenCount()).isEqualTo(220);
    assertThat(sum.cacheReadTokenCount()).isEqualTo(11);
    assertThat(sum.cacheCreationTokenCount()).isEqualTo(22);
    assertThat(sum.reasoningTokenCount()).isEqualTo(33);
  }

  @Test
  void tokenUsageAddSummarizesMixedRecordsWithAndWithoutCacheAndReasoningCounts() {
    final var withDetail = new TokenUsage(10, 20, 1, 2, 3);
    final var withoutDetail = new TokenUsage(100, 200);

    final var sum = withDetail.add(withoutDetail);

    assertThat(sum.inputTokenCount()).isEqualTo(110);
    assertThat(sum.outputTokenCount()).isEqualTo(220);
    assertThat(sum.cacheReadTokenCount()).isEqualTo(1);
    assertThat(sum.cacheCreationTokenCount()).isEqualTo(2);
    assertThat(sum.reasoningTokenCount()).isEqualTo(3);
  }

  @Test
  void tokenUsageWithZeroCacheAndReasoningCountsOmitsThemFromJson() throws Exception {
    final var tokenUsage = new TokenUsage(10, 20);

    final var serialized = objectMapper.writeValueAsString(tokenUsage);

    JSONAssert.assertEquals(
        "{\"inputTokenCount\":10,\"outputTokenCount\":20}", serialized, JSONCompareMode.STRICT);

    final var deserialized = objectMapper.readValue(serialized, TokenUsage.class);
    assertThat(deserialized).isEqualTo(tokenUsage);
  }

  @Test
  void tokenUsageWithNonZeroCacheAndReasoningCountsIncludesThemInJson() throws Exception {
    final var tokenUsage = new TokenUsage(10, 20, 1, 2, 3);

    final var serialized = objectMapper.writeValueAsString(tokenUsage);

    JSONAssert.assertEquals(
        """
        {
          "inputTokenCount": 10,
          "outputTokenCount": 20,
          "cacheReadTokenCount": 1,
          "cacheCreationTokenCount": 2,
          "reasoningTokenCount": 3
        }
        """,
        serialized,
        JSONCompareMode.STRICT);

    final var deserialized = objectMapper.readValue(serialized, TokenUsage.class);
    assertThat(deserialized).isEqualTo(tokenUsage);
  }

  @Test
  void oldTwoFieldTokenUsageJsonStillDeserializes() throws Exception {
    final var oldJson = "{\"inputTokenCount\":10,\"outputTokenCount\":20}";

    final var deserialized = objectMapper.readValue(oldJson, TokenUsage.class);

    assertThat(deserialized).isEqualTo(new TokenUsage(10, 20));
    assertThat(deserialized.cacheReadTokenCount()).isZero();
    assertThat(deserialized.cacheCreationTokenCount()).isZero();
    assertThat(deserialized.reasoningTokenCount()).isZero();
  }

  @ParameterizedTest
  @MethodSource("invalidConstructorParameters")
  void throwsExceptionOnInvalidConstructorParameters(
      int modelCalls,
      TokenUsage tokenUsage,
      int toolCalls,
      Class<? extends Throwable> exceptionClass,
      String exceptionMessage) {
    assertThatThrownBy(() -> new AgentMetrics(modelCalls, tokenUsage, toolCalls))
        .isInstanceOf(exceptionClass)
        .hasMessage(exceptionMessage);
  }

  static Stream<Arguments> invalidConstructorParameters() {
    return Stream.of(
        arguments(
            -10,
            TokenUsage.empty(),
            0,
            IllegalArgumentException.class,
            "Model calls must be non-negative"),
        arguments(10, null, 0, NullPointerException.class, "Token usage must not be null"),
        arguments(
            0,
            TokenUsage.empty(),
            -5,
            IllegalArgumentException.class,
            "Tool calls must be non-negative"));
  }
}
