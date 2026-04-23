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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PropertyUtil {

  static final String OPERATION_DISCRIMINATOR_PROPERTY_ID = "operationId";
  static final String AUTH_DISCRIMINATOR_PROPERTY_ID = "authType";
  static final String OPERATION_PATH_INPUT_NAME = "operationPath";

  /**
   * Create a pre-configured property builder for the auth type discriminator. The caller is
   * responsible for setting the final {@code id} on the returned builder. {@code availableTypes}
   * must be a {@link List} so that the default selection (first element) is deterministic.
   */
  static PropertyBuilder authDiscriminatorPropertyPrefab(List<HttpAuthentication> availableTypes) {

    if (availableTypes.isEmpty()) {
      throw new IllegalArgumentException("No auth types, expected at least one");
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
                      && !basicAuth.key().isEmpty()) {
                    label += " (" + basicAuth.key() + ")";
                  }
                  return new DropdownProperty.DropdownChoice(label, type.id());
                })
            .toList();

    return DropdownProperty.builder()
        .choices(choices)
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
                        .toList())
                .id(OPERATION_DISCRIMINATOR_PROPERTY_ID)
                .group("operation")
                .value(operations.iterator().next().id())
                .binding(new ZeebeInput(OPERATION_DISCRIMINATOR_PROPERTY_ID))
                .build())
        .build();
  }

  static PropertyGroup serverDiscriminatorPropertyGroup(Collection<HttpServerData> servers) {
    Collection<HttpServerData> configuredServers = servers == null ? List.of() : servers;
    List<Property> properties = new ArrayList<>();

    if (configuredServers.isEmpty()) {
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
    } else if (configuredServers.size() == 1) {
      // single server: visible and pre-filled, but editable
      properties.add(
          StringProperty.builder()
              .id("baseUrl")
              .group("server")
              .label("Base URL")
              .value(configuredServers.iterator().next().baseUrl())
              .binding(new ZeebeInput("baseUrl"))
              .constraints(PropertyConstraints.builder().notEmpty(true).build())
              .feel(FeelMode.optional)
              .build());
    } else {
      // multiple servers, add a dropdown
      properties.add(
          DropdownProperty.builder()
              .choices(
                  configuredServers.stream()
                      .map(server -> new DropdownChoice(server.label(), server.baseUrl()))
                      .toList())
              .id("baseUrl")
              .value(configuredServers.iterator().next().baseUrl())
              .label("Server")
              .group("server")
              .binding(new ZeebeInput("baseUrl"))
              .build());
    }
    return PropertyGroup.builder().id("server").label("Server").properties(properties).build();
  }

  /**
   * Result of {@link #authPropertyGroup}. Carries both the generated group and whether all auth
   * properties are unconditional, so callers do not have to re-derive that fact.
   */
  record AuthGroupResult(PropertyGroup group, boolean unconditional) {}

  static AuthGroupResult authPropertyGroup(
      Collection<HttpAuthentication> authentications, Collection<HttpOperation> operations) {
    Collection<HttpAuthentication> configuredAuthentications =
        authentications == null ? List.of() : authentications;

    // Group operations by their effective auth configuration.
    // Order of first appearance is preserved via LinkedHashMap.
    LinkedHashMap<List<AuthFingerprint>, List<HttpAuthentication>> authKeyToTypes =
        new LinkedHashMap<>();
    LinkedHashMap<List<AuthFingerprint>, List<String>> authKeyToOpIds = new LinkedHashMap<>();

    for (var operation : operations) {
      List<HttpAuthentication> effectiveAuth =
          (operation.authenticationOverride() != null
                  && !operation.authenticationOverride().isEmpty())
              ? operation.authenticationOverride()
              : new ArrayList<>(configuredAuthentications);

      // Skip operations with no effective auth (caller passed empty authentications and this
      // operation has no override; the resulting template simply has no auth section).
      if (effectiveAuth.isEmpty()) {
        continue;
      }

      List<AuthFingerprint> key = effectiveAuth.stream().map(AuthFingerprint::from).toList();
      authKeyToTypes.putIfAbsent(key, effectiveAuth);
      authKeyToOpIds.computeIfAbsent(key, k -> new ArrayList<>()).add(operation.id());
    }

    boolean unconditional = authKeyToTypes.size() == 1;

    List<Property> properties = new ArrayList<>();

    for (var entry : authKeyToTypes.entrySet()) {
      List<AuthFingerprint> key = entry.getKey();
      List<HttpAuthentication> auths = entry.getValue();
      // Sort op IDs so the prefix is the lexicographically smallest ID in the group,
      // independent of the order in which operations were registered.
      List<String> opIds = authKeyToOpIds.get(key).stream().sorted().toList();

      // In the conditional (multi-group) case each group gets a scoped discriminator ID to avoid
      // duplicate property IDs across groups. The prefix is the sorted-first op ID of the group.
      String discriminatorId =
          unconditional ? AUTH_DISCRIMINATOR_PROPERTY_ID : opIds.getFirst() + "_authType";

      // --- discriminator property ---
      if (auths.size() > 1) {
        var discriminatorBuilder = authDiscriminatorPropertyPrefab(auths).id(discriminatorId);
        if (!unconditional) {
          discriminatorBuilder.condition(new OneOf(OPERATION_DISCRIMINATOR_PROPERTY_ID, opIds));
        }
        properties.add(discriminatorBuilder.build());
      } else {
        // single auth type — hidden discriminator
        var discriminatorBuilder =
            HiddenProperty.builder()
                .id(discriminatorId)
                .group("authentication")
                .value(auths.getFirst().id())
                .binding(new ZeebeInput(AUTH_DISCRIMINATOR_PROPERTY_ID));
        if (!unconditional) {
          discriminatorBuilder.condition(new OneOf(OPERATION_DISCRIMINATOR_PROPERTY_ID, opIds));
        }
        properties.add(discriminatorBuilder.build());
      }

      // --- auth field properties ---
      for (var authentication : auths) {
        var authProperties =
            HttpAuthentication.getPropertyPrefabs(authentication).stream()
                .map(
                    builder -> {
                      if (unconditional && auths.size() == 1) {
                        // single auth for all ops — no condition at all
                        return builder.build();
                      } else if (unconditional) {
                        // multiple auth types but unconditional (all ops share same multi-auth set)
                        return builder
                            .condition(new Equals(discriminatorId, authentication.id()))
                            .build();
                      } else {
                        // conditional — scope to the ops in this group
                        // prefix field IDs with the sorted-first op ID to avoid duplicates
                        String fieldId = opIds.getFirst() + "_" + builder.getId();
                        return builder
                            .id(fieldId)
                            .condition(
                                new AllMatch(
                                    new Equals(discriminatorId, authentication.id()),
                                    new OneOf(OPERATION_DISCRIMINATOR_PROPERTY_ID, opIds)))
                            .build();
                      }
                    })
                .toList();
        properties.addAll(authProperties);
      }
    }

    PropertyGroup group =
        PropertyGroup.builder()
            .id("authentication")
            .label("Authentication")
            .properties(properties)
            .build();
    return new AuthGroupResult(group, unconditional);
  }

  private record AuthFingerprint(String id, List<String> details) {

    private static AuthFingerprint from(HttpAuthentication authentication) {
      return switch (authentication) {
        case HttpAuthentication.BasicAuth basicAuth ->
            new AuthFingerprint(authentication.id(), List.of(basicAuth.key()));
        case HttpAuthentication.ApiKey apiKey ->
            new AuthFingerprint(
                authentication.id(),
                List.of(
                    apiKey.in() == null ? "" : apiKey.in(),
                    apiKey.key() == null ? "" : apiKey.key(),
                    apiKey.value() == null ? "" : apiKey.value()));
        case HttpAuthentication.OAuth2 oauth2 ->
            new AuthFingerprint(
                authentication.id(),
                List.of(
                    oauth2.tokenUrl() == null ? "" : oauth2.tokenUrl(),
                    oauth2.scopes().stream()
                        .sorted()
                        .reduce((left, right) -> left + "\u0000" + right)
                        .orElse("")));
        default -> new AuthFingerprint(authentication.id(), List.of());
      };
    }
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
