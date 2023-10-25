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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.generator.dsl.DropdownProperty;
import io.camunda.connector.generator.dsl.DropdownProperty.DropdownChoice;
import io.camunda.connector.generator.dsl.HiddenProperty;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.dsl.Property.FeelMode;
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeInput;
import io.camunda.connector.generator.dsl.PropertyBuilder;
import io.camunda.connector.generator.dsl.PropertyCondition.AllMatch;
import io.camunda.connector.generator.dsl.PropertyCondition.Equals;
import io.camunda.connector.generator.dsl.PropertyCondition.OneOf;
import io.camunda.connector.generator.dsl.PropertyGroup;
import io.camunda.connector.generator.dsl.StringProperty;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class PropertyUtil {

  static final String OPERATION_DISCRIMINATOR_PROPERTY_ID = "operationId";
  static final String AUTH_DISCRIMINATOR_PROPERTY_ID = "authType";
  static final String OPERATION_PATH_INPUT_NAME = "operationPath";

  private static final ObjectMapper mapper = ConnectorsObjectMapperSupplier.DEFAULT_MAPPER;

  /**
   * Create a pre-configured property builder for the auth type discriminator. Add a condition if
   * necessary.
   */
  static PropertyBuilder authDiscriminatorPropertyPrefab(
      Collection<HttpAuthentication> availableTypes) {

    if (availableTypes.isEmpty()) {
      throw new RuntimeException("No auth types, expected at least one");
    }
    var choices =
        availableTypes.stream()
            .map(type -> new DropdownProperty.DropdownChoice(type.label(), type.id()))
            .toList();

    return DropdownProperty.builder()
        .choices(choices)
        .id("authType")
        .label("Authentication")
        .optional(false)
        .binding(new ZeebeInput("authType"))
        .value(choices.get(0).value());
  }

  static PropertyGroup operationDiscriminatorPropertyGroup(Collection<HttpOperation> operations) {
    if (operations.size() == 1) {
      return PropertyGroup.builder()
          .id("operation")
          .label("Operation")
          .properties(
              HiddenProperty.builder()
                  .id(OPERATION_DISCRIMINATOR_PROPERTY_ID)
                  .value(operations.iterator().next().id())
                  .binding(new ZeebeInput(OPERATION_DISCRIMINATOR_PROPERTY_ID)))
          .build();
    }

    return PropertyGroup.builder()
        .id("operation")
        .label("Operation")
        .properties(
            DropdownProperty.builder()
                .choices(
                    operations.stream()
                        .map(operation -> new DropdownChoice(operation.label(), operation.id()))
                        .collect(Collectors.toList()))
                .id(OPERATION_DISCRIMINATOR_PROPERTY_ID)
                .label("Operation")
                .group("operation")
                .value(operations.iterator().next().id())
                .binding(new ZeebeInput(OPERATION_DISCRIMINATOR_PROPERTY_ID))
                .build())
        .build();
  }

  static PropertyGroup serverPropertyGroup(Collection<HttpServerData> servers) {
    List<Property> properties = new ArrayList<>();

    // top-level property
    var urlProperty =
        HiddenProperty.builder()
            .id("url")
            .binding(new ZeebeInput("url"))
            .group("server")
            .value("= baseUrl + " + OPERATION_PATH_INPUT_NAME)
            .build();
    properties.add(urlProperty);

    if (servers == null || servers.isEmpty()) {
      // add a visible property for base URL, no servers configured
      properties.add(
          StringProperty.builder()
              .id("baseUrl")
              .group("server")
              .label("Base URL")
              .binding(new ZeebeInput("baseUrl"))
              .feel(FeelMode.optional)
              .build());
    } else if (servers.size() == 1) {
      // invisible but hard-coded, as there is only one server
      properties.add(
          HiddenProperty.builder()
              .id("baseUrl")
              .group("server")
              .value(servers.iterator().next().baseUrl())
              .binding(new ZeebeInput("baseUrl"))
              .build());
    } else {
      // multiple servers, add a dropdown
      properties.add(
          DropdownProperty.builder()
              .choices(
                  servers.stream()
                      .map(server -> new DropdownChoice(server.label(), server.baseUrl()))
                      .collect(Collectors.toList()))
              .id("baseUrl")
              .label("Server")
              .group("server")
              .binding(new ZeebeInput("baseUrl"))
              .build());
    }
    return PropertyGroup.builder().id("server").label("Server").properties(properties).build();
  }

  static PropertyGroup authPropertyGroup(
      Collection<HttpAuthentication> authentications, Collection<HttpOperation> operations) {

    var operationsWithoutCustomAuth =
        operations.stream()
            .filter(op -> op.authenticationOverride() == null)
            .map(HttpOperation::id)
            .toList();

    List<Property> properties = new ArrayList<>();
    if (authentications.size() > 1) {

      var discriminator =
          authDiscriminatorPropertyPrefab(authentications)
              .group("authentication")
              .condition(
                  new OneOf(OPERATION_DISCRIMINATOR_PROPERTY_ID, operationsWithoutCustomAuth))
              .build();
      properties.add(discriminator);
    } else {
      // only one auth type, no need for discriminator
      properties.add(
          HiddenProperty.builder()
              .id(AUTH_DISCRIMINATOR_PROPERTY_ID)
              .group("authentication")
              .value(authentications.iterator().next().id())
              .binding(new ZeebeInput(AUTH_DISCRIMINATOR_PROPERTY_ID))
              .build());
    }

    // handle default auth types
    for (var authentication : authentications) {
      var authProperties =
          HttpAuthentication.getPropertyPrefabs(authentication).stream()
              .map(
                  builder ->
                      builder
                          .condition(
                              new AllMatch(
                                  new Equals(AUTH_DISCRIMINATOR_PROPERTY_ID, authentication.id()),
                                  new OneOf(
                                      OPERATION_DISCRIMINATOR_PROPERTY_ID,
                                      operationsWithoutCustomAuth)))
                          .build())
              .toList();

      properties.addAll(authProperties);
    }

    // handle operation-specific auth types
    for (var operation : operations) {
      if (operation.authenticationOverride() == null) {
        continue;
      }
      for (var authentication : operation.authenticationOverride()) {
        var authProperties =
            HttpAuthentication.getPropertyPrefabs(authentication).stream()
                .map(
                    builder ->
                        builder
                            .id(operation.id() + "_" + builder.getId()) // shade property ids
                            .condition(
                                new AllMatch(
                                    new Equals(AUTH_DISCRIMINATOR_PROPERTY_ID, authentication.id()),
                                    new Equals(
                                        OPERATION_DISCRIMINATOR_PROPERTY_ID, operation.id())))
                            .build())
                .toList();

        properties.addAll(authProperties);
      }
    }

    return PropertyGroup.builder()
        .id("authentication")
        .label("Authentication")
        .properties(properties)
        .build();
  }

  static PropertyGroup parametersPropertyGroup(Collection<HttpOperation> operations) {
    List<Property> properties = new ArrayList<>();

    for (var operation : operations) {
      var operationPathProperty =
          HiddenProperty.builder()
              .id(operation.id() + "_path")
              .group("parameters")
              .binding(new ZeebeInput(OPERATION_PATH_INPUT_NAME))
              .value(operation.pathFeelExpression())
              .condition(new Equals(OPERATION_DISCRIMINATOR_PROPERTY_ID, operation.id()))
              .build();
      var operationHeadersProperty =
          HiddenProperty.builder()
              .id(operation.id() + "_headers")
              .group("parameters")
              .value(operation.headersFeelExpression())
              .binding(new ZeebeInput("headers"))
              .condition(new Equals(OPERATION_DISCRIMINATOR_PROPERTY_ID, operation.id()))
              .build();
      var operationQueryProperty =
          HiddenProperty.builder()
              .id(operation.id() + "_queryParameters")
              .group("parameters")
              .value(operation.queryParamsFeelExpression())
              .binding(new ZeebeInput("queryParameters"))
              .condition(new Equals(OPERATION_DISCRIMINATOR_PROPERTY_ID, operation.id()))
              .build();
      var operationMethodProperty =
          HiddenProperty.builder()
              .id(operation.id() + "_method")
              .group("parameters")
              .value(operation.method().name())
              .binding(new ZeebeInput("method"))
              .condition(new Equals(OPERATION_DISCRIMINATOR_PROPERTY_ID, operation.id()))
              .build();

      properties.add(operationPathProperty);
      properties.add(operationHeadersProperty);
      properties.add(operationQueryProperty);
      properties.add(operationMethodProperty);

      properties.addAll(operation.properties());
    }
    return PropertyGroup.builder()
        .id("parameters")
        .label("Parameters")
        .properties(properties)
        .build();
  }

  static PropertyGroup requestBodyPropertyGroup(Collection<HttpOperation> operations) {
    List<Property> properties = new ArrayList<>();

    for (var operation : operations) {
      if (!operation.method().supportsBody) {
        continue;
      }
      properties.add(
          StringProperty.builder()
              .id(operation.id() + "_body")
              .label("Request body")
              .feel(FeelMode.required)
              .group("requestBody")
              .value(operation.bodyFeelExpression())
              .condition(new Equals(OPERATION_DISCRIMINATOR_PROPERTY_ID, operation.id()))
              .binding(new ZeebeInput("body"))
              .build());
    }
    return PropertyGroup.builder()
        .id("requestBody")
        .label("Request body")
        .properties(properties)
        .build();
  }
}
