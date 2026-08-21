/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory;

import static io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory.ChatModelProviderSupport.deriveTimeoutSetting;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.genai.Client;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.types.ClientOptions;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import dev.langchain4j.model.google.genai.GoogleGenAiChatModel;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.ChatMessageConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.ChatModelHttpProxySupport;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.CloseableChatModel;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.CloseableChatModelDelegate;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.jsonschema.JsonSchemaConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.tool.ToolSpecificationConverter;
import io.camunda.connector.agenticai.aiagent.model.request.v1.GoogleVertexAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v1.GoogleVertexAiProviderConfiguration.GoogleVertexAiAuthentication.ServiceAccountCredentialsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v1.GoogleVertexAiProviderConfiguration.GoogleVertexAiConnection;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.ChatModelProperties;
import io.camunda.connector.api.error.ConnectorInputException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoogleVertexAiChatModelFactory
    extends LangChain4JChatModelFactory<GoogleVertexAiProviderConfiguration> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(GoogleVertexAiChatModelFactory.class);

  private static final String GOOGLE_CLOUD_PLATFORM_SCOPE =
      "https://www.googleapis.com/auth/cloud-platform";

  private static final Duration MAX_GOOGLE_GENAI_TIMEOUT = Duration.ofMillis(Integer.MAX_VALUE);

  private final ChatModelProperties config;
  private final ChatModelHttpProxySupport proxySupport;

  public GoogleVertexAiChatModelFactory(
      ChatModelProperties config,
      ChatModelHttpProxySupport proxySupport,
      ChatMessageConverter chatMessageConverter,
      ToolSpecificationConverter toolSpecificationConverter,
      JsonSchemaConverter jsonSchemaConverter) {
    super(chatMessageConverter, toolSpecificationConverter, jsonSchemaConverter);
    this.config = config;
    this.proxySupport = proxySupport;
  }

  @Override
  public String providerType() {
    return GoogleVertexAiProviderConfiguration.GOOGLE_VERTEX_AI_ID;
  }

  @Override
  public CloseableChatModel createChatModel(GoogleVertexAiProviderConfiguration vertexAi) {
    final var connection = vertexAi.googleVertexAi();
    final var client = createGoogleGenAiClient(connection);

    final var builder =
        GoogleGenAiChatModel.builder()
            .client(client)
            .modelName(connection.model().model())
            .maxRetries(0);

    final var modelParameters = connection.model().parameters();
    if (modelParameters != null) {
      Optional.ofNullable(modelParameters.maxOutputTokens()).ifPresent(builder::maxOutputTokens);
      Optional.ofNullable(modelParameters.temperature())
          .map(Float::doubleValue)
          .ifPresent(builder::temperature);
      Optional.ofNullable(modelParameters.topP()).map(Float::doubleValue).ifPresent(builder::topP);
      Optional.ofNullable(modelParameters.topK()).ifPresent(builder::topK);
    }

    // GoogleGenAiChatModel is not AutoCloseable and never exposes its client, so we build the
    // client ourselves and close that instead - otherwise every job execution leaks the OkHttp
    // dispatcher and connection pool.
    return new CloseableChatModelDelegate(builder.build(), client);
  }

  private Client createGoogleGenAiClient(GoogleVertexAiConnection connection) {
    final var apiTimeout =
        deriveTimeoutSetting("Google Vertex AI model call", config, connection.timeouts(), LOGGER);

    final var httpOptions =
        HttpOptions.builder()
            .retryOptions(HttpRetryOptions.builder().attempts(1).build())
            .timeout(toGoogleGenAiTimeoutMillis(apiTimeout));

    Optional.ofNullable(connection.endpoint())
        .filter(StringUtils::isNotBlank)
        .ifPresent(httpOptions::baseUrl);

    final var clientBuilder =
        Client.builder()
            .vertexAI(true)
            .project(connection.projectId())
            .location(connection.region())
            .httpOptions(httpOptions.build());

    proxySupport
        .createGoogleGenAiProxyOptions(connection.endpoint(), connection.region())
        .ifPresent(
            proxyOptions ->
                clientBuilder.clientOptions(
                    ClientOptions.builder().proxyOptions(proxyOptions).build()));

    if (connection.authentication() instanceof ServiceAccountCredentialsAuthentication sac) {
      clientBuilder.credentials(createGoogleServiceAccountCredentials(sac));
    }

    try {
      // application default credentials are resolved eagerly here
      return clientBuilder.build();
    } catch (GenAiIOException | IllegalArgumentException e) {
      LOGGER.error("Failed to create Google Vertex AI client", e);
      throw new ConnectorInputException("Failed to create Google Vertex AI client", e);
    }
  }

  /**
   * {@code HttpOptions.timeout} only accepts an {@code Integer} millisecond value, while the
   * connector accepts any positive {@link Duration}. Values above {@code Integer.MAX_VALUE} ms
   * (~24.8 days) would silently overflow on a raw cast, so reject them with a clear input error
   * instead.
   */
  private int toGoogleGenAiTimeoutMillis(Duration apiTimeout) {
    if (apiTimeout.compareTo(MAX_GOOGLE_GENAI_TIMEOUT) > 0) {
      throw new ConnectorInputException(
          "Configured timeout of %s exceeds the maximum supported by the Google GenAI SDK (%dms)"
              .formatted(apiTimeout, Integer.MAX_VALUE));
    }

    // a positive sub-millisecond timeout would otherwise truncate to 0, which OkHttp treats as
    // an unlimited call timeout rather than the shortest possible one
    return (int) Math.max(1, apiTimeout.toMillis());
  }

  private GoogleCredentials createGoogleServiceAccountCredentials(
      ServiceAccountCredentialsAuthentication sac) {
    try {
      // Credentials read from a key file carry no scopes. google-genai only scopes the
      // application default credentials it resolves itself and passes these through verbatim,
      // so without this the token request fails with invalid_scope.
      return ServiceAccountCredentials.fromStream(
              new ByteArrayInputStream(sac.jsonKey().getBytes(StandardCharsets.UTF_8)))
          .createScoped(GOOGLE_CLOUD_PLATFORM_SCOPE);
    } catch (IOException e) {
      LOGGER.error("Failed to parse service account credentials", e);
      throw new ConnectorInputException(
          "Authentication failed for provided service account credentials", e);
    }
  }
}
