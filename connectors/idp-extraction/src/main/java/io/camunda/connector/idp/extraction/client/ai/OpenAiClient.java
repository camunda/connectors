/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.client.ai;

import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.openai.OpenAiChatModel;
import io.camunda.connector.idp.extraction.client.ai.base.AiClient;
import io.camunda.connector.idp.extraction.model.ConverseData;
import java.util.LinkedHashMap;
import java.util.Map;

public class OpenAiClient extends AiClient {

  public OpenAiClient(String endpoint, Map<String, String> headers, ConverseData converseData) {
    var sanitizedHeaders = sanitizeHeaderValues(headers);
    var builder =
        OpenAiChatModel.builder()
            .baseUrl(endpoint)
            .modelName(converseData.modelId())
            .customHeaders(sanitizedHeaders)
            .responseFormat(ResponseFormat.JSON);

    // Commenting out the max tokens assignment because it negatively impacts responses
    //    if (converseData.maxTokens() != null) {
    //      builder.maxTokens(converseData.maxTokens());
    //    }

    if (converseData.temperature() != null) {
      builder.temperature(converseData.temperature().doubleValue());
    }

    if (converseData.topP() != null) {
      builder.topP(converseData.topP().doubleValue());
    }

    this.chatModel = builder.build();
  }

  /**
   * Sanitizes header values by replacing non-ASCII whitespace characters (e.g., Line Separator
   * U+2028, Paragraph Separator U+2029) with regular spaces, and stripping leading/trailing
   * whitespace. This prevents JDK HttpClient from rejecting headers containing invisible Unicode
   * characters introduced by copy-paste artifacts or secret store formatting.
   */
  private static Map<String, String> sanitizeHeaderValues(Map<String, String> headers) {
    Map<String, String> sanitized = new LinkedHashMap<>(headers.size());
    for (var entry : headers.entrySet()) {
      String value = entry.getValue();
      sanitized.put(entry.getKey(), value != null ? sanitizeHeaderValue(value) : null);
    }
    return sanitized;
  }

  private static String sanitizeHeaderValue(String value) {
    // Replace non-ASCII characters (e.g. U+2028 Line Separator from copy-paste) with spaces
    return value.replaceAll("[^\\x09\\x20-\\x7E\\x80-\\xFF]", " ").strip();
  }
}
