/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.adhoctoolsschema.model;

import io.camunda.connector.generator.java.annotation.TemplateProperty;

public record AdHocToolsSchemaRequest(AdHocToolsSchemaRequestData data) {
  public record AdHocToolsSchemaRequestData(
      @TemplateProperty(
              group = "tools",
              label = "Ad-hoc subprocess ID containing tools",
              description = "The ID of the subprocess containing the tools to be called")
          String containerElementId) {}
}
