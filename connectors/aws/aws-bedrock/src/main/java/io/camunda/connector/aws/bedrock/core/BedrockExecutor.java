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

  /**
   * Builds the production client through the shared {@link AwsClientSupport#createClient}, so
   * credentials, region, and endpoint are configured exactly as every other AWS SDK v2 connector
   * (issue #7083).
   *
   * <p>Note: unlike the previous inline builder, {@code AwsClientSupport} also applies {@code
   * configuration.endpoint} if present. That property is already exposed (hidden) on the element
   * template but was silently ignored by the previous hand-rolled builder; it is now honored, so
   * any process definition that already sets it (e.g. via hybrid template or raw XML) will start
   * talking to that endpoint instead of the default AWS one.
   */
  public static BedrockExecutor create(BedrockRequest bedrockRequest) {
    return new BedrockExecutor(
        AwsClientSupport.createClient(BedrockRuntimeClient.builder(), bedrockRequest),
        bedrockRequest.getData());
  }

  public Object execute() {
    return this.requestData.execute(bedrockRuntimeClient, ObjectMapperSupplier.getMapperInstance());
  }
}
