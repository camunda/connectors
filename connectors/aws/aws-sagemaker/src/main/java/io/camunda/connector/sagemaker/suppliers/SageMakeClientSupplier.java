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
   * Builds the production sync client through the shared {@link AwsClientSupport#createClient}, so
   * credentials, region, and endpoint-override handling are configured exactly as every other AWS
   * SDK v2 connector (issue #7083).
   *
   * <p>Note: unlike the previous hand-rolled builder, {@code AwsClientSupport} also applies {@code
   * configuration.endpoint} if present. That property was already exposed (hidden) on the element
   * template but was silently ignored by this supplier; it is now honored. Additionally, when
   * {@code configuration} or {@code configuration.region} is absent, {@code AwsClientSupport}
   * leaves the region unset (falling back to the SDK's default region provider chain) instead of
   * the previous caller-side {@code request.getConfiguration().region()} dereference in {@code
   * SagemakerConnectorFunction}, which threw {@code NullPointerException} when either was absent.
   */
  public SageMakerRuntimeClient getSyncClient(final SageMakerRequest request) {
    return AwsClientSupport.createClient(SageMakerRuntimeClient.builder(), request);
  }

  /** Async counterpart of {@link #getSyncClient(SageMakerRequest)}; see that method's Javadoc. */
  public SageMakerRuntimeAsyncClient getAsyncClient(final SageMakerRequest request) {
    return AwsClientSupport.createClient(SageMakerRuntimeAsyncClient.builder(), request);
  }
}
