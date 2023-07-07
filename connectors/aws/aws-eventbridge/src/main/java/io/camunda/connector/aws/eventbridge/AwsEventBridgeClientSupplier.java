/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.eventbridge;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.eventbridge.AmazonEventBridge;
import com.amazonaws.services.eventbridge.AmazonEventBridgeClient;

public class AwsEventBridgeClientSupplier {

  public AmazonEventBridge getAmazonEventBridgeClient(
      final AWSCredentialsProvider credentialsProvider, final String region) {
    return AmazonEventBridgeClient.builder()
        .withCredentials(credentialsProvider)
        .withRegion(region)
        .build();
  }
}
