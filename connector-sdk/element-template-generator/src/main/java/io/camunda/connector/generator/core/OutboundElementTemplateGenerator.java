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
package io.camunda.connector.generator.core;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.generator.annotation.ElementTemplate;
import io.camunda.connector.generator.core.util.ReflectionUtil;
import io.camunda.connector.generator.core.util.TemplatePropertiesUtil;
import io.camunda.connector.generator.dsl.CommonProperties;
import io.camunda.connector.generator.dsl.ElementTemplateIcon;
import io.camunda.connector.generator.dsl.OutboundElementTemplate;
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeInput;
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeTaskHeader;
import io.camunda.connector.generator.dsl.PropertyBuilder;
import io.camunda.connector.generator.dsl.PropertyGroup;
import io.camunda.connector.generator.dsl.PropertyGroup.PropertyGroupBuilder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

public class OutboundElementTemplateGenerator
    implements ElementTemplateGenerator<OutboundElementTemplate> {

  private final ClassLoader classLoader;

  public OutboundElementTemplateGenerator(ClassLoader classLoader) {
    this.classLoader = classLoader;
  }

  public OutboundElementTemplateGenerator() {
    this(Thread.currentThread().getContextClassLoader());
  }

  @Override
  public OutboundElementTemplate generate(Class<?> connectorDefinition) {
    var connector =
        ReflectionUtil.getRequiredAnnotation(connectorDefinition, OutboundConnector.class);
    var template = ReflectionUtil.getRequiredAnnotation(connectorDefinition, ElementTemplate.class);
    var connectorInput = template.inputDataClass();

    List<PropertyBuilder> properties =
        TemplatePropertiesUtil.extractTemplatePropertiesFromType(connectorInput).stream()
            .map(builder -> builder.binding(new ZeebeInput(builder.getId())))
            .toList();

    var groupsDefinedInProperties =
        new ArrayList<>(TemplatePropertiesUtil.groupProperties(properties));
    var manuallyDefinedGroups = Arrays.asList(template.propertyGroups());

    List<PropertyGroup> mergedGroups = new ArrayList<>();

    if (!manuallyDefinedGroups.isEmpty()) {
      for (ElementTemplate.PropertyGroup group : manuallyDefinedGroups) {
        var groupDefinedInProperties =
            groupsDefinedInProperties.stream()
                .filter(g -> g.build().id().equals(group.id()))
                .findFirst();

        if (groupDefinedInProperties.isEmpty()) {
          throw new IllegalStateException(
              String.format(
                  "Property group '%s' defined in @ElementTemplate but no properties with this group id found",
                  group.id()));
        }

        mergedGroups.add(
            PropertyGroup.builder()
                .id(group.id())
                .label(group.label())
                .properties(groupDefinedInProperties.get().build().properties())
                .build());
      }
    } else {
      mergedGroups =
          new ArrayList<>(
              groupsDefinedInProperties.stream().map(PropertyGroupBuilder::build).toList());
    }

    if (groupsDefinedInProperties.isEmpty()) {
      // default group so that user properties are higher up in the UI than the output/error mapping
      mergedGroups.add(
          PropertyGroup.builder()
              .id("default")
              .label("Properties")
              .properties(properties.toArray(PropertyBuilder[]::new))
              .build());
    }

    var outputGroup =
        PropertyGroup.builder()
            .id("output")
            .label("Output mapping")
            .properties(
                CommonProperties.RESULT_VARIABLE
                    .binding(new ZeebeTaskHeader("resultVariable"))
                    .build(),
                CommonProperties.RESULT_EXPRESSION
                    .binding(new ZeebeTaskHeader("resultExpression"))
                    .build())
            .build();
    var errorGroup =
        PropertyGroup.builder()
            .id("error")
            .label("Error handling")
            .properties(
                CommonProperties.ERROR_EXPRESSION
                    .binding(new ZeebeTaskHeader("errorExpression"))
                    .build())
            .build();

    mergedGroups.add(outputGroup);
    mergedGroups.add(errorGroup);

    var nonGroupedProperties =
        properties.stream().filter(property -> property.build().getGroup() == null).toList();

    var icon = template.icon().isBlank() ? null : resolveIcon(template.icon());

    return OutboundElementTemplate.builder()
        .id(template.id())
        .type(connector.type())
        .name(template.name())
        .version(template.version())
        .icon(icon)
        .documentationRef(
            template.documentationRef().isEmpty() ? null : template.documentationRef())
        .description(template.description().isEmpty() ? null : template.description())
        .properties(nonGroupedProperties.stream().map(PropertyBuilder::build).toList())
        .propertyGroups(mergedGroups)
        .build();
  }

  public ElementTemplateIcon resolveIcon(String iconDefinition) {
    if (iconDefinition.startsWith("https://")) {
      return new ElementTemplateIcon(iconDefinition);
    }
    try {
      return new ElementTemplateIcon(resolveIconFile(iconDefinition));
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "Invalid icon definition: " + iconDefinition + ", " + e.getMessage(), e);
    }
  }

  private String resolveIconFile(String path) throws IOException {
    var resource = classLoader.getResource(path);
    if (resource == null) {
      throw new IllegalArgumentException("Icon file not found: " + path);
    }
    String base64Data;
    try (var stream = resource.openStream()) {
      var bytes = stream.readAllBytes();
      base64Data = Base64.getEncoder().encodeToString(bytes);
    }

    if (path.endsWith(".svg")) {
      return "data:image/svg+xml;base64," + base64Data;
    } else if (path.endsWith(".png")) {
      return "data:image/png;base64," + base64Data;
    } else {
      throw new IllegalArgumentException("Unsupported icon file: " + path);
    }
  }
}
