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
      description =
          "Enter the event source value. Details in the <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/amazon-eventbridge/?awseventbridge=outbound\" target=\"_blank\">documentation</a>")
  @NotBlank
  private String source;

  @TemplateProperty(
      group = "eventDetails",
      label = "Detail type",
      description =
          "Enter the event detail type. Details in the <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/amazon-eventbridge/?awseventbridge=outbound\" target=\"_blank\">documentation</a>")
  @NotBlank
  private String detailType;

  @TemplateProperty(
      group = "eventDetails",
      label = "Event bus name",
      type = TemplateProperty.PropertyType.Text,
      feel = FeelMode.required,
      description =
          "Enter the event bus name. Details in the <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/amazon-eventbridge/?awseventbridge=outbound\" target=\"_blank\">documentation</a>")
  @NotBlank
  private String eventBusName;

  @TemplateProperty(
      group = "eventPayload",
      label = "Event payload",
      description =
          "Provide the payload for the event as JSON. Details in the <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/amazon-eventbridge/?awseventbridge=outbound\" target=\"_blank\">documentation</a>")
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
