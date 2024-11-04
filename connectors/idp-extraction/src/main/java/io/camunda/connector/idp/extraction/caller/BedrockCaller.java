/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.caller;

import io.camunda.connector.idp.extraction.model.ConverseData;
import io.camunda.connector.idp.extraction.model.ExtractionRequest;
import io.camunda.connector.idp.extraction.model.LlmModel;
import java.util.stream.Collectors;
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

  private static final String SYSTEM_PROMPT_VARIABLE_TEMPLATE =
      """
            <VAR>
                <NAME>%s</NAME>
                <PROMPT>%s</PROMPT>
            </VAR>
            """;

  public static final String EXTRACTED_TEXT_PLACEHOLDER_FOR_MESSAGE = "{{extractedText}}";

  public static final String TAXONOMY_PLACEHOLDER_FOR_MESSAGE = "{{taxonomy}}";

  public String call(
      ExtractionRequest extractionRequest,
      String extractedText,
      BedrockRuntimeClient bedrockRuntimeClient) {
    LOGGER.debug("Calling AWS Bedrock model with extraction request: {}", extractionRequest);

    String taxonomyItems =
        extractionRequest.input().taxonomyItems().stream()
            .map(item -> String.format(SYSTEM_PROMPT_VARIABLE_TEMPLATE, item.name(), item.prompt()))
            .collect(Collectors.joining());

    ConverseData converseData = extractionRequest.input().converseData();
    LlmModel llmModel = LlmModel.fromId(converseData.modelId());

    ConverseResponse response =
        bedrockRuntimeClient.converse(
            request -> {
              String userMessage = llmModel.getMessage(extractedText, taxonomyItems);

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
                  .modelId(converseData.modelId())
                  .messages(message)
                  .inferenceConfig(
                      config ->
                          config
                              .maxTokens(converseData.maxTokens())
                              .temperature(converseData.temperature())
                              .topP(converseData.topP()));
            });

    return response.output().message().content().getFirst().text();
  }
}
