/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.service;

import static io.camunda.connector.idp.extraction.error.IdpErrorCodes.JSON_PARSING_FAILED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.idp.extraction.client.ai.base.AiClient;
import io.camunda.connector.idp.extraction.client.extraction.base.TextExtractor;
import io.camunda.connector.idp.extraction.model.ClassificationResult;
import io.camunda.connector.idp.extraction.model.ConverseData;
import io.camunda.connector.idp.extraction.model.DocumentType;
import io.camunda.connector.idp.extraction.request.classification.ClassificationRequest;
import io.camunda.connector.idp.extraction.request.classification.ClassificationRequestData;
import io.camunda.connector.idp.extraction.request.common.ai.AiProvider;
import io.camunda.connector.idp.extraction.request.common.ai.OpenAiRequest;
import io.camunda.connector.idp.extraction.request.common.extraction.ExtractionProvider;
import io.camunda.connector.idp.extraction.utils.ProviderUtil;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClassificationServiceTest {

  private ClassificationService classificationService;

  @Mock private AiClient aiClient;

  @Mock private TextExtractor textExtractor;

  @Mock private Document document;

  private AiProvider aiProvider;

  private MockedStatic<ProviderUtil> providerUtilMock;

  @BeforeEach
  void setUp() {
    classificationService = new ClassificationService();
    providerUtilMock = mockStatic(ProviderUtil.class);
    aiProvider =
        new OpenAiRequest("https://api.openai.com/v1", Map.of("Authorization", "Bearer test-key"));
  }

  @AfterEach
  void tearDown() {
    providerUtilMock.close();
  }

  @Test
  void execute_shouldSucceed_whenMultimodalPathWithValidJsonResponse() throws Exception {
    // given
    String jsonResponse =
        """
        {
          "extractedValue": "invoice",
          "confidence": "HIGH",
          "reasoning": "Document contains typical invoice elements"
        }
        """;

    ClassificationRequestData requestData =
        createRequestData(List.of(docType("invoice"), docType("receipt")));
    ClassificationRequest request =
        new ClassificationRequest(null, aiProvider, requestData); // null extractor = multimodal

    ChatResponse chatResponse = createChatResponse(jsonResponse);

    providerUtilMock
        .when(() -> ProviderUtil.getTextExtractor((ExtractionProvider) null))
        .thenReturn(null);
    providerUtilMock
        .when(() -> ProviderUtil.getAiClient(eq(aiProvider), any(ConverseData.class)))
        .thenReturn(aiClient);

    when(aiClient.chat(anyString(), anyString(), any(Document.class))).thenReturn(chatResponse);

    // when
    ClassificationResult result = classificationService.execute(request);

    // then
    assertThat(result.extractedValue()).isEqualTo("invoice");
    assertThat(result.confidence()).isEqualTo("HIGH");
    assertThat(result.reasoning()).isEqualTo("Document contains typical invoice elements");
    assertThat(result.metadata()).isNotNull();

    verify(aiClient).chat(anyString(), anyString(), eq(document));
  }

  @Test
  void execute_shouldSucceed_whenTextExtractionPathWithValidJsonResponse() throws Exception {
    // given
    String extractedText = "Invoice #12345\nAmount: $100\nDate: 2023-01-01";
    String jsonResponse =
        """
        {
          "extractedValue": "invoice",
          "confidence": "HIGH",
          "reasoning": "Contains invoice number and amount"
        }
        """;

    ClassificationRequestData requestData =
        createRequestData(List.of(docType("invoice"), docType("receipt")));
    ExtractionProvider extractionProvider = new ExtractionProvider.ApachePdfBoxExtractorRequest();
    ClassificationRequest request =
        new ClassificationRequest(extractionProvider, aiProvider, requestData);

    ChatResponse chatResponse = createChatResponse(jsonResponse);

    providerUtilMock
        .when(() -> ProviderUtil.getTextExtractor(extractionProvider))
        .thenReturn(textExtractor);
    providerUtilMock
        .when(() -> ProviderUtil.getAiClient(eq(aiProvider), any(ConverseData.class)))
        .thenReturn(aiClient);

    when(textExtractor.extract(document)).thenReturn(extractedText);
    when(aiClient.chat(anyString(), anyString())).thenReturn(chatResponse);

    // when
    ClassificationResult result = classificationService.execute(request);

    // then
    assertThat(result.extractedValue()).isEqualTo("invoice");
    assertThat(result.confidence()).isEqualTo("HIGH");
    assertThat(result.reasoning()).isEqualTo("Contains invoice number and amount");

    verify(textExtractor).extract(document);
    verify(aiClient).chat(anyString(), anyString());
  }

  @Test
  void execute_shouldSucceed_whenResponseWrappedInMarkdownCodeBlock() throws Exception {
    // given
    String jsonResponse =
        """
        ```json
        {
          "extractedValue": "receipt",
          "confidence": "HIGH",
          "reasoning": "Clear receipt format"
        }
        ```
        """;

    ClassificationRequestData requestData =
        createRequestData(List.of(docType("invoice"), docType("receipt")));
    ClassificationRequest request = new ClassificationRequest(null, aiProvider, requestData);

    ChatResponse chatResponse = createChatResponse(jsonResponse);

    setupMultimodalMocks();
    when(aiClient.chat(anyString(), anyString(), any(Document.class))).thenReturn(chatResponse);

    // when
    ClassificationResult result = classificationService.execute(request);

    // then
    assertThat(result.extractedValue()).isEqualTo("receipt");
    assertThat(result.confidence()).isEqualTo("HIGH");
    assertThat(result.reasoning()).isEqualTo("Clear receipt format");
  }

  @Test
  void execute_shouldSucceed_whenResponseContainsThinkingTags() throws Exception {
    // given
    String jsonResponse =
        """
        <thinking>Let me analyze this document...</thinking>
        {
          "extractedValue": "invoice",
          "confidence": "HIGH",
          "reasoning": "Invoice structure detected"
        }
        """;

    ClassificationRequestData requestData =
        createRequestData(List.of(docType("invoice"), docType("receipt")));
    ClassificationRequest request = new ClassificationRequest(null, aiProvider, requestData);

    ChatResponse chatResponse = createChatResponse(jsonResponse);

    setupMultimodalMocks();
    when(aiClient.chat(anyString(), anyString(), any(Document.class))).thenReturn(chatResponse);

    // when
    ClassificationResult result = classificationService.execute(request);

    // then
    assertThat(result.extractedValue()).isEqualTo("invoice");
    assertThat(result.confidence()).isEqualTo("HIGH");
    assertThat(result.reasoning()).isEqualTo("Invoice structure detected");
  }

  @Test
  void execute_shouldSucceed_whenResponseHasNestedResponseWrapper() throws Exception {
    // given
    String jsonResponse =
        """
        {
          "response": {
            "extractedValue": "contract",
            "confidence": "LOW",
            "reasoning": "Uncertain classification"
          }
        }
        """;

    ClassificationRequestData requestData =
        createRequestData(List.of(docType("contract"), docType("agreement")));
    ClassificationRequest request = new ClassificationRequest(null, aiProvider, requestData);

    ChatResponse chatResponse = createChatResponse(jsonResponse);

    setupMultimodalMocks();
    when(aiClient.chat(anyString(), anyString(), any(Document.class))).thenReturn(chatResponse);

    // when
    ClassificationResult result = classificationService.execute(request);

    // then
    assertThat(result.extractedValue()).isEqualTo("contract");
    assertThat(result.confidence()).isEqualTo("LOW");
    assertThat(result.reasoning()).isEqualTo("Uncertain classification");
  }

  @Test
  void execute_shouldSucceed_whenResponseHasNestedResponseWrapperAsString() throws Exception {
    // given
    String nestedJson =
        """
        {
          "extractedValue": "purchase_order",
          "confidence": "HIGH",
          "reasoning": "Clear PO format"
        }
        """;
    String jsonResponse = "{\"response\": " + "\"" + nestedJson.replace("\"", "\\\"") + "\"}";
    String cleanedResponse = nestedJson; // The cleanup should extract the nested JSON

    ClassificationRequestData requestData =
        createRequestData(List.of(docType("purchase_order"), docType("invoice")));
    ClassificationRequest request = new ClassificationRequest(null, aiProvider, requestData);

    ChatResponse initialResponse = createChatResponse(jsonResponse);
    ChatResponse cleanupResponse = createChatResponse(cleanedResponse);

    setupMultimodalMocks();
    when(aiClient.chat(anyString(), anyString(), any(Document.class))).thenReturn(initialResponse);
    when(aiClient.chat(anyString(), anyString())).thenReturn(cleanupResponse);

    // when
    ClassificationResult result = classificationService.execute(request);

    // then
    assertThat(result.extractedValue()).isEqualTo("purchase_order");
    assertThat(result.confidence()).isEqualTo("HIGH");
    assertThat(result.reasoning()).isEqualTo("Clear PO format");

    // Verify initial call with document
    verify(aiClient).chat(anyString(), anyString(), any(Document.class));
    // Verify cleanup call without document
    verify(aiClient).chat(anyString(), anyString());
  }

  @Test
  void execute_shouldAttemptCleanup_whenInitialParsingFails() throws Exception {
    // given
    String malformedResponse = "Here's the classification: {\"extractedValue\": \"invoice\"";
    String cleanedResponse =
        """
        {
          "extractedValue": "invoice",
          "confidence": "HIGH",
          "reasoning": "Fixed by cleanup"
        }
        """;

    ClassificationRequestData requestData =
        createRequestData(List.of(docType("invoice"), docType("receipt")));
    ClassificationRequest request = new ClassificationRequest(null, aiProvider, requestData);

    ChatResponse initialResponse = createChatResponse(malformedResponse);
    ChatResponse cleanupResponse = createChatResponse(cleanedResponse);

    setupMultimodalMocks();
    when(aiClient.chat(anyString(), anyString(), any(Document.class))).thenReturn(initialResponse);
    when(aiClient.chat(anyString(), anyString())).thenReturn(cleanupResponse);

    // when
    ClassificationResult result = classificationService.execute(request);

    // then
    assertThat(result.extractedValue()).isEqualTo("invoice");
    assertThat(result.confidence()).isEqualTo("HIGH");
    assertThat(result.reasoning()).isEqualTo("Fixed by cleanup");

    // Verify initial call with document
    verify(aiClient).chat(anyString(), anyString(), any(Document.class));
    // Verify cleanup call without document
    verify(aiClient).chat(anyString(), anyString());
  }

  @Test
  void execute_shouldThrowException_whenResponseIsNotJsonObject() throws Exception {
    // given
    String jsonResponse = "[]"; // Array instead of object

    ClassificationRequestData requestData =
        createRequestData(List.of(docType("invoice"), docType("receipt")));
    ClassificationRequest request = new ClassificationRequest(null, aiProvider, requestData);

    ChatResponse chatResponse = createChatResponse(jsonResponse);
    ChatResponse cleanupResponse = createChatResponse(jsonResponse); // Cleanup also fails

    setupMultimodalMocks();
    when(aiClient.chat(anyString(), anyString(), any(Document.class))).thenReturn(chatResponse);
    when(aiClient.chat(anyString(), anyString())).thenReturn(cleanupResponse);

    // when & then
    assertThatThrownBy(() -> classificationService.execute(request))
        .isInstanceOf(ConnectorException.class)
        .hasFieldOrPropertyWithValue("errorCode", JSON_PARSING_FAILED)
        .hasMessageContaining("Failed to parse JSON even after cleanup attempt");
  }

  @Test
  void execute_shouldThrowException_whenCleanupAlsoFails() throws Exception {
    // given
    String malformedResponse = "This is not JSON at all!";

    ClassificationRequestData requestData =
        createRequestData(List.of(docType("invoice"), docType("receipt")));
    ClassificationRequest request = new ClassificationRequest(null, aiProvider, requestData);

    ChatResponse initialResponse = createChatResponse(malformedResponse);
    ChatResponse cleanupResponse = createChatResponse("Still not valid JSON!");

    setupMultimodalMocks();
    when(aiClient.chat(anyString(), anyString(), any(Document.class))).thenReturn(initialResponse);
    when(aiClient.chat(anyString(), anyString())).thenReturn(cleanupResponse);

    // when & then
    assertThatThrownBy(() -> classificationService.execute(request))
        .isInstanceOf(ConnectorException.class)
        .hasFieldOrPropertyWithValue("errorCode", JSON_PARSING_FAILED)
        .hasMessageContaining("Failed to parse JSON even after cleanup attempt");
  }

  @Test
  void execute_shouldHandleMissingOptionalFields() throws Exception {
    // given - response with only extractedValue
    String jsonResponse =
        """
        {
          "extractedValue": "invoice"
        }
        """;

    ClassificationRequestData requestData =
        createRequestData(List.of(docType("invoice"), docType("receipt")));
    ClassificationRequest request = new ClassificationRequest(null, aiProvider, requestData);

    ChatResponse chatResponse = createChatResponse(jsonResponse);

    setupMultimodalMocks();
    when(aiClient.chat(anyString(), anyString(), any(Document.class))).thenReturn(chatResponse);

    // when
    ClassificationResult result = classificationService.execute(request);

    // then
    assertThat(result.extractedValue()).isEqualTo("invoice");
    assertThat(result.confidence()).isNull();
    assertThat(result.reasoning()).isNull();
  }

  @Test
  void execute_shouldReturnFallbackOutputValue_whenLowConfidence() throws Exception {
    // given
    String jsonResponse =
        """
        {
          "extractedValue": "unknown",
          "confidence": "LOW",
          "reasoning": "Document does not match any provided types with high confidence"
        }
        """;

    ClassificationRequestData requestData =
        createRequestData(List.of(docType("invoice"), docType("receipt")), "fallback_value");
    ClassificationRequest request = new ClassificationRequest(null, aiProvider, requestData);

    ChatResponse chatResponse = createChatResponse(jsonResponse);

    setupMultimodalMocks();
    when(aiClient.chat(anyString(), anyString(), any(Document.class))).thenReturn(chatResponse);

    // when
    ClassificationResult result = classificationService.execute(request);

    // then
    assertThat(result.extractedValue()).isEqualTo("unknown");
    assertThat(result.confidence()).isEqualTo("LOW");
  }

  @Test
  void execute_shouldSucceed_whenResponseHasBothThinkingTagsAndMarkdown() throws Exception {
    // given
    String jsonResponse =
        """
        <thinking>Analyzing the document structure...</thinking>
        ```json
        {
          "extractedValue": "invoice",
          "confidence": "HIGH",
          "reasoning": "Multiple indicators present"
        }
        ```
        """;

    ClassificationRequestData requestData =
        createRequestData(List.of(docType("invoice"), docType("receipt")));
    ClassificationRequest request = new ClassificationRequest(null, aiProvider, requestData);

    ChatResponse chatResponse = createChatResponse(jsonResponse);

    setupMultimodalMocks();
    when(aiClient.chat(anyString(), anyString(), any(Document.class))).thenReturn(chatResponse);

    // when
    ClassificationResult result = classificationService.execute(request);

    // then
    assertThat(result.extractedValue()).isEqualTo("invoice");
    assertThat(result.confidence()).isEqualTo("HIGH");
    assertThat(result.reasoning()).isEqualTo("Multiple indicators present");
  }

  @Test
  void execute_shouldIncludeDocumentTypeFieldsInPrompt_whenFieldsArePopulated() throws Exception {
    // given
    String jsonResponse =
        """
        {
          "extractedValue": "inv_output",
          "confidence": "HIGH",
          "reasoning": "Matched invoice based on instructions and description"
        }
        """;

    DocumentType invoiceType =
        new DocumentType(
            "invoice",
            "Look for invoice number and line items",
            "A commercial document issued by a seller",
            "inv_output");
    DocumentType receiptType =
        new DocumentType(
            "receipt", "Look for payment confirmation", "Proof of payment", "rec_output");

    ClassificationRequestData requestData = createRequestData(List.of(invoiceType, receiptType));
    ClassificationRequest request = new ClassificationRequest(null, aiProvider, requestData);

    ChatResponse chatResponse = createChatResponse(jsonResponse);

    setupMultimodalMocks();
    when(aiClient.chat(anyString(), anyString(), any(Document.class))).thenReturn(chatResponse);

    // when
    ClassificationResult result = classificationService.execute(request);

    // then
    assertThat(result.extractedValue()).isEqualTo("inv_output");
    assertThat(result.confidence()).isEqualTo("HIGH");
    assertThat(result.reasoning())
        .isEqualTo("Matched invoice based on instructions and description");
  }

  @Test
  void execute_shouldPopulateMetadataWithTokenUsageAndLatency() throws Exception {
    // given
    String jsonResponse =
        """
        {
          "extractedValue": "invoice",
          "confidence": "HIGH",
          "reasoning": "Invoice detected"
        }
        """;

    ClassificationRequestData requestData =
        createRequestData(List.of(docType("invoice"), docType("receipt")));
    ClassificationRequest request = new ClassificationRequest(null, aiProvider, requestData);

    ChatResponse chatResponse = createChatResponse(jsonResponse);

    setupMultimodalMocks();
    when(aiClient.chat(anyString(), anyString(), any(Document.class))).thenReturn(chatResponse);

    // when
    ClassificationResult result = classificationService.execute(request);

    // then
    assertThat(result.metadata()).isNotNull();
    // TokenUsage is 100 input + 50 output = 150 total
    assertThat(result.metadata().tokenUsage()).isEqualTo(150);
    assertThat(result.metadata().latencyMs()).isGreaterThanOrEqualTo(0);
  }

  @Test
  void execute_shouldUseFallbackOutputValueInSystemPrompt() throws Exception {
    // given
    String jsonResponse =
        """
        {
          "extractedValue": "other_document",
          "confidence": "LOW",
          "reasoning": "No matching document type found"
        }
        """;

    ClassificationRequestData requestData =
        createRequestData(List.of(docType("invoice"), docType("receipt")), "other_document");
    ClassificationRequest request = new ClassificationRequest(null, aiProvider, requestData);

    ChatResponse chatResponse = createChatResponse(jsonResponse);

    setupMultimodalMocks();
    when(aiClient.chat(anyString(), anyString(), any(Document.class))).thenReturn(chatResponse);

    // when
    ClassificationResult result = classificationService.execute(request);

    // then
    assertThat(result.extractedValue()).isEqualTo("other_document");
    assertThat(result.confidence()).isEqualTo("LOW");
    assertThat(result.reasoning()).isEqualTo("No matching document type found");
    assertThat(result.metadata()).isNotNull();
    assertThat(result.metadata().tokenUsage()).isEqualTo(150);
  }

  // Helper methods

  private void setupMultimodalMocks() {
    providerUtilMock
        .when(() -> ProviderUtil.getTextExtractor((ExtractionProvider) null))
        .thenReturn(null);
    providerUtilMock
        .when(() -> ProviderUtil.getAiClient(eq(aiProvider), any(ConverseData.class)))
        .thenReturn(aiClient);
  }

  private ClassificationRequestData createRequestData(List<DocumentType> documentTypes) {
    return createRequestData(documentTypes, null);
  }

  private ClassificationRequestData createRequestData(
      List<DocumentType> documentTypes, String fallbackOutputValue) {
    ClassificationRequestData requestData = mock(ClassificationRequestData.class);
    ConverseData converseData = mock(ConverseData.class);

    when(requestData.getConverseData()).thenReturn(converseData);
    when(requestData.getDocumentTypes()).thenReturn(documentTypes);
    when(requestData.getDocument()).thenReturn(document);
    when(requestData.getFallbackOutputValue()).thenReturn(fallbackOutputValue);

    return requestData;
  }

  private static DocumentType docType(String name) {
    return new DocumentType(name, null, null, null);
  }

  private ChatResponse createChatResponse(String responseText) {
    AiMessage aiMessage = AiMessage.from(responseText);
    TokenUsage tokenUsage = new TokenUsage(100, 50);
    return ChatResponse.builder().aiMessage(aiMessage).tokenUsage(tokenUsage).build();
  }
}
