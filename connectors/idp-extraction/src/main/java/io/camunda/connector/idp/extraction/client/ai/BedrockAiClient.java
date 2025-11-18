/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.client.ai;

import dev.langchain4j.model.bedrock.BedrockChatModel;
import dev.langchain4j.model.bedrock.BedrockChatRequestParameters;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import io.camunda.connector.idp.extraction.client.ai.base.AiClient;
import io.camunda.connector.idp.extraction.model.ConverseData;
import io.camunda.connector.idp.extraction.utils.AwsLlmModelUtil;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

public class BedrockAiClient extends AiClient {

  public BedrockAiClient(
      AwsCredentialsProvider credentialsProvider, String region, ConverseData converseData) {

    final String modelId =
        AwsLlmModelUtil.supportsCrossRegionInference(region)
            ? AwsLlmModelUtil.processModelIdForCrossRegion(converseData.modelId(), region)
            : converseData.modelId();

    BedrockRuntimeClient bedrockClient =
        BedrockRuntimeClient.builder()
            .region(Region.of(region))
            .credentialsProvider(credentialsProvider)
            .build();

    this.chatModel =
        BedrockChatModel.builder()
            .client(bedrockClient)
            .defaultRequestParameters(getChatRequestParameters(converseData))
            .modelId(modelId)
            .build();
  }

  private ChatRequestParameters getChatRequestParameters(ConverseData converseData) {
    var parametersBuilder = BedrockChatRequestParameters.builder();

    if (converseData.maxTokens() != null) {
      parametersBuilder.maxOutputTokens(converseData.maxTokens());
    }
    if (converseData.temperature() != null) {
      parametersBuilder.temperature(converseData.temperature().doubleValue());
    }
    if (converseData.topP() != null) {
      parametersBuilder.topP(converseData.topP().doubleValue());
    }

    return parametersBuilder.build();
  }
}
