/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

@TemplateSubType(id = "invokeModel", label = "Invoke Model")
public final class InvokeModelData implements RequestData {

  @TemplateProperty(
      label = "Model ID",
      group = "invokeModel",
      id = "data.modelId0",
      description =
          "Specify the model ID. Details in the <a href=\"https://docs.aws.amazon.com/bedrock/latest/userguide/model-ids.html\" target=\"_blank\">documentation</a>",
      feel = FeelMode.optional,
      binding = @TemplateProperty.PropertyBinding(name = "data.modelId"))
  @Valid
  @NotNull
  String modelId;

  @TemplateProperty(
      label = "Payload",
      group = "invokeModel",
      description =
          "Specify the payload. Details in the <a href=\"https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters.html\" target=\"_blank\">documentation</a>",
      id = "data.payload",
      feel = FeelMode.required,
      binding = @TemplateProperty.PropertyBinding(name = "data.payload"))
  @Valid
  @NotNull
  Object payload;

  @Override
  public BedrockResponse execute(
      BedrockRuntimeClient bedrockRuntimeClient, ObjectMapper mapperInstance) {
    try {
      String body = mapperInstance.writeValueAsString(this.payload);
      String response =
          bedrockRuntimeClient
              .invokeModel(
                  builder -> builder.body(SdkBytes.fromUtf8String(body)).modelId(this.modelId))
              .body()
              .asUtf8String();
      return new InvokeModelWrappedResponse(mapperInstance.readValue(response, Object.class));
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e.getOriginalMessage(), e);
    }
  }

  public void setModelId(@Valid @NotNull String modelId) {
    this.modelId = modelId;
  }

  public void setPayload(@Valid @NotNull Object payload) {
    this.payload = payload;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    InvokeModelData that = (InvokeModelData) o;
    return Objects.equals(modelId, that.modelId) && Objects.equals(payload, that.payload);
  }

  @Override
  public int hashCode() {
    return Objects.hash(modelId, payload);
  }

  @Override
  public String toString() {
    return "InvokeModelData{" + "modelId='" + modelId + '\'' + '}';
  }
}
