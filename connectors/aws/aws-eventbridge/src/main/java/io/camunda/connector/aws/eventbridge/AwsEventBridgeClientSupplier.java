/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.eventbridge;

import io.camunda.connector.aws.AwsClientSupport;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;

public class AwsEventBridgeClientSupplier {

  /**
   * Delegates to {@link AwsClientSupport#configureClient} (issue #7083). {@code region} is taken
   * explicitly for symmetry with the lambda/sns suppliers, which apply a genuine per-resource
   * region fallback on top of {@code configureClient}; this connector has none, so here it's a
   * no-op re-application of the value {@code configureClient} already read from the request.
   */
  public EventBridgeClient getAmazonEventBridgeClient(
      final AwsEventBridgeRequest request, final String region) {
    return AwsClientSupport.configureClient(EventBridgeClient.builder(), request)
        .region(Region.of(region))
        .build();
  }
}
