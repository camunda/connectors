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
package io.camunda.connector.generator.dsl.http;

import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.dsl.http.HttpOperationProperty.Target;
import io.camunda.connector.http.base.model.HttpMethod;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HttpOperationBuilder {

  private String id;
  private String label;
  private HttpMethod method;
  private HttpPathFeelBuilder pathFeelExpression;
  private String bodyExample = "";
  private List<HttpAuthentication> authenticationOverride = null;
  private List<HttpOperationProperty> properties = Collections.emptyList();

  public static HttpOperationBuilder create() {
    return new HttpOperationBuilder();
  }

  public static HttpOperationBuilder createForUpdate(HttpOperation operation) {
    return new HttpOperationBuilder()
        .id(operation.id())
        .label(operation.label())
        .method(operation.method())
        .pathFeelExpression(operation.pathFeelExpression())
        .bodyExample(operation.bodyFeelExpression())
        .authenticationOverride(operation.authenticationOverride())
        .properties(operation.properties());
  }

  /** A unique identifier for this operation. */
  public HttpOperationBuilder id(String id) {
    this.id = id;
    return this;
  }

  public String getId() {
    return id;
  }

  /** A human-readable description for this operation. */
  public HttpOperationBuilder label(String label) {
    this.label = label;
    return this;
  }

  /** The HTTP method for this operation. */
  public HttpOperationBuilder method(HttpMethod method) {
    this.method = method;
    return this;
  }

  /**
   * HTTP request path for this operation. This may include variable values from properties. This
   * path should not include the server URL.
   *
   * <p>Example: {@code = "/foo/" + bar} <br>
   * The variable {@code bar} should be present in {@link #properties(Collection)}.
   *
   * <p>Usage of {@link HttpPathFeelBuilder} is enforced to avoid complex string transformations and
   * to avoid errors.
   */
  public HttpOperationBuilder pathFeelExpression(HttpPathFeelBuilder builder) {
    this.pathFeelExpression = builder;
    return this;
  }

  public HttpOperationBuilder bodyExample(String bodyExample) {
    this.bodyExample = bodyExample;
    return this;
  }

  /**
   * Optional authentication override for this operation. If set, this authentication will be used
   * instead of the one defined on the top level in {@link
   * HttpOutboundElementTemplateBuilder#authentication(List)}.
   */
  public HttpOperationBuilder authenticationOverride(List<HttpAuthentication> authentication) {
    this.authenticationOverride = authentication;
    return this;
  }

  /**
   * Custom properties defined for this operation. This should not include technical properties like
   * the path or the method, i.e. only properties that are relevant for the business logic.
   */
  public HttpOperationBuilder properties(HttpOperationProperty... properties) {
    return properties(Arrays.asList(properties));
  }

  /**
   * Custom properties defined for this operation. This should not include technical properties like
   * the path or the method, i.e. only properties that are relevant for the business logic.
   */
  public HttpOperationBuilder properties(Collection<HttpOperationProperty> properties) {
    if (this.properties != null && !this.properties.isEmpty()) {
      throw new IllegalStateException("Properties already set");
    }
    this.properties = new ArrayList<>(properties);
    return this;
  }

  public List<HttpOperationProperty> getProperties() {
    return properties;
  }

  public HttpOperation build() {
    validate();
    List<Property> transformedProperties = new ArrayList<>();
    Set<String> headerProperties = new HashSet<>();
    Set<String> queryParamProperties = new HashSet<>();

    for (var property : properties) {
      if (property.target() == Target.HEADER) {
        headerProperties.add(property.id());
      } else if (property.target() == Target.QUERY) {
        queryParamProperties.add(property.id());
      }
    }

    return new HttpOperation(
        id, label, pathFeelExpression, method, bodyExample, properties, authenticationOverride);
  }

  private void validate() {
    if (method == null) {
      throw new IllegalStateException("HTTP method is not defined");
    }
    if (id == null) {
      throw new IllegalStateException("Operation id is not defined");
    }
    if (label == null) {
      throw new IllegalStateException("Operation description is not defined");
    }
  }
}
