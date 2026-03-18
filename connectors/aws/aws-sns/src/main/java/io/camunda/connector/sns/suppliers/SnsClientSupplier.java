/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.sns.suppliers;

import com.amazonaws.services.sns.message.SnsMessageManager;
import java.net.URI;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;

public class SnsClientSupplier {

  public SnsClient getSnsClient(
      final AwsCredentialsProvider credentialsProvider, final String region) {
    return SnsClient.builder()
        .credentialsProvider(credentialsProvider)
        .region(Region.of(region))
        .build();
  }

  public SnsClient getSnsClient(
      final AwsCredentialsProvider credentialsProvider,
      final String region,
      final String endpoint) {
    return SnsClient.builder()
        .credentialsProvider(credentialsProvider)
        .region(Region.of(region))
        .endpointOverride(URI.create(endpoint))
        .build();
  }

  // TODO: SnsMessageManager is from AWS SDK v1 and has no equivalent in v2.
  //  Migrate once resolved: https://github.com/aws/aws-sdk-java-v2/issues/1302
  public SnsMessageManager messageManager(final String region) {
    return new SnsMessageManager(region);
  }
}
