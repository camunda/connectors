/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.core;

import io.camunda.connector.aws.CredentialsProviderSupportV2;
import io.camunda.connector.aws.ObjectMapperSupplier;
import io.camunda.connector.aws.bedrock.model.BedrockRequest;
import io.camunda.connector.aws.bedrock.model.BedrockResponse;
import io.camunda.connector.aws.bedrock.model.RequestData;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

public class BedrockExecutor<T extends RequestData> {

  private final BedrockRuntimeClient bedrockRuntimeClient;
  private final T requestData;

  public BedrockExecutor(BedrockRuntimeClient bedrockRuntimeClient, T requestData) {
    this.bedrockRuntimeClient = bedrockRuntimeClient;
    this.requestData = requestData;
  }

  public static <T extends RequestData> BedrockExecutor<T> create(
      BedrockRequest<T> bedrockRequest) {
    return new BedrockExecutor<>(
        BedrockRuntimeClient.builder()
            .credentialsProvider(CredentialsProviderSupportV2.credentialsProvider(bedrockRequest))
            .region(Region.of(bedrockRequest.getConfiguration().region()))
            .build(),
        bedrockRequest.getData());
  }

  public BedrockResponse execute() {
    return this.requestData.execute(bedrockRuntimeClient, ObjectMapperSupplier.getMapperInstance());
  }
}
