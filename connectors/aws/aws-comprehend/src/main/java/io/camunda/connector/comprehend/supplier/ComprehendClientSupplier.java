/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.comprehend.supplier;

import com.amazonaws.services.comprehend.AmazonComprehendAsyncClient;
import com.amazonaws.services.comprehend.AmazonComprehendClient;
import io.camunda.connector.aws.CredentialsProviderSupport;
import io.camunda.connector.comprehend.model.ComprehendRequest;

public class ComprehendClientSupplier {

  public AmazonComprehendClient getSyncClient(ComprehendRequest comprehendRequest) {
    return (AmazonComprehendClient)
        AmazonComprehendClient.builder()
            .withCredentials(CredentialsProviderSupport.credentialsProvider(comprehendRequest))
            .withRegion(comprehendRequest.getConfiguration().region())
            .build();
  }

  public AmazonComprehendAsyncClient getAsyncClient(ComprehendRequest comprehendRequest) {
    return (AmazonComprehendAsyncClient)
        AmazonComprehendAsyncClient.asyncBuilder()
            .withCredentials(CredentialsProviderSupport.credentialsProvider(comprehendRequest))
            .withRegion(comprehendRequest.getConfiguration().region())
            .build();
  }
}
