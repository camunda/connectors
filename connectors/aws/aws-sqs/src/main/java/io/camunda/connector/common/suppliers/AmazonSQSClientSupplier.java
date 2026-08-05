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
 * Client-supplier seam for the AWS SDK v2 {@link SqsClient} (issue #7083). Takes the whole {@link
 * AwsBaseRequest} rather than a resolved credentials provider so the implementation can delegate to
 * {@code AwsClientSupport}, mirroring {@code DynamoDbClientSupplier}. Uses {@link AwsBaseRequest}
 * (not a narrower subtype) since this interface serves both the outbound ({@code
 * SqsConnectorRequest}) and inbound ({@code SqsInboundProperties}) request types.
 *
 * <p>Region is still passed explicitly because both callers fall back to the deprecated per-queue
 * region field via {@code AwsUtils.extractRegionOrDefault} before calling this.
 */
public interface AmazonSQSClientSupplier {
  SqsClient sqsClient(AwsBaseRequest request, String region);
}
