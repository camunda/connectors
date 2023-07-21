/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.eventbridge;

import io.camunda.connector.api.annotation.Secret;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;

public class AwsEventBridgeInput {
  @NotBlank @Secret private String source;
  @NotBlank @Secret private String detailType;
  @NotBlank @Secret private String eventBusName;
  @NotNull @Secret private Object detail;

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
