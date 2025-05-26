/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.camunda.connector.agenticai.aiagent.model.AgentMetrics.TokenUsage;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AgentMetricsTest {
  private static final AgentMetrics EMPTY_METRICS = new AgentMetrics(0, new TokenUsage(0, 0));

  @Test
  void emptyMetrics() {
    final var metrics = AgentMetrics.empty();
    assertEquals(0, metrics.modelCalls());
    assertEquals(TokenUsage.empty(), metrics.tokenUsage());
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
    assertThat(updatedMetrics.tokenUsage().totalTokenCount()).isEqualTo(30);
  }

  @Test
  void incrementTokenUsage() {
    final var initialMetrics = AgentMetrics.empty().withTokenUsage(new TokenUsage(10, 20));
    final var updatedMetrics = initialMetrics.incrementTokenUsage(new TokenUsage(1, 2));

    assertThat(updatedMetrics).isNotEqualTo(initialMetrics);
    assertThat(initialMetrics.tokenUsage()).isEqualTo(new TokenUsage(10, 20));

    assertThat(updatedMetrics.tokenUsage()).isEqualTo(new TokenUsage(11, 22));
    assertThat(updatedMetrics.tokenUsage().totalTokenCount()).isEqualTo(33);
  }

  /*
  @Nested
  class FromLangchain4JTokenUsage {

    @Test
    void mapsTokenUsage() {
      final var mapped =
          AgentMetrics.TokenUsage.from(new dev.langchain4j.model.output.TokenUsage(10, 20));
      assertThat(mapped.inputTokenCount()).isEqualTo(10);
      assertThat(mapped.outputTokenCount()).isEqualTo(20);
      assertThat(mapped.totalTokenCount()).isEqualTo(30);
    }

    @Test
    void mapsNullTokenUsageToEmpty() {
      final var mapped = AgentMetrics.TokenUsage.from(null);
      assertThat(mapped).isNotNull().isEqualTo(TokenUsage.empty());
    }

    @ParameterizedTest
    @MethodSource("tokenUsageWithNullFields")
    void mapsTokenUsageWithNullFields(
        dev.langchain4j.model.output.TokenUsage tokenUsage, TokenUsage expectedTokenUsage) {
      assertThat(AgentMetrics.TokenUsage.from(tokenUsage)).isEqualTo(expectedTokenUsage);
    }

    public static Stream<Arguments> tokenUsageWithNullFields() {
      return Stream.of(
          arguments(new dev.langchain4j.model.output.TokenUsage(), TokenUsage.empty()),
          arguments(new dev.langchain4j.model.output.TokenUsage(null), TokenUsage.empty()),
          arguments(new dev.langchain4j.model.output.TokenUsage(null, null), TokenUsage.empty()),
          arguments(new dev.langchain4j.model.output.TokenUsage(10), new TokenUsage(10, 0)),
          arguments(new dev.langchain4j.model.output.TokenUsage(10, 20), new TokenUsage(10, 20)));
    }
  }
  */

  @ParameterizedTest
  @MethodSource("invalidConstructorParameters")
  void throwsExceptionOnInvalidConstructorParameters(
      int modelCalls,
      TokenUsage tokenUsage,
      Class<? extends Throwable> exceptionClass,
      String exceptionMessage) {
    assertThatThrownBy(() -> new AgentMetrics(modelCalls, tokenUsage))
        .isInstanceOf(exceptionClass)
        .hasMessage(exceptionMessage);
  }

  static Stream<Arguments> invalidConstructorParameters() {
    return Stream.of(
        arguments(
            -10,
            TokenUsage.empty(),
            IllegalArgumentException.class,
            "Model calls must be non-negative"),
        arguments(10, null, NullPointerException.class, "Token usage must not be null"));
  }
}
