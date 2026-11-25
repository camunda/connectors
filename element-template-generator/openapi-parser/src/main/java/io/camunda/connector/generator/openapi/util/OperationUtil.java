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
    var globalAuthenticationConflict = globalAuthenticationConflict(openAPI);
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
                    globalAuthenticationConflict,
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
                    globalAuthenticationConflict,
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
                    globalAuthenticationConflict,
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
                    globalAuthenticationConflict,
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
                    globalAuthenticationConflict,
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
                Collectors.toList(), OperationUtil::markConflictingOperationsUnsupported));
  }

  private static OperationParseResult extractOperation(
      String path,
      HttpMethod method,
      Operation operation,
      List<String> tags,
      String description,
      Components components,
      String globalAuthenticationConflict,
      Options options) {
    try {
      var parameters = operation.getParameters();
      Set<HttpOperationProperty> properties =
          parameters == null
              ? new LinkedHashSet<>()
              : parameters.stream()
                  .map(parameter -> ParameterUtil.resolve(parameter, components))
                  .filter(parameter -> !ParameterUtil.isOptionalCookie(parameter))
                  .map(parameter -> ParameterUtil.transformToProperty(parameter, components))
                  .collect(Collectors.toCollection(LinkedHashSet::new));

      var label = method.name() + " " + path;

      var authenticationOverride = parseAuthentication(operation.getSecurity(), components);
      if (authenticationOverride.isEmpty() && globalAuthenticationConflict != null) {
        // the operation inherits the global security requirements
        throw new IllegalArgumentException(globalAuthenticationConflict);
      }

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
        // parameter would duplicate it. Only the exact name is matched: other spellings get a
        // different property ID and have always been kept.
        properties.removeIf(
            property -> property.target() == Target.HEADER && CONTENT_TYPE.equals(property.id()));
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
   * Conflicting global security schemes only matter for operations that inherit them. Those
   * operations are marked unsupported, so the scan doesn't fail when every operation it keeps
   * declares its own security.
   *
   * @return the reason, or {@code null} if the global security requirements have no conflict. Any
   *     other parsing failure is left to the template build, as before.
   */
  private static String globalAuthenticationConflict(OpenAPI openAPI) {
    try {
      parseAuthentication(openAPI.getSecurity(), openAPI.getComponents());
      return null;
    } catch (SecurityUtil.ConflictingSecuritySchemesException e) {
      return "Global security requirements are not supported: " + e.getMessage();
    } catch (RuntimeException e) {
      return null;
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
   * Template property IDs are prefixed with the operation ID, so two operations can still produce
   * the same property ID: when they share an operation ID, or when the prefixes line up, for
   * example operation {@code foo} with query parameter {@code query_bar} and operation {@code
   * foo_query} with query parameter {@code bar}. Keep the first operation and mark the later one
   * unsupported.
   */
  private static List<OperationParseResult> markConflictingOperationsUnsupported(
      List<OperationParseResult> operations) {
    Set<String> seenOperationIds = new HashSet<>();
    Set<String> seenPropertyIds = new HashSet<>();
    List<OperationParseResult> result = new ArrayList<>(operations.size());
    for (var operation : operations) {
      if (!operation.supported()) {
        result.add(operation);
        continue;
      }
      var id = operation.builder().getId();
      String conflict = null;
      if (seenOperationIds.contains(id)) {
        conflict = "Duplicate operation ID '" + id + "': another operation already uses it";
      } else {
        var propertyIds = generatedPropertyIds(operation);
        var clash = propertyIds.stream().filter(seenPropertyIds::contains).findFirst();
        if (clash.isPresent()) {
          conflict =
              "Generated property ID '" + clash.get() + "' is already used by another operation";
        } else {
          seenOperationIds.add(id);
          seenPropertyIds.addAll(propertyIds);
        }
      }
      result.add(
          conflict == null
              ? operation
              : new OperationParseResult(
                  operation.id(),
                  operation.path(),
                  operation.method(),
                  operation.tags(),
                  false,
                  operation.description(),
                  conflict,
                  null));
    }
    return result;
  }

  /**
   * The per-operation property IDs that {@code PropertyUtil} in http-dsl generates for the
   * parameters and request body groups. Keep in sync with {@code transformProperty}, {@code
   * parametersPropertyGroup} and {@code requestBodyPropertyGroup}.
   */
  private static Set<String> generatedPropertyIds(OperationParseResult operation) {
    var id = operation.builder().getId();
    boolean supportsBody = operation.method().supportsBody;
    Set<String> ids =
        new HashSet<>(
            List.of(id + "_$path", id + "_$headers", id + "_$queryParameters", id + "_$method"));
    boolean hasBodyProperties = false;
    for (var property : operation.builder().getProperties()) {
      if (property.target() == Target.BODY) {
        if (!supportsBody) {
          continue;
        }
        hasBodyProperties = true;
      }
      ids.add(id + "_" + property.target().name().toLowerCase() + "_" + property.id());
    }
    if (supportsBody) {
      ids.add(id + (hasBodyProperties ? "_$body" : "_body"));
    }
    return ids;
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
