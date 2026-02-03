/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.comprehend.supplier;

import io.camunda.connector.aws.CredentialsProviderSupport;
import io.camunda.connector.comprehend.model.ComprehendRequest;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.comprehend.ComprehendAsyncClient;
import software.amazon.awssdk.services.comprehend.ComprehendClient;

public class ComprehendClientSupplier {

  public ComprehendClient getSyncClient(ComprehendRequest comprehendRequest) {
    return (ComprehendClient)
        ComprehendClient.builder()
            .credentialsProvider(CredentialsProviderSupport.credentialsProvider(comprehendRequest))
            .region(Region.of(comprehendRequest.getConfiguration().region()))
            .build();
  }

  public ComprehendAsyncClient getAsyncClient(ComprehendRequest comprehendRequest) {
    return (ComprehendAsyncClient)
        ComprehendAsyncClient.builder()
            .credentialsProvider(CredentialsProviderSupport.credentialsProvider(comprehendRequest))
            .region(Region.of(comprehendRequest.getConfiguration().region()))
            .build();
  }
}
