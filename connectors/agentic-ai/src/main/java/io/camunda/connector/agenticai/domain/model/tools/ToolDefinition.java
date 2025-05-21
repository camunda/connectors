/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.domain.model.tools;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import io.camunda.connector.agenticai.model.AgenticAiRecordBuilder;
import java.util.Map;

@AgenticAiRecordBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(builder = ToolDefinition.ToolDefinitionJacksonProxyBuilder.class)
public record ToolDefinition(String name, String description, Map<String, Object> inputSchema)
    implements ToolDefinitionBuilder.With {

  public static ToolDefinitionBuilder builder() {
    return ToolDefinitionBuilder.builder();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class ToolDefinitionJacksonProxyBuilder extends ToolDefinitionBuilder {}
}
