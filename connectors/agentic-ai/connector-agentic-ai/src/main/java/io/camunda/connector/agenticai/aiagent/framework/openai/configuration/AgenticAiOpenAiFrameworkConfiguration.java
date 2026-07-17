/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.openai.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilitiesResolver;
import io.camunda.connector.agenticai.aiagent.framework.openai.OpenAiChatModelApiFactory;
import io.camunda.connector.agenticai.aiagent.framework.transport.HttpTransportSupport;
import io.camunda.connector.runtime.annotation.ConnectorsObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the {@link OpenAiChatModelApiFactory} as an additional {@code ChatModelApiFactory} bean
 * so it is picked up by {@code chatModelApiRegistry(List<ChatModelApiFactory>)} and resolved for
 * the configurations it supports.
 */
@Configuration
@ConditionalOnBooleanProperty(
    value = "camunda.connector.agenticai.aiagent.framework.openai.enabled",
    matchIfMissing = true)
public class AgenticAiOpenAiFrameworkConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public OpenAiChatModelApiFactory openAiChatModelApiFactory(
      HttpTransportSupport httpTransportSupport,
      ModelCapabilitiesResolver modelCapabilitiesResolver,
      @ConnectorsObjectMapper ObjectMapper objectMapper) {
    return new OpenAiChatModelApiFactory(
        httpTransportSupport, modelCapabilitiesResolver, objectMapper);
  }
}
