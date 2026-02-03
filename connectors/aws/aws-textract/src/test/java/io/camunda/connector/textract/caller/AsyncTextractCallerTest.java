/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.textract.caller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.textract.model.DocumentLocationType;
import io.camunda.connector.textract.model.TextractExecutionType;
import io.camunda.connector.textract.model.TextractRequestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.textract.TextractAsyncClient;
import software.amazon.awssdk.services.textract.model.StartDocumentAnalysisRequest;
import software.amazon.awssdk.services.textract.model.StartDocumentAnalysisResponse;

@ExtendWith(MockitoExtension.class)
class AsyncTextractCallerTest {

  @Captor private ArgumentCaptor<StartDocumentAnalysisRequest> requestArgumentCaptor;

  @Test
  void callWithAllFields() {
    TextractRequestData requestData = prepareReqData("roleArn", "topicArna");

    TextractAsyncClient asyncClient = Mockito.mock(TextractAsyncClient.class);
    when(asyncClient.startDocumentAnalysis(any(StartDocumentAnalysisRequest.class)))
        .thenReturn(StartDocumentAnalysisResponse.builder()
        .build());

    new AsyncTextractCaller().call(requestData, asyncClient);

    verify(asyncClient).startDocumentAnalysis(requestArgumentCaptor.capture());

    final StartDocumentAnalysisRequest startDocumentAnalysisRequest =
        requestArgumentCaptor.getValue();

    assertThat(startDocumentAnalysisRequest.featureTypes().size()).isEqualTo(4);
    assertThat(startDocumentAnalysisRequest.documentLocation()).isNotNull();
    assertThat(startDocumentAnalysisRequest.clientRequestToken())
        .isEqualTo(requestData.clientRequestToken());
    assertThat(startDocumentAnalysisRequest.jobTag()).isEqualTo(requestData.jobTag());
    assertThat(startDocumentAnalysisRequest.kmsKeyId()).isEqualTo(requestData.kmsKeyId());
    assertThat(startDocumentAnalysisRequest.notificationChannel()).isNotNull();
    assertThat(startDocumentAnalysisRequest.notificationChannel().roleArn())
        .isEqualTo(requestData.notificationChannelRoleArn());
    assertThat(startDocumentAnalysisRequest.notificationChannel().snsTopicArn())
        .isEqualTo(requestData.notificationChannelSnsTopicArn());
    assertThat(startDocumentAnalysisRequest.outputConfig()).isNotNull();
    assertThat(startDocumentAnalysisRequest.outputConfig().s3Bucket())
        .isEqualTo(requestData.outputConfigS3Bucket());
    assertThat(startDocumentAnalysisRequest.outputConfig().s3Prefix())
        .isEqualTo(requestData.outputConfigS3Prefix());
  }

  @Test
  void callWithoutNotificationChanelFieldsShouldNotCreateNotificationObj() {
    TextractRequestData requestData = prepareReqData("", "");

    TextractAsyncClient asyncClient = Mockito.mock(TextractAsyncClient.class);
    when(asyncClient.startDocumentAnalysis(any(StartDocumentAnalysisRequest.class)))
        .thenReturn(StartDocumentAnalysisResponse.builder()
        .build());

    new AsyncTextractCaller().call(requestData, asyncClient);

    verify(asyncClient).startDocumentAnalysis(requestArgumentCaptor.capture());

    final StartDocumentAnalysisRequest startDocumentAnalysisRequest =
        requestArgumentCaptor.getValue();

    assertThat(startDocumentAnalysisRequest.notificationChannel()).isNull();
  }

  @Test
  void callWithoutOutputS3BucketShouldNotCreateOutputObj() {
    TextractRequestData requestData = prepareReqDataWithoutOutputS3Bucket();

    TextractAsyncClient asyncClient = Mockito.mock(TextractAsyncClient.class);
    when(asyncClient.startDocumentAnalysis(any(StartDocumentAnalysisRequest.class)))
        .thenReturn(StartDocumentAnalysisResponse.builder()
        .build());

    new AsyncTextractCaller().call(requestData, asyncClient);

    verify(asyncClient).startDocumentAnalysis(requestArgumentCaptor.capture());

    final StartDocumentAnalysisRequest startDocumentAnalysisRequest =
        requestArgumentCaptor.getValue();

    assertThat(startDocumentAnalysisRequest.outputConfig()).isNull();
  }

  private TextractRequestData prepareReqData(String roleArn, String topicArn) {
    return new TextractRequestData(
        DocumentLocationType.S3,
        "test-bucket",
        "test-object",
        "1",
        null,
        TextractExecutionType.ASYNC,
        true,
        true,
        true,
        true,
        false,
        "",
        "token",
        "jobTag",
        "kmsId",
        roleArn,
        topicArn,
        "outputBucket",
        "prefix");
  }

  private TextractRequestData prepareReqDataWithoutOutputS3Bucket() {
    return new TextractRequestData(
        DocumentLocationType.S3,
        "test-bucket",
        "test-object",
        "1",
        null,
        TextractExecutionType.ASYNC,
        true,
        true,
        true,
        true,
        false,
        "",
        "token",
        "jobTag",
        "kmsId",
        "roleArn",
        "topicArn",
        "",
        "prefix");
  }
}
