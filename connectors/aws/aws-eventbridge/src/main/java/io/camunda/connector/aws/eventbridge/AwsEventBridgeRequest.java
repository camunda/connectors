/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.eventbridge;

import io.camunda.connector.api.annotation.Secret;
import io.camunda.connector.aws.model.impl.AwsBaseRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;

public class AwsEventBridgeRequest extends AwsBaseRequest {
  @Valid @NotNull @Secret private AwsEventBridgeInput input;

  public AwsEventBridgeInput getInput() {
    return input;
  }

  public void setInput(final AwsEventBridgeInput input) {
    this.input = input;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof final AwsEventBridgeRequest request)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    return Objects.equals(input, request.input);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), input);
  }

  @Override
  public String toString() {
    return "AwsEventBridgeRequest{" + "input=" + input + super.toString() + "}";
  }
}
