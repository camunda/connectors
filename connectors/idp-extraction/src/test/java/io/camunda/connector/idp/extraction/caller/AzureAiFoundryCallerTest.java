/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.caller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.idp.extraction.client.ai.AzureAiFoundryClient;
import io.camunda.connector.idp.extraction.client.ai.AzureOpenAiClient;
import io.camunda.connector.idp.extraction.client.ai.base.AiClient;
import io.camunda.connector.idp.extraction.model.ConverseData;
import io.camunda.connector.idp.extraction.model.ExtractionRequestData;
import io.camunda.connector.idp.extraction.model.LlmModel;
import io.camunda.connector.idp.extraction.model.providers.AzureProvider;
import io.camunda.connector.idp.extraction.model.providers.azure.AiFoundryConfig;
import io.camunda.connector.idp.extraction.util.ExtractionTestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AzureAiFoundryCallerTest {

  private AzureAiFoundryCaller azureAiFoundryCaller;
  private AiClient aiClient;
  private AzureProvider azureProvider;

  @BeforeEach
  void setUp() {
    azureAiFoundryCaller = spy(new AzureAiFoundryCaller());
    aiClient = mock(AiClient.class);
    azureProvider = createAzureProvider(false);
  }

  @Test
  void shouldCreateAzureAiFoundryClientForBaseFoundryModels() {
    AiClient client =
        new AzureAiFoundryCaller()
            .createAiClient(createTestRequestData("DeepSeek-V3"), createAzureProvider(false));

    assertThat(client).isInstanceOf(AzureAiFoundryClient.class);
  }

  @Test
  void shouldCreateAzureOpenAiClientForOpenAiModels() {
    AiClient client =
        new AzureAiFoundryCaller()
            .createAiClient(createTestRequestData("gpt-4o"), createAzureProvider(true));

    assertThat(client).isInstanceOf(AzureOpenAiClient.class);
  }

  @Test
  void executeSuccessfulExtraction() {
    String expectedResponse =
        """
        {
          "name": "John Smith",
          "age": 32
        }
        """;
    ExtractionRequestData requestData = createTestRequestData("gpt-4o");
    doReturn(aiClient).when(azureAiFoundryCaller).createAiClient(requestData, azureProvider);
    when(aiClient.chat(anyString(), anyString())).thenReturn(expectedResponse);

    String result = azureAiFoundryCaller.call(requestData, azureProvider, "extracted text");

    assertEquals(expectedResponse, result);
  }

  @Test
  void shouldSendSystemPromptSeparatelyWhenAllowed() {
    ExtractionRequestData requestData =
        createTestRequestData("anthropic.claude-3-sonnet-20240229-v1:0");
    doReturn(aiClient).when(azureAiFoundryCaller).createAiClient(requestData, azureProvider);
    when(aiClient.chat(anyString(), anyString())).thenReturn("{}");

    azureAiFoundryCaller.call(requestData, azureProvider, "extracted text");

    ArgumentCaptor<String> userMessage = ArgumentCaptor.forClass(String.class);
    verify(aiClient).chat(eq(LlmModel.CLAUDE.getSystemPrompt()), userMessage.capture());
    assertThat(userMessage.getValue()).contains("extracted text");
  }

  @Test
  void shouldMergeSystemPromptIntoUserMessageForTitan() {
    ExtractionRequestData requestData = createTestRequestData("amazon.titan-text-express-v1");
    doReturn(aiClient).when(azureAiFoundryCaller).createAiClient(requestData, azureProvider);
    when(aiClient.chat(anyString())).thenReturn("{}");

    azureAiFoundryCaller.call(requestData, azureProvider, "extracted text");

    ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
    verify(aiClient).chat(message.capture());
    verify(aiClient, never()).chat(anyString(), anyString());
    assertThat(message.getValue())
        .startsWith(LlmModel.TITAN.getSystemPrompt())
        .contains("extracted text");
  }

  @Test
  void shouldThrowRuntimeExceptionOnClientFailure() {
    ExtractionRequestData requestData = createTestRequestData("gpt-4o");
    doReturn(aiClient).when(azureAiFoundryCaller).createAiClient(requestData, azureProvider);
    when(aiClient.chat(anyString(), anyString()))
        .thenThrow(new RuntimeException("Azure service unavailable"));

    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () -> azureAiFoundryCaller.call(requestData, azureProvider, "extracted text"));

    assertTrue(exception.getMessage().contains("Failed to call Azure AI Foundry"));
    assertTrue(exception.getCause().getMessage().contains("Azure service unavailable"));
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

  private AzureProvider createAzureProvider(boolean usingOpenAI) {
    AiFoundryConfig config = new AiFoundryConfig();
    config.setEndpoint("https://test-foundry.services.ai.azure.com/models");
    config.setApiKey("test-api-key");
    config.setUsingOpenAI(usingOpenAI);

    AzureProvider provider = new AzureProvider();
    provider.setAiFoundryConfig(config);
    return provider;
  }
}
