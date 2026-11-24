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

import com.fasterxml.jackson.core.io.JsonStringEncoder;
import io.camunda.connector.feel.FeelEngineWrapperException;
import io.camunda.connector.feel.LocalFeelExpressionEvaluator;
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
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BodyUtil {

  // ordered by priority if endpoint allows multiple
  private static final List<String> SUPPORTED_BODY_MEDIA_TYPES =
      List.of("application/json", "text/plain", "multipart/form-data");

  public static BodyParseResult parseBody(
      RequestBody requestBody, Components components, OpenApiGenerationSource.Options options) {

    if (requestBody == null) {
      return new Raw("");
    }
    if (requestBody.get$ref() != null) {
      var ref = requestBody.get$ref();
      if (!ref.startsWith("#/")) {
        throw new IllegalArgumentException(
            "External $ref '"
                + ref
                + "' cannot be resolved: the spec contains a reference to an external file or URL "
                + "that was not inlined during parsing. Either remove '--no-resolve-refs' so the "
                + "parser can follow the reference, or replace the external $ref with an inline "
                + "schema definition.");
      }
      requestBody =
          components.getRequestBodies().get(ref.replace("#/components/requestBodies/", ""));
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
              + SUPPORTED_BODY_MEDIA_TYPES
              + " but is "
              + requestBody.getContent().keySet());
    }

    if (options.rawBody()
        || isComplexSchema(schema, components)
        || propertiesContainInvalidFeelNaming(schema)) {
      return new Raw(buildRawBodyExample(schema, components));
    }

    try {
      return buildDetailedBody(schema, components);
    } catch (Exception e) {
      return new Raw(buildRawBodyExample(schema, components));
    }
  }

  private static boolean propertiesContainInvalidFeelNaming(Schema<?> schema) {
    if (schema.getProperties() == null) {
      return false;
    }
    var feelEngine = new LocalFeelExpressionEvaluator();
    return schema.getProperties().keySet().stream()
        .anyMatch(property -> {
          try {
            Map<String, String> mockPropertyContext = Map.of(property, "mock");
            String result = feelEngine.evaluate(property, mockPropertyContext);
            return result == null;
          } catch (FeelEngineWrapperException e) {
            return true;
          }
        });
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
        if (isContainerType(ParameterUtil.typeOf(propertySchema))) {
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
    var type = ParameterUtil.typeOf(schema);

    if (schema.getEnum() != null) {
      return HttpOperationProperty.createEnumProperty(
          name, Target.BODY, schema.getDescription(), true, schema.getEnum());
    } else if (type == null) {
      // no type, or several non-null types (OpenAPI 3.1); the caller falls back to a raw body
      throw new IllegalArgumentException("Body property '" + name + "' has no single type");
    } else if (type.equals("boolean")) {
      return HttpOperationProperty.createEnumProperty(
          name, Target.BODY, schema.getDescription(), true, Arrays.asList("true", "false"));
    } else if (type.equals("string") || type.equals("integer") || type.equals("number")) {
      return HttpOperationProperty.createStringProperty(
          name,
          Target.BODY,
          schema.getDescription(),
          true,
          ParameterUtil.getExampleFromSchema(schema, components));
    }
    throw new IllegalArgumentException("Unsupported parameter type: " + type);
  }

  private static String buildRawBodyExample(Schema<?> schema, Components components) {
    var schemaExample = ParameterUtil.getExampleFromSchema(schema, components);
    if (schemaExample == null) {
      return "";
    }
    // The raw body is used as a FEEL expression ("=" + body). A string must keep its quotes,
    // otherwise FEEL reads it as a variable name.
    var resolved = ParameterUtil.getSchemaOrFromComponents(schema, components);
    if ("string".equals(ParameterUtil.typeOf(resolved))) {
      return "\"" + new String(JsonStringEncoder.getInstance().quoteAsString(schemaExample)) + "\"";
    }
    return schemaExample;
  }

  // complex objects are difficult to visually break down into fields
  // this includes objects with nested objects, arrays, or a combination of both
  private static boolean isComplexSchema(Schema<?> schema, Components components) {
    return isComplexSchema(schema, components, Collections.newSetFromMap(new IdentityHashMap<>()));
  }

  private static boolean isComplexSchema(
      Schema<?> schema, Components components, Set<Schema<?>> visitedSchemas) {
    if (hasUnresolvableInternalRef(schema, components)) {
      return true;
    }
    schema = ParameterUtil.getSchemaOrFromComponents(schema, components);
    if (schema != null && !visitedSchemas.add(schema)) {
      // a schema that contains itself, such as an array of itself, stays a raw body
      return true;
    }
    var type = ParameterUtil.typeOf(schema);
    if (type == null) {
      return false;
    }
    if (type.equals("array")) {
      // an array without 'items' (or with only 3.1 'prefixItems') stays a raw body
      return schema.getItems() == null
          || isComplexSchema(schema.getItems(), components, visitedSchemas);
    }
    if (type.equals("object") && schema.getProperties() != null) {
      return schema.getProperties().values().stream()
          .anyMatch(s ->
              hasUnresolvableInternalRef(s, components)
                  || isContainerType(
                      ParameterUtil.typeOf(
                          ParameterUtil.getSchemaOrFromComponents(s, components))));
    }
    return false;
  }

  private static boolean isContainerType(String type) {
    return "object".equals(type) || "array".equals(type);
  }

  // Only '#/components/schemas/...' references can be resolved. Other references inside the
  // document, such as '#/paths/...', are kept in a raw body instead of being broken into fields.
  private static boolean hasUnresolvableInternalRef(Schema<?> schema, Components components) {
    if (schema == null || schema.get$ref() == null || !schema.get$ref().startsWith("#/")) {
      return false;
    }
    return !schema.get$ref().startsWith("#/components/schemas/")
        || components == null
        || components.getSchemas() == null;
  }

  public sealed interface BodyParseResult permits BodyParseResult.Detailed, BodyParseResult.Raw {

    record Detailed(HttpFeelBuilder feelBuilder, List<HttpOperationProperty> properties)
        implements BodyParseResult {}

    record Raw(String rawBody) implements BodyParseResult {}
  }
}
