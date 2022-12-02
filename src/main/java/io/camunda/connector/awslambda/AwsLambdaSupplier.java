/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.awslambda;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaClientBuilder;
import io.camunda.connector.awslambda.model.AuthenticationRequestData;

public class AwsLambdaSupplier {

  public AWSLambda awsLambdaService(
      final AuthenticationRequestData authentication, final String region) {
    return AWSLambdaClientBuilder.standard()
        .withCredentials(
            new AWSStaticCredentialsProvider(
                new BasicAWSCredentials(
                    authentication.getAccessKey(), authentication.getSecretKey())))
        .withRegion(region)
        .build();
  }
}
