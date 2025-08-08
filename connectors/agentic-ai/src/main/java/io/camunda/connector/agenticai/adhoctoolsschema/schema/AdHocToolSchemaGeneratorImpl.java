/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.adhoctoolsschema.schema;

import static io.camunda.connector.agenticai.JsonSchemaConstants.PROPERTY_DESCRIPTION;
import static io.camunda.connector.agenticai.JsonSchemaConstants.PROPERTY_PROPERTIES;
import static io.camunda.connector.agenticai.JsonSchemaConstants.PROPERTY_REQUIRED;
import static io.camunda.connector.agenticai.JsonSchemaConstants.PROPERTY_TYPE;
import static io.camunda.connector.agenticai.JsonSchemaConstants.TYPE_OBJECT;
import static io.camunda.connector.agenticai.JsonSchemaConstants.TYPE_STRING;

import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolElement;
import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolElementParameter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

public class AdHocToolSchemaGeneratorImpl implements AdHocToolSchemaGenerator {

  public static final String DEFAULT_EXPECTED_PARAMETER_NAMESPACE = "toolCall.";
  public static final List<String> DEFAULT_RESTRICTED_PARAM_NAMES = List.of("_meta");

  private final String expectedParameterNamespace;
  private final List<String> restrictedParamNames;

  public AdHocToolSchemaGeneratorImpl() {
    this(DEFAULT_EXPECTED_PARAMETER_NAMESPACE, DEFAULT_RESTRICTED_PARAM_NAMES);
  }

  public AdHocToolSchemaGeneratorImpl(
      String expectedParameterNamespace, List<String> restrictedParamNames) {
    this.expectedParameterNamespace = expectedParameterNamespace;
    this.restrictedParamNames = restrictedParamNames;
  }

  @Override
  public Map<String, Object> generateToolSchema(AdHocToolElement element) {
    Map<String, Object> properties = new LinkedHashMap<>();
    List<String> required = new ArrayList<>();

    element
        .parameters()
        .forEach(
            parameter -> {
              final var parameterName = parameterName(element, parameter);

              if (restrictedParamNames.contains(parameterName)) {
                throw new AdHocToolSchemaGenerationException(
                    "Failed to generate ad-hoc tool schema for element '%s'. Parameter name '%s' is restricted and cannot be used."
                        .formatted(element.elementId(), parameter.name()));
              }

              if (properties.containsKey(parameterName)) {
                throw new AdHocToolSchemaGenerationException(
                    "Failed to generate ad-hoc tool schema for element '%s'. Duplicate parameter name '%s'."
                        .formatted(element.elementId(), parameter.name()));
              }

              final var propertySchema =
                  Optional.ofNullable(parameter.schema())
                      .map(LinkedHashMap::new)
                      .orElseGet(LinkedHashMap::new);

              // apply type from parameter if it is set
              if (StringUtils.isNotBlank(parameter.type())) {
                propertySchema.put(PROPERTY_TYPE, parameter.type());
              }

              // default to string if no type is set (not on parameter, not in schema directly)
              if (!propertySchema.containsKey(PROPERTY_TYPE)) {
                propertySchema.put(PROPERTY_TYPE, TYPE_STRING);
              }

              if (StringUtils.isNotBlank(parameter.description())) {
                propertySchema.put(PROPERTY_DESCRIPTION, parameter.description());
              }

              properties.put(parameterName, propertySchema);

              if (isParameterRequired(element, parameter)) {
                required.add(parameterName);
              }
            });

    Map<String, Object> inputSchema = new LinkedHashMap<>();
    inputSchema.put(PROPERTY_TYPE, TYPE_OBJECT);
    inputSchema.put(PROPERTY_PROPERTIES, properties);
    inputSchema.put(PROPERTY_REQUIRED, required);

    return inputSchema;
  }

  private String parameterName(AdHocToolElement element, AdHocToolElementParameter parameter) {
    final var parameterName = parameter.name();
    if (StringUtils.isBlank(parameterName)
        || !parameterName.startsWith(expectedParameterNamespace)) {
      throw new AdHocToolSchemaGenerationException(
          "Failed to generate ad-hoc tool schema for element '%s'. Parameter name '%s' is not part of expected namespace '%s'."
              .formatted(element.elementId(), parameter.name(), expectedParameterNamespace));
    }

    final var parameterNameWithoutNamespace =
        parameterName.substring(expectedParameterNamespace.length());

    if (StringUtils.isBlank(parameterNameWithoutNamespace)) {
      throw new AdHocToolSchemaGenerationException(
          "Failed to generate ad-hoc tool schema for element '%s'. Parameter name '%s' is empty after removing the expected namespace '%s'."
              .formatted(element.elementId(), parameter.name(), expectedParameterNamespace));
    }

    if (parameterNameWithoutNamespace.contains(".")) {
      throw new AdHocToolSchemaGenerationException(
          "Failed to generate ad-hoc tool schema for element '%s'. Parameter name '%s' with removed namespace '%s' is not a leaf reference (must not contain dots)."
              .formatted(
                  element.elementId(), parameterNameWithoutNamespace, expectedParameterNamespace));
    }

    return parameterNameWithoutNamespace;
  }

  private boolean isParameterRequired(
      AdHocToolElement element, AdHocToolElementParameter parameter) {
    if (parameter.options() == null) {
      return true;
    }

    Object requiredValue = parameter.options().get("required");
    if (requiredValue == null) {
      return true;
    }

    if (requiredValue instanceof Boolean) {
      return (Boolean) requiredValue;
    }

    throw new AdHocToolSchemaGenerationException(
        "Failed to generate ad-hoc tool schema for element '%s'. Parameter '%s' 'required' option must be a boolean value, but was: %s"
            .formatted(
                element.elementId(), parameter.name(), requiredValue.getClass().getSimpleName()));
  }
}
