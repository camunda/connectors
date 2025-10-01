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

import java.io.IOException;
import java.util.Map;

/**
 * Represents the result of an HTTP request executed by the {@link
 * io.camunda.connector.http.client.client.HttpClient}.
 *
 * <p>Note that this class implements {@link AutoCloseable} to ensure that resources associated with
 * the response body can be released. It is important to call the {@link #close()} method when the
 * response body is no longer needed to prevent resource leaks.
 */
public record HttpClientResult(
    int status, Map<String, Object> headers, ResponseBody body, String reason)
    implements AutoCloseable {

  public HttpClientResult(int status, Map<String, Object> headers, ResponseBody body) {
    this(status, headers, body, null);
  }

  public int status() {
    return status;
  }

  public Map<String, Object> headers() {
    return headers;
  }

  public ResponseBody body() {
    return body;
  }

  public String reason() {
    return reason;
  }

  @Override
  public void close() throws IOException {
    if (body != null) {
      body.close();
    }
  }
}
