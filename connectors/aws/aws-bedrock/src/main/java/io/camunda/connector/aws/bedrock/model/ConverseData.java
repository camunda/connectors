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
import io.camunda.connector.aws.bedrock.mapper.DocumentMapper;
import io.camunda.connector.aws.bedrock.mapper.PreviousMessageMapper;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import io.camunda.document.Document;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@TemplateSubType(id = "converse", label = "Converse")
public final class ConverseData implements RequestData {

    @TemplateProperty(
            label = "Model ID",
            group = "converse",
            description =
                    "Specify the model ID. Details in the <a href=\"https://docs.aws.amazon.com/bedrock/latest/userguide/model-ids.html\" target=\"_blank\">documentation</a>",
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
            description = "Specify the next message",
            feel = Property.FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "data.nextMessage"))
    @Valid
    @NotBlank
    private String nextMessage;

    @TemplateProperty(
            label = "Message History",
            group = "converse",
            id = "data.messages",
            description = "Specify the message history, when previous context is needed",
            feel = Property.FeelMode.required,
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

    @TemplateProperty(
            label = "documents",
            group = "converse",
            id = "data.documents",
            feel = Property.FeelMode.required,
            optional = true,
            binding = @TemplateProperty.PropertyBinding(name = "data.documents"))
    private List<Document> documents;

    @Override
    public BedrockResponse execute(
            BedrockRuntimeClient bedrockRuntimeClient, ObjectMapper mapperInstance) {
        addPreviousMessages();

        Message.Builder messageBuilder = Message.builder()
                .content(this.messages.stream()
                        .map(PreviousMessage::message)
                        .map(this::mapToContentBlock)
                        .toList())
                .role(ConversationRole.USER);

        ConverseResponse converseResponse =
                bedrockRuntimeClient.converse(
                        builder ->
                                builder
                                        .modelId(this.modelId)
                                        .messages(List.of(messageBuilder.build()))
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

    private ContentBlock mapToContentBlock(Object message) {
        if (message instanceof String) {
            return ContentBlock.fromText((String) message);
        }

        if (message instanceof ImageBlock) {
            return ContentBlock.fromImage((ImageBlock) message);
        }

        return ContentBlock.fromDocument((DocumentBlock) message);
    }


    // toDo rename
    private void addPreviousMessages() {
        String user = ConversationRole.USER.name();
        this.messages.add(new PreviousMessage(this.nextMessage, user));
        List<Object> documentBlocks = DocumentMapper.mapToDocumentBlocks(this.documents);
        var previousMessages = PreviousMessageMapper.mapToPreviousMessage(documentBlocks, user);
        this.messages.addAll(previousMessages);
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

    public List<Document> getDocuments() {
        return documents;
    }

    public void setDocuments(List<Document> documents) {
        this.documents = documents;
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
