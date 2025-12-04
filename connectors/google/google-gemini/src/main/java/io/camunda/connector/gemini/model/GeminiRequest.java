/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.gemini.model;

import io.camunda.google.model.GoogleBaseRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;

public class GeminiRequest extends GoogleBaseRequest {
  @Valid @NotNull private GeminiRequestData input;

  public GeminiRequestData getInput() {
    return input;
  }

  public void setInput(@Valid @NotNull GeminiRequestData input) {
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
    GeminiRequest that = (GeminiRequest) o;
    return Objects.equals(authentication, that.authentication) && Objects.equals(input, that.input);
  }

  @Override
  public int hashCode() {
    return Objects.hash(authentication, input);
  }

  @Override
  public String toString() {
    return "GeminiRequest{" + "authentication=" + authentication + ", input=" + input + '}';
  }
}
