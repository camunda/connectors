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
package io.camunda.connector.generator.openapi.util;

import static io.camunda.connector.generator.openapi.util.SecurityUtil.parseAuthentication;

import io.camunda.connector.generator.dsl.http.HttpFeelBuilder;
import io.camunda.connector.generator.dsl.http.HttpOperation;
import io.camunda.connector.generator.dsl.http.HttpOperationProperty;
import io.camunda.connector.generator.openapi.OpenApiGenerationSource.Options;
import io.camunda.connector.generator.openapi.OperationParseResult;
import io.camunda.connector.generator.openapi.util.BodyUtil.BodyParseResult;
import io.camunda.connector.http.base.model.HttpMethod;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility functions related to parsing OpenAPI operations and converting them into {@link
 * HttpOperation}s.
 */
public class OperationUtil {

  public static List<OperationParseResult> extractOperations(
      OpenAPI openAPI, Set<String> includeOperations, Options options) {
    var components = openAPI.getComponents();
    return openAPI.getPaths().entrySet().stream()
        .flatMap(
            pathEntry -> {
              var pathItem = pathEntry.getValue();
              if (pathItem.get$ref() != null) {
                pathItem =
                    components
                        .getPathItems()
                        .get(pathItem.get$ref().replace("#/components/pathItems/", ""));
              }

              List<OperationParseResult> operations = new ArrayList<>();
              if (pathItem.getGet() != null) {
                operations.add(
                    extractOperation(
                        pathEntry.getKey(),
                        HttpMethod.GET,
                        pathItem.getGet(),
                        components,
                        options));
              }
              if (pathItem.getPost() != null) {
                operations.add(
                    extractOperation(
                        pathEntry.getKey(),
                        HttpMethod.POST,
                        pathItem.getPost(),
                        components,
                        options));
              }
              if (pathItem.getPut() != null) {
                operations.add(
                    extractOperation(
                        pathEntry.getKey(),
                        HttpMethod.PUT,
                        pathItem.getPut(),
                        components,
                        options));
              }
              if (pathItem.getPatch() != null) {
                operations.add(
                    extractOperation(
                        pathEntry.getKey(),
                        HttpMethod.PATCH,
                        pathItem.getPatch(),
                        components,
                        options));
              }
              if (pathItem.getDelete() != null) {
                operations.add(
                    extractOperation(
                        pathEntry.getKey(),
                        HttpMethod.DELETE,
                        pathItem.getDelete(),
                        components,
                        options));
              }
              var path = extractPath(pathEntry.getKey());

              operations.forEach(
                  operation -> {
                    if (operation.supported()) {
                      operation.builder().pathFeelExpression(path);
                    }
                  });
              return operations.stream();
            })
        .filter(
            operation ->
                includeOperations == null
                    || includeOperations.isEmpty()
                    || includeOperations.contains(operation.builder().getId()))
        .collect(Collectors.toList());
  }

  private static OperationParseResult extractOperation(
      String path, HttpMethod method, Operation operation, Components components, Options options) {
    try {
      var parameters = operation.getParameters();
      Set<HttpOperationProperty> properties =
          new HashSet<>(
              parameters == null
                  ? Collections.emptySet()
                  : parameters.stream()
                      .map(parameter -> ParameterUtil.transformToProperty(parameter, components))
                      .collect(Collectors.toSet()));

      var label = method.name() + " " + path;

      var authenticationOverride = parseAuthentication(operation.getSecurity(), components);

      var body = BodyUtil.parseBody(operation.getRequestBody(), components, options);
      HttpFeelBuilder bodyFeelExpression;

      if (body instanceof BodyParseResult.Raw raw) {
        bodyFeelExpression = HttpFeelBuilder.preFormatted("=" + raw.rawBody());
      } else {
        bodyFeelExpression = ((BodyParseResult.Detailed) body).feelBuilder();
        properties.addAll(((BodyParseResult.Detailed) body).properties());
      }

      var opBuilder =
          HttpOperation.builder()
              .id(Optional.ofNullable(operation.getOperationId()).orElse(label.replace(" ", "_")))
              .label(label)
              .bodyFeelExpression(bodyFeelExpression)
              .authenticationOverride(authenticationOverride)
              .method(method)
              .properties(properties);
      return new OperationParseResult(operation.getOperationId(), path, true, null, opBuilder);
    } catch (Exception e) {
      return new OperationParseResult(
          operation.getOperationId(), path, false, e.getMessage(), null);
    }
  }

  private static HttpFeelBuilder extractPath(String rawPath) {
    // split path into parts, each part is either a variable or a constant
    String[] pathParts = rawPath.split("\\{");
    var builder = HttpFeelBuilder.string();
    if (pathParts.length == 1) {
      // no variables
      builder.part(rawPath);
    } else {
      for (String pathPart : pathParts) {
        if (pathPart.contains("}")) {
          String[] variableParts = pathPart.split("}");
          // replace dashes in variable names with underscores, same must be done for properties
          var property = variableParts[0].replace("-", "_");
          builder.property(property);
          if (variableParts.length > 1) {
            builder.part(variableParts[1]);
          }
        } else {
          builder.part(pathPart);
        }
      }
    }
    return builder;
  }
}
