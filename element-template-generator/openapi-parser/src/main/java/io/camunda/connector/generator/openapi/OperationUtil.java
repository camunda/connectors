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
package io.camunda.connector.generator.openapi;

import static io.camunda.connector.generator.openapi.SecurityUtil.parseAuthentication;

import io.camunda.connector.generator.dsl.http.HttpOperation;
import io.camunda.connector.generator.dsl.http.HttpOperationProperty;
import io.camunda.connector.generator.dsl.http.HttpPathFeelBuilder;
import io.camunda.connector.http.base.model.HttpMethod;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.RequestBody;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility functions related to parsing OpenAPI operations and converting them into {@link
 * HttpOperation}s.
 */
public class OperationUtil {

  // ordered by priority if endpoint allows multiple
  private static final List<String> SUPPORTED_BODY_MEDIA_TYPES =
      List.of("application/json", "text/plain");

  static List<OperationParseResult> extractOperations(
      OpenAPI openAPI, Set<String> includeOperations) {
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
                        pathEntry.getKey(), HttpMethod.GET, pathItem.getGet(), components));
              }
              if (pathItem.getPost() != null) {
                operations.add(
                    extractOperation(
                        pathEntry.getKey(), HttpMethod.POST, pathItem.getPost(), components));
              }
              if (pathItem.getPut() != null) {
                operations.add(
                    extractOperation(
                        pathEntry.getKey(), HttpMethod.PUT, pathItem.getPut(), components));
              }
              if (pathItem.getPatch() != null) {
                operations.add(
                    extractOperation(
                        pathEntry.getKey(), HttpMethod.PATCH, pathItem.getPatch(), components));
              }
              if (pathItem.getDelete() != null) {
                operations.add(
                    extractOperation(
                        pathEntry.getKey(), HttpMethod.DELETE, pathItem.getDelete(), components));
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
        .filter(OperationParseResult::supported)
        .filter(
            operation ->
                includeOperations == null
                    || includeOperations.isEmpty()
                    || includeOperations.contains(operation.builder().getId()))
        .collect(Collectors.toList());
  }

  private static OperationParseResult extractOperation(
      String path, HttpMethod method, Operation operation, Components components) {
    try {
      var parameters = operation.getParameters();
      Set<HttpOperationProperty> properties =
          parameters == null
              ? Collections.emptySet()
              : parameters.stream()
                  .map(parameter -> ParameterUtil.transformToProperty(parameter, components))
                  .collect(Collectors.toSet());

      var bodyExample = extractBodyExample(operation.getRequestBody(), components);
      var label = method.name() + " " + path;

      var authenticationOverride = parseAuthentication(operation.getSecurity(), components);
      var opBuilder =
          HttpOperation.builder()
              .id(operation.getOperationId())
              .label(label)
              .bodyExample(bodyExample)
              .authenticationOverride(authenticationOverride)
              .method(method)
              .properties(properties);
      return new OperationParseResult(operation.getOperationId(), path, true, null, opBuilder);
    } catch (Exception e) {
      return new OperationParseResult(
          operation.getOperationId(), path, false, e.getMessage(), null);
    }
  }

  private static HttpPathFeelBuilder extractPath(String rawPath) {
    // split path into parts, each part is either a variable or a constant
    String[] pathParts = rawPath.split("\\{");
    var builder = HttpPathFeelBuilder.create();
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

  private static String extractBodyExample(RequestBody body, Components components) {
    if (body == null) {
      return "";
    }
    if (body.get$ref() != null) {
      body =
          components
              .getRequestBodies()
              .get(body.get$ref().replace("#/components/requestBodies/", ""));
    }

    var content = body.getContent();
    for (String mediaType : SUPPORTED_BODY_MEDIA_TYPES) {
      if (content.containsKey(mediaType)) {
        var mt = content.get(mediaType);
        var example = mt.getExample();
        if (example == null) {
          example = ParameterUtil.getExampleFromSchema(mt.getSchema(), components);
        }
        return example == null ? "" : example.toString();
      }
    }
    throw new IllegalArgumentException(
        "Request body media types are not supported by the REST Connector");
  }
}
