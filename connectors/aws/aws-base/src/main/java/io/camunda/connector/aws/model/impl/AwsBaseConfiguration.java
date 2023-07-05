/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.model.impl;

import io.camunda.connector.aws.model.AwsConfiguration;
import java.util.Objects;

public class AwsBaseConfiguration implements AwsConfiguration {
  private String region;

  public String getRegion() {
    return region;
  }

  public void setRegion(final String region) {
    this.region = region;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final AwsBaseConfiguration that = (AwsBaseConfiguration) o;
    return Objects.equals(region, that.region);
  }

  @Override
  public int hashCode() {
    return Objects.hash(region);
  }

  @Override
  public String toString() {
    return "AwsBaseConfiguration{" + "region='" + region + "'" + "}";
  }
}
