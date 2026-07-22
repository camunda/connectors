/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb;

import io.camunda.connector.aws.AwsClientSupport;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class DefaultDynamoDbClientSupplier implements DynamoDbClientSupplier {

  /**
   * Builds the production client through the shared {@link AwsClientSupport#createClient}, so
   * credentials, region, and endpoint are configured exactly as every other AWS SDK v2 connector
   * (issue #7973/#7083). In particular {@code AwsClientSupport} ignores a null/blank endpoint,
   * which keeps the region-only client path intact instead of feeding {@code URI.create("")} to
   * {@code endpointOverride}.
   */
  @Override
  public DynamoDbClient dynamoDbClient(final AwsDynamoDbRequest request) {
    return AwsClientSupport.createClient(DynamoDbClient.builder(), request);
  }
}
