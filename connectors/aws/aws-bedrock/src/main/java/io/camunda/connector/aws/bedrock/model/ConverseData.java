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
import java.util.Objects;
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
  private String modelId;

  @TemplateProperty(
      label = "New Message",
      group = "converse",
      id = "data.nextMessage",
      feel = Property.FeelMode.optional,
      binding = @TemplateProperty.PropertyBinding(name = "data.nextMessage"))
  @Valid
  @NotBlank
  private String nextMessage;

  @TemplateProperty(
      label = "Messages History",
      group = "converse",
      id = "data.messages",
      feel = Property.FeelMode.optional,
      optional = true,
      binding = @TemplateProperty.PropertyBinding(name = "data.messages"))
  @Valid
  @JsonSetter(nulls = Nulls.SKIP)
  private List<PreviousMessage> messages = new ArrayList<>();

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
    this.messages.add(new PreviousMessage(this.nextMessage, ConversationRole.USER.name()));
    Message.Builder messageBuilder = Message.builder();
    List<Message> messages =
        this.messages.stream()
            .map(
                message ->
                    messageBuilder
                        .role(ConversationRole.valueOf(message.role()))
                        .content(ContentBlock.fromText(message.message()))
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
    this.messages.add(new PreviousMessage(newMessage, ConversationRole.ASSISTANT.name()));
    return new ConverseWrapperResponse(this.messages, newMessage);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ConverseData that = (ConverseData) o;
    return Objects.equals(modelId, that.modelId)
        && Objects.equals(nextMessage, that.nextMessage)
        && Objects.equals(messages, that.messages)
        && Objects.equals(maxTokens, that.maxTokens)
        && Objects.equals(temperature, that.temperature)
        && Objects.equals(topP, that.topP);
  }

  @Override
  public int hashCode() {
    return Objects.hash(modelId, nextMessage, messages, maxTokens, temperature, topP);
  }

  public void setModelId(@Valid @NotNull String modelId) {
    this.modelId = modelId;
  }

  public void setNextMessage(@Valid @NotBlank String nextMessage) {
    this.nextMessage = nextMessage;
  }

  public void setMessages(@Valid List<PreviousMessage> messages) {
    this.messages = messages;
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

  @Override
  public String toString() {
    return "ConverseData{"
        + "modelId='"
        + modelId
        + '\''
        + ", maxTokens="
        + maxTokens
        + ", temperature="
        + temperature
        + ", topP="
        + topP
        + '}';
  }
}
