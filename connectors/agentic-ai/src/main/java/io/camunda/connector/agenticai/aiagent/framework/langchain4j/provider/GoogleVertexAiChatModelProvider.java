/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider;

import com.google.auth.oauth2.ServiceAccountCredentials;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.vertexai.gemini.VertexAiGeminiChatModel;
import io.camunda.connector.agenticai.aiagent.model.request.provider.GoogleVertexAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.GoogleVertexAiProviderConfiguration.GoogleVertexAiAuthentication.ServiceAccountCredentialsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.ProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.TimeoutConfiguration;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties;
import io.camunda.connector.api.error.ConnectorInputException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoogleVertexAiChatModelProvider implements ChatModelProvider {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(GoogleVertexAiChatModelProvider.class);

  private final AgenticAiConnectorsConfigurationProperties.ChatModelProperties chatModelProperties;

  public GoogleVertexAiChatModelProvider(
      AgenticAiConnectorsConfigurationProperties agenticAiConnectorsConfigurationProperties) {
    this.chatModelProperties = agenticAiConnectorsConfigurationProperties.aiagent().chatModel();
  }

  @Override
  public String getProviderType() {
    return GoogleVertexAiProviderConfiguration.TYPE;
  }

  @Override
  public boolean supports(ProviderConfiguration providerConfiguration) {
    return providerConfiguration instanceof GoogleVertexAiProviderConfiguration;
  }

  @Override
  public ChatModel createChatModel(ProviderConfiguration providerConfiguration) {
    if (!(providerConfiguration instanceof GoogleVertexAiProviderConfiguration vertexAi)) {
      throw new IllegalArgumentException(
          "Expected GoogleVertexAiProviderConfiguration but got "
              + providerConfiguration.getClass().getSimpleName());
    }

    final var connection = vertexAi.googleVertexAi();
    final var builder =
        VertexAiGeminiChatModel.builder()
            .project(connection.projectId())
            .location(connection.region())
            .modelName(connection.model().model());

    if (connection.authentication() instanceof ServiceAccountCredentialsAuthentication sac) {
      builder.credentials(createGoogleServiceAccountCredentials(sac));
    }

    final var modelParameters = connection.model().parameters();
    if (modelParameters != null) {
      Optional.ofNullable(modelParameters.maxOutputTokens()).ifPresent(builder::maxOutputTokens);
      Optional.ofNullable(modelParameters.temperature()).ifPresent(builder::temperature);
      Optional.ofNullable(modelParameters.topP()).ifPresent(builder::topP);
      Optional.ofNullable(modelParameters.topK()).ifPresent(builder::topK);
    }

    return builder.build();
  }

  private ServiceAccountCredentials createGoogleServiceAccountCredentials(
      ServiceAccountCredentialsAuthentication sac) {
    try {
      return ServiceAccountCredentials.fromStream(
          new ByteArrayInputStream(sac.jsonKey().getBytes(StandardCharsets.UTF_8)));
    } catch (IOException e) {
      LOGGER.error("Failed to parse service account credentials", e);
      throw new ConnectorInputException(
          "Authentication failed for provided service account credentials", e);
    }
  }

  private Duration deriveTimeoutSetting(TimeoutConfiguration timeoutConfiguration) {
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
