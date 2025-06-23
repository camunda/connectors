/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.service;

import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.idp.extraction.caller.DocumentAiCaller;
import io.camunda.connector.idp.extraction.caller.PollingTextractCaller;
import io.camunda.connector.idp.extraction.model.*;
import io.camunda.connector.idp.extraction.model.providers.AwsProvider;
import io.camunda.connector.idp.extraction.model.providers.GcpProvider;
import io.camunda.connector.idp.extraction.supplier.S3ClientSupplier;
import io.camunda.connector.idp.extraction.supplier.TextractClientSupplier;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StructuredService implements ExtractionService {

  private static final Logger LOGGER = LoggerFactory.getLogger(StructuredService.class);
  private final PollingTextractCaller pollingTextractCaller;
  private final DocumentAiCaller documentAiCaller;
  private final TextractClientSupplier textractClientSupplier;
  private final S3ClientSupplier s3ClientSupplier;

  public StructuredService() {
    this.pollingTextractCaller = new PollingTextractCaller();
    this.documentAiCaller = new DocumentAiCaller();
    this.textractClientSupplier = new TextractClientSupplier();
    this.s3ClientSupplier = new S3ClientSupplier();
  }

  public StructuredService(
      PollingTextractCaller pollingTextractCaller,
      DocumentAiCaller documentAiCaller,
      TextractClientSupplier textractClientSupplier,
      S3ClientSupplier s3ClientSupplier) {
    this.pollingTextractCaller = pollingTextractCaller;
    this.documentAiCaller = documentAiCaller;
    this.textractClientSupplier = textractClientSupplier;
    this.s3ClientSupplier = s3ClientSupplier;
  }

  @Override
  public Object extract(ExtractionRequest extractionRequest) {
    final var input = extractionRequest.input();
    return switch (extractionRequest.baseRequest()) {
      case AwsProvider aws -> extractUsingTextract(input, aws);
      case GcpProvider gcp -> extractUsingGcp(input, gcp);
      default ->
          throw new IllegalStateException(
              "Unsupported provider for structured extraction: " + extractionRequest.baseRequest());
    };
  }

  private StructuredExtractionResult extractUsingTextract(
      ExtractionRequestData input, AwsProvider baseRequest) {
    try {
      long startTime = System.currentTimeMillis();

      StructuredExtractionResponse results =
          pollingTextractCaller.extractKeyValuePairsWithConfidence(
              input.document(),
              baseRequest.getS3BucketName(),
              textractClientSupplier.getTextractClient(baseRequest),
              s3ClientSupplier.getAsyncS3Client(baseRequest));

      StructuredExtractionResult processedResults = processExtractedData(results, input);

      long endTime = System.currentTimeMillis();
      LOGGER.info("Aws content extraction took {} ms", (endTime - startTime));
      return processedResults;
    } catch (Exception e) {
      LOGGER.error("Document extraction failed: {}", e.getMessage());
      throw new ConnectorException(e);
    }
  }

  private StructuredExtractionResult extractUsingGcp(
      ExtractionRequestData input, GcpProvider provider) {
    try {
      long startTime = System.currentTimeMillis();

      StructuredExtractionResponse results =
          documentAiCaller.extractKeyValuePairsWithConfidence(input, provider);
      StructuredExtractionResult processedResults = processExtractedData(results, input);

      long endTime = System.currentTimeMillis();
      LOGGER.info("Document AI content extraction took {} ms", (endTime - startTime));
      return processedResults;
    } catch (Exception e) {
      LOGGER.error("Document extraction failed: {}", e.getMessage());
      throw new ConnectorException(e);
    }
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
    Map<String, Float> processedConfidenceScores = new HashMap<>();
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
              Float confidenceScore = response.confidenceScore().get(key);

              // if the list is empty will not filter any fields.
              if ((input.includedFields() == null
                      || input.includedFields().isEmpty()
                      || input.includedFields().contains(key))
                  && value != null) {
                parsedResults.put(variableName, value);
                originalKeys.put(variableName, key);

                if (confidenceScore != null) {
                  processedConfidenceScores.put(variableName, confidenceScore);
                }
              }
            });

    return new StructuredExtractionResult(parsedResults, processedConfidenceScores, originalKeys);
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
