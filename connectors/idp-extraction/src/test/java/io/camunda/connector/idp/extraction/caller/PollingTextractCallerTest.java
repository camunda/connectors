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

import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.idp.extraction.model.StructuredExtractionResponse;
import io.camunda.connector.idp.extraction.utils.AwsS3Util;
import io.camunda.document.Document;
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
  }
}
