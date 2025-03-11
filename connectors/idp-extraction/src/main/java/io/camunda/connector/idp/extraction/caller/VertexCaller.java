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
import io.camunda.connector.idp.extraction.model.ExtractionRequestData;
import io.camunda.connector.idp.extraction.model.LlmModel;
import io.camunda.connector.idp.extraction.model.providers.VertexProvider;
import io.camunda.connector.idp.extraction.supplier.VertexAISupplier;
import io.camunda.connector.idp.extraction.utils.GcsUtil;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VertexCaller {

  private static final Logger LOGGER = LoggerFactory.getLogger(VertexCaller.class);

  public String generateContent(ExtractionRequestData input, VertexProvider baseRequest)
      throws Exception {
    LlmModel llmModel = LlmModel.fromId(input.converseData().modelId());
    String fileUri;
    final String fileName =
        input.document().metadata().getFileName() == null
            ? "temporaryDocument"
            : input.document().metadata().getFileName();
    try {
      fileUri =
          GcsUtil.uploadNewFileFromDocument(
              input.document(),
              fileName,
              baseRequest.getConfiguration().bucketName(),
              baseRequest.getConfiguration().projectId(),
              baseRequest.getAuthentication());
      LOGGER.debug("File uploaded to GCS with URI: {}", fileUri);
    } catch (IOException e) {
      LOGGER.error("Error while uploading file to GCS", e);
      throw new RuntimeException(e);
    }

    try (VertexAI vertexAi = VertexAISupplier.getVertexAI(baseRequest)) {
      GenerativeModel model =
          getGenerativeModel(input, vertexAi)
              .withSystemInstruction(ContentMaker.fromString(llmModel.getSystemPrompt()));
      var content =
          ContentMaker.fromMultiModalData(
              llmModel.getMessage(input.taxonomyItems()),
              PartMaker.fromMimeTypeAndData(input.document().metadata().getContentType(), fileUri));
      GenerateContentResponse response = model.generateContent(content);
      String output = ResponseHandler.getText(response);
      LOGGER.debug("Gemini generate content response: {}", output);
      return output;
    } finally {
      CompletableFuture.runAsync(
          () -> {
            try {
              GcsUtil.deleteObjectFromBucket(
                  baseRequest.getConfiguration().bucketName(),
                  fileName,
                  baseRequest.getConfiguration().projectId(),
                  baseRequest.getAuthentication());
              LOGGER.debug("File deleted from GCS");
            } catch (Exception e) {
              LOGGER.error("Error while deleting file from GCS", e);
            }
          });
    }
  }

  private GenerativeModel getGenerativeModel(ExtractionRequestData input, VertexAI vertexAi) {
    GenerativeModel.Builder modelBuilder =
        new GenerativeModel.Builder()
            .setModelName(input.converseData().modelId())
            .setVertexAi(vertexAi)
            .setGenerationConfig(buildGenerationConfig(input))
            .setSafetySettings(prepareSafetySettings());

    return modelBuilder.build();
  }

  private GenerationConfig buildGenerationConfig(ExtractionRequestData input) {
    GenerationConfig.Builder builder =
        GenerationConfig.newBuilder()
            .setMaxOutputTokens(input.converseData().maxTokens())
            .setTemperature(input.converseData().temperature())
            .setResponseMimeType("application/json")
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
