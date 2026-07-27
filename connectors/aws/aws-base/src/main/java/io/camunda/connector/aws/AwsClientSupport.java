/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws;

import io.camunda.connector.aws.model.impl.AwsBaseRequest;
import java.net.URI;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.regions.Region;

public class AwsClientSupport {

  public static <B extends AwsClientBuilder<B, C>, C extends AutoCloseable> C createClient(
      B builder, AwsBaseRequest request) {
    return configureClient(builder, request).build();
  }

  public static <B extends AwsClientBuilder<B, C>, C extends AutoCloseable> B configureClient(
      B builder, AwsBaseRequest request) {
    builder.credentialsProvider(CredentialsProviderSupportV2.credentialsProvider(request));
    var config = request.getConfiguration();
    if (config != null && config.region() != null) {
      builder.region(Region.of(config.region()));
    }
    if (config != null && config.endpoint() != null && !config.endpoint().isBlank()) {
      builder.endpointOverride(URI.create(config.endpoint()));
    }
    return builder;
  }
}
