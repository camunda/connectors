/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class AwsLlmModelUtil {

  // Models that require cross-region inference (marked with asterisks in AWS docs)
  private static final Set<String> CROSS_REGION_ONLY_MODELS =
      Set.of(
          // Amazon Nova models
          "amazon.nova-lite-v1:0",
          "amazon.nova-micro-v1:0",
          "amazon.nova-premier-v1:0",
          "amazon.nova-pro-v1:0",

          // Anthropic Claude models
          "anthropic.claude-3-5-haiku-20241022-v1:0",
          "anthropic.claude-3-5-sonnet-20241022-v2:0",
          "anthropic.claude-3-7-sonnet-20250219-v1:0",
          "anthropic.claude-opus-4-20250514-v1:0",
          "anthropic.claude-sonnet-4-20250514-v1:0",

          // Meta Llama models
          "meta.llama3-1-405b-instruct-v1:0",
          "meta.llama4-maverick-17b-instruct-v1:0",
          "meta.llama4-scout-17b-instruct-v1:0",

          // Mistral models
          "mistral.pixtral-large-2502-v1:0",

          // Writer models
          "writer.palmyra-x4-v1:0",
          "writer.palmyra-x5-v1:0",

          // DeepSeek models
          "deepseek.r1-v1:0");

  // Regional groupings for cross-region inference
  private static final Map<String, String> REGION_TO_PREFIX;

  static {
    Map<String, String> regionMap = new HashMap<>();

    // US regions
    regionMap.put("us-east-1", "us");
    regionMap.put("us-east-2", "us");
    regionMap.put("us-west-1", "us");
    regionMap.put("us-west-2", "us");
    regionMap.put("us-gov-east-1", "us-gov");
    regionMap.put("us-gov-west-1", "us-gov");

    // EU regions
    regionMap.put("eu-central-1", "eu");
    regionMap.put("eu-north-1", "eu");
    regionMap.put("eu-south-1", "eu");
    regionMap.put("eu-south-2", "eu");
    regionMap.put("eu-west-1", "eu");
    regionMap.put("eu-west-2", "eu");
    regionMap.put("eu-west-3", "eu");

    // APAC regions
    regionMap.put("ap-northeast-1", "apac");
    regionMap.put("ap-northeast-2", "apac");
    regionMap.put("ap-northeast-3", "apac");
    regionMap.put("ap-south-1", "apac");
    regionMap.put("ap-south-2", "apac");
    regionMap.put("ap-southeast-1", "apac");
    regionMap.put("ap-southeast-2", "apac");
    regionMap.put("ap-southeast-4", "apac");

    REGION_TO_PREFIX = Map.copyOf(regionMap);
  }

  /**
   * Processes the model ID to add proper region prefix for cross-region inference if needed.
   *
   * @param modelId The original model ID (e.g., "anthropic.claude-3-5-sonnet-20241022-v2:0")
   * @param userRegion The AWS region the user is operating in (e.g., "us-east-1")
   * @return The processed model ID with regional prefix if needed for cross-region inference
   */
  public static String processModelIdForCrossRegion(String modelId, String userRegion) {
    if (modelId == null || modelId.trim().isEmpty()) {
      throw new IllegalArgumentException("Model ID cannot be null or empty");
    }

    if (userRegion == null || userRegion.trim().isEmpty()) {
      throw new IllegalArgumentException("User region cannot be null or empty");
    }

    // Normalize inputs
    String normalizedModelId = modelId.trim();
    String normalizedRegion = userRegion.trim().toLowerCase();

    // If model ID already has a regional prefix, return as-is
    if (hasRegionalPrefix(normalizedModelId)) {
      return normalizedModelId;
    }

    // Check if this model requires cross-region inference
    if (!requiresCrossRegionInference(normalizedModelId)) {
      return normalizedModelId;
    }

    // Get the appropriate regional prefix
    String regionPrefix = REGION_TO_PREFIX.get(normalizedRegion);
    if (regionPrefix == null) {
      // If region is not in our mapping, return original model ID
      // This handles cases like regions not supported for cross-region inference
      return normalizedModelId;
    }

    // Add the regional prefix
    return regionPrefix + "." + normalizedModelId;
  }

  /**
   * Checks if the model ID already has a regional prefix.
   *
   * @param modelId The model ID to check
   * @return true if the model ID starts with a regional prefix (us., eu., apac., us-gov.)
   */
  private static boolean hasRegionalPrefix(String modelId) {
    return modelId.startsWith("us.")
        || modelId.startsWith("eu.")
        || modelId.startsWith("apac.")
        || modelId.startsWith("us-gov.");
  }

  /**
   * Determines if a model requires cross-region inference based on AWS documentation. Models marked
   * with asterisks in the AWS Bedrock supported models table require cross-region inference.
   *
   * @param modelId The model ID to check (without regional prefix)
   * @return true if the model requires cross-region inference
   */
  private static boolean requiresCrossRegionInference(String modelId) {
    return CROSS_REGION_ONLY_MODELS.contains(modelId);
  }

  /**
   * Gets the regional prefix for a given AWS region.
   *
   * @param region The AWS region (e.g., "us-east-1")
   * @return The regional prefix (e.g., "us") or null if region is not supported
   */
  public static String getRegionalPrefix(String region) {
    if (region == null) {
      return null;
    }
    return REGION_TO_PREFIX.get(region.trim().toLowerCase());
  }

  /**
   * Checks if a region supports cross-region inference.
   *
   * @param region The AWS region to check
   * @return true if the region supports cross-region inference
   */
  public static boolean supportsCrossRegionInference(String region) {
    return getRegionalPrefix(region) != null;
  }
}
