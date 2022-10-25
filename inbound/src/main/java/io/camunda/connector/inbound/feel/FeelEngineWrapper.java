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
package io.camunda.connector.inbound.feel;

import org.camunda.feel.FeelEngine;
import org.camunda.feel.impl.JavaValueMapper;
import org.camunda.feel.impl.SpiServiceLoader;
import org.springframework.stereotype.Service;
import scala.jdk.javaapi.CollectionConverters;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Wait for https://github.com/camunda/connector-sdk/issues/178 to be solved - then delete this here.
 */
public class FeelEngineWrapper {

  private final FeelEngine feelEngine;

  public FeelEngineWrapper() {
    feelEngine =
        new FeelEngine.Builder()
            .customValueMapper(new JavaValueMapper())
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
      final Map<String, Object> map) {
    final HashMap<String, Object> context = new HashMap<>(map);
    return scala.collection.immutable.Map.from(CollectionConverters.asScala(context));
  }

  private static Object toJava(final Object scalaObject) {

    if (scalaObject instanceof scala.collection.immutable.Map) {
      return CollectionConverters.asJava((scala.collection.immutable.Map) scalaObject);
    }

    return scalaObject;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> ensureVariablesMap(final Object variables) {
    return (Map<String, Object>) Objects.requireNonNull(variables, "variables cannot be null");
  }

  public <T> T evaluate(final String expression, final Object variables) {
    scala.collection.immutable.Map<String, Object> context =
        Optional.ofNullable(variables)
            .map(FeelEngineWrapper::ensureVariablesMap)
            .map(FeelEngineWrapper::toScalaMap)
            .get();

    var result = feelEngine.evalExpression(trimExpression(expression), context);

    if (result.isLeft()) {
      throw new FeelEngineWrapperException(
          "expression evaluation failed with message: " + result.left().get().message(),
          expression,
          variables);
    }

    var val = result.right().get();

    if (val == null) {
      return null;
    }

    return (T) toJava(val);
  }
}
