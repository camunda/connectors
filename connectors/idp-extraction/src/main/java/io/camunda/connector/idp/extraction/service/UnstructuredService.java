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
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.idp.extraction.client.ai.AzureAiFoundryClient;
import io.camunda.connector.idp.extraction.client.ai.AzureOpenAiClient;
import io.camunda.connector.idp.extraction.client.ai.BedrockAiClient;
import io.camunda.connector.idp.extraction.client.ai.OpenAiClient;
import io.camunda.connector.idp.extraction.client.ai.VertexAiClient;
import io.camunda.connector.idp.extraction.client.ai.base.AiClient;
import io.camunda.connector.idp.extraction.client.extraction.AwsTextrtactExtractionClient;
import io.camunda.connector.idp.extraction.client.extraction.AzureDocumentIntelligenceExtractionClient;
import io.camunda.connector.idp.extraction.client.extraction.GcpDocumentAiExtractionClient;
import io.camunda.connector.idp.extraction.client.extraction.base.TextExtractor;
import io.camunda.connector.idp.extraction.model.ExtractionRequest;
import io.camunda.connector.idp.extraction.model.ExtractionRequestData;
import io.camunda.connector.idp.extraction.model.ExtractionResult;
import io.camunda.connector.idp.extraction.model.LlmModel;
import io.camunda.connector.idp.extraction.model.TaxonomyItem;
import io.camunda.connector.idp.extraction.model.providers.AwsProvider;
import io.camunda.connector.idp.extraction.model.providers.AzureProvider;
import io.camunda.connector.idp.extraction.model.providers.GcpProvider;
import io.camunda.connector.idp.extraction.model.providers.OpenAiProvider;
import io.camunda.connector.idp.extraction.model.providers.ProviderConfig;
import io.camunda.connector.idp.extraction.model.providers.gcp.DocumentAiRequestConfiguration;
import io.camunda.connector.idp.extraction.model.providers.gcp.VertexRequestConfiguration;
import io.camunda.connector.idp.extraction.utils.StringUtil;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UnstructuredService implements ExtractionService {

  private static final Logger LOGGER = LoggerFactory.getLogger(UnstructuredService.class);
  private final ObjectMapper objectMapper = new ObjectMapper();

  public UnstructuredService() {}

  @Override
  public Object extract(ExtractionRequest extractionRequest) {
    TextExtractor textExtractor = getTextExtractor(extractionRequest.baseRequest());
    AiClient aiClient = getAiClient(extractionRequest);
    LlmModel llmModel = LlmModel.fromId(extractionRequest.input().converseData().modelId());

    String aiResponse;
    long extractionStartTime = System.currentTimeMillis();
    LOGGER.info("Starting {} text extraction", textExtractor.getClass().getSimpleName());
    String extractedText = textExtractor.extract(extractionRequest.input().document());
    long extractionEndTime = System.currentTimeMillis();
    LOGGER.info(
        "{} text extraction took {} ms",
        textExtractor.getClass().getSimpleName(),
        (extractionEndTime - extractionStartTime));

    long aiStartTime = System.currentTimeMillis();
    LOGGER.info("Starting {} conversation", aiClient.getClass().getSimpleName());
    String userMessageText =
        llmModel.getMessage(extractedText, extractionRequest.input().taxonomyItems());
    aiResponse = aiClient.chat(userMessageText);
    long aiEndTime = System.currentTimeMillis();
    LOGGER.info(
        "{} conversation took {} ms",
        aiClient.getClass().getSimpleName(),
        (aiEndTime - aiStartTime));

    return new ExtractionResult(
        buildResponseJsonIfPossible(aiResponse, extractionRequest.input().taxonomyItems()));
  }

  //  private TextExtractor getTextExtractor(ExtractorConfig extractorConfig) {
  //    return switch (extractorConfig) {
  //      case AwsProvider aws ->
  //          new AwsTextrtactExtractionClient(
  //              aws.getAuthentication(), aws.getConfiguration().region(), aws.getS3BucketName());
  //      case AzureProvider azure ->
  //          new AzureDocumentIntelligenceExtractionClient(
  //              azure.getDocumentIntelligenceConfiguration().getEndpoint(),
  //              azure.getDocumentIntelligenceConfiguration().getApiKey());
  //      case GcpProvider gcp -> {
  //        DocumentAiRequestConfiguration config =
  //            (DocumentAiRequestConfiguration) gcp.getConfiguration();
  //        yield new GcpDocumentAiExtractionClient(
  //            gcp.getAuthentication(),
  //            config.getProjectId(),
  //            config.getRegion(),
  //            config.getProcessorId());
  //      }
  //      case ApachePdfBoxProvider pdfBox -> new PdfBoxExtractionClient();
  //      case MultimodalExtractorProvider multimodal -> null;
  //    };
  //  }

  private TextExtractor getTextExtractor(ProviderConfig providerConfig) {
    return switch (providerConfig) {
      case AwsProvider aws ->
          new AwsTextrtactExtractionClient(
              aws.getAuthentication(), aws.getConfiguration().region(), aws.getS3BucketName());
      case AzureProvider azure ->
          new AzureDocumentIntelligenceExtractionClient(
              azure.getDocumentIntelligenceConfiguration().getEndpoint(),
              azure.getDocumentIntelligenceConfiguration().getApiKey());
      case GcpProvider gcp -> {
        DocumentAiRequestConfiguration config =
            (DocumentAiRequestConfiguration) gcp.getConfiguration();
        yield new GcpDocumentAiExtractionClient(
            gcp.getAuthentication(),
            config.getProjectId(),
            config.getRegion(),
            config.getProcessorId());
      }
      default -> throw new IllegalStateException("Unexpected value: " + providerConfig);
    };
  }

  private AiClient getAiClient(ExtractionRequest extractionRequest) {
    final var input = (ExtractionRequestData) extractionRequest.input();
    return switch (extractionRequest.baseRequest()) {
      case AwsProvider aws ->
          new BedrockAiClient(aws, aws.getConfiguration().region(), input.converseData());
      case OpenAiProvider openAi ->
          new OpenAiClient(
              openAi.getOpenAiEndpoint(), openAi.getOpenAiHeaders(), input.converseData());
      case GcpProvider gemini -> {
        var config = (VertexRequestConfiguration) gemini.getConfiguration();
        yield new VertexAiClient(
            gemini.getAuthentication().serviceAccountJson(),
            config.getProjectId(),
            config.getRegion(),
            input.converseData());
      }
      case AzureProvider azure -> {
        if (azure.getAiFoundryConfig().isUsingOpenAI()) {
          yield new AzureOpenAiClient(
              azure.getAiFoundryConfig().getEndpoint(),
              azure.getAiFoundryConfig().getApiKey(),
              input.converseData());
        } else {
          yield new AzureAiFoundryClient(
              azure.getAiFoundryConfig().getEndpoint(),
              azure.getAiFoundryConfig().getApiKey(),
              input.converseData());
        }
      }
    };
  }

  private Map<String, Object> buildResponseJsonIfPossible(
      String llmResponse, List<TaxonomyItem> taxonomyItems) {
    try {
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
                String.format(
                    "LLM response is neither a JSON object nor a string: %s", llmResponse));
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
            String.valueOf(500),
            String.format("LLM response is not a JSON object: %s", llmResponse));
      }
    } catch (JsonProcessingException e) {
      throw new ConnectorException(
          String.valueOf(500),
          String.format("Failed to parse the JSON response from LLM: %s", llmResponse),
          e);
    }
  }
}
