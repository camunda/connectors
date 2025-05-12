/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tools;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;

public abstract class ToolSpecificationMixin {

  @JsonCreator
  public ToolSpecificationMixin(
      @JsonProperty("name") String name,
      @JsonProperty("description") String description,
      @JsonProperty("parameters") JsonSchemaElement parameters) {}

  @JsonProperty("name")
  public abstract String name();

  @JsonProperty("description")
  public abstract String description();

  @JsonProperty("parameters")
  public abstract JsonSchemaElement parameters();
}
