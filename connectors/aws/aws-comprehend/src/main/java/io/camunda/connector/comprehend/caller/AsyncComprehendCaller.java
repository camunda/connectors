/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.comprehend.caller;

import static io.camunda.connector.comprehend.model.ComprehendDocumentReadAction.TEXTRACT_ANALYZE_DOCUMENT;

import software.amazon.awssdk.services.comprehend.ComprehendClient;
import software.amazon.awssdk.services.comprehend.model.*;
import com.amazonaws.util.CollectionUtils;
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

public class AsyncComprehendCaller
    implements ComprehendCaller<StartDocumentClassificationJobResponse, ComprehendAsyncRequestData> {

  public static final String VPC_CONFIG_EXCEPTION_MSG =
      "Or both VpcConfig fields SecurityGroupIds and Subnets or none";

  private static final Logger LOGGER = LoggerFactory.getLogger(AsyncComprehendCaller.class);

  private static final int INITIAL_FEATURES_CAPACITY = 2;

  @Override
  public StartDocumentClassificationJobResponse call(
      ComprehendClient client, ComprehendAsyncRequestData asyncRequest) {
    LOGGER.debug(
        "Starting async comprehend task for document classification with request data: {}",
        asyncRequest);
    var docClassificationRequest = StartDocumentClassificationJobRequest.builder()
        .build();

    if (StringUtils.isNotBlank(asyncRequest.clientRequestToken())) {
      docClassificationRequest.clientRequestToken(asyncRequest.clientRequestToken());
    }

    docClassificationRequest.dataAccessRoleArn(asyncRequest.dataAccessRoleArn());

    if (StringUtils.isNotBlank(asyncRequest.documentClassifierArn())) {
      docClassificationRequest.documentClassifierArn(asyncRequest.documentClassifierArn());
    }

    if (StringUtils.isNotBlank(asyncRequest.flywheelArn())) {
      docClassificationRequest.flywheelArn(asyncRequest.flywheelArn());
    }

    docClassificationRequest.inputDataConfig(prepareInputConfig(asyncRequest));

    if (StringUtils.isNotBlank(asyncRequest.jobName())) {
      docClassificationRequest.jobName(asyncRequest.jobName());
    }

    docClassificationRequest.outputDataConfig(prepareOutputDataConf(asyncRequest));

    if (asyncRequest.tags() != null && !asyncRequest.tags().isEmpty()) {
      docClassificationRequest.tags(prepareTags(asyncRequest));
    }

    if (StringUtils.isNotBlank(asyncRequest.volumeKmsKeyId())) {
      docClassificationRequest.volumeKmsKeyId(asyncRequest.volumeKmsKeyId());
    }

    docClassificationRequest.vpcConfig(prepareVpcConfig(asyncRequest));

    return client.startDocumentClassificationJob(docClassificationRequest);
  }

  private InputDataConfig prepareInputConfig(ComprehendAsyncRequestData request) {
    var inputConfig =
        InputDataConfig.builder()
            .s3Uri(request.inputS3Uri())
            .documentReaderConfig(prepareDocumentReaderConfig(request))
        .build();

    if (request.comprehendInputFormat() != ComprehendInputFormat.NO_DATA) {
      inputConfig.inputFormat(request.comprehendInputFormat().name());
    }

    return inputConfig;
  }

  private OutputDataConfig prepareOutputDataConf(ComprehendAsyncRequestData request) {
    var outputConf = OutputDataConfig.builder().s3Uri(request.outputS3Uri())
        .build();

    if (StringUtils.isNotBlank(request.outputKmsKeyId())) {
      outputConf.kmsKeyId(request.outputKmsKeyId());
    }

    return outputConf;
  }

  private List<Tag> prepareTags(ComprehendAsyncRequestData request) {
    return request.tags().entrySet().stream().filter(Objects::nonNull).map(this::creatTag).toList();
  }

  private Tag creatTag(Map.Entry<String, String> entry) {
    return Tag.builder().key(entry.getKey()).value(entry.getValue())
        .build();
  }

  private VpcConfig prepareVpcConfig(ComprehendAsyncRequestData request) {
    List<String> groupIds = request.securityGroupIds();
    List<String> subnets = request.subnets();

    if (CollectionUtils.isNullOrEmpty(groupIds) && CollectionUtils.isNullOrEmpty(subnets)) {
      return null;
    }

    if (!CollectionUtils.isNullOrEmpty(groupIds) && !CollectionUtils.isNullOrEmpty(subnets)) {
      return VpcConfig.builder().securityGroupIds(groupIds).subnets(subnets)
          .build();
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
        DocumentReaderConfig.builder().documentReadAction(requestData.documentReadAction().name())
        .build();

    if (requestData.documentReadMode() != (ComprehendDocumentReadMode.NO_DATA)) {
      documentReaderConfig.documentReadMode(requestData.documentReadMode().name());
    }

    if (requestData.documentReadAction() == TEXTRACT_ANALYZE_DOCUMENT) {
      List<String> features = prepareFeatures(requestData);
      if (features.isEmpty()) {
        LOGGER.warn("DocumentReadAction: TEXTRACT_ANALYZE_DOCUMENT, but features not selected.");
        throw new IllegalArgumentException(READ_ACTION_WITHOUT_FEATURES_EX);
      }
      documentReaderConfig.featureTypes(features);
    }

    return documentReaderConfig;
  }

  private List<String> prepareFeatures(ComprehendAsyncRequestData requestData) {
    List<String> features = new ArrayList<>(INITIAL_FEATURES_CAPACITY);
    if (requestData.featureTypeForms()) {
      features.add(DocumentReadFeatureTypes.FORMS.name());
    }
    if (requestData.featureTypeTables()) {
      features.add(DocumentReadFeatureTypes.TABLES.name());
    }
    return features;
  }
}
