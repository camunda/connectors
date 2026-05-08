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
import io.camunda.connector.agenticai.aiagent.model.request.provider.GoogleGenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.GoogleGenAiProviderConfiguration.GoogleGenAiAuthentication.ServiceAccountCredentialsAuthentication;
import io.camunda.connector.api.error.ConnectorInputException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoogleVertexAiChatModelProvider
    implements ChatModelProvider<GoogleGenAiProviderConfiguration> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(GoogleVertexAiChatModelProvider.class);

  @Override
  public String type() {
    return GoogleGenAiProviderConfiguration.GOOGLE_GENAI_ID;
  }

  @Override
  public ChatModel createChatModel(GoogleGenAiProviderConfiguration vertexAi) {
    final var connection = vertexAi.googleGenAi();
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
}
