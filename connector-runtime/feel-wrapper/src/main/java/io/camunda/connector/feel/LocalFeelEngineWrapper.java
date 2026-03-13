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

import static io.camunda.connector.feel.JacksonSupport.MAP_TYPE_REFERENCE;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.scala.DefaultScalaModule$;
import io.camunda.connector.document.jackson.JacksonModuleDocumentSerializer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.camunda.feel.FeelEngine;
import org.camunda.feel.impl.JavaValueMapper;
import org.camunda.feel.impl.SpiServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.collection.Iterable;
import scala.jdk.javaapi.CollectionConverters;

/**
 * Local implementation of {@link FeelExpressionEvaluator} that uses the embedded FEEL engine for
 * expression evaluation. This is the default implementation for scenarios where cluster-based
 * evaluation is not needed.
 */
public class LocalFeelEngineWrapper implements FeelExpressionEvaluator {

  private static final Logger LOG = LoggerFactory.getLogger(LocalFeelEngineWrapper.class);
  private final FeelEngine feelEngine;
  private final ObjectMapper objectMapper;

  public LocalFeelEngineWrapper() {
    this.objectMapper =
        new ObjectMapper()
            .registerModule(DefaultScalaModule$.MODULE$)
            .registerModule(new JavaTimeModule())
            .registerModule(new Jdk8Module())
            .registerModule(new JacksonModuleDocumentSerializer())
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS);

    this.feelEngine =
        new FeelEngine.Builder()
            .customValueMapper(new JavaValueMapper())
            .functionProvider(SpiServiceLoader.loadFunctionProvider())
            .customValueMapper(new CustomValueMapper(objectMapper))
            .build();
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

  private Optional<Map<String, Object>> tryConvertToMap(Object o) {
    try {
      return Optional.of(sanitizeScalaOutput(objectMapper.convertValue(o, MAP_TYPE_REFERENCE)));
    } catch (IllegalArgumentException ex) {
      LOG.warn(ex.getMessage(), ex);
      return Optional.empty();
    }
  }

  @SuppressWarnings("unchecked")
  public <T> T sanitizeScalaOutput(T output) {
    return switch (output) {
      case scala.collection.Map<?, ?> scalaMap ->
          (T)
              CollectionConverters.asJava(scalaMap).entrySet().stream()
                  .collect(
                      HashMap::new,
                      (m, v) -> m.put(v.getKey(), sanitizeScalaOutput(v.getValue())),
                      HashMap::putAll);
      case Map<?, ?> javaMap ->
          (T)
              javaMap.entrySet().stream()
                  .collect(
                      HashMap::new,
                      (m, e) -> m.put(e.getKey(), sanitizeScalaOutput(e.getValue())),
                      HashMap::putAll);
      case Iterable<?> scalaIterable ->
          (T)
              StreamSupport.stream(CollectionConverters.asJava(scalaIterable).spliterator(), false)
                  .map(this::sanitizeScalaOutput)
                  .collect(Collectors.toList());
      case null, default -> output;
    };
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T evaluate(final String expression, final Object... variables) {
    try {
      return (T) evaluateInternal(expression, variables);
    } catch (Exception e) {
      throw new FeelEngineWrapperException(e.getMessage(), expression, variables, e);
    }
  }

  @Override
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

  @Override
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

  @Override
  public String evaluateToJson(final String expression, final Object... variables) {
    try {
      var result = evaluateInternal(expression, variables);
      if (result != null) {
        return resultToJson(result);
      } else return null;
    } catch (Exception e) {
      throw new FeelEngineWrapperException(e.getMessage(), expression, variables, e);
    }
  }

  private Object evaluateInternal(final String expression, final Object[] variables) {
    var variablesAsMap = FeelEngineWrapperUtil.mergeMapVariables(objectMapper, variables);
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
