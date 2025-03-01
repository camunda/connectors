/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.caller;

import static com.google.cloud.vertexai.api.HarmCategory.*;

import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.*;
import com.google.cloud.vertexai.generativeai.ContentMaker;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.PartMaker;
import com.google.cloud.vertexai.generativeai.ResponseHandler;
import io.camunda.connector.idp.extraction.model.ExtractionRequest;
import io.camunda.connector.idp.extraction.model.ExtractionRequestData;
import io.camunda.connector.idp.extraction.model.LlmModel;
import io.camunda.connector.idp.extraction.supplier.VertexAISupplier;
import io.camunda.connector.idp.extraction.utils.GcsUtil;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GeminiCaller {

  private static final Logger LOGGER = LoggerFactory.getLogger(GeminiCaller.class);

  public String generateContent(ExtractionRequest extractionRequest) throws Exception {
    LlmModel llmModel = LlmModel.fromId(extractionRequest.input().converseData().modelId());
    String fileUri;
    try {
      fileUri =
          GcsUtil.uploadNewFileFromDocument(
              extractionRequest.input().document(),
              extractionRequest
                  .providerConfiguration()
                  .geminiRequest()
                  .getConfiguration()
                  .bucketName(),
              extractionRequest
                  .providerConfiguration()
                  .geminiRequest()
                  .getConfiguration()
                  .projectId(),
              extractionRequest.providerConfiguration().geminiRequest().getAuthentication());
      LOGGER.debug("File uploaded to GCS with URI: {}", fileUri);
    } catch (IOException e) {
      LOGGER.error("Error while uploading file to GCS", e);
      throw new RuntimeException(e);
    }

    try (VertexAI vertexAi =
        VertexAISupplier.getVertexAI(extractionRequest.providerConfiguration().geminiRequest())) {
      GenerativeModel model =
          getGenerativeModel(extractionRequest, vertexAi)
              .withSystemInstruction(ContentMaker.fromString(llmModel.getSystemPrompt()));
      var content =
          ContentMaker.fromMultiModalData(
              llmModel.getMessage(extractionRequest.input().taxonomyItems()),
              PartMaker.fromMimeTypeAndData(
                  extractionRequest.input().document().metadata().getContentType(), fileUri));
      GenerateContentResponse response = model.generateContent(content);
      String output = ResponseHandler.getText(response);
      LOGGER.debug("Gemini generate content response: {}", output);
      return output;
    } finally {
      CompletableFuture.runAsync(
          () -> {
            try {
              GcsUtil.deleteObjectFromBucket(
                  extractionRequest
                      .providerConfiguration()
                      .geminiRequest()
                      .getConfiguration()
                      .bucketName(),
                  extractionRequest.input().document().metadata().getFileName(),
                  extractionRequest
                      .providerConfiguration()
                      .geminiRequest()
                      .getConfiguration()
                      .projectId(),
                  extractionRequest.providerConfiguration().geminiRequest().getAuthentication());
              LOGGER.debug("File deleted from GCS");
            } catch (Exception e) {
              LOGGER.error("Error while deleting file from GCS", e);
            }
          });
    }
  }

  private GenerativeModel getGenerativeModel(ExtractionRequest requestData, VertexAI vertexAi) {
    GenerativeModel.Builder modelBuilder =
        new GenerativeModel.Builder()
            .setModelName(requestData.input().converseData().modelId())
            .setVertexAi(vertexAi)
            .setGenerationConfig(buildGenerationConfig(requestData.input()))
            .setSafetySettings(prepareSafetySettings());

    return modelBuilder.build();
  }

  private GenerationConfig buildGenerationConfig(ExtractionRequestData input) {
    GenerationConfig.Builder builder =
        GenerationConfig.newBuilder()
            .setMaxOutputTokens(input.converseData().maxTokens())
            .setTemperature(input.converseData().temperature())
            .setTopP(input.converseData().topP());
    return builder.build();
  }

  private List<SafetySetting> prepareSafetySettings() {
    return List.of(
        createSafetySetting(
            HARM_CATEGORY_HATE_SPEECH, SafetySetting.HarmBlockThreshold.BLOCK_ONLY_HIGH),
        createSafetySetting(HARM_CATEGORY_DANGEROUS_CONTENT, SafetySetting.HarmBlockThreshold.OFF),
        createSafetySetting(
            HARM_CATEGORY_SEXUALLY_EXPLICIT, SafetySetting.HarmBlockThreshold.BLOCK_ONLY_HIGH),
        createSafetySetting(
            HARM_CATEGORY_HARASSMENT, SafetySetting.HarmBlockThreshold.BLOCK_ONLY_HIGH));
  }

  private SafetySetting createSafetySetting(
      HarmCategory category, SafetySetting.HarmBlockThreshold threshold) {
    return SafetySetting.newBuilder().setCategory(category).setThreshold(threshold).build();
  }
}
