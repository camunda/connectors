/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.comprehend;

import software.amazon.awssdk.awscore.AwsResponseMetadata;

/**
 * Request-id metadata shared by every AWS Comprehend v2 response, reconstructed from {@code
 * response.responseMetadata()}.
 *
 * <p>Mirrors the {@code sdkResponseMetadata} object the pre-v2 (AWS SDK v1) connector exposed, and
 * is shared by both {@link ComprehendClassifyResult} and {@link ComprehendClassificationJobResult}
 * since both response types carry the same AWS-request-id metadata.
 */
public record SdkResponseMetadata(String requestId) {
  // v2 returns the literal "UNKNOWN" when AWS_REQUEST_ID is absent; v1 exposed null there
  private static final String UNKNOWN_REQUEST_ID = "UNKNOWN";

  public static SdkResponseMetadata from(final AwsResponseMetadata metadata) {
    if (metadata == null) {
      return null;
    }
    String requestId = metadata.requestId();
    return new SdkResponseMetadata(UNKNOWN_REQUEST_ID.equals(requestId) ? null : requestId);
  }
}
