/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.comprehend.caller;

import static io.camunda.connector.comprehend.model.ComprehendDocumentReadAction.TEXTRACT_ANALYZE_DOCUMENT;

import io.camunda.connector.comprehend.ComprehendClassificationJobResult;
import io.camunda.connector.comprehend.model.ComprehendAsyncRequestData;
import io.camunda.connector.comprehend.model.ComprehendDocumentReadAction;
import io.camunda.connector.comprehend.model.ComprehendDocumentReadMode;
import io.camunda.connector.comprehend.model.ComprehendInputFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.comprehend.ComprehendAsyncClient;
import software.amazon.awssdk.services.comprehend.model.DocumentReadFeatureTypes;
import software.amazon.awssdk.services.comprehend.model.DocumentReaderConfig;
import software.amazon.awssdk.services.comprehend.model.InputDataConfig;
import software.amazon.awssdk.services.comprehend.model.OutputDataConfig;
import software.amazon.awssdk.services.comprehend.model.StartDocumentClassificationJobRequest;
import software.amazon.awssdk.services.comprehend.model.Tag;
import software.amazon.awssdk.services.comprehend.model.VpcConfig;

/**
 * Submits an async {@code StartDocumentClassificationJob} using the real AWS SDK v2 {@link
 * ComprehendAsyncClient} -- unlike AWS SDK v1 (where the async client was a subtype of the sync
 * client, so the pre-migration version of this class could get away with declaring its {@code
 * call()} parameter as the sync client type), v2's {@code ComprehendAsyncClient} and {@code
 * ComprehendClient} are unrelated sibling interfaces. The async client returns a {@code
 * CompletableFuture}; this caller blocks on it (via {@code join()}) to preserve the connector's
 * existing synchronous calling convention.
 */
public class AsyncComprehendCaller
    implements ComprehendCaller<
        ComprehendAsyncClient, ComprehendAsyncRequestData, ComprehendClassificationJobResult> {

  public static final String VPC_CONFIG_EXCEPTION_MSG =
      "Or both VpcConfig fields SecurityGroupIds and Subnets or none";

  private static final Logger LOGGER = LoggerFactory.getLogger(AsyncComprehendCaller.class);

  private static final int INITIAL_FEATURES_CAPACITY = 2;

  @Override
  public ComprehendClassificationJobResult call(
      ComprehendAsyncClient client, ComprehendAsyncRequestData asyncRequest) {
    LOGGER.debug(
        "Starting async comprehend task for document classification with request data: {}",
        asyncRequest);
    var requestBuilder = StartDocumentClassificationJobRequest.builder();

    if (StringUtils.isNotBlank(asyncRequest.clientRequestToken())) {
      requestBuilder.clientRequestToken(asyncRequest.clientRequestToken());
    }

    requestBuilder.dataAccessRoleArn(asyncRequest.dataAccessRoleArn());

    if (StringUtils.isNotBlank(asyncRequest.documentClassifierArn())) {
      requestBuilder.documentClassifierArn(asyncRequest.documentClassifierArn());
    }

    if (StringUtils.isNotBlank(asyncRequest.flywheelArn())) {
      requestBuilder.flywheelArn(asyncRequest.flywheelArn());
    }

    requestBuilder.inputDataConfig(prepareInputConfig(asyncRequest));

    if (StringUtils.isNotBlank(asyncRequest.jobName())) {
      requestBuilder.jobName(asyncRequest.jobName());
    }

    requestBuilder.outputDataConfig(prepareOutputDataConf(asyncRequest));

    if (asyncRequest.tags() != null && !asyncRequest.tags().isEmpty()) {
      requestBuilder.tags(prepareTags(asyncRequest));
    }

    if (StringUtils.isNotBlank(asyncRequest.volumeKmsKeyId())) {
      requestBuilder.volumeKmsKeyId(asyncRequest.volumeKmsKeyId());
    }

    requestBuilder.vpcConfig(prepareVpcConfig(asyncRequest));

    StartDocumentClassificationJobRequest docClassificationRequest = requestBuilder.build();

    return ComprehendClassificationJobResult.from(
        joinUnwrapped(client.startDocumentClassificationJob(docClassificationRequest)));
  }

  /**
   * Blocks on the async SDK call's future and unwraps {@link CompletionException} so a failing call
   * surfaces the original AWS SDK exception (e.g. {@code ComprehendException}) to callers, matching
   * the exception type that the pre-migration (AWS SDK v1) synchronous-call-based async caller
   * propagated, rather than the future's wrapper exception.
   */
  private static <T> T joinUnwrapped(CompletableFuture<T> future) {
    try {
      return future.join();
    } catch (CompletionException e) {
      if (e.getCause() instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw e;
    }
  }

  private InputDataConfig prepareInputConfig(ComprehendAsyncRequestData request) {
    var inputConfigBuilder =
        InputDataConfig.builder()
            .s3Uri(request.inputS3Uri())
            .documentReaderConfig(prepareDocumentReaderConfig(request));

    if (request.comprehendInputFormat() != ComprehendInputFormat.NO_DATA) {
      inputConfigBuilder.inputFormat(request.comprehendInputFormat().name());
    }

    return inputConfigBuilder.build();
  }

  private OutputDataConfig prepareOutputDataConf(ComprehendAsyncRequestData request) {
    var outputConfBuilder = OutputDataConfig.builder().s3Uri(request.outputS3Uri());

    if (StringUtils.isNotBlank(request.outputKmsKeyId())) {
      outputConfBuilder.kmsKeyId(request.outputKmsKeyId());
    }

    return outputConfBuilder.build();
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

  private static boolean isNullOrEmpty(List<String> list) {
    return list == null || list.isEmpty();
  }

  private DocumentReaderConfig prepareDocumentReaderConfig(ComprehendAsyncRequestData requestData) {
    if (requestData.documentReadAction() == (ComprehendDocumentReadAction.NO_DATA)) {
      return null;
    }

    var documentReaderConfigBuilder =
        DocumentReaderConfig.builder().documentReadAction(requestData.documentReadAction().name());

    if (requestData.documentReadMode() != (ComprehendDocumentReadMode.NO_DATA)) {
      documentReaderConfigBuilder.documentReadMode(requestData.documentReadMode().name());
    }

    if (requestData.documentReadAction() == TEXTRACT_ANALYZE_DOCUMENT) {
      List<String> features = prepareFeatures(requestData);
      if (features.isEmpty()) {
        LOGGER.warn("DocumentReadAction: TEXTRACT_ANALYZE_DOCUMENT, but features not selected.");
        throw new IllegalArgumentException(READ_ACTION_WITHOUT_FEATURES_EX);
      }
      documentReaderConfigBuilder.featureTypesWithStrings(features);
    }

    return documentReaderConfigBuilder.build();
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
