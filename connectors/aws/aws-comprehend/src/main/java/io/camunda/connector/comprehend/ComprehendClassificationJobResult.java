/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.comprehend;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import software.amazon.awssdk.services.comprehend.model.StartDocumentClassificationJobResponse;

/**
 * Connector-owned result of a Comprehend {@code StartDocumentClassificationJob} call.
 *
 * <p>The AWS SDK v2 model class ({@link StartDocumentClassificationJobResponse}) exposes fluent
 * accessors ({@code jobId()}, {@code jobArn()}, ...) rather than JavaBean getters. Serializing it
 * directly with the connectors' {@code ObjectMapper} (which disables {@code FAIL_ON_EMPTY_BEANS})
 * would silently produce {@code {}}. This record maps the v2 response back into the exact JSON
 * shape that the pre-v2 (AWS SDK v1) connector documented and returned.
 */
@JsonPropertyOrder({
  "sdkResponseMetadata",
  "sdkHttpMetadata",
  "jobId",
  "jobArn",
  "jobStatus",
  "documentClassifierArn"
})
public record ComprehendClassificationJobResult(
    SdkResponseMetadata sdkResponseMetadata,
    SdkHttpMetadata sdkHttpMetadata,
    String jobId,
    String jobArn,
    String jobStatus,
    String documentClassifierArn) {

  /**
   * Maps an AWS SDK v2 {@link StartDocumentClassificationJobResponse} into the v1-shaped connector
   * result.
   */
  public static ComprehendClassificationJobResult from(
      final StartDocumentClassificationJobResponse response) {
    return new ComprehendClassificationJobResult(
        SdkResponseMetadata.from(response.responseMetadata()),
        SdkHttpMetadata.from(response.sdkHttpResponse()),
        response.jobId(),
        response.jobArn(),
        response.jobStatusAsString(),
        response.documentClassifierArn());
  }
}
