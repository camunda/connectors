/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.adhoctoolsschema.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

public record AdHocToolsSchemaResponse(List<AdHocToolDefinition> toolDefinitions) {
  @JsonInclude(JsonInclude.Include.NON_ABSENT)
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record AdHocToolDefinition(
      String name, String description, Map<String, Object> inputSchema) {}
}
