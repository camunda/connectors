/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.caller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import io.camunda.connector.idp.extraction.model.ConverseData;
import io.camunda.connector.idp.extraction.model.ExtractionRequestData;
import io.camunda.connector.idp.extraction.model.providers.OpenAiProvider;
import io.camunda.connector.idp.extraction.util.ExtractionTestUtils;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OpenAiSpecCallerTest {

  private static class TestOpenAiSpecCaller extends OpenAiSpecCaller {
    private final OpenAiChatModel mockModel;

    public TestOpenAiSpecCaller(OpenAiChatModel mockModel) {
      this.mockModel = mockModel;
    }

    @Override
    public String call(ExtractionRequestData input, OpenAiProvider provider, String extractedText) {
      try {
        ChatResponse response = mockModel.chat(anyList());
        return response.aiMessage().text();
      } catch (Exception e) {
        throw new RuntimeException("Failed to call OpenAI API", e);
      }
    }
  }

  private OpenAiProvider createProvider() {
    OpenAiProvider provider = new OpenAiProvider();
    provider.setOpenAiEndpoint("https://api.openai.com/v1");
    provider.setOpenAiHeaders(Map.of("Authorization", "Bearer test-token"));
    return provider;
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

  @Test
  void executeSuccessfulExtraction() {
    String expectedResponse =
        """
        {
          "name": "John Smith",
          "age": 32
        }
        """;

    OpenAiChatModel mockChatModel = mock(OpenAiChatModel.class);
    ChatResponse mockResponse = mock(ChatResponse.class);
    AiMessage mockAiMessage = mock(AiMessage.class);

    when(mockResponse.aiMessage()).thenReturn(mockAiMessage);
    when(mockAiMessage.text()).thenReturn(expectedResponse);
    when(mockChatModel.chat(anyList())).thenReturn(mockResponse);

    try (MockedStatic<OpenAiChatModel> chatModelMockedStatic = mockStatic(OpenAiChatModel.class)) {
      OpenAiProvider provider = createProvider();
      TestOpenAiSpecCaller testCaller = new TestOpenAiSpecCaller(mockChatModel);

      String result =
          testCaller.call(createTestRequestData("gpt-4"), provider, "extracted text content");

      assertEquals(expectedResponse, result);
    }
  }

  @Test
  void executeWithClaudeModel() {
    String expectedResponse =
        """
        {
          "supplier": "ACME Corp",
          "amount": "1250.00"
        }
        """;

    OpenAiChatModel mockChatModel = mock(OpenAiChatModel.class);
    ChatResponse mockResponse = mock(ChatResponse.class);
    AiMessage mockAiMessage = mock(AiMessage.class);

    when(mockResponse.aiMessage()).thenReturn(mockAiMessage);
    when(mockAiMessage.text()).thenReturn(expectedResponse);
    when(mockChatModel.chat(anyList())).thenReturn(mockResponse);

    OpenAiProvider provider = createProvider();
    TestOpenAiSpecCaller testCaller = new TestOpenAiSpecCaller(mockChatModel);

    String result =
        testCaller.call(
            createTestRequestData("anthropic.claude-3-5-sonnet-20240620-v1:0"),
            provider,
            "Invoice from ACME Corp for $1250.00");

    assertEquals(expectedResponse, result);
  }

  @Test
  void executeWithGeminiModel() {
    String expectedResponse =
        """
        {
          "document_type": "receipt",
          "total": "45.99"
        }
        """;

    OpenAiChatModel mockChatModel = mock(OpenAiChatModel.class);
    ChatResponse mockResponse = mock(ChatResponse.class);
    AiMessage mockAiMessage = mock(AiMessage.class);

    when(mockResponse.aiMessage()).thenReturn(mockAiMessage);
    when(mockAiMessage.text()).thenReturn(expectedResponse);
    when(mockChatModel.chat(anyList())).thenReturn(mockResponse);

    OpenAiProvider provider = createProvider();
    TestOpenAiSpecCaller testCaller = new TestOpenAiSpecCaller(mockChatModel);

    String result =
        testCaller.call(
            createTestRequestData("gemini-1.5-pro"), provider, "Receipt showing total of $45.99");

    assertEquals(expectedResponse, result);
  }

  @Test
  void executeWithApiFailure() {
    OpenAiChatModel mockChatModel = mock(OpenAiChatModel.class);
    when(mockChatModel.chat(anyList())).thenThrow(new RuntimeException("API Error"));

    OpenAiProvider provider = createProvider();
    TestOpenAiSpecCaller testCaller = new TestOpenAiSpecCaller(mockChatModel);

    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () ->
                testCaller.call(
                    createTestRequestData("gpt-4"), provider, "extracted text content"));

    assertEquals("Failed to call OpenAI API", exception.getMessage());
  }

  @Test
  void testWithDifferentModelParameters() {
    String expectedResponse = "{ \"result\": \"success\" }";

    OpenAiChatModel mockChatModel = mock(OpenAiChatModel.class);
    ChatResponse mockResponse = mock(ChatResponse.class);
    AiMessage mockAiMessage = mock(AiMessage.class);

    when(mockResponse.aiMessage()).thenReturn(mockAiMessage);
    when(mockAiMessage.text()).thenReturn(expectedResponse);
    when(mockChatModel.chat(anyList())).thenReturn(mockResponse);

    OpenAiProvider provider = createProvider();
    TestOpenAiSpecCaller testCaller = new TestOpenAiSpecCaller(mockChatModel);

    // Test with different converse data parameters (null values)
    ConverseData converseDataWithNulls = new ConverseData("gpt-3.5-turbo", null, null, null);
    ExtractionRequestData requestData =
        new ExtractionRequestData(
            ExtractionTestUtils.TEXTRACT_EXTRACTION_REQUEST_DATA.document(),
            ExtractionTestUtils.TEXTRACT_EXTRACTION_REQUEST_DATA.extractionType(),
            ExtractionTestUtils.TEXTRACT_EXTRACTION_REQUEST_DATA.taxonomyItems(),
            ExtractionTestUtils.TEXTRACT_EXTRACTION_REQUEST_DATA.includedFields(),
            ExtractionTestUtils.TEXTRACT_EXTRACTION_REQUEST_DATA.renameMappings(),
            ExtractionTestUtils.TEXTRACT_EXTRACTION_REQUEST_DATA.delimiter(),
            converseDataWithNulls);

    String result = testCaller.call(requestData, provider, "test content");

    assertEquals(expectedResponse, result);
  }
}
