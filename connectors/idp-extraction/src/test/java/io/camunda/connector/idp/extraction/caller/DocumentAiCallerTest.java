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
import io.camunda.client.api.response.DocumentMetadata;
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
    Document.Page.FormField mockFormField = mock(Document.Page.FormField.class);

    // Create mock Layout objects
    Document.Page.Layout mockNameLayout = mock(Document.Page.Layout.class);
    Document.Page.Layout mockValueLayout = mock(Document.Page.Layout.class);

    // Mock bounding poly for layouts
    BoundingPoly mockBoundingPoly = mock(BoundingPoly.class);
    when(mockNameLayout.hasBoundingPoly()).thenReturn(true);
    when(mockNameLayout.getBoundingPoly()).thenReturn(mockBoundingPoly);
    when(mockValueLayout.hasBoundingPoly()).thenReturn(true);
    when(mockValueLayout.getBoundingPoly()).thenReturn(mockBoundingPoly);
    when(mockBoundingPoly.getVerticesCount()).thenReturn(0);
    when(mockBoundingPoly.getVerticesList()).thenReturn(List.of());

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
    io.camunda.document.Document mockInputDocument = mock(io.camunda.document.Document.class);
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
    io.camunda.document.Document mockInputDocument = mock(io.camunda.document.Document.class);
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

    // Set the full document text
    String fullText = "Name Age Location John Doe 32 New York Jane Smith 28 London";
    when(mockDocument.getText()).thenReturn(fullText);

    // Create mock table
    Document.Page.Table mockTable = mock(Document.Page.Table.class);

    // Add layout to the table itself
    Document.Page.Layout mockTableLayout = mock(Document.Page.Layout.class);
    BoundingPoly mockTableBoundingPoly = mock(BoundingPoly.class);
    when(mockTableLayout.hasBoundingPoly()).thenReturn(true);
    when(mockTableLayout.getBoundingPoly()).thenReturn(mockTableBoundingPoly);
    when(mockTableBoundingPoly.getVerticesCount()).thenReturn(0);
    when(mockTableBoundingPoly.getVerticesList()).thenReturn(List.of());

    when(mockTable.hasLayout()).thenReturn(true);
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
    io.camunda.document.Document mockInputDocument = mock(io.camunda.document.Document.class);
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

    // Check confidence score (average of all cells:
    // (0.95+0.94+0.93+0.92+0.91+0.90+0.89+0.88+0.87)/9 = 0.91)
    assertTrue(response.confidenceScore().containsKey("table 1"));
    assertEquals(
        0.91f, response.confidenceScore().get("table 1"), 0.01f); // Allow small rounding difference
  }

  @Test
  void extractKeyValuePairs_ExtractsPositionInformation() throws Exception {
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
    when(mockPage.getTablesList()).thenReturn(List.of());
    // Set page number
    when(mockPage.getPageNumber()).thenReturn(1);

    // Set up document text
    when(mockDocument.getText()).thenReturn("Invoice 123");

    // Create form field with specific position information
    Document.Page.FormField mockFormField = mock(Document.Page.FormField.class);
    Document.Page.Layout mockNameLayout = mock(Document.Page.Layout.class);
    Document.Page.Layout mockValueLayout = mock(Document.Page.Layout.class);

    // Create text anchors
    Document.TextAnchor mockNameAnchor = createTextAnchor(0, 7); // "Invoice"
    Document.TextAnchor mockValueAnchor = createTextAnchor(8, 11); // "123"

    // Create bounding poly with specific coordinates
    BoundingPoly mockNameBoundingPoly =
        createBoundingPolyWithVertices(
            0.1f, 0.1f, // top-left
            0.3f, 0.1f, // top-right
            0.3f, 0.2f, // bottom-right
            0.1f, 0.2f // bottom-left
            );

    BoundingPoly mockValueBoundingPoly =
        createBoundingPolyWithVertices(
            0.4f, 0.1f, // top-left
            0.5f, 0.1f, // top-right
            0.5f, 0.2f, // bottom-right
            0.4f, 0.2f // bottom-left
            );

    // Configure layouts
    when(mockNameLayout.hasBoundingPoly()).thenReturn(true);
    when(mockNameLayout.getBoundingPoly()).thenReturn(mockNameBoundingPoly);
    when(mockValueLayout.hasBoundingPoly()).thenReturn(true);
    when(mockValueLayout.getBoundingPoly()).thenReturn(mockValueBoundingPoly);
    when(mockNameLayout.getTextAnchor()).thenReturn(mockNameAnchor);
    when(mockValueLayout.getTextAnchor()).thenReturn(mockValueAnchor);
    when(mockNameLayout.getConfidence()).thenReturn(0.95f);
    when(mockValueLayout.getConfidence()).thenReturn(0.9f);

    // Configure form field
    when(mockFormField.getFieldName()).thenReturn(mockNameLayout);
    when(mockFormField.getFieldValue()).thenReturn(mockValueLayout);
    when(mockFormField.hasFieldName()).thenReturn(true);
    when(mockFormField.hasFieldValue()).thenReturn(true);
    when(mockFormField.getValueType()).thenReturn(null);

    // Add form field to page
    when(mockPage.getFormFieldsList()).thenReturn(List.of(mockFormField));
    when(mockDocument.getPagesList()).thenReturn(List.of(mockPage));

    // Setup request data
    GcpProvider baseRequest = new GcpProvider();
    DocumentAiRequestConfiguration configuration =
        new DocumentAiRequestConfiguration("us", "test-project", "test-processor");
    baseRequest.setConfiguration(configuration);
    GcpAuthentication authentication =
        new GcpAuthentication(GcpAuthenticationType.BEARER, "test-token", null, null, null, null);
    baseRequest.setAuthentication(authentication);

    ExtractionRequestData requestData = mock(ExtractionRequestData.class);
    io.camunda.document.Document mockInputDocument = mock(io.camunda.document.Document.class);
    DocumentMetadata mockMetadata = mock(DocumentMetadata.class);
    InputStream mockInputStream = new ByteArrayInputStream("test content".getBytes());

    when(requestData.document()).thenReturn(mockInputDocument);
    when(mockInputDocument.asInputStream()).thenReturn(mockInputStream);
    when(mockInputDocument.metadata()).thenReturn(mockMetadata);
    when(mockMetadata.getContentType()).thenReturn("application/pdf");

    // Act
    StructuredExtractionResponse response =
        caller.extractKeyValuePairsWithConfidence(requestData, baseRequest);

    // Assert
    // Check extracted field and confidence
    assertEquals("123", response.extractedFields().get("Invoice"));
    assertEquals(0.9f, response.confidenceScore().get("Invoice"));

    // Check position information
    assertTrue(response.geometry().containsKey("Invoice"));
    Polygon polygon = response.geometry().get("Invoice");

    // Check page number (should match the page we set up)
    assertEquals(1, polygon.getPage());

    // Check the points list
    List<PolygonPoint> points = polygon.getPoints();
    assertEquals(4, points.size());

    // Verify the coordinates of the bounding box
    // It should have the top-left point of the name and extend to include the value
    assertEquals(0.1f, points.get(0).getX());
    assertEquals(0.1f, points.get(0).getY());
    assertEquals(0.5f, points.get(1).getX());
    assertEquals(0.1f, points.get(1).getY());
    assertEquals(0.5f, points.get(2).getX());
    assertEquals(0.2f, points.get(2).getY());
    assertEquals(0.1f, points.get(3).getX());
    assertEquals(0.2f, points.get(3).getY());
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

    // Add bounding poly mocks
    BoundingPoly mockBoundingPoly = mock(BoundingPoly.class);
    when(mockNameLayout.hasBoundingPoly()).thenReturn(true);
    when(mockNameLayout.getBoundingPoly()).thenReturn(mockBoundingPoly);
    when(mockValueLayout.hasBoundingPoly()).thenReturn(true);
    when(mockValueLayout.getBoundingPoly()).thenReturn(mockBoundingPoly);
    when(mockBoundingPoly.getVerticesCount()).thenReturn(0);
    when(mockBoundingPoly.getVerticesList()).thenReturn(List.of());

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

      // Add bounding poly mock
      BoundingPoly mockBoundingPoly = mock(BoundingPoly.class);
      when(mockLayout.hasBoundingPoly()).thenReturn(true);
      when(mockLayout.getBoundingPoly()).thenReturn(mockBoundingPoly);
      when(mockBoundingPoly.getVerticesCount()).thenReturn(0);
      when(mockBoundingPoly.getVerticesList()).thenReturn(List.of());

      when(mockCell.hasLayout()).thenReturn(true);
      when(mockCell.getLayout()).thenReturn(mockLayout);
      when(mockLayout.getTextAnchor()).thenReturn(mockTextAnchor);
      when(mockLayout.getConfidence()).thenReturn(confidence);

      cells.add(mockCell);
    }

    when(mockRow.getCellsList()).thenReturn(cells);
    return mockRow;
  }

  // Helper method to create a bounding poly with specific coordinates
  private BoundingPoly createBoundingPolyWithVertices(float... coordinates) {
    BoundingPoly mockBoundingPoly = mock(BoundingPoly.class);
    List<NormalizedVertex> vertices = new ArrayList<>();

    // Create 4 vertices (top-left, top-right, bottom-right, bottom-left)
    for (int i = 0; i < coordinates.length; i += 2) {
      NormalizedVertex vertex = mock(NormalizedVertex.class);
      when(vertex.getX()).thenReturn(coordinates[i]);
      when(vertex.getY()).thenReturn(coordinates[i + 1]);
      vertices.add(vertex);
    }

    when(mockBoundingPoly.getVerticesCount()).thenReturn(vertices.size());
    when(mockBoundingPoly.getNormalizedVerticesList()).thenReturn(vertices);

    return mockBoundingPoly;
  }
}
