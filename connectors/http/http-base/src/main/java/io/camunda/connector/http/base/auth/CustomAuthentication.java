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
package io.camunda.connector.http.base.auth;

import com.google.api.client.http.HttpHeaders;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.annotation.TemplateSubType;
import io.camunda.connector.http.base.model.HttpCommonRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.Objects;

@TemplateSubType(ignore = true)
public final class CustomAuthentication extends Authentication {

  @FEEL @NotNull @Valid private HttpCommonRequest request;

  @FEEL private Map<String, String> outputBody;

  @FEEL private Map<String, String> outputHeaders;

  @Override
  public void setHeaders(final HttpHeaders headers) {}

  public HttpCommonRequest getRequest() {
    return request;
  }

  public void setRequest(final HttpCommonRequest request) {
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
