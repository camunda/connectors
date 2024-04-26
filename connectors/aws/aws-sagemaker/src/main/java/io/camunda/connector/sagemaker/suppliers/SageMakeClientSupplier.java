/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.sagemaker.suppliers;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.sagemakerruntime.AmazonSageMakerRuntime;
import com.amazonaws.services.sagemakerruntime.AmazonSageMakerRuntimeAsync;
import com.amazonaws.services.sagemakerruntime.AmazonSageMakerRuntimeAsyncClientBuilder;
import com.amazonaws.services.sagemakerruntime.AmazonSageMakerRuntimeClientBuilder;

public class SageMakeClientSupplier {

  public AmazonSageMakerRuntime getSyncClient(
      final AWSCredentialsProvider credentialsProvider, final String region) {
    return AmazonSageMakerRuntimeClientBuilder.standard()
        .withCredentials(credentialsProvider)
        .withRegion(region)
        .build();
  }

  public AmazonSageMakerRuntimeAsync getAsyncClient(
      final AWSCredentialsProvider credentialsProvider, final String region) {
    return AmazonSageMakerRuntimeAsyncClientBuilder.standard()
        .withCredentials(credentialsProvider)
        .withRegion(region)
        .build();
  }
}
