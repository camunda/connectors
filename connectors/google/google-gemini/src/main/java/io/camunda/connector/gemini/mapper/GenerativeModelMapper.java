/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gemini.mapper;

import static com.google.cloud.vertexai.api.HarmCategory.*;

import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.*;
import com.google.cloud.vertexai.generativeai.ContentMaker;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import io.camunda.connector.gemini.model.BlockingDegree;
import io.camunda.connector.gemini.model.GeminiRequestData;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;

public class GenerativeModelMapper {

  private final FunctionDeclarationMapper functionDeclarationMapper;

  public GenerativeModelMapper(FunctionDeclarationMapper functionDeclarationMapper) {
    this.functionDeclarationMapper = functionDeclarationMapper;
  }

  public GenerativeModel map(GeminiRequestData requestData, VertexAI vertexAi) {
    GenerativeModel.Builder modelBuilder =
        new GenerativeModel.Builder()
            .setModelName(requestData.resolveModelName())
            .setVertexAi(vertexAi)
            .setGenerationConfig(buildGenerationConfig(requestData))
            .setSafetySettings(prepareSafetySettings(requestData))
            .setTools(collectTools(requestData));

    Optional.ofNullable(requestData.systemInstrText())
        .filter(StringUtils::isNotBlank)
        .map(ContentMaker::fromString)
        .ifPresent(modelBuilder::setSystemInstruction);

    return modelBuilder.build();
  }

  private List<Tool> collectTools(GeminiRequestData requestData) {
    return Stream.concat(
            Optional.ofNullable(requestData.functionCalls())
                .filter(functionCalls -> !functionCalls.isEmpty())
                .map(this::prepareFunctionDeclarationsTool)
                .stream(),
            Optional.ofNullable(requestData.dataStorePath())
                .filter(StringUtils::isNotBlank)
                .map(this::prepareGrounding)
                .stream())
        .toList();
  }

  private Tool prepareFunctionDeclarationsTool(List<Object> functionCalls) {
    return Tool.newBuilder()
        .addAllFunctionDeclarations(functionDeclarationMapper.map(functionCalls))
        .build();
  }

  private Tool prepareGrounding(String dataStorePath) {
    return Tool.newBuilder()
        .setRetrieval(
            Retrieval.newBuilder()
                .setVertexAiSearch(VertexAISearch.newBuilder().setDatastore(dataStorePath)))
        .build();
  }

  private GenerationConfig buildGenerationConfig(GeminiRequestData requestData) {
    GenerationConfig.Builder builder =
        GenerationConfig.newBuilder()
            .setMaxOutputTokens(requestData.maxOutputTokens())
            .setTemperature(requestData.temperature())
            .setTopP(requestData.topP())
            .setSeed(requestData.seed());

    Optional.of(requestData.topK()).filter(topK -> topK != 0).ifPresent(builder::setTopK);

    Optional.ofNullable(requestData.stopSequences()).ifPresent(builder::addAllStopSequences);

    return builder.build();
  }

  private List<SafetySetting> prepareSafetySettings(GeminiRequestData requestData) {
    return List.of(
        createSafetySetting(HARM_CATEGORY_HATE_SPEECH, requestData.hateSpeech()),
        createSafetySetting(HARM_CATEGORY_DANGEROUS_CONTENT, requestData.dangerousContent()),
        createSafetySetting(HARM_CATEGORY_SEXUALLY_EXPLICIT, requestData.sexuallyExplicit()),
        createSafetySetting(HARM_CATEGORY_HARASSMENT, requestData.harassment()));
  }

  private SafetySetting createSafetySetting(HarmCategory category, BlockingDegree degree) {
    return SafetySetting.newBuilder()
        .setCategory(category)
        .setThreshold(mapHarmBlock(degree))
        .build();
  }

  private SafetySetting.HarmBlockThreshold mapHarmBlock(BlockingDegree blockingDegree) {
    return switch (blockingDegree) {
      case null -> SafetySetting.HarmBlockThreshold.OFF;
      case OFF -> SafetySetting.HarmBlockThreshold.OFF;
      case BLOCK_ONLY_HIGH -> SafetySetting.HarmBlockThreshold.BLOCK_ONLY_HIGH;
      case BLOCK_MEDIUM_AND_ABOVE -> SafetySetting.HarmBlockThreshold.BLOCK_MEDIUM_AND_ABOVE;
      case BLOCK_LOW_AND_ABOVE -> SafetySetting.HarmBlockThreshold.BLOCK_LOW_AND_ABOVE;
    };
  }
}
