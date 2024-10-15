/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.caller;

import io.camunda.connector.idp.extraction.model.ConverseData;
import io.camunda.connector.idp.extraction.model.ExtractionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;

import java.util.stream.Collectors;

public class BedrockCaller {

    private static final Logger LOGGER = LoggerFactory.getLogger(BedrockCaller.class);

    private static final String EXTRACTED_TEXT_PLACEHOLDER_FOR_PROMPT = "{{extractedText}}";

    private static final String TAXONOMY_PLACEHOLDER_FOR_PROMPT = "{{taxonomy}}";

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            You will receive extracted text from a PDF document. This text will be between the <DOCUMENT_TEXT> tags.
            Your task is to extract certain variables from the text. The description how to extract the variables is
            between the <EXTRACTION> tags. Every variable is represented by a <VAR> tag. Every variable has a name,
            which is represented by the <NAME> tag, as well as instructions which data to extract, which is represented
            by the <PROMPT> tag.
            
            Respond in JSON format, without any preamble. Example response:
            {
              "name": "John Smith",
              "age": 32
            }
            
            Here is the document text as well as your instructions on which variables to extract:
            <DOCUMENT_TEXT>%s</DOCUMENT_TEXT>
            <EXTRACTION>%s</EXTRACTION>
            """.formatted(EXTRACTED_TEXT_PLACEHOLDER_FOR_PROMPT, TAXONOMY_PLACEHOLDER_FOR_PROMPT);

    private static final String SYSTEM_PROMPT_VARIABLE_TEMPLATE = """
            <VAR>
                <NAME>%s</NAME>
                <PROMPT>%s</PROMPT>
            </VAR>
            """;

    public String call(
            ExtractionRequest extractionRequest,
            String extractedText,
            BedrockRuntimeClient bedrockRuntimeClient) {
        LOGGER.debug("Calling AWS Bedrock model with extraction request: {}", extractionRequest);

        String taxonomyItems = extractionRequest.getInput().taxonomyItems()
                .stream()
                .map(item -> String.format(SYSTEM_PROMPT_VARIABLE_TEMPLATE, item.name(), item.prompt()))
                .collect(Collectors.joining());

        String prompt = SYSTEM_PROMPT_TEMPLATE
                .replace(EXTRACTED_TEXT_PLACEHOLDER_FOR_PROMPT, extractedText)
                .replace(TAXONOMY_PLACEHOLDER_FOR_PROMPT, taxonomyItems);

        Message message = Message.builder()
                .content(ContentBlock.fromText(prompt))
                .role(ConversationRole.USER)
                .build();

        ConverseData converseData = extractionRequest.getInput().converseData();
        ConverseResponse response = bedrockRuntimeClient.converse(request -> request
                .modelId(converseData.modelId())
                .messages(message)
                .inferenceConfig(config -> config
                        .maxTokens(converseData.maxTokens())
                        .temperature(converseData.temperature())
                        .topP(converseData.topP())));

        return response.output().message().content().getFirst().text();
    }
}
