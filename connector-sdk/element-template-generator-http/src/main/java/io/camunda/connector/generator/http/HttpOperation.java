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
import io.camunda.connector.http.base.auth.Authentication;
import java.util.Collection;
import java.util.Map;

/**
 * @param id A unique identifier for this operation.
 * @param label A human-readable label for this operation.
 * @param pathFeelExpression A FEEL expression for the request path that may include variable values
 *     from properties.
 * @param method The HTTP method for this operation.
 * @param bodyFeelExpression A FEEL expression for the request body that may include variable values
 *     from properties.
 * @param headers Headers defined for this operation.
 * @param queryParameters Query parameters defined for this operation.
 * @param properties Custom properties defined for this operation. This should not include technical
 *     properties like the path or the method, i.e. only properties that are relevant for the
 *     business logic.
 */
public record HttpOperation(
    String id,
    String label,
    String pathFeelExpression,
    ConnectorHttpMethod method,
    String bodyFeelExpression,
    Map<String, String> headers,
    Map<String, String> queryParameters,
    Collection<PropertyBuilder> properties,
    Authentication authenticationOverride) {

  /** HTTP methods supported by the HTTP connector family. */
  enum ConnectorHttpMethod {
    GET,
    POST,
    PUT,
    PATCH,
    DELETE
  }
}
