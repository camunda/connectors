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

  /**
   * Delegates to {@link AwsClientSupport#createClient} (issue #7083).
   *
   * <p>Behavior change: {@code configuration.endpoint} (hidden on the element template) was
   * previously ignored; it's now honored, so a process definition that already sets it will target
   * that endpoint instead of the default. A missing region no longer throws {@code
   * NullPointerException} (the previous caller-side {@code request.getConfiguration().region()}
   * dereference); it now falls through to the SDK's default region-provider chain, since {@code
   * AwsClientSupport} has no region validation.
   */
  public SageMakerRuntimeClient getSyncClient(final SageMakerRequest request) {
    return AwsClientSupport.createClient(SageMakerRuntimeClient.builder(), request);
  }

  /** Async counterpart of {@link #getSyncClient(SageMakerRequest)}; see that method's Javadoc. */
  public SageMakerRuntimeAsyncClient getAsyncClient(final SageMakerRequest request) {
    return AwsClientSupport.createClient(SageMakerRuntimeAsyncClient.builder(), request);
  }
}
