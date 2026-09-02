/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.common.suppliers;

import io.camunda.connector.aws.model.impl.AwsBaseRequest;
import software.amazon.awssdk.services.sqs.SqsClient;

// Client-supplier seam for the AWS SDK v2 SqsClient (issue #7083), mirroring
// DynamoDbClientSupplier. Takes the whole AwsBaseRequest (serves both outbound and inbound
// request types) so the implementation can delegate to AwsClientSupport.
public interface AmazonSQSClientSupplier {
  SqsClient sqsClient(AwsBaseRequest request, String region);
}
