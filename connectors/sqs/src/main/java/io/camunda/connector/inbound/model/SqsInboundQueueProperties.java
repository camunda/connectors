/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.model;

import io.camunda.connector.api.annotation.Secret;
import java.util.List;
import java.util.Objects;
import javax.validation.constraints.NotEmpty;

public class SqsInboundQueueProperties {
  @NotEmpty @Secret private String region;
  @NotEmpty @Secret private String name;
  @Secret private List<String> attributeNames;
  @Secret private List<String> messageAttributeNames;

  public String getRegion() {
    return region;
  }

  public void setRegion(final String region) {
    this.region = region;
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public boolean isContainAttributeNames() {
    return attributeNames != null && !attributeNames.isEmpty();
  }

  public boolean isContainMessageAttributeNames() {
    return messageAttributeNames != null && !messageAttributeNames.isEmpty();
  }

  public List<String> getAttributeNames() {
    return attributeNames;
  }

  public void setAttributeNames(final List<String> attributeNames) {
    this.attributeNames = attributeNames;
  }

  public List<String> getMessageAttributeNames() {
    return messageAttributeNames;
  }

  public void setMessageAttributeNames(final List<String> messageAttributeNames) {
    this.messageAttributeNames = messageAttributeNames;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SqsInboundQueueProperties that = (SqsInboundQueueProperties) o;
    return Objects.equals(region, that.region)
        && Objects.equals(name, that.name)
        && Objects.equals(attributeNames, that.attributeNames)
        && Objects.equals(messageAttributeNames, that.messageAttributeNames);
  }

  @Override
  public int hashCode() {
    return Objects.hash(region, name, attributeNames, messageAttributeNames);
  }

  @Override
  public String toString() {
    return "SqsInboundQueueProperties{"
        + "region='"
        + region
        + "'"
        + ", name='"
        + name
        + "'"
        + ", attributeNames="
        + attributeNames
        + ", messageAttributeNames="
        + messageAttributeNames
        + "}";
  }
}
