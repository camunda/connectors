/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.model;

import java.util.List;
import java.util.stream.Collectors;

public enum LlmModel {
  CLAUDE("anthropic", getCommonSystemPrompt(), getCommonMessageTemplate()),
  LLAMA(
      "meta",
      """
            <|begin_of_text|><|start_header_id|>system<|end_header_id|>
            %s

            You are a helpful assistant with tool calling capabilities.
      """
          .formatted(getCommonSystemInstruction()),
      """
            <|eot_id|><|start_header_id|>user<|end_header_id|>
            Given the following functions, please respond with a JSON for a function call with its proper arguments
            that best answers the given prompts.

            Respond in JSON format, without any preamble. Example response:
            {
              "name": "John Smith",
              "age": 32
            }

            {
                "type": "function",
                "function": {
                "name": "extract text variables",
                "description": "extract every variable based on the given prompt in the question",
                "parameters": {
                    "type": "object"
                    }
                }
            }

            Question: Given the following functions, please respond with a JSON for a function call with its proper arguments
            that best answers the given prompt.
            %s
      """
          .formatted(getCommonMessageTemplate())),
  TITAN("amazon", getCommonSystemPrompt(), getCommonMessageTemplate());

  private final String vendor;
  private final String systemPrompt;
  private final String messageTemplate;

  private static final String EXTRACTED_TEXT_PLACEHOLDER_FOR_MESSAGE = "{{extractedText}}";
  private static final String TAXONOMY_PLACEHOLDER_FOR_MESSAGE = "{{taxonomy}}";
  private static final String SYSTEM_PROMPT_VARIABLE_TEMPLATE =
      """
            <VAR>
                <NAME>%s</NAME>
                <PROMPT>%s</PROMPT>
            </VAR>
      """;

  LlmModel(String vendor, String systemPrompt, String messageTemplate) {
    this.vendor = vendor;
    this.systemPrompt = systemPrompt;
    this.messageTemplate = messageTemplate;
  }

  public String getSystemPrompt() {
    return systemPrompt;
  }

  public String getVendor() {
    return vendor;
  }

  public String getMessage(String extractedText, List<TaxonomyItem> taxonomyItems) {
    String taxonomies =
        taxonomyItems.stream()
            .map(item -> String.format(SYSTEM_PROMPT_VARIABLE_TEMPLATE, item.name(), item.prompt()))
            .collect(Collectors.joining());

    return messageTemplate
        .replace(EXTRACTED_TEXT_PLACEHOLDER_FOR_MESSAGE, extractedText)
        .replace(TAXONOMY_PLACEHOLDER_FOR_MESSAGE, taxonomies);
  }

  public boolean isSystemPromptAllowed() {
    return this != TITAN;
  }

  public static LlmModel fromId(String id) {
    String modelId = id.toLowerCase();
    if (modelId.contains(CLAUDE.getVendor())) {
      return CLAUDE;
    } else if (modelId.contains(LLAMA.getVendor())) {
      return LLAMA;
    } else if (modelId.contains(TITAN.getVendor())) {
      return TITAN;
    } else {
      return CLAUDE;
    }
  }

  private static String getCommonSystemInstruction() {
    return """
            You will receive extracted text from a PDF document. This text will be between the <DOCUMENT_TEXT> tags.
            Your task is to extract certain variables from the text. The description how to extract the variables is
            between the <EXTRACTION> tags. Every variable is represented by a <VAR> tag. Every variable has a name,
            which is represented by the <NAME> tag, as well as instructions which data to extract, which is represented
            by the <PROMPT> tag.
      """;
  }

  private static String getCommonSystemPrompt() {
    return """
            %s

            Respond in JSON format, without any preamble. Example response:
            {
              "name": "John Smith",
              "age": 32
            }
      """
        .formatted(getCommonSystemInstruction());
  }

  private static String getCommonMessageTemplate() {
    return """
            Here is the document text as well as your instructions on which variables to extract:
            <DOCUMENT_TEXT>%s</DOCUMENT_TEXT>
            <EXTRACTION>%s</EXTRACTION>
      """
        .formatted(EXTRACTED_TEXT_PLACEHOLDER_FOR_MESSAGE, TAXONOMY_PLACEHOLDER_FOR_MESSAGE);
  }
}
