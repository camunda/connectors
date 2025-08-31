/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.caller;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import io.camunda.connector.idp.extraction.model.ExtractionRequestData;
import io.camunda.connector.idp.extraction.model.LlmModel;
import io.camunda.connector.idp.extraction.model.providers.OpenAiSpecProvider;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenAiSpecCaller {

  private static final Logger LOGGER = LoggerFactory.getLogger(OpenAiSpecCaller.class);

  public String call(
      ExtractionRequestData input, OpenAiSpecProvider provider, String extractedText) {
    try {
      LOGGER.debug("Creating OpenAI chat model with endpoint: {}", provider.getOpenAiEndpoint());

      OpenAiChatModel chatModel = createChatModel(input, provider);

      // Get the LLM model based on the model ID, defaulting to CLAUDE
      LlmModel llmModel = LlmModel.fromId(input.converseData().modelId());

      String systemPrompt = llmModel.getSystemPrompt();
      String userMessage = llmModel.getMessage(extractedText, input.taxonomyItems());

      LOGGER.debug("Sending request to OpenAI API");
      var response =
          llmModel.isSystemPromptAllowed()
              ? chatModel.chat(
                  List.of(SystemMessage.from(systemPrompt), UserMessage.from(userMessage)))
              : chatModel.chat(List.of(UserMessage.from(userMessage)));
      String responseText = response.aiMessage().text();

      LOGGER.debug("Received response from OpenAI API");
      return responseText;

    } catch (Exception e) {
      LOGGER.error("Error calling OpenAI API: {}", e.getMessage(), e);
      throw new RuntimeException("Failed to call OpenAI API", e);
    }
  }

  private OpenAiChatModel createChatModel(
      ExtractionRequestData input, OpenAiSpecProvider provider) {
    var converseData = input.converseData();

    var builder =
        OpenAiChatModel.builder()
            .baseUrl(provider.getOpenAiEndpoint())
            .modelName(converseData.modelId())
            .customHeaders(provider.getOpenAiHeaders())
            .responseFormat("json_object");

    if (converseData.maxTokens() != null) {
      builder.maxTokens(converseData.maxTokens());
    }

    if (converseData.temperature() != null) {
      builder.temperature(converseData.temperature().doubleValue());
    }

    if (converseData.topP() != null) {
      builder.topP(converseData.topP().doubleValue());
    }

    return builder.build();
  }
}
