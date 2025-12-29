/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import io.camunda.connector.idp.extraction.client.extraction.base.MlExtractor;
import io.camunda.connector.idp.extraction.client.extraction.base.TextExtractor;
import io.camunda.connector.idp.extraction.model.ConverseData;
import io.camunda.connector.idp.extraction.model.providers.gcp.GcpAuthenticationType;
import io.camunda.connector.idp.extraction.request.common.ai.AzureAiRequest;
import io.camunda.connector.idp.extraction.request.common.ai.BedrockAiRequest;
import io.camunda.connector.idp.extraction.request.common.ai.OpenAiRequest;
import io.camunda.connector.idp.extraction.request.common.ai.VertexAiRequest;
import io.camunda.connector.idp.extraction.request.common.extraction.DocumentAiExtractorRequest;
import io.camunda.connector.idp.extraction.request.common.extraction.DocumentIntelligenceExtractorRequest;
import io.camunda.connector.idp.extraction.request.common.extraction.ExtractionProvider;
import io.camunda.connector.idp.extraction.request.common.extraction.TextractExtractorRequest;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProviderUtilTest {

  @Nested
  class GetMlExtractorFromExtractionProviderTests {

    @Test
    void getMlExtractor_shouldReturnGcpDocumentAiClient_whenDocumentAiExtractorRequest() {
      // given
      DocumentAiExtractorRequest request = mock(DocumentAiExtractorRequest.class);
      when(request.authType()).thenReturn(GcpAuthenticationType.BEARER);
      when(request.bearerToken()).thenReturn("test-token");
      when(request.projectId()).thenReturn("test-project");
      when(request.region()).thenReturn("us-central1");
      when(request.processorId()).thenReturn("test-processor");

      // when
      MlExtractor extractor = ProviderUtil.getMlExtractor(request);

      // then
      assertThat(extractor).isInstanceOf(GcpDocumentAiExtractionClient.class);
    }

    @Test
    void getMlExtractor_shouldReturnAwsTextractClient_whenTextractExtractorRequest() {
      // given
      TextractExtractorRequest request = mock(TextractExtractorRequest.class);
      when(request.awsAuthType()).thenReturn("credentials");
      when(request.accessKey()).thenReturn("accessKey");
      when(request.secretKey()).thenReturn("secretKey");
      when(request.region()).thenReturn("us-east-1");
      when(request.bucketName()).thenReturn("test-bucket");

      // when
      MlExtractor extractor = ProviderUtil.getMlExtractor(request);

      // then
      assertThat(extractor).isInstanceOf(AwsTextrtactExtractionClient.class);
    }

    @Test
    void getMlExtractor_shouldThrowException_whenUnsupportedExtractor() {
      // given
      DocumentIntelligenceExtractorRequest unsupportedRequest =
          mock(DocumentIntelligenceExtractorRequest.class);

      // when & then
      assertThatThrownBy(() -> ProviderUtil.getMlExtractor(unsupportedRequest))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Extractor not supported");
    }
  }

  @Nested
  class GetTextExtractorFromExtractionProviderTests {

    @Test
    void
        getTextExtractor_shouldReturnAzureDocumentIntelligenceClient_whenDocumentIntelligenceRequest() {
      // given
      DocumentIntelligenceExtractorRequest request =
          mock(DocumentIntelligenceExtractorRequest.class);
      when(request.endpoint()).thenReturn("https://test.cognitiveservices.azure.com");
      when(request.apiKey()).thenReturn("test-api-key");

      // when
      TextExtractor extractor = ProviderUtil.getTextExtractor(request);

      // then
      assertThat(extractor).isInstanceOf(AzureDocumentIntelligenceExtractionClient.class);
    }

    @Test
    void getTextExtractor_shouldReturnGcpDocumentAiClient_whenDocumentAiExtractorRequest() {
      // given
      DocumentAiExtractorRequest request = mock(DocumentAiExtractorRequest.class);
      when(request.authType()).thenReturn(GcpAuthenticationType.BEARER);
      when(request.bearerToken()).thenReturn("test-token");
      when(request.projectId()).thenReturn("test-project");
      when(request.region()).thenReturn("us-central1");
      when(request.processorId()).thenReturn("test-processor");

      // when
      TextExtractor extractor = ProviderUtil.getTextExtractor(request);

      // then
      assertThat(extractor).isInstanceOf(GcpDocumentAiExtractionClient.class);
    }

    @Test
    void getTextExtractor_shouldReturnAwsTextractClient_whenTextractExtractorRequest() {
      // given
      TextractExtractorRequest request = mock(TextractExtractorRequest.class);
      when(request.awsAuthType()).thenReturn("credentials");
      when(request.accessKey()).thenReturn("accessKey");
      when(request.secretKey()).thenReturn("secretKey");
      when(request.region()).thenReturn("us-east-1");
      when(request.bucketName()).thenReturn("test-bucket");

      // when
      TextExtractor extractor = ProviderUtil.getTextExtractor(request);

      // then
      assertThat(extractor).isInstanceOf(AwsTextrtactExtractionClient.class);
    }

    @Test
    void getTextExtractor_shouldReturnPdfBoxClient_whenApachePdfBoxRequest() {
      // given
      ExtractionProvider.ApachePdfBoxExtractorRequest request =
          mock(ExtractionProvider.ApachePdfBoxExtractorRequest.class);

      // when
      TextExtractor extractor = ProviderUtil.getTextExtractor(request);

      // then
      assertThat(extractor).isInstanceOf(PdfBoxExtractionClient.class);
    }

    @Test
    void getTextExtractor_shouldReturnNull_whenMultimodalExtractorRequest() {
      // given
      ExtractionProvider.MultimodalExtractorRequest request =
          mock(ExtractionProvider.MultimodalExtractorRequest.class);

      // when
      TextExtractor extractor = ProviderUtil.getTextExtractor(request);

      // then
      assertThat(extractor).isNull();
    }
  }

  @Nested
  class GetAiClientFromAiProviderTests {

    @Test
    void getAiClient_shouldReturnAzureOpenAiClient_whenAzureRequestWithOpenAI() {
      // given
      AzureAiRequest azureRequest = mock(AzureAiRequest.class);
      when(azureRequest.usingOpenAI()).thenReturn(true);
      when(azureRequest.endpoint()).thenReturn("https://test.openai.azure.com");
      when(azureRequest.apiKey()).thenReturn("test-api-key");

      ConverseData converseData = mock(ConverseData.class);
      when(converseData.modelId()).thenReturn("gpt-4");

      // when
      AiClient client = ProviderUtil.getAiClient(azureRequest, converseData);

      // then
      assertThat(client).isInstanceOf(AzureOpenAiClient.class);
    }

    @Test
    void getAiClient_shouldReturnAzureAiFoundryClient_whenAzureRequestWithoutOpenAI() {
      // given
      AzureAiRequest azureRequest = mock(AzureAiRequest.class);
      when(azureRequest.usingOpenAI()).thenReturn(false);
      when(azureRequest.endpoint()).thenReturn("https://test.inference.azure.com");
      when(azureRequest.apiKey()).thenReturn("test-api-key");

      ConverseData converseData = mock(ConverseData.class);
      when(converseData.modelId()).thenReturn("gpt-4o");

      // when
      AiClient client = ProviderUtil.getAiClient(azureRequest, converseData);

      // then
      assertThat(client).isInstanceOf(AzureAiFoundryClient.class);
    }

    @Test
    void getAiClient_shouldReturnVertexAiClient_whenVertexAiRequest() {
      // given
      VertexAiRequest vertexRequest = mock(VertexAiRequest.class);
      when(vertexRequest.authType()).thenReturn(GcpAuthenticationType.BEARER);
      when(vertexRequest.bearerToken()).thenReturn("test-token");
      when(vertexRequest.projectId()).thenReturn("test-project");
      when(vertexRequest.region()).thenReturn("us-central1");

      ConverseData converseData = mock(ConverseData.class);
      when(converseData.modelId()).thenReturn("gemini-1.5-pro");

      // when
      AiClient client = ProviderUtil.getAiClient(vertexRequest, converseData);

      // then
      assertThat(client).isInstanceOf(VertexAiClient.class);
    }

    @Test
    void getAiClient_shouldReturnBedrockClient_whenBedrockAiRequest() {
      // given
      BedrockAiRequest bedrockRequest = mock(BedrockAiRequest.class);
      when(bedrockRequest.awsAuthType()).thenReturn("credentials");
      when(bedrockRequest.accessKey()).thenReturn("accessKey");
      when(bedrockRequest.secretKey()).thenReturn("secretKey");
      when(bedrockRequest.region()).thenReturn("us-east-1");

      ConverseData converseData = mock(ConverseData.class);
      when(converseData.modelId()).thenReturn("anthropic.claude-v2");

      // when
      AiClient client = ProviderUtil.getAiClient(bedrockRequest, converseData);

      // then
      assertThat(client).isInstanceOf(BedrockAiClient.class);
    }

    @Test
    void getAiClient_shouldReturnOpenAiClient_whenOpenAiRequest() {
      // given
      OpenAiRequest openAiRequest = mock(OpenAiRequest.class);
      when(openAiRequest.openAiEndpoint()).thenReturn("https://api.openai.com");
      when(openAiRequest.openAiHeaders()).thenReturn(Map.of("Authorization", "Bearer test-key"));

      ConverseData converseData = mock(ConverseData.class);
      when(converseData.modelId()).thenReturn("gpt-4");

      // when
      AiClient client = ProviderUtil.getAiClient(openAiRequest, converseData);

      // then
      assertThat(client).isInstanceOf(OpenAiClient.class);
    }
  }
}
