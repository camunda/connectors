/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.textract.caller;

import com.amazonaws.services.textract.AmazonTextract;
import com.amazonaws.services.textract.model.NotificationChannel;
import com.amazonaws.services.textract.model.OutputConfig;
import com.amazonaws.services.textract.model.StartDocumentAnalysisRequest;
import com.amazonaws.services.textract.model.StartDocumentAnalysisResult;
import io.camunda.connector.textract.model.TextractRequestData;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AsyncTextractCaller implements TextractCaller<StartDocumentAnalysisResult> {

  private static final Logger LOGGER = LoggerFactory.getLogger(AsyncTextractCaller.class);

  @Override
  public StartDocumentAnalysisResult call(
      TextractRequestData requestData, AmazonTextract textractClient) {
    LOGGER.debug("Starting async task for document analysis with request data: {}", requestData);
    final StartDocumentAnalysisRequest startDocumentAnalysisRequest =
        new StartDocumentAnalysisRequest()
            .withFeatureTypes(prepareFeatureTypes(requestData))
            .withDocumentLocation(prepareDocumentLocation(requestData))
            .withQueriesConfig(prepareQueryConfig(requestData))
            .withClientRequestToken(requestData.clientRequestToken())
            .withJobTag(requestData.jobTag())
            .withKMSKeyId(requestData.kmsKeyId());

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
          new NotificationChannel().withSNSTopicArn(snsTopic).withRoleArn(roleArn);
      startDocumentAnalysisRequest.withNotificationChannel(notificationChannel);
    }
  }

  private void prepareOutput(
      StartDocumentAnalysisRequest startDocumentAnalysisRequest, TextractRequestData requestData) {
    String s3Bucket = requestData.outputConfigS3Bucket();
    String s3Prefix = requestData.outputConfigS3Prefix();
    if (StringUtils.isNoneBlank(s3Bucket)) {
      LOGGER.debug("Output data s3Bucket: {}, s3Prefix: {} ", s3Bucket, s3Prefix);
      OutputConfig outputConfig = new OutputConfig().withS3Bucket(s3Bucket).withS3Prefix(s3Prefix);
      startDocumentAnalysisRequest.withOutputConfig(outputConfig);
    }
  }
}
