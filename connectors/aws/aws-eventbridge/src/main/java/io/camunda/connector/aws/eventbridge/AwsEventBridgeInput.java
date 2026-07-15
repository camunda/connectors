/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.eventbridge;

import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;

public class AwsEventBridgeInput {
  @TemplateProperty(
      group = "eventDetails",
      label = "Source",
      tooltip =
          "Value that identifies the service that generated the event. See the <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/amazon-eventbridge/?awseventbridge=outbound\" target=\"_blank\">Amazon EventBridge documentation</a>.")
  @NotBlank
  private String source;

  @TemplateProperty(
      group = "eventDetails",
      label = "Detail type",
      tooltip =
          "Type of event being sent. See the <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/amazon-eventbridge/?awseventbridge=outbound\" target=\"_blank\">Amazon EventBridge documentation</a>.")
  @NotBlank
  private String detailType;

  @TemplateProperty(
      group = "eventDetails",
      label = "Event bus name",
      type = TemplateProperty.PropertyType.Text,
      feel = FeelMode.required,
      tooltip =
          "Name of the destination event bus. See the <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/amazon-eventbridge/?awseventbridge=outbound\" target=\"_blank\">Amazon EventBridge documentation</a>.")
  @NotBlank
  private String eventBusName;

  @TemplateProperty(
      group = "eventPayload",
      label = "Event payload",
      placeholder = "{\"key\": \"value\"}",
      tooltip =
          "Payload must be provided as JSON. See the <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/amazon-eventbridge/?awseventbridge=outbound\" target=\"_blank\">Amazon EventBridge event payload</a> documentation.")
  @NotNull
  private Object detail;

  public String getSource() {
    return source;
  }

  public void setSource(final String source) {
    this.source = source;
  }

  public String getDetailType() {
    return detailType;
  }

  public void setDetailType(final String detailType) {
    this.detailType = detailType;
  }

  public String getEventBusName() {
    return eventBusName;
  }

  public void setEventBusName(final String eventBusName) {
    this.eventBusName = eventBusName;
  }

  public Object getDetail() {
    return detail;
  }

  public void setDetail(final Object detail) {
    this.detail = detail;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final AwsEventBridgeInput input = (AwsEventBridgeInput) o;
    return Objects.equals(source, input.source)
        && Objects.equals(detailType, input.detailType)
        && Objects.equals(eventBusName, input.eventBusName)
        && Objects.equals(detail, input.detail);
  }

  @Override
  public int hashCode() {
    return Objects.hash(source, detailType, eventBusName, detail);
  }

  @Override
  public String toString() {
    return "AwsEventBridgeInput{"
        + "source='"
        + source
        + "'"
        + ", detailType='"
        + detailType
        + "'"
        + ", eventBusName='"
        + eventBusName
        + "'"
        + ", detail="
        + detail
        + "}";
  }
}
