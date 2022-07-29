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
package io.camunda.connector.http.model;

import com.google.common.base.Objects;
import java.util.Map;

public class HttpJsonResult {
  private int status;
  private Map<String, Object> headers;
  private Object body;

  public int getStatus() {
    return status;
  }

  public void setStatus(int status) {
    this.status = status;
  }

  public Map<String, Object> getHeaders() {
    return headers;
  }

  public void setHeaders(Map<String, Object> headers) {
    this.headers = headers;
  }

  public Object getBody() {
    return body;
  }

  public void setBody(Object body) {
    this.body = body;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    HttpJsonResult that = (HttpJsonResult) o;
    return status == that.status
        && Objects.equal(headers, that.headers)
        && Objects.equal(body, that.body);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(status, headers, body);
  }

  @Override
  public String toString() {
    return "HttpJsonResult{" + "status=" + status + ", headers=" + headers + ", body=" + body + '}';
  }
}
