/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.sagemaker.suppliers;

import io.camunda.connector.aws.AwsClientSupport;
import io.camunda.connector.sagemaker.model.SageMakerRequest;
import software.amazon.awssdk.services.sagemakerruntime.SageMakerRuntimeAsyncClient;
import software.amazon.awssdk.services.sagemakerruntime.SageMakerRuntimeClient;

public class SageMakeClientSupplier {

  // Delegates to AwsClientSupport (issue #7083); endpoint override is now honored, and a missing
  // region falls through to the SDK's default chain instead of throwing NullPointerException.
  public SageMakerRuntimeClient getSyncClient(final SageMakerRequest request) {
    return AwsClientSupport.createClient(SageMakerRuntimeClient.builder(), request);
  }

  // Async counterpart of getSyncClient; same behavior notes apply.
  public SageMakerRuntimeAsyncClient getAsyncClient(final SageMakerRequest request) {
    return AwsClientSupport.createClient(SageMakerRuntimeAsyncClient.builder(), request);
  }
}
