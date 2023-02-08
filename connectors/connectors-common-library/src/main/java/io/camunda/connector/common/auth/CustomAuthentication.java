/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.common.auth;

import com.google.api.client.http.HttpHeaders;
import io.camunda.connector.api.annotation.Secret;
import io.camunda.connector.common.model.CommonRequest;
import java.util.Map;
import java.util.Objects;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class CustomAuthentication extends Authentication {

  @NotNull @Valid private CommonRequest request;

  @Secret private Map<String, String> outputBody;

  @Secret private Map<String, String> outputHeaders;

  @Override
  public void setHeaders(final HttpHeaders headers) {}

  public CommonRequest getRequest() {
    return request;
  }

  public void setRequest(final CommonRequest request) {
    this.request = request;
  }

  public Map<String, String> getOutputBody() {
    return outputBody;
  }

  public void setOutputBody(final Map<String, String> outputBody) {
    this.outputBody = outputBody;
  }

  public Map<String, String> getOutputHeaders() {
    return outputHeaders;
  }

  public void setOutputHeaders(final Map<String, String> outputHeaders) {
    this.outputHeaders = outputHeaders;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final CustomAuthentication that = (CustomAuthentication) o;
    return Objects.equals(request, that.request)
        && Objects.equals(outputBody, that.outputBody)
        && Objects.equals(outputHeaders, that.outputHeaders);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), request, outputBody, outputHeaders);
  }

  @Override
  public String toString() {
    return "CustomAuthentication{"
        + "request="
        + request
        + ", outputBody="
        + outputBody
        + ", outputHeaders="
        + outputHeaders
        + "}";
  }
}
