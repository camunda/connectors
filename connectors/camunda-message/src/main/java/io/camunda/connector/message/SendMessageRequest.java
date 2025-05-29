/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.message;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.util.Map;

public record SendMessageRequest(
    @NotBlank String messageName,
    @TemplateProperty(optional = true) String correlationKey,
    @TemplateProperty(optional = true, label = "Payload") Map<String, Object> variables,
    @TemplateProperty(id = "correlationType") CorrelationType correlationType,
    @TemplateProperty(optional = true) String tenantId,
    @TemplateProperty(optional = true) Duration requestTimeout) {

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = CorrelationType.Publish.class, name = "publish"),
    @JsonSubTypes.Type(value = CorrelationType.CorrelateWithResult.class, name = "correlate")
  })
  @TemplateDiscriminatorProperty(
      label = "Correlation mode",
      name = "type",
      defaultValue = "publish",
      description =
          "Send message with <a href='https://docs.camunda.io/docs/components/concepts/messages/#message-buffering' target='_blank'>buffer (publish)</a> or with <a href='https://docs.camunda.io/docs/components/concepts/messages/#message-response' target='_blank'>result (correlate)</a>")
  public sealed interface CorrelationType
      permits CorrelationType.CorrelateWithResult, CorrelationType.Publish {

    @TemplateSubType(id = "publish", label = "publish message (with buffer)")
    record Publish(
        @TemplateProperty(
                optional = true,
                label = "Time to live (as ISO 8601)",
                description = "Duration for which the message remains buffered")
            Duration timeToLive,
        @TemplateProperty(optional = true, label = "Message id (optional)") String messageId)
        implements CorrelationType {}

    @TemplateSubType(id = "correlate", label = "correlate message (with result)")
    record CorrelateWithResult() implements CorrelationType {}
  }
}
