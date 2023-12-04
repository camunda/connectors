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
package io.camunda.connector.generator.java;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.generator.api.ElementTemplateGenerator;
import io.camunda.connector.generator.api.GeneratorConfiguration;
import io.camunda.connector.generator.api.GeneratorConfiguration.ConnectorMode;
import io.camunda.connector.generator.dsl.BpmnType;
import io.camunda.connector.generator.dsl.ElementTemplateIcon;
import io.camunda.connector.generator.dsl.OutboundElementTemplate;
import io.camunda.connector.generator.dsl.PropertyBuilder;
import io.camunda.connector.generator.dsl.PropertyGroup;
import io.camunda.connector.generator.dsl.PropertyGroup.PropertyGroupBuilder;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.util.ConfigurationUtil;
import io.camunda.connector.generator.java.util.ReflectionUtil;
import io.camunda.connector.generator.java.util.TemplatePropertiesUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class OutboundClassBasedTemplateGenerator
    implements ElementTemplateGenerator<Class<?>, OutboundElementTemplate> {

  private static final Set<BpmnType> SUPPORTED_ELEMENT_TYPES =
      Set.of(
          BpmnType.SERVICE_TASK,
          BpmnType.INTERMEDIATE_THROW_EVENT,
          BpmnType.SCRIPT_TASK,
          BpmnType.MESSAGE_END_EVENT);
  private static final GeneratorConfiguration.ConnectorElementType DEFAULT_ELEMENT_TYPE =
      new GeneratorConfiguration.ConnectorElementType(Set.of(BpmnType.TASK), BpmnType.SERVICE_TASK);
  private final ClassLoader classLoader;

  public OutboundClassBasedTemplateGenerator(ClassLoader classLoader) {
    this.classLoader = classLoader;
  }

  public OutboundClassBasedTemplateGenerator() {
    this(Thread.currentThread().getContextClassLoader());
  }

  @Override
  public List<OutboundElementTemplate> generate(
      Class<?> connectorDefinition, GeneratorConfiguration configuration) {

    var connector =
        ReflectionUtil.getRequiredAnnotation(connectorDefinition, OutboundConnector.class);
    var template = ReflectionUtil.getRequiredAnnotation(connectorDefinition, ElementTemplate.class);
    var connectorInput = template.inputDataClass();

    GeneratorConfiguration mergedConfig = ConfigurationUtil.fromAnnotation(template, configuration);
    var elementTypes = mergedConfig.elementTypes();
    if (elementTypes.isEmpty()) {
      elementTypes = Set.of(DEFAULT_ELEMENT_TYPE);
    }
    elementTypes.stream()
        .filter(t -> !SUPPORTED_ELEMENT_TYPES.contains(t.elementType()))
        .findFirst()
        .ifPresent(
            t -> {
              throw new IllegalArgumentException(
                  String.format("Unsupported element type '%s'", t.elementType().getName()));
            });

    List<PropertyBuilder> properties =
        TemplatePropertiesUtil.extractTemplatePropertiesFromType(connectorInput);

    var groupsDefinedInProperties =
        new ArrayList<>(TemplatePropertiesUtil.groupProperties(properties));
    var manuallyDefinedGroups = Arrays.asList(template.propertyGroups());

    final List<PropertyGroup> mergedGroups = new ArrayList<>();

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
      mergedGroups.addAll(
          new ArrayList<>(
              groupsDefinedInProperties.stream().map(PropertyGroupBuilder::build).toList()));
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

    mergedGroups.add(PropertyGroup.OUTPUT_GROUP);
    mergedGroups.add(PropertyGroup.ERROR_GROUP);
    mergedGroups.add(PropertyGroup.RETRIES_GROUP);

    var nonGroupedProperties =
        properties.stream().filter(property -> property.build().getGroup() == null).toList();

    var icon =
        template.icon().isBlank() ? null : ElementTemplateIcon.from(template.icon(), classLoader);

    return elementTypes.stream()
        .map(
            elementType ->
                OutboundElementTemplate.builder()
                    .id(template.id())
                    .type(
                        connector.type(),
                        ConnectorMode.HYBRID.equals(configuration.connectorMode()))
                    .name(template.name())
                    .version(template.version())
                    .appliesTo(elementType.appliesTo())
                    .elementType(elementType.elementType())
                    .icon(icon)
                    .documentationRef(
                        template.documentationRef().isEmpty() ? null : template.documentationRef())
                    .description(template.description().isEmpty() ? null : template.description())
                    .properties(nonGroupedProperties.stream().map(PropertyBuilder::build).toList())
                    .propertyGroups(mergedGroups)
                    .build())
        .toList();
  }
}
