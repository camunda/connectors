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
import io.camunda.connector.api.document.Document;
import io.camunda.connector.aws.bedrock.mapper.BedrockContentMapper;
import io.camunda.connector.aws.bedrock.mapper.DocumentMapper;
import io.camunda.connector.aws.bedrock.mapper.MessageMapper;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;

@TemplateSubType(id = "converse", label = "Converse")
public final class ConverseData implements RequestData {

  @TemplateProperty(
      label = "Message History",
      group = "converse",
      id = "data.messagesHistory",
      description = "Specify the message history, when previous context is needed",
      feel = FeelMode.required,
      optional = true,
      binding = @TemplateProperty.PropertyBinding(name = "data.messagesHistory"))
  @Valid
  @JsonSetter(nulls = Nulls.SKIP)
  private List<BedrockMessage> messagesHistory = new ArrayList<>();

  @TemplateProperty(
      label = "Model ID",
      group = "converse",
      description =
          "Specify the model ID. Details in the <a href=\"https://docs.aws.amazon.com/bedrock/latest/userguide/model-ids.html\" target=\"_blank\">documentation</a>",
      id = "data.modelId1",
      feel = FeelMode.optional,
      binding = @TemplateProperty.PropertyBinding(name = "data.modelId"))
  @Valid
  @NotNull
  private String modelId;

  @TemplateProperty(
      label = "New Message",
      group = "converse",
      id = "data.newMessage",
      description = "Specify the next message",
      feel = FeelMode.optional,
      binding = @TemplateProperty.PropertyBinding(name = "data.newMessage"))
  @Valid
  @NotBlank
  private String newMessage;

  @TemplateProperty(
      label = "Max token returned",
      group = "converse",
      id = "data.maxTokens",
      feel = FeelMode.optional,
      optional = true,
      binding = @TemplateProperty.PropertyBinding(name = "data.maxTokens"))
  private Integer maxTokens = 512;

  @TemplateProperty(
      label = "Temperature",
      group = "converse",
      id = "data.temperature",
      feel = FeelMode.optional,
      optional = true,
      binding = @TemplateProperty.PropertyBinding(name = "data.temperature"))
  private Float temperature;

  @TemplateProperty(
      label = "top P",
      group = "converse",
      id = "data.topP",
      feel = FeelMode.optional,
      optional = true,
      binding = @TemplateProperty.PropertyBinding(name = "data.topP"))
  private Float topP;

  @TemplateProperty(
      label = "documents",
      group = "converse",
      id = "data.newDocuments",
      feel = FeelMode.required,
      optional = true,
      binding = @TemplateProperty.PropertyBinding(name = "data.newDocuments"))
  private List<Document> newDocuments;

  @Override
  public List<BedrockMessage> execute(
      BedrockRuntimeClient bedrockRuntimeClient, ObjectMapper mapperInstance) {
    var messageMapper = createMessageMapper();
    this.messagesHistory.add(messageMapper.mapToBedrockMessage(newDocuments, newMessage));

    List<Message> messages = messageMapper.mapToMessages(this.messagesHistory);

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

    var responseMessage = converseResponse.output().message();
    this.messagesHistory.add(messageMapper.mapToBedrockMessage(responseMessage));

    return messagesHistory;
  }

  private MessageMapper createMessageMapper() {
    var bedrockContentMapper = new BedrockContentMapper(new DocumentMapper());
    return new MessageMapper(bedrockContentMapper);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ConverseData that = (ConverseData) o;
    return Objects.equals(messagesHistory, that.messagesHistory)
        && Objects.equals(modelId, that.modelId)
        && Objects.equals(newMessage, that.newMessage)
        && Objects.equals(maxTokens, that.maxTokens)
        && Objects.equals(temperature, that.temperature)
        && Objects.equals(topP, that.topP)
        && Objects.equals(newDocuments, that.newDocuments);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        messagesHistory, modelId, newMessage, maxTokens, temperature, topP, newDocuments);
  }

  public void setModelId(@Valid @NotNull String modelId) {
    this.modelId = modelId;
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

  public List<Document> getNewDocuments() {
    return newDocuments;
  }

  public void setNewDocuments(List<Document> newDocuments) {
    this.newDocuments = newDocuments;
  }

  public List<BedrockMessage> getMessagesHistory() {
    return this.messagesHistory;
  }

  public void setMessagesHistory(List<BedrockMessage> messagesHistory) {
    this.messagesHistory = messagesHistory;
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
