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
import io.camunda.connector.generator.dsl.http.HttpOperationProperty.Target;
import io.camunda.connector.generator.dsl.http.OperationParseResult;
import io.camunda.connector.generator.openapi.OpenApiGenerationSource.Options;
import io.camunda.connector.generator.openapi.util.BodyUtil.BodyParseResult;
import io.camunda.connector.http.base.model.HttpMethod;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.RequestBody;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility functions related to parsing OpenAPI operations and converting them into {@link
 * HttpOperation}s.
 */
public class OperationUtil {

  private static final String CONTENT_TYPE = "Content-Type";

  public static List<OperationParseResult> extractOperations(
      OpenAPI openAPI, Set<String> includeOperations, Options options) {
    var components = openAPI.getComponents();
    return openAPI.getPaths().entrySet().stream()
        .flatMap(pathEntry -> {
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
                    pathItem.getGet().getTags(),
                    pathItem.getGet().getDescription(),
                    components,
                    options));
          }
          if (pathItem.getPost() != null) {
            operations.add(
                extractOperation(
                    pathEntry.getKey(),
                    HttpMethod.POST,
                    pathItem.getPost(),
                    pathItem.getPost().getTags(),
                    pathItem.getPost().getDescription(),
                    components,
                    options));
          }
          if (pathItem.getPut() != null) {
            operations.add(
                extractOperation(
                    pathEntry.getKey(),
                    HttpMethod.PUT,
                    pathItem.getPut(),
                    pathItem.getPut().getTags(),
                    pathItem.getPut().getDescription(),
                    components,
                    options));
          }
          if (pathItem.getPatch() != null) {
            operations.add(
                extractOperation(
                    pathEntry.getKey(),
                    HttpMethod.PATCH,
                    pathItem.getPatch(),
                    pathItem.getPatch().getTags(),
                    pathItem.getPatch().getDescription(),
                    components,
                    options));
          }
          if (pathItem.getDelete() != null) {
            operations.add(
                extractOperation(
                    pathEntry.getKey(),
                    HttpMethod.DELETE,
                    pathItem.getDelete(),
                    pathItem.getDelete().getTags(),
                    pathItem.getDelete().getDescription(),
                    components,
                    options));
          }
          var path = extractPath(pathEntry.getKey());

          operations.forEach(operation -> {
            if (operation.supported()) {
              operation.builder().pathFeelExpression(path);
            }
          });
          return operations.stream();
        })
        .filter(operation ->
            includeOperations == null
                || includeOperations.isEmpty()
                || operation.builder() != null
                    && includeOperations.contains(operation.builder().getId()))
        .collect(
            Collectors.collectingAndThen(
                Collectors.toList(), OperationUtil::markDuplicateOperationIdsUnsupported));
  }

  private static OperationParseResult extractOperation(
      String path,
      HttpMethod method,
      Operation operation,
      List<String> tags,
      String description,
      Components components,
      Options options) {
    try {
      var parameters = operation.getParameters();
      Set<HttpOperationProperty> properties =
          parameters == null
              ? new LinkedHashSet<>()
              : parameters.stream()
                  .map(parameter -> ParameterUtil.transformToProperty(parameter, components))
                  .collect(Collectors.toCollection(LinkedHashSet::new));

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

      boolean multipartBody =
          Optional.ofNullable(operation.getRequestBody())
              .map(RequestBody::getContent)
              .filter(content -> content.containsKey("multipart/form-data"))
              .isPresent();
      if (multipartBody) {
        // The multipart body sets the Content-Type header, so an explicit Content-Type header
        // parameter would duplicate it.
        properties.removeIf(property ->
            property.target() == Target.HEADER && CONTENT_TYPE.equalsIgnoreCase(property.id()));
        properties.add(
            HttpOperationProperty.createHiddenProperty(
                CONTENT_TYPE, Target.HEADER, "", true, "multipart/form-data"));
      }
      ensureUniquePropertyIds(properties);

      var opBuilder =
          HttpOperation.builder()
              .id(Optional.ofNullable(operation.getOperationId()).orElse(label.replace(" ", "_")))
              .label(label)
              .bodyFeelExpression(bodyFeelExpression)
              .authenticationOverride(authenticationOverride)
              .method(method)
              .properties(properties);
      return new OperationParseResult(
          operation.getOperationId(), path, method, tags, true, description, null, opBuilder);
    } catch (Exception e) {
      return new OperationParseResult(
          operation.getOperationId(), path, method, tags, false, description, e.getMessage(), null);
    }
  }

  /**
   * The template property ID of an operation property is derived from its target and ID. Two
   * properties with the same target and ID, for example a header parameter declared twice, would
   * make the whole template invalid, so the operation is rejected instead.
   */
  private static void ensureUniquePropertyIds(Collection<HttpOperationProperty> properties) {
    Set<String> seen = new HashSet<>();
    for (var property : properties) {
      var target = property.target().name().toLowerCase();
      if (!seen.add(target + "_" + property.id())) {
        throw new IllegalArgumentException(
            "Duplicate " + target + " property '" + property.id() + "'");
      }
    }
  }

  /**
   * Operation IDs must be unique, as template property IDs are prefixed with them. Keep the first
   * operation with a given ID and mark the others unsupported.
   */
  private static List<OperationParseResult> markDuplicateOperationIdsUnsupported(
      List<OperationParseResult> operations) {
    Set<String> seen = new HashSet<>();
    List<OperationParseResult> result = new ArrayList<>(operations.size());
    for (var operation : operations) {
      if (!operation.supported() || seen.add(operation.builder().getId())) {
        result.add(operation);
      } else {
        result.add(
            new OperationParseResult(
                operation.id(),
                operation.path(),
                operation.method(),
                operation.tags(),
                false,
                operation.description(),
                "Duplicate operation ID '"
                    + operation.builder().getId()
                    + "': another operation already uses it",
                null));
      }
    }
    return result;
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
