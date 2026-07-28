/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.common.suppliers;

import io.camunda.connector.aws.model.impl.AwsBaseRequest;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * Client-supplier seam for the AWS SDK v2 {@link SqsClient}, taking the whole {@link
 * AwsBaseRequest} (rather than an already-resolved credentials provider plus plain strings) so the
 * production implementation can delegate credential/endpoint configuration to the shared {@code
 * io.camunda.connector.aws.AwsClientSupport} (issue #7083 centralization), mirroring {@code
 * io.camunda.connector.aws.dynamodb.DynamoDbClientSupplier}. {@link AwsBaseRequest} (not a more
 * specific subtype) because this interface serves both the outbound ({@code SqsConnectorRequest})
 * and inbound ({@code SqsInboundProperties}) request types.
 *
 * <p>The region is still passed explicitly (rather than left to {@code AwsClientSupport}'s
 * config-only lookup) because both callers must first fall back to the deprecated per-queue region
 * field and enforce that a region is present via {@code AwsUtils.extractRegionOrDefault}.
 */
public interface AmazonSQSClientSupplier {
  SqsClient sqsClient(AwsBaseRequest request, String region);
}
