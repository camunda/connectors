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
package io.camunda.connector.generator.dsl.http;

import io.camunda.connector.feel.FeelEngineWrapper;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Fluent API to avoid the complex string transformations when creating HTTP path FEEL expressions.
 */
public class HttpPathFeelBuilder {

  private final StringBuilder sb = new StringBuilder();
  private final Set<String> propertySet = new HashSet<>();
  private static final FeelEngineWrapper feelEngineWrapper = new FeelEngineWrapper();

  public static final String FEEL_OPERATOR_CHARACTERS = "!=<>+-*/[]{}@ ";

  private HttpPathFeelBuilder() {}

  public static HttpPathFeelBuilder create() {
    return new HttpPathFeelBuilder();
  }

  /** Add a constant part to the path */
  public HttpPathFeelBuilder part(String part) {
    if (sb.isEmpty()) {
      sb.append("=");
    } else {
      sb.append("+");
    }
    sb.append("\"");
    sb.append(part);
    sb.append("\"");
    return this;
  }

  /** Add a variable property to the path */
  public HttpPathFeelBuilder property(String property) {
    if (property == null || property.isEmpty()) {
      throw new IllegalArgumentException("Property must not be null or empty");
    }
    for (char c : FEEL_OPERATOR_CHARACTERS.toCharArray()) {
      if (property.contains(String.valueOf(c))) {
        throw new IllegalArgumentException(
            "Property must not contain FEEL operator characters: " + FEEL_OPERATOR_CHARACTERS);
      }
    }
    if (sb.isEmpty()) {
      sb.append("=");
    } else {
      sb.append("+");
    }
    sb.append(property);
    propertySet.add(property);
    return this;
  }

  /** Append a '/' slash symbol to the URL */
  public HttpPathFeelBuilder slash() {
    if (sb.isEmpty()) {
      sb.append("=");
    } else {
      sb.append("+");
    }
    sb.append("\"/\"");
    return this;
  }

  /** Transform the constructed path into a FEEL expression string */
  String build() {
    String result = sb.toString();
    evaluateFeel(result, propertySet);
    return result;
  }

  static void evaluateFeel(String expression, Set<String> propertyList) {
    Map<String, String> mockPropertyContext =
        propertyList.stream().collect(Collectors.toMap(property -> property, property -> "mock"));
    String resultingPath = feelEngineWrapper.evaluate(expression, mockPropertyContext);
    if (!resultingPath.startsWith("/")) {
      throw new IllegalArgumentException("Operation path must start with '/'");
    }
  }
}
