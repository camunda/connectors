/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.idp.extraction.client.ai.AzureAiFoundryClient;
import io.camunda.connector.idp.extraction.client.ai.AzureOpenAiClient;
import io.camunda.connector.idp.extraction.client.ai.BedrockAiClient;
import io.camunda.connector.idp.extraction.client.ai.OpenAiClient;
import io.camunda.connector.idp.extraction.client.ai.VertexAiClient;
import io.camunda.connector.idp.extraction.client.ai.base.AiClient;
import io.camunda.connector.idp.extraction.client.extraction.AwsTextrtactExtractionClient;
import io.camunda.connector.idp.extraction.client.extraction.AzureDocumentIntelligenceExtractionClient;
import io.camunda.connector.idp.extraction.client.extraction.GcpDocumentAiExtractionClient;
import io.camunda.connector.idp.extraction.client.extraction.PdfBoxExtractionClient;
import io.camunda.connector.idp.extraction.client.extraction.base.TextExtractor;
import io.camunda.connector.idp.extraction.model.ClassificationResult;
import io.camunda.connector.idp.extraction.model.ConverseData;
import io.camunda.connector.idp.extraction.model.LlmModel;
import io.camunda.connector.idp.extraction.request.classification.ClassificationRequest;
import io.camunda.connector.idp.extraction.request.common.ai.AiProvider;
import io.camunda.connector.idp.extraction.request.common.ai.AzureAiRequest;
import io.camunda.connector.idp.extraction.request.common.ai.BedrockAiRequest;
import io.camunda.connector.idp.extraction.request.common.ai.OpenAiRequest;
import io.camunda.connector.idp.extraction.request.common.ai.VertexAiRequest;
import io.camunda.connector.idp.extraction.request.common.extraction.DocumentAiExtractorRequest;
import io.camunda.connector.idp.extraction.request.common.extraction.DocumentIntelligenceExtractorRequest;
import io.camunda.connector.idp.extraction.request.common.extraction.ExtractionProvider;
import io.camunda.connector.idp.extraction.request.common.extraction.TextractExtractorRequest;
import io.camunda.connector.idp.extraction.utils.AwsUtil;
import io.camunda.connector.idp.extraction.utils.GcsUtil;
import io.camunda.connector.idp.extraction.utils.StringUtil;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

@OutboundConnector(
    name = "IDP classification outbound Connector",
    inputVariables = {"extractor", "ai", "input"},
    type = "io.camunda:idp-classification-connector-template:1")
@ElementTemplate(
    engineVersion = "^8.7",
    id = "io.camunda.connector.IdpClassificationOutBoundTemplate.v1",
    name = "IDP classification outbound Connector",
    version = 2,
    description = "Execute IDP classification requests",
    icon = "classification-icon.svg",
    documentationRef = "https://docs.camunda.io/docs/guides/",
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "input", label = "Input message data"),
      @ElementTemplate.PropertyGroup(id = "extractor", label = "Extractor selection"),
      @ElementTemplate.PropertyGroup(id = "ai", label = "Ai provider selection")
    },
    inputDataClass = ClassificationRequest.class)
public class ClassificationConnectorFunction implements OutboundConnectorFunction {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ClassificationConnectorFunction.class);
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public Object execute(OutboundConnectorContext context) throws Exception {
    ClassificationRequest request = context.bindVariables(ClassificationRequest.class);

    TextExtractor textExtractor = getTextExtractor(request.extractor());
    AiClient aiClient = getAiClient(request.ai(), request.input().getConverseData());

    if (textExtractor == null) {
      // multimodal chat
      return null;
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
      String response = aiClient.chat(prompt);
      long endTime = System.currentTimeMillis();
      LOGGER.info(
          "Finished ai conversation in {}ms and in total took {}ms",
          (endTime - aiStartTime),
          (endTime - startTime));

      return parseClassificationResponse(response);
    }
  }

  private TextExtractor getTextExtractor(@Valid ExtractionProvider extractor) {
    return switch (extractor) {
      case DocumentIntelligenceExtractorRequest docIntelligenceRequest ->
          new AzureDocumentIntelligenceExtractionClient(
              docIntelligenceRequest.endpoint(), docIntelligenceRequest.apiKey());
      case DocumentAiExtractorRequest docAiRequest -> {
        GoogleCredentials credentials =
            GcsUtil.getCredentials(
                docAiRequest.authType(),
                docAiRequest.bearerToken(),
                docAiRequest.serviceAccountJson(),
                docAiRequest.oauthClientId(),
                docAiRequest.oauthClientSecret(),
                docAiRequest.oauthRefreshToken());
        yield new GcpDocumentAiExtractionClient(
            credentials,
            docAiRequest.projectId(),
            docAiRequest.region(),
            docAiRequest.processorId());
      }
      case TextractExtractorRequest textractRequest -> {
        AwsCredentialsProvider credentialsProvider =
            AwsUtil.credentialsProvider(
                textractRequest.awsAuthType(),
                textractRequest.accessKey(),
                textractRequest.secretKey());
        yield new AwsTextrtactExtractionClient(
            credentialsProvider, textractRequest.region(), textractRequest.bucketName());
      }
      case ExtractionProvider.ApachePdfBoxExtractorRequest pdfbox -> new PdfBoxExtractionClient();
      case ExtractionProvider.MultimodalExtractorRequest multimodal -> null;
    };
  }

  private AiClient getAiClient(@Valid AiProvider aiProvider, ConverseData converseData) {
    return switch (aiProvider) {
      case AzureAiRequest azureRequest -> {
        if (azureRequest.usingOpenAI()) {
          yield new AzureOpenAiClient(azureRequest.endpoint(), azureRequest.apiKey(), converseData);
        } else {
          yield new AzureAiFoundryClient(
              azureRequest.endpoint(), azureRequest.apiKey(), converseData);
        }
      }
      case VertexAiRequest vertexRequest -> {
        GoogleCredentials credentials =
            GcsUtil.getCredentials(
                vertexRequest.authType(),
                vertexRequest.bearerToken(),
                vertexRequest.serviceAccountJson(),
                vertexRequest.oauthClientId(),
                vertexRequest.oauthClientSecret(),
                vertexRequest.oauthRefreshToken());
        yield new VertexAiClient(
            credentials, vertexRequest.projectId(), vertexRequest.region(), converseData);
      }
      case BedrockAiRequest bedrockRequest -> {
        AwsCredentialsProvider credentialsProvider =
            AwsUtil.credentialsProvider(
                bedrockRequest.awsAuthType(),
                bedrockRequest.accessKey(),
                bedrockRequest.secretKey());
        yield new BedrockAiClient(credentialsProvider, bedrockRequest.region(), converseData);
      }
      case OpenAiRequest openAiRequest ->
          new OpenAiClient(
              openAiRequest.openAiEndpoint(), openAiRequest.openAiHeaders(), converseData);
    };
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
