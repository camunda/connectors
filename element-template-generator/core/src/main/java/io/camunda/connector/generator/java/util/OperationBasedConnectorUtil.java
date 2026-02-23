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
package io.camunda.connector.generator.java.util;

import static io.camunda.connector.generator.java.processor.TemplatePropertyAnnotationProcessor.buildCondition;
import static io.camunda.connector.generator.java.processor.TemplatePropertyAnnotationProcessor.getValue;
import static io.camunda.connector.util.reflection.ReflectionUtil.*;

import io.camunda.connector.api.annotation.Header;
import io.camunda.connector.api.annotation.Operation;
import io.camunda.connector.api.annotation.Variable;
import io.camunda.connector.generator.dsl.*;
import io.camunda.connector.generator.dsl.PropertyBinding;
import io.camunda.connector.generator.dsl.PropertyCondition;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.util.reflection.ReflectionUtil.MethodWithAnnotation;
import java.lang.reflect.Parameter;
import java.util.Comparator;
import java.util.List;

public class OperationBasedConnectorUtil {

  public static String OPERATION_PROPERTY_ID = "operation";
  public static String OPERATION_GROUP_ID = "operation";
  public static String OPERATION_TASK_HEADER_KEY = OPERATION_PROPERTY_ID;
  public static String OPERATION_PROPERTY_SEPARATOR = ":";
  public static String VARIABLE_PATH_SEPARATOR = ".";

  public static PropertyBuilder createOperationsProperty(
      List<MethodWithAnnotation<Operation>> methods) {
    if (methods.isEmpty()) {
      throw new IllegalArgumentException("No operations found for the connector.");
    }
    PropertyBuilder operationsProperty;
    if (methods.size() == 1) {
      operationsProperty = HiddenProperty.builder();
    } else {
      methods.sort(Comparator.comparing(o -> getOperationName(o.annotation())));
      operationsProperty =
          new DropdownProperty.DropdownPropertyBuilder()
              .choices(
                  methods.stream()
                      .map(
                          m -> {
                            Operation operation = m.annotation();
                            String operationName = getOperationName(operation);
                            String operationId = getOperationId(operation);
                            return new DropdownProperty.DropdownChoice(operationName, operationId);
                          })
                      .toList());
    }

    return operationsProperty
        .id(OPERATION_PROPERTY_ID)
        .binding(new PropertyBinding.ZeebeTaskHeader(OPERATION_TASK_HEADER_KEY))
        .label("Operation")
        .description("The operation to execute")
        .feel(FeelMode.disabled)
        .value(getOperationId(methods.getFirst().annotation()))
        .group("operation");
  }

  public static List<PropertyBuilder> getOperationProperties(
      List<MethodWithAnnotation<Operation>> methods, TemplateGenerationContext context) {
    return methods.stream().flatMap(m -> getOperationProperties(m, context).stream()).toList();
  }

  private static List<PropertyBuilder> getOperationProperties(
      MethodWithAnnotation<Operation> method, TemplateGenerationContext context) {
    Operation operation = method.annotation();
    List<Parameter> parameters =
        method.parameters().stream()
            .filter(
                p ->
                    !p.isSynthetic()
                        && (p.isAnnotationPresent(Variable.class)
                            || p.isAnnotationPresent(Header.class)))
            .toList();

    return parameters.stream()
        .map(
            parameter -> {
              boolean shouldMapParameterBindings =
                  TemplatePropertiesUtil.shouldMapBindingsForParameter(parameter);
              Variable variable = parameter.getAnnotation(Variable.class);
              if (variable != null) {
                List<PropertyBuilder> properties =
                    TemplatePropertiesUtil.extractTemplatePropertiesFromParameter(
                        parameter, context);
                return properties.stream()
                    .map(
                        property ->
                            mapProperty(property, operation, variable, shouldMapParameterBindings))
                    .toList();
              } else {
                return List.of(buildHeaderProperty(operation, parameter));
              }
            })
        .flatMap(List::stream)
        .toList();
  }

  private static PropertyBuilder buildHeaderProperty(Operation operation, Parameter parameter) {
    Header header = parameter.getAnnotation(Header.class);
    String headerName = getHeaderName(header);
    if (headerName.isBlank()) {
      throw new IllegalArgumentException(
          "Header parameter '"
              + parameter.getName()
              + "' of operation '"
              + getOperationName(operation)
              + "' is missing a name. Please provide a name using the @Header annotation.");
    }
    TemplateProperty templateProperty = parameter.getAnnotation(TemplateProperty.class);
    String id = concatenateOperationIdAndPropertyId(getOperationId(operation), headerName);
    var builder =
        StringProperty.builder().binding(new PropertyBinding.ZeebeTaskHeader(headerName)).id(id);
    if (templateProperty != null) {
      builder
          .id(
              !templateProperty.id().isBlank()
                  ? concatenateOperationIdAndPropertyId(
                      getOperationId(operation), templateProperty.id())
                  : id)
          .group(
              !templateProperty.group().isBlank() ? templateProperty.group() : OPERATION_GROUP_ID)
          .label(templateProperty.label())
          .tooltip(templateProperty.tooltip())
          .description(templateProperty.description())
          .feel(templateProperty.feel());

      if (!templateProperty.defaultValue().isBlank()) {
        builder.value(getValue(templateProperty, parameter.getType(), true));
      }
      if (templateProperty.condition() != null) {
        builder.condition(mapCondition(buildCondition(templateProperty.condition()), operation));
      }
    }
    return builder;
  }

  public static String concatenateOperationIdAndPropertyId(String operationId, String propertyId) {
    return operationId + OPERATION_PROPERTY_SEPARATOR + propertyId;
  }

  private static PropertyBuilder mapProperty(
      PropertyBuilder property,
      Operation operation,
      Variable variable,
      boolean shouldMapParameterBindings) {
    setTemplatePropertyValues(property, operation);
    if (shouldMapParameterBindings) {
      property.binding(mapBinding(property.getBinding(), variable));
    }
    return property;
  }

  private static void setTemplatePropertyValues(PropertyBuilder property, Operation operation) {
    var id = concatenateOperationIdAndPropertyId(getOperationId(operation), property.getId());
    var group =
        property.getGroup() == null || property.getGroup().isBlank()
            ? OPERATION_GROUP_ID
            : property.getGroup();
    property.id(id).condition(mapCondition(property.getCondition(), operation)).group(group);
  }

  private static PropertyCondition mapCondition(PropertyCondition condition, Operation operation) {
    var operationId = getOperationId(operation);
    var operationCondition = new PropertyCondition.Equals(OPERATION_PROPERTY_ID, operationId);
    return switch (condition) {
      case null -> new PropertyCondition.AllMatch(List.of(operationCondition));
      case PropertyCondition.Equals eq ->
          new PropertyCondition.AllMatch(
              List.of(operationCondition, mapPropertyCondition(eq, operationId)));
      case PropertyCondition.IsActive isActive ->
          new PropertyCondition.AllMatch(
              List.of(operationCondition, mapPropertyCondition(isActive, operationId)));
      case PropertyCondition.AllMatch allMatch -> {
        PropertyCondition.AllMatch allMatchPatched = mapPropertyCondition(allMatch, operationId);
        allMatchPatched.allMatch().add(operationCondition);
        yield allMatchPatched;
      }
      case PropertyCondition.OneOf oneOf ->
          new PropertyCondition.AllMatch(
              List.of(operationCondition, mapPropertyCondition(oneOf, operationId)));
    };
  }

  private static <T extends PropertyCondition> T mapPropertyCondition(
      T condition, String operationId) {
    return (T)
        switch (condition) {
          case PropertyCondition.Equals eq ->
              new PropertyCondition.Equals(
                  concatenateOperationIdAndPropertyId(operationId, eq.property()), eq.equals());
          case PropertyCondition.IsActive isActive ->
              new PropertyCondition.IsActive(
                  concatenateOperationIdAndPropertyId(operationId, isActive.property()),
                  isActive.isActive());
          case PropertyCondition.OneOf oneOf ->
              new PropertyCondition.OneOf(
                  concatenateOperationIdAndPropertyId(operationId, oneOf.property()),
                  oneOf.oneOf());
          case PropertyCondition.AllMatch allMatch -> {
            List<PropertyCondition> mappedConditions =
                allMatch.allMatch().stream()
                    .map(nested -> mapPropertyCondition(nested, operationId))
                    .toList();
            yield new PropertyCondition.AllMatch(mappedConditions);
          }
        };
  }

  private static String concatenateVariablePathWithName(String variablePath, String name) {
    return variablePath + VARIABLE_PATH_SEPARATOR + name;
  }

  private static PropertyBinding mapBinding(PropertyBinding propertyBinding, Variable variable) {
    String variableName = getVariableName(variable);
    if (!variableName.isBlank()) {
      if (propertyBinding instanceof PropertyBinding.ZeebeInput(String name)) {
        return new PropertyBinding.ZeebeInput(concatenateVariablePathWithName(variableName, name));
      } else if (propertyBinding instanceof PropertyBinding.ZeebeTaskHeader(String key)) {
        return new PropertyBinding.ZeebeTaskHeader(
            concatenateVariablePathWithName(variableName, key));
      } else {
        return propertyBinding;
      }
    } else {
      return propertyBinding;
    }
  }
}
