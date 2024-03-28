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

import io.camunda.connector.generator.dsl.http.HttpFeelBuilder;
import io.camunda.connector.generator.dsl.http.HttpOperationProperty;
import io.camunda.connector.generator.dsl.http.HttpOperationProperty.Target;
import io.camunda.connector.generator.openapi.OpenApiGenerationSource;
import io.camunda.connector.generator.openapi.util.BodyUtil.BodyParseResult.Detailed;
import io.camunda.connector.generator.openapi.util.BodyUtil.BodyParseResult.Raw;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class BodyUtil {

  // ordered by priority if endpoint allows multiple
  private static final List<String> SUPPORTED_BODY_MEDIA_TYPES =
      List.of("application/json", "text/plain");

  public sealed interface BodyParseResult permits BodyParseResult.Detailed, BodyParseResult.Raw {

    record Detailed(HttpFeelBuilder feelBuilder, List<HttpOperationProperty> properties)
        implements BodyParseResult {}

    record Raw(String rawBody) implements BodyParseResult {}
  }

  public static BodyParseResult parseBody(
      RequestBody requestBody, Components components, OpenApiGenerationSource.Options options) {

    if (requestBody == null) {
      return new Raw("");
    }
    if (requestBody.get$ref() != null) {
      requestBody =
          components
              .getRequestBodies()
              .get(requestBody.get$ref().replace("#/components/requestBodies/", ""));
    }
    Schema<?> schema = null;
    var content = requestBody.getContent();
    for (String mediaType : SUPPORTED_BODY_MEDIA_TYPES) {
      if (content.containsKey(mediaType)) {
        var mt = content.get(mediaType);
        schema = ParameterUtil.getSchemaOrFromComponents(mt.getSchema(), components);
        break;
      }
    }
    if (schema == null) {
      throw new IllegalArgumentException(
          "Request body must have a schema of one of the following media types: "
              + SUPPORTED_BODY_MEDIA_TYPES);
    }

    if (options.rawBody() || isComplexSchema(schema, components)) {
      return new Raw(buildRawBodyExample(schema, components));
    }

    try {
      return buildDetailedBody(schema, components);
    } catch (Exception e) {
      return new Raw(buildRawBodyExample(schema, components));
    }
  }

  private static Detailed buildDetailedBody(Schema<?> schema, Components components) {

    HttpFeelBuilder feelBuilder = null;

    List<HttpOperationProperty> properties = new ArrayList<>();

    if (schema.getProperties() != null) {
      var contextBuilder = HttpFeelBuilder.context();

      var entries = schema.getProperties().entrySet().stream().toList();

      for (java.util.Map.Entry<String, Schema> entry : entries) {
        var propertySchema = ParameterUtil.getSchemaOrFromComponents(entry.getValue(), components);
        var name = entry.getKey();
        if ("object".equals(propertySchema.getType()) || "array".equals(propertySchema.getType())) {
          throw new IllegalArgumentException(
              "Complex objects are not supported in detailed request bodies");
        }
        var property = fromSchema(name, propertySchema, components);
        properties.add(property);
        contextBuilder.property(name, property.id());
        feelBuilder = contextBuilder;
      }
    } else {
      properties.add(fromSchema("body", schema, components));
      feelBuilder = HttpFeelBuilder.string().property("body");
    }
    return new Detailed(feelBuilder, properties);
  }

  static HttpOperationProperty fromSchema(String name, Schema<?> schema, Components components) {
    schema = ParameterUtil.getSchemaOrFromComponents(schema, components);

    if (schema.getEnum() != null) {
      return HttpOperationProperty.createEnumProperty(
          name, Target.BODY, schema.getDescription(), true, (List<Object>) schema.getEnum());
    } else if (schema.getType().equals("boolean")) {
      return HttpOperationProperty.createEnumProperty(
          name, Target.BODY, schema.getDescription(), true, Arrays.asList("true", "false"));
    } else if (schema.getType().equals("string")
        || schema.getType().equals("integer")
        || schema.getType().equals("number")) {
      return HttpOperationProperty.createStringProperty(
          name,
          Target.BODY,
          schema.getDescription(),
          true,
          ParameterUtil.getExampleFromSchema(schema, components));
    }
    throw new IllegalArgumentException("Unsupported parameter type: " + schema.getType());
  }

  private static String buildRawBodyExample(Schema<?> schema, Components components) {
    var schemaExample = ParameterUtil.getExampleFromSchema(schema, components);
    return Optional.ofNullable(schemaExample).orElse("");
  }

  // complex objects are difficult to visually break down into fields
  // this includes objects with nested objects, arrays, or a combination of both
  private static boolean isComplexSchema(Schema<?> schema, Components components) {
    schema = ParameterUtil.getSchemaOrFromComponents(schema, components);
    if (schema == null) {
      return false;
    }
    if (schema.getType().equals("array")) {
      return isComplexSchema(schema.getItems(), components);
    }
    if (schema.getType().equals("object")) {
      return schema.getProperties().values().stream()
          .map(s -> ParameterUtil.getSchemaOrFromComponents(s, components))
          .anyMatch(s -> "object".equals(s.getType()) || "array".equals(s.getType()));
    }
    return false;
  }
}
