/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.client.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentLinkParameters;
import io.camunda.connector.api.document.DocumentMetadata;
import io.camunda.connector.idp.extraction.client.ai.base.AiClient;
import io.camunda.connector.idp.extraction.model.ConverseData;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

@ExtendWith(MockitoExtension.class)
class AiClientTest {

  @Mock private ChatModel mockChatModel;

  @Mock private Document mockDocument;

  @Mock private DocumentMetadata mockMetadata;

  // Test implementation of abstract AiClient for testing base functionality
  private static class TestAiClient extends AiClient {
    public TestAiClient(ChatModel chatModel) {
      this.chatModel = chatModel;
    }
  }

  @Nested
  class BaseFunctionalityTests {

    @Test
    void chat_shouldSucceed_withSingleStringInput() {
      // given
      TestAiClient client = new TestAiClient(mockChatModel);
      String input = "Hello AI";
      String expectedResponse = "Hello! How can I help you?";

      when(mockChatModel.chat(input)).thenReturn(expectedResponse);

      // when
      String response = client.chat(input);

      // then
      assertThat(response).isEqualTo(expectedResponse);
      verify(mockChatModel).chat(input);
    }

    @Test
    void chat_shouldSucceed_withSystemAndUserMessages() {
      // given
      TestAiClient client = new TestAiClient(mockChatModel);
      String systemMessage = "You are a helpful assistant";
      String userMessage = "Extract data from this document";

      ChatResponse chatResponse = createChatResponse("Extracted data");
      when(mockChatModel.chat(any(), any())).thenReturn(chatResponse);

      // when
      ChatResponse response = client.chat(systemMessage, userMessage);

      // then
      assertThat(response).isNotNull();
      assertThat(response.aiMessage().text()).isEqualTo("Extracted data");
      verify(mockChatModel).chat(any(), any());
    }

    @Test
    void chat_shouldSucceed_withDocumentUsingLink_whenPdfContentType() throws Exception {
      // given
      TestAiClient client = new TestAiClient(mockChatModel);
      String systemMessage = "You are a helpful assistant";
      String userMessage = "Extract data from this PDF";
      String documentLink = "https://example.com/document.pdf";

      when(mockDocument.metadata()).thenReturn(mockMetadata);
      when(mockMetadata.getContentType()).thenReturn("application/pdf");
      when(mockDocument.generateLink(any(DocumentLinkParameters.class))).thenReturn(documentLink);

      ChatResponse chatResponse = createChatResponse("Extracted data");
      when(mockChatModel.chat(any(), any())).thenReturn(chatResponse);

      // when
      ChatResponse response = client.chat(systemMessage, userMessage, mockDocument);

      // then
      assertThat(response).isNotNull();
      assertThat(response.aiMessage().text()).isEqualTo("Extracted data");
      verify(mockDocument).generateLink(any(DocumentLinkParameters.class));
      verify(mockChatModel).chat(any(), any());
    }

    @Test
    void chat_shouldSucceed_withDocumentUsingLink_whenImageContentType() throws Exception {
      // given
      TestAiClient client = new TestAiClient(mockChatModel);
      String systemMessage = "You are a helpful assistant";
      String userMessage = "Extract data from this image";
      String documentLink = "https://example.com/image.png";

      when(mockDocument.metadata()).thenReturn(mockMetadata);
      when(mockMetadata.getContentType()).thenReturn("image/png");
      when(mockDocument.generateLink(any(DocumentLinkParameters.class))).thenReturn(documentLink);

      ChatResponse chatResponse = createChatResponse("Extracted data");
      when(mockChatModel.chat(any(), any())).thenReturn(chatResponse);

      // when
      ChatResponse response = client.chat(systemMessage, userMessage, mockDocument);

      // then
      assertThat(response).isNotNull();
      assertThat(response.aiMessage().text()).isEqualTo("Extracted data");
      verify(mockDocument).generateLink(any(DocumentLinkParameters.class));
      verify(mockChatModel).chat(any(), any());
    }

    @Test
    void chat_shouldFallbackToBase64_whenLinkGenerationFails() {
      // given
      TestAiClient client = new TestAiClient(mockChatModel);
      String systemMessage = "You are a helpful assistant";
      String userMessage = "Extract data from this document";
      String base64Content = "base64encodedcontent";

      when(mockDocument.metadata()).thenReturn(mockMetadata);
      when(mockMetadata.getContentType()).thenReturn("application/pdf");
      when(mockDocument.generateLink(any(DocumentLinkParameters.class)))
          .thenThrow(new RuntimeException("Link generation failed"));
      when(mockDocument.asBase64()).thenReturn(base64Content);

      ChatResponse chatResponse = createChatResponse("Extracted data");
      when(mockChatModel.chat(any(), any())).thenReturn(chatResponse);

      // when
      ChatResponse response = client.chat(systemMessage, userMessage, mockDocument);

      // then
      assertThat(response).isNotNull();
      assertThat(response.aiMessage().text()).isEqualTo("Extracted data");
      verify(mockDocument).asBase64();
      verify(mockChatModel).chat(any(), any());
    }

    @Test
    void chat_shouldDefaultToPdf_whenContentTypeIsNull() {
      // given
      TestAiClient client = new TestAiClient(mockChatModel);
      String systemMessage = "You are a helpful assistant";
      String userMessage = "Extract data from this document";
      String base64Content = "base64encodedcontent";

      when(mockDocument.metadata()).thenReturn(null);
      when(mockDocument.generateLink(any(DocumentLinkParameters.class)))
          .thenThrow(new RuntimeException("Link generation failed"));
      when(mockDocument.asBase64()).thenReturn(base64Content);

      ChatResponse chatResponse = createChatResponse("Extracted data");
      when(mockChatModel.chat(any(), any())).thenReturn(chatResponse);

      // when
      ChatResponse response = client.chat(systemMessage, userMessage, mockDocument);

      // then
      assertThat(response).isNotNull();
      verify(mockDocument).asBase64();
      verify(mockChatModel).chat(any(), any());
    }

    @Test
    void chat_shouldHandleImageContentTypes() {
      // given
      TestAiClient client = new TestAiClient(mockChatModel);
      String systemMessage = "You are a helpful assistant";
      String userMessage = "Extract data from this image";
      String base64Content = "base64encodedimage";

      when(mockDocument.metadata()).thenReturn(mockMetadata);
      when(mockMetadata.getContentType()).thenReturn("image/jpeg");
      when(mockDocument.generateLink(any(DocumentLinkParameters.class)))
          .thenThrow(new RuntimeException("Link generation failed"));
      when(mockDocument.asBase64()).thenReturn(base64Content);

      ChatResponse chatResponse = createChatResponse("Extracted data");
      when(mockChatModel.chat(any(), any())).thenReturn(chatResponse);

      // when
      ChatResponse response = client.chat(systemMessage, userMessage, mockDocument);

      // then
      assertThat(response).isNotNull();
      verify(mockDocument).asBase64();
      verify(mockChatModel).chat(any(), any());
    }
  }

  @Nested
  class ImplementationConfigurationTests {

    @Test
    void openAiClient_shouldConfigureCorrectly_withMinimalParameters() {
      // given
      String endpoint = "https://api.openai.com/v1";
      Map<String, String> headers = new HashMap<>();
      headers.put("Authorization", "Bearer sk-test-key");
      ConverseData converseData = new ConverseData("gpt-4", null, null, null);

      // when & then
      assertThatCode(() -> new OpenAiClient(endpoint, headers, converseData))
          .doesNotThrowAnyException();
    }

    @Test
    void openAiClient_shouldConfigureCorrectly_withAllParameters() {
      // given
      String endpoint = "https://api.openai.com/v1";
      Map<String, String> headers = new HashMap<>();
      headers.put("Authorization", "Bearer sk-test-key");
      ConverseData converseData = new ConverseData("gpt-4", 1000, 0.7f, 0.9f);

      // when & then
      assertThatCode(() -> new OpenAiClient(endpoint, headers, converseData))
          .doesNotThrowAnyException();
    }

    @Test
    void azureOpenAiClient_shouldConfigureCorrectly_withMinimalParameters() {
      // given
      String endpoint = "https://my-resource.openai.azure.com";
      String apiKey = "test-api-key";
      ConverseData converseData = new ConverseData("gpt-4-deployment", null, null, null);

      // when & then
      assertThatCode(() -> new AzureOpenAiClient(endpoint, apiKey, converseData))
          .doesNotThrowAnyException();
    }

    @Test
    void azureOpenAiClient_shouldConfigureCorrectly_withAllParameters() {
      // given
      String endpoint = "https://my-resource.openai.azure.com";
      String apiKey = "test-api-key";
      ConverseData converseData = new ConverseData("gpt-4-deployment", 1000, 0.7f, 0.9f);

      // when & then
      assertThatCode(() -> new AzureOpenAiClient(endpoint, apiKey, converseData))
          .doesNotThrowAnyException();
    }

    @Test
    void azureAiFoundryClient_shouldConfigureCorrectly_withMinimalParameters() {
      // given
      String endpoint = "https://idp-ai-provider.services.ai.azure.com/models";
      String apiKey = "test-api-key";
      ConverseData converseData = new ConverseData("gpt-4", null, null, null);

      // when & then
      assertThatCode(() -> new AzureAiFoundryClient(endpoint, apiKey, converseData))
          .doesNotThrowAnyException();
    }

    @Test
    void azureAiFoundryClient_shouldConfigureCorrectly_withAllParameters() {
      // given
      String endpoint = "https://idp-ai-provider.services.ai.azure.com/models";
      String apiKey = "test-api-key";
      ConverseData converseData = new ConverseData("gpt-4", 1000, 0.7f, 0.9f);

      // when & then
      assertThatCode(() -> new AzureAiFoundryClient(endpoint, apiKey, converseData))
          .doesNotThrowAnyException();
    }

    @Test
    void azureAiFoundryClient_shouldHandleEndpointWithTrailingSlash() {
      // given
      String endpoint = "https://idp-ai-provider.services.ai.azure.com/models/";
      String apiKey = "test-api-key";
      ConverseData converseData = new ConverseData("gpt-4", null, null, null);

      // when & then
      assertThatCode(() -> new AzureAiFoundryClient(endpoint, apiKey, converseData))
          .doesNotThrowAnyException();
    }

    @Test
    void bedrockAiClient_shouldConfigureCorrectly_withMinimalParameters() {
      // given
      var credentialsProvider =
          StaticCredentialsProvider.create(
              AwsBasicCredentials.create("test-access-key", "test-secret-key"));
      String region = "us-east-1";
      ConverseData converseData =
          new ConverseData("anthropic.claude-3-5-sonnet-20240620-v1:0", null, null, null);

      // when & then
      assertThatCode(() -> new BedrockAiClient(credentialsProvider, region, converseData))
          .doesNotThrowAnyException();
    }

    @Test
    void bedrockAiClient_shouldConfigureCorrectly_withAllParameters() {
      // given
      var credentialsProvider =
          StaticCredentialsProvider.create(
              AwsBasicCredentials.create("test-access-key", "test-secret-key"));
      String region = "us-east-1";
      ConverseData converseData =
          new ConverseData("anthropic.claude-3-5-sonnet-20240620-v1:0", 1000, 0.7f, 0.9f);

      // when & then
      assertThatCode(() -> new BedrockAiClient(credentialsProvider, region, converseData))
          .doesNotThrowAnyException();
    }

    @Test
    void bedrockAiClient_shouldConfigureCorrectly_withCrossRegionInference() {
      // given
      var credentialsProvider =
          StaticCredentialsProvider.create(
              AwsBasicCredentials.create("test-access-key", "test-secret-key"));
      String region = "us-west-2"; // Cross-region inference supported region
      ConverseData converseData =
          new ConverseData("us.anthropic.claude-3-5-sonnet-20240620-v1:0", null, null, null);

      // when & then
      assertThatCode(() -> new BedrockAiClient(credentialsProvider, region, converseData))
          .doesNotThrowAnyException();
    }
  }

  @Nested
  class ConverseDataParameterTests {

    @Test
    void converseData_shouldHandleNullOptionalParameters() {
      // given
      ConverseData converseData = new ConverseData("model-id", null, null, null);

      // when & then
      assertThat(converseData.modelId()).isEqualTo("model-id");
      assertThat(converseData.maxTokens()).isNull();
      assertThat(converseData.temperature()).isNull();
      assertThat(converseData.topP()).isNull();
    }

    @Test
    void converseData_shouldHandleAllParameters() {
      // given
      ConverseData converseData = new ConverseData("model-id", 2000, 0.8f, 0.95f);

      // when & then
      assertThat(converseData.modelId()).isEqualTo("model-id");
      assertThat(converseData.maxTokens()).isEqualTo(2000);
      assertThat(converseData.temperature()).isEqualTo(0.8f);
      assertThat(converseData.topP()).isEqualTo(0.95f);
    }

    @Test
    void converseData_shouldHandlePartialParameters() {
      // given
      ConverseData converseData = new ConverseData("model-id", null, 0.7f, null);

      // when & then
      assertThat(converseData.modelId()).isEqualTo("model-id");
      assertThat(converseData.maxTokens()).isNull();
      assertThat(converseData.temperature()).isEqualTo(0.7f);
      assertThat(converseData.topP()).isNull();
    }
  }

  // Helper methods

  private ChatResponse createChatResponse(String responseText) {
    AiMessage aiMessage = AiMessage.from(responseText);
    TokenUsage tokenUsage = new TokenUsage(100, 50);
    return ChatResponse.builder().aiMessage(aiMessage).tokenUsage(tokenUsage).build();
  }
}
