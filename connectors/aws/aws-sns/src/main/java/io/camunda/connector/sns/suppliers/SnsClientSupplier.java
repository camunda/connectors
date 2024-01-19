/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.sns.suppliers;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sns.message.SnsMessageManager;

public class SnsClientSupplier {

  public AmazonSNS getSnsClient(
      final AWSCredentialsProvider credentialsProvider, final String region) {
    return AmazonSNSClientBuilder.standard()
        .withCredentials(credentialsProvider)
        .withRegion(region)
        .build();
  }

  public AmazonSNS getSnsClient(
      final AWSCredentialsProvider credentialsProvider,
      final String region,
      final String endpoint) {
    return AmazonSNSClientBuilder.standard()
        .withCredentials(credentialsProvider)
        .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(endpoint, region))
        .build();
  }

  public SnsMessageManager messageManager(final String region) {
    return new SnsMessageManager(region);
  }
}
