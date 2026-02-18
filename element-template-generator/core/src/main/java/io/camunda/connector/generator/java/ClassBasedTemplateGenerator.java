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

import static io.camunda.connector.generator.java.util.OperationBasedConnectorUtil.*;
import static io.camunda.connector.generator.java.util.TemplateGenerationStringUtil.camelCaseToSpaces;

import io.camunda.connector.api.annotation.Operation;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.api.outbound.OutboundConnectorProvider;
import io.camunda.connector.generator.api.ElementTemplateGenerator;
import io.camunda.connector.generator.api.GeneratorConfiguration;
import io.camunda.connector.generator.api.GeneratorConfiguration.ConnectorElementType;
import io.camunda.connector.generator.api.GeneratorConfiguration.ConnectorMode;
import io.camunda.connector.generator.api.GeneratorConfiguration.GenerationFeature;
import io.camunda.connector.generator.dsl.*;
import io.camunda.connector.generator.java.annotation.BpmnType;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.processor.TemplatePropertyAnnotationProcessor;
import io.camunda.connector.generator.java.util.*;
import io.camunda.connector.generator.java.util.TemplateGenerationContext.Outbound;
import io.camunda.connector.util.reflection.ReflectionUtil;
import io.camunda.connector.util.reflection.ReflectionUtil.MethodWithAnnotation;
import java.util.*;
import java.util.regex.Pattern;

public class ClassBasedTemplateGenerator implements ElementTemplateGenerator<Class<?>> {

  private static final Pattern SEM_VER_PATTERN =
      Pattern.compile(
          "^(?:[~^]?(?:0|[1-9]\\d*)\\.(?:\\d+)(?:\\.\\d+)?(?:-[\\da-z.-]+)?(?:\\+[\\da-z.-]+)?|\\*|\\d+\\.\\d+|\\d+)(?:\\s*[-,]\\s*[~^]?(?:0|[1-9]\\d*)\\.(?:\\d+)(?:\\.\\d+)?(?:-[\\da-z.-]+)?(?:\\+[\\da-z.-]+)?)?$");
  private final ClassLoader classLoader;

  public ClassBasedTemplateGenerator(ClassLoader classLoader) {
    this.classLoader = classLoader;
  }

  public ClassBasedTemplateGenerator() {
    this(Thread.currentThread().getContextClassLoader());
  }

  private static String createId(
      TemplateGenerationContext context,
      String templateId,
      ConnectorElementType elementType,
      final boolean isHybridMode) {
    String baseTemplateId =
        Optional.ofNullable(elementType.templateIdOverride())
            .orElseGet(
                () ->
                    context.elementTypes().size() > 1
                        ? templateId + ":" + elementType.elementType().getId()
                        : templateId);
    return isHybridMode
        ? baseTemplateId + GeneratorConfiguration.HYBRID_TEMPLATE_ID_SUFFIX
        : baseTemplateId;
  }

  private static String createName(
      TemplateGenerationContext context,
      String templateName,
      ConnectorElementType elementType,
      boolean isHybridMode) {
    String baseTemplateName =
        Optional.ofNullable(elementType.templateNameOverride())
            .orElseGet(
                () -> {
                  if (context.elementTypes().size() > 1) {
                    return templateName
                        + " ("
                        + camelCaseToSpaces(elementType.elementType().getId())
                        + ")";
                  }
                  return templateName;
                });
    return isHybridMode
        ? GeneratorConfiguration.HYBRID_TEMPLATE_NAME_PREFIX + baseTemplateName
        : baseTemplateName;
  }

  @Override
  public List<io.camunda.connector.generator.dsl.ElementTemplate> generate(
      Class<?> connectorDefinition, GeneratorConfiguration configuration) {

    var template = ReflectionUtil.getRequiredAnnotation(connectorDefinition, ElementTemplate.class);
    var connectorInput = template.inputDataClass();
    var context = TemplateGenerationContextUtil.createContext(connectorDefinition, configuration);

    List<PropertyBuilder> properties;
    if (OutboundConnectorFunction.class.isAssignableFrom(connectorDefinition)
        || InboundConnectorExecutable.class.isAssignableFrom(connectorDefinition)) {
      properties =
          new ArrayList<>(
              TemplatePropertiesUtil.extractTemplatePropertiesFromType(connectorInput, context));
    } else if (OutboundConnectorProvider.class.isAssignableFrom(connectorDefinition)) {
      List<MethodWithAnnotation<Operation>> methods =
          ReflectionUtil.getMethodsAnnotatedWith(connectorDefinition, Operation.class);
      properties = new ArrayList<>(List.of(createOperationsProperty(methods)));
      properties.addAll(getOperationProperties(methods, context));
    } else {
      throw new IllegalArgumentException(
          "Connector class "
              + connectorDefinition.getName()
              + " must implement OutboundConnectorFunction, InboundConnectorExecutable or OutboundConnectorProvider");
    }

    List<PropertyBuilder> extensionProperties = generateExtensionProperties(template);
    properties.addAll(extensionProperties);

    final List<PropertyGroup> mergedGroups = new ArrayList<>();

    var groupsDefinedInProperties =
        new ArrayList<>(TemplatePropertiesUtil.groupProperties(properties));

    var manuallyDefinedGroups = Arrays.asList(template.propertyGroups());

    if (!manuallyDefinedGroups.isEmpty()) {
      for (ElementTemplate.PropertyGroup group : manuallyDefinedGroups) {
        var groupDefinedInProperties =
            groupsDefinedInProperties.stream()
                .filter(g -> g.getId().equals(group.id()))
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
                .tooltip(group.tooltip().isBlank() ? null : group.tooltip())
                .openByDefault(group.openByDefault() == Boolean.TRUE ? null : false)
                .properties(groupDefinedInProperties.get().build().properties())
                .build());

        groupsDefinedInProperties.remove(groupDefinedInProperties.get());
      }
    }

    if (!groupsDefinedInProperties.isEmpty()) {
      mergedGroups.addAll(
          groupsDefinedInProperties.stream()
              .map(PropertyGroup.PropertyGroupBuilder::build)
              .toList());
    }

    if (groupsDefinedInProperties.isEmpty() && manuallyDefinedGroups.isEmpty()) {
      // default group so that user properties are higher up in the UI than the output/error mapping
      mergedGroups.add(
          PropertyGroup.builder()
              .id("default")
              .label("Properties")
              .properties(properties.toArray(PropertyBuilder[]::new))
              .build());
    }

    var nonGroupedProperties =
        properties.stream().filter(property -> property.build().getGroup() == null).toList();

    var icon =
        template.icon().isBlank() ? null : ElementTemplateIcon.from(template.icon(), classLoader);

    if (!template.engineVersion().isBlank()
        && !SEM_VER_PATTERN.matcher(template.engineVersion()).matches()) {
      throw new IllegalArgumentException(
          template.engineVersion() + " is not a valid semantic version");
    }

    return context.elementTypes().stream()
        .map(
            elementType -> {
              var builder =
                  context instanceof Outbound
                      ? ElementTemplateBuilder.createOutbound()
                      : ElementTemplateBuilder.createInbound();
              boolean isHybridMode = ConnectorMode.HYBRID.equals(configuration.connectorMode());
              return builder
                  .id(createId(context, template.id(), elementType, isHybridMode))
                  .type(context.connectorType(), isHybridMode)
                  .name(createName(context, template.name(), elementType, isHybridMode))
                  .version(template.version())
                  .appliesTo(elementType.appliesTo())
                  .engines(
                      !template.engineVersion().isBlank()
                          ? new Engines(template.engineVersion())
                          : null)
                  .elementType(elementType.elementType())
                  .icon(icon)
                  .metadata(
                      new io.camunda.connector.generator.dsl.ElementTemplate.Metadata(
                          template.metadata().keywords()))
                  .documentationRef(
                      template.documentationRef().isEmpty() ? null : template.documentationRef())
                  .description(template.description().isEmpty() ? null : template.description())
                  .properties(nonGroupedProperties.stream().map(PropertyBuilder::build).toList())
                  .propertyGroups(
                      addServiceProperties(
                          mergedGroups, context, elementType, configuration, template))
                  .build();
            })
        .toList();
  }

  private List<PropertyGroup> addServiceProperties(
      List<PropertyGroup> groups,
      TemplateGenerationContext context,
      ConnectorElementType elementType,
      GeneratorConfiguration configuration,
      ElementTemplate template) {
    var newGroups = new ArrayList<>(groups);
    if (context instanceof Outbound) {
      newGroups.add(
          PropertyGroup.ADD_CONNECTORS_DETAILS_OUTPUT.apply(template.id(), template.version()));
      newGroups.add(
          PropertyGroup.OUTPUT_GROUP_OUTBOUND.apply(
              template.defaultResultVariable(), template.defaultResultExpression()));
      newGroups.add(PropertyGroup.ERROR_GROUP);
      newGroups.add(PropertyGroup.RETRIES_GROUP);
    } else {

      if (configuration.features().get(GenerationFeature.ACKNOWLEDGEMENT_STRATEGY_SELECTION)
          == Boolean.TRUE) {
        newGroups.add(PropertyGroup.ACTIVATION_GROUP_WITH_CONSUME_UNMATCHED_EVENTS);
      } else {
        newGroups.add(PropertyGroup.ACTIVATION_GROUP);
      }

      if (elementType.elementType().equals(BpmnType.MESSAGE_START_EVENT)) {
        newGroups.add(PropertyGroup.CORRELATION_GROUP_MESSAGE_START_EVENT);
      } else if (elementType.elementType().equals(BpmnType.INTERMEDIATE_CATCH_EVENT)
          || elementType.elementType().equals(BpmnType.BOUNDARY_EVENT)
          || elementType.elementType().equals(BpmnType.RECEIVE_TASK)) {
        newGroups.add(
            PropertyGroup.CORRELATION_GROUP_INTERMEDIATE_CATCH_EVENT_OR_BOUNDARY_OR_RECEIVE);
      }
      if (configuration.features().get(GenerationFeature.INBOUND_DEDUPLICATION) == Boolean.TRUE) {
        newGroups.add(PropertyGroup.DEDUPLICATION_GROUP);
      }
      newGroups.add(
          PropertyGroup.OUTPUT_GROUP_INBOUND.apply(
              template.defaultResultVariable(), template.defaultResultExpression()));
    }
    return newGroups;
  }

  private List<PropertyBuilder> generateExtensionProperties(ElementTemplate template) {
    return Arrays.stream(template.extensionProperties())
        .map(
            extensionProperty ->
                HiddenProperty.builder()
                    .binding(new PropertyBinding.ZeebeProperty(extensionProperty.name()))
                    .value(extensionProperty.value())
                    .condition(
                        TemplatePropertyAnnotationProcessor.buildCondition(
                            extensionProperty.condition())))
        .toList();
  }
}
