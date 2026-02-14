/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.comprehend.caller;

import static io.camunda.connector.comprehend.caller.ComprehendCaller.READ_ACTION_WITHOUT_FEATURES_EX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.comprehend.model.ComprehendAsyncRequestData;
import io.camunda.connector.comprehend.model.ComprehendDocumentReadAction;
import io.camunda.connector.comprehend.model.ComprehendDocumentReadMode;
import io.camunda.connector.comprehend.model.ComprehendInputFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import software.amazon.awssdk.services.comprehend.ComprehendAsyncClient;
import software.amazon.awssdk.services.comprehend.model.DocumentReadFeatureTypes;
import software.amazon.awssdk.services.comprehend.model.DocumentReaderConfig;
import software.amazon.awssdk.services.comprehend.model.InputDataConfig;
import software.amazon.awssdk.services.comprehend.model.OutputDataConfig;
import software.amazon.awssdk.services.comprehend.model.StartDocumentClassificationJobRequest;
import software.amazon.awssdk.services.comprehend.model.Tag;
import software.amazon.awssdk.services.comprehend.model.VpcConfig;

class AsyncComprehendCallerTest {

  private final AsyncComprehendCaller asyncCaller = new AsyncComprehendCaller();

  @Test
  void callWithAllFields() {
    var asyncRequest =
        prepareAsyncRequest(
            ComprehendDocumentReadMode.SERVICE_DEFAULT,
            ComprehendDocumentReadAction.TEXTRACT_ANALYZE_DOCUMENT,
            true,
            true);

    var docClassificationRequest =
        StartDocumentClassificationJobRequest.builder()
            .clientRequestToken(asyncRequest.clientRequestToken())
            .dataAccessRoleArn(asyncRequest.dataAccessRoleArn())
            .documentClassifierArn(asyncRequest.documentClassifierArn())
            .flywheelArn(asyncRequest.flywheelArn())
            .inputDataConfig(
                InputDataConfig.builder()
                    .s3Uri(asyncRequest.inputS3Uri())
                    .documentReaderConfig(
                        DocumentReaderConfig.builder()
                            .documentReadAction(asyncRequest.documentReadAction().name())
                            .documentReadMode(asyncRequest.documentReadMode().name())
                            .featureTypes(
                                List.of(
                                    DocumentReadFeatureTypes.FORMS,
                                    DocumentReadFeatureTypes.TABLES))
                            .build())
                    .inputFormat(asyncRequest.comprehendInputFormat().name())
                    .build())
            .jobName(asyncRequest.jobName())
            .outputDataConfig(
                OutputDataConfig.builder()
                    .s3Uri(asyncRequest.outputS3Uri())
                    .kmsKeyId(asyncRequest.outputKmsKeyId())
                    .build())
            .tags(
                List.of(
                    Tag.builder()
                        .key(asyncRequest.tags().keySet().stream().findFirst().get())
                        .value(asyncRequest.tags().values().stream().findFirst().get())
                        .build()))
            .volumeKmsKeyId(asyncRequest.volumeKmsKeyId())
            .vpcConfig(
                VpcConfig.builder()
                    .securityGroupIds(asyncRequest.securityGroupIds())
                    .subnets(asyncRequest.subnets())
                    .build())
            .build();
    var client = Mockito.mock(ComprehendAsyncClient.class);

    when(client.startDocumentClassificationJob(docClassificationRequest))
        .thenReturn(CompletableFuture.completedFuture(null));
    asyncCaller.call(client, asyncRequest);

    verify(client).startDocumentClassificationJob(docClassificationRequest);
  }

  @Test
  void callWithoutOutputDataConfig() {
    var asyncRequest =
        prepareAsyncRequest(
            ComprehendDocumentReadMode.NO_DATA, ComprehendDocumentReadAction.NO_DATA, false, false);

    var docClassificationRequest =
        StartDocumentClassificationJobRequest.builder()
            .clientRequestToken(asyncRequest.clientRequestToken())
            .dataAccessRoleArn(asyncRequest.dataAccessRoleArn())
            .documentClassifierArn(asyncRequest.documentClassifierArn())
            .flywheelArn(asyncRequest.flywheelArn())
            .inputDataConfig(
                InputDataConfig.builder()
                    .s3Uri(asyncRequest.inputS3Uri())
                    .inputFormat(asyncRequest.comprehendInputFormat().name())
                    .build())
            .jobName(asyncRequest.jobName())
            .outputDataConfig(
                OutputDataConfig.builder()
                    .s3Uri(asyncRequest.outputS3Uri())
                    .kmsKeyId(asyncRequest.outputKmsKeyId())
                    .build())
            .tags(
                List.of(
                    Tag.builder()
                        .key(asyncRequest.tags().keySet().stream().findFirst().get())
                        .value(asyncRequest.tags().values().stream().findFirst().get())
                        .build()))
            .volumeKmsKeyId(asyncRequest.volumeKmsKeyId())
            .vpcConfig(
                VpcConfig.builder()
                    .securityGroupIds(asyncRequest.securityGroupIds())
                    .subnets(asyncRequest.subnets())
                    .build())
            .build();
    var client = Mockito.mock(ComprehendAsyncClient.class);

    when(client.startDocumentClassificationJob(docClassificationRequest))
        .thenReturn(CompletableFuture.completedFuture(null));
    asyncCaller.call(client, asyncRequest);

    verify(client).startDocumentClassificationJob(docClassificationRequest);
  }

  @Test
  void callWithMandatoryFields() {
    var asyncRequest =
        new ComprehendAsyncRequestData(
            ComprehendDocumentReadMode.NO_DATA,
            ComprehendDocumentReadAction.NO_DATA,
            false,
            false,
            "input",
            ComprehendInputFormat.NO_DATA,
            "",
            "roleArn",
            "classArn",
            "",
            "",
            "output",
            "",
            Map.of(),
            "",
            List.of(),
            List.of());

    var docClassificationRequest =
        StartDocumentClassificationJobRequest.builder()
            .dataAccessRoleArn(asyncRequest.dataAccessRoleArn())
            .documentClassifierArn(asyncRequest.documentClassifierArn())
            .inputDataConfig(InputDataConfig.builder().s3Uri(asyncRequest.inputS3Uri()).build())
            .outputDataConfig(OutputDataConfig.builder().s3Uri(asyncRequest.outputS3Uri()).build())
            .build();

    var client = Mockito.mock(ComprehendAsyncClient.class);
    when(client.startDocumentClassificationJob(docClassificationRequest))
        .thenReturn(CompletableFuture.completedFuture(null));
    asyncCaller.call(client, asyncRequest);

    verify(client).startDocumentClassificationJob(docClassificationRequest);
  }

  @Test
  void callWithTextractAnalyzeDocActionWithoutSelectedFeaturesShouldThrowEx() {
    var asyncRequest =
        prepareAsyncRequest(
            ComprehendDocumentReadMode.SERVICE_DEFAULT,
            ComprehendDocumentReadAction.TEXTRACT_ANALYZE_DOCUMENT,
            false,
            false);
    var client = Mockito.mock(ComprehendAsyncClient.class);

    Exception ex =
        assertThrows(IllegalArgumentException.class, () -> asyncCaller.call(client, asyncRequest));
    assertThat(ex.getMessage()).isEqualTo(READ_ACTION_WITHOUT_FEATURES_EX);
  }

  @Test
  void callWithPartiallyFilledVPCShouldThrowEx() {
    var asyncRequest =
        new ComprehendAsyncRequestData(
            ComprehendDocumentReadMode.NO_DATA,
            ComprehendDocumentReadAction.NO_DATA,
            false,
            false,
            "input",
            ComprehendInputFormat.NO_DATA,
            "",
            "roleArn",
            "",
            "",
            "",
            "output",
            "",
            Map.of(),
            "",
            List.of("seg-1"),
            List.of());

    ComprehendAsyncClient asyncClient = Mockito.mock(ComprehendAsyncClient.class);

    Exception ex =
        assertThrows(
            IllegalArgumentException.class, () -> asyncCaller.call(asyncClient, asyncRequest));
    assertThat(ex.getMessage()).isEqualTo(AsyncComprehendCaller.VPC_CONFIG_EXCEPTION_MSG);
  }

  private ComprehendAsyncRequestData prepareAsyncRequest(
      ComprehendDocumentReadMode readMode,
      ComprehendDocumentReadAction readAction,
      boolean tables,
      boolean forms) {
    return new ComprehendAsyncRequestData(
        readMode,
        readAction,
        tables,
        forms,
        "input",
        ComprehendInputFormat.ONE_DOC_PER_FILE,
        "token",
        "roleArn",
        "classifierArn",
        "flywheelArn",
        "jobName",
        "output",
        "outputKms",
        Map.of("key", "val"),
        "volumeKms",
        List.of("seg-1"),
        List.of("subnet-1"));
  }
}
