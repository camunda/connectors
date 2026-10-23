/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.gemini;

import java.util.Locale;
import java.util.Set;

final class GeminiEndpoints {

  /** Vertex AI's two multi-regional locations, mirroring {@code com.google.genai.ApiClient}. */
  private static final Set<String> MULTI_REGIONAL_LOCATIONS = Set.of("us", "eu");

  private GeminiEndpoints() {}

  /**
   * Resolves the default Google GenAI API host for the given Vertex AI region, mirroring {@code
   * com.google.genai.ApiClient}'s own (package-private, unexported) resolution: the global endpoint
   * host for {@code global}, the multi-regional host for {@code us}/{@code eu}, otherwise the
   * regional endpoint host.
   */
  static String defaultGoogleGenAiBaseUrl(String region) {
    if ("global".equalsIgnoreCase(region)) {
      return "https://aiplatform.googleapis.com";
    }
    if (MULTI_REGIONAL_LOCATIONS.contains(region.toLowerCase(Locale.ROOT))) {
      return "https://aiplatform.%s.rep.googleapis.com".formatted(region.toLowerCase(Locale.ROOT));
    }
    return "https://%s-aiplatform.googleapis.com".formatted(region);
  }
}
