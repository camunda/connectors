/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.gemini.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.vertexai.api.FunctionDeclaration;
import com.google.protobuf.util.JsonFormat;
import java.util.List;
import java.util.Optional;

public class FunctionDeclarationMapper {

  public static final String DESERIALIZATION_EX_MSG =
      "Exception during function call deserialization";

  private final ObjectMapper objectMapper;

  public FunctionDeclarationMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public List<FunctionDeclaration> map(List<Object> functionCalls) {
    return Optional.ofNullable(functionCalls).orElse(List.of()).stream()
        .map(call -> convertToFunctionDeclaration(call, objectMapper))
        .toList();
  }

  private FunctionDeclaration convertToFunctionDeclaration(Object call, ObjectMapper objectMapper) {
    try {
      String jsonString = objectMapper.writeValueAsString(objectMapper.valueToTree(call));
      FunctionDeclaration.Builder builder = FunctionDeclaration.newBuilder();
      JsonFormat.parser().merge(jsonString, builder);
      return builder.build();
    } catch (Exception e) {
      throw new RuntimeException(DESERIALIZATION_EX_MSG, e);
    }
  }
}
