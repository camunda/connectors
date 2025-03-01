/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.supplier;

import io.camunda.connector.aws.CredentialsProviderSupportV2;
import io.camunda.connector.aws.model.impl.AwsBaseRequest;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

public class BedrockRuntimeClientSupplier {

  public BedrockRuntimeClient getBedrockRuntimeClient(final AwsBaseRequest request) {
    return BedrockRuntimeClient.builder()
        .credentialsProvider(CredentialsProviderSupportV2.credentialsProvider(request))
        .region(Region.of(request.getConfiguration().region()))
        .build();
  }
}
