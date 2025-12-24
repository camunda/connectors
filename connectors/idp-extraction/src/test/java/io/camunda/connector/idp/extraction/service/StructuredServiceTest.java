/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.idp.extraction.client.extraction.base.MlExtractor;
import io.camunda.connector.idp.extraction.model.Polygon;
import io.camunda.connector.idp.extraction.model.PolygonPoint;
import io.camunda.connector.idp.extraction.model.StructuredExtractionResponse;
import io.camunda.connector.idp.extraction.model.StructuredExtractionResult;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StructuredServiceTest {

  private StructuredService structuredService;

  @Mock private MlExtractor mlExtractor;

  @Mock private Document document;

  @BeforeEach
  void setUp() {
    structuredService = new StructuredService();
  }

  @Test
  void extract_shouldSucceed_withBasicExtraction() {
    // given
    Map<String, Object> extractedFields = new HashMap<>();
    extractedFields.put("Invoice Number", "INV-12345");
    extractedFields.put("Total Amount", "1000.00");
    extractedFields.put("Date", "2023-01-01");

    Map<String, Object> confidenceScores = new HashMap<>();
    confidenceScores.put("Invoice Number", 0.95);
    confidenceScores.put("Total Amount", 0.88);
    confidenceScores.put("Date", 0.92);

    Map<String, Polygon> geometry = new HashMap<>();
    geometry.put("Invoice Number", createPolygon(1));
    geometry.put("Total Amount", createPolygon(1));
    geometry.put("Date", createPolygon(1));

    StructuredExtractionResponse response =
        new StructuredExtractionResponse(extractedFields, confidenceScores, geometry);

    when(mlExtractor.runDocumentAnalysis(document)).thenReturn(response);

    // when
    Object result = structuredService.extract(mlExtractor, null, null, null, document);

    // then
    assertThat(result).isInstanceOf(StructuredExtractionResult.class);
    StructuredExtractionResult structuredResult = (StructuredExtractionResult) result;

    assertThat(structuredResult.extractedFields()).hasSize(3);
    assertThat(structuredResult.extractedFields().get("invoice_number")).isEqualTo("INV-12345");
    assertThat(structuredResult.extractedFields().get("total_amount")).isEqualTo("1000.00");
    assertThat(structuredResult.extractedFields().get("date")).isEqualTo("2023-01-01");

    assertThat(structuredResult.confidenceScore()).hasSize(3);
    assertThat(structuredResult.confidenceScore().get("invoice_number")).isEqualTo(0.95);
    assertThat(structuredResult.confidenceScore().get("total_amount")).isEqualTo(0.88);
    assertThat(structuredResult.confidenceScore().get("date")).isEqualTo(0.92);

    assertThat(structuredResult.originalKeys()).hasSize(3);
    assertThat(structuredResult.originalKeys().get("invoice_number")).isEqualTo("Invoice Number");
    assertThat(structuredResult.originalKeys().get("total_amount")).isEqualTo("Total Amount");
    assertThat(structuredResult.originalKeys().get("date")).isEqualTo("Date");

    assertThat(structuredResult.geometry()).hasSize(3);

    verify(mlExtractor).runDocumentAnalysis(document);
  }

  @Test
  void extract_shouldSucceed_withIncludedFieldsFilter() {
    // given
    Map<String, Object> extractedFields = new HashMap<>();
    extractedFields.put("Invoice Number", "INV-12345");
    extractedFields.put("Total Amount", "1000.00");
    extractedFields.put("Date", "2023-01-01");
    extractedFields.put("Vendor Name", "Acme Corp");

    Map<String, Object> confidenceScores = new HashMap<>();
    confidenceScores.put("Invoice Number", 0.95);
    confidenceScores.put("Total Amount", 0.88);
    confidenceScores.put("Date", 0.92);
    confidenceScores.put("Vendor Name", 0.90);

    StructuredExtractionResponse response =
        new StructuredExtractionResponse(extractedFields, confidenceScores, new HashMap<>());

    when(mlExtractor.runDocumentAnalysis(document)).thenReturn(response);

    List<String> includedFields = List.of("Invoice Number", "Total Amount");

    // when
    Object result = structuredService.extract(mlExtractor, includedFields, null, null, document);

    // then
    StructuredExtractionResult structuredResult = (StructuredExtractionResult) result;

    assertThat(structuredResult.extractedFields()).hasSize(2);
    assertThat(structuredResult.extractedFields().get("invoice_number")).isEqualTo("INV-12345");
    assertThat(structuredResult.extractedFields().get("total_amount")).isEqualTo("1000.00");
    assertThat(structuredResult.extractedFields()).doesNotContainKey("date");
    assertThat(structuredResult.extractedFields()).doesNotContainKey("vendor_name");

    assertThat(structuredResult.confidenceScore()).hasSize(2);
  }

  @Test
  void extract_shouldSucceed_withRenameMappings() {
    // given
    Map<String, Object> extractedFields = new HashMap<>();
    extractedFields.put("Invoice Number", "INV-12345");
    extractedFields.put("Total Amount", "1000.00");

    Map<String, Object> confidenceScores = new HashMap<>();
    confidenceScores.put("Invoice Number", 0.95);
    confidenceScores.put("Total Amount", 0.88);

    StructuredExtractionResponse response =
        new StructuredExtractionResponse(extractedFields, confidenceScores, new HashMap<>());

    when(mlExtractor.runDocumentAnalysis(document)).thenReturn(response);

    Map<String, String> renameMappings = new HashMap<>();
    renameMappings.put("Invoice Number", "customInvoiceId");
    renameMappings.put("Total Amount", "totalPrice");

    // when
    Object result = structuredService.extract(mlExtractor, null, renameMappings, null, document);

    // then
    StructuredExtractionResult structuredResult = (StructuredExtractionResult) result;

    assertThat(structuredResult.extractedFields()).hasSize(2);
    assertThat(structuredResult.extractedFields().get("customInvoiceId")).isEqualTo("INV-12345");
    assertThat(structuredResult.extractedFields().get("totalPrice")).isEqualTo("1000.00");
    assertThat(structuredResult.extractedFields()).doesNotContainKey("invoice_number");
    assertThat(structuredResult.extractedFields()).doesNotContainKey("total_amount");

    assertThat(structuredResult.originalKeys().get("customInvoiceId")).isEqualTo("Invoice Number");
    assertThat(structuredResult.originalKeys().get("totalPrice")).isEqualTo("Total Amount");
  }

  @Test
  void extract_shouldSucceed_withCustomDelimiter() {
    // given
    Map<String, Object> extractedFields = new HashMap<>();
    extractedFields.put("Invoice Number", "INV-12345");
    extractedFields.put("Total Amount", "1000.00");

    Map<String, Object> confidenceScores = new HashMap<>();
    confidenceScores.put("Invoice Number", 0.95);
    confidenceScores.put("Total Amount", 0.88);

    StructuredExtractionResponse response =
        new StructuredExtractionResponse(extractedFields, confidenceScores, new HashMap<>());

    when(mlExtractor.runDocumentAnalysis(document)).thenReturn(response);

    // when
    Object result = structuredService.extract(mlExtractor, null, null, "-", document);

    // then
    StructuredExtractionResult structuredResult = (StructuredExtractionResult) result;

    assertThat(structuredResult.extractedFields()).hasSize(2);
    assertThat(structuredResult.extractedFields().get("invoice-number")).isEqualTo("INV-12345");
    assertThat(structuredResult.extractedFields().get("total-amount")).isEqualTo("1000.00");
  }

  @Test
  void extract_shouldFilterOutNullValues() {
    // given
    Map<String, Object> extractedFields = new HashMap<>();
    extractedFields.put("Invoice Number", "INV-12345");
    extractedFields.put("Total Amount", null);
    extractedFields.put("Date", "2023-01-01");

    Map<String, Object> confidenceScores = new HashMap<>();
    confidenceScores.put("Invoice Number", 0.95);
    confidenceScores.put("Total Amount", 0.88);
    confidenceScores.put("Date", 0.92);

    StructuredExtractionResponse response =
        new StructuredExtractionResponse(extractedFields, confidenceScores, new HashMap<>());

    when(mlExtractor.runDocumentAnalysis(document)).thenReturn(response);

    // when
    Object result = structuredService.extract(mlExtractor, null, null, null, document);

    // then
    StructuredExtractionResult structuredResult = (StructuredExtractionResult) result;

    assertThat(structuredResult.extractedFields()).hasSize(2);
    assertThat(structuredResult.extractedFields().get("invoice_number")).isEqualTo("INV-12345");
    assertThat(structuredResult.extractedFields().get("date")).isEqualTo("2023-01-01");
    assertThat(structuredResult.extractedFields()).doesNotContainKey("total_amount");

    // Confidence score should also be filtered out when value is null
    assertThat(structuredResult.confidenceScore()).hasSize(2);
    assertThat(structuredResult.confidenceScore()).doesNotContainKey("total_amount");
  }

  @Test
  void extract_shouldHandleMissingConfidenceScores() {
    // given
    Map<String, Object> extractedFields = new HashMap<>();
    extractedFields.put("Invoice Number", "INV-12345");
    extractedFields.put("Total Amount", "1000.00");

    Map<String, Object> confidenceScores = new HashMap<>();
    confidenceScores.put("Invoice Number", 0.95);
    // Missing confidence score for "Total Amount"

    StructuredExtractionResponse response =
        new StructuredExtractionResponse(extractedFields, confidenceScores, new HashMap<>());

    when(mlExtractor.runDocumentAnalysis(document)).thenReturn(response);

    // when
    Object result = structuredService.extract(mlExtractor, null, null, null, document);

    // then
    StructuredExtractionResult structuredResult = (StructuredExtractionResult) result;

    assertThat(structuredResult.extractedFields()).hasSize(2);
    assertThat(structuredResult.extractedFields().get("invoice_number")).isEqualTo("INV-12345");
    assertThat(structuredResult.extractedFields().get("total_amount")).isEqualTo("1000.00");

    assertThat(structuredResult.confidenceScore()).hasSize(1);
    assertThat(structuredResult.confidenceScore().get("invoice_number")).isEqualTo(0.95);
    assertThat(structuredResult.confidenceScore()).doesNotContainKey("total_amount");
  }

  @Test
  void extract_shouldHandleEmptyIncludedFieldsList() {
    // given
    Map<String, Object> extractedFields = new HashMap<>();
    extractedFields.put("Invoice Number", "INV-12345");
    extractedFields.put("Total Amount", "1000.00");

    Map<String, Object> confidenceScores = new HashMap<>();
    confidenceScores.put("Invoice Number", 0.95);
    confidenceScores.put("Total Amount", 0.88);

    StructuredExtractionResponse response =
        new StructuredExtractionResponse(extractedFields, confidenceScores, new HashMap<>());

    when(mlExtractor.runDocumentAnalysis(document)).thenReturn(response);

    List<String> includedFields = List.of();

    // when
    Object result = structuredService.extract(mlExtractor, includedFields, null, null, document);

    // then
    StructuredExtractionResult structuredResult = (StructuredExtractionResult) result;

    // Empty list should include all fields (same as null)
    assertThat(structuredResult.extractedFields()).hasSize(2);
  }

  @Test
  void extract_shouldCombineIncludedFieldsAndRenameMappings() {
    // given
    Map<String, Object> extractedFields = new HashMap<>();
    extractedFields.put("Invoice Number", "INV-12345");
    extractedFields.put("Total Amount", "1000.00");
    extractedFields.put("Date", "2023-01-01");

    Map<String, Object> confidenceScores = new HashMap<>();
    confidenceScores.put("Invoice Number", 0.95);
    confidenceScores.put("Total Amount", 0.88);
    confidenceScores.put("Date", 0.92);

    StructuredExtractionResponse response =
        new StructuredExtractionResponse(extractedFields, confidenceScores, new HashMap<>());

    when(mlExtractor.runDocumentAnalysis(document)).thenReturn(response);

    List<String> includedFields = List.of("Invoice Number", "Total Amount");
    Map<String, String> renameMappings = new HashMap<>();
    renameMappings.put("Invoice Number", "invoiceId");

    // when
    Object result =
        structuredService.extract(mlExtractor, includedFields, renameMappings, null, document);

    // then
    StructuredExtractionResult structuredResult = (StructuredExtractionResult) result;

    assertThat(structuredResult.extractedFields()).hasSize(2);
    assertThat(structuredResult.extractedFields().get("invoiceId")).isEqualTo("INV-12345");
    assertThat(structuredResult.extractedFields().get("total_amount")).isEqualTo("1000.00");
    assertThat(structuredResult.extractedFields()).doesNotContainKey("date");
  }

  @Test
  void extract_shouldHandleComplexFieldNamesWithSpecialCharacters() {
    // given
    Map<String, Object> extractedFields = new HashMap<>();
    extractedFields.put("Invoice#Number!", "INV-12345");
    extractedFields.put("Total$Amount@", "1000.00");
    extractedFields.put("Customer's Name", "John Doe");

    Map<String, Object> confidenceScores = new HashMap<>();
    confidenceScores.put("Invoice#Number!", 0.95);
    confidenceScores.put("Total$Amount@", 0.88);
    confidenceScores.put("Customer's Name", 0.90);

    StructuredExtractionResponse response =
        new StructuredExtractionResponse(extractedFields, confidenceScores, new HashMap<>());

    when(mlExtractor.runDocumentAnalysis(document)).thenReturn(response);

    // when
    Object result = structuredService.extract(mlExtractor, null, null, null, document);

    // then
    StructuredExtractionResult structuredResult = (StructuredExtractionResult) result;

    assertThat(structuredResult.extractedFields()).hasSize(3);
    // Special characters should be removed and spaces converted to underscores
    assertThat(structuredResult.extractedFields().get("invoicenumber")).isEqualTo("INV-12345");
    assertThat(structuredResult.extractedFields().get("totalamount")).isEqualTo("1000.00");
    assertThat(structuredResult.extractedFields().get("customers_name")).isEqualTo("John Doe");
  }

  @Test
  void extract_shouldHandleGeometryData() {
    // given
    Map<String, Object> extractedFields = new HashMap<>();
    extractedFields.put("Invoice Number", "INV-12345");

    Map<String, Object> confidenceScores = new HashMap<>();
    confidenceScores.put("Invoice Number", 0.95);

    Map<String, Polygon> geometry = new HashMap<>();
    Polygon polygon = createPolygon(1);
    geometry.put("Invoice Number", polygon);

    StructuredExtractionResponse response =
        new StructuredExtractionResponse(extractedFields, confidenceScores, geometry);

    when(mlExtractor.runDocumentAnalysis(document)).thenReturn(response);

    // when
    Object result = structuredService.extract(mlExtractor, null, null, null, document);

    // then
    StructuredExtractionResult structuredResult = (StructuredExtractionResult) result;

    assertThat(structuredResult.geometry()).hasSize(1);
    assertThat(structuredResult.geometry().get("invoice_number")).isNotNull();
    assertThat(structuredResult.geometry().get("invoice_number").getPage()).isEqualTo(1);
    assertThat(structuredResult.geometry().get("invoice_number").getPoints()).hasSize(4);
  }

  @Test
  void formatZeebeVariableName_shouldHandleNullInput() {
    // when
    String result = StructuredService.formatZeebeVariableName(null, null);

    // then
    assertThat(result).isNull();
  }

  @Test
  void formatZeebeVariableName_shouldConvertToLowercase() {
    // when
    String result = StructuredService.formatZeebeVariableName("INVOICE NUMBER", null);

    // then
    assertThat(result).isEqualTo("invoice_number");
  }

  @Test
  void formatZeebeVariableName_shouldReplaceSpacesWithDelimiter() {
    // when
    String result = StructuredService.formatZeebeVariableName("Invoice Number Total", "-");

    // then
    assertThat(result).isEqualTo("invoice-number-total");
  }

  @Test
  void formatZeebeVariableName_shouldUseUnderscoreAsDefaultDelimiter() {
    // when
    String result = StructuredService.formatZeebeVariableName("Invoice Number", null);

    // then
    assertThat(result).isEqualTo("invoice_number");
  }

  @Test
  void formatZeebeVariableName_shouldRemoveSpecialCharacters() {
    // when
    String result = StructuredService.formatZeebeVariableName("Invoice#Number!", null);

    // then
    assertThat(result).isEqualTo("invoicenumber");
  }

  @Test
  void formatZeebeVariableName_shouldPreserveUnderscoresAndDashes() {
    // when
    String result = StructuredService.formatZeebeVariableName("Invoice_Number-ID", null);

    // then
    assertThat(result).isEqualTo("invoice_number-id");
  }

  @Test
  void formatZeebeVariableName_shouldTrimWhitespace() {
    // when
    String result = StructuredService.formatZeebeVariableName("  Invoice Number  ", null);

    // then
    assertThat(result).isEqualTo("invoice_number");
  }

  @Test
  void formatZeebeVariableName_shouldHandleMultipleConsecutiveSpaces() {
    // when
    String result = StructuredService.formatZeebeVariableName("Invoice    Number", null);

    // then
    assertThat(result).isEqualTo("invoice_number");
  }

  @Test
  void formatZeebeVariableName_shouldHandleAlphanumericCharacters() {
    // when
    String result = StructuredService.formatZeebeVariableName("Invoice123Number456", null);

    // then
    assertThat(result).isEqualTo("invoice123number456");
  }

  @Test
  void formatZeebeVariableName_shouldHandleComplexCase() {
    // when
    String result =
        StructuredService.formatZeebeVariableName("Customer's Invoice #123 (Total)", "-");

    // then
    assertThat(result).isEqualTo("customers-invoice-123-total");
  }

  // Helper methods

  private Polygon createPolygon(int page) {
    List<PolygonPoint> points =
        List.of(
            new PolygonPoint(0.0f, 0.0f),
            new PolygonPoint(1.0f, 0.0f),
            new PolygonPoint(1.0f, 1.0f),
            new PolygonPoint(0.0f, 1.0f));
    return new Polygon(page, points);
  }
}
