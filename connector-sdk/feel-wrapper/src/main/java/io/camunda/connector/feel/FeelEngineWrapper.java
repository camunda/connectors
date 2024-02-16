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
package io.camunda.connector.feel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.scala.DefaultScalaModule$;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.camunda.feel.FeelEngine;
import org.camunda.feel.impl.JavaValueMapper;
import org.camunda.feel.impl.SpiServiceLoader;
import scala.collection.Iterable;
import scala.jdk.javaapi.CollectionConverters;

/** Wrapper for the FEEL engine, handling type conversions and expression evaluations. */
public class FeelEngineWrapper {

  static final String ERROR_CONTEXT_IS_NULL = "Context is null";

  static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE = new TypeReference<>() {};

  private final FeelEngine feelEngine;
  private final ObjectMapper objectMapper;

  /**
   * Default constructor, creating an {@link ObjectMapper} and a {@link FeelEngine} with default
   * configuration.
   */
  public FeelEngineWrapper() {
    this.feelEngine =
        new FeelEngine.Builder()
            .customValueMapper(new JavaValueMapper())
            .functionProvider(SpiServiceLoader.loadFunctionProvider())
            .build();
    this.objectMapper =
        new ObjectMapper()
            .registerModule(DefaultScalaModule$.MODULE$)
            .registerModule(new JavaTimeModule())
            // deserialize unknown types as empty objects
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
  }

  /**
   * Injection constructor allowing to pass in the {@link FeelEngine} and {@link ObjectMapper} to
   * use.
   *
   * @param feelEngine the FEEL engine to use
   * @param objectMapper the object mapper to use
   */
  public FeelEngineWrapper(final FeelEngine feelEngine, final ObjectMapper objectMapper) {
    this.feelEngine = feelEngine;
    this.objectMapper = objectMapper;
  }

  private static String trimExpression(final String expression) {
    var feelExpression = expression.trim();
    if (feelExpression.startsWith("=")) {
      feelExpression = feelExpression.substring(1);
    }
    return feelExpression.trim();
  }

  private static scala.collection.immutable.Map<String, Object> toScalaMap(
      final Map<String, Object> responseMap) {
    final HashMap<String, Object> context = new HashMap<>(responseMap);
    return scala.collection.immutable.Map.from(CollectionConverters.asScala(context));
  }

  private Map<String, Object> ensureVariablesMap(final Object[] variables) {
    try {
      Objects.requireNonNull(variables, ERROR_CONTEXT_IS_NULL);
      Map<String, Object> variablesMap = new HashMap<>();
      for (Object o : variables) {
        Objects.requireNonNull(o, ERROR_CONTEXT_IS_NULL);
        variablesMap.putAll(objectMapper.convertValue(o, MAP_TYPE_REFERENCE));
      }
      return variablesMap;
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException(
          String.format("Unable to parse '%s' as context", variables), ex);
    }
  }

  @SuppressWarnings("unchecked")
  private <T> T sanitizeScalaOutput(T output) {
    if (output instanceof scala.collection.Map<?, ?> scalaMap) {
      return (T)
          CollectionConverters.asJava(scalaMap).entrySet().stream()
              .collect(
                  HashMap::new,
                  (m, v) -> m.put(v.getKey(), sanitizeScalaOutput(v.getValue())),
                  HashMap::putAll);
    } else if (output instanceof Iterable<?> scalaIterable) {
      return (T)
          StreamSupport.stream(CollectionConverters.asJava(scalaIterable).spliterator(), false)
              .map(this::sanitizeScalaOutput)
              .collect(Collectors.toList());
    } else {
      return output;
    }
  }

  /**
   * Evaluates an expression with the FEEL engine with the given variables.
   *
   * @param expression the expression to evaluate
   * @param variables the variables to use in evaluation, the first context provided will be
   *     available wrapped as "response"
   * @param <T> the type to cast the evaluation result to
   * @return the evaluation result
   * @throws FeelEngineWrapperException when there is an exception message as a result of the
   *     evaluation or the result cannot be cast to the given type
   */
  public <T> T evaluate(final String expression, final Object... variables) {
    try {
      return (T) evaluateInternal(expression, variables);
    } catch (Exception e) {
      throw new FeelEngineWrapperException(e.getMessage(), expression, variables, e);
    }
  }

  /**
   * Evaluates an expression with the FEEL engine with the given variables.
   *
   * @param expression the expression to evaluate
   * @param clazz the class the result should be converted to
   * @param variables the variables to use in evaluation, the first context provided will be
   *     available wrapped as "response"
   * @param <T> the type to cast the evaluation result to
   * @return the evaluation result
   * @throws FeelEngineWrapperException when there is an exception message as a result of the
   *     evaluation or the result cannot be cast to the given type
   */
  public <T> T evaluate(final String expression, final Class<T> clazz, final Object... variables) {
    Function<JsonNode, T> converter =
        (JsonNode jsonNode) -> {
          try {
            return objectMapper.treeToValue(jsonNode, clazz);
          } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
          }
        };
    var type = objectMapper.getTypeFactory().constructType(clazz);
    return evaluateAndConvert(expression, type, converter, variables);
  }

  /**
   * Evaluates an expression with the FEEL engine with the given variables.
   *
   * @param expression the expression to evaluate
   * @param clazz the class the result should be converted to
   * @param variables the variables to use in evaluation, the first context provided will be
   *     available wrapped as "response"
   * @param <T> the type to cast the evaluation result to
   * @return the evaluation result
   * @throws FeelEngineWrapperException when there is an exception message as a result of the
   *     evaluation or the result cannot be cast to the given type
   */
  public <T> T evaluate(final String expression, final JavaType clazz, final Object... variables) {
    Function<JsonNode, T> converter =
        (JsonNode jsonNode) -> {
          try {
            return objectMapper.treeToValue(jsonNode, clazz);
          } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
          }
        };
    return evaluateAndConvert(expression, clazz, converter, variables);
  }

  /**
   * For use in custom deserializers where there is a need to use the deserialization context. This
   * allows to preserve custom settings and registered jackson modules when evaluating expressions.
   *
   * <p>Evaluates an expression with the FEEL engine with the given variables.
   *
   * @param ctx the deserialization context to apply
   * @param expression the expression to evaluate
   * @param clazz the class the result should be converted to
   * @param variables the variables to use in evaluation, the first context provided will be
   *     available wrapped as "response"
   * @param <T> the type to cast the evaluation result to
   * @return the evaluation result
   * @throws FeelEngineWrapperException when there is an exception message as a result of the
   *     evaluation or the result cannot be cast to the given type
   */
  public <T> T evaluate(
      final DeserializationContext ctx,
      final String expression,
      final JavaType clazz,
      final Object... variables) {
    Function<JsonNode, T> converter =
        (JsonNode jsonNode) -> {
          try {
            if (jsonNode == null || jsonNode.isNull()) {
              return null;
            }
            return ctx.readTreeAsValue(jsonNode, clazz);
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        };
    return evaluateAndConvert(expression, clazz, converter, variables);
  }

  @SuppressWarnings("unchecked")
  private <T> T evaluateAndConvert(
      final String expression,
      final JavaType clazz,
      Function<JsonNode, T> converter,
      final Object... variables) {

    Object result = evaluate(expression, variables);
    JsonNode jsonNode = objectMapper.convertValue(result, JsonNode.class);

    try {
      if (clazz.getRawClass().equals(String.class) && jsonNode.isObject()) {
        return (T) objectMapper.writeValueAsString(jsonNode);
      } else {
        return sanitizeScalaOutput(converter.apply(jsonNode));
      }
    } catch (Exception e) {
      throw new FeelEngineWrapperException(
          "Failed to convert FEEL evaluation result to the target type", expression, variables, e);
    }
  }

  /**
   * Evaluates an expression to a JSON String.
   *
   * @param expression the expression to evaluate
   * @param variables the variables to use in evaluation, the first context provided will be
   *     available wrapped as "response"
   * @return the JSON String representing the evaluation result
   * @throws FeelEngineWrapperException when there is an exception message as a result of the
   *     evaluation or the result cannot be parsed as JSON
   */
  public String evaluateToJson(final String expression, final Object... variables) {
    try {

      return resultToJson(evaluateInternal(expression, variables));
    } catch (Exception e) {
      throw new FeelEngineWrapperException(e.getMessage(), expression, variables, e);
    }
  }

  private Object evaluateInternal(final String expression, final Object[] variables) {
    var variablesAsMap = ensureVariablesMap(variables);
    var variablesAsMapAsScalaMap = toScalaMap(variablesAsMap);

    var result = feelEngine.evalExpression(trimExpression(expression), variablesAsMapAsScalaMap);
    if (result.isRight()) {
      return result.right().get();
    } else {
      throw new RuntimeException(result.left().get().message());
    }
  }

  private String resultToJson(final Object result) {
    try {
      return objectMapper.writeValueAsString(result);
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(
          "The output expression result cannot be parsed as JSON: " + result, e);
    }
  }
}
