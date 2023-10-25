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
import io.camunda.connector.http.base.model.HttpMethod;
import java.util.Collection;
import java.util.List;

/**
 * @param id A unique identifier for this operation.
 * @param label A human-readable label for this operation.
 * @param pathFeelExpression A FEEL expression for the request path that may include variable values
 *     from properties.
 * @param method The HTTP method for this operation.
 * @param bodyFeelExpression A FEEL expression for the request bodyFeelExpression that may include
 *     variable values from properties.
 * @param headersFeelExpression Headers defined for this operation (FEEL context expression).
 * @param queryParamsFeelExpression Query parameters defined for this operation (FEEL context
 *     expression).
 * @param properties Custom properties defined for this operation. This should not include technical
 *     properties like the path or the method, i.e. only properties that are relevant for the
 *     business logic.
 */
public record HttpOperation(
    String id,
    String label,
    String pathFeelExpression,
    HttpMethod method,
    String bodyFeelExpression,
    String headersFeelExpression,
    String queryParamsFeelExpression,
    Collection<Property> properties,
    List<HttpAuthentication> authenticationOverride) {

  public static HttpOperationBuilder builder() {
    return HttpOperationBuilder.create();
  }
}
