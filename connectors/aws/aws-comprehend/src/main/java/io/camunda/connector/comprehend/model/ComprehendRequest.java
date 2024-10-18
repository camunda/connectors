/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.comprehend.model;

import io.camunda.connector.aws.model.impl.AwsBaseRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class ComprehendRequest extends AwsBaseRequest {
  @Valid @NotNull private ComprehendRequestData input;

  public ComprehendRequest() {}

  public ComprehendRequestData getInput() {
    return input;
  }

  public void setInput(ComprehendRequestData input) {
    this.input = input;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ComprehendRequest that = (ComprehendRequest) o;

    return new EqualsBuilder().appendSuper(super.equals(o)).append(input, that.input).isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37).appendSuper(super.hashCode()).append(input).toHashCode();
  }

  @Override
  public String toString() {
    return "ComprehendRequest{" + "input=" + input + '}';
  }
}
