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
import io.camunda.connector.idp.extraction.model.ExtractionRequest;
import io.camunda.connector.idp.extraction.model.ExtractionRequestData;
import io.camunda.connector.idp.extraction.model.ExtractionResult;
import io.camunda.connector.idp.extraction.model.TextExtractionEngineType;
import io.camunda.connector.idp.extraction.model.providers.AwsProvider;
import io.camunda.connector.idp.extraction.model.providers.DocumentAIProvider;
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

  @Override
  public Object extract(ExtractionRequest extractionRequest) {
    final var input = extractionRequest.input();
    return switch (extractionRequest.baseRequest()) {
      case AwsProvider aws -> extractUsingTextract(input, aws);
      case DocumentAIProvider documentAi -> extractUsingDocumentAi(input, documentAi);
      default ->
          throw new IllegalStateException(
              "Unsupported provider for structured extraction: " + extractionRequest.baseRequest());
    };
  }

  private ExtractionResult extractUsingTextract(
      ExtractionRequestData input, AwsProvider baseRequest) {
    try {
      long startTime = System.currentTimeMillis();
      Map<String, Object> extractedMap;

      if (baseRequest.getExtractionEngineType() == TextExtractionEngineType.AWS_TEXTRACT) {
        Map<String, String> results = extractTextUsingTextract(input, baseRequest);
        extractedMap = processExtractedFields(results, input);
      } else {
        throw new ConnectorException(
            "Unsupported extraction engine type: " + baseRequest.getExtractionEngineType());
      }
      long endTime = System.currentTimeMillis();
      LOGGER.info("Aws content extraction took {} ms", (endTime - startTime));
      return new ExtractionResult(extractedMap);
    } catch (Exception e) {
      LOGGER.error("Document extraction failed: {}", e.getMessage());
      throw new ConnectorException(e);
    }
  }

  private Map<String, String> extractTextUsingTextract(
      ExtractionRequestData input, AwsProvider baseRequest) throws Exception {
    return pollingTextractCaller.extractKeyValuePairs(
        input.document(),
        baseRequest.getS3BucketName(),
        textractClientSupplier.getTextractClient(baseRequest),
        s3ClientSupplier.getAsyncS3Client(baseRequest));
  }

  private ExtractionResult extractUsingDocumentAi(
      ExtractionRequestData input, DocumentAIProvider baseRequest) {
    try {
      long startTime = System.currentTimeMillis();

      Map<String, String> extractedMap = documentAiCaller.extractKeyValuePairs(input, baseRequest);
      Map<String, Object> parsedResults = processExtractedFields(extractedMap, input);

      long endTime = System.currentTimeMillis();
      LOGGER.info("Document AI content extraction took {} ms", (endTime - startTime));
      return new ExtractionResult(parsedResults);
    } catch (Exception e) {
      LOGGER.error("Document extraction failed: {}", e.getMessage());
      throw new ConnectorException(e);
    }
  }

  /**
   * Processes extracted key-value pairs by formatting variable names and filtering out excluded or
   * empty values.
   *
   * @param rawResults The raw key-value pairs extracted from the document
   * @param input The extraction request data containing filtering rules
   * @return A map of processed results with formatted variable names
   */
  private Map<String, Object> processExtractedFields(
      Map<String, String> rawResults, ExtractionRequestData input) {
    Map<String, Object> parsedResults = new HashMap<>();

    rawResults.forEach(
        (key, value) -> {
          String variableName = formatZeebeVariableName(key, input.delimiter());
          if ((input.excludedFields() == null || !input.excludedFields().contains(variableName))
              && (value != null && !value.isBlank())) {
            parsedResults.put(variableName, value);
          }
        });

    return parsedResults;
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
    String delimited = input.replace(" ", delimiter);
    return delimited.replaceAll("[^a-zA-Z0-9_-]", "").toLowerCase();
  }
}
