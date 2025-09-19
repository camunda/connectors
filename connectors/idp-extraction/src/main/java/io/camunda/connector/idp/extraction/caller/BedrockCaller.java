/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.caller;

import io.camunda.connector.idp.extraction.model.ConverseData;
import io.camunda.connector.idp.extraction.model.ExtractionRequestData;
import io.camunda.connector.idp.extraction.model.LlmModel;
import io.camunda.connector.idp.extraction.utils.AwsLlmModelUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;

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

    ConverseResponse response =
        bedrockRuntimeClient.converse(
            request -> {
              String userMessage = llmModel.getMessage(extractedText, input.taxonomyItems());

              if (llmModel.isSystemPromptAllowed()) {
                SystemContentBlock prompt =
                    SystemContentBlock.builder().text(llmModel.getSystemPrompt()).build();
                request.system(prompt);
              } else {
                userMessage = String.format("%s%n%s", llmModel.getSystemPrompt(), userMessage);
              }

              Message message =
                  Message.builder()
                      .content(ContentBlock.fromText(userMessage))
                      .role(ConversationRole.USER)
                      .build();

              request
                  .modelId(modelId)
                  .messages(message)
                  .inferenceConfig(
                      config ->
                          config.temperature(converseData.temperature()).topP(converseData.topP()));
            });

    return response.output().message().content().getFirst().text();
  }
}
