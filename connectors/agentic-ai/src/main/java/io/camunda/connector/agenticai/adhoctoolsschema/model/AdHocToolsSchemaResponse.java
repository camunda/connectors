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

public record AdHocToolsSchemaResponse(
    List<AdHocToolDefinition> toolDefinitions, List<GatewayToolDefinition> gatewayToolDefinitions) {

  /** A tool definition, being a single tool available within the ad-hoc sub-process. */
  @JsonInclude(JsonInclude.Include.NON_ABSENT)
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record AdHocToolDefinition(
      String name, String description, Map<String, Object> inputSchema) {}

  /**
   * A gateway tool definition, being an entrypoint for multiple tools. Tool discovery needs to be
   * done at runtime.
   *
   * <p>Example use case: MCP Client is a gateway to multiple tools exposed by the connected MCP
   * server
   */
  @JsonInclude(JsonInclude.Include.NON_ABSENT)
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record GatewayToolDefinition(
      String name, String description, Map<String, Object> properties) {}
}
