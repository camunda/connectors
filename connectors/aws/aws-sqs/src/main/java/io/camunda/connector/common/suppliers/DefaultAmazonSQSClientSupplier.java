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

  // Delegates to AwsClientSupport (issue #7083). A blank endpoint is now a no-op instead of
  // failing, and the inbound path now honors a custom endpoint (previously unreachable).
  @Override
  public SqsClient sqsClient(final AwsBaseRequest request, final String region) {
    return AwsClientSupport.configureClient(SqsClient.builder(), request)
        .region(Region.of(region))
        .build();
  }
}
