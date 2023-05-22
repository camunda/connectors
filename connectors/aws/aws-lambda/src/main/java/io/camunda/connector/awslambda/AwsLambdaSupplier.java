/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.awslambda;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaClientBuilder;

public class AwsLambdaSupplier {

  public AWSLambda awsLambdaService(
      final AWSCredentialsProvider credentialsProvider, final String region) {
    return AWSLambdaClientBuilder.standard()
        .withCredentials(credentialsProvider)
        .withRegion(region)
        .build();
  }
}
