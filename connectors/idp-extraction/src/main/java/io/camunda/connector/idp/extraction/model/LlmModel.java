/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.model;

import java.util.List;
import java.util.stream.Collectors;

public class LlmModel {

  private static final String EXTRACTED_TEXT_PLACEHOLDER_FOR_MESSAGE = "{{extractedText}}";
  private static final String TAXONOMY_PLACEHOLDER_FOR_MESSAGE = "{{taxonomy}}";
  private static final String SYSTEM_PROMPT_VARIABLE_TEMPLATE =
      """
            <VAR>
                <NAME>%s</NAME>
                <PROMPT>%s</PROMPT>
            </VAR>
      """;

  public static String getExtractionSystemInstruction() {
    return """
            You are a helpful assistant tasked with extracting variables from the provided document
            using specific extraction instructions.

            You will receive a document in one of the following formats:
            - Extracted text from a document (between the <DOCUMENT_TEXT> tags)
            - An image of a document
            - A PDF file

            Your task is to extract certain variables from the document content. The extraction instructions
            are provided between the <EXTRACTION> tags. Every variable is represented by a <VAR> tag.
            Each variable has:
            - A <NAME> tag: the variable name
            - A <PROMPT> tag: instructions on which data to extract and how

            Analyze the document carefully and extract the requested variables according to the provided instructions.

            Critical: in all cases -- respond in valid JSON format, without any preamble.
            If you cannot extract a variable or all of them, just return null for that variable name.
            The response json will be validated so we need to make sure its valid. Example response:
            {
              "<NAME>": "<EXTRACTED_VALUE>",
              ...
            }
      """;
  }

  public static String getExtractionUserPrompt(List<TaxonomyItem> taxonomyItems) {
    String taxonomies =
        taxonomyItems.stream()
            .map(item -> String.format(SYSTEM_PROMPT_VARIABLE_TEMPLATE, item.name(), item.prompt()))
            .collect(Collectors.joining());
    return getMultimodalMessageTemplate().replace(TAXONOMY_PLACEHOLDER_FOR_MESSAGE, taxonomies);
  }

  public static String getExtractionUserPrompt(
      String extractedText, List<TaxonomyItem> taxonomyItems) {
    String taxonomies =
        taxonomyItems.stream()
            .map(item -> String.format(SYSTEM_PROMPT_VARIABLE_TEMPLATE, item.name(), item.prompt()))
            .collect(Collectors.joining());

    return getCommonMessageTemplate()
        .replace(EXTRACTED_TEXT_PLACEHOLDER_FOR_MESSAGE, extractedText)
        .replace(TAXONOMY_PLACEHOLDER_FOR_MESSAGE, taxonomies);
  }

  public static String getClassificationSystemPrompt(boolean autoClassify) {
    if (autoClassify) {
      return getClassificationAutoClassifyPrompt().formatted(getBaseClassificationSystemPrompt());
    }
    return getBaseClassificationSystemPrompt();
  }

  public static String getBaseClassificationSystemPrompt() {
    return """
            You are a document classification expert. You will need to analyze a document and classify it
            into one of the provided document types.

            Critical: in all cases -- respond only in this valid JSON format, without any preamble.
            The response json will be validated so we need to make sure its valid:
            {
                "extractedValue": "<one of the listed document types>",
                "confidence": "<HIGH or LOW>",
                "reasoning": "<1-2 sentences on the reasoning behind your choice and confidence level."
            }
      """;
  }

  public static String getClassificationAutoClassifyPrompt() {
    return """
            %s

            You are free to classify outside the given types if you are confident the document does not match any of the listed document types.
            You may classify it as a different type that better represents the document. Use the same case as the given types.

      """;
  }

  public static String getClassificationUserPrompt(
      List<String> documentTypes, String documentContent) {
    return """
        %s

        The following is the document content:

        %s

        """
        .formatted(getClassificationUserPrompt(documentTypes), documentContent);
  }

  public static String getClassificationUserPrompt(List<String> documentTypes) {
    return """
        Analyze this document and classify it into one of the following types:

        %s
        """
        .formatted(String.join(", ", documentTypes));
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

  public static String getJsonExtractionSystemPrompt() {
    return """
            You are a JSON cleanup expert. Your task is to extract and fix JSON from content that may contain:
            - Preambles or introductory text before the JSON
            - Markdown code blocks (```json or ```) wrapping the JSON
            - Explanatory notes or comments within or around the JSON
            - Additional text after the JSON
            - Formatting issues or invalid JSON syntax

            Your job is to:
            1. Identify the JSON content
            2. Fix any syntax errors to make it valid JSON
            3. Return ONLY the cleaned, valid JSON

            Critical: respond ONLY with the valid JSON object, without any preamble, explanation, or markdown formatting.
            Do not wrap the response in code blocks or add any additional text.
      """;
  }

  public static String getJsonExtractionUserPrompt(String content) {
    return """
        Clean up and extract the valid JSON from the following content:

        %s
        """
        .formatted(content);
  }
}
