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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.idp.extraction.utils.AwsS3Util;
import io.camunda.document.Document;
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
  void callTextractDocumentAnalysisWithSuccess() throws Exception {
    String jobId = "1";
    GetDocumentAnalysisRequest getDocumentAnalysisRequest =
        GetDocumentAnalysisRequest.builder().jobId(jobId).maxResults(MAX_RESULT).build();

    GetDocumentAnalysisResponse getDocumentAnalysisResponse =
        GetDocumentAnalysisResponse.builder()
            .jobStatus(JobStatus.SUCCEEDED)
            .blocks(
                List.of(
                    Block.builder().text("AAA").blockType(BlockType.LINE).build(),
                    Block.builder().text("BBB").blockType(BlockType.LINE).build()))
            .build();

    StartDocumentAnalysisResponse startDocumentAnalysisResponse =
        StartDocumentAnalysisResponse.builder().jobId(jobId).build();

    when(textractClient.startDocumentAnalysis(any(StartDocumentAnalysisRequest.class)))
        .thenReturn(startDocumentAnalysisResponse);

    when(textractClient.getDocumentAnalysis(getDocumentAnalysisRequest))
        .thenReturn(getDocumentAnalysisResponse);

    String expectedExtractedText = "AAA\nBBB";
    String extractedText =
        new PollingTextractCaller()
            .call(mockedDocument, "test-aws-s3-bucket-name", textractClient, s3AsyncClient);

    assertThat(extractedText).isEqualTo(expectedExtractedText);
  }

  @Test
  void callTextractDocumentAnalysisWithEmptyResult() throws Exception {
    String jobId = "1";
    GetDocumentAnalysisRequest getDocumentAnalysisRequest =
        GetDocumentAnalysisRequest.builder().jobId(jobId).maxResults(MAX_RESULT).build();

    GetDocumentAnalysisResponse getDocumentAnalysisResponse =
        GetDocumentAnalysisResponse.builder()
            .jobStatus(JobStatus.SUCCEEDED)
            .blocks(List.of())
            .build();

    StartDocumentAnalysisResponse startDocumentAnalysisResponse =
        StartDocumentAnalysisResponse.builder().jobId(jobId).build();

    when(textractClient.startDocumentAnalysis(any(StartDocumentAnalysisRequest.class)))
        .thenReturn(startDocumentAnalysisResponse);

    when(textractClient.getDocumentAnalysis(getDocumentAnalysisRequest))
        .thenReturn(getDocumentAnalysisResponse);

    String expectedExtractedText = "";
    String extractedText =
        new PollingTextractCaller()
            .call(mockedDocument, "test-aws-s3-bucket-name", textractClient, s3AsyncClient);

    assertThat(extractedText).isEqualTo(expectedExtractedText);
  }

  @Test
  void callTextractDocumentAnalysisWithFailure() {
    String jobId = "1";
    GetDocumentAnalysisRequest getDocumentAnalysisRequest =
        GetDocumentAnalysisRequest.builder().jobId(jobId).maxResults(MAX_RESULT).build();

    GetDocumentAnalysisResponse getDocumentAnalysisResponse =
        GetDocumentAnalysisResponse.builder()
            .jobStatus(JobStatus.FAILED)
            .statusMessage("Test exception message")
            .build();

    StartDocumentAnalysisResponse startDocumentAnalysisResponse =
        StartDocumentAnalysisResponse.builder().jobId(jobId).build();

    when(textractClient.startDocumentAnalysis(any(StartDocumentAnalysisRequest.class)))
        .thenReturn(startDocumentAnalysisResponse);

    when(textractClient.getDocumentAnalysis(getDocumentAnalysisRequest))
        .thenReturn(getDocumentAnalysisResponse);

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
  void extractKeyValuePairsWithConfidenceShouldProcessTableContent() throws Exception {
    String jobId = "1";
    GetDocumentAnalysisRequest getDocumentAnalysisRequest =
        GetDocumentAnalysisRequest.builder().jobId(jobId).maxResults(MAX_RESULT).build();

    // Create table blocks
    Block tableBlock =
        Block.builder()
            .id("T1")
            .blockType(BlockType.TABLE)
            .entityTypes(List.of(EntityType.STRUCTURED_TABLE))
            .relationships(
                List.of(
                    Relationship.builder()
                        .type(RelationshipType.CHILD)
                        .ids(List.of("C1", "C2", "C3", "C4", "C5", "C6"))
                        .build()))
            .build();

    // Header cells
    Block headerCell1 =
        Block.builder()
            .id("C1")
            .blockType(BlockType.CELL)
            .rowIndex(1)
            .columnIndex(1)
            .confidence(95.0f)
            .relationships(
                List.of(
                    Relationship.builder().type(RelationshipType.CHILD).ids(List.of("W1")).build()))
            .build();

    Block headerCell2 =
        Block.builder()
            .id("C2")
            .blockType(BlockType.CELL)
            .rowIndex(1)
            .columnIndex(2)
            .confidence(95.0f)
            .relationships(
                List.of(
                    Relationship.builder().type(RelationshipType.CHILD).ids(List.of("W2")).build()))
            .build();

    // Data cells
    Block dataCell1 =
        Block.builder()
            .id("C3")
            .blockType(BlockType.CELL)
            .rowIndex(2)
            .columnIndex(1)
            .confidence(90.0f)
            .relationships(
                List.of(
                    Relationship.builder().type(RelationshipType.CHILD).ids(List.of("W3")).build()))
            .build();

    Block dataCell2 =
        Block.builder()
            .id("C4")
            .blockType(BlockType.CELL)
            .rowIndex(2)
            .columnIndex(2)
            .confidence(90.0f)
            .relationships(
                List.of(
                    Relationship.builder().type(RelationshipType.CHILD).ids(List.of("W4")).build()))
            .build();

    Block dataCell3 =
        Block.builder()
            .id("C5")
            .blockType(BlockType.CELL)
            .rowIndex(3)
            .columnIndex(1)
            .confidence(90.0f)
            .relationships(
                List.of(
                    Relationship.builder().type(RelationshipType.CHILD).ids(List.of("W5")).build()))
            .build();

    Block dataCell4 =
        Block.builder()
            .id("C6")
            .blockType(BlockType.CELL)
            .rowIndex(3)
            .columnIndex(2)
            .confidence(90.0f)
            .relationships(
                List.of(
                    Relationship.builder().type(RelationshipType.CHILD).ids(List.of("W6")).build()))
            .build();

    // Text blocks
    Block text1 = Block.builder().id("W1").text("Name").blockType(BlockType.WORD).build();
    Block text2 = Block.builder().id("W2").text("Value").blockType(BlockType.WORD).build();
    Block text3 = Block.builder().id("W3").text("Item1").blockType(BlockType.WORD).build();
    Block text4 = Block.builder().id("W4").text("100").blockType(BlockType.WORD).build();
    Block text5 = Block.builder().id("W5").text("Item2").blockType(BlockType.WORD).build();
    Block text6 = Block.builder().id("W6").text("200").blockType(BlockType.WORD).build();

    GetDocumentAnalysisResponse getDocumentAnalysisResponse =
        GetDocumentAnalysisResponse.builder()
            .jobStatus(JobStatus.SUCCEEDED)
            .blocks(
                List.of(
                    tableBlock,
                    headerCell1,
                    headerCell2,
                    dataCell1,
                    dataCell2,
                    dataCell3,
                    dataCell4,
                    text1,
                    text2,
                    text3,
                    text4,
                    text5,
                    text6))
            .build();

    StartDocumentAnalysisResponse startDocumentAnalysisResponse =
        StartDocumentAnalysisResponse.builder().jobId(jobId).build();

    when(textractClient.startDocumentAnalysis(any(StartDocumentAnalysisRequest.class)))
        .thenReturn(startDocumentAnalysisResponse);

    when(textractClient.getDocumentAnalysis(getDocumentAnalysisRequest))
        .thenReturn(getDocumentAnalysisResponse);

    var response =
        new PollingTextractCaller()
            .extractKeyValuePairsWithConfidence(
                mockedDocument, "test-aws-s3-bucket-name", textractClient, s3AsyncClient);

    // Verify table data extraction
    assertThat(response.extractedFields()).containsEntry("table Name 1", "Item1");
    assertThat(response.extractedFields()).containsEntry("table Name 2", "Item2");
    assertThat(response.extractedFields()).containsEntry("table Value 1", "100");
    assertThat(response.extractedFields()).containsEntry("table Value 2", "200");

    // Verify confidence scores
    assertThat(response.confidenceScore().get("table Name 1")).isEqualTo(0.9f);
    assertThat(response.confidenceScore().get("table Value 1")).isEqualTo(0.9f);
  }
}
