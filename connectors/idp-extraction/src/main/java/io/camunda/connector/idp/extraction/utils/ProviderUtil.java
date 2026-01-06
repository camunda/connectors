/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.utils;

import com.google.auth.oauth2.GoogleCredentials;
import io.camunda.connector.idp.extraction.client.ai.AzureAiFoundryClient;
import io.camunda.connector.idp.extraction.client.ai.AzureOpenAiClient;
import io.camunda.connector.idp.extraction.client.ai.BedrockAiClient;
import io.camunda.connector.idp.extraction.client.ai.OpenAiClient;
import io.camunda.connector.idp.extraction.client.ai.VertexAiClient;
import io.camunda.connector.idp.extraction.client.ai.base.AiClient;
import io.camunda.connector.idp.extraction.client.extraction.AwsTextrtactExtractionClient;
import io.camunda.connector.idp.extraction.client.extraction.AzureDocumentIntelligenceExtractionClient;
import io.camunda.connector.idp.extraction.client.extraction.GcpDocumentAiExtractionClient;
import io.camunda.connector.idp.extraction.client.extraction.PdfBoxExtractionClient;
import io.camunda.connector.idp.extraction.client.extraction.base.MlExtractor;
import io.camunda.connector.idp.extraction.client.extraction.base.TextExtractor;
import io.camunda.connector.idp.extraction.model.ConverseData;
import io.camunda.connector.idp.extraction.model.ExtractionRequest;
import io.camunda.connector.idp.extraction.model.providers.AwsProvider;
import io.camunda.connector.idp.extraction.model.providers.AzureProvider;
import io.camunda.connector.idp.extraction.model.providers.GcpProvider;
import io.camunda.connector.idp.extraction.model.providers.OpenAiProvider;
import io.camunda.connector.idp.extraction.model.providers.ProviderConfig;
import io.camunda.connector.idp.extraction.model.providers.gcp.DocumentAiRequestConfiguration;
import io.camunda.connector.idp.extraction.model.providers.gcp.VertexRequestConfiguration;
import io.camunda.connector.idp.extraction.request.common.ai.AiProvider;
import io.camunda.connector.idp.extraction.request.common.ai.AzureAiRequest;
import io.camunda.connector.idp.extraction.request.common.ai.BedrockAiRequest;
import io.camunda.connector.idp.extraction.request.common.ai.OpenAiRequest;
import io.camunda.connector.idp.extraction.request.common.ai.VertexAiRequest;
import io.camunda.connector.idp.extraction.request.common.extraction.DocumentAiExtractorRequest;
import io.camunda.connector.idp.extraction.request.common.extraction.DocumentIntelligenceExtractorRequest;
import io.camunda.connector.idp.extraction.request.common.extraction.ExtractionProvider;
import io.camunda.connector.idp.extraction.request.common.extraction.TextractExtractorRequest;
import jakarta.validation.Valid;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

public class ProviderUtil {

  // legacy retrieval of text extractor from ProviderConfig
  public static TextExtractor getTextExtractor(ProviderConfig providerConfig) {
    return switch (providerConfig) {
      case AwsProvider aws -> {
        AwsCredentialsProvider credentialsProvider =
            AwsUtil.credentialsProvider(aws.getAuthentication());
        yield new AwsTextrtactExtractionClient(
            credentialsProvider, aws.getConfiguration().region(), aws.getS3BucketName());
      }
      case AzureProvider azure ->
          new AzureDocumentIntelligenceExtractionClient(
              azure.getDocumentIntelligenceConfiguration().getEndpoint(),
              azure.getDocumentIntelligenceConfiguration().getApiKey());
      case OpenAiProvider openAi -> new PdfBoxExtractionClient();
      case GcpProvider gcp -> null;
      default -> throw new IllegalStateException("Unexpected value: " + providerConfig);
    };
  }

  public static MlExtractor getMlExtractor(ExtractionProvider extractor) {
    return switch (extractor) {
      case DocumentAiExtractorRequest docAiRequest -> {
        GoogleCredentials credentials =
            GcsUtil.getCredentials(
                docAiRequest.authType(),
                docAiRequest.bearerToken(),
                docAiRequest.serviceAccountJson(),
                docAiRequest.oauthClientId(),
                docAiRequest.oauthClientSecret(),
                docAiRequest.oauthRefreshToken());
        yield new GcpDocumentAiExtractionClient(
            credentials,
            docAiRequest.projectId(),
            docAiRequest.region(),
            docAiRequest.processorId());
      }
      case TextractExtractorRequest textractRequest -> {
        AwsCredentialsProvider credentialsProvider =
            AwsUtil.credentialsProvider(
                textractRequest.awsAuthType(),
                textractRequest.accessKey(),
                textractRequest.secretKey());
        yield new AwsTextrtactExtractionClient(
            credentialsProvider, textractRequest.region(), textractRequest.bucketName());
      }
      default ->
          throw new IllegalStateException(
              "Extractor not supported: " + extractor.getClass().getSimpleName());
    };
  }

  public static MlExtractor getMlExtractor(ProviderConfig providerConfig) {
    return switch (providerConfig) {
      case AwsProvider aws -> {
        AwsCredentialsProvider credentialsProvider =
            AwsUtil.credentialsProvider(aws.getAuthentication());
        yield new AwsTextrtactExtractionClient(
            credentialsProvider, aws.getConfiguration().region(), aws.getS3BucketName());
      }
      case GcpProvider gcp -> {
        DocumentAiRequestConfiguration config =
            (DocumentAiRequestConfiguration) gcp.getConfiguration();
        GoogleCredentials credentials =
            GcsUtil.getCredentials(
                gcp.getAuthentication().authType(),
                gcp.getAuthentication().bearerToken(),
                gcp.getAuthentication().serviceAccountJson(),
                gcp.getAuthentication().oauthClientId(),
                gcp.getAuthentication().oauthClientSecret(),
                gcp.getAuthentication().oauthRefreshToken());
        yield new GcpDocumentAiExtractionClient(
            credentials, config.getProjectId(), config.getRegion(), config.getProcessorId());
      }
      default ->
          throw new IllegalStateException(
              "Unsupported provider for structured extraction: "
                  + providerConfig.getClass().getSimpleName());
    };
  }

  public static TextExtractor getTextExtractor(@Valid ExtractionProvider extractor) {
    if (extractor == null) {
      throw new IllegalArgumentException(
          "ExtractionProvider argument 'extractor' must not be null");
    }
    return switch (extractor) {
      case DocumentIntelligenceExtractorRequest docIntelligenceRequest ->
          new AzureDocumentIntelligenceExtractionClient(
              docIntelligenceRequest.endpoint(), docIntelligenceRequest.apiKey());
      case DocumentAiExtractorRequest docAiRequest -> {
        GoogleCredentials credentials =
            GcsUtil.getCredentials(
                docAiRequest.authType(),
                docAiRequest.bearerToken(),
                docAiRequest.serviceAccountJson(),
                docAiRequest.oauthClientId(),
                docAiRequest.oauthClientSecret(),
                docAiRequest.oauthRefreshToken());
        yield new GcpDocumentAiExtractionClient(
            credentials,
            docAiRequest.projectId(),
            docAiRequest.region(),
            docAiRequest.processorId());
      }
      case TextractExtractorRequest textractRequest -> {
        AwsCredentialsProvider credentialsProvider =
            AwsUtil.credentialsProvider(
                textractRequest.awsAuthType(),
                textractRequest.accessKey(),
                textractRequest.secretKey());
        yield new AwsTextrtactExtractionClient(
            credentialsProvider, textractRequest.region(), textractRequest.bucketName());
      }
      case ExtractionProvider.ApachePdfBoxExtractorRequest pdfbox -> new PdfBoxExtractionClient();
      case ExtractionProvider.MultimodalExtractorRequest multimodal -> null;
    };
  }

  // legacy retrieval of ai client from ExtractionRequest
  public static AiClient getAiClient(ExtractionRequest extractionRequest) {
    final var input = extractionRequest.input();
    return switch (extractionRequest.baseRequest()) {
      case AwsProvider aws -> {
        AwsCredentialsProvider credentialsProvider =
            AwsUtil.credentialsProvider(aws.getAuthentication());
        yield new BedrockAiClient(
            credentialsProvider, aws.getConfiguration().region(), input.converseData());
      }
      case OpenAiProvider openAi ->
          new OpenAiClient(
              openAi.getOpenAiEndpoint(), openAi.getOpenAiHeaders(), input.converseData());
      case GcpProvider gemini -> {
        var config = (VertexRequestConfiguration) gemini.getConfiguration();
        GoogleCredentials credentials =
            GcsUtil.getCredentials(
                gemini.getAuthentication().authType(),
                gemini.getAuthentication().bearerToken(),
                gemini.getAuthentication().serviceAccountJson(),
                gemini.getAuthentication().oauthClientId(),
                gemini.getAuthentication().oauthClientSecret(),
                gemini.getAuthentication().oauthRefreshToken());
        yield new VertexAiClient(
            credentials, config.getProjectId(), config.getRegion(), input.converseData());
      }
      case AzureProvider azure -> {
        if (azure.getAiFoundryConfig().isUsingOpenAI()) {
          yield new AzureOpenAiClient(
              azure.getAiFoundryConfig().getEndpoint(),
              azure.getAiFoundryConfig().getApiKey(),
              input.converseData());
        } else {
          yield new AzureAiFoundryClient(
              azure.getAiFoundryConfig().getEndpoint(),
              azure.getAiFoundryConfig().getApiKey(),
              input.converseData());
        }
      }
    };
  }

  public static AiClient getAiClient(@Valid AiProvider aiProvider, ConverseData converseData) {
    return switch (aiProvider) {
      case AzureAiRequest azureRequest -> {
        if (azureRequest.usingOpenAI()) {
          yield new AzureOpenAiClient(azureRequest.endpoint(), azureRequest.apiKey(), converseData);
        } else {
          yield new AzureAiFoundryClient(
              azureRequest.endpoint(), azureRequest.apiKey(), converseData);
        }
      }
      case VertexAiRequest vertexRequest -> {
        GoogleCredentials credentials =
            GcsUtil.getCredentials(
                vertexRequest.authType(),
                vertexRequest.bearerToken(),
                vertexRequest.serviceAccountJson(),
                vertexRequest.oauthClientId(),
                vertexRequest.oauthClientSecret(),
                vertexRequest.oauthRefreshToken());
        yield new VertexAiClient(
            credentials, vertexRequest.projectId(), vertexRequest.region(), converseData);
      }
      case BedrockAiRequest bedrockRequest -> {
        AwsCredentialsProvider credentialsProvider =
            AwsUtil.credentialsProvider(
                bedrockRequest.awsAuthType(),
                bedrockRequest.accessKey(),
                bedrockRequest.secretKey());
        yield new BedrockAiClient(credentialsProvider, bedrockRequest.region(), converseData);
      }
      case OpenAiRequest openAiRequest ->
          new OpenAiClient(
              openAiRequest.openAiEndpoint(), openAiRequest.openAiHeaders(), converseData);
    };
  }
}
