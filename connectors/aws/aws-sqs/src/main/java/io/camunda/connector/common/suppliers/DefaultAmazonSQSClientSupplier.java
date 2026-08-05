/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.common.suppliers;

import io.camunda.connector.aws.AwsClientSupport;
import io.camunda.connector.aws.model.impl.AwsBaseRequest;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

public class DefaultAmazonSQSClientSupplier implements AmazonSQSClientSupplier {

  /**
   * Delegates to {@link AwsClientSupport#configureClient} (issue #7083). Region is applied
   * explicitly since the caller must first fall back to the deprecated per-queue region before
   * calling this.
   *
   * <p>Behavior changes: a blank (non-null) endpoint is now a no-op instead of failing at
   * construction time. On the inbound path, endpoint override was previously unreachable at all
   * ({@code SqsExecutable} never called the old 3-arg overload); it's now honored like every other
   * connector.
   */
  @Override
  public SqsClient sqsClient(final AwsBaseRequest request, final String region) {
    return AwsClientSupport.configureClient(SqsClient.builder(), request)
        .region(Region.of(region))
        .build();
  }
}
