/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.adhoctoolsschema.resolver.schema;

import static io.camunda.connector.agenticai.JsonSchemaConstants.PROPERTY_DESCRIPTION;
import static io.camunda.connector.agenticai.JsonSchemaConstants.PROPERTY_PROPERTIES;
import static io.camunda.connector.agenticai.JsonSchemaConstants.PROPERTY_REQUIRED;
import static io.camunda.connector.agenticai.JsonSchemaConstants.PROPERTY_TYPE;
import static io.camunda.connector.agenticai.JsonSchemaConstants.TYPE_OBJECT;
import static io.camunda.connector.agenticai.JsonSchemaConstants.TYPE_STRING;

import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolElement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

public class AdHocToolSchemaGeneratorImpl implements AdHocToolSchemaGenerator {

  public static final List<String> DEFAULT_RESTRICTED_PARAM_NAMES = List.of("_meta");

  private final List<String> restrictedParamNames;

  public AdHocToolSchemaGeneratorImpl() {
    this(DEFAULT_RESTRICTED_PARAM_NAMES);
  }

  public AdHocToolSchemaGeneratorImpl(List<String> restrictedParamNames) {
    this.restrictedParamNames = restrictedParamNames;
  }

  @Override
  public Map<String, Object> generateToolSchema(AdHocToolElement element) {
    Map<String, Object> properties = new LinkedHashMap<>();
    List<String> required = new ArrayList<>();

    element
        .parameters()
        .forEach(
            inputParam -> {
              if (restrictedParamNames.contains(inputParam.name())) {
                throw new AdHocToolSchemaGenerationException(
                    "Failed to generate ad-hoc tool schema for element '%s'. Input parameter name '%s' is restricted and cannot be used."
                        .formatted(element.elementId(), inputParam.name()));
              }

              if (properties.containsKey(inputParam.name())) {
                throw new AdHocToolSchemaGenerationException(
                    "Failed to generate ad-hoc tool schema for element '%s'. Duplicate input parameter name '%s'."
                        .formatted(element.elementId(), inputParam.name()));
              }

              final var propertySchema =
                  Optional.ofNullable(inputParam.schema())
                      .map(LinkedHashMap::new)
                      .orElseGet(LinkedHashMap::new);

              // apply type from inputParam if it is set
              if (!StringUtils.isBlank(inputParam.type())) {
                propertySchema.put(PROPERTY_TYPE, inputParam.type());
              }

              // default to string if no type is set (not on inputParam, not in schema directly)
              if (!propertySchema.containsKey(PROPERTY_TYPE)) {
                propertySchema.put(PROPERTY_TYPE, TYPE_STRING);
              }

              if (!StringUtils.isBlank(inputParam.description())) {
                propertySchema.put(PROPERTY_DESCRIPTION, inputParam.description());
              }

              properties.put(inputParam.name(), propertySchema);
              required.add(inputParam.name());
            });

    Map<String, Object> inputSchema = new LinkedHashMap<>();
    inputSchema.put(PROPERTY_TYPE, TYPE_OBJECT);
    inputSchema.put(PROPERTY_PROPERTIES, properties);
    inputSchema.put(PROPERTY_REQUIRED, required);

    return inputSchema;
  }
}
