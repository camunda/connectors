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
}
