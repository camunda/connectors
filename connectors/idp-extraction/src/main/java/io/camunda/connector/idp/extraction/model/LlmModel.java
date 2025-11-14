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
  CLAUDE("anthropic", getCommonSystemPrompt(), getCommonMessageTemplate(), false),
  GEMINI(
      "gemini",
      """
            %s

            Respond with a json object, not an array.
      """
          .formatted(getCommonSystemPrompt()),
      getMultimodalMessageTemplate(),
      true),
  LLAMA(
      "meta",
      """
            <|begin_of_text|><|start_header_id|>system<|end_header_id|>
            %s

            You are a helpful assistant tasked with extracting variables from the provided text
            using specific extraction instructions.
            The output must be a flat JSON object, where each key is the variable name and its
            corresponding value is the extracted value.

            Your response should strictly adhere to this format:
            {
              "variable1": "value1",
              "variable2": "value2"
            }

            Do not include any preamble, explanations, or additional text, and ensure the response is valid JSON.
      """
          .formatted(getCommonSystemInstruction()),
      """
            <|eot_id|><|start_header_id|>user<|end_header_id|>
            Given the following functions, extract the needed variables based on the given prompt.
            {
              "name": "variable_extract",
              "description": "extract value from a text using the provided prompts.",
              "input_schema": {
                "type": "object",
                "properties": {
                    "name": {
                      "type": "string",
                      "description": "the corresponding name in the variable taxonomy"
                    },
                    "value": {
                      "type": "any",
                      "description": "the extracted value found in the document text using the prompts"
                    }
                  }
              }
            }

            Question: Given the variable_extract function and document text, extract the variables.
            %s
            Only respond with the function output, no preamble.
            <|eot_id|><|start_header_id|>assistant<|end_header_id|>
      """
          .formatted(getCommonMessageTemplate()),
      false),
  TITAN("amazon", getCommonSystemPrompt(), getCommonMessageTemplate(), false);

  private final String vendor;
  private final String systemPrompt;
  private final String messageTemplate;
  private final boolean multimodal;

  private static final String EXTRACTED_TEXT_PLACEHOLDER_FOR_MESSAGE = "{{extractedText}}";
  private static final String TAXONOMY_PLACEHOLDER_FOR_MESSAGE = "{{taxonomy}}";
  private static final String SYSTEM_PROMPT_VARIABLE_TEMPLATE =
      """
            <VAR>
                <NAME>%s</NAME>
                <PROMPT>%s</PROMPT>
            </VAR>
      """;

  LlmModel(String vendor, String systemPrompt, String messageTemplate, boolean multimodal) {
    this.vendor = vendor;
    this.systemPrompt = systemPrompt;
    this.messageTemplate = messageTemplate;
    this.multimodal = multimodal;
  }

  public String getSystemPrompt() {
    return systemPrompt;
  }

  public String getVendor() {
    return vendor;
  }

  public boolean isMultimodal() {
    return multimodal;
  }

  public String getMessage(List<TaxonomyItem> taxonomyItems) {
    String taxonomies =
        taxonomyItems.stream()
            .map(item -> String.format(SYSTEM_PROMPT_VARIABLE_TEMPLATE, item.name(), item.prompt()))
            .collect(Collectors.joining());
    return messageTemplate.replace(TAXONOMY_PLACEHOLDER_FOR_MESSAGE, taxonomies);
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
    } else if (modelId.contains(GEMINI.getVendor())) {
      return GEMINI;
    } else {
      return CLAUDE;
    }
  }

  public static String getFormatSystemPrompt() {
    return """
            %s

            Respond in this JSON format, without any preamble:
            {
                "documentType": "<one of the listed document types>",
                "confidence": "<HIGH or LOW>",
                "reasoning": "<1-2 sentences on the reasoning behind your choice and confidence level."
            }
      """;
  }

  public static String getClasssificationSystemPromptWithUnknownOption() {
    return """
            %s

            If you are confident the document does not match any of the listed document types,
            you may classify it as a different type that better represents the document.

      """;
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

  private static String getMultimodalMessageTemplate() {
    return """
            Here is the instructions on which variables to extract:
            <EXTRACTION>%s</EXTRACTION>
      """
        .formatted(TAXONOMY_PLACEHOLDER_FOR_MESSAGE);
  }
}
