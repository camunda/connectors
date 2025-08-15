/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.caller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.cloud.documentai.v1.BoundingPoly;
import com.google.cloud.documentai.v1.Document;
import com.google.cloud.documentai.v1.DocumentProcessorServiceClient;
import com.google.cloud.documentai.v1.NormalizedVertex;
import com.google.cloud.documentai.v1.ProcessRequest;
import com.google.cloud.documentai.v1.ProcessResponse;
import io.camunda.connector.api.document.DocumentMetadata;
import io.camunda.connector.idp.extraction.model.ExtractionRequestData;
import io.camunda.connector.idp.extraction.model.Polygon;
import io.camunda.connector.idp.extraction.model.PolygonPoint;
import io.camunda.connector.idp.extraction.model.StructuredExtractionResponse;
import io.camunda.connector.idp.extraction.model.providers.GcpProvider;
import io.camunda.connector.idp.extraction.model.providers.gcp.DocumentAiRequestConfiguration;
import io.camunda.connector.idp.extraction.model.providers.gcp.GcpAuthentication;
import io.camunda.connector.idp.extraction.model.providers.gcp.GcpAuthenticationType;
import io.camunda.connector.idp.extraction.supplier.DocumentAiClientSupplier;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocumentAiCallerTest {

  @Test
  void extractKeyValuePairsWithConfidence_SuccessfulExtraction() throws Exception {
    // Arrange
    DocumentAiClientSupplier mockSupplier = mock(DocumentAiClientSupplier.class);
    DocumentProcessorServiceClient mockClient = mock(DocumentProcessorServiceClient.class);
    ProcessResponse mockResponse = mock(ProcessResponse.class);
    Document mockDocument = mock(Document.class);

    when(mockSupplier.getDocumentAiClient(any(GcpAuthentication.class))).thenReturn(mockClient);
    when(mockClient.processDocument((ProcessRequest) any())).thenReturn(mockResponse);
    when(mockResponse.getDocument()).thenReturn(mockDocument);

    DocumentAiCaller caller = new DocumentAiCaller(mockSupplier);

    // Mock the document pages with form fields
    Document.Page mockPage = mock(Document.Page.class);
    when(mockPage.getPageNumber()).thenReturn(1);
    Document.Page.FormField mockFormField = mock(Document.Page.FormField.class);

    // Create mock Layout objects
    Document.Page.Layout mockNameLayout = mock(Document.Page.Layout.class);
    Document.Page.Layout mockValueLayout = mock(Document.Page.Layout.class);

    // Set up text anchors properly for the new implementation
    Document.TextAnchor mockNameAnchor = createTextAnchor(0, 7); // "Invoice"
    Document.TextAnchor mockValueAnchor = createTextAnchor(8, 14); // "Total:"

    when(mockDocument.getText()).thenReturn("Invoice Total:");

    // Set up the form field with field name and value layouts
    when(mockFormField.getFieldName()).thenReturn(mockNameLayout);
    when(mockFormField.getFieldValue()).thenReturn(mockValueLayout);
    when(mockFormField.hasFieldName()).thenReturn(true);
    when(mockFormField.hasFieldValue()).thenReturn(true);
    when(mockFormField.getValueType()).thenReturn(null);

    // Set up text anchors for the layouts
    when(mockNameLayout.getTextAnchor()).thenReturn(mockNameAnchor);
    when(mockValueLayout.getTextAnchor()).thenReturn(mockValueAnchor);

    // Set up confidence values on the layouts
    when(mockNameLayout.getConfidence()).thenReturn(0.95f);
    when(mockValueLayout.getConfidence()).thenReturn(0.85f);

    // Add bounding polygon mocks to fix NullPointerException
    BoundingPoly mockNameBoundingPoly = mock(BoundingPoly.class);
    BoundingPoly mockValueBoundingPoly = mock(BoundingPoly.class);
    NormalizedVertex mockNameVertex1 = mock(NormalizedVertex.class);
    NormalizedVertex mockNameVertex2 = mock(NormalizedVertex.class);
    NormalizedVertex mockNameVertex3 = mock(NormalizedVertex.class);
    NormalizedVertex mockNameVertex4 = mock(NormalizedVertex.class);

    NormalizedVertex mockValueVertex1 = mock(NormalizedVertex.class);
    NormalizedVertex mockValueVertex2 = mock(NormalizedVertex.class);
    NormalizedVertex mockValueVertex3 = mock(NormalizedVertex.class);
    NormalizedVertex mockValueVertex4 = mock(NormalizedVertex.class);

    // Set up name polygon vertices (left side)
    when(mockNameVertex1.getX()).thenReturn(0.1f);
    when(mockNameVertex1.getY()).thenReturn(0.1f);
    when(mockNameVertex2.getX()).thenReturn(0.3f);
    when(mockNameVertex2.getY()).thenReturn(0.1f);
    when(mockNameVertex3.getX()).thenReturn(0.3f);
    when(mockNameVertex3.getY()).thenReturn(0.2f);
    when(mockNameVertex4.getX()).thenReturn(0.1f);
    when(mockNameVertex4.getY()).thenReturn(0.2f);

    // Set up value polygon vertices (right side)
    when(mockValueVertex1.getX()).thenReturn(0.4f);
    when(mockValueVertex1.getY()).thenReturn(0.1f);
    when(mockValueVertex2.getX()).thenReturn(0.6f);
    when(mockValueVertex2.getY()).thenReturn(0.1f);
    when(mockValueVertex3.getX()).thenReturn(0.6f);
    when(mockValueVertex3.getY()).thenReturn(0.2f);
    when(mockValueVertex4.getX()).thenReturn(0.4f);
    when(mockValueVertex4.getY()).thenReturn(0.2f);

    when(mockNameBoundingPoly.getNormalizedVerticesList())
        .thenReturn(List.of(mockNameVertex1, mockNameVertex2, mockNameVertex3, mockNameVertex4));
    when(mockValueBoundingPoly.getNormalizedVerticesList())
        .thenReturn(
            List.of(mockValueVertex1, mockValueVertex2, mockValueVertex3, mockValueVertex4));
    when(mockNameLayout.getBoundingPoly()).thenReturn(mockNameBoundingPoly);
    when(mockValueLayout.getBoundingPoly()).thenReturn(mockValueBoundingPoly);

    when(mockPage.getFormFieldsList()).thenReturn(List.of(mockFormField));
    when(mockPage.getTablesList()).thenReturn(List.of());
    when(mockDocument.getPagesList()).thenReturn(List.of(mockPage));

    // Create and configure the GcpProvider
    GcpProvider baseRequest = new GcpProvider();
    DocumentAiRequestConfiguration configuration =
        new DocumentAiRequestConfiguration("us", "test-project", "test-processor");
    baseRequest.setConfiguration(configuration);

    // Set authentication
    GcpAuthentication authentication =
        new GcpAuthentication(GcpAuthenticationType.BEARER, "test-token", null, null, null, null);
    baseRequest.setAuthentication(authentication);

    // Mock ExtractionRequestData and Document
    ExtractionRequestData requestData = mock(ExtractionRequestData.class);
    io.camunda.connector.api.document.Document mockInputDocument =
        mock(io.camunda.connector.api.document.Document.class);
    DocumentMetadata mockMetadata = mock(DocumentMetadata.class);
    InputStream mockInputStream = new ByteArrayInputStream("test document content".getBytes());

    when(requestData.document()).thenReturn(mockInputDocument);
    when(mockInputDocument.asInputStream()).thenReturn(mockInputStream);
    when(mockInputDocument.metadata()).thenReturn(mockMetadata);
    when(mockMetadata.getContentType()).thenReturn("application/pdf");

    // Act
    StructuredExtractionResponse response =
        caller.extractKeyValuePairsWithConfidence(requestData, baseRequest);

    // Assert
    Map<String, Object> expectedKeyValuePairs = new HashMap<>();
    expectedKeyValuePairs.put("Invoice", "Total:");

    Map<String, Float> expectedConfidenceScores = new HashMap<>();
    expectedConfidenceScores.put("Invoice", 0.85f); // Min of 0.95 and 0.85

    assertEquals(expectedKeyValuePairs, response.extractedFields());
    assertEquals(expectedConfidenceScores, response.confidenceScore());

    // Test geometry/position extraction
    assertTrue(response.geometry().containsKey("Invoice"));
    Polygon polygon = response.geometry().get("Invoice");
    assertEquals(1, polygon.getPage()); // Should be page 1

    // The polygon should combine both name and value bounding boxes
    // Expected combined bounding box: min(0.1, 0.4) = 0.1, max(0.3, 0.6) = 0.6 for X
    // Y coordinates: min(0.1, 0.1) = 0.1, max(0.2, 0.2) = 0.2
    List<PolygonPoint> points = polygon.getPoints();
    assertEquals(4, points.size()); // Should have 4 corner points

    // Check that the bounding rectangle encompasses both name and value regions
    boolean foundTopLeft = points.stream().anyMatch(p -> p.getX() == 0.1f && p.getY() == 0.1f);
    boolean foundTopRight = points.stream().anyMatch(p -> p.getX() == 0.6f && p.getY() == 0.1f);
    boolean foundBottomRight = points.stream().anyMatch(p -> p.getX() == 0.6f && p.getY() == 0.2f);
    boolean foundBottomLeft = points.stream().anyMatch(p -> p.getX() == 0.1f && p.getY() == 0.2f);

    assertTrue(foundTopLeft, "Should contain top-left corner (0.1, 0.1)");
    assertTrue(foundTopRight, "Should contain top-right corner (0.6, 0.1)");
    assertTrue(foundBottomRight, "Should contain bottom-right corner (0.6, 0.2)");
    assertTrue(foundBottomLeft, "Should contain bottom-left corner (0.1, 0.2)");
  }

  @Test
  void extractKeyValuePairs_HandlesMultipleDuplicateKeys() throws Exception {
    // Arrange
    DocumentAiClientSupplier mockSupplier = mock(DocumentAiClientSupplier.class);
    DocumentProcessorServiceClient mockClient = mock(DocumentProcessorServiceClient.class);
    ProcessResponse mockResponse = mock(ProcessResponse.class);
    Document mockDocument = mock(Document.class);

    when(mockSupplier.getDocumentAiClient(any(GcpAuthentication.class))).thenReturn(mockClient);
    when(mockClient.processDocument((ProcessRequest) any())).thenReturn(mockResponse);
    when(mockResponse.getDocument()).thenReturn(mockDocument);

    DocumentAiCaller caller = new DocumentAiCaller(mockSupplier);

    // Set the full document text
    String fullText = "Invoice Total1 Invoice Total2 Invoice Total3 Date 2023-05-15";
    when(mockDocument.getText()).thenReturn(fullText);

    // Mock document page
    Document.Page mockPage = mock(Document.Page.class);
    when(mockPage.getPageNumber()).thenReturn(1);
    when(mockPage.getTablesList()).thenReturn(List.of());

    // Create multiple form fields with the same key but using text segments
    Document.Page.FormField formField1 = createMockFormFieldWithSegments(0, 7, 8, 14, 0.95f, 0.90f);
    Document.Page.FormField formField2 =
        createMockFormFieldWithSegments(15, 22, 23, 29, 0.92f, 0.89f);
    Document.Page.FormField formField3 =
        createMockFormFieldWithSegments(30, 37, 38, 44, 0.88f, 0.85f);
    Document.Page.FormField uniqueFormField =
        createMockFormFieldWithSegments(45, 49, 50, 60, 0.99f, 0.97f);

    when(mockPage.getFormFieldsList())
        .thenReturn(List.of(formField1, formField2, formField3, uniqueFormField));
    when(mockDocument.getPagesList()).thenReturn(List.of(mockPage));

    // Setup GcpProvider and request data (same as existing test)
    GcpProvider baseRequest = new GcpProvider();
    DocumentAiRequestConfiguration configuration =
        new DocumentAiRequestConfiguration("us", "test-project", "test-processor");
    baseRequest.setConfiguration(configuration);

    GcpAuthentication authentication =
        new GcpAuthentication(GcpAuthenticationType.BEARER, "test-token", null, null, null, null);
    baseRequest.setAuthentication(authentication);

    ExtractionRequestData requestData = mock(ExtractionRequestData.class);
    io.camunda.connector.api.document.Document mockInputDocument =
        mock(io.camunda.connector.api.document.Document.class);
    DocumentMetadata mockMetadata = mock(DocumentMetadata.class);
    InputStream mockInputStream = new ByteArrayInputStream("test document content".getBytes());

    when(requestData.document()).thenReturn(mockInputDocument);
    when(mockInputDocument.asInputStream()).thenReturn(mockInputStream);
    when(mockInputDocument.metadata()).thenReturn(mockMetadata);
    when(mockMetadata.getContentType()).thenReturn("application/pdf");

    // Act
    StructuredExtractionResponse response =
        caller.extractKeyValuePairsWithConfidence(requestData, baseRequest);

    // Assert
    Map<String, Object> expectedKeyValuePairs = new HashMap<>();
    expectedKeyValuePairs.put("Invoice", "Total1"); // First occurrence keeps original key
    expectedKeyValuePairs.put("Invoice 2", "Total2"); // Second occurrence gets suffix " 2"
    expectedKeyValuePairs.put("Invoice 3", "Total3"); // Third occurrence gets suffix " 3"
    expectedKeyValuePairs.put("Date", "2023-05-15"); // Unique key stays as is

    Map<String, Float> expectedConfidenceScores = new HashMap<>();
    expectedConfidenceScores.put("Invoice", 0.90f); // Min of 0.95 and 0.90
    expectedConfidenceScores.put("Invoice 2", 0.89f); // Min of 0.92 and 0.89
    expectedConfidenceScores.put("Invoice 3", 0.85f); // Min of 0.88 and 0.85
    expectedConfidenceScores.put("Date", 0.97f); // Min of 0.99 and 0.97

    assertEquals(expectedKeyValuePairs, response.extractedFields());
    assertEquals(expectedConfidenceScores, response.confidenceScore());
  }

  @Test
  void extractKeyValuePairs_HandlesTablesWithConfidence() throws Exception {
    // Arrange
    DocumentAiClientSupplier mockSupplier = mock(DocumentAiClientSupplier.class);
    DocumentProcessorServiceClient mockClient = mock(DocumentProcessorServiceClient.class);
    ProcessResponse mockResponse = mock(ProcessResponse.class);
    Document mockDocument = mock(Document.class);

    when(mockSupplier.getDocumentAiClient(any(GcpAuthentication.class))).thenReturn(mockClient);
    when(mockClient.processDocument((ProcessRequest) any())).thenReturn(mockResponse);
    when(mockResponse.getDocument()).thenReturn(mockDocument);

    DocumentAiCaller caller = new DocumentAiCaller(mockSupplier);

    // Mock document page
    Document.Page mockPage = mock(Document.Page.class);
    when(mockPage.getFormFieldsList()).thenReturn(List.of());
    when(mockPage.getPageNumber()).thenReturn(1);

    // Set the full document text
    String fullText = "Name Age Location John Doe 32 New York Jane Smith 28 London";
    when(mockDocument.getText()).thenReturn(fullText);

    // Create mock table
    Document.Page.Table mockTable = mock(Document.Page.Table.class);

    // Create table layout with bounding polygon to fix NullPointerException
    Document.Page.Layout mockTableLayout = mock(Document.Page.Layout.class);
    BoundingPoly mockBoundingPoly = mock(BoundingPoly.class);
    NormalizedVertex mockVertex1 = mock(NormalizedVertex.class);
    NormalizedVertex mockVertex2 = mock(NormalizedVertex.class);
    NormalizedVertex mockVertex3 = mock(NormalizedVertex.class);
    NormalizedVertex mockVertex4 = mock(NormalizedVertex.class);

    when(mockVertex1.getX()).thenReturn(0.1f);
    when(mockVertex1.getY()).thenReturn(0.1f);
    when(mockVertex2.getX()).thenReturn(0.9f);
    when(mockVertex2.getY()).thenReturn(0.1f);
    when(mockVertex3.getX()).thenReturn(0.9f);
    when(mockVertex3.getY()).thenReturn(0.5f);
    when(mockVertex4.getX()).thenReturn(0.1f);
    when(mockVertex4.getY()).thenReturn(0.5f);

    when(mockBoundingPoly.getNormalizedVerticesList())
        .thenReturn(List.of(mockVertex1, mockVertex2, mockVertex3, mockVertex4));
    when(mockTableLayout.getBoundingPoly()).thenReturn(mockBoundingPoly);
    when(mockTable.getLayout()).thenReturn(mockTableLayout);

    // Create header row with proper text segments
    Document.Page.Table.TableRow headerRow =
        createMockTableRowWithSegments(
            List.of(new int[] {0, 4}, new int[] {5, 8}, new int[] {9, 17}),
            List.of(0.95f, 0.94f, 0.93f));

    // Create body rows with proper text segments
    Document.Page.Table.TableRow bodyRow1 =
        createMockTableRowWithSegments(
            List.of(new int[] {18, 26}, new int[] {27, 29}, new int[] {30, 38}),
            List.of(0.92f, 0.91f, 0.90f));
    Document.Page.Table.TableRow bodyRow2 =
        createMockTableRowWithSegments(
            List.of(new int[] {39, 49}, new int[] {50, 52}, new int[] {53, 59}),
            List.of(0.89f, 0.88f, 0.87f));

    when(mockTable.getHeaderRowsList()).thenReturn(List.of(headerRow));
    when(mockTable.getBodyRowsList()).thenReturn(List.of(bodyRow1, bodyRow2));
    when(mockPage.getTablesList()).thenReturn(List.of(mockTable));

    when(mockDocument.getPagesList()).thenReturn(List.of(mockPage));

    // Setup GcpProvider and request data (same as existing test)
    GcpProvider baseRequest = new GcpProvider();
    DocumentAiRequestConfiguration configuration =
        new DocumentAiRequestConfiguration("us", "test-project", "test-processor");
    baseRequest.setConfiguration(configuration);

    GcpAuthentication authentication =
        new GcpAuthentication(GcpAuthenticationType.BEARER, "test-token", null, null, null, null);
    baseRequest.setAuthentication(authentication);

    ExtractionRequestData requestData = mock(ExtractionRequestData.class);
    io.camunda.connector.api.document.Document mockInputDocument =
        mock(io.camunda.connector.api.document.Document.class);
    DocumentMetadata mockMetadata = mock(DocumentMetadata.class);
    InputStream mockInputStream = new ByteArrayInputStream("test document content".getBytes());

    when(requestData.document()).thenReturn(mockInputDocument);
    when(mockInputDocument.asInputStream()).thenReturn(mockInputStream);
    when(mockInputDocument.metadata()).thenReturn(mockMetadata);
    when(mockMetadata.getContentType()).thenReturn("application/pdf");

    // Act
    StructuredExtractionResponse response =
        caller.extractKeyValuePairsWithConfidence(requestData, baseRequest);

    // Assert
    // Check table structure
    assertEquals(1, response.extractedFields().size());
    assertTrue(response.extractedFields().containsKey("table 1"));

    List<List<String>> tableData = (List<List<String>>) response.extractedFields().get("table 1");
    assertEquals(3, tableData.size()); // Header + 2 rows

    // Check header row
    assertEquals(List.of("Name", "Age", "Location"), tableData.get(0));
    // Check data rows
    assertEquals(List.of("John Doe", "32", "New York"), tableData.get(1));
    assertEquals(List.of("Jane Smith", "28", "London"), tableData.get(2));

    // Check confidence score - now it's a List<List<Float>> for per-cell confidence scores
    assertTrue(response.confidenceScore().containsKey("table 1"));
    List<List<Float>> tableConfidenceData =
        (List<List<Float>>) response.confidenceScore().get("table 1");

    assertEquals(3, tableConfidenceData.size()); // 3 rows of confidence scores

    // Check confidence scores for header row
    assertEquals(3, tableConfidenceData.get(0).size()); // 3 columns
    assertEquals(0.95f, tableConfidenceData.get(0).get(0), 0.01f); // "Name" cell confidence
    assertEquals(0.94f, tableConfidenceData.get(0).get(1), 0.01f); // "Age" cell confidence
    assertEquals(0.93f, tableConfidenceData.get(0).get(2), 0.01f); // "Location" cell confidence

    // Check confidence scores for first data row
    assertEquals(3, tableConfidenceData.get(1).size()); // 3 columns
    assertEquals(0.92f, tableConfidenceData.get(1).get(0), 0.01f); // "John Doe" cell confidence
    assertEquals(0.91f, tableConfidenceData.get(1).get(1), 0.01f); // "32" cell confidence
    assertEquals(0.90f, tableConfidenceData.get(1).get(2), 0.01f); // "New York" cell confidence

    // Check confidence scores for second data row
    assertEquals(3, tableConfidenceData.get(2).size()); // 3 columns
    assertEquals(0.89f, tableConfidenceData.get(2).get(0), 0.01f); // "Jane Smith" cell confidence
    assertEquals(0.88f, tableConfidenceData.get(2).get(1), 0.01f); // "28" cell confidence
    assertEquals(0.87f, tableConfidenceData.get(2).get(2), 0.01f); // "London" cell confidence

    // Test table geometry/position extraction
    assertTrue(response.geometry().containsKey("table 1"));
    Polygon tablePolygon = response.geometry().get("table 1");
    assertEquals(1, tablePolygon.getPage()); // Should be page 1

    // Verify the table polygon points are correctly extracted
    List<PolygonPoint> tablePoints = tablePolygon.getPoints();
    assertEquals(4, tablePoints.size()); // Should have 4 corner points

    // Check that the table polygon contains the expected vertices from our mock
    boolean foundTableTopLeft =
        tablePoints.stream().anyMatch(p -> p.getX() == 0.1f && p.getY() == 0.1f);
    boolean foundTableTopRight =
        tablePoints.stream().anyMatch(p -> p.getX() == 0.9f && p.getY() == 0.1f);
    boolean foundTableBottomRight =
        tablePoints.stream().anyMatch(p -> p.getX() == 0.9f && p.getY() == 0.5f);
    boolean foundTableBottomLeft =
        tablePoints.stream().anyMatch(p -> p.getX() == 0.1f && p.getY() == 0.5f);

    assertTrue(foundTableTopLeft, "Should contain table top-left corner (0.1, 0.1)");
    assertTrue(foundTableTopRight, "Should contain table top-right corner (0.9, 0.1)");
    assertTrue(foundTableBottomRight, "Should contain table bottom-right corner (0.9, 0.5)");
    assertTrue(foundTableBottomLeft, "Should contain table bottom-left corner (0.1, 0.5)");
  }

  @Test
  void extractKeyValuePairs_VerifiesPolygonCalculationForMultipleFields() throws Exception {
    // Arrange
    DocumentAiClientSupplier mockSupplier = mock(DocumentAiClientSupplier.class);
    DocumentProcessorServiceClient mockClient = mock(DocumentProcessorServiceClient.class);
    ProcessResponse mockResponse = mock(ProcessResponse.class);
    Document mockDocument = mock(Document.class);

    when(mockSupplier.getDocumentAiClient(any(GcpAuthentication.class))).thenReturn(mockClient);
    when(mockClient.processDocument((ProcessRequest) any())).thenReturn(mockResponse);
    when(mockResponse.getDocument()).thenReturn(mockDocument);

    DocumentAiCaller caller = new DocumentAiCaller(mockSupplier);

    // Set document text
    String fullText = "Name John Date 01/01/2024";
    when(mockDocument.getText()).thenReturn(fullText);

    // Mock document page
    Document.Page mockPage = mock(Document.Page.class);
    when(mockPage.getPageNumber()).thenReturn(2); // Use page 2 to test page number extraction
    when(mockPage.getTablesList()).thenReturn(List.of());

    // Create form fields with different polygon positions to test the bounding calculation
    Document.Page.FormField nameField =
        createMockFormFieldWithDifferentPolygons(
            0,
            4,
            5,
            9,
            0.95f,
            0.90f,
            // Name polygon (top-left area)
            0.1f,
            0.1f,
            0.2f,
            0.2f,
            // Value polygon (top-right area)
            0.3f,
            0.1f,
            0.4f,
            0.2f);

    Document.Page.FormField dateField =
        createMockFormFieldWithDifferentPolygons(
            10,
            14,
            15,
            25,
            0.85f,
            0.80f,
            // Date polygon (bottom-left area)
            0.1f,
            0.8f,
            0.2f,
            0.9f,
            // Value polygon (bottom-right area)
            0.6f,
            0.8f,
            0.9f,
            0.9f);

    when(mockPage.getFormFieldsList()).thenReturn(List.of(nameField, dateField));
    when(mockDocument.getPagesList()).thenReturn(List.of(mockPage));

    // Setup GCP provider and request data
    GcpProvider baseRequest = new GcpProvider();
    DocumentAiRequestConfiguration configuration =
        new DocumentAiRequestConfiguration("us", "test-project", "test-processor");
    baseRequest.setConfiguration(configuration);

    GcpAuthentication authentication =
        new GcpAuthentication(GcpAuthenticationType.BEARER, "test-token", null, null, null, null);
    baseRequest.setAuthentication(authentication);

    ExtractionRequestData requestData = mock(ExtractionRequestData.class);
    io.camunda.connector.api.document.Document mockInputDocument =
        mock(io.camunda.connector.api.document.Document.class);
    DocumentMetadata mockMetadata = mock(DocumentMetadata.class);
    InputStream mockInputStream = new ByteArrayInputStream("test document content".getBytes());

    when(requestData.document()).thenReturn(mockInputDocument);
    when(mockInputDocument.asInputStream()).thenReturn(mockInputStream);
    when(mockInputDocument.metadata()).thenReturn(mockMetadata);
    when(mockMetadata.getContentType()).thenReturn("application/pdf");

    // Act
    StructuredExtractionResponse response =
        caller.extractKeyValuePairsWithConfidence(requestData, baseRequest);

    // Assert
    // Verify extracted fields
    assertEquals("John", response.extractedFields().get("Name"));
    assertEquals("01/01/2024", response.extractedFields().get("Date"));

    // Test geometry extraction for Name field
    assertTrue(response.geometry().containsKey("Name"));
    Polygon namePolygon = response.geometry().get("Name");
    assertEquals(2, namePolygon.getPage()); // Should be page 2

    // Name field should have bounding box that encompasses both key (0.1,0.1 to 0.2,0.2)
    // and value (0.3,0.1 to 0.4,0.2) polygons, resulting in (0.1,0.1 to 0.4,0.2)
    List<PolygonPoint> namePoints = namePolygon.getPoints();
    assertEquals(4, namePoints.size());

    boolean nameTopLeft = namePoints.stream().anyMatch(p -> p.getX() == 0.1f && p.getY() == 0.1f);
    boolean nameTopRight = namePoints.stream().anyMatch(p -> p.getX() == 0.4f && p.getY() == 0.1f);
    boolean nameBottomRight =
        namePoints.stream().anyMatch(p -> p.getX() == 0.4f && p.getY() == 0.2f);
    boolean nameBottomLeft =
        namePoints.stream().anyMatch(p -> p.getX() == 0.1f && p.getY() == 0.2f);

    assertTrue(
        nameTopLeft && nameTopRight && nameBottomRight && nameBottomLeft,
        "Name polygon should span from (0.1,0.1) to (0.4,0.2)");

    // Test geometry extraction for Date field
    assertTrue(response.geometry().containsKey("Date"));
    Polygon datePolygon = response.geometry().get("Date");
    assertEquals(2, datePolygon.getPage()); // Should be page 2

    // Date field should have bounding box that encompasses both key (0.1,0.8 to 0.2,0.9)
    // and value (0.6,0.8 to 0.9,0.9) polygons, resulting in (0.1,0.8 to 0.9,0.9)
    List<PolygonPoint> datePoints = datePolygon.getPoints();
    assertEquals(4, datePoints.size());

    boolean dateTopLeft = datePoints.stream().anyMatch(p -> p.getX() == 0.1f && p.getY() == 0.8f);
    boolean dateTopRight = datePoints.stream().anyMatch(p -> p.getX() == 0.9f && p.getY() == 0.8f);
    boolean dateBottomRight =
        datePoints.stream().anyMatch(p -> p.getX() == 0.9f && p.getY() == 0.9f);
    boolean dateBottomLeft =
        datePoints.stream().anyMatch(p -> p.getX() == 0.1f && p.getY() == 0.9f);

    assertTrue(
        dateTopLeft && dateTopRight && dateBottomRight && dateBottomLeft,
        "Date polygon should span from (0.1,0.8) to (0.9,0.9)");
  }

  // Add new helper methods to create text anchors with segments
  private Document.TextAnchor createTextAnchor(int startIndex, int endIndex) {
    Document.TextAnchor mockAnchor = mock(Document.TextAnchor.class);
    Document.TextAnchor.TextSegment mockSegment = mock(Document.TextAnchor.TextSegment.class);

    when(mockSegment.getStartIndex()).thenReturn((long) startIndex);
    when(mockSegment.getEndIndex()).thenReturn((long) endIndex);
    when(mockAnchor.getTextSegmentsList()).thenReturn(List.of(mockSegment));

    return mockAnchor;
  }

  private Document.Page.FormField createMockFormFieldWithSegments(
      int keyStartIndex,
      int keyEndIndex,
      int valueStartIndex,
      int valueEndIndex,
      float keyConfidence,
      float valueConfidence) {

    Document.Page.FormField mockFormField = mock(Document.Page.FormField.class);
    Document.Page.Layout mockNameLayout = mock(Document.Page.Layout.class);
    Document.Page.Layout mockValueLayout = mock(Document.Page.Layout.class);

    Document.TextAnchor mockNameAnchor = createTextAnchor(keyStartIndex, keyEndIndex);
    Document.TextAnchor mockValueAnchor = createTextAnchor(valueStartIndex, valueEndIndex);

    when(mockFormField.getFieldName()).thenReturn(mockNameLayout);
    when(mockFormField.getFieldValue()).thenReturn(mockValueLayout);
    when(mockFormField.hasFieldName()).thenReturn(true);
    when(mockFormField.hasFieldValue()).thenReturn(true);
    when(mockFormField.getValueType()).thenReturn(null);

    when(mockNameLayout.getTextAnchor()).thenReturn(mockNameAnchor);
    when(mockValueLayout.getTextAnchor()).thenReturn(mockValueAnchor);

    when(mockNameLayout.getConfidence()).thenReturn(keyConfidence);
    when(mockValueLayout.getConfidence()).thenReturn(valueConfidence);

    // Add bounding polygon mocks to fix NullPointerException
    BoundingPoly mockNameBoundingPoly = mock(BoundingPoly.class);
    BoundingPoly mockValueBoundingPoly = mock(BoundingPoly.class);
    NormalizedVertex mockVertex1 = mock(NormalizedVertex.class);
    NormalizedVertex mockVertex2 = mock(NormalizedVertex.class);
    NormalizedVertex mockVertex3 = mock(NormalizedVertex.class);
    NormalizedVertex mockVertex4 = mock(NormalizedVertex.class);

    when(mockVertex1.getX()).thenReturn(0.1f);
    when(mockVertex1.getY()).thenReturn(0.1f);
    when(mockVertex2.getX()).thenReturn(0.5f);
    when(mockVertex2.getY()).thenReturn(0.1f);
    when(mockVertex3.getX()).thenReturn(0.5f);
    when(mockVertex3.getY()).thenReturn(0.2f);
    when(mockVertex4.getX()).thenReturn(0.1f);
    when(mockVertex4.getY()).thenReturn(0.2f);

    when(mockNameBoundingPoly.getNormalizedVerticesList())
        .thenReturn(List.of(mockVertex1, mockVertex2, mockVertex3, mockVertex4));
    when(mockValueBoundingPoly.getNormalizedVerticesList())
        .thenReturn(List.of(mockVertex1, mockVertex2, mockVertex3, mockVertex4));
    when(mockNameLayout.getBoundingPoly()).thenReturn(mockNameBoundingPoly);
    when(mockValueLayout.getBoundingPoly()).thenReturn(mockValueBoundingPoly);

    return mockFormField;
  }

  private Document.Page.Table.TableRow createMockTableRowWithSegments(
      List<int[]> cellPositions, List<Float> confidences) {

    Document.Page.Table.TableRow mockRow = mock(Document.Page.Table.TableRow.class);
    List<Document.Page.Table.TableCell> cells = new ArrayList<>();

    for (int i = 0; i < cellPositions.size(); i++) {
      int[] position = cellPositions.get(i);
      float confidence = confidences.get(i);

      Document.Page.Table.TableCell mockCell = mock(Document.Page.Table.TableCell.class);
      Document.Page.Layout mockLayout = mock(Document.Page.Layout.class);
      Document.TextAnchor mockTextAnchor = createTextAnchor(position[0], position[1]);

      when(mockCell.hasLayout()).thenReturn(true);
      when(mockCell.getLayout()).thenReturn(mockLayout);
      when(mockLayout.getTextAnchor()).thenReturn(mockTextAnchor);
      when(mockLayout.getConfidence()).thenReturn(confidence);

      cells.add(mockCell);
    }

    when(mockRow.getCellsList()).thenReturn(cells);
    return mockRow;
  }

  private Document.Page.FormField createMockFormFieldWithDifferentPolygons(
      int keyStartIndex,
      int keyEndIndex,
      int valueStartIndex,
      int valueEndIndex,
      float keyConfidence,
      float valueConfidence,
      float x1,
      float y1,
      float x2,
      float y2,
      float x3,
      float y3,
      float x4,
      float y4) {

    Document.Page.FormField mockFormField = mock(Document.Page.FormField.class);
    Document.Page.Layout mockNameLayout = mock(Document.Page.Layout.class);
    Document.Page.Layout mockValueLayout = mock(Document.Page.Layout.class);

    Document.TextAnchor mockNameAnchor = createTextAnchor(keyStartIndex, keyEndIndex);
    Document.TextAnchor mockValueAnchor = createTextAnchor(valueStartIndex, valueEndIndex);

    when(mockFormField.getFieldName()).thenReturn(mockNameLayout);
    when(mockFormField.getFieldValue()).thenReturn(mockValueLayout);
    when(mockFormField.hasFieldName()).thenReturn(true);
    when(mockFormField.hasFieldValue()).thenReturn(true);
    when(mockFormField.getValueType()).thenReturn(null);

    when(mockNameLayout.getTextAnchor()).thenReturn(mockNameAnchor);
    when(mockValueLayout.getTextAnchor()).thenReturn(mockValueAnchor);

    when(mockNameLayout.getConfidence()).thenReturn(keyConfidence);
    when(mockValueLayout.getConfidence()).thenReturn(valueConfidence);

    // Create separate vertices for name and value polygons
    BoundingPoly mockNameBoundingPoly = mock(BoundingPoly.class);
    BoundingPoly mockValueBoundingPoly = mock(BoundingPoly.class);

    // Name polygon vertices
    NormalizedVertex mockNameVertex1 = mock(NormalizedVertex.class);
    NormalizedVertex mockNameVertex2 = mock(NormalizedVertex.class);
    NormalizedVertex mockNameVertex3 = mock(NormalizedVertex.class);
    NormalizedVertex mockNameVertex4 = mock(NormalizedVertex.class);

    when(mockNameVertex1.getX()).thenReturn(x1);
    when(mockNameVertex1.getY()).thenReturn(y1);
    when(mockNameVertex2.getX()).thenReturn(x2);
    when(mockNameVertex2.getY()).thenReturn(y1);
    when(mockNameVertex3.getX()).thenReturn(x2);
    when(mockNameVertex3.getY()).thenReturn(y2);
    when(mockNameVertex4.getX()).thenReturn(x1);
    when(mockNameVertex4.getY()).thenReturn(y2);

    // Value polygon vertices
    NormalizedVertex mockValueVertex1 = mock(NormalizedVertex.class);
    NormalizedVertex mockValueVertex2 = mock(NormalizedVertex.class);
    NormalizedVertex mockValueVertex3 = mock(NormalizedVertex.class);
    NormalizedVertex mockValueVertex4 = mock(NormalizedVertex.class);

    when(mockValueVertex1.getX()).thenReturn(x3);
    when(mockValueVertex1.getY()).thenReturn(y3);
    when(mockValueVertex2.getX()).thenReturn(x4);
    when(mockValueVertex2.getY()).thenReturn(y3);
    when(mockValueVertex3.getX()).thenReturn(x4);
    when(mockValueVertex3.getY()).thenReturn(y4);
    when(mockValueVertex4.getX()).thenReturn(x3);
    when(mockValueVertex4.getY()).thenReturn(y4);

    when(mockNameBoundingPoly.getNormalizedVerticesList())
        .thenReturn(List.of(mockNameVertex1, mockNameVertex2, mockNameVertex3, mockNameVertex4));
    when(mockValueBoundingPoly.getNormalizedVerticesList())
        .thenReturn(
            List.of(mockValueVertex1, mockValueVertex2, mockValueVertex3, mockValueVertex4));
    when(mockNameLayout.getBoundingPoly()).thenReturn(mockNameBoundingPoly);
    when(mockValueLayout.getBoundingPoly()).thenReturn(mockValueBoundingPoly);

    return mockFormField;
  }
}
