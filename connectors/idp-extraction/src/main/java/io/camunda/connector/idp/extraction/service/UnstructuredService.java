/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.idp.extraction.client.ai.base.AiClient;
import io.camunda.connector.idp.extraction.client.extraction.base.TextExtractor;
import io.camunda.connector.idp.extraction.model.ExtractionResult;
import io.camunda.connector.idp.extraction.model.LlmModel;
import io.camunda.connector.idp.extraction.model.TaxonomyItem;
import io.camunda.connector.idp.extraction.utils.StringUtil;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UnstructuredService {

  private static final Logger LOGGER = LoggerFactory.getLogger(UnstructuredService.class);
  private final ObjectMapper objectMapper = new ObjectMapper();

  public UnstructuredService() {}

  public Object extract(
      TextExtractor textExtractor,
      AiClient aiClient,
      List<TaxonomyItem> taxonomyItems,
      Document document) {
    ChatResponse aiResponse;
    if (textExtractor == null) {
      long aiStartTime = System.currentTimeMillis();
      LOGGER.info("Starting multimodal {} conversation", aiClient.getClass().getSimpleName());
      aiResponse =
          aiClient.chat(
              LlmModel.getExtractionSystemInstruction(),
              LlmModel.getExtractionUserPrompt(taxonomyItems),
              document);
      long aiEndTime = System.currentTimeMillis();
      LOGGER.info(
          "Multimodal {} conversation took {} ms",
          aiClient.getClass().getSimpleName(),
          (aiEndTime - aiStartTime));
    } else {
      long extractionStartTime = System.currentTimeMillis();
      LOGGER.info("Starting {} text extraction", textExtractor.getClass().getSimpleName());
      String extractedText = textExtractor.extract(document);
      long extractionEndTime = System.currentTimeMillis();
      LOGGER.info(
          "{} text extraction took {} ms",
          textExtractor.getClass().getSimpleName(),
          (extractionEndTime - extractionStartTime));

      long aiStartTime = System.currentTimeMillis();
      LOGGER.info("Starting {} conversation", aiClient.getClass().getSimpleName());
      aiResponse =
          aiClient.chat(
              LlmModel.getExtractionSystemInstruction(),
              LlmModel.getExtractionUserPrompt(extractedText, taxonomyItems));
      long aiEndTime = System.currentTimeMillis();
      LOGGER.info(
          "{} conversation took {} ms",
          aiClient.getClass().getSimpleName(),
          (aiEndTime - aiStartTime));
    }

    return new ExtractionResult(
        buildResponseJsonIfPossible(aiResponse.aiMessage().text(), taxonomyItems, aiClient),
        aiResponse.metadata());
  }

  private Map<String, Object> buildResponseJsonIfPossible(
      String llmResponse, List<TaxonomyItem> taxonomyItems, AiClient aiClient) {
    try {
      return parseAndValidateResponse(llmResponse, taxonomyItems);
    } catch (JsonProcessingException | ConnectorException e) {
      LOGGER.warn(
          "Initial JSON parsing failed, attempting to clean up response with LLM. Error: {}",
          e.getMessage());

      // Try to fix the JSON with another LLM call
      try {
        long cleanupStartTime = System.currentTimeMillis();
        LOGGER.info("Starting JSON cleanup {} conversation", aiClient.getClass().getSimpleName());
        ChatResponse cleanupResponse =
            aiClient.chat(
                LlmModel.getJsonExtractionSystemPrompt(),
                LlmModel.getJsonExtractionUserPrompt(llmResponse));
        long cleanupEndTime = System.currentTimeMillis();
        LOGGER.info(
            "JSON cleanup {} conversation took {} ms",
            aiClient.getClass().getSimpleName(),
            (cleanupEndTime - cleanupStartTime));

        String cleanedResponse = cleanupResponse.aiMessage().text();
        return parseAndValidateResponse(cleanedResponse, taxonomyItems);
      } catch (Exception cleanupException) {
        throw new ConnectorException(
            String.format(
                "Failed to parse JSON even after cleanup attempt. Original response: %s",
                llmResponse),
            cleanupException);
      }
    }
  }

  private Map<String, Object> parseAndValidateResponse(
      String llmResponse, List<TaxonomyItem> taxonomyItems) throws JsonProcessingException {
    // First filter out thinking content, then strip markdown code blocks
    String thinkingRemoved = StringUtil.filterThinkingContent(llmResponse);
    String cleanedResponse = StringUtil.stripMarkdownCodeBlocks(thinkingRemoved);
    var llmResponseJson = objectMapper.readValue(cleanedResponse, JsonNode.class);
    var taxonomyItemsNames = taxonomyItems.stream().map(TaxonomyItem::name).toList();

    if (llmResponseJson.isObject()) {
      if (llmResponseJson.has("response")
          && llmResponseJson.size() == 1
          && !taxonomyItemsNames.contains("response")) {
        var nestedResponse = llmResponseJson.get("response");
        if (nestedResponse.isObject()) {
          llmResponseJson = nestedResponse;
        } else if (nestedResponse.isTextual()) {
          llmResponseJson = objectMapper.readValue(nestedResponse.asText(), JsonNode.class);
        } else {
          throw new ConnectorException(
              String.valueOf(500),
              String.format("LLM response is neither a JSON object nor a string: %s", llmResponse));
        }
      }

      Map<String, Object> result =
          taxonomyItemsNames.stream()
              .filter(llmResponseJson::has)
              .collect(Collectors.toMap(name -> name, llmResponseJson::get));

      var missingKeys =
          taxonomyItemsNames.stream().filter(name -> !result.containsKey(name)).toList();
      if (!missingKeys.isEmpty()) {
        LOGGER.warn(
            "LLM model response is missing the following keys: ({})",
            String.join(", ", missingKeys));
      }

      return result;

    } else {
      throw new ConnectorException(
          String.valueOf(500), String.format("LLM response is not a JSON object: %s", llmResponse));
    }
  }
}
