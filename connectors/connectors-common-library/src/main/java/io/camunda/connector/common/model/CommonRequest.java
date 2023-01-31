/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.common.model;

import io.camunda.connector.api.annotation.Secret;
import io.camunda.connector.common.auth.Authentication;
import java.util.Objects;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

public class CommonRequest {

  @NotBlank
  @Pattern(regexp = "^(http://|https://|secrets).*$")
  @Secret
  private String url;

  @NotBlank @Secret private String method;

  @Valid @Secret private Authentication authentication;

  @Pattern(regexp = "^([0-9]*$)|(secrets.*$)")
  @Secret
  private String connectionTimeoutInSeconds;

  public boolean hasAuthentication() {
    return authentication != null;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public Authentication getAuthentication() {
    return authentication;
  }

  public void setAuthentication(Authentication authentication) {
    this.authentication = authentication;
  }

  public String getMethod() {
    return method;
  }

  public void setMethod(String method) {
    this.method = method;
  }

  public String getConnectionTimeoutInSeconds() {
    return connectionTimeoutInSeconds;
  }

  public void setConnectionTimeoutInSeconds(String connectionTimeoutInSeconds) {
    this.connectionTimeoutInSeconds = connectionTimeoutInSeconds;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    CommonRequest that = (CommonRequest) o;
    return url.equals(that.url)
        && method.equals(that.method)
        && Objects.equals(authentication, that.authentication)
        && Objects.equals(connectionTimeoutInSeconds, that.connectionTimeoutInSeconds);
  }

  @Override
  public int hashCode() {
    return Objects.hash(url, method, authentication, connectionTimeoutInSeconds);
  }

  @Override
  public String toString() {
    return "CommonRequest{"
        + "url='"
        + url
        + '\''
        + ", method='"
        + method
        + '\''
        + ", authentication="
        + authentication
        + ", connectionTimeoutInSeconds='"
        + connectionTimeoutInSeconds
        + '\''
        + '}';
  }
}
