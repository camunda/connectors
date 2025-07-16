/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.caller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.azure.ai.inference.ChatCompletionsClient;
import com.azure.ai.inference.models.ChatCompletions;
import com.azure.ai.inference.models.ChatCompletionsOptions;
import com.azure.ai.openai.OpenAIClient;
import io.camunda.connector.idp.extraction.model.ConverseData;
import io.camunda.connector.idp.extraction.model.ExtractionRequestData;
import io.camunda.connector.idp.extraction.model.providers.AzureProvider;
import io.camunda.connector.idp.extraction.model.providers.azure.AIFoundryConfig;
import io.camunda.connector.idp.extraction.supplier.AzureAiFoundrySupplier;
import io.camunda.connector.idp.extraction.util.ExtractionTestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AzureAiFoundryCallerTest {

  private AzureAiFoundryCaller azureAiFoundryCaller;
  private AzureProvider azureProvider;

  @BeforeEach
  void setUp() {
    azureAiFoundryCaller = new AzureAiFoundryCaller();
    azureProvider = createAzureProvider(false); // Default to non-OpenAI
  }

  @Test
  void executeSuccessfulExtractionWithChatCompletionsClient() {
    String expectedResponse =
        """
        {
          "name": "John Smith",
          "age": 32
        }
        """;

    ChatCompletionsClient mockClient = mock(ChatCompletionsClient.class);
    ChatCompletions mockResponse = mock(ChatCompletions.class, Mockito.RETURNS_DEEP_STUBS);

    when(mockResponse.getChoices().get(0).getMessage().getContent()).thenReturn(expectedResponse);
    when(mockClient.complete(any(ChatCompletionsOptions.class))).thenReturn(mockResponse);

    try (MockedStatic<AzureAiFoundrySupplier> mockedSupplier =
        mockStatic(AzureAiFoundrySupplier.class)) {
      mockedSupplier
          .when(() -> AzureAiFoundrySupplier.getChatCompletionsClient(any()))
          .thenReturn(mockClient);

      ExtractionRequestData requestData =
          createTestRequestData("anthropic.claude-3-sonnet-20240229-v1:0");
      String result = azureAiFoundryCaller.call(requestData, azureProvider, "extracted text");

      assertEquals(expectedResponse, result);
      verify(mockClient).complete(any(ChatCompletionsOptions.class));
    }
  }

  @Test
  void executeSuccessfulExtractionWithOpenAIClient() {
    String expectedResponse =
        """
        {
          "name": "Jane Doe",
          "age": 28
        }
        """;

    azureProvider = createAzureProvider(true); // Use OpenAI client

    OpenAIClient mockClient = mock(OpenAIClient.class);
    com.azure.ai.openai.models.ChatCompletions mockResponse =
        mock(com.azure.ai.openai.models.ChatCompletions.class, Mockito.RETURNS_DEEP_STUBS);

    when(mockResponse.getChoices().get(0).getMessage().getContent()).thenReturn(expectedResponse);
    when(mockClient.getChatCompletions(
            anyString(), any(com.azure.ai.openai.models.ChatCompletionsOptions.class)))
        .thenReturn(mockResponse);

    try (MockedStatic<AzureAiFoundrySupplier> mockedSupplier =
        mockStatic(AzureAiFoundrySupplier.class)) {
      mockedSupplier
          .when(() -> AzureAiFoundrySupplier.getOpenAIClient(any()))
          .thenReturn(mockClient);

      ExtractionRequestData requestData = createTestRequestData("gpt-4");
      String result = azureAiFoundryCaller.call(requestData, azureProvider, "extracted text");

      assertEquals(expectedResponse, result);
      verify(mockClient)
          .getChatCompletions(
              anyString(), any(com.azure.ai.openai.models.ChatCompletionsOptions.class));
    }
  }

  @Test
  void shouldHandleClaudeModelWithSystemPromptSupport() {
    String expectedResponse = "{ \"result\": \"success\" }";
    String modelId = "anthropic.claude-3-sonnet-20240229-v1:0";

    ChatCompletionsClient mockClient = mock(ChatCompletionsClient.class);
    ChatCompletions mockResponse = mock(ChatCompletions.class, Mockito.RETURNS_DEEP_STUBS);

    when(mockResponse.getChoices().get(0).getMessage().getContent()).thenReturn(expectedResponse);
    when(mockClient.complete(any(ChatCompletionsOptions.class))).thenReturn(mockResponse);

    try (MockedStatic<AzureAiFoundrySupplier> mockedSupplier =
        mockStatic(AzureAiFoundrySupplier.class)) {
      mockedSupplier
          .when(() -> AzureAiFoundrySupplier.getChatCompletionsClient(any()))
          .thenReturn(mockClient);

      ExtractionRequestData requestData = createTestRequestData(modelId);
      String result = azureAiFoundryCaller.call(requestData, azureProvider, "extracted text");

      assertEquals(expectedResponse, result);

      // Verify that the options were set correctly
      ArgumentCaptor<ChatCompletionsOptions> optionsCaptor =
          ArgumentCaptor.forClass(ChatCompletionsOptions.class);
      verify(mockClient).complete(optionsCaptor.capture());

      ChatCompletionsOptions options = optionsCaptor.getValue();
      assertEquals(modelId, options.getModel());
      assertEquals(0.5, options.getTemperature(), 0.001);
      assertEquals(512, options.getMaxTokens());
      assertEquals(0.9, options.getTopP(), 0.001);
    }
  }

  @Test
  void shouldHandleTitanModelWithoutSystemPromptSupport() {
    String expectedResponse = "{ \"result\": \"titan_response\" }";
    String modelId = "amazon.titan-text-express-v1";

    ChatCompletionsClient mockClient = mock(ChatCompletionsClient.class);
    ChatCompletions mockResponse = mock(ChatCompletions.class, Mockito.RETURNS_DEEP_STUBS);

    when(mockResponse.getChoices().get(0).getMessage().getContent()).thenReturn(expectedResponse);
    when(mockClient.complete(any(ChatCompletionsOptions.class))).thenReturn(mockResponse);

    try (MockedStatic<AzureAiFoundrySupplier> mockedSupplier =
        mockStatic(AzureAiFoundrySupplier.class)) {
      mockedSupplier
          .when(() -> AzureAiFoundrySupplier.getChatCompletionsClient(any()))
          .thenReturn(mockClient);

      ExtractionRequestData requestData = createTestRequestData(modelId);
      String result = azureAiFoundryCaller.call(requestData, azureProvider, "extracted text");

      assertEquals(expectedResponse, result);

      // Verify that the call was made with the correct options
      ArgumentCaptor<ChatCompletionsOptions> optionsCaptor =
          ArgumentCaptor.forClass(ChatCompletionsOptions.class);
      verify(mockClient).complete(optionsCaptor.capture());

      ChatCompletionsOptions options = optionsCaptor.getValue();
      assertEquals(modelId, options.getModel());
    }
  }

  @Test
  void shouldHandleGeminiModelWithOpenAIClient() {
    String expectedResponse = "{ \"result\": \"gemini_response\" }";
    String modelId = "gemini-pro";

    azureProvider = createAzureProvider(true); // Use OpenAI client

    OpenAIClient mockClient = mock(OpenAIClient.class);
    com.azure.ai.openai.models.ChatCompletions mockResponse =
        mock(com.azure.ai.openai.models.ChatCompletions.class, Mockito.RETURNS_DEEP_STUBS);

    when(mockResponse.getChoices().get(0).getMessage().getContent()).thenReturn(expectedResponse);
    when(mockClient.getChatCompletions(
            anyString(), any(com.azure.ai.openai.models.ChatCompletionsOptions.class)))
        .thenReturn(mockResponse);

    try (MockedStatic<AzureAiFoundrySupplier> mockedSupplier =
        mockStatic(AzureAiFoundrySupplier.class)) {
      mockedSupplier
          .when(() -> AzureAiFoundrySupplier.getOpenAIClient(any()))
          .thenReturn(mockClient);

      ExtractionRequestData requestData = createTestRequestData(modelId);
      String result = azureAiFoundryCaller.call(requestData, azureProvider, "extracted text");

      assertEquals(expectedResponse, result);

      // Verify that the options were set correctly
      ArgumentCaptor<com.azure.ai.openai.models.ChatCompletionsOptions> optionsCaptor =
          ArgumentCaptor.forClass(com.azure.ai.openai.models.ChatCompletionsOptions.class);
      verify(mockClient).getChatCompletions(anyString(), optionsCaptor.capture());

      com.azure.ai.openai.models.ChatCompletionsOptions options = optionsCaptor.getValue();
      assertEquals(0.5, options.getTemperature(), 0.001);
      assertEquals(512, options.getMaxTokens());
      assertEquals(0.9, options.getTopP(), 0.001);
    }
  }

  @Test
  void shouldThrowRuntimeExceptionOnClientFailure() {
    ChatCompletionsClient mockClient = mock(ChatCompletionsClient.class);
    when(mockClient.complete(any(ChatCompletionsOptions.class)))
        .thenThrow(new RuntimeException("Azure service unavailable"));

    try (MockedStatic<AzureAiFoundrySupplier> mockedSupplier =
        mockStatic(AzureAiFoundrySupplier.class)) {
      mockedSupplier
          .when(() -> AzureAiFoundrySupplier.getChatCompletionsClient(any()))
          .thenReturn(mockClient);

      ExtractionRequestData requestData =
          createTestRequestData("anthropic.claude-3-sonnet-20240229-v1:0");

      RuntimeException exception =
          assertThrows(
              RuntimeException.class,
              () -> azureAiFoundryCaller.call(requestData, azureProvider, "extracted text"));

      assertTrue(exception.getMessage().contains("Failed to call Azure AI Foundry"));
      assertTrue(exception.getCause().getMessage().contains("Azure service unavailable"));
    }
  }

  @Test
  void shouldThrowRuntimeExceptionOnOpenAIClientFailure() {
    azureProvider = createAzureProvider(true); // Use OpenAI client

    OpenAIClient mockClient = mock(OpenAIClient.class);
    when(mockClient.getChatCompletions(
            anyString(), any(com.azure.ai.openai.models.ChatCompletionsOptions.class)))
        .thenThrow(new RuntimeException("OpenAI service error"));

    try (MockedStatic<AzureAiFoundrySupplier> mockedSupplier =
        mockStatic(AzureAiFoundrySupplier.class)) {
      mockedSupplier
          .when(() -> AzureAiFoundrySupplier.getOpenAIClient(any()))
          .thenReturn(mockClient);

      ExtractionRequestData requestData = createTestRequestData("gpt-4");

      RuntimeException exception =
          assertThrows(
              RuntimeException.class,
              () -> azureAiFoundryCaller.call(requestData, azureProvider, "extracted text"));

      assertTrue(exception.getMessage().contains("Failed to call Azure AI Foundry"));
      assertTrue(exception.getCause().getMessage().contains("OpenAI service error"));
    }
  }

  @Test
  void shouldHandleLlamaModelWithSpecialFormatting() {
    String expectedResponse = "{ \"result\": \"llama_response\" }";
    String modelId = "meta.llama-2-70b-chat-v1";

    ChatCompletionsClient mockClient = mock(ChatCompletionsClient.class);
    ChatCompletions mockResponse = mock(ChatCompletions.class, Mockito.RETURNS_DEEP_STUBS);

    when(mockResponse.getChoices().get(0).getMessage().getContent()).thenReturn(expectedResponse);
    when(mockClient.complete(any(ChatCompletionsOptions.class))).thenReturn(mockResponse);

    try (MockedStatic<AzureAiFoundrySupplier> mockedSupplier =
        mockStatic(AzureAiFoundrySupplier.class)) {
      mockedSupplier
          .when(() -> AzureAiFoundrySupplier.getChatCompletionsClient(any()))
          .thenReturn(mockClient);

      ExtractionRequestData requestData = createTestRequestData(modelId);
      String result = azureAiFoundryCaller.call(requestData, azureProvider, "extracted text");

      assertEquals(expectedResponse, result);
      verify(mockClient).complete(any(ChatCompletionsOptions.class));
    }
  }

  private ExtractionRequestData createTestRequestData(String modelId) {
    ConverseData converseData = new ConverseData(modelId, 512, 0.5f, 0.9f);
    return new ExtractionRequestData(
        ExtractionTestUtils.TEXTRACT_EXTRACTION_REQUEST_DATA.document(),
        ExtractionTestUtils.TEXTRACT_EXTRACTION_REQUEST_DATA.extractionType(),
        ExtractionTestUtils.TEXTRACT_EXTRACTION_REQUEST_DATA.taxonomyItems(),
        ExtractionTestUtils.TEXTRACT_EXTRACTION_REQUEST_DATA.includedFields(),
        ExtractionTestUtils.TEXTRACT_EXTRACTION_REQUEST_DATA.renameMappings(),
        ExtractionTestUtils.TEXTRACT_EXTRACTION_REQUEST_DATA.delimiter(),
        converseData);
  }

  private AIFoundryConfig createAIFoundryConfig() {
    AIFoundryConfig config = new AIFoundryConfig();
    config.setEndpoint("https://test-foundry.openai.azure.com/");
    config.setApiKey("test-api-key");
    return config;
  }

  private AzureProvider createAzureProvider(boolean usingOpenAI) {
    AIFoundryConfig config = createAIFoundryConfig();
    // Set the usingOpenAI field
    try {
      var usingOpenAIField = AIFoundryConfig.class.getDeclaredField("usingOpenAI");
      usingOpenAIField.setAccessible(true);
      usingOpenAIField.set(config, usingOpenAI);
    } catch (Exception e) {
      throw new RuntimeException("Failed to set usingOpenAI field", e);
    }

    AzureProvider provider = new AzureProvider();
    provider.setAiFoundryConfig(config);
    return provider;
  }
}
