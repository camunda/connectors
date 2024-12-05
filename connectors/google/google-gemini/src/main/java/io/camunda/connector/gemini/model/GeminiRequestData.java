/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gemini.model;

import io.camunda.connector.feel.annotation.FEEL;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record GeminiRequestData(
    @TemplateProperty(
            label = "Project ID",
            group = "input",
            description = "Project identifier.",
            feel = Property.FeelMode.disabled)
        @NotNull
        String projectId,
    @TemplateProperty(
            label = "Region",
            group = "input",
            description = "Input region.",
            feel = Property.FeelMode.disabled)
        @NotNull
        String region,
    @TemplateProperty(
            label = "Model",
            group = "input",
            description = "Select gemini model.",
            feel = Property.FeelMode.disabled,
            choices = {
              @TemplateProperty.DropdownPropertyChoice(
                  value = "GEMINI_1_5_FLASH_001",
                  label = "gemini-1.5-flash-001"),
              @TemplateProperty.DropdownPropertyChoice(
                  value = "GEMINI_1_5_FLASH_002",
                  label = "gemini-1.5-flash-002"),
              @TemplateProperty.DropdownPropertyChoice(
                  value = "GEMINI_1_5_PRO_001",
                  label = "gemini-1.5-pro-001"),
              @TemplateProperty.DropdownPropertyChoice(
                  value = "GEMINI_1_5_PRO_002",
                  label = "gemini-1.5-pro-002"),
              @TemplateProperty.DropdownPropertyChoice(
                  value = "GEMINI_1_0_PRO_001",
                  label = "gemini-1.0-pro-001"),
              @TemplateProperty.DropdownPropertyChoice(
                  value = "GEMINI_1_0_PRO_002",
                  label = "gemini-1.0-pro-002"),
              @TemplateProperty.DropdownPropertyChoice(
                  value = "GEMINI_1_0_PRO_VISION_001",
                  label = "gemini-1.0-pro-vision-001")
            })
        @NotNull
        ModelVersion model,
    @FEEL
        @TemplateProperty(
            label = "Prompt",
            group = "input",
            description = "Insert prompt.",
            feel = Property.FeelMode.required)
        @NotNull
        List<Object> prompts,
    @TemplateProperty(
            label = "System instructions",
            group = "input",
            description = "System instructions inform how the model should respond.",
            feel = Property.FeelMode.disabled,
            tooltip =
                "System instructions inform how the model should respond."
                    + " Use them to give the model context to understand the task, "
                    + "provide more custom responses and adhere to specific guidelines. "
                    + "Instructions apply each time you send a request to the model."
                    + "<a href=\"https://cloud.google.com/vertex-ai/generative-ai/docs/learn/prompts/system-instructions?hl=en\" Learn more about system instructions </a>",
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "input.model",
                    oneOf = {
                      "GEMINI_1_5_FLASH_001",
                      "GEMINI_1_5_FLASH_002",
                      "GEMINI_1_5_PRO_001",
                      "GEMINI_1_5_PRO_002",
                      "GEMINI_1_0_PRO_002"
                    }),
            optional = true)
        String systemInstrText,
    @TemplateProperty(
            label = "Grounding",
            group = "input",
            description = "Customize grounding by Vertex AI Search.",
            type = TemplateProperty.PropertyType.Boolean,
            defaultValueType = TemplateProperty.DefaultValueType.Boolean,
            tooltip =
                "Grounding connects model output to verifiable sources of information. "
                    + "This is useful in situations where accuracy and reliability are important."
                    + "<a href=\"https://cloud.google.com/vertex-ai/generative-ai/docs/grounding/overview?hl=en\" Learn more about grounding </a>",
            feel = Property.FeelMode.disabled,
            defaultValue = "false")
        boolean grounding,
    @TemplateProperty(
            label = "Vertex AI data store path",
            group = "input",
            description = "Vertex AI datastore path",
            feel = Property.FeelMode.disabled,
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "input.grounding",
                    equalsBoolean = TemplateProperty.EqualsBoolean.TRUE),
            optional = true,
            constraints =
                @TemplateProperty.PropertyConstraints(
                    pattern =
                        @TemplateProperty.Pattern(
                            value =
                                "(^projects\\/.*\\/locations\\/.*\\/collections\\/.*\\/dataStores\\/.*$)",
                            message =
                                "value must match this template: projects/{}/locations/{}/collections/{}/dataStores/{}")))
        String dataStorePath,
    @TemplateProperty(
            label = "Safety Filter Settings",
            group = "input",
            type = TemplateProperty.PropertyType.Boolean,
            defaultValueType = TemplateProperty.DefaultValueType.Boolean,
            description =
                "You can adjust the likelihood of receiving a model response that could contain harmful content."
                    + " Content is blocked based on the probability that it's harmful."
                    + "<a href=\"https://cloud.google.com/vertex-ai/generative-ai/docs/learn/responsible-ai?hl=en#safety_filters_and_attributes\" Learn more.</a>",
            feel = Property.FeelMode.disabled,
            defaultValue = "false")
        boolean safetySettings,
    @TemplateProperty(
            label = "Hate speech",
            group = "input",
            feel = Property.FeelMode.disabled,
            defaultValue = "OFF",
            choices = {
              @TemplateProperty.DropdownPropertyChoice(value = "OFF", label = "OFF"),
              @TemplateProperty.DropdownPropertyChoice(
                  value = "BLOCK_ONLY_HIGH",
                  label = "Block few"),
              @TemplateProperty.DropdownPropertyChoice(
                  value = "BLOCK_MEDIUM_AND_ABOVE",
                  label = "Block some"),
              @TemplateProperty.DropdownPropertyChoice(
                  value = "BLOCK_LOW_AND_ABOVE",
                  label = "Block most"),
            },
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "input.safetySettings",
                    equalsBoolean = TemplateProperty.EqualsBoolean.TRUE),
            tooltip =
                "You can adjust the likelihood of receiving a model response that could contain harmful content. "
                    + "Content is blocked based on the probability that it's harmful."
                    + "<a href=\"https://cloud.google.com/vertex-ai/docs/generative-ai/learn/responsible-ai?hl=en#safety_filters_and_attributes\" Learn more </a>",
            optional = true)
        BlockingDegree hateSpeech,
    @TemplateProperty(
            label = "Dangerous content",
            group = "input",
            feel = Property.FeelMode.disabled,
            defaultValue = "OFF",
            choices = {
              @TemplateProperty.DropdownPropertyChoice(value = "OFF", label = "OFF"),
              @TemplateProperty.DropdownPropertyChoice(
                  value = "BLOCK_ONLY_HIGH",
                  label = "Block few"),
              @TemplateProperty.DropdownPropertyChoice(
                  value = "BLOCK_MEDIUM_AND_ABOVE",
                  label = "Block some"),
              @TemplateProperty.DropdownPropertyChoice(
                  value = "BLOCK_LOW_AND_ABOVE",
                  label = "Block most"),
            },
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "input.safetySettings",
                    equalsBoolean = TemplateProperty.EqualsBoolean.TRUE),
            tooltip =
                "You can adjust the likelihood of receiving a model response that could contain harmful content. "
                    + "Content is blocked based on the probability that it's harmful."
                    + "<a href=\"https://cloud.google.com/vertex-ai/docs/generative-ai/learn/responsible-ai?hl=en#safety_filters_and_attributes\" Learn more </a>",
            optional = true)
        BlockingDegree dangerousContent,
    @TemplateProperty(
            label = "Sexually explicit content",
            group = "input",
            feel = Property.FeelMode.disabled,
            defaultValue = "OFF",
            choices = {
              @TemplateProperty.DropdownPropertyChoice(value = "OFF", label = "OFF"),
              @TemplateProperty.DropdownPropertyChoice(
                  value = "BLOCK_ONLY_HIGH",
                  label = "Block few"),
              @TemplateProperty.DropdownPropertyChoice(
                  value = "BLOCK_MEDIUM_AND_ABOVE",
                  label = "Block some"),
              @TemplateProperty.DropdownPropertyChoice(
                  value = "BLOCK_LOW_AND_ABOVE",
                  label = "Block most"),
            },
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "input.safetySettings",
                    equalsBoolean = TemplateProperty.EqualsBoolean.TRUE),
            tooltip =
                "You can adjust the likelihood of receiving a model response that could contain harmful content. "
                    + "Content is blocked based on the probability that it's harmful."
                    + "<a href=\"https://cloud.google.com/vertex-ai/docs/generative-ai/learn/responsible-ai?hl=en#safety_filters_and_attributes\" Learn more </a>",
            optional = true)
        BlockingDegree sexuallyExplicit,
    @TemplateProperty(
            label = "Harassment content",
            group = "input",
            feel = Property.FeelMode.disabled,
            defaultValue = "OFF",
            choices = {
              @TemplateProperty.DropdownPropertyChoice(value = "OFF", label = "OFF"),
              @TemplateProperty.DropdownPropertyChoice(
                  value = "BLOCK_ONLY_HIGH",
                  label = "Block few"),
              @TemplateProperty.DropdownPropertyChoice(
                  value = "BLOCK_MEDIUM_AND_ABOVE",
                  label = "Block some"),
              @TemplateProperty.DropdownPropertyChoice(
                  value = "BLOCK_LOW_AND_ABOVE",
                  label = "Block most"),
            },
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "input.safetySettings",
                    equalsBoolean = TemplateProperty.EqualsBoolean.TRUE),
            tooltip =
                "You can adjust the likelihood of receiving a model response that could contain harmful content. "
                    + "Content is blocked based on the probability that it's harmful."
                    + "<a href=\"https://cloud.google.com/vertex-ai/docs/generative-ai/learn/responsible-ai?hl=en#safety_filters_and_attributes\" Learn more </a>",
            optional = true)
        BlockingDegree harassment,
    @FEEL
        @TemplateProperty(
            label = "Add stop sequence",
            group = "input",
            description = "Vertex AI datastore path",
            feel = Property.FeelMode.required,
            optional = true,
            tooltip =
                "A stop sequence is a series of characters (including spaces) that stops response generation if the model encounters it."
                    + " The sequence is not included as part of the response. You can add up to five stop sequences.")
        List<String> stopSequences,
    @TemplateProperty(
            label = "Temperature",
            group = "input",
            feel = Property.FeelMode.disabled,
            optional = true,
            tooltip =
                "Temperature controls the randomness in token selection.\n"
                    + "A lower temperature is good when you expect a true or correct response. \n"
                    + "A temperature of 0 means the highest probability token is usually selected.\n"
                    + "A higher temperature can lead to diverse or unexpected results. Some models have a higher temperature max to encourage more random responses.",
            constraints =
                @TemplateProperty.PropertyConstraints(
                    pattern =
                        @TemplateProperty.Pattern(
                            value = "(^(([0-1]\\.[0-9])|([0-2]))$)|(^$)",
                            message =
                                "value must be in the range from 0 to 2 in increments of 0.1")))
        float temperature,
    @TemplateProperty(
            label = "Output token limit from 1 to 8192",
            group = "input",
            feel = Property.FeelMode.disabled,
            tooltip =
                "Output token limit determines the maximum amount of text output from one prompt. "
                    + "A token is approximately four characters.",
            constraints =
                @TemplateProperty.PropertyConstraints(
                    pattern =
                        @TemplateProperty.Pattern(
                            value =
                                "(^([1-9]|[1-9]\\d{1,2}|[1-7]\\d{3}|8(0[0-9]{2}|1[0-8][0-9]|19[0-2]))$)|(^$)",
                            message =
                                "value must be in the range from 1 to 8192 in increments of 1"),
                    notEmpty = true))
        int maxOutputTokens,
    @TemplateProperty(
            label = "Seed",
            group = "input",
            feel = Property.FeelMode.disabled,
            optional = true,
            tooltip =
                "Setting a seed value is useful when you make repeated requests and want the same model response.\n"
                    + "Deterministic outcome isn’t guaranteed. Changing the model or other settings can cause variations "
                    + "in the response even when you use the same seed value.",
            constraints =
                @TemplateProperty.PropertyConstraints(
                    pattern =
                        @TemplateProperty.Pattern(
                            value = "(^-?\\d*$)",
                            message =
                                "value must be whole numbers that range from -2,147,483,647 to 2,147,483,647")))
        int seed,
    @TemplateProperty(
            label = "Top-K",
            group = "input",
            feel = Property.FeelMode.disabled,
            optional = true,
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "input.model",
                    equals = "GEMINI_1_0_PRO_001"),
            tooltip =
                "Top-K specifies the number of candidate tokens when the model is selecting an output token. "
                    + "Use a lower value for less random responses and a higher value for more random responses.",
            constraints =
                @TemplateProperty.PropertyConstraints(
                    pattern =
                        @TemplateProperty.Pattern(
                            value = "(^([1-9]|[1-3][0-9]|40)$)(^$)",
                            message = "value must be an integer between 1 and 40")))
        int topK,
    @TemplateProperty(
            label = "Top-P",
            group = "input",
            feel = Property.FeelMode.disabled,
            optional = true,
            tooltip =
                "Top-p changes how the model selects tokens for output."
                    + " Tokens are selected from most probable to least until the sum of their probabilities equals the top-p value."
                    + " For example, if tokens A, B, and C have a probability of .3, .2, and .1 and the top-p value is .5, then the model will select either A or B as the next token (using temperature)."
                    + " For the least variable results, set top-P to 0.",
            constraints =
                @TemplateProperty.PropertyConstraints(
                    pattern =
                        @TemplateProperty.Pattern(
                            value = "(^((0\\.[0-9])|1|0)$)|(^$)",
                            message =
                                "value must be in the range from 0 to 1 in increments of 0.1")))
        float topP,
    @FEEL
        @TemplateProperty(
            label = "Function call description",
            description = "Describe function calls.",
            group = "input",
            feel = Property.FeelMode.required,
            optional = true)
        List<Object> functionCalls) {
  @Override
  public String toString() {
    return "GeminiRequestData{"
        + "projectId='"
        + projectId
        + '\''
        + ", region='"
        + region
        + '\''
        + ", model="
        + model
        + ", systemInstrText='"
        + systemInstrText
        + '\''
        + ", grounding="
        + grounding
        + ", dataStorePath='"
        + dataStorePath
        + '\''
        + ", safetySettings="
        + safetySettings
        + ", hateSpeech="
        + hateSpeech
        + ", dangerousContent="
        + dangerousContent
        + ", sexuallyExplicit="
        + sexuallyExplicit
        + ", harassment="
        + harassment
        + ", stopSequences="
        + stopSequences
        + ", temperature="
        + temperature
        + ", maxOutputTokens="
        + maxOutputTokens
        + ", seed="
        + seed
        + ", topK="
        + topK
        + ", topP="
        + topP
        + ", functionCalls="
        + functionCalls
        + '}';
  }
}
