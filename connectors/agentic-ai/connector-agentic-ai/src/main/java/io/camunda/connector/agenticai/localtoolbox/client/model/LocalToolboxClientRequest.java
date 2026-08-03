/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.localtoolbox.client.model;

import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyConstraints;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public record LocalToolboxClientRequest(@Valid @NotNull LocalToolboxClientRequestData data) {

  public record LocalToolboxClientRequestData(
      @TemplateProperty(
              group = "toolbox",
              label = "Process ID",
              description = "The BPMN process ID of the toolbox process to invoke.",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              constraints = @PropertyConstraints(notEmpty = true))
          @NotBlank
          String processId,
      @TemplateProperty(
              group = "toolbox",
              label = "Version",
              description =
                  "Pins the toolbox process to a specific version. Leave empty to always use the latest deployed version.",
              tooltip =
                  "The discovered tool schema is resolved once, at agent initialization. Pinning a"
                      + " version keeps that schema stable for the life of the conversation, even"
                      + " if a new toolbox version is deployed later.",
              type = TemplateProperty.PropertyType.Number,
              feel = FeelMode.optional,
              optional = true)
          @Nullable Integer version,
      @TemplateProperty(
              group = "toolbox",
              label = "Tool container element ID",
              description =
                  "The ID of the ad-hoc sub-process inside the toolbox process whose tool elements"
                      + " should be discovered and exposed to the calling agent.",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              constraints = @PropertyConstraints(notEmpty = true))
          @NotBlank
          String containerElementId,
      @TemplateProperty(
              group = "toolbox",
              label = "Meta",
              description =
                  "Deterministic (non-LLM) variables forwarded unmodified to the toolbox process"
                      + " instance alongside the tool call, bypassing the LLM entirely.",
              feel = FeelMode.required,
              optional = true)
          @Nullable Map<String, Object> meta,
      @Valid @NotNull LocalToolboxOperationConfiguration operation) {

    public record LocalToolboxOperationConfiguration(
        @TemplateProperty(
                group = "operation",
                label = "Method",
                description = "The local toolbox operation to be performed.",
                type = TemplateProperty.PropertyType.String,
                feel = FeelMode.optional,
                defaultValue = "=toolCall.method",
                constraints = @PropertyConstraints(notEmpty = true))
            @NotBlank
            String method,
        @TemplateProperty(
                group = "operation",
                label = "Parameters",
                description = "The parameters to be passed to the operation.",
                feel = FeelMode.required,
                defaultValue = "=toolCall.params",
                optional = true)
            @Nullable Map<String, Object> params) {}
  }
}
