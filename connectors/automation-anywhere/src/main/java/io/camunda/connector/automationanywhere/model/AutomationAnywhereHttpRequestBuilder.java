/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.automationanywhere.model;

import io.camunda.connector.http.base.model.auth.NoAuthentication;
import io.camunda.connector.http.base.model.HttpCommonRequest;
import io.camunda.connector.http.base.model.HttpMethod;
import java.util.Map;

public class AutomationAnywhereHttpRequestBuilder {
  private String url;
  private HttpMethod method;
  private Object body;
  private Map<String, String> headers;
  private Integer timeoutInSeconds;

  public AutomationAnywhereHttpRequestBuilder withUrl(final String url) {
    this.url = url;
    return this;
  }

  public AutomationAnywhereHttpRequestBuilder withMethod(final HttpMethod method) {
    this.method = method;
    return this;
  }

  public AutomationAnywhereHttpRequestBuilder withBody(final Object body) {
    this.body = body;
    return this;
  }

  public AutomationAnywhereHttpRequestBuilder withHeaders(final Map<String, String> headers) {
    this.headers = headers;
    return this;
  }

  public AutomationAnywhereHttpRequestBuilder withTimeoutInSeconds(final Integer timeoutInSeconds) {
    this.timeoutInSeconds = timeoutInSeconds;
    return this;
  }

  public HttpCommonRequest build() {
    final var request = new HttpCommonRequest();
    request.setAuthentication(new NoAuthentication());
    request.setUrl(this.url);
    request.setMethod(this.method);
    request.setBody(this.body);
    request.setHeaders(this.headers);
    request.setConnectionTimeoutInSeconds(this.timeoutInSeconds);
    return request;
  }
}
