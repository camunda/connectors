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
   * Sanitizes header values by replacing characters outside the RFC 7230 field-value range (e.g.,
   * Unicode Line Separator U+2028, control characters) with regular spaces, and stripping
   * leading/trailing whitespace. Headers with null or blank values are skipped. This prevents JDK
   * HttpClient from rejecting headers containing invisible characters introduced by copy-paste
   * artifacts or secret store formatting.
   */
  private static Map<String, String> sanitizeHeaderValues(Map<String, String> headers) {
    Map<String, String> sanitized = new LinkedHashMap<>(headers.size());
    for (var entry : headers.entrySet()) {
      String value = sanitizeHeaderValue(entry.getValue());
      if (value != null) {
        sanitized.put(entry.getKey(), value);
      }
    }
    return sanitized;
  }

  private static String sanitizeHeaderValue(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    // Replace characters outside the RFC 7230 field-value range with spaces
    return value.replaceAll("[^\\x09\\x20-\\x7E\\x80-\\xFF]", " ").strip();
  }
}
