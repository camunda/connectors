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
package io.camunda.connector.generator.openapi.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.generator.dsl.http.HttpOperationProperty;
import io.camunda.connector.generator.dsl.http.HttpOperationProperty.Target;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.util.*;

/** Utility functions related to converting OpenAPI parameters to {@link HttpOperationProperty}s. */
public class ParameterUtil {

  private static final Map<String, Target> targetMapping =
      Map.of(
          "path", HttpOperationProperty.Target.PATH,
          "query", HttpOperationProperty.Target.QUERY,
          "header", HttpOperationProperty.Target.HEADER,
          "headers", HttpOperationProperty.Target.HEADER);

  public static HttpOperationProperty transformToProperty(
      Parameter parameter, Components components) {
    if (parameter.get$ref() != null) {
      parameter = resolveParameterRef(parameter.get$ref(), components);
    }
    if (parameter.getSchema() != null) {
      return fromSchema(parameter, components);
    } else if (parameter.getContent() != null) {
      throw new IllegalArgumentException("Complex parameters with content are not supported yet");
    } else {
      throw new IllegalArgumentException("Parameter must have either a schema or a content");
    }
  }

  private static Parameter resolveParameterRef(String ref, Components components) {
    if (!ref.startsWith("#/")) {
      throw new IllegalArgumentException(
          "External $ref '"
              + ref
              + "' cannot be resolved: the spec contains a reference to an external file or URL "
              + "that was not inlined during parsing. Either remove '--no-resolve-refs' so the "
              + "parser can follow the reference, or replace the external $ref with an inline "
              + "parameter definition.");
    }
    var prefix = "#/components/parameters/";
    if (!ref.startsWith(prefix)) {
      throw new IllegalArgumentException(
          "Parameter $ref '"
              + ref
              + "' cannot be resolved: only '#/components/parameters/...' references are supported.");
    }
    var paramName = ref.substring(prefix.length());
    if (components == null || components.getParameters() == null) {
      throw new IllegalArgumentException(
          "Parameter $ref '"
              + ref
              + "' cannot be resolved: the spec has no components.parameters section.");
    }
    var resolved = components.getParameters().get(paramName);
    if (resolved == null) {
      throw new IllegalArgumentException(
          "Parameter $ref '"
              + ref
              + "' cannot be resolved: '"
              + paramName
              + "' is not defined in components.parameters.");
    }
    return resolved;
  }

  private static HttpOperationProperty fromSchema(Parameter parameter, Components components) {

    // Path parameter names may contain dashes, which are not allowed by the DSL, as this would be
    // interpreted as a subtraction by the FEEL engine. It is safe to replace path parameter names
    // with underscores (this doesn't apply to other parameter targets!)

    var name = parameter.getName();
    if ("path".equals(parameter.getIn())) {
      name = name.replace("-", "_");
    }

    String example;
    if (parameter.getExample() != null) {
      example = parameter.getExample().toString();
    } else {
      example = getExampleFromSchema(parameter.getSchema(), components);
    }
    var schema = getSchemaOrFromComponents(parameter.getSchema(), components);
    var type = typeOf(schema);

    if (Objects.isNull(type)
        || type.equals("string")
        || type.equals("integer")
        || type.equals("number")) {
      return HttpOperationProperty.createStringProperty(
          name,
          targetMapping.get(parameter.getIn()),
          parameter.getDescription(),
          parameter.getRequired() != null && parameter.getRequired(),
          example);
    } else if (!Objects.isNull(schema.getEnum())) {
      return HttpOperationProperty.createEnumProperty(
          name,
          targetMapping.get(parameter.getIn()),
          parameter.getDescription(),
          parameter.getRequired() != null && parameter.getRequired(),
          schema.getEnum());
    } else if (type.equals("boolean")) {
      return HttpOperationProperty.createEnumProperty(
          name,
          targetMapping.get(parameter.getIn()),
          parameter.getDescription(),
          parameter.getRequired() != null && parameter.getRequired(),
          Arrays.asList("true", "false"));
    } else if (type.equals("object") || type.equals("array")) {
      return HttpOperationProperty.createFeelProperty(
          name,
          targetMapping.get(parameter.getIn()),
          parameter.getDescription(),
          parameter.getRequired() != null && parameter.getRequired(),
          example);
    }
    throw new IllegalArgumentException("Unsupported parameter type: " + type);
  }

  /**
   * Returns the single type of a schema, for both OpenAPI 3.0 and 3.1 documents.
   *
   * <p>For 3.0 documents, the parser sets {@link Schema#getType()}. For 3.1 documents, it sets
   * {@link Schema#getTypes()} instead and {@link Schema#getType()} returns {@code null}. In 3.1, a
   * nullable value is written as a type array such as {@code [string, "null"]}, so the {@code
   * "null"} entry is ignored.
   *
   * @return the type, or {@code null} if the schema declares no type or more than one non-null type
   */
  public static String typeOf(Schema<?> schema) {
    if (schema == null) {
      return null;
    }
    if (schema.getType() != null) {
      return schema.getType();
    }
    if (schema.getTypes() == null) {
      return null;
    }
    var nonNullTypes = schema.getTypes().stream().filter(t -> !"null".equals(t)).toList();
    return nonNullTypes.size() == 1 ? nonNullTypes.getFirst() : null;
  }

  public static String getExampleFromSchema(Schema schema, Components components) {
    schema = getSchemaOrFromComponents(schema, components);
    if (schema == null) {
      return null;
    }
    if (schema.getExample() != null) {
      return exampleToString(schema.getExample());
    }
    if (schema.getExamples() != null) {
      // nullable 3.1 values often list null as an example; prefill the first real value instead
      for (Object example : schema.getExamples()) {
        if (example != null && !(example instanceof JsonNode node && node.isNull())) {
          return exampleToString(example);
        }
      }
    }
    return generateFakeDataStringFromSchema(schema, components);
  }

  /**
   * The parser can return an OpenAPI 3.1 {@code examples} entry as a {@link JsonNode}. The {@code
   * toString()} of a text node keeps its JSON quotes, so plain values are read as text instead.
   */
  private static String exampleToString(Object example) {
    if (example instanceof JsonNode node && node.isValueNode()) {
      return node.asText();
    }
    return example.toString();
  }

  public static Schema<?> getSchemaOrFromComponents(Schema<?> schema, Components components) {
    if (schema.get$ref() != null) {
      var ref = schema.get$ref();
      if (!ref.startsWith("#/")) {
        throw new IllegalArgumentException(
            "External $ref '"
                + ref
                + "' cannot be resolved: the spec contains a reference to an external file or URL "
                + "that was not inlined during parsing. Either remove '--no-resolve-refs' so the "
                + "parser can follow the reference, or replace the external $ref with an inline "
                + "schema definition.");
      }
      return components.getSchemas().get(ref.replace("#/components/schemas/", ""));
    }
    return schema;
  }

  static String generateFakeDataStringFromSchema(Schema<?> schema, Components components) {
    Object data;
    try {
      data = generateFakeDataFromSchema(schema, components);
    } catch (Exception e) {
      return null;
    }
    if (data instanceof String) {
      return (String) data;
    } else if (data instanceof Boolean || data instanceof Number) {
      return data.toString();
    }
    try {
      return ConnectorsObjectMapperSupplier.getCopy()
          .writerWithDefaultPrettyPrinter()
          .writeValueAsString(data);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private static Object generateFakeDataFromSchema(Schema<?> schema, Components components) {
    return generateFakeDataFromSchema(schema, components, new HashSet<>());
  }

  private static Object generateFakeDataFromSchema(
      Schema<?> schema, Components components, HashSet<Schema<?>> visitedSchemas) {
    switch (typeOf(schema)) {
      case "string" -> {
        return "string";
      }
      case "object" -> {
        Map<String, Object> nested = new HashMap<>();

        boolean shouldEarlyExitWhenCircleDetected = visitedSchemas.contains(schema);
        if (shouldEarlyExitWhenCircleDetected) {
          return nested;
        }
        visitedSchemas.add(schema);

        schema.getProperties().entrySet().stream()
            .map(entry ->
                Map.entry(entry.getKey(), getSchemaOrFromComponents(entry.getValue(), components)))
            .forEach(entry ->
                nested.put(
                    entry.getKey(),
                    generateFakeDataFromSchema(
                        entry.getValue(), components, new HashSet<>(visitedSchemas))));
        return nested;
      }
      case "array" -> {
        var items = getSchemaOrFromComponents(schema.getItems(), components);
        if (items.getEnum() != null && !items.getEnum().isEmpty()) {
          return items.getEnum();
        }
        return new Object[] {generateFakeDataFromSchema(items, components)};
      }
      case "integer", "number" -> {
        return 0;
      }
      case "boolean" -> {
        return true;
      }
      default -> {
        return null;
      }
    }
  }
}
