/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.model;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Objects;

public class SqsInboundQueueProperties {
  @Deprecated private String region;
  @NotEmpty private String url;
  private List<String> attributeNames;
  private List<String> messageAttributeNames;

  // TODO: when migrating to template generator, default to 20 (max possible value)
  @Pattern(regexp = "^([0-9]?|1[0-9]|20|secrets\\..+)$")
  private String pollingWaitTime;

  @Deprecated
  public String getRegion() {
    return region;
  }

  @Deprecated
  public void setRegion(final String region) {
    this.region = region;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(final String url) {
    this.url = url;
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

  public String getPollingWaitTime() {
    return pollingWaitTime;
  }

  public void setPollingWaitTime(final String pollingWaitTime) {
    this.pollingWaitTime = pollingWaitTime;
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
        && Objects.equals(url, that.url)
        && Objects.equals(attributeNames, that.attributeNames)
        && Objects.equals(messageAttributeNames, that.messageAttributeNames)
        && Objects.equals(pollingWaitTime, that.pollingWaitTime);
  }

  @Override
  public int hashCode() {
    return Objects.hash(region, url, attributeNames, messageAttributeNames, pollingWaitTime);
  }

  @Override
  public String toString() {
    return "SqsInboundQueueProperties{"
        + "region='"
        + region
        + "'"
        + ", url='"
        + url
        + "'"
        + ", attributeNames="
        + attributeNames
        + ", messageAttributeNames="
        + messageAttributeNames
        + ", pollingWaitTime='"
        + pollingWaitTime
        + "'"
        + "}";
  }
}
