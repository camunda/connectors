/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request;

import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.TextResponseFormatConfiguration;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record OutboundConnectorResponseConfiguration(
    @Valid @NotNull ResponseFormatConfiguration format,
    @TemplateProperty(
            group = "response",
            label = "Include assistant message",
            description = "Include the full assistant message as part of the result object.",
            tooltip =
                "In addition to the text content, the assistant message may include multiple additional content blocks "
                    + "and metadata (such as token usage). The message will be available as <code>response.responseMessage</code>.",
            type = TemplateProperty.PropertyType.Boolean,
            optional = true)
        Boolean includeAssistantMessage)
    implements ResponseConfiguration {

  public OutboundConnectorResponseConfiguration {
    if (format == null) {
      format = new TextResponseFormatConfiguration(false);
    }
  }
}
