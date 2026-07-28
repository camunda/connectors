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
   * Builds the production client through the shared {@link AwsClientSupport#configureClient}, so
   * credentials and endpoint-override handling are configured exactly as every other AWS SDK v2
   * connector (issue #7083). The region is applied explicitly from the already-resolved {@code
   * region} argument (rather than left to {@code AwsClientSupport}'s config-only lookup) because
   * the caller must first fall back to the deprecated per-queue region and enforce that a region is
   * present; overriding after {@code configureClient} keeps that resolution authoritative
   * regardless of what {@code request.getConfiguration()} contains.
   *
   * <p>Note: unlike the previous hand-rolled builder, {@code AwsClientSupport} ignores a null/blank
   * endpoint instead of feeding it to {@code endpointOverride(URI.create(...))}. On the outbound
   * path, a blank (non-null) endpoint previously reached the v1-style builder as {@code
   * URI.create("")}; that edge case is now a no-op instead of a construction-time error. On the
   * inbound path, endpoint override was previously unreachable altogether ({@code SqsExecutable}
   * never called the old 3-arg overload); it is now honored like every other connector.
   */
  @Override
  public SqsClient sqsClient(final AwsBaseRequest request, final String region) {
    return AwsClientSupport.configureClient(SqsClient.builder(), request)
        .region(Region.of(region))
        .build();
  }
}
