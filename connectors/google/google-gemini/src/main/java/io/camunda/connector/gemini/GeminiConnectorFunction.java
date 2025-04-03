/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gemini;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.vertexai.api.Content;
import com.google.protobuf.util.JsonFormat;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.gemini.caller.GeminiCaller;
import io.camunda.connector.gemini.model.GeminiRequest;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import java.util.HashMap;

@OutboundConnector(
    name = "Google Gemini Outbound Connector",
    inputVariables = {"authentication", "input"},
    type = "io.camunda:google-gemini:1")
@ElementTemplate(
    engineVersion = "^8.7",
    id = "io.camunda.connectors.GoogleGemini.v1",
    name = "Google Gemini Outbound Connector",
    description =
        " A large language model (LLM) created by Google AI. It's a multimodal model, meaning it can understand"
            + " and work with different types of information like text, code, audio, images, and video",
    inputDataClass = GeminiRequest.class,
    version = 1,
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "authentication", label = "Authentication"),
      @ElementTemplate.PropertyGroup(id = "input", label = "Configure input")
    },
    documentationRef =
        "https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/google-gemini/",
    icon = "icon.svg")
public class GeminiConnectorFunction implements OutboundConnectorFunction {

  private final GeminiCaller caller;
  private final ObjectMapper objectMapper;

  public GeminiConnectorFunction(GeminiCaller caller, ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    this.caller = caller;
  }

  public GeminiConnectorFunction() {
    this.objectMapper = ConnectorsObjectMapperSupplier.getCopy();
    this.caller = new GeminiCaller(objectMapper);
  }

  @Override
  public Object execute(OutboundConnectorContext context) throws Exception {
    var geminiRequest = context.bindVariables(GeminiRequest.class);

    Content content = caller.generateContent(geminiRequest);
    String json = JsonFormat.printer().print(content);
    return objectMapper.readValue(json, HashMap.class);
  }
}
