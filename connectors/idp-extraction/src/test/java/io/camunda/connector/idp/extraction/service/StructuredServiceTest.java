/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.idp.extraction.caller.DocumentAiCaller;
import io.camunda.connector.idp.extraction.caller.PollingTextractCaller;
import io.camunda.connector.idp.extraction.model.*;
import io.camunda.connector.idp.extraction.model.providers.*;
import io.camunda.connector.idp.extraction.model.providers.AwsProvider;
import io.camunda.connector.idp.extraction.model.providers.gcp.DocumentAiRequestConfiguration;
import io.camunda.connector.idp.extraction.model.providers.gcp.GcpAuthentication;
import io.camunda.connector.idp.extraction.model.providers.gcp.GcpAuthenticationType;
import io.camunda.connector.idp.extraction.supplier.S3ClientSupplier;
import io.camunda.connector.idp.extraction.supplier.TextractClientSupplier;
import io.camunda.connector.idp.extraction.util.ExtractionTestUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class StructuredServiceTest {

  @Mock private PollingTextractCaller pollingTextractCaller;
  @Mock private DocumentAiCaller documentAiCaller;
  @Mock private TextractClientSupplier textractClientSupplier;
  @Mock private S3ClientSupplier s3ClientSupplier;

  private StructuredService structuredService;

  @BeforeEach
  void setUp() {
    // Initialize service with mocked dependencies
    structuredService =
        new StructuredService(
            pollingTextractCaller, documentAiCaller, textractClientSupplier, s3ClientSupplier);
  }

  @Test
  void extractUsingTextract_ReturnsCorrectResult() throws Exception {
    // given
    var request = prepareExtractionRequest();

    StructuredExtractionResponse extractionResponse = getStructuredExtractionResponse();

    when(pollingTextractCaller.extractKeyValuePairsWithConfidence(any(), any(), any(), any()))
        .thenReturn(extractionResponse);

    // when
    var result = structuredService.extract(request);

    // then
    assertThat(result).isNotNull().isInstanceOf(StructuredExtractionResult.class);

    StructuredExtractionResult structuredResult = (StructuredExtractionResult) result;

    // Verify the extracted fields were properly processed
    assertThat(structuredResult.extractedFields())
        .containsEntry("invoice_number", "INV-12345")
        .containsEntry("total_amount", "$12.25")
        .containsEntry("supplier_name", "Camunda Inc.");

    // Verify confidence scores were properly processed
    assertThat(structuredResult.confidenceScore())
        .containsEntry("invoice_number", 0.95f)
        .containsEntry("total_amount", 0.98f)
        .containsEntry("supplier_name", 0.92f);

    // Verify original keys were properly mapped
    assertThat(structuredResult.originalKeys())
        .containsEntry("invoice_number", "Invoice Number")
        .containsEntry("total_amount", "Total Amount")
        .containsEntry("supplier_name", "Supplier Name");
  }

  @Test
  void extractUsingTextract_WithIncludedFields_ReturnsOnlyIncludedFields() throws Exception {
    // given
    AwsProvider baseRequest = ExtractionTestUtils.createDefaultAwsProvider();

    ExtractionRequestData requestDataWithInclusions =
        new ExtractionRequestData(
            ExtractionTestUtils.TEXTRACT_EXTRACTION_REQUEST_DATA.document(),
            ExtractionType.STRUCTURED,
            ExtractionTestUtils.TEXTRACT_EXTRACTION_REQUEST_DATA.taxonomyItems(),
            List.of("Invoice Number", "Supplier Name"), // Only include these fields
            null, // No rename mappings
            "_", // Set a delimiter
            ExtractionTestUtils.TEXTRACT_EXTRACTION_REQUEST_DATA.converseData());

    ExtractionRequest request = new ExtractionRequest(requestDataWithInclusions, baseRequest);

    StructuredExtractionResponse extractionResponse = getStructuredExtractionResponse();

    when(pollingTextractCaller.extractKeyValuePairsWithConfidence(any(), any(), any(), any()))
        .thenReturn(extractionResponse);

    // when
    var result = structuredService.extract(request);

    // then
    assertThat(result).isNotNull().isInstanceOf(StructuredExtractionResult.class);

    StructuredExtractionResult structuredResult = (StructuredExtractionResult) result;

    assertThat(structuredResult.extractedFields())
        .containsEntry("invoice_number", "INV-12345")
        .containsEntry("supplier_name", "Camunda Inc.")
        .doesNotContainKey("total_amount");

    assertThat(structuredResult.confidenceScore())
        .containsEntry("invoice_number", 0.95f)
        .containsEntry("supplier_name", 0.92f)
        .doesNotContainKey("total_amount");

    // Verify original keys were properly mapped (excluding non-included fields)
    assertThat(structuredResult.originalKeys())
        .containsEntry("invoice_number", "Invoice Number")
        .containsEntry("supplier_name", "Supplier Name")
        .doesNotContainKey("total_amount");
  }

  @Test
  void extractUsingTextract_ShouldThrowConnectorException_whenExtractionFails() throws Exception {
    // given
    var request = prepareExtractionRequest();

    // Mock the textract caller to throw an exception
    when(pollingTextractCaller.extractKeyValuePairsWithConfidence(any(), any(), any(), any()))
        .thenThrow(new RuntimeException("Extraction failed"));

    // when & then
    assertThatThrownBy(() -> structuredService.extract(request))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("Extraction failed");
  }

  @Test
  void formatZeebeVariableName_ShouldFormatCorrectly() {
    // Test various input scenarios
    assertThat(StructuredService.formatZeebeVariableName("Invoice Number", "_"))
        .isEqualTo("invoice_number");

    assertThat(StructuredService.formatZeebeVariableName("Total Amount $", "_"))
        .isEqualTo("total_amount");

    assertThat(StructuredService.formatZeebeVariableName("Special@Characters#Here", "-"))
        .isEqualTo("specialcharactershere");

    assertThat(StructuredService.formatZeebeVariableName(null, "_")).isNull();

    assertThat(StructuredService.formatZeebeVariableName("  Trim  Spaces  ", "_"))
        .isEqualTo("trim_spaces");
  }

  @Test
  void extractUsingGcp_ReturnsCorrectResult() {
    // given
    GcpProvider gcpProvider = new GcpProvider();
    DocumentAiRequestConfiguration configuration =
        new DocumentAiRequestConfiguration("us-central1", "test-project", "test-processor-id");
    gcpProvider.setConfiguration(configuration);

    GcpAuthentication authentication =
        new GcpAuthentication(GcpAuthenticationType.BEARER, "test-token", null, null, null, null);
    gcpProvider.setAuthentication(authentication);

    ExtractionRequest request =
        new ExtractionRequest(ExtractionTestUtils.TEXTRACT_EXTRACTION_REQUEST_DATA, gcpProvider);

    StructuredExtractionResponse extractionResponse = getStructuredExtractionResponse();

    // Mock the Document AI caller to return our sample response
    when(documentAiCaller.extractKeyValuePairsWithConfidence(any(), any(GcpProvider.class)))
        .thenReturn(extractionResponse);

    // when
    var result = structuredService.extract(request);

    // then
    assertThat(result).isNotNull().isInstanceOf(StructuredExtractionResult.class);

    StructuredExtractionResult structuredResult = (StructuredExtractionResult) result;

    // Verify the extracted fields were properly processed
    assertThat(structuredResult.extractedFields())
        .containsEntry("invoice_number", "INV-12345")
        .containsEntry("total_amount", "$12.25")
        .containsEntry("supplier_name", "Camunda Inc.");

    // Verify confidence scores were properly processed
    assertThat(structuredResult.confidenceScore())
        .containsEntry("invoice_number", 0.95f)
        .containsEntry("total_amount", 0.98f)
        .containsEntry("supplier_name", 0.92f);

    // Verify original keys were properly mapped
    assertThat(structuredResult.originalKeys())
        .containsEntry("invoice_number", "Invoice Number")
        .containsEntry("total_amount", "Total Amount")
        .containsEntry("supplier_name", "Supplier Name");
  }

  @Test
  void extractUsingGcp_WithIncludedFields_ReturnsOnlyIncludedFields() {
    GcpProvider gcpProvider = new GcpProvider();
    DocumentAiRequestConfiguration configuration =
        new DocumentAiRequestConfiguration("us-central1", "test-project", "test-processor-id");
    gcpProvider.setConfiguration(configuration);

    GcpAuthentication authentication =
        new GcpAuthentication(GcpAuthenticationType.BEARER, "test-token", null, null, null, null);
    gcpProvider.setAuthentication(authentication);

    // Create a custom extraction request with included fields
    ExtractionRequestData requestDataWithInclusions =
        new ExtractionRequestData(
            ExtractionTestUtils.TEXTRACT_EXTRACTION_REQUEST_DATA.document(),
            ExtractionType.STRUCTURED,
            ExtractionTestUtils.TEXTRACT_EXTRACTION_REQUEST_DATA.taxonomyItems(),
            List.of("Invoice Number", "Supplier Name"), // Only include these fields
            null, // No rename mappings
            "_", // Set a delimiter
            ExtractionTestUtils.TEXTRACT_EXTRACTION_REQUEST_DATA.converseData());

    ExtractionRequest request = new ExtractionRequest(requestDataWithInclusions, gcpProvider);

    // Create the extraction response with all fields (but only included ones will be processed)
    StructuredExtractionResponse extractionResponse = getStructuredExtractionResponse();

    // Mock the Document AI caller to return our sample response
    when(documentAiCaller.extractKeyValuePairsWithConfidence(any(), any(GcpProvider.class)))
        .thenReturn(extractionResponse);

    // when
    var result = structuredService.extract(request);

    // then
    assertThat(result).isNotNull().isInstanceOf(StructuredExtractionResult.class);

    StructuredExtractionResult structuredResult = (StructuredExtractionResult) result;

    // Verify the extracted fields were properly processed
    assertThat(structuredResult.extractedFields())
        .containsEntry("invoice_number", "INV-12345")
        .containsEntry("supplier_name", "Camunda Inc.")
        .doesNotContainKey("total_amount");

    // Verify confidence scores were properly processed
    assertThat(structuredResult.confidenceScore())
        .containsEntry("invoice_number", 0.95f)
        .containsEntry("supplier_name", 0.92f)
        .doesNotContainKey("total_amount");

    // Verify original keys were properly mapped (excluding non-included fields)
    assertThat(structuredResult.originalKeys())
        .containsEntry("invoice_number", "Invoice Number")
        .containsEntry("supplier_name", "Supplier Name")
        .doesNotContainKey("total_amount");
  }

  @Test
  void extractUsingGcp_ShouldThrowConnectorException_whenExtractionFails() {
    // given
    GcpProvider gcpProvider = new GcpProvider();
    DocumentAiRequestConfiguration configuration =
        new DocumentAiRequestConfiguration("us-central1", "test-project", "test-processor-id");
    gcpProvider.setConfiguration(configuration);

    GcpAuthentication authentication =
        new GcpAuthentication(GcpAuthenticationType.BEARER, "test-token", null, null, null, null);
    gcpProvider.setAuthentication(authentication);

    ExtractionRequest request =
        new ExtractionRequest(ExtractionTestUtils.TEXTRACT_EXTRACTION_REQUEST_DATA, gcpProvider);

    // Mock the Document AI caller to throw an exception
    when(documentAiCaller.extractKeyValuePairsWithConfidence(any(), any(GcpProvider.class)))
        .thenThrow(new RuntimeException("Document AI extraction failed"));

    // when & then
    assertThatThrownBy(() -> structuredService.extract(request))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("Document AI extraction failed");
  }

  @Test
  void extractUsingTextract_WithRenameMappings_ReturnsCorrectResultWithCustomFieldNames()
      throws Exception {
    // given
    AwsProvider baseRequest = ExtractionTestUtils.createDefaultAwsProvider();

    Map<String, String> renameMappings = new HashMap<>();
    renameMappings.put("Invoice Number", "customInvoiceField");
    renameMappings.put("Total Amount", "customTotalField");

    ExtractionRequestData requestDataWithRenameMappings =
        new ExtractionRequestData(
            ExtractionTestUtils.TEXTRACT_EXTRACTION_REQUEST_DATA.document(),
            ExtractionType.STRUCTURED,
            ExtractionTestUtils.TEXTRACT_EXTRACTION_REQUEST_DATA.taxonomyItems(),
            null,
            renameMappings, // Apply our custom rename mappings
            "_", // Set a delimiter
            ExtractionTestUtils.TEXTRACT_EXTRACTION_REQUEST_DATA.converseData());

    ExtractionRequest request = new ExtractionRequest(requestDataWithRenameMappings, baseRequest);

    StructuredExtractionResponse extractionResponse = getStructuredExtractionResponse();

    when(pollingTextractCaller.extractKeyValuePairsWithConfidence(any(), any(), any(), any()))
        .thenReturn(extractionResponse);

    // when
    var result = structuredService.extract(request);

    // then
    assertThat(result).isNotNull().isInstanceOf(StructuredExtractionResult.class);

    StructuredExtractionResult structuredResult = (StructuredExtractionResult) result;

    // Verify the renamed fields are properly processed with custom names
    assertThat(structuredResult.extractedFields())
        .containsEntry("customInvoiceField", "INV-12345")
        .containsEntry("customTotalField", "$12.25")
        .containsEntry("supplier_name", "Camunda Inc."); // Not renamed, should use formatted name

    // Verify confidence scores were properly processed with the same custom names
    assertThat(structuredResult.confidenceScore())
        .containsEntry("customInvoiceField", 0.95f)
        .containsEntry("customTotalField", 0.98f)
        .containsEntry("supplier_name", 0.92f); // Not renamed, should use formatted name
  }

  private static StructuredExtractionResponse getStructuredExtractionResponse() {
    Map<String, Object> extractedFields = new HashMap<>();
    extractedFields.put("Invoice Number", "INV-12345");
    extractedFields.put("Total Amount", "$12.25");
    extractedFields.put("Supplier Name", "Camunda Inc.");

    Map<String, Object> confidenceScores = new HashMap<>();
    confidenceScores.put("Invoice Number", 0.95f);
    confidenceScores.put("Total Amount", 0.98f);
    confidenceScores.put("Supplier Name", 0.92f);

    Map<String, Polygon> geometry = new HashMap<>();

    return new StructuredExtractionResponse(extractedFields, confidenceScores, geometry);
  }

  private ExtractionRequest prepareExtractionRequest() {
    AwsProvider baseRequest = ExtractionTestUtils.createDefaultAwsProvider();
    return new ExtractionRequest(ExtractionTestUtils.TEXTRACT_EXTRACTION_REQUEST_DATA, baseRequest);
  }
}
