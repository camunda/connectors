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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.idp.extraction.client.ai.base.AiClient;
import io.camunda.connector.idp.extraction.client.extraction.base.TextExtractor;
import io.camunda.connector.idp.extraction.model.ExtractionResult;
import io.camunda.connector.idp.extraction.model.TaxonomyItem;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UnstructuredServiceTest {

  private UnstructuredService unstructuredService;

  @Mock private AiClient aiClient;

  @Mock private TextExtractor textExtractor;

  @Mock private Document document;

  @BeforeEach
  void setUp() {
    unstructuredService = new UnstructuredService();
  }

  @Test
  void extract_shouldSucceed_whenMultimodalPathWithValidJsonResponse() throws Exception {
    // given
    String jsonResponse =
        """
        {
          "invoiceNumber": "INV-12345",
          "totalAmount": "1000.00",
          "vendorName": "Acme Corp"
        }
        """;

    List<TaxonomyItem> taxonomyItems =
        List.of(
            new TaxonomyItem("invoiceNumber", "Extract the invoice number"),
            new TaxonomyItem("totalAmount", "Extract the total amount"),
            new TaxonomyItem("vendorName", "Extract the vendor name"));

    ChatResponse chatResponse = createChatResponse(jsonResponse);

    when(aiClient.chat(anyString(), anyString(), any(Document.class))).thenReturn(chatResponse);

    // when
    Object result = unstructuredService.extract(null, aiClient, taxonomyItems, document);

    // then
    assertThat(result).isInstanceOf(ExtractionResult.class);
    ExtractionResult extractionResult = (ExtractionResult) result;

    assertThat(extractionResult.extractedFields()).hasSize(3);
    assertThat(extractionResult.extractedFields().get("invoiceNumber").toString())
        .contains("INV-12345");
    assertThat(extractionResult.extractedFields().get("totalAmount").toString())
        .contains("1000.00");
    assertThat(extractionResult.extractedFields().get("vendorName").toString())
        .contains("Acme Corp");
    assertThat(extractionResult.metadata()).isNotNull();

    verify(aiClient).chat(anyString(), anyString(), eq(document));
  }

  @Test
  void extract_shouldSucceed_whenTextExtractionPathWithValidJsonResponse() throws Exception {
    // given
    String extractedText = "Invoice #INV-12345\nTotal: $1000.00\nVendor: Acme Corp";
    String jsonResponse =
        """
        {
          "invoiceNumber": "INV-12345",
          "totalAmount": "1000.00",
          "vendorName": "Acme Corp"
        }
        """;

    List<TaxonomyItem> taxonomyItems =
        List.of(
            new TaxonomyItem("invoiceNumber", "Extract the invoice number"),
            new TaxonomyItem("totalAmount", "Extract the total amount"),
            new TaxonomyItem("vendorName", "Extract the vendor name"));

    ChatResponse chatResponse = createChatResponse(jsonResponse);

    when(textExtractor.extract(document)).thenReturn(extractedText);
    when(aiClient.chat(anyString(), anyString())).thenReturn(chatResponse);

    // when
    Object result = unstructuredService.extract(textExtractor, aiClient, taxonomyItems, document);

    // then
    assertThat(result).isInstanceOf(ExtractionResult.class);
    ExtractionResult extractionResult = (ExtractionResult) result;

    assertThat(extractionResult.extractedFields()).hasSize(3);
    assertThat(extractionResult.extractedFields().get("invoiceNumber").toString())
        .contains("INV-12345");
    assertThat(extractionResult.extractedFields().get("totalAmount").toString())
        .contains("1000.00");
    assertThat(extractionResult.extractedFields().get("vendorName").toString())
        .contains("Acme Corp");

    verify(textExtractor).extract(document);
    verify(aiClient).chat(anyString(), anyString());
  }

  @Test
  void extract_shouldSucceed_whenResponseWrappedInMarkdownCodeBlock() throws Exception {
    // given
    String jsonResponse =
        """
        ```json
        {
          "invoiceNumber": "INV-12345",
          "totalAmount": "1000.00"
        }
        ```
        """;

    List<TaxonomyItem> taxonomyItems =
        List.of(
            new TaxonomyItem("invoiceNumber", "Extract the invoice number"),
            new TaxonomyItem("totalAmount", "Extract the total amount"));

    ChatResponse chatResponse = createChatResponse(jsonResponse);

    when(aiClient.chat(anyString(), anyString(), any(Document.class))).thenReturn(chatResponse);

    // when
    Object result = unstructuredService.extract(null, aiClient, taxonomyItems, document);

    // then
    ExtractionResult extractionResult = (ExtractionResult) result;

    assertThat(extractionResult.extractedFields()).hasSize(2);
    assertThat(extractionResult.extractedFields().get("invoiceNumber").toString())
        .contains("INV-12345");
    assertThat(extractionResult.extractedFields().get("totalAmount").toString())
        .contains("1000.00");
  }

  @Test
  void extract_shouldSucceed_whenResponseContainsThinkingTags() throws Exception {
    // given
    String jsonResponse =
        """
        <thinking>Let me analyze this document...</thinking>
        {
          "invoiceNumber": "INV-12345",
          "totalAmount": "1000.00"
        }
        """;

    List<TaxonomyItem> taxonomyItems =
        List.of(
            new TaxonomyItem("invoiceNumber", "Extract the invoice number"),
            new TaxonomyItem("totalAmount", "Extract the total amount"));

    ChatResponse chatResponse = createChatResponse(jsonResponse);

    when(aiClient.chat(anyString(), anyString(), any(Document.class))).thenReturn(chatResponse);

    // when
    Object result = unstructuredService.extract(null, aiClient, taxonomyItems, document);

    // then
    ExtractionResult extractionResult = (ExtractionResult) result;

    assertThat(extractionResult.extractedFields()).hasSize(2);
    assertThat(extractionResult.extractedFields().get("invoiceNumber").toString())
        .contains("INV-12345");
    assertThat(extractionResult.extractedFields().get("totalAmount").toString())
        .contains("1000.00");
  }

  @Test
  void extract_shouldSucceed_whenResponseHasNestedResponseWrapper() throws Exception {
    // given
    String jsonResponse =
        """
        {
          "response": {
            "invoiceNumber": "INV-12345",
            "totalAmount": "1000.00"
          }
        }
        """;

    List<TaxonomyItem> taxonomyItems =
        List.of(
            new TaxonomyItem("invoiceNumber", "Extract the invoice number"),
            new TaxonomyItem("totalAmount", "Extract the total amount"));

    ChatResponse chatResponse = createChatResponse(jsonResponse);

    when(aiClient.chat(anyString(), anyString(), any(Document.class))).thenReturn(chatResponse);

    // when
    Object result = unstructuredService.extract(null, aiClient, taxonomyItems, document);

    // then
    ExtractionResult extractionResult = (ExtractionResult) result;

    assertThat(extractionResult.extractedFields()).hasSize(2);
    assertThat(extractionResult.extractedFields().get("invoiceNumber").toString())
        .contains("INV-12345");
    assertThat(extractionResult.extractedFields().get("totalAmount").toString())
        .contains("1000.00");
  }

  @Test
  void extract_shouldSucceed_whenResponseHasNestedResponseWrapperAsString() throws Exception {
    // given
    String nestedJson =
        """
        {
          "invoiceNumber": "INV-12345",
          "totalAmount": "1000.00"
        }
        """;
    String jsonResponse = "{\"response\": " + "\"" + nestedJson.replace("\"", "\\\"") + "\"}";
    String cleanedResponse = nestedJson;

    List<TaxonomyItem> taxonomyItems =
        List.of(
            new TaxonomyItem("invoiceNumber", "Extract the invoice number"),
            new TaxonomyItem("totalAmount", "Extract the total amount"));

    ChatResponse initialResponse = createChatResponse(jsonResponse);
    ChatResponse cleanupResponse = createChatResponse(cleanedResponse);

    when(aiClient.chat(anyString(), anyString(), any(Document.class))).thenReturn(initialResponse);
    when(aiClient.chat(anyString(), anyString())).thenReturn(cleanupResponse);

    // when
    Object result = unstructuredService.extract(null, aiClient, taxonomyItems, document);

    // then
    ExtractionResult extractionResult = (ExtractionResult) result;

    assertThat(extractionResult.extractedFields()).hasSize(2);
    assertThat(extractionResult.extractedFields().get("invoiceNumber").toString())
        .contains("INV-12345");
    assertThat(extractionResult.extractedFields().get("totalAmount").toString())
        .contains("1000.00");

    // Verify initial call with document
    verify(aiClient).chat(anyString(), anyString(), any(Document.class));
    // Verify cleanup call without document
    verify(aiClient).chat(anyString(), anyString());
  }

  @Test
  void extract_shouldAttemptCleanup_whenInitialParsingFails() throws Exception {
    // given
    String malformedResponse = "Here's the data: {\"invoiceNumber\": \"INV-12345\"";
    String cleanedResponse =
        """
        {
          "invoiceNumber": "INV-12345",
          "totalAmount": "1000.00"
        }
        """;

    List<TaxonomyItem> taxonomyItems =
        List.of(
            new TaxonomyItem("invoiceNumber", "Extract the invoice number"),
            new TaxonomyItem("totalAmount", "Extract the total amount"));

    ChatResponse initialResponse = createChatResponse(malformedResponse);
    ChatResponse cleanupResponse = createChatResponse(cleanedResponse);

    when(aiClient.chat(anyString(), anyString(), any(Document.class))).thenReturn(initialResponse);
    when(aiClient.chat(anyString(), anyString())).thenReturn(cleanupResponse);

    // when
    Object result = unstructuredService.extract(null, aiClient, taxonomyItems, document);

    // then
    ExtractionResult extractionResult = (ExtractionResult) result;

    assertThat(extractionResult.extractedFields()).hasSize(2);
    assertThat(extractionResult.extractedFields().get("invoiceNumber").toString())
        .contains("INV-12345");
    assertThat(extractionResult.extractedFields().get("totalAmount").toString())
        .contains("1000.00");

    // Verify initial call with document
    verify(aiClient).chat(anyString(), anyString(), any(Document.class));
    // Verify cleanup call without document
    verify(aiClient).chat(anyString(), anyString());

    // Verify metadata includes aggregated token usage from both initial and cleanup calls
    // Each ChatResponse has TokenUsage(100, 50) = 150 total tokens
    assertThat(extractionResult.metadata().tokenUsage()).isEqualTo(300);
  }

  @Test
  void extract_shouldNotAggregateCleanupMetadata_whenInitialParsingSucceeds() throws Exception {
    // given
    String jsonResponse =
        """
        {
          "invoiceNumber": "INV-12345"
        }
        """;

    List<TaxonomyItem> taxonomyItems =
        List.of(new TaxonomyItem("invoiceNumber", "Extract the invoice number"));

    ChatResponse chatResponse = createChatResponse(jsonResponse);

    when(aiClient.chat(anyString(), anyString(), any(Document.class))).thenReturn(chatResponse);

    // when
    Object result = unstructuredService.extract(null, aiClient, taxonomyItems, document);

    // then
    ExtractionResult extractionResult = (ExtractionResult) result;

    // Only the initial call's token usage should be included (100 + 50 = 150)
    assertThat(extractionResult.metadata().tokenUsage()).isEqualTo(150);
  }

  @Test
  void extract_shouldThrowException_whenResponseIsNotJsonObject() throws Exception {
    // given
    String jsonResponse = "[]"; // Array instead of object

    List<TaxonomyItem> taxonomyItems =
        List.of(
            new TaxonomyItem("invoiceNumber", "Extract the invoice number"),
            new TaxonomyItem("totalAmount", "Extract the total amount"));

    ChatResponse chatResponse = createChatResponse(jsonResponse);
    ChatResponse cleanupResponse = createChatResponse(jsonResponse); // Cleanup also fails

    when(aiClient.chat(anyString(), anyString(), any(Document.class))).thenReturn(chatResponse);
    when(aiClient.chat(anyString(), anyString())).thenReturn(cleanupResponse);

    // when & then
    assertThatThrownBy(() -> unstructuredService.extract(null, aiClient, taxonomyItems, document))
        .isInstanceOf(ConnectorException.class)
        .hasFieldOrPropertyWithValue("errorCode", JSON_PARSING_FAILED)
        .hasMessageContaining("Failed to parse JSON even after cleanup attempt");
  }

  @Test
  void extract_shouldThrowException_whenCleanupAlsoFails() throws Exception {
    // given
    String malformedResponse = "This is not JSON at all!";

    List<TaxonomyItem> taxonomyItems =
        List.of(
            new TaxonomyItem("invoiceNumber", "Extract the invoice number"),
            new TaxonomyItem("totalAmount", "Extract the total amount"));

    ChatResponse initialResponse = createChatResponse(malformedResponse);
    ChatResponse cleanupResponse = createChatResponse("Still not valid JSON!");

    when(aiClient.chat(anyString(), anyString(), any(Document.class))).thenReturn(initialResponse);
    when(aiClient.chat(anyString(), anyString())).thenReturn(cleanupResponse);

    // when & then
    assertThatThrownBy(() -> unstructuredService.extract(null, aiClient, taxonomyItems, document))
        .isInstanceOf(ConnectorException.class)
        .hasFieldOrPropertyWithValue("errorCode", JSON_PARSING_FAILED)
        .hasMessageContaining("Failed to parse JSON even after cleanup attempt");
  }

  @Test
  void extract_shouldHandleMissingOptionalFields() throws Exception {
    // given - response with only some fields
    String jsonResponse =
        """
        {
          "invoiceNumber": "INV-12345"
        }
        """;

    List<TaxonomyItem> taxonomyItems =
        List.of(
            new TaxonomyItem("invoiceNumber", "Extract the invoice number"),
            new TaxonomyItem("totalAmount", "Extract the total amount"),
            new TaxonomyItem("vendorName", "Extract the vendor name"));

    ChatResponse chatResponse = createChatResponse(jsonResponse);

    when(aiClient.chat(anyString(), anyString(), any(Document.class))).thenReturn(chatResponse);

    // when
    Object result = unstructuredService.extract(null, aiClient, taxonomyItems, document);

    // then
    ExtractionResult extractionResult = (ExtractionResult) result;

    // Only the field present in the response should be included
    assertThat(extractionResult.extractedFields()).hasSize(1);
    assertThat(extractionResult.extractedFields().get("invoiceNumber").toString())
        .contains("INV-12345");
    assertThat(extractionResult.extractedFields()).doesNotContainKey("totalAmount");
    assertThat(extractionResult.extractedFields()).doesNotContainKey("vendorName");
  }

  @Test
  void extract_shouldSucceed_whenResponseHasBothThinkingTagsAndMarkdown() throws Exception {
    // given
    String jsonResponse =
        """
        <thinking>Analyzing the document structure...</thinking>
        ```json
        {
          "invoiceNumber": "INV-12345",
          "totalAmount": "1000.00"
        }
        ```
        """;

    List<TaxonomyItem> taxonomyItems =
        List.of(
            new TaxonomyItem("invoiceNumber", "Extract the invoice number"),
            new TaxonomyItem("totalAmount", "Extract the total amount"));

    ChatResponse chatResponse = createChatResponse(jsonResponse);

    when(aiClient.chat(anyString(), anyString(), any(Document.class))).thenReturn(chatResponse);

    // when
    Object result = unstructuredService.extract(null, aiClient, taxonomyItems, document);

    // then
    ExtractionResult extractionResult = (ExtractionResult) result;

    assertThat(extractionResult.extractedFields()).hasSize(2);
    assertThat(extractionResult.extractedFields().get("invoiceNumber").toString())
        .contains("INV-12345");
    assertThat(extractionResult.extractedFields().get("totalAmount").toString())
        .contains("1000.00");
  }

  @Test
  void extract_shouldHandleSingleTaxonomyItem() throws Exception {
    // given
    String jsonResponse =
        """
        {
          "invoiceNumber": "INV-12345"
        }
        """;

    List<TaxonomyItem> taxonomyItems =
        List.of(new TaxonomyItem("invoiceNumber", "Extract the invoice number"));

    ChatResponse chatResponse = createChatResponse(jsonResponse);

    when(aiClient.chat(anyString(), anyString(), any(Document.class))).thenReturn(chatResponse);

    // when
    Object result = unstructuredService.extract(null, aiClient, taxonomyItems, document);

    // then
    ExtractionResult extractionResult = (ExtractionResult) result;

    assertThat(extractionResult.extractedFields()).hasSize(1);
    assertThat(extractionResult.extractedFields().get("invoiceNumber").toString())
        .contains("INV-12345");
  }

  @Test
  void extract_shouldNotUnwrapResponseKey_whenItIsATaxonomyItem() throws Exception {
    // given - "response" is actually a valid taxonomy item name
    String jsonResponse =
        """
        {
          "response": "This is the response field",
          "invoiceNumber": "INV-12345"
        }
        """;

    List<TaxonomyItem> taxonomyItems =
        List.of(
            new TaxonomyItem("response", "Extract the response"),
            new TaxonomyItem("invoiceNumber", "Extract the invoice number"));

    ChatResponse chatResponse = createChatResponse(jsonResponse);

    when(aiClient.chat(anyString(), anyString(), any(Document.class))).thenReturn(chatResponse);

    // when
    Object result = unstructuredService.extract(null, aiClient, taxonomyItems, document);

    // then
    ExtractionResult extractionResult = (ExtractionResult) result;

    assertThat(extractionResult.extractedFields()).hasSize(2);
    assertThat(extractionResult.extractedFields().get("response").toString())
        .contains("This is the response field");
    assertThat(extractionResult.extractedFields().get("invoiceNumber").toString())
        .contains("INV-12345");
  }

  @Test
  void extract_shouldHandleComplexNestedJson() throws Exception {
    // given
    String jsonResponse =
        """
        {
          "invoiceNumber": "INV-12345",
          "lineItems": [
            {"description": "Item 1", "price": 100},
            {"description": "Item 2", "price": 200}
          ],
          "metadata": {
            "createdDate": "2023-01-01",
            "modifiedDate": "2023-01-02"
          }
        }
        """;

    List<TaxonomyItem> taxonomyItems =
        List.of(
            new TaxonomyItem("invoiceNumber", "Extract the invoice number"),
            new TaxonomyItem("lineItems", "Extract the line items"),
            new TaxonomyItem("metadata", "Extract the metadata"));

    ChatResponse chatResponse = createChatResponse(jsonResponse);

    when(aiClient.chat(anyString(), anyString(), any(Document.class))).thenReturn(chatResponse);

    // when
    Object result = unstructuredService.extract(null, aiClient, taxonomyItems, document);

    // then
    ExtractionResult extractionResult = (ExtractionResult) result;

    assertThat(extractionResult.extractedFields()).hasSize(3);
    assertThat(extractionResult.extractedFields().get("invoiceNumber").toString())
        .contains("INV-12345");
    assertThat(extractionResult.extractedFields().get("lineItems")).isInstanceOf(JsonNode.class);
    assertThat(extractionResult.extractedFields().get("metadata")).isInstanceOf(JsonNode.class);
  }

  @Test
  void extract_shouldHandleEmptyJsonResponse() throws Exception {
    // given
    String jsonResponse = "{}";

    List<TaxonomyItem> taxonomyItems =
        List.of(
            new TaxonomyItem("invoiceNumber", "Extract the invoice number"),
            new TaxonomyItem("totalAmount", "Extract the total amount"));

    ChatResponse chatResponse = createChatResponse(jsonResponse);

    when(aiClient.chat(anyString(), anyString(), any(Document.class))).thenReturn(chatResponse);

    // when
    Object result = unstructuredService.extract(null, aiClient, taxonomyItems, document);

    // then
    ExtractionResult extractionResult = (ExtractionResult) result;

    // No fields should be present
    assertThat(extractionResult.extractedFields()).isEmpty();
  }

  @Test
  void extract_shouldHandleSpecialCharactersInFieldNames() throws Exception {
    // given
    String jsonResponse =
        """
        {
          "invoice_number": "INV-12345",
          "total-amount": "1000.00",
          "vendor.name": "Acme Corp"
        }
        """;

    List<TaxonomyItem> taxonomyItems =
        List.of(
            new TaxonomyItem("invoice_number", "Extract the invoice number"),
            new TaxonomyItem("total-amount", "Extract the total amount"),
            new TaxonomyItem("vendor.name", "Extract the vendor name"));

    ChatResponse chatResponse = createChatResponse(jsonResponse);

    when(aiClient.chat(anyString(), anyString(), any(Document.class))).thenReturn(chatResponse);

    // when
    Object result = unstructuredService.extract(null, aiClient, taxonomyItems, document);

    // then
    ExtractionResult extractionResult = (ExtractionResult) result;

    assertThat(extractionResult.extractedFields()).hasSize(3);
    assertThat(extractionResult.extractedFields().get("invoice_number").toString())
        .contains("INV-12345");
    assertThat(extractionResult.extractedFields().get("total-amount").toString())
        .contains("1000.00");
    assertThat(extractionResult.extractedFields().get("vendor.name").toString())
        .contains("Acme Corp");
  }

  // Helper methods

  private ChatResponse createChatResponse(String responseText) {
    AiMessage aiMessage = AiMessage.from(responseText);
    TokenUsage tokenUsage = new TokenUsage(100, 50);
    return ChatResponse.builder().aiMessage(aiMessage).tokenUsage(tokenUsage).build();
  }
}
