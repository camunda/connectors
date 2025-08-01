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
package io.camunda.connector.http.client.model;

import io.camunda.document.Document;
import java.util.Map;

public record HttpClientResult(
    int status, Map<String, Object> headers, Object body, String reason, Document document) {

  public HttpClientResult(int status, Map<String, Object> headers, Object body, String reason) {
    this(status, headers, body, reason, null);
  }

  public HttpClientResult(
      int status, Map<String, Object> headers, Object body, Document documentReference) {
    this(status, headers, body, null, documentReference);
  }

  public HttpClientResult(int status, Map<String, Object> headers, Object body) {
    this(status, headers, body, null, null);
  }

  public int status() {
    return status;
  }

  public Map<String, Object> headers() {
    return headers;
  }

  public Object body() {
    return body;
  }

  public String reason() {
    return reason;
  }

  public Document document() {
    return document;
  }
}
