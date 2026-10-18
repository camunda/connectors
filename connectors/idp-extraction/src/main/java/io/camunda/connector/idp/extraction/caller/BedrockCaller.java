/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.caller;

import io.camunda.connector.idp.extraction.client.ai.BedrockAiClient;
import io.camunda.connector.idp.extraction.model.ConverseData;
import io.camunda.connector.idp.extraction.model.ExtractionRequestData;
import io.camunda.connector.idp.extraction.model.LlmModel;
import io.camunda.connector.idp.extraction.utils.AwsLlmModelUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

public class BedrockCaller {

  private static final Logger LOGGER = LoggerFactory.getLogger(BedrockCaller.class);

  public String call(
      ExtractionRequestData input,
      String awsRegion,
      String extractedText,
      BedrockRuntimeClient bedrockRuntimeClient) {
    LOGGER.debug("Calling AWS Bedrock model with extraction request data: {}", input);

    ConverseData converseData = input.converseData();
    final String modelId =
        AwsLlmModelUtil.supportsCrossRegionInference(awsRegion)
            ? AwsLlmModelUtil.processModelIdForCrossRegion(converseData.modelId(), awsRegion)
            : converseData.modelId();

    LlmModel llmModel = LlmModel.fromId(modelId);
    BedrockAiClient aiClient = new BedrockAiClient(bedrockRuntimeClient, modelId, converseData);

    String userMessage = llmModel.getMessage(extractedText, input.taxonomyItems());
    if (llmModel.isSystemPromptAllowed()) {
      return aiClient.chat(llmModel.getSystemPrompt(), userMessage);
    }
    return aiClient.chat(String.format("%s%n%s", llmModel.getSystemPrompt(), userMessage));
  }
}
