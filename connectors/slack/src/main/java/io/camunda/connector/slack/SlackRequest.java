/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack;

import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import io.camunda.connector.api.annotation.Secret;
import java.io.IOException;
import java.util.Objects;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class SlackRequest<T extends SlackRequestData> {

  @NotBlank @Secret private String token;
  @NotBlank private String method;

  @Valid @NotNull @Secret private T data;

  public SlackResponse invoke(final Slack slack) throws SlackApiException, IOException {
    MethodsClient methods = slack.methods(token);
    return data.invoke(methods);
  }

  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }

  public String getMethod() {
    return method;
  }

  public void setMethod(String method) {
    this.method = method;
  }

  public T getData() {
    return data;
  }

  public void setData(T data) {
    this.data = data;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SlackRequest<?> that = (SlackRequest<?>) o;
    return Objects.equals(token, that.token)
        && Objects.equals(method, that.method)
        && Objects.equals(data, that.data);
  }

  @Override
  public int hashCode() {
    return Objects.hash(token, method, data);
  }

  @Override
  public String toString() {
    return "SlackRequest{"
        + "token='"
        + token
        + '\''
        + ", method='"
        + method
        + '\''
        + ", data="
        + data
        + '}';
  }
}
