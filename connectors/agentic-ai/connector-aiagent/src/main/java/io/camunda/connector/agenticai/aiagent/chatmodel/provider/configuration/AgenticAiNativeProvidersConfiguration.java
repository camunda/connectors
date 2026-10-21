/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.configuration;

import static io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.OpenAiConfiguration.PROVIDER;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.chatmodel.connection.ConnectionResolver;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.AnthropicChatModelFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.AnthropicContentConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.AnthropicMessageRequestConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.AnthropicMessageResponseConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.azure.EntraIdTokenCredentialFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.azure.FoundryCredentialResolver;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.bedrock.BedrockConverseChatModelFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.bedrock.BedrockConverseContentConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.bedrock.BedrockConverseRequestConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.bedrock.BedrockConverseResponseConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.gemini.GeminiChatModelFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.gemini.GeminiContentConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.gemini.GeminiContentRequestConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.gemini.GeminiContentResponseConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.mistral.MistralChatModelFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.mistral.MistralCompletionsStreamAssembler;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.mistral.MistralConfiguration;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.mistral.MistralDocumentUrlContentChunkStrategy;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.mistral.MistralReasoningContentDialect;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.OpenAiChatModelFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.OpenAiContentConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsContentChunkStrategy;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsContentDialect;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsRequestConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsResponseConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsStrategy;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsStreamAssembler;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.responses.OpenAiResponsesRequestConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.responses.OpenAiResponsesResponseConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.responses.OpenAiResponsesStrategy;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.responses.OpenAiResponsesStreamAssembler;
import io.camunda.connector.agenticai.aiagent.configuration.AgenticAiConnectorsConfigurationProperties;
import io.camunda.connector.agenticai.aiagent.proxy.aws.AwsSdkHttpProxySupport;
import io.camunda.connector.agenticai.aiagent.proxy.azure.AzureHttpProxySupport;
import io.camunda.connector.agenticai.common.http.AgenticAiHttpProxySupport;
import io.camunda.connector.http.client.authentication.OAuthClientCredentialsTokenResolver;
import io.camunda.connector.runtime.annotation.ConnectorsObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgenticAiNativeProvidersConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public AnthropicChatModelFactory aiAgentAnthropicChatModelFactory(
      ConnectionResolver connectionResolver, @ConnectorsObjectMapper ObjectMapper objectMapper) {
    final var contentConverter = new AnthropicContentConverter(objectMapper);
    final var requestConverter = new AnthropicMessageRequestConverter(contentConverter);
    final var responseConverter = new AnthropicMessageResponseConverter(objectMapper);
    return new AnthropicChatModelFactory(connectionResolver, requestConverter, responseConverter);
  }

  @Bean
  @ConditionalOnMissingBean
  public BedrockConverseChatModelFactory aiAgentBedrockConverseChatModelFactory(
      ConnectionResolver connectionResolver,
      AwsSdkHttpProxySupport awsSdkHttpProxySupport,
      @ConnectorsObjectMapper ObjectMapper objectMapper) {
    final var contentConverter = new BedrockConverseContentConverter(objectMapper);
    final var requestConverter =
        new BedrockConverseRequestConverter(contentConverter, objectMapper);
    final var responseConverter = new BedrockConverseResponseConverter();
    return new BedrockConverseChatModelFactory(
        connectionResolver,
        awsSdkHttpProxySupport,
        requestConverter,
        responseConverter,
        objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public EntraIdTokenCredentialFactory aiAgentEntraIdTokenCredentialFactory(
      AgenticAiConnectorsConfigurationProperties configuration,
      AzureHttpProxySupport azureHttpProxySupport) {
    return new EntraIdTokenCredentialFactory(
        azureHttpProxySupport, configuration.aiagent().chatModel().azure().credentialCache());
  }

  @Bean
  @ConditionalOnMissingBean
  public FoundryCredentialResolver aiAgentFoundryCredentialResolver(
      EntraIdTokenCredentialFactory entraIdTokenCredentialFactory) {
    return new FoundryCredentialResolver(entraIdTokenCredentialFactory);
  }

  @Bean
  @ConditionalOnMissingBean
  public ConnectionResolver aiAgentConnectionResolver(
      AgenticAiHttpProxySupport httpProxySupport,
      OAuthClientCredentialsTokenResolver oAuthClientCredentialsTokenResolver,
      FoundryCredentialResolver foundryCredentialResolver) {
    return new ConnectionResolver(
        httpProxySupport, oAuthClientCredentialsTokenResolver, foundryCredentialResolver);
  }

  @Bean
  @ConditionalOnMissingBean
  public OpenAiChatModelFactory aiAgentOpenAiChatModelFactory(
      ConnectionResolver connectionResolver, @ConnectorsObjectMapper ObjectMapper objectMapper) {
    final var contentConverter = new OpenAiContentConverter(objectMapper);
    final var completionsStrategy =
        new OpenAiCompletionsStrategy(
            new OpenAiCompletionsRequestConverter(
                PROVIDER,
                contentConverter,
                OpenAiCompletionsContentChunkStrategy.openAi(objectMapper),
                objectMapper,
                OpenAiCompletionsContentDialect.none()),
            new OpenAiCompletionsResponseConverter(
                PROVIDER, objectMapper, OpenAiCompletionsContentDialect.none()),
            OpenAiCompletionsStreamAssembler.accumulating());
    final var responsesStrategy =
        new OpenAiResponsesStrategy(
            new OpenAiResponsesRequestConverter(contentConverter, objectMapper),
            new OpenAiResponsesResponseConverter(objectMapper),
            OpenAiResponsesStreamAssembler.accumulating());
    return new OpenAiChatModelFactory(connectionResolver, completionsStrategy, responsesStrategy);
  }

  @Bean
  @ConditionalOnMissingBean
  public MistralChatModelFactory aiAgentMistralChatModelFactory(
      ConnectionResolver connectionResolver, @ConnectorsObjectMapper ObjectMapper objectMapper) {
    final var contentConverter = new OpenAiContentConverter(objectMapper);
    final var mistralReasoningContentDialect = new MistralReasoningContentDialect();
    return new MistralChatModelFactory(
        connectionResolver,
        new OpenAiCompletionsRequestConverter(
            MistralConfiguration.PROVIDER,
            contentConverter,
            new MistralDocumentUrlContentChunkStrategy(objectMapper),
            objectMapper,
            mistralReasoningContentDialect),
        new OpenAiCompletionsResponseConverter(
            MistralConfiguration.PROVIDER, objectMapper, mistralReasoningContentDialect),
        new MistralCompletionsStreamAssembler());
  }

  @Bean
  @ConditionalOnMissingBean
  public GeminiChatModelFactory aiAgentGeminiChatModelFactory(
      ConnectionResolver connectionResolver, @ConnectorsObjectMapper ObjectMapper objectMapper) {
    final var contentConverter = new GeminiContentConverter(objectMapper);
    final var requestConverter = new GeminiContentRequestConverter(contentConverter);
    final var responseConverter = new GeminiContentResponseConverter();
    return new GeminiChatModelFactory(connectionResolver, requestConverter, responseConverter);
  }
}
