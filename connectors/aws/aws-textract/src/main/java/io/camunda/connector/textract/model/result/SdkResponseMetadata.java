/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.textract.model.result;

import software.amazon.awssdk.awscore.AwsResponseMetadata;

/**
 * Shared {@code sdkResponseMetadata} shape, reused by every connector-owned Textract result (sync
 * analyze, async start, polling/merge) since AWS SDK v2 exposes the same {@link
 * AwsResponseMetadata} on every {@code TextractResponse} subtype.
 */
public record SdkResponseMetadata(String requestId) {

  // v2 returns the literal "UNKNOWN" when AWS_REQUEST_ID is absent; v1 exposed null there.
  private static final String UNKNOWN_REQUEST_ID = "UNKNOWN";

  public static SdkResponseMetadata from(final AwsResponseMetadata metadata) {
    if (metadata == null) {
      return null;
    }
    String requestId = metadata.requestId();
    return new SdkResponseMetadata(UNKNOWN_REQUEST_ID.equals(requestId) ? null : requestId);
  }
}
