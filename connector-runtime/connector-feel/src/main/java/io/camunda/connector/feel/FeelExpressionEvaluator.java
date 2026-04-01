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

import com.fasterxml.jackson.databind.JavaType;

/**
 * Abstraction for FEEL expression evaluation. Implementations may evaluate expressions locally
 * using a FEEL engine or remotely via a Camunda cluster.
 */
public interface FeelExpressionEvaluator {

  /**
   * Evaluates a FEEL expression with the given variables.
   *
   * @param expression the FEEL expression to evaluate (with or without leading '=')
   * @param variables the variables to use in evaluation (will be merged)
   * @param <T> the type to cast the evaluation result to
   * @return the evaluation result
   * @throws FeelEngineWrapperException when evaluation fails or result cannot be cast
   */
  <T> T evaluate(String expression, Object... variables);

  /**
   * Evaluates a FEEL expression with the given variables and converts to the specified type.
   *
   * @param expression the FEEL expression to evaluate
   * @param targetType the class the result should be converted to
   * @param variables the variables to use in evaluation
   * @param <T> the type to cast the evaluation result to
   * @return the evaluation result converted to targetType
   * @throws FeelEngineWrapperException when evaluation fails or result cannot be converted
   */
  <T> T evaluate(String expression, Class<T> targetType, Object... variables);

  /**
   * Evaluates a FEEL expression with the given variables and converts to the specified JavaType.
   *
   * @param expression the FEEL expression to evaluate
   * @param targetType the JavaType the result should be converted to
   * @param variables the variables to use in evaluation
   * @param <T> the type to cast the evaluation result to
   * @return the evaluation result converted to targetType
   * @throws FeelEngineWrapperException when evaluation fails or result cannot be converted
   */
  <T> T evaluate(String expression, JavaType targetType, Object... variables);

  /**
   * Evaluates an expression to a JSON String.
   *
   * @param expression the expression to evaluate
   * @param variables the variables to use in evaluation
   * @return the JSON String representing the evaluation result, or null if result is null
   * @throws FeelEngineWrapperException when evaluation fails or result cannot be parsed as JSON
   */
  String evaluateToJson(String expression, Object... variables);
}
