/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.adhoctoolsschema.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import io.camunda.connector.agenticai.aiagent.model.tool.GatewayToolDefinition;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.common.AgenticAiRecord;
import java.util.List;

@AgenticAiRecord
@JsonDeserialize(
    builder = AdHocToolsSchemaResponse.AdHocToolsSchemaResponseJacksonProxyBuilder.class)
public record AdHocToolsSchemaResponse(
    List<ToolDefinition> toolDefinitions,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) List<GatewayToolDefinition> gatewayToolDefinitions)
    implements AdHocToolsSchemaResponseBuilder.With {

  public static AdHocToolsSchemaResponseBuilder builder() {
    return AdHocToolsSchemaResponseBuilder.builder();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class AdHocToolsSchemaResponseJacksonProxyBuilder
      extends AdHocToolsSchemaResponseBuilder {}
}
