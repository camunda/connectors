/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.request.common.ai;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import jakarta.validation.constraints.NotNull;

@NotNull
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", defaultImpl = OpenAiRequest.class)
@JsonSubTypes({
  @JsonSubTypes.Type(value = AzureAiRequest.class, name = "azureAiFoundry"),
  @JsonSubTypes.Type(value = BedrockAiRequest.class, name = "bedrockAi"),
  @JsonSubTypes.Type(value = OpenAiRequest.class, name = "openAi"),
  @JsonSubTypes.Type(value = VertexAiRequest.class, name = "vertexAi")
})
@TemplateDiscriminatorProperty(label = "Text extraction providers", group = "ai", name = "type")
public sealed interface AiProvider
    permits AzureAiRequest, BedrockAiRequest, OpenAiRequest, VertexAiRequest {}
