/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.service;

import static io.camunda.connector.idp.extraction.utils.ProviderUtil.getAiClient;
import static io.camunda.connector.idp.extraction.utils.ProviderUtil.getTextExtractor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.idp.extraction.client.ai.base.AiClient;
import io.camunda.connector.idp.extraction.client.extraction.base.TextExtractor;
import io.camunda.connector.idp.extraction.model.ClassificationResult;
import io.camunda.connector.idp.extraction.model.LlmModel;
import io.camunda.connector.idp.extraction.request.classification.ClassificationRequest;
import io.camunda.connector.idp.extraction.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClassificationService {

  private static final Logger LOGGER = LoggerFactory.getLogger(ClassificationService.class);
  private final ObjectMapper objectMapper = new ObjectMapper();

  public ClassificationResult execute(ClassificationRequest request) throws Exception {

    TextExtractor textExtractor = getTextExtractor(request.extractor());
    AiClient aiClient = getAiClient(request.ai(), request.input().getConverseData());

    String aiResponse;
    if (textExtractor == null) {
      long aiStartTime = System.currentTimeMillis();
      String prompt =
          new StringBuilder()
              .append(request.input().getUserPrompt())
              .append("\n------ Document types: ------\n")
              .append(String.join(", ", request.input().getDocumentTypes()))
              .append("\n------ Response format: ------\n")
              .append(LlmModel.getFormatSystemPrompt())
              .append(
                  request.input().isAutoClassify()
                      ? LlmModel.getClasssificationSystemPromptWithUnknownOption()
                      : "")
              .toString();

      LOGGER.info("Starting multimodal {} conversation", aiClient.getClass().getSimpleName());
      aiResponse = aiClient.chat(prompt, request.input().getDocument());
      long aiEndTime = System.currentTimeMillis();
      LOGGER.info(
          "Multimodal {} conversation took {} ms",
          aiClient.getClass().getSimpleName(),
          (aiEndTime - aiStartTime));
    } else {
      long startTime = System.currentTimeMillis();
      LOGGER.info("Extracting text through {}", textExtractor.getClass().getSimpleName());
      String extractedText = textExtractor.extract(request.input().getDocument());
      long extractionEndTime = System.currentTimeMillis();
      LOGGER.info("Finished text extraction in {}ms", extractionEndTime - startTime);

      String prompt =
          new StringBuilder()
              .append(request.input().getUserPrompt())
              .append("\n------ Document types: ------\n")
              .append(String.join(", ", request.input().getDocumentTypes()))
              .append("\n------ Response format: ------\n")
              .append(LlmModel.getFormatSystemPrompt())
              .append(
                  request.input().isAutoClassify()
                      ? LlmModel.getClasssificationSystemPromptWithUnknownOption()
                      : "")
              .append("\n------ Extracted document text: ------\n")
              .append(extractedText)
              .toString();

      long aiStartTime = System.currentTimeMillis();
      LOGGER.info(
          "Classifying with ai provider {} and model {}",
          aiClient.getClass().getSimpleName(),
          request.input().getConverseData().modelId());
      aiResponse = aiClient.chat(prompt);
      long endTime = System.currentTimeMillis();
      LOGGER.info(
          "Finished ai conversation in {}ms and in total took {}ms",
          (endTime - aiStartTime),
          (endTime - startTime));
    }
    return parseClassificationResponse(aiResponse);
  }

  private ClassificationResult parseClassificationResponse(String llmResponse) {
    try {
      // First filter out thinking content, then strip markdown code blocks
      String thinkingRemoved = StringUtil.filterThinkingContent(llmResponse);
      String cleanedResponse = StringUtil.stripMarkdownCodeBlocks(thinkingRemoved);
      JsonNode llmResponseJson = objectMapper.readValue(cleanedResponse, JsonNode.class);

      if (!llmResponseJson.isObject()) {
        throw new ConnectorException(
            String.valueOf(500),
            String.format("LLM response is not a JSON object: %s", llmResponse));
      }

      // Handle nested "response" wrapper if present
      JsonNode dataNode = llmResponseJson;
      if (llmResponseJson.has("response") && llmResponseJson.size() == 1) {
        var nestedResponse = llmResponseJson.get("response");
        if (nestedResponse.isObject()) {
          dataNode = nestedResponse;
        } else if (nestedResponse.isTextual()) {
          dataNode = objectMapper.readValue(nestedResponse.asText(), JsonNode.class);
        }
      }

      // Extract the required fields
      String extractedValue =
          dataNode.has("extractedValue") ? dataNode.get("extractedValue").asText() : null;
      String confidence = dataNode.has("confidence") ? dataNode.get("confidence").asText() : null;
      String reasoning = dataNode.has("reasoning") ? dataNode.get("reasoning").asText() : null;

      return new ClassificationResult(extractedValue, confidence, reasoning);

    } catch (JsonProcessingException e) {
      throw new ConnectorException(
          String.valueOf(500),
          String.format("Failed to parse the JSON response from LLM: %s", llmResponse),
          e);
    }
  }
}
