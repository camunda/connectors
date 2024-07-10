/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.model;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;

@TemplateSubType(id = "converse", label = "Converse")
public final class ConverseData implements RequestData {

  @TemplateProperty(
      label = "Model id",
      group = "converse",
      id = "data.modelId1",
      feel = Property.FeelMode.optional,
      binding = @TemplateProperty.PropertyBinding(name = "data.modelId"))
  @Valid
  @NotNull
  String modelId;

  @TemplateProperty(
      label = "New Message",
      group = "converse",
      id = "data.newMessage",
      feel = Property.FeelMode.optional,
      binding = @TemplateProperty.PropertyBinding(name = "data.newMessage"))
  @Valid
  @NotBlank
  private String newMessage;

  @TemplateProperty(
      label = "Messages History",
      group = "converse",
      id = "data.messagesHistory",
      feel = Property.FeelMode.optional,
      optional = true,
      binding = @TemplateProperty.PropertyBinding(name = "data.messagesHistory"))
  @Valid
  @JsonSetter(nulls = Nulls.SKIP)
  private List<PreviousMessage> messagesHistory = new ArrayList<>();

  @TemplateProperty(
      label = "Max token returned",
      group = "converse",
      id = "data.maxTokens",
      feel = Property.FeelMode.optional,
      optional = true,
      binding = @TemplateProperty.PropertyBinding(name = "data.maxTokens"))
  private Integer maxTokens = 512;

  @TemplateProperty(
      label = "Temperature",
      group = "converse",
      id = "data.temperature",
      feel = Property.FeelMode.optional,
      optional = true,
      binding = @TemplateProperty.PropertyBinding(name = "data.temperature"))
  private Float temperature = 0.5f;

  @TemplateProperty(
      label = "top P",
      group = "converse",
      id = "data.topP",
      feel = Property.FeelMode.optional,
      optional = true,
      binding = @TemplateProperty.PropertyBinding(name = "data.topP"))
  private Float topP = 0.9f;

  @Override
  public BedrockResponse execute(
      BedrockRuntimeClient bedrockRuntimeClient, ObjectMapper mapperInstance) {
    this.messagesHistory.add(new PreviousMessage(this.newMessage, ConversationRole.USER));
    Message.Builder messageBuilder = Message.builder();
    List<Message> messages =
        this.messagesHistory.stream()
            .map(
                message ->
                    messageBuilder
                        .role(message.getRole())
                        .content(ContentBlock.fromText(message.getMessage()))
                        .build())
            .toList();
    ConverseResponse converseResponse =
        bedrockRuntimeClient.converse(
            builder ->
                builder
                    .modelId(this.modelId)
                    .messages(messages)
                    .inferenceConfig(
                        config ->
                            config
                                .temperature(this.temperature)
                                .maxTokens(this.maxTokens)
                                .topP(this.topP)
                                .build()));
    String newMessage = converseResponse.output().message().content().getFirst().text();
    this.messagesHistory.add(new PreviousMessage(newMessage, ConversationRole.ASSISTANT));
    return new ConverseWrapperResponse(this.messagesHistory, newMessage);
  }

  public void setModelId(@Valid @NotNull String modelId) {
    this.modelId = modelId;
  }

  public void setMessagesHistory(@Valid List<PreviousMessage> messagesHistory) {
    this.messagesHistory = messagesHistory;
  }

  public void setNewMessage(@Valid @NotBlank String newMessage) {
    this.newMessage = newMessage;
  }

  public void setMaxTokens(Integer maxTokens) {
    this.maxTokens = maxTokens;
  }

  public void setTemperature(Float temperature) {
    this.temperature = temperature;
  }

  public void setTopP(Float topP) {
    this.topP = topP;
  }
}
