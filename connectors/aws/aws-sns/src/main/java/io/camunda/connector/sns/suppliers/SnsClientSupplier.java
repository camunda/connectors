/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.sns.suppliers;

import com.amazonaws.services.sns.message.SnsMessageManager;
import io.camunda.connector.aws.AwsClientSupport;
import io.camunda.connector.sns.outbound.model.SnsConnectorRequest;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;

public class SnsClientSupplier {

  /**
   * Builds the production client through the shared {@link AwsClientSupport#configureClient}, so
   * credentials and endpoint-override handling are configured exactly as every other AWS SDK v2
   * connector (issue #7083). The region is applied explicitly from the already-resolved {@code
   * region} argument (rather than left to {@code AwsClientSupport}'s config-only lookup) because
   * the caller ({@link io.camunda.connector.sns.outbound.SnsConnectorFunction}) must first fall
   * back to the deprecated per-topic region and enforce that a region is present; overriding after
   * {@code configureClient} keeps that resolution authoritative regardless of what {@code
   * request.getConfiguration()} contains.
   *
   * <p>Note: unlike the previous hand-rolled builder, {@code AwsClientSupport} ignores a null/blank
   * endpoint instead of feeding it to {@code endpointOverride(URI.create(...))}. A blank (non-null)
   * endpoint previously reached the v1-style builder as {@code URI.create("")}; that edge case is
   * now a no-op instead of a construction-time error.
   */
  public SnsClient getSnsClient(final SnsConnectorRequest request, final String region) {
    return AwsClientSupport.configureClient(SnsClient.builder(), request)
        .region(Region.of(region))
        .build();
  }

  // TODO: SnsMessageManager is from AWS SDK v1 and has no equivalent in v2.
  //  Migrate once resolved: https://github.com/aws/aws-sdk-java-v2/issues/1302
  public SnsMessageManager messageManager(final String region) {
    return new SnsMessageManager(region);
  }
}
