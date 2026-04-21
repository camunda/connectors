/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider;

import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.TimeoutConfiguration;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;

final class ChatModelProviderTestSupport {

  static final TimeoutConfiguration MODEL_TIMEOUT =
      new TimeoutConfiguration(Duration.ofSeconds(30));

  private ChatModelProviderTestSupport() {}

  static Stream<TimeoutConfiguration> defaultTimeoutYieldingConfigs() {
    return Stream.of(
        new TimeoutConfiguration(null),
        new TimeoutConfiguration(Duration.ZERO),
        new TimeoutConfiguration(Duration.ofMinutes(-5)));
  }

  static AgenticAiConnectorsConfigurationProperties createDefaultConfigurationProperties() {
    final var binder = new Binder(List.of());
    return binder.bindOrCreate(
        "camunda.connector.agenticai",
        Bindable.of(AgenticAiConnectorsConfigurationProperties.class));
  }

  static class ResultCaptor<T> implements Answer<T> {
    private T result = null;

    public T getResult() {
      return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T answer(InvocationOnMock invocationOnMock) throws Throwable {
      result = (T) invocationOnMock.callRealMethod();
      return result;
    }
  }
}
