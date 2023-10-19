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
package io.camunda.connector.generator.http;

import io.camunda.connector.generator.dsl.PropertyBuilder;
import io.camunda.connector.generator.http.HttpOperation.ConnectorHttpMethod;
import io.camunda.connector.http.base.auth.Authentication;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

public class HttpOperationBuilder {
  private String id;
  private String label;
  private ConnectorHttpMethod method;
  private String pathFeelExpression = "";
  private String bodyFeelExpression = "";
  private Map<String, String> headers = Map.of();
  private Map<String, String> queryParameters = Map.of();
  private Authentication authenticationOverride = null;
  private Collection<PropertyBuilder> properties = Collections.emptyList();

  public static HttpOperationBuilder create() {
    return new HttpOperationBuilder();
  }

  /** A unique identifier for this operation. */
  public HttpOperationBuilder id(String id) {
    this.id = id;
    return this;
  }

  /** A human-readable label for this operation. */
  public HttpOperationBuilder label(String label) {
    this.label = label;
    return this;
  }

  /** The HTTP method for this operation. */
  public HttpOperationBuilder method(ConnectorHttpMethod method) {
    this.method = method;
    return this;
  }

  /**
   * HTTP request path for this operation. This may include variable values from properties. This
   * path should not include the server URL.
   *
   * <p>Example: {@code = "/foo/" + bar} <br>
   * The variable {@code bar} should be present in {@link #properties(Collection)}.
   */
  public HttpOperationBuilder pathFeelExpression(String pathFeelExpression) {
    this.pathFeelExpression = pathFeelExpression;
    return this;
  }

  /**
   * HTTP request body for this operation. This may include variable values from properties.
   *
   * <p>Example: {@code = { "foo": bar }} <br>
   * The variable {@code bar} should be present in {@link #properties(Collection)}.
   */
  public HttpOperationBuilder bodyFeelExpression(String bodyFeelExpression) {
    this.bodyFeelExpression = bodyFeelExpression;
    return this;
  }

  /** HTTP request headers for this operation. This may include variable values from properties. */
  public HttpOperationBuilder headers(Map<String, String> headers) {
    this.headers = headers;
    return this;
  }

  /** HTTP request headers for this operation. This may include variable values from properties. */
  public HttpOperationBuilder queryParameters(Map<String, String> queryParameters) {
    this.queryParameters = queryParameters;
    return this;
  }

  /**
   * Optional authentication override for this operation. If set, this authentication will be used
   * instead of the one defined on the top level in {@link
   * HttpOutboundElementTemplateBuilder#authentication(Authentication)}.
   */
  public HttpOperationBuilder authenticationOverride(Authentication authentication) {
    this.authenticationOverride = authentication;
    return this;
  }

  /**
   * Custom properties defined for this operation. This should not include technical properties like
   * the path or the method, i.e. only properties that are relevant for the business logic.
   */
  public HttpOperationBuilder properties(PropertyBuilder... properties) {
    return properties(Arrays.asList(properties));
  }

  /**
   * Custom properties defined for this operation. This should not include technical properties like
   * the path or the method, i.e. only properties that are relevant for the business logic.
   */
  public HttpOperationBuilder properties(Collection<PropertyBuilder> properties) {
    if (this.properties != null && !this.properties.isEmpty()) {
      throw new IllegalStateException("Properties already set");
    }
    this.properties = properties;
    return this;
  }

  public HttpOperation build() {
    // TODO: validate by parsing FEEL: are all mentioned properties provided?
    if (method == null) {
      throw new IllegalStateException("HTTP method is not defined");
    }
    if (id == null) {
      throw new IllegalStateException("Operation id is not defined");
    }
    if (label == null) {
      throw new IllegalStateException("Operation label is not defined");
    }
    // shade property ids with operation id as they may be duplicate
    // TODO: rename properties in the path, body template/expressions accordingly
    properties.forEach(prop -> prop.id(id + "_" + prop.getId()));

    return new HttpOperation(
        id,
        label,
        pathFeelExpression,
        method,
        bodyFeelExpression,
        headers,
        queryParameters,
        properties,
        authenticationOverride);
  }
}
