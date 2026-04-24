/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider;

import io.camunda.connector.agenticai.aiagent.model.request.provider.ProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.TimeoutConfiguration;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Base class for built-in {@link ChatModelProvider} implementations. */
public abstract class AbstractChatModelProvider<T extends ProviderConfiguration>
    implements ChatModelProvider<T> {

  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractChatModelProvider.class);

  protected final AgenticAiConnectorsConfigurationProperties.ChatModelProperties
      chatModelProperties;

  protected AbstractChatModelProvider(
      AgenticAiConnectorsConfigurationProperties agenticAiConnectorsConfigurationProperties) {
    this.chatModelProperties = agenticAiConnectorsConfigurationProperties.aiagent().chatModel();
  }

  protected Duration deriveTimeoutSetting(TimeoutConfiguration timeoutConfiguration) {
    var derivedTimeout =
        Optional.ofNullable(timeoutConfiguration)
            .map(TimeoutConfiguration::timeout)
            .filter(Duration::isPositive)
            .or(() -> Optional.of(chatModelProperties.api().defaultTimeout()))
            .get();

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(
          "Setting model call timeout to {} for executing requests against the LLM provider",
          derivedTimeout);
    }

    return derivedTimeout;
  }
}
