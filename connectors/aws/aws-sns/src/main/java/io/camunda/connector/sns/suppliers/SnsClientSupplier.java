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

  // Delegates to AwsClientSupport (issue #7083); region is passed explicitly since the caller
  // resolves the deprecated per-topic fallback itself. A blank endpoint is now a no-op instead
  // of failing at construction time.
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
