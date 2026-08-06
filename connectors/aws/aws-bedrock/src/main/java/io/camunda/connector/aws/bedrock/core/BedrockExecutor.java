/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.core;

import io.camunda.connector.aws.AwsClientSupport;
import io.camunda.connector.aws.ObjectMapperSupplier;
import io.camunda.connector.aws.bedrock.model.BedrockRequest;
import io.camunda.connector.aws.bedrock.model.RequestData;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

public class BedrockExecutor {

  private final BedrockRuntimeClient bedrockRuntimeClient;
  private final RequestData requestData;

  public BedrockExecutor(BedrockRuntimeClient bedrockRuntimeClient, RequestData requestData) {
    this.bedrockRuntimeClient = bedrockRuntimeClient;
    this.requestData = requestData;
  }

  // Delegates to AwsClientSupport (issue #7083); endpoint override is now honored, not ignored.
  public static BedrockExecutor create(BedrockRequest bedrockRequest) {
    return new BedrockExecutor(
        AwsClientSupport.createClient(BedrockRuntimeClient.builder(), bedrockRequest),
        bedrockRequest.getData());
  }

  public Object execute() {
    return this.requestData.execute(bedrockRuntimeClient, ObjectMapperSupplier.getMapperInstance());
  }
}
