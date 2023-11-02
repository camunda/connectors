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
package io.camunda.connector.generator.openapi;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Utility functions related to converting OpenAPI parameters to {@link HttpOperationProperty}s. */
public class ParameterUtil {

  private static final Logger LOG = LoggerFactory.getLogger(ParameterUtil.class);

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

    String example;
    if (parameter.getExample() != null) {
      example = parameter.getExample().toString();
    } else {
      example = getExampleFromSchema(parameter.getSchema(), components);
    }
    var schema = getSchemaOrFromComponents(parameter.getSchema(), components);

    if (schema.getEnum() != null) {
      return HttpOperationProperty.createEnumProperty(
          parameter.getName(),
          targetMapping.get(parameter.getIn()),
          parameter.getDescription(),
          parameter.getRequired(),
          (List<String>) schema.getEnum());
    } else if (schema.getType().equals("boolean")) {
      return HttpOperationProperty.createEnumProperty(
          parameter.getName(),
          targetMapping.get(parameter.getIn()),
          parameter.getDescription(),
          parameter.getRequired(),
          Arrays.asList("true", "false"));
    } else if (schema.getType().equals("string")
        || schema.getType().equals("integer")
        || schema.getType().equals("number")) {
      return HttpOperationProperty.createStringProperty(
          parameter.getName(),
          targetMapping.get(parameter.getIn()),
          parameter.getDescription(),
          parameter.getRequired(),
          example);
    } else if (schema.getType().equals("object") || schema.getType().equals("array")) {
      return HttpOperationProperty.createFeelProperty(
          parameter.getName(),
          targetMapping.get(parameter.getIn()),
          parameter.getDescription(),
          parameter.getRequired(),
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
    return generateFakeDataStringFromSchema(schema);
  }

  public static Schema<?> getSchemaOrFromComponents(Schema<?> schema, Components components) {
    if (schema.get$ref() != null) {
      return components.getSchemas().get(schema.get$ref().replace("#/components/schemas/", ""));
    }
    return schema;
  }

  private static String generateFakeDataStringFromSchema(Schema<?> schema) {
    Object data;
    try {
      data = generateFakeDataFromSchema(schema);
    } catch (Exception e) {
      return null;
    }
    try {
      return ConnectorsObjectMapperSupplier.DEFAULT_MAPPER
          .writerWithDefaultPrettyPrinter()
          .writeValueAsString(data);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private static Object generateFakeDataFromSchema(Schema<?> schema) {
    switch (schema.getType()) {
      case "string" -> {
        return "string";
      }
      case "object" -> {
        Map<String, Object> nested = new HashMap<>();
        schema
            .getProperties()
            .forEach((key, propSchema) -> nested.put(key, generateFakeDataFromSchema(propSchema)));
        return nested;
      }
      case "array" -> {
        var items = schema.getItems();
        return new Object[] {generateFakeDataFromSchema(items)};
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
