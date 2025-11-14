/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction;

import com.google.auth.oauth2.GoogleCredentials;
import io.camunda.connector.api.annotation.OutboundConnector;
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
import jakarta.validation.Valid;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

@OutboundConnector(
    name = "IDP classification outbound Connector",
    inputVariables = {"extractionProvider", "input"},
    type = "io.camunda:idp-classification-connector-template:1")
@ElementTemplate(
    engineVersion = "^8.7",
    id = "io.camunda.connector.IdpClassificationOutBoundTemplate.v1",
    name = "IDP classification outbound Connector",
    version = 2,
    description = "Execute IDP classification requests",
    icon = "icon.svg",
    documentationRef = "https://docs.camunda.io/docs/guides/",
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "input", label = "Input message data"),
      @ElementTemplate.PropertyGroup(id = "extractor", label = "Extractor selection"),
      @ElementTemplate.PropertyGroup(id = "ai", label = "Ai provider selection")
    },
    inputDataClass = ClassificationRequest.class)
public class ClassificationConnectorFunction implements OutboundConnectorFunction {

  @Override
  public Object execute(OutboundConnectorContext context) throws Exception {
    ClassificationRequest request = context.bindVariables(ClassificationRequest.class);

    TextExtractor textExtractor = getTextExtractor(request.extractor());
    AiClient aiClient = getAiClient(request.ai(), request.input().getConverseData());

    if (textExtractor == null) {
      // multimodal chat
      return null;
    } else {
      String extractedText = textExtractor.extract(request.input().getDocument());
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
      return aiClient.chat(prompt);
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
      case TextractExtractorRequest textractRequest ->
          new AwsTextrtactExtractionClient(
              null, textractRequest.region(), textractRequest.bucketName());
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
}
