/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider;

import static io.camunda.connector.agenticai.aiagent.chatmodel.provider.ChatModelProviderSupport.deriveTimeoutSetting;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.request.v1.shared.TimeoutConfiguration;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.ChatModelProperties;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.ChatModelProperties.ApiProperties;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.ChatModelProperties.AzureProperties;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.ChatModelProperties.AzureProperties.CredentialCacheProperties;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ChatModelProviderSupportTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(ChatModelProviderSupportTest.class);
  private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(3);

  private final ChatModelProperties chatModelProperties =
      new ChatModelProperties(
          new ApiProperties(DEFAULT_TIMEOUT),
          new AzureProperties(new CredentialCacheProperties(true, 100L, Duration.ofMinutes(10))));

  @ParameterizedTest
  @MethodSource("timeoutConfigurations")
  void derivesTimeoutFallingBackToConfiguredDefault(
      TimeoutConfiguration timeoutConfiguration, Duration expectedTimeout) {
    final var result =
        deriveTimeoutSetting("test", chatModelProperties, timeoutConfiguration, LOGGER);

    assertThat(result).isEqualTo(expectedTimeout);
  }

  static Stream<Arguments> timeoutConfigurations() {
    return Stream.of(
        Arguments.of(new TimeoutConfiguration(Duration.ofSeconds(45)), Duration.ofSeconds(45)),
        Arguments.of(null, DEFAULT_TIMEOUT),
        Arguments.of(new TimeoutConfiguration(null), DEFAULT_TIMEOUT),
        Arguments.of(new TimeoutConfiguration(Duration.ZERO), DEFAULT_TIMEOUT),
        Arguments.of(new TimeoutConfiguration(Duration.ofSeconds(-1)), DEFAULT_TIMEOUT));
  }
}
