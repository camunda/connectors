/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider;

import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.TimeoutConfiguration;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.ChatModelProperties;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;

public final class ChatModelProviderSupport {

  private ChatModelProviderSupport() {}

  public static Duration deriveTimeoutSetting(
      String timeoutType,
      ChatModelProperties chatModelConfig,
      TimeoutConfiguration timeoutConfiguration,
      Logger logger) {
    var derivedTimeout =
        Optional.ofNullable(timeoutConfiguration)
            .map(TimeoutConfiguration::timeout)
            .filter(Duration::isPositive)
            .or(() -> Optional.of(chatModelConfig.api().defaultTimeout()))
            .get();

    if (logger.isDebugEnabled()) {
      logger.debug("Setting {} timeout to {}", timeoutType, derivedTimeout);
    }

    return derivedTimeout;
  }
}
