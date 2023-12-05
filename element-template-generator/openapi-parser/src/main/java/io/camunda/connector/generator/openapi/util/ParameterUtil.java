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
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.generator.dsl.http.HttpOperationProperty;
import io.camunda.connector.generator.dsl.http.HttpOperationProperty.Target;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Utility functions related to converting OpenAPI parameters to {@link HttpOperationProperty}s. */
public class ParameterUtil {

  private static final Map<String, Target> targetMapping =
      Map.of(
          "path", HttpOperationProperty.Target.PATH,
          "query", HttpOperationProperty.Target.QUERY,
          "header", HttpOperationProperty.Target.HEADER);

  public static HttpOperationProperty transformToProperty(
      Parameter parameter, Components components) {
    if (parameter.getSchema() != null) {
      return fromSchema(parameter, components);
    } else if (parameter.getContent() != null) {
      throw new IllegalArgumentException("Complex parameters with content are not supported yet");
    } else {
      throw new IllegalArgumentException("Parameter must have either a schema or a content");
    }
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

    if (schema.getEnum() != null) {
      return HttpOperationProperty.createEnumProperty(
          name,
          targetMapping.get(parameter.getIn()),
          parameter.getDescription(),
          parameter.getRequired() != null && parameter.getRequired(),
          (List<String>) schema.getEnum());
    } else if (schema.getType().equals("boolean")) {
      return HttpOperationProperty.createEnumProperty(
          name,
          targetMapping.get(parameter.getIn()),
          parameter.getDescription(),
          parameter.getRequired() != null && parameter.getRequired(),
          Arrays.asList("true", "false"));
    } else if (schema.getType().equals("string")
        || schema.getType().equals("integer")
        || schema.getType().equals("number")) {
      return HttpOperationProperty.createStringProperty(
          name,
          targetMapping.get(parameter.getIn()),
          parameter.getDescription(),
          parameter.getRequired() != null && parameter.getRequired(),
          example);
    } else if (schema.getType().equals("object") || schema.getType().equals("array")) {
      return HttpOperationProperty.createFeelProperty(
          name,
          targetMapping.get(parameter.getIn()),
          parameter.getDescription(),
          parameter.getRequired() != null && parameter.getRequired(),
          example);
    }
    throw new IllegalArgumentException("Unsupported parameter type: " + schema.getType());
  }

  public static String getExampleFromSchema(Schema schema, Components components) {
    schema = getSchemaOrFromComponents(schema, components);
    if (schema == null) {
      return null;
    }
    if (schema.getExample() != null) {
      return schema.getExample().toString();
    }
    if (schema.getExamples() != null && !schema.getExamples().isEmpty()) {
      return schema.getExamples().get(0).toString();
    }
    return generateFakeDataStringFromSchema(schema, components);
  }

  public static Schema<?> getSchemaOrFromComponents(Schema<?> schema, Components components) {
    if (schema.get$ref() != null) {
      return components.getSchemas().get(schema.get$ref().replace("#/components/schemas/", ""));
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
      return ConnectorsObjectMapperSupplier.DEFAULT_MAPPER
          .writerWithDefaultPrettyPrinter()
          .writeValueAsString(data);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private static Object generateFakeDataFromSchema(Schema<?> schema, Components components) {
    switch (schema.getType()) {
      case "string" -> {
        return "string";
      }
      case "object" -> {
        Map<String, Object> nested = new HashMap<>();
        schema.getProperties().entrySet().stream()
            .map(
                entry ->
                    Map.entry(
                        entry.getKey(), getSchemaOrFromComponents(entry.getValue(), components)))
            .forEach(
                entry ->
                    nested.put(
                        entry.getKey(), generateFakeDataFromSchema(entry.getValue(), components)));
        return nested;
      }
      case "array" -> {
        var items = schema.getItems();
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
