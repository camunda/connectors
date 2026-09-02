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

  // Delegates to AwsClientSupport (issue #7083); region param kept only for signature symmetry
  // with lambda/sns, which do have a real per-resource fallback to apply.
  public EventBridgeClient getAmazonEventBridgeClient(
      final AwsEventBridgeRequest request, final String region) {
    return AwsClientSupport.configureClient(EventBridgeClient.builder(), request)
        .region(Region.of(region))
        .build();
  }
}
