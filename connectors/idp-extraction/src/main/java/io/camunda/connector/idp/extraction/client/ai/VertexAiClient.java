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
    this.chatModel =
        VertexAiGeminiChatModel.builder()
            .credentials(credentials)
            .project(projectId)
            .location(location)
            .modelName(converseData.modelId())
            .temperature(converseData.temperature())
            .topP(converseData.topP())
            .responseMimeType("application/json")
            .build();
  }
}
