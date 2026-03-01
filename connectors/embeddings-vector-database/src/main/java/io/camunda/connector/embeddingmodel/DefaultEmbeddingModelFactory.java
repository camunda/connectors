/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.embeddingmodel;

import com.azure.core.http.ProxyOptions;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.google.auth.oauth2.ServiceAccountCredentials;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.model.azure.AzureOpenAiEmbeddingModel;
import dev.langchain4j.model.bedrock.BedrockTitanEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.vertexai.VertexAiEmbeddingModel;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.http.client.client.jdk.proxy.JdkHttpClientProxyConfigurator;
import io.camunda.connector.http.client.proxy.NonProxyHosts;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import io.camunda.connector.model.embedding.models.AzureOpenAiEmbeddingModelProvider;
import io.camunda.connector.model.embedding.models.AzureOpenAiEmbeddingModelProvider.AzureAuthentication;
import io.camunda.connector.model.embedding.models.BedrockEmbeddingModelProvider;
import io.camunda.connector.model.embedding.models.BedrockModels;
import io.camunda.connector.model.embedding.models.EmbeddingModelProvider;
import io.camunda.connector.model.embedding.models.GoogleVertexAiEmbeddingModelProvider;
import io.camunda.connector.model.embedding.models.GoogleVertexAiEmbeddingModelProvider.GoogleVertexAiAuthentication.ServiceAccountCredentialsAuthentication;
import io.camunda.connector.model.embedding.models.OpenAiEmbeddingModelProvider;
import io.camunda.connector.util.ProxyUtil;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

public class DefaultEmbeddingModelFactory {

  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultEmbeddingModelFactory.class);
  private final ProxyConfiguration proxyConfig;
  private final JdkHttpClientProxyConfigurator proxyConfigurator;

  public DefaultEmbeddingModelFactory(ProxyConfiguration proxyConfig) {
    this.proxyConfig = proxyConfig;
    this.proxyConfigurator = new JdkHttpClientProxyConfigurator(proxyConfig);
  }

  public EmbeddingModel createEmbeddingModel(EmbeddingModelProvider embeddingModelProvider) {
    return switch (embeddingModelProvider) {
      case BedrockEmbeddingModelProvider bedrock -> createBedrockEmbeddingModel(bedrock);
      case OpenAiEmbeddingModelProvider openAi -> createOpenAiEmbeddingModel(openAi);
      case AzureOpenAiEmbeddingModelProvider azureOpenAi ->
          createAzureOpenAiEmbeddingModel(azureOpenAi);
      case GoogleVertexAiEmbeddingModelProvider vertexAi -> createVertexAiEmbeddingModel(vertexAi);
    };
  }

  private EmbeddingModel createAzureOpenAiEmbeddingModel(
      AzureOpenAiEmbeddingModelProvider azureOpenAiEmbeddingModelProvider) {
    final var azureOpenAi = azureOpenAiEmbeddingModelProvider.azureOpenAi();
    AzureOpenAiEmbeddingModel.Builder builder =
        AzureOpenAiEmbeddingModel.builder()
            .endpoint(azureOpenAi.endpoint())
            .deploymentName(azureOpenAi.deploymentName());

    configureAzureOpenAiProxy(builder, azureOpenAi.endpoint());

    Optional.ofNullable(azureOpenAi.dimensions()).ifPresent(builder::dimensions);
    Optional.ofNullable(azureOpenAi.maxRetries()).ifPresent(builder::maxRetries);
    Optional.ofNullable(azureOpenAi.customHeaders()).ifPresent(builder::customHeaders);

    switch (azureOpenAi.authentication()) {
      case AzureAuthentication.AzureApiKeyAuthentication apiKey -> builder.apiKey(apiKey.apiKey());
      case AzureAuthentication.AzureClientCredentialsAuthentication auth -> {
        ClientSecretCredentialBuilder clientSecretCredentialBuilder =
            new ClientSecretCredentialBuilder()
                .clientId(auth.clientId())
                .clientSecret(auth.clientSecret())
                .tenantId(auth.tenantId());
        if (StringUtils.isNotBlank(auth.authorityHost())) {
          clientSecretCredentialBuilder.authorityHost(auth.authorityHost());
        }
        builder.tokenCredential(clientSecretCredentialBuilder.build());
      }
    }
    return builder.build();
  }

  private void configureAzureOpenAiProxy(
      AzureOpenAiEmbeddingModel.Builder builder, String endpoint) {
    final var uri = URI.create(endpoint);
    if (uri.getScheme() == null) {
      throw new IllegalArgumentException("Invalid endpoint URI: " + endpoint);
    }
    // If connector proxy env vars are not present, the Azure OpenAI client will use the system
    // properties for proxy configuration.
    proxyConfig
        .getProxyDetails(uri.getScheme())
        .ifPresent(
            proxyDetails -> {
              ProxyOptions proxyOptions =
                  new ProxyOptions(
                      ProxyOptions.Type.HTTP,
                      new InetSocketAddress(proxyDetails.host(), proxyDetails.port()));
              // Non-proxy hosts patterns are sanitized indide ProxyOptions
              proxyOptions.setNonProxyHosts(
                  NonProxyHosts.getNonProxyHostsPatterns()
                      .distinct()
                      .collect(Collectors.joining("|")));
              if (proxyDetails.hasCredentials()) {
                proxyOptions.setCredentials(proxyDetails.user(), proxyDetails.password());
              }

              builder.proxyOptions(proxyOptions);
            });
  }

  private EmbeddingModel createBedrockEmbeddingModel(
      BedrockEmbeddingModelProvider bedrockEmbeddingModelProvider) {
    final var bedrock = bedrockEmbeddingModelProvider.bedrock();

    var builder =
        BedrockTitanEmbeddingModel.builder()
            .model(bedrock.resolveSelectedModelName())
            .dimensions(
                bedrock.modelName() == BedrockModels.TitanEmbedTextV2
                    ? bedrock.dimensions().getDimensions()
                    : null)
            .normalize(
                bedrock.modelName() == BedrockModels.TitanEmbedTextV2 ? bedrock.normalize() : null)
            .maxRetries(bedrock.maxRetries());

    var bedrockClientBuilder =
        BedrockRuntimeClient.builder()
            .region(Region.of(bedrock.region()))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(bedrock.accessKey(), bedrock.secretKey())));

    // assuming the Bedrock client uses the HTTPS proxy settings, as the service endpoints are
    // HTTPS.
    SdkHttpClient httpClient =
        ProxyUtil.createAwsProxyAwareHttpClient(proxyConfig, ProxyConfiguration.HTTPS);
    bedrockClientBuilder.httpClient(httpClient);

    return builder.client(bedrockClientBuilder.build()).build();
  }

  private EmbeddingModel createOpenAiEmbeddingModel(
      OpenAiEmbeddingModelProvider openAiEmbeddingModelProvider) {
    final var openAi = openAiEmbeddingModelProvider.openAi();
    final var httpClientBuilder = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1);
    proxyConfigurator.configure(httpClientBuilder);

    OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder builder =
        OpenAiEmbeddingModel.builder()
            .apiKey(openAi.apiKey())
            .modelName(openAi.modelName())
            .httpClientBuilder(new JdkHttpClientBuilder().httpClientBuilder(httpClientBuilder));

    Optional.ofNullable(openAi.organizationId()).ifPresent(builder::organizationId);
    Optional.ofNullable(openAi.projectId()).ifPresent(builder::projectId);
    Optional.ofNullable(openAi.baseUrl()).ifPresent(builder::baseUrl);
    Optional.ofNullable(openAi.customHeaders()).ifPresent(builder::customHeaders);
    Optional.ofNullable(openAi.dimensions()).ifPresent(builder::dimensions);
    Optional.ofNullable(openAi.maxRetries()).ifPresent(builder::maxRetries);

    return builder.build();
  }

  private EmbeddingModel createVertexAiEmbeddingModel(
      GoogleVertexAiEmbeddingModelProvider provider) {
    final var googleVertexAi = provider.googleVertexAi();
    final var publisher =
        StringUtils.isNotBlank(googleVertexAi.publisher())
            ? googleVertexAi.publisher()
            : GoogleVertexAiEmbeddingModelProvider.VERTEX_AI_DEFAULT_PUBLISHER;
    VertexAiEmbeddingModel.Builder builder =
        VertexAiEmbeddingModel.builder()
            .project(googleVertexAi.projectId())
            .location(googleVertexAi.region())
            .publisher(publisher)
            .modelName(googleVertexAi.modelName())
            .outputDimensionality(googleVertexAi.dimensions())
            .taskType(VertexAiEmbeddingModel.TaskType.RETRIEVAL_DOCUMENT);

    if (googleVertexAi.authentication() instanceof ServiceAccountCredentialsAuthentication sac) {
      builder.credentials(createGoogleServiceAccountCredentials(sac));
    }

    Optional.ofNullable(googleVertexAi.maxRetries()).ifPresent(builder::maxRetries);

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
