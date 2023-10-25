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

import io.camunda.connector.generator.dsl.http.HttpOperationProperty;
import io.camunda.connector.generator.dsl.http.HttpOperationProperty.Target;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.util.Arrays;
import java.util.Map;

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
    var schema = parameter.getSchema();
    if (schema.get$ref() != null) {
      schema = components.getSchemas().get(schema.get$ref().replace("#/components/schemas/", ""));
    }

    String example = "";
    if (parameter.getExample() != null) {
      example = parameter.getExample().toString();
    } else if (schema.getExample() != null) {
      example = schema.getExample().toString();
    }

    if (schema.getEnum() != null) {
      return HttpOperationProperty.createEnumProperty(
          parameter.getName(),
          targetMapping.get(parameter.getIn()),
          parameter.getDescription(),
          parameter.getRequired(),
          schema.getEnum());
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
}
