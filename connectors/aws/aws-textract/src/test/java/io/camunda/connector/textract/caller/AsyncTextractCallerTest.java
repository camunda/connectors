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

import com.amazonaws.services.textract.AmazonTextractAsyncClient;
import com.amazonaws.services.textract.model.StartDocumentAnalysisRequest;
import com.amazonaws.services.textract.model.StartDocumentAnalysisResult;
import io.camunda.connector.textract.model.DocumentLocationType;
import io.camunda.connector.textract.model.TextractExecutionType;
import io.camunda.connector.textract.model.TextractRequestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AsyncTextractCallerTest {

  @Captor private ArgumentCaptor<StartDocumentAnalysisRequest> requestArgumentCaptor;

  @Test
  void callWithAllFields() {
    TextractRequestData requestData = prepareReqData("roleArn", "topicArna");

    AmazonTextractAsyncClient asyncClient = Mockito.mock(AmazonTextractAsyncClient.class);
    when(asyncClient.startDocumentAnalysis(any(StartDocumentAnalysisRequest.class)))
        .thenReturn(new StartDocumentAnalysisResult());

    new AsyncTextractCaller().call(requestData, asyncClient);

    verify(asyncClient).startDocumentAnalysis(requestArgumentCaptor.capture());

    final StartDocumentAnalysisRequest startDocumentAnalysisRequest =
        requestArgumentCaptor.getValue();

    assertThat(startDocumentAnalysisRequest.getFeatureTypes().size()).isEqualTo(4);
    assertThat(startDocumentAnalysisRequest.getDocumentLocation()).isNotNull();
    assertThat(startDocumentAnalysisRequest.getClientRequestToken())
        .isEqualTo(requestData.clientRequestToken());
    assertThat(startDocumentAnalysisRequest.getJobTag()).isEqualTo(requestData.jobTag());
    assertThat(startDocumentAnalysisRequest.getKMSKeyId()).isEqualTo(requestData.kmsKeyId());
    assertThat(startDocumentAnalysisRequest.getNotificationChannel()).isNotNull();
    assertThat(startDocumentAnalysisRequest.getNotificationChannel().getRoleArn())
        .isEqualTo(requestData.notificationChannelRoleArn());
    assertThat(startDocumentAnalysisRequest.getNotificationChannel().getSNSTopicArn())
        .isEqualTo(requestData.notificationChannelSnsTopicArn());
    assertThat(startDocumentAnalysisRequest.getOutputConfig()).isNotNull();
    assertThat(startDocumentAnalysisRequest.getOutputConfig().getS3Bucket())
        .isEqualTo(requestData.outputConfigS3Bucket());
    assertThat(startDocumentAnalysisRequest.getOutputConfig().getS3Prefix())
        .isEqualTo(requestData.outputConfigS3Prefix());
  }

  @Test
  void callWithoutNotificationChanelFieldsShouldNotCreateNotificationObj() {
    TextractRequestData requestData = prepareReqData("", "");

    AmazonTextractAsyncClient asyncClient = Mockito.mock(AmazonTextractAsyncClient.class);
    when(asyncClient.startDocumentAnalysis(any(StartDocumentAnalysisRequest.class)))
        .thenReturn(new StartDocumentAnalysisResult());

    new AsyncTextractCaller().call(requestData, asyncClient);

    verify(asyncClient).startDocumentAnalysis(requestArgumentCaptor.capture());

    final StartDocumentAnalysisRequest startDocumentAnalysisRequest =
        requestArgumentCaptor.getValue();

    assertThat(startDocumentAnalysisRequest.getNotificationChannel()).isNull();
  }

  @Test
  void callWithoutOutputS3BucketShouldNotCreateOutputObj() {
    TextractRequestData requestData = prepareReqDataWithoutOutputS3Bucket();

    AmazonTextractAsyncClient asyncClient = Mockito.mock(AmazonTextractAsyncClient.class);
    when(asyncClient.startDocumentAnalysis(any(StartDocumentAnalysisRequest.class)))
        .thenReturn(new StartDocumentAnalysisResult());

    new AsyncTextractCaller().call(requestData, asyncClient);

    verify(asyncClient).startDocumentAnalysis(requestArgumentCaptor.capture());

    final StartDocumentAnalysisRequest startDocumentAnalysisRequest =
        requestArgumentCaptor.getValue();

    assertThat(startDocumentAnalysisRequest.getOutputConfig()).isNull();
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
