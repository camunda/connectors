/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.service;

import com.google.auth.oauth2.GoogleCredentials;
import io.camunda.connector.idp.extraction.client.extraction.AwsTextrtactExtractionClient;
import io.camunda.connector.idp.extraction.client.extraction.GcpDocumentAiExtractionClient;
import io.camunda.connector.idp.extraction.client.extraction.base.MlExtractor;
import io.camunda.connector.idp.extraction.model.*;
import io.camunda.connector.idp.extraction.model.providers.AwsProvider;
import io.camunda.connector.idp.extraction.model.providers.GcpProvider;
import io.camunda.connector.idp.extraction.model.providers.ProviderConfig;
import io.camunda.connector.idp.extraction.model.providers.gcp.DocumentAiRequestConfiguration;
import io.camunda.connector.idp.extraction.utils.AwsUtil;
import io.camunda.connector.idp.extraction.utils.GcsUtil;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

public class StructuredService implements ExtractionService {

  private static final Logger LOGGER = LoggerFactory.getLogger(StructuredService.class);

  public StructuredService() {}

  @Override
  public Object extract(ExtractionRequest extractionRequest) {
    final var input = extractionRequest.input();
    long startTime = System.currentTimeMillis();
    MlExtractor mlExtractor = getMlExtractor(extractionRequest.baseRequest());
    LOGGER.info("Starting {} document analysis", mlExtractor.getClass().getSimpleName());

    StructuredExtractionResponse results =
        mlExtractor.runDocumentAnalysis(extractionRequest.input().document());
    StructuredExtractionResult processedResults = processExtractedData(results, input);

    long endTime = System.currentTimeMillis();
    LOGGER.info(
        "{} document analysis took {} ms",
        mlExtractor.getClass().getSimpleName(),
        (endTime - startTime));
    return processedResults;
  }

  private MlExtractor getMlExtractor(ProviderConfig providerConfig) {
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

  /**
   * Processes extracted key-value pairs and confidence scores by formatting variable names and
   * filtering out excluded or empty values.
   *
   * @param response The StructuredExtractionResponse containing both extracted fields and
   *     confidence scores
   * @param input The extraction request data containing filtering rules
   * @return A StructuredExtractionResult with processed extracted fields and confidence scores
   */
  private StructuredExtractionResult processExtractedData(
      StructuredExtractionResponse response, ExtractionRequestData input) {
    Map<String, Object> parsedResults = new HashMap<>();
    Map<String, Object> processedConfidenceScores = new HashMap<>();
    Map<String, Polygon> processedGeometry = new HashMap<>();
    Map<String, String> originalKeys = new HashMap<>();

    response
        .extractedFields()
        .forEach(
            (key, value) -> {
              String variableName;
              // Check if key variable should be overridden by renameMappings value
              if (input.renameMappings() != null && input.renameMappings().containsKey(key)) {
                variableName = input.renameMappings().get(key);
              } else {
                variableName = formatZeebeVariableName(key, input.delimiter());
              }
              Object confidenceScore = response.confidenceScore().get(key);

              // Check if field should be included based on includedFields filter
              boolean shouldInclude;
              if (input.includedFields() == null || input.includedFields().isEmpty()) {
                shouldInclude = true;
              } else {
                shouldInclude = input.includedFields().contains(key);
              }

              // Include the field if it should be included and value is not null
              if (shouldInclude && value != null) {
                parsedResults.put(variableName, value);
                originalKeys.put(variableName, key);
                processedGeometry.put(variableName, response.geometry().get(key));
                if (confidenceScore != null) {
                  processedConfidenceScores.put(variableName, confidenceScore);
                }
              }
            });

    return new StructuredExtractionResult(
        parsedResults, processedConfidenceScores, originalKeys, processedGeometry);
  }

  /**
   * Formats a string to be a valid Zeebe variable name by: - Removing all characters except
   * alphanumeric, underscores, and dashes - Converting to lowercase
   *
   * @param input The string to format
   * @param delimiter The character to replace spaces with
   * @return A valid Zeebe variable name
   */
  public static String formatZeebeVariableName(String input, String delimiter) {
    if (input == null) {
      return null;
    }
    String actualDelimiter = delimiter != null ? delimiter : "_";
    String trimmed = input.trim();
    String sanitized = trimmed.replaceAll("[^a-zA-Z0-9_\\- ]", "").trim();
    String delimited = sanitized.replaceAll("\\s+", actualDelimiter);
    return delimited.toLowerCase();
  }
}
