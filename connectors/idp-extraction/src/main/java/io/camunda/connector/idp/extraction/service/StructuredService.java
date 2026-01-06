/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.service;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.idp.extraction.client.extraction.base.MlExtractor;
import io.camunda.connector.idp.extraction.model.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StructuredService {

  private static final Logger LOGGER = LoggerFactory.getLogger(StructuredService.class);

  public StructuredService() {}

  public Object extract(
      MlExtractor mlExtractor,
      List<String> includedFields,
      Map<String, String> renameMappings,
      String delimiter,
      Document document) {
    long startTime = System.currentTimeMillis();
    LOGGER.info("Starting {} document analysis", mlExtractor.getClass().getSimpleName());

    StructuredExtractionResponse results = mlExtractor.runDocumentAnalysis(document);
    StructuredExtractionResult processedResults =
        processExtractedData(results, includedFields, renameMappings, delimiter);

    long endTime = System.currentTimeMillis();
    LOGGER.info(
        "{} document analysis took {} ms",
        mlExtractor.getClass().getSimpleName(),
        (endTime - startTime));
    return processedResults;
  }

  /**
   * Processes extracted key-value pairs and confidence scores by formatting variable names and
   * filtering out excluded or empty values.
   *
   * @param response The StructuredExtractionResponse containing both extracted fields and
   *     confidence scores
   * @return A StructuredExtractionResult with processed extracted fields and confidence scores
   */
  private StructuredExtractionResult processExtractedData(
      StructuredExtractionResponse response,
      List<String> includedFields,
      Map<String, String> renameMappings,
      String delimiter) {
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
              if (renameMappings != null && renameMappings.containsKey(key)) {
                variableName = renameMappings.get(key);
              } else {
                variableName = formatZeebeVariableName(key, delimiter);
              }
              Object confidenceScore = response.confidenceScore().get(key);

              // Check if field should be included based on includedFields filter
              boolean shouldInclude;
              if (includedFields == null || includedFields.isEmpty()) {
                shouldInclude = true;
              } else {
                shouldInclude = includedFields.contains(key);
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
