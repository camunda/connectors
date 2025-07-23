/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest.AgentRequestData.LimitsConfiguration;
import io.camunda.connector.api.error.ConnectorException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentLimitsValidatorTest {

  @Mock private AgentExecutionContext executionContext;

  @Nested
  class MaxModelCallsValidation {

    private static final AgentContext AGENT_CONTEXT =
        AgentContext.builder().metrics(AgentMetrics.empty().withModelCalls(5)).build();

    private final AgentLimitsValidator limitsValidator = new AgentLimitsValidatorImpl();

    @Test
    void validationSucceedsWhenNotOverConfiguredLimit() {
      when(executionContext.limits()).thenReturn(new LimitsConfiguration(8));

      assertDoesNotThrow(
          () -> limitsValidator.validateConfiguredLimits(executionContext, AGENT_CONTEXT));
    }

    @ParameterizedTest
    @ValueSource(ints = {8, 9})
    void validationFailsWhenHittingConfiguredLimit(int modelCalls) {
      when(executionContext.limits()).thenReturn(new LimitsConfiguration(8));

      final var agentContext =
          AGENT_CONTEXT.withMetrics(AGENT_CONTEXT.metrics().withModelCalls(modelCalls));

      assertThatThrownBy(
              () -> limitsValidator.validateConfiguredLimits(executionContext, agentContext))
          .isInstanceOfSatisfying(
              ConnectorException.class,
              e -> {
                assertThat(e.getErrorCode()).isEqualTo("MAXIMUM_NUMBER_OF_MODEL_CALLS_REACHED");
                assertThat(e.getMessage())
                    .isEqualTo(
                        "Maximum number of model calls reached (modelCalls: %d, limit: 8)"
                            .formatted(modelCalls));
              });
    }

    @ParameterizedTest
    @MethodSource("limitsConfigurationsWithoutMaxModelCalls")
    void fallsBackToDefaultLimitWhenNoLimitIsConfigured(LimitsConfiguration limitsConfiguration) {
      when(executionContext.limits()).thenReturn(limitsConfiguration);

      final var agentContext =
          AGENT_CONTEXT.withMetrics(AGENT_CONTEXT.metrics().withModelCalls(12));

      assertThatThrownBy(
              () -> limitsValidator.validateConfiguredLimits(executionContext, agentContext))
          .isInstanceOfSatisfying(
              ConnectorException.class,
              e -> {
                assertThat(e.getErrorCode()).isEqualTo("MAXIMUM_NUMBER_OF_MODEL_CALLS_REACHED");
                assertThat(e.getMessage())
                    .isEqualTo("Maximum number of model calls reached (modelCalls: 12, limit: 10)");
              });
    }

    static Stream<LimitsConfiguration> limitsConfigurationsWithoutMaxModelCalls() {
      return Stream.of(null, new LimitsConfiguration(null));
    }
  }
}
