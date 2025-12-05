/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gemini.caller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.Content;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.generativeai.ContentMaker;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.ResponseHandler;
import io.camunda.connector.gemini.mapper.FunctionDeclarationMapper;
import io.camunda.connector.gemini.mapper.GenerativeModelMapper;
import io.camunda.connector.gemini.mapper.PromptsMapper;
import io.camunda.connector.gemini.model.GeminiRequest;
import io.camunda.connector.gemini.model.GeminiRequestData;
import io.camunda.connector.gemini.supplier.VertexAISupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GeminiCaller {

  private static final Logger LOGGER = LoggerFactory.getLogger(GeminiCaller.class);

  private final GenerativeModelMapper generativeModelMapper;
  private final PromptsMapper promptsMapper;

  public GeminiCaller(ObjectMapper objectMapper) {
    FunctionDeclarationMapper functionDeclarationMapper =
        new FunctionDeclarationMapper(objectMapper);
    this.generativeModelMapper = new GenerativeModelMapper(functionDeclarationMapper);
    this.promptsMapper = new PromptsMapper(objectMapper);
  }

  public GeminiCaller(GenerativeModelMapper generativeModelMapper, PromptsMapper promptsMapper) {
    this.generativeModelMapper = generativeModelMapper;
    this.promptsMapper = promptsMapper;
  }

  public Content generateContent(GeminiRequest geminiRequest) throws Exception {
    GeminiRequestData requestData = geminiRequest.getInput();
    LOGGER.debug("Starting gemini generate content with request data: {}", requestData);

    try (VertexAI vertexAi = VertexAISupplier.getVertexAI(geminiRequest)) {
      GenerativeModel model = generativeModelMapper.map(requestData, vertexAi);

      var content = ContentMaker.fromMultiModalData(promptsMapper.map(requestData.prompts()));
      GenerateContentResponse response = model.generateContent(content);
      return ResponseHandler.getContent(response);
    }
  }
}
