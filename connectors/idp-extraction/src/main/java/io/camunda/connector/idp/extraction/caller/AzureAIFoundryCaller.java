/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.caller;

import com.azure.ai.inference.ChatCompletionsClient;
import com.azure.ai.inference.models.ChatCompletions;
import com.azure.ai.inference.models.ChatCompletionsOptions;
import com.azure.ai.inference.models.ChatRequestMessage;
import com.azure.ai.inference.models.ChatRequestSystemMessage;
import com.azure.ai.inference.models.ChatRequestUserMessage;
import com.azure.ai.openai.OpenAIClient;
import io.camunda.connector.idp.extraction.model.ExtractionRequestData;
import io.camunda.connector.idp.extraction.model.LlmModel;
import io.camunda.connector.idp.extraction.model.providers.AzureProvider;
import io.camunda.connector.idp.extraction.supplier.AzureAIFoundrySupplier;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AzureAIFoundryCaller {

  private static final Logger LOGGER = LoggerFactory.getLogger(AzureAIFoundryCaller.class);

  public String call(ExtractionRequestData input, AzureProvider baseRequest, String extractedText) {
    LOGGER.debug("Calling Azure AI Foundry with extraction request data: {}", input);

    try {

      // Determine the LLM model from the model ID
      LlmModel llmModel = LlmModel.fromId(input.converseData().modelId());

      if (baseRequest.getAiFoundryConfig().isUsingOpenAI()) {
        return callOpenAIClient(input, baseRequest, extractedText, llmModel);
      } else {
        return callChatCompletionsClient(input, baseRequest, extractedText, llmModel);
      }

    } catch (Exception e) {
      LOGGER.error("Error calling Azure AI Foundry", e);
      throw new RuntimeException("Failed to call Azure AI Foundry: " + e.getMessage(), e);
    }
  }

  private String callOpenAIClient(
      ExtractionRequestData input,
      AzureProvider baseRequest,
      String extractedText,
      LlmModel llmModel) {
    OpenAIClient client = AzureAIFoundrySupplier.getOpenAIClient(baseRequest);

    // Build the messages for the chat completion
    List<com.azure.ai.openai.models.ChatRequestMessage> messages =
        buildOpenAIMessages(input, extractedText, llmModel);

    // Create the chat completions options for OpenAI client
    com.azure.ai.openai.models.ChatCompletionsOptions options =
        new com.azure.ai.openai.models.ChatCompletionsOptions(messages);
    options.setModel(input.converseData().modelId());
    options.setTemperature(input.converseData().temperature().doubleValue());
    options.setMaxTokens(input.converseData().maxTokens());
    options.setTopP(input.converseData().topP().doubleValue());

    // Make the call to Azure AI Foundry
    com.azure.ai.openai.models.ChatCompletions response =
        client.getChatCompletions(input.converseData().modelId(), options);

    // Extract and return the response text
    String responseText = response.getChoices().get(0).getMessage().getContent();
    LOGGER.debug("Azure AI Foundry response: {}", responseText);

    return responseText;
  }

  private String callChatCompletionsClient(
      ExtractionRequestData input,
      AzureProvider baseRequest,
      String extractedText,
      LlmModel llmModel) {
    ChatCompletionsClient client = AzureAIFoundrySupplier.getChatCompletionsClient(baseRequest);

    // Build the messages for the chat completion
    List<ChatRequestMessage> messages = buildMessages(input, extractedText, llmModel);

    // Create the chat completions options for ChatCompletions client
    ChatCompletionsOptions options = new ChatCompletionsOptions(messages);
    options.setModel(input.converseData().modelId());
    options.setTemperature(input.converseData().temperature().doubleValue());
    options.setMaxTokens(input.converseData().maxTokens());
    options.setTopP(input.converseData().topP().doubleValue());

    // Make the call to Azure AI Foundry
    ChatCompletions response = client.complete(options);

    // Extract and return the response text
    String responseText = response.getChoices().get(0).getMessage().getContent();
    LOGGER.debug("Azure AI Foundry response: {}", responseText);

    return responseText;
  }

  private List<ChatRequestMessage> buildMessages(
      ExtractionRequestData input, String extractedText, LlmModel llmModel) {
    List<ChatRequestMessage> messages = new ArrayList<>();

    // Build the user message using the LlmModel template
    String userMessage = llmModel.getMessage(extractedText, input.taxonomyItems());

    // Check if system prompts are allowed for this model
    if (llmModel.isSystemPromptAllowed()) {
      // Add system message first
      messages.add(new ChatRequestSystemMessage(llmModel.getSystemPrompt()));
      // Add user message
      messages.add(new ChatRequestUserMessage(userMessage));
    } else {
      // For models that don't support system prompts, combine system and user messages
      String combinedMessage = String.format("%s%n%s", llmModel.getSystemPrompt(), userMessage);
      messages.add(new ChatRequestUserMessage(combinedMessage));
    }

    return messages;
  }

  private List<com.azure.ai.openai.models.ChatRequestMessage> buildOpenAIMessages(
      ExtractionRequestData input, String extractedText, LlmModel llmModel) {
    List<com.azure.ai.openai.models.ChatRequestMessage> messages = new ArrayList<>();

    // Build the user message using the LlmModel template
    String userMessage = llmModel.getMessage(extractedText, input.taxonomyItems());

    // Check if system prompts are allowed for this model
    if (llmModel.isSystemPromptAllowed()) {
      // Add system message first
      messages.add(
          new com.azure.ai.openai.models.ChatRequestSystemMessage(llmModel.getSystemPrompt()));
      // Add user message
      messages.add(new com.azure.ai.openai.models.ChatRequestUserMessage(userMessage));
    } else {
      // For models that don't support system prompts, combine system and user messages
      String combinedMessage = String.format("%s%n%s", llmModel.getSystemPrompt(), userMessage);
      messages.add(new com.azure.ai.openai.models.ChatRequestUserMessage(combinedMessage));
    }

    return messages;
  }
}
