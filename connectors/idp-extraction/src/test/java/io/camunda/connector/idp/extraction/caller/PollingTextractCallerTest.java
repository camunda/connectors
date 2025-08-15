/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.caller;

import static io.camunda.connector.idp.extraction.caller.PollingTextractCaller.MAX_RESULT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.idp.extraction.model.StructuredExtractionResponse;
import io.camunda.connector.idp.extraction.utils.AwsS3Util;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.*;

@ExtendWith(MockitoExtension.class)
class PollingTextractCallerTest {

  TextractClient textractClient = Mockito.mock(TextractClient.class);
  S3AsyncClient s3AsyncClient = Mockito.mock(S3AsyncClient.class);
  S3Object s3Object = Mockito.mock(S3Object.class);
  MockedStatic<AwsS3Util> awsS3UtilMockedStatic;
  Document mockedDocument = Mockito.mock(Document.class);

  @BeforeEach
  void beforeEach() {
    when(s3Object.name()).thenReturn("Test document.pdf");

    awsS3UtilMockedStatic = Mockito.mockStatic(AwsS3Util.class);
    awsS3UtilMockedStatic
        .when(
            () ->
                AwsS3Util.buildS3ObjectFromDocument(
                    any(), any(String.class), any(S3AsyncClient.class)))
        .thenReturn(s3Object);
    awsS3UtilMockedStatic
        .when(
            () ->
                AwsS3Util.deleteS3ObjectFromBucketAsync(
                    any(), any(String.class), any(S3AsyncClient.class)))
        .thenAnswer(invocation -> null);
  }

  @AfterEach
  void afterEach() {
    awsS3UtilMockedStatic.close();
  }

  @Test
  void callTextractTextDetectionWithSuccess() throws Exception {
    String jobId = "1";
    GetDocumentTextDetectionRequest getDocumentTextDetectionRequest =
        GetDocumentTextDetectionRequest.builder().jobId(jobId).maxResults(MAX_RESULT).build();

    GetDocumentTextDetectionResponse getDocumentTextDetectionResponse =
        GetDocumentTextDetectionResponse.builder()
            .jobStatus(JobStatus.SUCCEEDED)
            .blocks(
                List.of(
                    Block.builder().text("AAA").blockType(BlockType.LINE).build(),
                    Block.builder().text("BBB").blockType(BlockType.LINE).build()))
            .build();

    StartDocumentTextDetectionResponse startDocumentTextDetectionResponse =
        StartDocumentTextDetectionResponse.builder().jobId(jobId).build();

    when(textractClient.startDocumentTextDetection(any(StartDocumentTextDetectionRequest.class)))
        .thenReturn(startDocumentTextDetectionResponse);

    when(textractClient.getDocumentTextDetection(getDocumentTextDetectionRequest))
        .thenReturn(getDocumentTextDetectionResponse);

    String expectedExtractedText = "AAA\nBBB";
    String extractedText =
        new PollingTextractCaller()
            .call(mockedDocument, "test-aws-s3-bucket-name", textractClient, s3AsyncClient);

    assertThat(extractedText).isEqualTo(expectedExtractedText);
  }

  @Test
  void callTextractTextDetectionWithEmptyResult() throws Exception {
    String jobId = "1";
    GetDocumentTextDetectionRequest getDocumentTextDetectionRequest =
        GetDocumentTextDetectionRequest.builder().jobId(jobId).maxResults(MAX_RESULT).build();

    GetDocumentTextDetectionResponse getDocumentTextDetectionResponse =
        GetDocumentTextDetectionResponse.builder()
            .jobStatus(JobStatus.SUCCEEDED)
            .blocks(List.of())
            .build();

    StartDocumentTextDetectionResponse startDocumentTextDetectionResponse =
        StartDocumentTextDetectionResponse.builder().jobId(jobId).build();

    when(textractClient.startDocumentTextDetection(any(StartDocumentTextDetectionRequest.class)))
        .thenReturn(startDocumentTextDetectionResponse);

    when(textractClient.getDocumentTextDetection(getDocumentTextDetectionRequest))
        .thenReturn(getDocumentTextDetectionResponse);

    String expectedExtractedText = "";
    String extractedText =
        new PollingTextractCaller()
            .call(mockedDocument, "test-aws-s3-bucket-name", textractClient, s3AsyncClient);

    assertThat(extractedText).isEqualTo(expectedExtractedText);
  }

  @Test
  void callTextractTextDetectionWithFailure() {
    String jobId = "1";
    GetDocumentTextDetectionRequest getDocumentTextDetectionRequest =
        GetDocumentTextDetectionRequest.builder().jobId(jobId).maxResults(MAX_RESULT).build();

    GetDocumentTextDetectionResponse getDocumentTextDetectionResponse =
        GetDocumentTextDetectionResponse.builder()
            .jobStatus(JobStatus.FAILED)
            .statusMessage("Test exception message")
            .build();

    StartDocumentTextDetectionResponse startDocumentTextDetectionResponse =
        StartDocumentTextDetectionResponse.builder().jobId(jobId).build();

    when(textractClient.startDocumentTextDetection(any(StartDocumentTextDetectionRequest.class)))
        .thenReturn(startDocumentTextDetectionResponse);

    when(textractClient.getDocumentTextDetection(getDocumentTextDetectionRequest))
        .thenReturn(getDocumentTextDetectionResponse);

    PollingTextractCaller pollingTextractCaller = new PollingTextractCaller();

    Exception exception =
        assertThrows(
            ConnectorException.class,
            () ->
                pollingTextractCaller.call(
                    mockedDocument, "test-aws-s3-bucket-name", textractClient, s3AsyncClient));

    assertEquals("Test exception message", exception.getMessage());
  }

  @Test
  void extractKeyValuePairsWithConfidence_HandlesTablesCorrectly() throws Exception {
    // Arrange
    String jobId = "1";

    // Mock TEXT blocks for cell contents
    Block nameHeaderBlock =
        Block.builder().id("t1").text("Name").blockType(BlockType.WORD).confidence(0.95f).build();

    Block ageHeaderBlock =
        Block.builder().id("t2").text("Age").blockType(BlockType.WORD).confidence(0.94f).build();

    Block locationHeaderBlock =
        Block.builder()
            .id("t3")
            .text("Location")
            .blockType(BlockType.WORD)
            .confidence(0.93f)
            .build();

    Block johnBlock =
        Block.builder()
            .id("t4")
            .text("John Doe")
            .blockType(BlockType.WORD)
            .confidence(0.92f)
            .build();

    Block age32Block =
        Block.builder().id("t5").text("32").blockType(BlockType.WORD).confidence(0.91f).build();

    Block nyBlock =
        Block.builder()
            .id("t6")
            .text("New York")
            .blockType(BlockType.WORD)
            .confidence(0.90f)
            .build();

    Block janeBlock =
        Block.builder()
            .id("t7")
            .text("Jane Smith")
            .blockType(BlockType.WORD)
            .confidence(0.89f)
            .build();

    Block age28Block =
        Block.builder().id("t8").text("28").blockType(BlockType.WORD).confidence(0.88f).build();

    Block londonBlock =
        Block.builder().id("t9").text("London").blockType(BlockType.WORD).confidence(0.87f).build();

    // Create table cells with relationships to text blocks
    Block cell1_1 =
        Block.builder()
            .id("c1")
            .blockType(BlockType.CELL)
            .rowIndex(1)
            .columnIndex(1)
            .confidence(0.95f)
            .relationships(
                List.of(
                    Relationship.builder().type(RelationshipType.CHILD).ids(List.of("t1")).build()))
            .build();

    Block cell1_2 =
        Block.builder()
            .id("c2")
            .blockType(BlockType.CELL)
            .rowIndex(1)
            .columnIndex(2)
            .confidence(0.94f)
            .relationships(
                List.of(
                    Relationship.builder().type(RelationshipType.CHILD).ids(List.of("t2")).build()))
            .build();

    Block cell1_3 =
        Block.builder()
            .id("c3")
            .blockType(BlockType.CELL)
            .rowIndex(1)
            .columnIndex(3)
            .confidence(0.93f)
            .relationships(
                List.of(
                    Relationship.builder().type(RelationshipType.CHILD).ids(List.of("t3")).build()))
            .build();

    Block cell2_1 =
        Block.builder()
            .id("c4")
            .blockType(BlockType.CELL)
            .rowIndex(2)
            .columnIndex(1)
            .confidence(0.92f)
            .relationships(
                List.of(
                    Relationship.builder().type(RelationshipType.CHILD).ids(List.of("t4")).build()))
            .build();

    Block cell2_2 =
        Block.builder()
            .id("c5")
            .blockType(BlockType.CELL)
            .rowIndex(2)
            .columnIndex(2)
            .confidence(0.91f)
            .relationships(
                List.of(
                    Relationship.builder().type(RelationshipType.CHILD).ids(List.of("t5")).build()))
            .build();

    Block cell2_3 =
        Block.builder()
            .id("c6")
            .blockType(BlockType.CELL)
            .rowIndex(2)
            .columnIndex(3)
            .confidence(0.90f)
            .relationships(
                List.of(
                    Relationship.builder().type(RelationshipType.CHILD).ids(List.of("t6")).build()))
            .build();

    Block cell3_1 =
        Block.builder()
            .id("c7")
            .blockType(BlockType.CELL)
            .rowIndex(3)
            .columnIndex(1)
            .confidence(0.89f)
            .relationships(
                List.of(
                    Relationship.builder().type(RelationshipType.CHILD).ids(List.of("t7")).build()))
            .build();

    Block cell3_2 =
        Block.builder()
            .id("c8")
            .blockType(BlockType.CELL)
            .rowIndex(3)
            .columnIndex(2)
            .confidence(0.88f)
            .relationships(
                List.of(
                    Relationship.builder().type(RelationshipType.CHILD).ids(List.of("t8")).build()))
            .build();

    Block cell3_3 =
        Block.builder()
            .id("c9")
            .blockType(BlockType.CELL)
            .rowIndex(3)
            .columnIndex(3)
            .confidence(0.87f)
            .relationships(
                List.of(
                    Relationship.builder().type(RelationshipType.CHILD).ids(List.of("t9")).build()))
            .build();

    // Create table block with relationships to cells
    Block tableBlock =
        Block.builder()
            .id("table1")
            .blockType(BlockType.TABLE)
            .confidence(0.96f)
            .page(1)
            .geometry(
                Geometry.builder()
                    .polygon(
                        List.of(
                            Point.builder().x(0.1f).y(0.1f).build(),
                            Point.builder().x(0.9f).y(0.1f).build(),
                            Point.builder().x(0.9f).y(0.5f).build(),
                            Point.builder().x(0.1f).y(0.5f).build()))
                    .build())
            .relationships(
                List.of(
                    Relationship.builder()
                        .type(RelationshipType.CHILD)
                        .ids(List.of("c1", "c2", "c3", "c4", "c5", "c6", "c7", "c8", "c9"))
                        .build()))
            .build();

    // Create key-value pair for form field
    Block keyBlock =
        Block.builder()
            .id("k1")
            .text("Invoice")
            .blockType(BlockType.KEY_VALUE_SET)
            .entityTypes(List.of(EntityType.KEY))
            .confidence(0.98f)
            .page(1)
            .geometry(
                Geometry.builder()
                    .polygon(
                        List.of(
                            Point.builder().x(0.1f).y(0.1f).build(),
                            Point.builder().x(0.3f).y(0.1f).build(),
                            Point.builder().x(0.3f).y(0.2f).build(),
                            Point.builder().x(0.1f).y(0.2f).build()))
                    .build())
            .relationships(
                List.of(
                    Relationship.builder().type(RelationshipType.CHILD).ids(List.of("t10")).build(),
                    Relationship.builder().type(RelationshipType.VALUE).ids(List.of("v1")).build()))
            .build();

    Block valueBlock =
        Block.builder()
            .id("v1")
            .blockType(BlockType.KEY_VALUE_SET)
            .entityTypes(List.of(EntityType.VALUE))
            .confidence(0.97f)
            .page(1)
            .geometry(
                Geometry.builder()
                    .polygon(
                        List.of(
                            Point.builder().x(0.4f).y(0.1f).build(),
                            Point.builder().x(0.7f).y(0.1f).build(),
                            Point.builder().x(0.7f).y(0.2f).build(),
                            Point.builder().x(0.4f).y(0.2f).build()))
                    .build())
            .relationships(
                List.of(
                    Relationship.builder()
                        .type(RelationshipType.CHILD)
                        .ids(List.of("t11"))
                        .build()))
            .build();

    Block keyTextBlock =
        Block.builder()
            .id("t10")
            .text("Invoice")
            .blockType(BlockType.WORD)
            .confidence(0.98f)
            .build();

    Block valueTextBlock =
        Block.builder()
            .id("t11")
            .text("INV-12345")
            .blockType(BlockType.WORD)
            .confidence(0.97f)
            .build();

    // Combine all blocks into one list
    List<Block> blocks = new ArrayList<>();
    blocks.addAll(
        List.of(
            nameHeaderBlock,
            ageHeaderBlock,
            locationHeaderBlock,
            johnBlock,
            age32Block,
            nyBlock,
            janeBlock,
            age28Block,
            londonBlock,
            cell1_1,
            cell1_2,
            cell1_3,
            cell2_1,
            cell2_2,
            cell2_3,
            cell3_1,
            cell3_2,
            cell3_3,
            tableBlock,
            keyBlock,
            valueBlock,
            keyTextBlock,
            valueTextBlock));

    GetDocumentAnalysisResponse getDocumentAnalysisResponse =
        GetDocumentAnalysisResponse.builder().jobStatus(JobStatus.SUCCEEDED).blocks(blocks).build();

    StartDocumentAnalysisResponse startDocumentAnalysisResponse =
        StartDocumentAnalysisResponse.builder().jobId(jobId).build();

    when(textractClient.startDocumentAnalysis(any(StartDocumentAnalysisRequest.class)))
        .thenReturn(startDocumentAnalysisResponse);

    when(textractClient.getDocumentAnalysis(any(GetDocumentAnalysisRequest.class)))
        .thenReturn(getDocumentAnalysisResponse);

    // Act
    StructuredExtractionResponse response =
        new PollingTextractCaller()
            .extractKeyValuePairsWithConfidence(
                mockedDocument, "test-bucket", textractClient, s3AsyncClient);

    // Assert
    // Check key-value pairs
    assertEquals("INV-12345", response.extractedFields().get("Invoice"));
    assertEquals(0.0097f, (Float) response.confidenceScore().get("Invoice"), 0.0001f);

    // Check table data
    assertTrue(response.extractedFields().containsKey("table 1"));
    List<List<String>> tableData = (List<List<String>>) response.extractedFields().get("table 1");

    assertEquals(3, tableData.size()); // 3 rows total

    // Check header row
    assertEquals(List.of("Name", "Age", "Location"), tableData.get(0));
    // Check data rows
    assertEquals(List.of("John Doe", "32", "New York"), tableData.get(1));
    assertEquals(List.of("Jane Smith", "28", "London"), tableData.get(2));

    // Check table confidence - now it's a List<List<Float>> for per-cell confidence scores
    assertTrue(response.confidenceScore().containsKey("table 1"));
    List<List<Float>> tableConfidenceData =
        (List<List<Float>>) response.confidenceScore().get("table 1");

    assertEquals(3, tableConfidenceData.size()); // 3 rows of confidence scores

    // Check confidence scores for header row (divided by 100 to convert to percentage)
    assertEquals(3, tableConfidenceData.get(0).size()); // 3 columns
    assertEquals(0.0095f, tableConfidenceData.get(0).get(0), 0.0001f); // "Name" cell confidence
    assertEquals(0.0094f, tableConfidenceData.get(0).get(1), 0.0001f); // "Age" cell confidence
    assertEquals(0.0093f, tableConfidenceData.get(0).get(2), 0.0001f); // "Location" cell confidence

    // Check confidence scores for first data row
    assertEquals(3, tableConfidenceData.get(1).size()); // 3 columns
    assertEquals(0.0092f, tableConfidenceData.get(1).get(0), 0.0001f); // "John Doe" cell confidence
    assertEquals(0.0091f, tableConfidenceData.get(1).get(1), 0.0001f); // "32" cell confidence
    assertEquals(0.0090f, tableConfidenceData.get(1).get(2), 0.0001f); // "New York" cell confidence

    // Check confidence scores for second data row
    assertEquals(3, tableConfidenceData.get(2).size()); // 3 columns
    assertEquals(
        0.0089f, tableConfidenceData.get(2).get(0), 0.0001f); // "Jane Smith" cell confidence
    assertEquals(0.0088f, tableConfidenceData.get(2).get(1), 0.0001f); // "28" cell confidence
    assertEquals(0.0087f, tableConfidenceData.get(2).get(2), 0.0001f); // "London" cell confidence

    // Test geometry/position extraction for key-value pairs
    assertTrue(response.geometry().containsKey("Invoice"));
    io.camunda.connector.idp.extraction.model.Polygon invoicePolygon =
        response.geometry().get("Invoice");
    assertEquals(1, invoicePolygon.getPage()); // Should be page 1

    // The polygon should combine both key and value bounding boxes
    // Expected combined bounding box: min(0.1, 0.4) = 0.1, max(0.3, 0.7) = 0.7 for X
    // Y coordinates: min(0.1, 0.1) = 0.1, max(0.2, 0.2) = 0.2
    List<io.camunda.connector.idp.extraction.model.PolygonPoint> invoicePoints =
        invoicePolygon.getPoints();
    assertEquals(4, invoicePoints.size()); // Should have 4 corner points

    // Check that the bounding rectangle encompasses both key and value regions
    boolean foundTopLeft =
        invoicePoints.stream().anyMatch(p -> p.getX() == 0.1f && p.getY() == 0.1f);
    boolean foundTopRight =
        invoicePoints.stream().anyMatch(p -> p.getX() == 0.7f && p.getY() == 0.1f);
    boolean foundBottomRight =
        invoicePoints.stream().anyMatch(p -> p.getX() == 0.7f && p.getY() == 0.2f);
    boolean foundBottomLeft =
        invoicePoints.stream().anyMatch(p -> p.getX() == 0.1f && p.getY() == 0.2f);

    assertTrue(foundTopLeft, "Should contain top-left corner (0.1, 0.1)");
    assertTrue(foundTopRight, "Should contain top-right corner (0.7, 0.1)");
    assertTrue(foundBottomRight, "Should contain bottom-right corner (0.7, 0.2)");
    assertTrue(foundBottomLeft, "Should contain bottom-left corner (0.1, 0.2)");

    // Test geometry/position extraction for tables
    assertTrue(response.geometry().containsKey("table 1"));
    io.camunda.connector.idp.extraction.model.Polygon tablePolygon =
        response.geometry().get("table 1");
    assertEquals(1, tablePolygon.getPage()); // Should be page 1

    // Verify the table polygon points are correctly extracted
    List<io.camunda.connector.idp.extraction.model.PolygonPoint> tablePoints =
        tablePolygon.getPoints();
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
  void extractKeyValuePairsWithConfidence_VerifiesPolygonCalculationForMultipleFields()
      throws Exception {
    // Arrange
    String jobId = "1";

    // Create key-value blocks with different positions to test polygon calculation
    Block nameKeyBlock =
        Block.builder()
            .id("k1")
            .text("Name")
            .blockType(BlockType.KEY_VALUE_SET)
            .entityTypes(List.of(EntityType.KEY))
            .confidence(0.95f)
            .page(2) // Use page 2 to test page number extraction
            .geometry(
                Geometry.builder()
                    .polygon(
                        List.of(
                            Point.builder().x(0.1f).y(0.1f).build(), // Top-left area
                            Point.builder().x(0.2f).y(0.1f).build(),
                            Point.builder().x(0.2f).y(0.2f).build(),
                            Point.builder().x(0.1f).y(0.2f).build()))
                    .build())
            .relationships(
                List.of(
                    Relationship.builder().type(RelationshipType.CHILD).ids(List.of("t1")).build(),
                    Relationship.builder().type(RelationshipType.VALUE).ids(List.of("v1")).build()))
            .build();

    Block nameValueBlock =
        Block.builder()
            .id("v1")
            .blockType(BlockType.KEY_VALUE_SET)
            .entityTypes(List.of(EntityType.VALUE))
            .confidence(0.90f)
            .page(2)
            .geometry(
                Geometry.builder()
                    .polygon(
                        List.of(
                            Point.builder().x(0.3f).y(0.1f).build(), // Top-right area
                            Point.builder().x(0.4f).y(0.1f).build(),
                            Point.builder().x(0.4f).y(0.2f).build(),
                            Point.builder().x(0.3f).y(0.2f).build()))
                    .build())
            .relationships(
                List.of(
                    Relationship.builder().type(RelationshipType.CHILD).ids(List.of("t2")).build()))
            .build();

    Block dateKeyBlock =
        Block.builder()
            .id("k2")
            .text("Date")
            .blockType(BlockType.KEY_VALUE_SET)
            .entityTypes(List.of(EntityType.KEY))
            .confidence(0.85f)
            .page(2)
            .geometry(
                Geometry.builder()
                    .polygon(
                        List.of(
                            Point.builder().x(0.1f).y(0.8f).build(), // Bottom-left area
                            Point.builder().x(0.2f).y(0.8f).build(),
                            Point.builder().x(0.2f).y(0.9f).build(),
                            Point.builder().x(0.1f).y(0.9f).build()))
                    .build())
            .relationships(
                List.of(
                    Relationship.builder().type(RelationshipType.CHILD).ids(List.of("t3")).build(),
                    Relationship.builder().type(RelationshipType.VALUE).ids(List.of("v2")).build()))
            .build();

    Block dateValueBlock =
        Block.builder()
            .id("v2")
            .blockType(BlockType.KEY_VALUE_SET)
            .entityTypes(List.of(EntityType.VALUE))
            .confidence(0.80f)
            .page(2)
            .geometry(
                Geometry.builder()
                    .polygon(
                        List.of(
                            Point.builder().x(0.6f).y(0.8f).build(), // Bottom-right area
                            Point.builder().x(0.9f).y(0.8f).build(),
                            Point.builder().x(0.9f).y(0.9f).build(),
                            Point.builder().x(0.6f).y(0.9f).build()))
                    .build())
            .relationships(
                List.of(
                    Relationship.builder().type(RelationshipType.CHILD).ids(List.of("t4")).build()))
            .build();

    Block nameKeyText = Block.builder().id("t1").text("Name").blockType(BlockType.WORD).build();
    Block nameValueText = Block.builder().id("t2").text("John").blockType(BlockType.WORD).build();
    Block dateKeyText = Block.builder().id("t3").text("Date").blockType(BlockType.WORD).build();
    Block dateValueText =
        Block.builder().id("t4").text("01/01/2024").blockType(BlockType.WORD).build();

    List<Block> blocks =
        List.of(
            nameKeyBlock,
            nameValueBlock,
            dateKeyBlock,
            dateValueBlock,
            nameKeyText,
            nameValueText,
            dateKeyText,
            dateValueText);

    GetDocumentAnalysisResponse getDocumentAnalysisResponse =
        GetDocumentAnalysisResponse.builder().jobStatus(JobStatus.SUCCEEDED).blocks(blocks).build();

    StartDocumentAnalysisResponse startDocumentAnalysisResponse =
        StartDocumentAnalysisResponse.builder().jobId(jobId).build();

    when(textractClient.startDocumentAnalysis(any(StartDocumentAnalysisRequest.class)))
        .thenReturn(startDocumentAnalysisResponse);

    when(textractClient.getDocumentAnalysis(any(GetDocumentAnalysisRequest.class)))
        .thenReturn(getDocumentAnalysisResponse);

    // Act
    StructuredExtractionResponse response =
        new PollingTextractCaller()
            .extractKeyValuePairsWithConfidence(
                mockedDocument, "test-bucket", textractClient, s3AsyncClient);

    // Assert
    // Verify extracted fields
    assertEquals("John", response.extractedFields().get("Name"));
    assertEquals("01/01/2024", response.extractedFields().get("Date"));

    // Test geometry extraction for Name field
    assertTrue(response.geometry().containsKey("Name"));
    io.camunda.connector.idp.extraction.model.Polygon namePolygon = response.geometry().get("Name");
    assertEquals(2, namePolygon.getPage()); // Should be page 2

    // Name field should have bounding box that encompasses both key (0.1,0.1 to 0.2,0.2)
    // and value (0.3,0.1 to 0.4,0.2) polygons, resulting in (0.1,0.1 to 0.4,0.2)
    List<io.camunda.connector.idp.extraction.model.PolygonPoint> namePoints =
        namePolygon.getPoints();
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
    io.camunda.connector.idp.extraction.model.Polygon datePolygon = response.geometry().get("Date");
    assertEquals(2, datePolygon.getPage()); // Should be page 2

    // Date field should have bounding box that encompasses both key (0.1,0.8 to 0.2,0.9)
    // and value (0.6,0.8 to 0.9,0.9) polygons, resulting in (0.1,0.8 to 0.9,0.9)
    List<io.camunda.connector.idp.extraction.model.PolygonPoint> datePoints =
        datePolygon.getPoints();
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

    // Verify confidence scores are correctly calculated (min of key and value, converted to
    // percentage)
    assertEquals(
        0.009f,
        (Float) response.confidenceScore().get("Name"),
        0.0001f); // Min of 0.95 and 0.90, divided by 100
    assertEquals(
        0.008f,
        (Float) response.confidenceScore().get("Date"),
        0.0001f); // Min of 0.85 and 0.80, divided by 100
  }

  @Test
  void callTextractTextDetectionWithPagination() throws Exception {
    String jobId = "1";
    String nextToken = "page2";

    // First request - without nextToken
    GetDocumentTextDetectionRequest getDocumentTextDetectionRequest1 =
        GetDocumentTextDetectionRequest.builder().jobId(jobId).maxResults(MAX_RESULT).build();

    // Second request - with nextToken
    GetDocumentTextDetectionRequest getDocumentTextDetectionRequest2 =
        GetDocumentTextDetectionRequest.builder()
            .jobId(jobId)
            .maxResults(MAX_RESULT)
            .nextToken(nextToken)
            .build();

    // First response - SUCCEEDED with nextToken (more pages)
    GetDocumentTextDetectionResponse getDocumentTextDetectionResponse1 =
        GetDocumentTextDetectionResponse.builder()
            .jobStatus(JobStatus.SUCCEEDED)
            .nextToken(nextToken)
            .blocks(
                List.of(
                    Block.builder().text("Page 1 Line 1").blockType(BlockType.LINE).build(),
                    Block.builder().text("Page 1 Line 2").blockType(BlockType.LINE).build()))
            .build();

    // Second response - SUCCEEDED without nextToken (last page)
    GetDocumentTextDetectionResponse getDocumentTextDetectionResponse2 =
        GetDocumentTextDetectionResponse.builder()
            .jobStatus(JobStatus.SUCCEEDED)
            .nextToken(null)
            .blocks(
                List.of(
                    Block.builder().text("Page 2 Line 1").blockType(BlockType.LINE).build(),
                    Block.builder().text("Page 2 Line 2").blockType(BlockType.LINE).build()))
            .build();

    StartDocumentTextDetectionResponse startDocumentTextDetectionResponse =
        StartDocumentTextDetectionResponse.builder().jobId(jobId).build();

    when(textractClient.startDocumentTextDetection(any(StartDocumentTextDetectionRequest.class)))
        .thenReturn(startDocumentTextDetectionResponse);

    when(textractClient.getDocumentTextDetection(getDocumentTextDetectionRequest1))
        .thenReturn(getDocumentTextDetectionResponse1);

    when(textractClient.getDocumentTextDetection(getDocumentTextDetectionRequest2))
        .thenReturn(getDocumentTextDetectionResponse2);

    String expectedExtractedText = "Page 1 Line 1\nPage 1 Line 2\nPage 2 Line 1\nPage 2 Line 2";
    String extractedText =
        new PollingTextractCaller()
            .call(mockedDocument, "test-aws-s3-bucket-name", textractClient, s3AsyncClient);

    assertThat(extractedText).isEqualTo(expectedExtractedText);

    // Verify both requests were made in order
    var inOrder = Mockito.inOrder(textractClient);
    inOrder.verify(textractClient).getDocumentTextDetection(getDocumentTextDetectionRequest1);
    inOrder.verify(textractClient).getDocumentTextDetection(getDocumentTextDetectionRequest2);
  }

  @Test
  void callTextractTextDetectionWithPaginationFailure() throws Exception {
    String jobId = "1";
    String nextToken = "page2";

    GetDocumentTextDetectionRequest getDocumentTextDetectionRequest1 =
        GetDocumentTextDetectionRequest.builder().jobId(jobId).maxResults(MAX_RESULT).build();

    GetDocumentTextDetectionRequest getDocumentTextDetectionRequest2 =
        GetDocumentTextDetectionRequest.builder()
            .jobId(jobId)
            .maxResults(MAX_RESULT)
            .nextToken(nextToken)
            .build();

    // First response - SUCCEEDED with nextToken
    GetDocumentTextDetectionResponse getDocumentTextDetectionResponse1 =
        GetDocumentTextDetectionResponse.builder()
            .jobStatus(JobStatus.SUCCEEDED)
            .nextToken(nextToken)
            .blocks(List.of(Block.builder().text("Page 1").blockType(BlockType.LINE).build()))
            .build();

    // Second response - FAILED
    GetDocumentTextDetectionResponse getDocumentTextDetectionResponse2 =
        GetDocumentTextDetectionResponse.builder()
            .jobStatus(JobStatus.FAILED)
            .statusMessage("Pagination failed at page 2")
            .build();

    StartDocumentTextDetectionResponse startDocumentTextDetectionResponse =
        StartDocumentTextDetectionResponse.builder().jobId(jobId).build();

    when(textractClient.startDocumentTextDetection(any(StartDocumentTextDetectionRequest.class)))
        .thenReturn(startDocumentTextDetectionResponse);

    when(textractClient.getDocumentTextDetection(getDocumentTextDetectionRequest1))
        .thenReturn(getDocumentTextDetectionResponse1);

    when(textractClient.getDocumentTextDetection(getDocumentTextDetectionRequest2))
        .thenReturn(getDocumentTextDetectionResponse2);

    PollingTextractCaller pollingTextractCaller = new PollingTextractCaller();

    Exception exception =
        assertThrows(
            ConnectorException.class,
            () ->
                pollingTextractCaller.call(
                    mockedDocument, "test-aws-s3-bucket-name", textractClient, s3AsyncClient));

    assertEquals("Pagination failed at page 2", exception.getMessage());
  }

  @Test
  void extractKeyValuePairsWithConfidence_WithPagination() throws Exception {
    String jobId = "1";
    String nextToken = "page2";

    // First request - without nextToken
    GetDocumentAnalysisRequest getDocumentAnalysisRequest1 =
        GetDocumentAnalysisRequest.builder().jobId(jobId).maxResults(MAX_RESULT).build();

    // Second request - with nextToken
    GetDocumentAnalysisRequest getDocumentAnalysisRequest2 =
        GetDocumentAnalysisRequest.builder()
            .jobId(jobId)
            .maxResults(MAX_RESULT)
            .nextToken(nextToken)
            .build();

    // Create blocks for first page
    Block nameKeyBlock =
        Block.builder()
            .id("k1")
            .text("Name")
            .blockType(BlockType.KEY_VALUE_SET)
            .entityTypes(List.of(EntityType.KEY))
            .confidence(0.95f)
            .page(1)
            .geometry(
                Geometry.builder()
                    .polygon(
                        List.of(
                            Point.builder().x(0.1f).y(0.1f).build(),
                            Point.builder().x(0.2f).y(0.1f).build(),
                            Point.builder().x(0.2f).y(0.2f).build(),
                            Point.builder().x(0.1f).y(0.2f).build()))
                    .build())
            .relationships(
                List.of(
                    Relationship.builder().type(RelationshipType.CHILD).ids(List.of("t1")).build(),
                    Relationship.builder().type(RelationshipType.VALUE).ids(List.of("v1")).build()))
            .build();

    Block nameValueBlock =
        Block.builder()
            .id("v1")
            .blockType(BlockType.KEY_VALUE_SET)
            .entityTypes(List.of(EntityType.VALUE))
            .confidence(0.90f)
            .page(1)
            .geometry(
                Geometry.builder()
                    .polygon(
                        List.of(
                            Point.builder().x(0.3f).y(0.1f).build(),
                            Point.builder().x(0.4f).y(0.1f).build(),
                            Point.builder().x(0.4f).y(0.2f).build(),
                            Point.builder().x(0.3f).y(0.2f).build()))
                    .build())
            .relationships(
                List.of(
                    Relationship.builder().type(RelationshipType.CHILD).ids(List.of("t2")).build()))
            .build();

    Block nameKeyText = Block.builder().id("t1").text("Name").blockType(BlockType.WORD).build();
    Block nameValueText = Block.builder().id("t2").text("John").blockType(BlockType.WORD).build();

    // Create blocks for second page
    Block ageKeyBlock =
        Block.builder()
            .id("k2")
            .text("Age")
            .blockType(BlockType.KEY_VALUE_SET)
            .entityTypes(List.of(EntityType.KEY))
            .confidence(0.85f)
            .page(2)
            .geometry(
                Geometry.builder()
                    .polygon(
                        List.of(
                            Point.builder().x(0.1f).y(0.8f).build(),
                            Point.builder().x(0.2f).y(0.8f).build(),
                            Point.builder().x(0.2f).y(0.9f).build(),
                            Point.builder().x(0.1f).y(0.9f).build()))
                    .build())
            .relationships(
                List.of(
                    Relationship.builder().type(RelationshipType.CHILD).ids(List.of("t3")).build(),
                    Relationship.builder().type(RelationshipType.VALUE).ids(List.of("v2")).build()))
            .build();

    Block ageValueBlock =
        Block.builder()
            .id("v2")
            .blockType(BlockType.KEY_VALUE_SET)
            .entityTypes(List.of(EntityType.VALUE))
            .confidence(0.80f)
            .page(2)
            .geometry(
                Geometry.builder()
                    .polygon(
                        List.of(
                            Point.builder().x(0.6f).y(0.8f).build(),
                            Point.builder().x(0.9f).y(0.8f).build(),
                            Point.builder().x(0.9f).y(0.9f).build(),
                            Point.builder().x(0.6f).y(0.9f).build()))
                    .build())
            .relationships(
                List.of(
                    Relationship.builder().type(RelationshipType.CHILD).ids(List.of("t4")).build()))
            .build();

    Block ageKeyText = Block.builder().id("t3").text("Age").blockType(BlockType.WORD).build();
    Block ageValueText = Block.builder().id("t4").text("30").blockType(BlockType.WORD).build();

    List<Block> firstPageBlocks = List.of(nameKeyBlock, nameValueBlock, nameKeyText, nameValueText);
    List<Block> secondPageBlocks = List.of(ageKeyBlock, ageValueBlock, ageKeyText, ageValueText);

    // First response - SUCCEEDED with nextToken
    GetDocumentAnalysisResponse getDocumentAnalysisResponse1 =
        GetDocumentAnalysisResponse.builder()
            .jobStatus(JobStatus.SUCCEEDED)
            .nextToken(nextToken)
            .blocks(firstPageBlocks)
            .build();

    // Second response - SUCCEEDED without nextToken (last page)
    GetDocumentAnalysisResponse getDocumentAnalysisResponse2 =
        GetDocumentAnalysisResponse.builder()
            .jobStatus(JobStatus.SUCCEEDED)
            .nextToken(null)
            .blocks(secondPageBlocks)
            .build();

    StartDocumentAnalysisResponse startDocumentAnalysisResponse =
        StartDocumentAnalysisResponse.builder().jobId(jobId).build();

    when(textractClient.startDocumentAnalysis(any(StartDocumentAnalysisRequest.class)))
        .thenReturn(startDocumentAnalysisResponse);

    when(textractClient.getDocumentAnalysis(getDocumentAnalysisRequest1))
        .thenReturn(getDocumentAnalysisResponse1);

    when(textractClient.getDocumentAnalysis(getDocumentAnalysisRequest2))
        .thenReturn(getDocumentAnalysisResponse2);

    // Act
    StructuredExtractionResponse response =
        new PollingTextractCaller()
            .extractKeyValuePairsWithConfidence(
                mockedDocument, "test-bucket", textractClient, s3AsyncClient);

    // Assert - verify fields from both pages were extracted
    assertEquals("John", response.extractedFields().get("Name"));
    assertEquals("30", response.extractedFields().get("Age"));

    // Verify confidence scores for both fields
    assertEquals(
        0.009f,
        (Float) response.confidenceScore().get("Name"),
        0.0001f); // Min of 0.95 and 0.90, divided by 100
    assertEquals(
        0.008f,
        (Float) response.confidenceScore().get("Age"),
        0.0001f); // Min of 0.85 and 0.80, divided by 100

    // Verify geometry is present for both fields
    assertTrue(response.geometry().containsKey("Name"));
    assertTrue(response.geometry().containsKey("Age"));
    assertEquals(1, response.geometry().get("Name").getPage());
    assertEquals(2, response.geometry().get("Age").getPage());

    // Verify both requests were made in order
    var inOrder = Mockito.inOrder(textractClient);
    inOrder.verify(textractClient).getDocumentAnalysis(getDocumentAnalysisRequest1);
    inOrder.verify(textractClient).getDocumentAnalysis(getDocumentAnalysisRequest2);
  }

  @Test
  void extractKeyValuePairsWithConfidence_WithPaginationFailure() throws Exception {
    String jobId = "1";
    String nextToken = "page2";

    GetDocumentAnalysisRequest getDocumentAnalysisRequest1 =
        GetDocumentAnalysisRequest.builder().jobId(jobId).maxResults(MAX_RESULT).build();

    GetDocumentAnalysisRequest getDocumentAnalysisRequest2 =
        GetDocumentAnalysisRequest.builder()
            .jobId(jobId)
            .maxResults(MAX_RESULT)
            .nextToken(nextToken)
            .build();

    // First response - SUCCEEDED with nextToken
    GetDocumentAnalysisResponse getDocumentAnalysisResponse1 =
        GetDocumentAnalysisResponse.builder()
            .jobStatus(JobStatus.SUCCEEDED)
            .nextToken(nextToken)
            .blocks(List.of())
            .build();

    // Second response - FAILED
    GetDocumentAnalysisResponse getDocumentAnalysisResponse2 =
        GetDocumentAnalysisResponse.builder()
            .jobStatus(JobStatus.FAILED)
            .statusMessage("Document analysis pagination failed")
            .build();

    StartDocumentAnalysisResponse startDocumentAnalysisResponse =
        StartDocumentAnalysisResponse.builder().jobId(jobId).build();

    when(textractClient.startDocumentAnalysis(any(StartDocumentAnalysisRequest.class)))
        .thenReturn(startDocumentAnalysisResponse);

    when(textractClient.getDocumentAnalysis(getDocumentAnalysisRequest1))
        .thenReturn(getDocumentAnalysisResponse1);

    when(textractClient.getDocumentAnalysis(getDocumentAnalysisRequest2))
        .thenReturn(getDocumentAnalysisResponse2);

    PollingTextractCaller pollingTextractCaller = new PollingTextractCaller();

    Exception exception =
        assertThrows(
            ConnectorException.class,
            () ->
                pollingTextractCaller.extractKeyValuePairsWithConfidence(
                    mockedDocument, "test-bucket", textractClient, s3AsyncClient));

    assertEquals("Document analysis pagination failed", exception.getMessage());
  }

  @Test
  void callTextractTextDetectionWithInProgressStatus() throws Exception {
    String jobId = "1";

    GetDocumentTextDetectionRequest getDocumentTextDetectionRequest =
        GetDocumentTextDetectionRequest.builder().jobId(jobId).maxResults(MAX_RESULT).build();

    // First response - IN_PROGRESS
    GetDocumentTextDetectionResponse getDocumentTextDetectionResponse1 =
        GetDocumentTextDetectionResponse.builder()
            .jobStatus(JobStatus.IN_PROGRESS)
            .blocks(List.of())
            .build();

    // Second response - SUCCEEDED
    GetDocumentTextDetectionResponse getDocumentTextDetectionResponse2 =
        GetDocumentTextDetectionResponse.builder()
            .jobStatus(JobStatus.SUCCEEDED)
            .blocks(List.of(Block.builder().text("Final result").blockType(BlockType.LINE).build()))
            .build();

    StartDocumentTextDetectionResponse startDocumentTextDetectionResponse =
        StartDocumentTextDetectionResponse.builder().jobId(jobId).build();

    when(textractClient.startDocumentTextDetection(any(StartDocumentTextDetectionRequest.class)))
        .thenReturn(startDocumentTextDetectionResponse);

    when(textractClient.getDocumentTextDetection(getDocumentTextDetectionRequest))
        .thenReturn(getDocumentTextDetectionResponse1)
        .thenReturn(getDocumentTextDetectionResponse2);

    String expectedExtractedText = "Final result";
    String extractedText =
        new PollingTextractCaller()
            .call(mockedDocument, "test-aws-s3-bucket-name", textractClient, s3AsyncClient);

    assertThat(extractedText).isEqualTo(expectedExtractedText);

    // Verify getDocumentTextDetection was called twice (once for IN_PROGRESS, once for SUCCEEDED)
    Mockito.verify(textractClient, Mockito.times(2))
        .getDocumentTextDetection(getDocumentTextDetectionRequest);
  }

  @Test
  void extractKeyValuePairsWithConfidence_WithInProgressStatus() throws Exception {
    String jobId = "1";

    GetDocumentAnalysisRequest getDocumentAnalysisRequest =
        GetDocumentAnalysisRequest.builder().jobId(jobId).maxResults(MAX_RESULT).build();

    // First response - IN_PROGRESS
    GetDocumentAnalysisResponse getDocumentAnalysisResponse1 =
        GetDocumentAnalysisResponse.builder()
            .jobStatus(JobStatus.IN_PROGRESS)
            .blocks(List.of())
            .build();

    // Second response - SUCCEEDED with some key-value data
    Block keyBlock =
        Block.builder()
            .id("k1")
            .text("Status")
            .blockType(BlockType.KEY_VALUE_SET)
            .entityTypes(List.of(EntityType.KEY))
            .confidence(0.95f)
            .page(1)
            .geometry(
                Geometry.builder()
                    .polygon(
                        List.of(
                            Point.builder().x(0.1f).y(0.1f).build(),
                            Point.builder().x(0.2f).y(0.1f).build(),
                            Point.builder().x(0.2f).y(0.2f).build(),
                            Point.builder().x(0.1f).y(0.2f).build()))
                    .build())
            .relationships(
                List.of(
                    Relationship.builder().type(RelationshipType.CHILD).ids(List.of("t1")).build(),
                    Relationship.builder().type(RelationshipType.VALUE).ids(List.of("v1")).build()))
            .build();

    Block valueBlock =
        Block.builder()
            .id("v1")
            .blockType(BlockType.KEY_VALUE_SET)
            .entityTypes(List.of(EntityType.VALUE))
            .confidence(0.90f)
            .page(1)
            .geometry(
                Geometry.builder()
                    .polygon(
                        List.of(
                            Point.builder().x(0.3f).y(0.1f).build(),
                            Point.builder().x(0.4f).y(0.1f).build(),
                            Point.builder().x(0.4f).y(0.2f).build(),
                            Point.builder().x(0.3f).y(0.2f).build()))
                    .build())
            .relationships(
                List.of(
                    Relationship.builder().type(RelationshipType.CHILD).ids(List.of("t2")).build()))
            .build();

    Block keyText = Block.builder().id("t1").text("Status").blockType(BlockType.WORD).build();
    Block valueText = Block.builder().id("t2").text("Complete").blockType(BlockType.WORD).build();

    GetDocumentAnalysisResponse getDocumentAnalysisResponse2 =
        GetDocumentAnalysisResponse.builder()
            .jobStatus(JobStatus.SUCCEEDED)
            .blocks(List.of(keyBlock, valueBlock, keyText, valueText))
            .build();

    StartDocumentAnalysisResponse startDocumentAnalysisResponse =
        StartDocumentAnalysisResponse.builder().jobId(jobId).build();

    when(textractClient.startDocumentAnalysis(any(StartDocumentAnalysisRequest.class)))
        .thenReturn(startDocumentAnalysisResponse);

    when(textractClient.getDocumentAnalysis(getDocumentAnalysisRequest))
        .thenReturn(getDocumentAnalysisResponse1)
        .thenReturn(getDocumentAnalysisResponse2);

    // Act
    StructuredExtractionResponse response =
        new PollingTextractCaller()
            .extractKeyValuePairsWithConfidence(
                mockedDocument, "test-bucket", textractClient, s3AsyncClient);

    // Assert
    assertEquals("Complete", response.extractedFields().get("Status"));
    assertEquals(0.009f, (Float) response.confidenceScore().get("Status"), 0.0001f);

    // Verify getDocumentAnalysis was called twice (once for IN_PROGRESS, once for SUCCEEDED)
    Mockito.verify(textractClient, Mockito.times(2))
        .getDocumentAnalysis(getDocumentAnalysisRequest);
  }
}
