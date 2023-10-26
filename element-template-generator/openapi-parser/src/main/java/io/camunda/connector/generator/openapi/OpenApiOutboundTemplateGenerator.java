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

import io.camunda.connector.generator.api.GeneratorConfiguration;
import io.camunda.connector.generator.api.OutboundTemplateGenerator;
import io.camunda.connector.generator.dsl.OutboundElementTemplate;
import io.camunda.connector.generator.dsl.http.HttpAuthentication.NoAuth;
import io.camunda.connector.generator.dsl.http.HttpOperation;
import io.camunda.connector.generator.dsl.http.HttpOperationBuilder;
import io.camunda.connector.generator.dsl.http.HttpOperationProperty;
import io.camunda.connector.generator.dsl.http.HttpOutboundElementTemplateBuilder;
import io.camunda.connector.generator.dsl.http.HttpPathFeelBuilder;
import io.camunda.connector.generator.dsl.http.HttpServerData;
import io.camunda.connector.http.base.model.HttpMethod;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.servers.Server;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class OpenApiOutboundTemplateGenerator
    implements OutboundTemplateGenerator<OpenApiGenerationSource> {

  // ordered by priority if endpoint allows multiple
  private static final List<String> SUPPORTED_BODY_MEDIA_TYPES =
      List.of("application/json", "text/plain");

  @Override
  public OutboundElementTemplate generate(
      OpenApiGenerationSource source, GeneratorConfiguration configuration) {
    var openAPI = source.openAPI();
    var info = openAPI.getInfo();
    var builder =
        HttpOutboundElementTemplateBuilder.create()
            .id(getIdFromApiTitle(info.getTitle()))
            .name(info.getTitle())
            .version(processVersion(info.getVersion()));

    List<HttpOperationBuilder> operations =
        extractOperations(source.openAPI(), source.includeOperations());
    if (operations.isEmpty()) {
      throw new IllegalArgumentException("No operations found in OpenAPI document");
    }
    builder.operations(
        operations.stream().map(HttpOperationBuilder::build).collect(Collectors.toList()));

    var authentication = parseAuthentication(openAPI.getSecurity(), openAPI.getComponents());
    if (authentication.isEmpty()) {
      authentication = List.of(NoAuth.INSTANCE);
    }

    return builder
        .servers(extractServers(openAPI.getServers()))
        .authentication(authentication)
        .build();
  }

  private String getIdFromApiTitle(String title) {
    return title.trim().replace(" ", "-");
  }

  private int processVersion(String openAPIDocVersion) {
    // open API doc version is a string, usually a semantic version, but it's not guaranteed
    // if it contains numbers and no letters, we only keep the numbers and parse as int
    // otherwise we transform characters to their ascii value and sum them up

    String onlyNumbers = openAPIDocVersion.replaceAll("[^0-9]", "");
    if (onlyNumbers.length() > 0) {
      return Integer.parseInt(onlyNumbers);
    } else {
      return openAPIDocVersion.chars().sum();
    }
  }

  private List<HttpOperationBuilder> extractOperations(
      OpenAPI openAPI, Set<String> includeOperations) {
    var components = openAPI.getComponents();
    return openAPI.getPaths().entrySet().stream()
        .flatMap(
            pathEntry -> {
              var path = extractPath(pathEntry.getKey());
              var pathItem = pathEntry.getValue();
              if (pathItem.get$ref() != null) {
                pathItem =
                    components
                        .getPathItems()
                        .get(pathItem.get$ref().replace("#/components/pathItems/", ""));
              }

              List<HttpOperationBuilder> operations = new ArrayList<>();
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
              operations.forEach(operation -> operation.pathFeelExpression(path));
              return operations.stream();
            })
        .filter(
            operation ->
                includeOperations == null
                    || includeOperations.isEmpty()
                    || includeOperations.contains(operation.getId()))
        .collect(Collectors.toList());
  }

  private HttpOperationBuilder extractOperation(
      String path, HttpMethod method, Operation operation, Components components) {
    var parameters = operation.getParameters();
    Set<HttpOperationProperty> properties =
        parameters == null
            ? Collections.emptySet()
            : parameters.stream()
                .map(parameter -> ParameterUtil.transformToProperty(parameter, components))
                .collect(Collectors.toSet());

    var bodyExample = extractBodyExample(operation.getRequestBody(), components);
    var label = extractLabel(operation, path, method);

    try {
      var authenticationOverride = parseAuthentication(operation.getSecurity(), components);
      return HttpOperation.builder()
          .id(operation.getOperationId())
          .label(label)
          .bodyExample(bodyExample)
          .authenticationOverride(authenticationOverride)
          .method(method)
          .properties(properties);
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "Failed to parse security schemes for operation " + operation.getOperationId(), e);
    }
  }

  private HttpPathFeelBuilder extractPath(String rawPath) {
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
          builder.property(variableParts[0]);
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

  private String extractBodyExample(RequestBody body, Components components) {
    if (body == null) {
      return "";
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
    throw new IllegalArgumentException("No supported media type found in bodyFeelExpression");
  }

  private String extractLabel(Operation operation, String path, HttpMethod method) {
    if (operation.getDescription() != null && operation.getDescription().length() < 50) {
      return operation.getDescription();
    } else {
      return method.name() + " " + path;
    }
  }

  private List<HttpServerData> extractServers(List<Server> servers) {
    if (servers == null) {
      return Collections.emptyList();
    }
    return servers.stream()
        .map(server -> new HttpServerData(server.getUrl(), server.getDescription()))
        .collect(Collectors.toList());
  }
}
