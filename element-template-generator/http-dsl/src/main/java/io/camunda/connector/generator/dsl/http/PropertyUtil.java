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

import io.camunda.connector.generator.dsl.DropdownProperty;
import io.camunda.connector.generator.dsl.DropdownProperty.DropdownChoice;
import io.camunda.connector.generator.dsl.HiddenProperty;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeInput;
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeProperty;
import io.camunda.connector.generator.dsl.PropertyBuilder;
import io.camunda.connector.generator.dsl.PropertyCondition.AllMatch;
import io.camunda.connector.generator.dsl.PropertyCondition.Equals;
import io.camunda.connector.generator.dsl.PropertyCondition.OneOf;
import io.camunda.connector.generator.dsl.PropertyConstraints;
import io.camunda.connector.generator.dsl.PropertyGroup;
import io.camunda.connector.generator.dsl.StringProperty;
import io.camunda.connector.generator.dsl.http.HttpOperationProperty.Target;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.util.TemplatePropertiesUtil;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class PropertyUtil {

  static final String OPERATION_DISCRIMINATOR_PROPERTY_ID = "operationId";
  static final String AUTH_DISCRIMINATOR_PROPERTY_ID = "authType";
  static final String OPERATION_PATH_INPUT_NAME = "operationPath";

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
            .map(
                type -> {
                  String label = type.label();
                  if (type instanceof HttpAuthentication.ApiKey apiKey && !apiKey.key().isEmpty()) {
                    label += " (" + apiKey.key() + ")";
                  }
                  if (type instanceof HttpAuthentication.BasicAuth basicAuth
                      && !basicAuth.key.isEmpty()) {
                    label += " (" + basicAuth.key + ")";
                  }
                  return new DropdownProperty.DropdownChoice(label, type.id());
                })
            .toList();

    return DropdownProperty.builder()
        .choices(choices)
        .id("authType")
        .group("authentication")
        .label("Authentication")
        .optional(false)
        .binding(new ZeebeProperty("authentication.dropdown"))
        .value(choices.getFirst().value());
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
                .group("operation")
                .value(operations.iterator().next().id())
                .binding(new ZeebeInput(OPERATION_DISCRIMINATOR_PROPERTY_ID))
                .build())
        .build();
  }

  static PropertyGroup serverDiscriminatorPropertyGroup(Collection<HttpServerData> servers) {
    List<Property> properties = new ArrayList<>();

    if (servers == null || servers.isEmpty()) {
      // add a visible property for base URL, no servers configured
      properties.add(
          StringProperty.builder()
              .id("baseUrl")
              .group("server")
              .label("Base URL")
              .binding(new ZeebeInput("baseUrl"))
              .constraints(PropertyConstraints.builder().notEmpty(true).build())
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
              .value(servers.iterator().next().baseUrl())
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
            .filter(
                op -> op.authenticationOverride() == null || op.authenticationOverride().isEmpty())
            .map(HttpOperation::id)
            .toList();

    List<Property> properties = new ArrayList<>();
    if (authentications.size() > 1) {
      var discriminator =
          authDiscriminatorPropertyPrefab(authentications)
              .condition(
                  new OneOf(OPERATION_DISCRIMINATOR_PROPERTY_ID, operationsWithoutCustomAuth))
              .build();
      properties.add(discriminator);
    } else if (!authentications.isEmpty()) {
      // only one auth type, no need for discriminator
      properties.add(
          HiddenProperty.builder()
              .id(AUTH_DISCRIMINATOR_PROPERTY_ID)
              .group("authentication")
              .value(authentications.iterator().next().id())
              .binding(new ZeebeInput(AUTH_DISCRIMINATOR_PROPERTY_ID))
              .condition(
                  new OneOf(OPERATION_DISCRIMINATOR_PROPERTY_ID, operationsWithoutCustomAuth))
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
      var authDiscriminator = operation.id() + "_" + "authType";
      final boolean addedDiscriminator;
      if (operation.authenticationOverride().size() > 1) {
        addedDiscriminator = true;
        properties.add(
            authDiscriminatorPropertyPrefab(operation.authenticationOverride())
                .id(authDiscriminator)
                .condition(new Equals(OPERATION_DISCRIMINATOR_PROPERTY_ID, operation.id()))
                .build());
      } else {
        addedDiscriminator = false;
      }
      for (var authentication : operation.authenticationOverride()) {
        var authProperties =
            HttpAuthentication.getPropertyPrefabs(authentication).stream() // size1 = 4
                .map(
                    builder -> {
                      String id = operation.id() + "_" + builder.getId();
                      builder.id(id); // shade property ids
                      if (addedDiscriminator) {
                        builder.condition(
                            new AllMatch(
                                new Equals(authDiscriminator, authentication.id()),
                                new Equals(OPERATION_DISCRIMINATOR_PROPERTY_ID, operation.id())));
                      } else {
                        builder.condition(
                            new Equals(OPERATION_DISCRIMINATOR_PROPERTY_ID, operation.id()));
                      }
                      return builder.build();
                    })
                .toList();
        properties.addAll(authProperties); // here the duplicated are generated
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
      Map<String, String> headerProperties = new HashMap<>();
      Map<String, String> queryProperties = new HashMap<>();
      List<Property> transformedProperties = new ArrayList<>();

      for (var property : operation.properties()) {
        if (property.target() == Target.BODY) {
          // body properties are handled separately
          continue;
        }
        var transformed = transformProperty(operation.id(), property, "parameters");
        if (!(transformed.getBinding() instanceof ZeebeInput binding)) {
          throw new RuntimeException(
              "Unexpected binding type: " + transformed.getBinding().getClass());
        }

        if (property.target() == Target.HEADER) {
          headerProperties.put(property.id(), binding.name());
        } else if (property.target() == Target.QUERY) {
          queryProperties.put(property.id(), binding.name());
        }
        transformedProperties.add(transformed);
      }

      var operationPathProperty =
          HiddenProperty.builder()
              .id(operation.id() + "_$path")
              .group("parameters")
              .binding(new ZeebeInput(OPERATION_PATH_INPUT_NAME))
              .value(operation.pathFeelExpression().build())
              .condition(new Equals(OPERATION_DISCRIMINATOR_PROPERTY_ID, operation.id()))
              .build();
      var operationHeadersProperty =
          HiddenProperty.builder()
              .id(operation.id() + "_$headers")
              .group("parameters")
              .value(buildContextExpression(headerProperties))
              .binding(new ZeebeInput("headers"))
              .condition(new Equals(OPERATION_DISCRIMINATOR_PROPERTY_ID, operation.id()))
              .build();
      var operationQueryProperty =
          HiddenProperty.builder()
              .id(operation.id() + "_$queryParameters")
              .group("parameters")
              .value(buildContextExpression(queryProperties))
              .binding(new ZeebeInput("queryParameters"))
              .condition(new Equals(OPERATION_DISCRIMINATOR_PROPERTY_ID, operation.id()))
              .build();
      var operationMethodProperty =
          HiddenProperty.builder()
              .id(operation.id() + "_$method")
              .group("parameters")
              .value(operation.method().name())
              .binding(new ZeebeInput("method"))
              .condition(new Equals(OPERATION_DISCRIMINATOR_PROPERTY_ID, operation.id()))
              .build();

      properties.addAll(transformedProperties);

      properties.add(operationPathProperty);
      properties.add(operationHeadersProperty);
      properties.add(operationQueryProperty);
      properties.add(operationMethodProperty);
    }
    return PropertyGroup.builder()
        .id("parameters")
        .label("Parameters")
        .properties(properties) // size 8
        .build();
  }

  private static Property transformProperty(
      String operationId, HttpOperationProperty property, String group) {
    PropertyBuilder builder =
        switch (property.type()) {
          case STRING -> StringProperty.builder().value(property.example()).feel(FeelMode.optional);
          case HIDDEN -> HiddenProperty.builder().value(property.example()).feel(FeelMode.disabled);
          case ENUM ->
              DropdownProperty.builder()
                  .choices(
                      property.choices().stream()
                          .map(choice -> new DropdownChoice(choice, choice))
                          .toList());
          case FEEL -> StringProperty.builder().value(property.example()).feel(FeelMode.required);
        };

    // shade property id with operation id as there may be duplicates in different operations
    builder
        .id(operationId + "_" + property.target().name().toLowerCase() + "_" + property.id())
        .label(TemplatePropertiesUtil.transformIdIntoLabel(property.id()))
        .description(property.description())
        .optional(!property.required())
        .binding(new ZeebeInput(property.id()))
        .condition(new Equals(OPERATION_DISCRIMINATOR_PROPERTY_ID, operationId))
        .group(group);

    if (property.required()) {
      builder.constraints(PropertyConstraints.builder().notEmpty(true).build());
    }

    return builder.build();
  }

  static PropertyGroup requestBodyPropertyGroup(Collection<HttpOperation> operations) {
    List<Property> properties = new ArrayList<>();

    for (var operation : operations) {
      if (!operation.method().supportsBody) {
        continue;
      }

      var bodyProperties =
          operation.properties().stream()
              .filter(p -> p.target() == Target.BODY)
              .map(p -> transformProperty(operation.id(), p, "requestBody"))
              .toList();

      Property bodyAggregationProperty;
      if (bodyProperties.isEmpty()) {
        bodyAggregationProperty =
            StringProperty.builder()
                .id(operation.id() + "_body")
                .feel(FeelMode.required)
                .group("requestBody")
                .value(
                    Optional.ofNullable(operation.bodyFeelExpression())
                        .map(HttpFeelBuilder::build)
                        .orElse(""))
                .condition(new Equals(OPERATION_DISCRIMINATOR_PROPERTY_ID, operation.id()))
                .binding(new ZeebeInput("body"))
                .build();
      } else {
        bodyAggregationProperty =
            HiddenProperty.builder()
                .id(operation.id() + "_$body")
                .group("requestBody")
                .value(
                    Optional.ofNullable(operation.bodyFeelExpression())
                        .map(item -> item)
                        .map(HttpFeelBuilder::build)
                        .orElse(""))
                .condition(new Equals(OPERATION_DISCRIMINATOR_PROPERTY_ID, operation.id()))
                .binding(new ZeebeInput("body"))
                .build();
      }

      properties.addAll(bodyProperties);
      properties.add(bodyAggregationProperty);
    }
    return PropertyGroup.builder()
        .id("requestBody")
        .label("Request body")
        .properties(properties)
        .build();
  }

  static PropertyGroup urlPropertyGroup() {
    var urlProperty =
        HiddenProperty.builder()
            .id("url")
            .binding(new ZeebeInput("url"))
            .group("url")
            .value("= baseUrl + " + OPERATION_PATH_INPUT_NAME)
            .build();
    return PropertyGroup.builder().id("url").label("URL").properties(urlProperty).build();
  }

  private static String buildContextExpression(Map<String, String> properties) {
    StringBuilder sb = new StringBuilder();
    sb.append("={");
    var it = properties.entrySet().iterator();
    while (it.hasNext()) {
      var entry = it.next();
      sb.append(entry.getKey()).append(": ").append(entry.getValue());
      if (it.hasNext()) {
        sb.append(", ");
      }
    }
    sb.append("}");
    return sb.toString();
  }
}
