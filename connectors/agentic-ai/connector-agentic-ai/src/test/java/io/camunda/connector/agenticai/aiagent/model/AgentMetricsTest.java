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

class AgentMetricsTest {
  private static final AgentMetrics EMPTY_METRICS = AgentMetrics.empty();

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
    final var updatedMetrics =
        initialMetrics.withTokenUsage(
            TokenUsage.builder().inputTokenCount(10).outputTokenCount(20).build());

    assertThat(updatedMetrics).isNotEqualTo(initialMetrics);
    assertThat(initialMetrics.tokenUsage()).isEqualTo(EMPTY_METRICS.tokenUsage());

    assertThat(updatedMetrics.tokenUsage())
        .isEqualTo(TokenUsage.builder().inputTokenCount(10).outputTokenCount(20).build());
    assertThat(updatedMetrics.tokenUsage().totalTokenCount()).isEqualTo(30);
  }

  @Test
  void incrementTokenUsage() {
    final var initialMetrics =
        AgentMetrics.empty()
            .withTokenUsage(TokenUsage.builder().inputTokenCount(10).outputTokenCount(20).build());
    final var updatedMetrics =
        initialMetrics.incrementTokenUsage(
            TokenUsage.builder().inputTokenCount(1).outputTokenCount(2).build());

    assertThat(updatedMetrics).isNotEqualTo(initialMetrics);
    assertThat(initialMetrics.tokenUsage())
        .isEqualTo(TokenUsage.builder().inputTokenCount(10).outputTokenCount(20).build());

    assertThat(updatedMetrics.tokenUsage())
        .isEqualTo(TokenUsage.builder().inputTokenCount(11).outputTokenCount(22).build());
    assertThat(updatedMetrics.tokenUsage().totalTokenCount()).isEqualTo(33);
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
    final var a =
        new AgentMetrics(
            2, TokenUsage.builder().inputTokenCount(30).outputTokenCount(70).build(), 1);
    final var b =
        new AgentMetrics(
            3, TokenUsage.builder().inputTokenCount(100).outputTokenCount(200).build(), 2);

    final var sum = a.add(b);

    assertThat(sum.modelCalls()).isEqualTo(5);
    assertThat(sum.tokenUsage())
        .isEqualTo(TokenUsage.builder().inputTokenCount(130).outputTokenCount(270).build());
    assertThat(sum.toolCalls()).isEqualTo(3);
    // operands are left untouched
    assertThat(a)
        .isEqualTo(
            new AgentMetrics(
                2, TokenUsage.builder().inputTokenCount(30).outputTokenCount(70).build(), 1));
  }

  @Test
  void incrementTokenUsageCarriesCacheAndReasoningDimensions() {
    final var initialMetrics =
        AgentMetrics.empty()
            .withTokenUsage(
                TokenUsage.builder()
                    .inputTokenCount(100)
                    .outputTokenCount(40)
                    .cacheReadTokenCount(60)
                    .cacheCreationTokenCount(10)
                    .reasoningTokenCount(25)
                    .build());
    final var updatedMetrics =
        initialMetrics.incrementTokenUsage(
            TokenUsage.builder()
                .inputTokenCount(50)
                .outputTokenCount(20)
                .cacheReadTokenCount(30)
                .cacheCreationTokenCount(0)
                .reasoningTokenCount(5)
                .build());

    assertThat(updatedMetrics.tokenUsage())
        .isEqualTo(
            TokenUsage.builder()
                .inputTokenCount(150)
                .outputTokenCount(60)
                .cacheReadTokenCount(90)
                .cacheCreationTokenCount(10)
                .reasoningTokenCount(30)
                .build());
    // cache/reasoning are subsets of input/output, so they are not added on top of the total
    assertThat(updatedMetrics.tokenUsage().totalTokenCount()).isEqualTo(210);
  }

  @Test
  void tokenUsageOmitsZeroCacheAndReasoningDimensionsWhenSerialized() throws Exception {
    final var mapper = new ObjectMapper();

    final var withoutExtras = TokenUsage.builder().inputTokenCount(10).outputTokenCount(20).build();
    final var json = mapper.writeValueAsString(withoutExtras);
    assertThat(json)
        .contains("inputTokenCount", "outputTokenCount")
        .doesNotContain("cacheReadTokenCount", "cacheCreationTokenCount", "reasoningTokenCount");

    // absent dimensions default back to zero on deserialization
    assertThat(mapper.readValue(json, TokenUsage.class)).isEqualTo(withoutExtras);

    final var withExtras =
        TokenUsage.builder()
            .inputTokenCount(10)
            .outputTokenCount(20)
            .cacheReadTokenCount(5)
            .reasoningTokenCount(3)
            .build();
    assertThat(mapper.writeValueAsString(withExtras))
        .contains("cacheReadTokenCount", "reasoningTokenCount");
  }

  @Test
  void tokenUsageWithoutCacheAndReasoningLeavesDimensionsAtZero() {
    final var tokenUsage = TokenUsage.builder().inputTokenCount(10).outputTokenCount(20).build();

    assertThat(tokenUsage.cacheReadTokenCount()).isZero();
    assertThat(tokenUsage.cacheCreationTokenCount()).isZero();
    assertThat(tokenUsage.reasoningTokenCount()).isZero();
    assertThat(tokenUsage.totalTokenCount()).isEqualTo(30);
  }

  @Test
  void tokenUsageCarriesCacheAndReasoningDimensionsAndAggregates() {
    var a =
        AgentMetrics.TokenUsage.builder()
            .inputTokenCount(100)
            .outputTokenCount(40)
            .cacheReadTokenCount(60)
            .cacheCreationTokenCount(10)
            .reasoningTokenCount(25)
            .build();
    var b =
        AgentMetrics.TokenUsage.builder()
            .inputTokenCount(50)
            .outputTokenCount(20)
            .cacheReadTokenCount(30)
            .cacheCreationTokenCount(0)
            .reasoningTokenCount(5)
            .build();

    var sum = a.add(b);

    assertThat(sum.inputTokenCount()).isEqualTo(150);
    assertThat(sum.outputTokenCount()).isEqualTo(60);
    assertThat(sum.cacheReadTokenCount()).isEqualTo(90);
    assertThat(sum.cacheCreationTokenCount()).isEqualTo(10);
    assertThat(sum.reasoningTokenCount()).isEqualTo(30);
    assertThat(sum.totalTokenCount())
        .isEqualTo(210); // cache/reasoning are subsets, not added on top
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
