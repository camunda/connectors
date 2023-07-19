/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.runtime.inbound.webhook.model;

import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

public class HttpServletRequestWebhookProcessingPayload implements WebhookProcessingPayload {

  private final String requestURL;
  private final String method;

  private final Map<String, Object> body;
  private final Map<String, String> headers;
  private final Map<String, String> params;
  private final byte[] rawBody;

  public HttpServletRequestWebhookProcessingPayload(
      final HttpServletRequest httpServletRequest,
      final Map<String, Object> body,
      final Map<String, String> params,
      final Map<String, String> headers,
      byte[] bodyAsByteArray) {
    this.requestURL = httpServletRequest.getRequestURL().toString();
    this.method = httpServletRequest.getMethod();
    this.body = body;
    this.headers = headers;
    this.params = params;
    this.rawBody = bodyAsByteArray;
  }

  @Override
  public String requestURL() {
    return requestURL;
  }

  @Override
  public String method() {
    return method;
  }

  @Override
  public Map<String, Object> body() {
    return Collections.unmodifiableMap(Optional.ofNullable(body).orElse(Collections.emptyMap()));
  }

  @Override
  public Map<String, String> headers() {
    return Collections.unmodifiableMap(Optional.ofNullable(headers).orElse(Collections.emptyMap()));
  }

  @Override
  public Map<String, String> params() {
    return Collections.unmodifiableMap(Optional.ofNullable(params).orElse(Collections.emptyMap()));
  }

  @Override
  public byte[] rawBody() {
    return rawBody != null ? Arrays.copyOf(rawBody, rawBody.length) : null;
  }

  @Override
  public String toString() {
    return "HttpServletRequestWebhookProcessingPayload{"
        + "requestURL='"
        + requestURL
        + "'"
        + ", method='"
        + method
        + "'"
        + ", headers="
        + headers
        + ", params="
        + params
        + ", rawBody="
        + Arrays.toString(rawBody)
        + "}";
  }
}
