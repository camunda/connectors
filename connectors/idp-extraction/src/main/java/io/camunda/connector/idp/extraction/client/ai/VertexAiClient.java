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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class VertexAiClient extends AiClient {

  public VertexAiClient(
      String serviceAccount, String projectId, String location, ConverseData converseData) {
    this.chatModel =
        VertexAiGeminiChatModel.builder()
            .credentials(getCredentials(serviceAccount))
            .project(projectId)
            .location(location)
            .modelName(converseData.modelId())
            .temperature(converseData.temperature())
            .topP(converseData.topP())
            .responseMimeType("application/json")
            .build();
  }

  public static GoogleCredentials getCredentials(String serviceAccount) {
    ByteArrayInputStream credentialsStream =
        new ByteArrayInputStream(serviceAccount.getBytes(StandardCharsets.UTF_8));
    try {
      return GoogleCredentials.fromStream(credentialsStream);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
