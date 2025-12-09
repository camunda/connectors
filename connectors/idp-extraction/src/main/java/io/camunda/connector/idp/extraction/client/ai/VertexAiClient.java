/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.client.ai;

import com.google.auth.oauth2.GoogleCredentials;
import dev.langchain4j.model.vertexai.gemini.VertexAiGeminiChatModel;
import io.camunda.connector.idp.extraction.client.ai.base.AiClient;
import io.camunda.connector.idp.extraction.model.ConverseData;

public class VertexAiClient extends AiClient {

  public VertexAiClient(
      GoogleCredentials credentials, String projectId, String location, ConverseData converseData) {

    var builder =
        VertexAiGeminiChatModel.builder()
            .credentials(credentials)
            .project(projectId)
            .location(location)
            .modelName(converseData.modelId())
            .responseMimeType("application/json");

    // Commenting out the max tokens assignment because it negatively impacts responses
    //    if (converseData.maxTokens() != null) {
    //      builder.maxOutputTokens(converseData.maxTokens());
    //    }
    if (converseData.temperature() != null) {
      builder.temperature(converseData.temperature());
    }
    if (converseData.topP() != null) {
      builder.topP(converseData.topP());
    }

    this.chatModel = builder.build();
  }
}
