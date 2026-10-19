/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.sns.inbound.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.feel.FeelExpressionEvaluator;
import io.camunda.connector.feel.jackson.FeelContextAwareObjectReader;
import io.camunda.connector.feel.jackson.JacksonModuleFeelFunction;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.sns.inbound.model.SnsWebhookConnectorProperties.SnsWebhookConnectorPropertiesWrapper;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@code topicsAllowList} is typed {@code List<String>} (not {@code String}) specifically so a FEEL
 * list expression binds as a real list. {@link
 * io.camunda.connector.runtime.test.inbound.InboundConnectorContextBuilder}, used by {@link
 * io.camunda.connector.sns.inbound.SnsWebhookExecutableTest}, does not wire in a live FEEL
 * evaluator, so it can prove the plain-comma-string path but not this one - these tests exercise
 * the same {@code FeelContextAwareObjectReader} binding path the runtime actually uses
 * (io.camunda.connector.runtime.core.inbound.InboundConnectorContextImpl#bindProperties), with a
 * stub evaluator standing in for the real FEEL engine so the test proves the binding, not FEEL
 * expression parsing itself (which is the framework's own, already-covered concern).
 */
class SnsWebhookConnectorPropertiesFeelBindingTest {

  @Test
  void feelListExpressionBindsAsRealList() throws Exception {
    var evaluator = new StubEvaluator(List.of("arnA", "arnB"));

    SnsWebhookConnectorPropertiesWrapper bound =
        FeelContextAwareObjectReader.of(mapper(evaluator))
            .withEvaluator(evaluator)
            .readValue(
                """
                {"inbound":{"context":"t","securitySubscriptionAllowedFor":"specific","topicsAllowList":"=[\\"arnA\\",\\"arnB\\"]"}}
                """,
                SnsWebhookConnectorPropertiesWrapper.class);

    // Previously this bound as a single-element list containing the literal, unevaluated
    // stringified array (e.g. ["arnA","arnB"]), which then matched no real topic ARN at all.
    assertThat(bound.inbound().topicsAllowList()).containsExactly("arnA", "arnB");
  }

  private static ObjectMapper mapper(FeelExpressionEvaluator evaluator) {
    var mapper = ConnectorsObjectMapperSupplier.getCopy();
    mapper.registerModule(new JacksonModuleFeelFunction(true, evaluator));
    return mapper;
  }

  /** Stands in for the real FEEL engine: returns a canned list for any "=" expression. */
  private record StubEvaluator(List<String> answer) implements FeelExpressionEvaluator {
    @SuppressWarnings("unchecked")
    @Override
    public <T> T evaluate(String expression, Object... variables) {
      return (T) answer;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T evaluate(String expression, Class<T> targetType, Object... variables) {
      return (T) answer;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T evaluate(String expression, JavaType targetType, Object... variables) {
      return (T) answer;
    }

    @Override
    public String evaluateToJson(String expression, Object... variables) {
      throw new UnsupportedOperationException();
    }
  }
}
