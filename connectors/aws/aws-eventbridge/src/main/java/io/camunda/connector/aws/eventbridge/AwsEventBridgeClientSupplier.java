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
   * Builds the production client through the shared {@link AwsClientSupport#configureClient}, so
   * credentials and endpoint-override handling are configured exactly as every other AWS SDK v2
   * connector (issue #7083). The {@code region} parameter is taken explicitly (rather than left to
   * {@code AwsClientSupport}'s config-only lookup) purely for symmetry with the lambda/sns
   * suppliers, which have a genuine per-resource region fallback to apply on top of {@code
   * configureClient}; this connector has no such fallback, so for {@link EventBridgeFunction} the
   * override is a no-op re-application of the same value {@code configureClient} already read off
   * {@code request.getConfiguration()}.
   */
  public EventBridgeClient getAmazonEventBridgeClient(
      final AwsEventBridgeRequest request, final String region) {
    return AwsClientSupport.configureClient(EventBridgeClient.builder(), request)
        .region(Region.of(region))
        .build();
  }
}
