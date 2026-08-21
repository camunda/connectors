/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.AnthropicChatModelFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.AnthropicContentConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.AnthropicMessageRequestConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.AnthropicMessageResponseConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.OpenAiChatModelFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.OpenAiContentConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsRequestConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsResponseConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsStrategy;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsStreamAssembler;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.responses.OpenAiResponsesRequestConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.responses.OpenAiResponsesResponseConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.responses.OpenAiResponsesStrategy;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.responses.OpenAiResponsesStreamAssembler;
import io.camunda.connector.agenticai.common.AgenticAiHttpProxySupport;
import io.camunda.connector.runtime.annotation.ConnectorsObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgenticAiNativeProvidersConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public AnthropicChatModelFactory aiAgentAnthropicChatModelFactory(
      AgenticAiHttpProxySupport httpProxySupport,
      @ConnectorsObjectMapper ObjectMapper objectMapper) {
    final var contentConverter = new AnthropicContentConverter(objectMapper);
    final var requestConverter = new AnthropicMessageRequestConverter(contentConverter);
    final var responseConverter = new AnthropicMessageResponseConverter(objectMapper);
    return new AnthropicChatModelFactory(httpProxySupport, requestConverter, responseConverter);
  }

  @Bean
  @ConditionalOnMissingBean
  public OpenAiChatModelFactory aiAgentOpenAiChatModelFactory(
      AgenticAiHttpProxySupport httpProxySupport,
      @ConnectorsObjectMapper ObjectMapper objectMapper) {
    final var contentConverter = new OpenAiContentConverter(objectMapper);
    final var completionsStrategy =
        new OpenAiCompletionsStrategy(
            new OpenAiCompletionsRequestConverter(contentConverter, objectMapper),
            new OpenAiCompletionsResponseConverter(objectMapper),
            OpenAiCompletionsStreamAssembler.accumulating());
    final var responsesStrategy =
        new OpenAiResponsesStrategy(
            new OpenAiResponsesRequestConverter(contentConverter, objectMapper),
            new OpenAiResponsesResponseConverter(objectMapper),
            OpenAiResponsesStreamAssembler.accumulating());
    return new OpenAiChatModelFactory(httpProxySupport, completionsStrategy, responsesStrategy);
  }
}
