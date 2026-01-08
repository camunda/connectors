/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.model.result;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import io.camunda.connector.agenticai.model.AgenticAiRecord;

@AgenticAiRecord
@JsonDeserialize(builder = ResourceTemplate.ResourceTemplateBuilderJacksonProxyBuilder.class)
public record ResourceTemplate(String uriTemplate, String name, String description, String mimeType) {

  public static ResourceTemplateBuilder builder() {
    return ResourceTemplateBuilder.builder();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class ResourceTemplateBuilderJacksonProxyBuilder
      extends ResourceTemplateBuilder {}
}
