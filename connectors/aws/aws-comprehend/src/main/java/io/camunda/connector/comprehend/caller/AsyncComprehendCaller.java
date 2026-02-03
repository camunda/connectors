/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.comprehend.caller;

import static io.camunda.connector.comprehend.model.ComprehendDocumentReadAction.TEXTRACT_ANALYZE_DOCUMENT;

import io.camunda.connector.comprehend.model.ComprehendAsyncRequestData;
import io.camunda.connector.comprehend.model.ComprehendDocumentReadAction;
import io.camunda.connector.comprehend.model.ComprehendDocumentReadMode;
import io.camunda.connector.comprehend.model.ComprehendInputFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.comprehend.ComprehendAsyncClient;
import software.amazon.awssdk.services.comprehend.model.DocumentReadFeatureTypes;
import software.amazon.awssdk.services.comprehend.model.DocumentReaderConfig;
import software.amazon.awssdk.services.comprehend.model.InputDataConfig;
import software.amazon.awssdk.services.comprehend.model.OutputDataConfig;
import software.amazon.awssdk.services.comprehend.model.StartDocumentClassificationJobRequest;
import software.amazon.awssdk.services.comprehend.model.StartDocumentClassificationJobResponse;
import software.amazon.awssdk.services.comprehend.model.Tag;
import software.amazon.awssdk.services.comprehend.model.VpcConfig;

public class AsyncComprehendCaller
    implements ComprehendCaller<
        ComprehendAsyncClient, StartDocumentClassificationJobResponse, ComprehendAsyncRequestData> {

  public static final String VPC_CONFIG_EXCEPTION_MSG =
      "Or both VpcConfig fields SecurityGroupIds and Subnets or none";

  private static final Logger LOGGER = LoggerFactory.getLogger(AsyncComprehendCaller.class);

  private static final int INITIAL_FEATURES_CAPACITY = 2;

  @Override
  public StartDocumentClassificationJobResponse call(
      ComprehendAsyncClient client, ComprehendAsyncRequestData asyncRequest) {
    LOGGER.debug(
        "Starting async comprehend task for document classification with request data: {}",
        asyncRequest);
    StartDocumentClassificationJobRequest.Builder requestBuilder =
        StartDocumentClassificationJobRequest.builder()
            .dataAccessRoleArn(asyncRequest.dataAccessRoleArn())
            .inputDataConfig(prepareInputConfig(asyncRequest))
            .outputDataConfig(prepareOutputDataConf(asyncRequest))
            .vpcConfig(prepareVpcConfig(asyncRequest));

    if (StringUtils.isNotBlank(asyncRequest.clientRequestToken())) {
      requestBuilder.clientRequestToken(asyncRequest.clientRequestToken());
    }

    if (StringUtils.isNotBlank(asyncRequest.documentClassifierArn())) {
      requestBuilder.documentClassifierArn(asyncRequest.documentClassifierArn());
    }

    if (StringUtils.isNotBlank(asyncRequest.flywheelArn())) {
      requestBuilder.flywheelArn(asyncRequest.flywheelArn());
    }

    if (StringUtils.isNotBlank(asyncRequest.jobName())) {
      requestBuilder.jobName(asyncRequest.jobName());
    }

    if (asyncRequest.tags() != null && !asyncRequest.tags().isEmpty()) {
      requestBuilder.tags(prepareTags(asyncRequest));
    }

    if (StringUtils.isNotBlank(asyncRequest.volumeKmsKeyId())) {
      requestBuilder.volumeKmsKeyId(asyncRequest.volumeKmsKeyId());
    }

    return client.startDocumentClassificationJob(requestBuilder.build()).join();
  }

  private InputDataConfig prepareInputConfig(ComprehendAsyncRequestData request) {
    InputDataConfig.Builder builder =
        InputDataConfig.builder()
            .s3Uri(request.inputS3Uri())
            .documentReaderConfig(prepareDocumentReaderConfig(request));

    if (request.comprehendInputFormat() != ComprehendInputFormat.NO_DATA) {
      builder.inputFormat(request.comprehendInputFormat().name());
    }

    return builder.build();
  }

  private OutputDataConfig prepareOutputDataConf(ComprehendAsyncRequestData request) {
    OutputDataConfig.Builder builder = OutputDataConfig.builder().s3Uri(request.outputS3Uri());

    if (StringUtils.isNotBlank(request.outputKmsKeyId())) {
      builder.kmsKeyId(request.outputKmsKeyId());
    }

    return builder.build();
  }

  private List<Tag> prepareTags(ComprehendAsyncRequestData request) {
    return request.tags().entrySet().stream().filter(Objects::nonNull).map(this::creatTag).toList();
  }

  private Tag creatTag(Map.Entry<String, String> entry) {
    return Tag.builder().key(entry.getKey()).value(entry.getValue()).build();
  }

  private VpcConfig prepareVpcConfig(ComprehendAsyncRequestData request) {
    List<String> groupIds = request.securityGroupIds();
    List<String> subnets = request.subnets();

    if (isNullOrEmpty(groupIds) && isNullOrEmpty(subnets)) {
      return null;
    }

    if (!isNullOrEmpty(groupIds) && !isNullOrEmpty(subnets)) {
      return VpcConfig.builder().securityGroupIds(groupIds).subnets(subnets).build();
    } else {
      LOGGER.warn(VPC_CONFIG_EXCEPTION_MSG);
      throw new IllegalArgumentException(VPC_CONFIG_EXCEPTION_MSG);
    }
  }

  private DocumentReaderConfig prepareDocumentReaderConfig(ComprehendAsyncRequestData requestData) {
    if (requestData.documentReadAction() == (ComprehendDocumentReadAction.NO_DATA)) {
      return null;
    }

    var documentReaderConfig =
        DocumentReaderConfig.builder().documentReadAction(requestData.documentReadAction().name());

    if (requestData.documentReadMode() != (ComprehendDocumentReadMode.NO_DATA)) {
      documentReaderConfig.documentReadMode(requestData.documentReadMode().name());
    }

    if (requestData.documentReadAction() == TEXTRACT_ANALYZE_DOCUMENT) {
      List<DocumentReadFeatureTypes> features = prepareFeatures(requestData);
      if (features.isEmpty()) {
        LOGGER.warn("DocumentReadAction: TEXTRACT_ANALYZE_DOCUMENT, but features not selected.");
        throw new IllegalArgumentException(READ_ACTION_WITHOUT_FEATURES_EX);
      }
      documentReaderConfig.featureTypes(features);
    }

    return documentReaderConfig.build();
  }

  private List<DocumentReadFeatureTypes> prepareFeatures(ComprehendAsyncRequestData requestData) {
    List<DocumentReadFeatureTypes> features = new ArrayList<>(INITIAL_FEATURES_CAPACITY);
    if (requestData.featureTypeForms()) {
      features.add(DocumentReadFeatureTypes.FORMS);
    }
    if (requestData.featureTypeTables()) {
      features.add(DocumentReadFeatureTypes.TABLES);
    }
    return features;
  }

  private boolean isNullOrEmpty(List<String> values) {
    return values == null || values.isEmpty();
  }
}
