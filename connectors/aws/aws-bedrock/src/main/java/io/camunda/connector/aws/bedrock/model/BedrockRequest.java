/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.aws.model.impl.AwsBaseRequest;
import io.camunda.connector.generator.java.annotation.NestedProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public class BedrockRequest<T extends RequestData> extends AwsBaseRequest {
  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME,
      include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
      property = "action")
  @JsonSubTypes(
      value = {
        @JsonSubTypes.Type(value = ConverseData.class, name = "converse"),
        @JsonSubTypes.Type(value = InvokeModelData.class, name = "invokeModel"),
      })
  @Valid
  @NotNull
  @NestedProperties(addNestedPath = false)
  private T data;

  public BedrockRequest(T invokeModelData) {
    super();
    this.data = invokeModelData;
  }

  public BedrockRequest() {
  }

  @Valid
  @NotNull
  public T getData() {
    return data;
  }
}
