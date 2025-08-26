/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.adhoctoolsschema.model;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyConstraints;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AdHocToolsSchemaRequest(@Valid @NotNull AdHocToolsSchemaRequestData data) {
  public record AdHocToolsSchemaRequestData(
      @NotBlank
          @TemplateProperty(
              group = "tools",
              label = "Ad-hoc sub-process ID",
              description = "The ID of the sub-process containing the tools to be called",
              constraints = @PropertyConstraints(notEmpty = true))
          String containerElementId) {}
}
