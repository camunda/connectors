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
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.authentication.oauth.OAuthClientCredentialsTokenResolver;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.azure.EntraIdTokenCredentialFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.bedrock.BedrockConverseChatModelFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.bedrock.BedrockConverseContentConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.bedrock.BedrockConverseRequestConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.bedrock.BedrockConverseResponseConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.gemini.GeminiChatModelFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.gemini.GeminiContentConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.gemini.GeminiContentRequestConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.gemini.GeminiContentResponseConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.OpenAiChatModelFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.OpenAiContentConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.OpenAiFoundryCredentialResolver;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsRequestConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsResponseConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsStrategy;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsStreamAssembler;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.responses.OpenAiResponsesRequestConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.responses.OpenAiResponsesResponseConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.responses.OpenAiResponsesStrategy;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.responses.OpenAiResponsesStreamAssembler;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties;
import io.camunda.connector.agenticai.common.AgenticAiHttpProxySupport;
import io.camunda.connector.http.client.authentication.OAuthService;
import io.camunda.connector.http.client.authentication.OAuthTokenCache;
import io.camunda.connector.http.client.authentication.OAuthTokenCacheHolder;
import io.camunda.connector.http.client.client.apache.CustomApacheHttpClient;
import io.camunda.connector.runtime.annotation.ConnectorsObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
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
  public BedrockConverseChatModelFactory aiAgentBedrockConverseChatModelFactory(
      AgenticAiConnectorsConfigurationProperties configuration,
      AgenticAiHttpProxySupport httpProxySupport,
      @ConnectorsObjectMapper ObjectMapper objectMapper) {
    final var contentConverter = new BedrockConverseContentConverter(objectMapper);
    final var requestConverter =
        new BedrockConverseRequestConverter(contentConverter, objectMapper);
    final var responseConverter = new BedrockConverseResponseConverter();
    return new BedrockConverseChatModelFactory(
        configuration.aiagent().chatModel(),
        httpProxySupport,
        requestConverter,
        responseConverter,
        objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public EntraIdTokenCredentialFactory aiAgentEntraIdTokenCredentialFactory(
      AgenticAiConnectorsConfigurationProperties configuration,
      AgenticAiHttpProxySupport httpProxySupport) {
    return new EntraIdTokenCredentialFactory(
        httpProxySupport, configuration.aiagent().chatModel().azure().credentialCache());
  }

  @Bean
  @ConditionalOnMissingBean
  public OpenAiFoundryCredentialResolver aiAgentOpenAiFoundryCredentialResolver(
      EntraIdTokenCredentialFactory entraIdTokenCredentialFactory) {
    return new OpenAiFoundryCredentialResolver(entraIdTokenCredentialFactory);
  }

  /**
   * The selected cache is explicitly re-registered in {@link OAuthTokenCacheHolder}: {@code
   * ConnectorsAutoConfiguration}'s own {@code OAuthTokenCache} bean is
   * {@code @ConditionalOnMissingBean}, so if a custom {@link OAuthTokenCache} bean is present
   * anywhere in the context, that bean's registration is skipped, and without this call the holder
   * would fall back to lazily creating a second, different default instance the first time
   * non-Spring HTTP client code reaches it -- silently diverging from the cache this resolver uses.
   */
  @Bean
  @ConditionalOnMissingBean
  public OAuthClientCredentialsTokenResolver aiAgentOAuthClientCredentialsTokenResolver(
      ObjectProvider<OAuthTokenCache> oAuthTokenCacheProvider) {
    final var oAuthTokenCache = oAuthTokenCacheProvider.getIfAvailable(OAuthTokenCacheHolder::get);
    OAuthTokenCacheHolder.set(oAuthTokenCache);
    return new OAuthClientCredentialsTokenResolver(
        new OAuthService(), oAuthTokenCache, new CustomApacheHttpClient());
  }

  @Bean
  @ConditionalOnMissingBean
  public OpenAiChatModelFactory aiAgentOpenAiChatModelFactory(
      AgenticAiHttpProxySupport httpProxySupport,
      OpenAiFoundryCredentialResolver openAiFoundryCredentialResolver,
      OAuthClientCredentialsTokenResolver oAuthClientCredentialsTokenResolver,
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
    return new OpenAiChatModelFactory(
        httpProxySupport,
        completionsStrategy,
        responsesStrategy,
        openAiFoundryCredentialResolver,
        oAuthClientCredentialsTokenResolver);
  }

  @Bean
  @ConditionalOnMissingBean
  public GeminiChatModelFactory aiAgentGeminiChatModelFactory(
      AgenticAiHttpProxySupport httpProxySupport,
      @ConnectorsObjectMapper ObjectMapper objectMapper) {
    final var contentConverter = new GeminiContentConverter(objectMapper);
    final var requestConverter = new GeminiContentRequestConverter(contentConverter);
    final var responseConverter = new GeminiContentResponseConverter();
    return new GeminiChatModelFactory(httpProxySupport, requestConverter, responseConverter);
  }
}
