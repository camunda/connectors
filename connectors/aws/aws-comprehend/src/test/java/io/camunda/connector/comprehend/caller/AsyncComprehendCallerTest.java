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

import com.amazonaws.services.comprehend.AmazonComprehendClient;
import com.amazonaws.services.comprehend.model.*;
import io.camunda.connector.comprehend.model.ComprehendAsyncRequestData;
import io.camunda.connector.comprehend.model.ComprehendDocumentReadAction;
import io.camunda.connector.comprehend.model.ComprehendDocumentReadMode;
import io.camunda.connector.comprehend.model.ComprehendInputFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

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
        new StartDocumentClassificationJobRequest()
            .withClientRequestToken(asyncRequest.clientRequestToken())
            .withDataAccessRoleArn(asyncRequest.dataAccessRoleArn())
            .withDocumentClassifierArn(asyncRequest.documentClassifierArn())
            .withFlywheelArn(asyncRequest.flywheelArn())
            .withInputDataConfig(
                new InputDataConfig()
                    .withS3Uri(asyncRequest.inputS3Uri())
                    .withDocumentReaderConfig(
                        new DocumentReaderConfig()
                            .withDocumentReadAction(asyncRequest.documentReadAction().name())
                            .withDocumentReadMode(asyncRequest.documentReadMode().name())
                            .withFeatureTypes(
                                List.of(
                                    DocumentReadFeatureTypes.FORMS.name(),
                                    DocumentReadFeatureTypes.TABLES.name())))
                    .withInputFormat(asyncRequest.comprehendInputFormat().name()))
            .withJobName(asyncRequest.jobName())
            .withOutputDataConfig(
                new OutputDataConfig()
                    .withS3Uri(asyncRequest.outputS3Uri())
                    .withKmsKeyId(asyncRequest.outputKmsKeyId()))
            .withTags(
                List.of(
                    new Tag()
                        .withKey(asyncRequest.tags().keySet().stream().findFirst().get())
                        .withValue(asyncRequest.tags().values().stream().findFirst().get())))
            .withVolumeKmsKeyId(asyncRequest.volumeKmsKeyId())
            .withVpcConfig(
                new VpcConfig()
                    .withSecurityGroupIds(asyncRequest.securityGroupIds())
                    .withSubnets(asyncRequest.subnets()));
    var client = Mockito.mock(AmazonComprehendClient.class);

    when(client.startDocumentClassificationJob(docClassificationRequest)).thenReturn(null);
    asyncCaller.call(client, asyncRequest);

    verify(client).startDocumentClassificationJob(docClassificationRequest);
  }

  @Test
  void callWithoutOutputDataConfig() {
    var asyncRequest =
        prepareAsyncRequest(
            ComprehendDocumentReadMode.NO_DATA, ComprehendDocumentReadAction.NO_DATA, false, false);

    var docClassificationRequest =
        new StartDocumentClassificationJobRequest()
            .withClientRequestToken(asyncRequest.clientRequestToken())
            .withDataAccessRoleArn(asyncRequest.dataAccessRoleArn())
            .withDocumentClassifierArn(asyncRequest.documentClassifierArn())
            .withFlywheelArn(asyncRequest.flywheelArn())
            .withInputDataConfig(
                new InputDataConfig()
                    .withS3Uri(asyncRequest.inputS3Uri())
                    .withInputFormat(asyncRequest.comprehendInputFormat().name()))
            .withJobName(asyncRequest.jobName())
            .withOutputDataConfig(
                new OutputDataConfig()
                    .withS3Uri(asyncRequest.outputS3Uri())
                    .withKmsKeyId(asyncRequest.outputKmsKeyId()))
            .withTags(
                List.of(
                    new Tag()
                        .withKey(asyncRequest.tags().keySet().stream().findFirst().get())
                        .withValue(asyncRequest.tags().values().stream().findFirst().get())))
            .withVolumeKmsKeyId(asyncRequest.volumeKmsKeyId())
            .withVpcConfig(
                new VpcConfig()
                    .withSecurityGroupIds(asyncRequest.securityGroupIds())
                    .withSubnets(asyncRequest.subnets()));
    var client = Mockito.mock(AmazonComprehendClient.class);

    when(client.startDocumentClassificationJob(docClassificationRequest)).thenReturn(null);
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
        new StartDocumentClassificationJobRequest()
            .withDataAccessRoleArn(asyncRequest.dataAccessRoleArn())
            .withDocumentClassifierArn(asyncRequest.documentClassifierArn())
            .withInputDataConfig(new InputDataConfig().withS3Uri(asyncRequest.inputS3Uri()))
            .withOutputDataConfig(new OutputDataConfig().withS3Uri(asyncRequest.outputS3Uri()));

    var client = Mockito.mock(AmazonComprehendClient.class);
    when(client.startDocumentClassificationJob(docClassificationRequest)).thenReturn(null);
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
    var client = Mockito.mock(AmazonComprehendClient.class);

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

    AmazonComprehendClient asyncClient = Mockito.mock(AmazonComprehendClient.class);

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
