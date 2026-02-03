/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.textract.caller;

import io.camunda.connector.textract.model.TextractRequestData;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.NotificationChannel;
import software.amazon.awssdk.services.textract.model.OutputConfig;
import software.amazon.awssdk.services.textract.model.StartDocumentAnalysisRequest;
import software.amazon.awssdk.services.textract.model.StartDocumentAnalysisResponse;

public class AsyncTextractCaller implements TextractCaller<StartDocumentAnalysisResponse> {

  private static final Logger LOGGER = LoggerFactory.getLogger(AsyncTextractCaller.class);

  @Override
  public StartDocumentAnalysisResponse call(
      TextractRequestData requestData, TextractClient textractClient) {
    LOGGER.debug("Starting async task for document analysis with request data: {}", requestData);
    final StartDocumentAnalysisRequest startDocumentAnalysisRequest =
        StartDocumentAnalysisRequest.builder()
            .featureTypes(prepareFeatureTypes(requestData))
            .documentLocation(prepareDocumentLocation(requestData))
            .queriesConfig(prepareQueryConfig(requestData))
            .clientRequestToken(requestData.clientRequestToken())
            .jobTag(requestData.jobTag())
            .kmsKeyId(requestData.kmsKeyId())
        .build();

    prepareNotification(startDocumentAnalysisRequest, requestData);
    prepareOutput(startDocumentAnalysisRequest, requestData);

    return textractClient.startDocumentAnalysis(startDocumentAnalysisRequest);
  }

  private void prepareNotification(
      StartDocumentAnalysisRequest startDocumentAnalysisRequest, TextractRequestData requestData) {
    String roleArn = requestData.notificationChannelRoleArn();
    String snsTopic = requestData.notificationChannelSnsTopicArn();
    if (StringUtils.isNoneBlank(roleArn, snsTopic)) {
      LOGGER.debug("Notification data roleArn: {}, snsTopic: {}", roleArn, snsTopic);
      NotificationChannel notificationChannel =
          NotificationChannel.builder().snsTopicArn(snsTopic).roleArn(roleArn)
          .build();
      startDocumentAnalysisRequest.notificationChannel(notificationChannel);
    }
  }

  private void prepareOutput(
      StartDocumentAnalysisRequest startDocumentAnalysisRequest, TextractRequestData requestData) {
    String s3Bucket = requestData.outputConfigS3Bucket();
    String s3Prefix = requestData.outputConfigS3Prefix();
    if (StringUtils.isNoneBlank(s3Bucket)) {
      LOGGER.debug("Output data s3Bucket: {}, s3Prefix: {} ", s3Bucket, s3Prefix);
      OutputConfig outputConfig = OutputConfig.builder().s3Bucket(s3Bucket).s3Prefix(s3Prefix)
          .build();
      startDocumentAnalysisRequest.outputConfig(outputConfig);
    }
  }
}
