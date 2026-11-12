/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.tools.schema;

import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.PROPERTY_DESCRIPTION;
import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.PROPERTY_PROPERTIES;
import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.PROPERTY_REQUIRED;
import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.PROPERTY_TYPE;
import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.TYPE_OBJECT;
import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.TYPE_STRING;

import io.camunda.connector.agenticai.aiagent.model.tool.AdHocToolElement;
import io.camunda.connector.agenticai.aiagent.model.tool.AdHocToolElementParameter;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

public class AdHocToolSchemaGeneratorImpl implements AdHocToolSchemaGenerator {

  public static final String DEFAULT_EXPECTED_PARAMETER_NAMESPACE = "toolCall.";
  public static final List<String> DEFAULT_RESTRICTED_PARAM_NAMES = List.of("_meta");

  /** Type that an authored schema uses as a shorthand for a document parameter. */
  private static final String TYPE_DOCUMENT = "document";

  // keywords whose values are data, not schemas
  private static final Set<String> DATA_KEYWORDS = Set.of("const", "enum", "default", "examples");

  // keywords whose values map names to schemas: a name is no keyword
  private static final Set<String> NAMED_SCHEMA_KEYWORDS =
      Set.of("properties", "patternProperties", "dependentSchemas", "$defs", "definitions");

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
        .forEach(parameter -> {
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

          final var authoredSchema =
              Optional.ofNullable(parameter.schema())
                  .map(LinkedHashMap::new)
                  .orElseGet(LinkedHashMap::new);

          // apply type from parameter if it is set
          if (StringUtils.isNotBlank(parameter.type())) {
            authoredSchema.put(PROPERTY_TYPE, parameter.type());
          }

          // the shorthand type "document" becomes the object schema of a document handle
          final var propertySchema =
              new LinkedHashMap<>(normalize(element, parameter, authoredSchema));

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

  /**
   * The authored schema with the shorthand type {@value #TYPE_DOCUMENT} turned into the schema of a
   * {@link DocumentParameter document parameter} (see {@link DocumentParameter#documentSchema}).
   *
   * <p>The shorthand is supported where a document parameter can sit: as the type of the parameter,
   * of the {@code items} of an array or of an object property, at any depth (see {@link
   * DocumentParameter#mapChildren}). Anywhere else, such as in an {@code anyOf} alternative or in a
   * {@code type} array, the schema is rejected: the model provider does not know the type.
   */
  private Map<String, Object> normalize(
      AdHocToolElement element,
      AdHocToolElementParameter parameter,
      Map<String, Object> authoredSchema) {
    final var normalized = normalizeNode(authoredSchema);
    if (containsShorthand(normalized)) {
      throw new AdHocToolSchemaGenerationException(
          "Failed to generate ad-hoc tool schema for element '%s'. Parameter '%s': The type 'document' can only be the type of a parameter, of the items of an array or of an object property. Use a separate parameter for a document."
              .formatted(element.elementId(), parameter.name()));
    }
    return normalized;
  }

  private Map<String, Object> normalizeNode(Map<String, Object> node) {
    if (TYPE_DOCUMENT.equals(node.get(PROPERTY_TYPE))) {
      return documentNode(node);
    }
    return DocumentParameter.mapChildren(node, (step, child) -> normalizeNode(child));
  }

  /**
   * The document schema with the description of the node; the other authored entries follow it
   * unless the document schema defines them.
   */
  private Map<String, Object> documentNode(Map<String, Object> node) {
    final var result =
        new LinkedHashMap<>(
            DocumentParameter.documentSchema(
                node.get(PROPERTY_DESCRIPTION) instanceof String description ? description : null));
    node.forEach(result::putIfAbsent);
    return result;
  }

  private boolean containsShorthand(@Nullable Object node) {
    if (node instanceof Map<?, ?> map) {
      final var type = map.get(PROPERTY_TYPE);
      if (TYPE_DOCUMENT.equals(type)
          || (type instanceof List<?> types && types.contains(TYPE_DOCUMENT))) {
        return true;
      }
      return map.entrySet().stream()
          .anyMatch(entry -> {
            final var keyword = String.valueOf(entry.getKey());
            if (DATA_KEYWORDS.contains(keyword)) {
              return false;
            }
            if (NAMED_SCHEMA_KEYWORDS.contains(keyword)
                && entry.getValue() instanceof Map<?, ?> named) {
              return named.values().stream().anyMatch(this::containsShorthand);
            }
            return containsShorthand(entry.getValue());
          });
    }
    if (node instanceof List<?> list) {
      return list.stream().anyMatch(this::containsShorthand);
    }
    return false;
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

    if (requiredValue instanceof Boolean required) {
      return required;
    }

    throw new AdHocToolSchemaGenerationException(
        "Failed to generate ad-hoc tool schema for element '%s'. Parameter '%s' 'required' option must be a boolean value, but was: %s"
            .formatted(
                element.elementId(), parameter.name(), requiredValue.getClass().getSimpleName()));
  }
}
