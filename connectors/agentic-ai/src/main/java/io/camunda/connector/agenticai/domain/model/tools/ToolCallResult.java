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
@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(builder = ToolCallResult.ToolCallResultJacksonProxyBuilder.class)
public record ToolCallResult(String id, String name, Map<String, Object> data)
    implements ToolCallResultBuilder.With {

  public static ToolCallResultBuilder builder() {
    return ToolCallResultBuilder.builder();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class ToolCallResultJacksonProxyBuilder extends ToolCallResultBuilder {}
}
