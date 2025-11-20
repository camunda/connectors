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
import java.util.Map;

public class OpenAiClient extends AiClient {

  public OpenAiClient(String endpoint, Map<String, String> headers, ConverseData converseData) {
    var builder =
        OpenAiChatModel.builder()
            .baseUrl(endpoint)
            .modelName(converseData.modelId())
            .customHeaders(headers)
            .responseFormat(ResponseFormat.JSON);

    if (converseData.maxTokens() != null) {
      builder.maxTokens(converseData.maxTokens());
    }

    if (converseData.temperature() != null) {
      builder.temperature(converseData.temperature().doubleValue());
    }

    if (converseData.topP() != null) {
      builder.topP(converseData.topP().doubleValue());
    }

    this.chatModel = builder.build();
  }
}
