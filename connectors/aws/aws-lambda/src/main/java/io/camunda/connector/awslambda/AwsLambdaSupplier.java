/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.awslambda;

import io.camunda.connector.aws.AwsClientSupport;
import io.camunda.connector.awslambda.model.AwsLambdaRequest;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;

public class AwsLambdaSupplier {

  // Delegates to AwsClientSupport (issue #7083); region is passed explicitly since the caller
  // resolves the deprecated per-function fallback itself.
  public LambdaClient awsLambdaService(final AwsLambdaRequest request, final String region) {
    return AwsClientSupport.configureClient(LambdaClient.builder(), request)
        .region(Region.of(region))
        .build();
  }
}
