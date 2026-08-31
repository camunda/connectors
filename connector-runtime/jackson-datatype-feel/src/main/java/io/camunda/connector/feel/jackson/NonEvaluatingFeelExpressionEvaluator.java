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
package io.camunda.connector.feel.jackson;

import io.camunda.connector.feel.FeelExpressionEvaluator;

/**
 * Answers every expression with the text of the expression itself.
 *
 * <p>Carried on the reader that binds an evaluation result. Leaving no evaluator there is not the
 * same thing: {@link FeelDeserializer} is public, so a model may name it directly with
 * {@code @JsonDeserialize}, and such a field is registered on any mapper that binds its type — the
 * result mapper included. With no evaluator on the reader it falls back to the local engine it was
 * constructed with, and process data then runs as an expression: {@code =1+1} computes, and a FEEL
 * function contributed through the SPI executes. With this one the text binds as the text.
 */
final class NonEvaluatingFeelExpressionEvaluator implements FeelExpressionEvaluator {

  static final NonEvaluatingFeelExpressionEvaluator INSTANCE =
      new NonEvaluatingFeelExpressionEvaluator();

  private NonEvaluatingFeelExpressionEvaluator() {}

  @SuppressWarnings("unchecked")
  @Override
  public <T> T evaluate(String expression, Object... variables) {
    return (T) expression;
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> T evaluate(String expression, Class<T> targetType, Object... variables) {
    return (T) expression;
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> T evaluate(
      String expression, com.fasterxml.jackson.databind.JavaType targetType, Object... variables) {
    return (T) expression;
  }

  @Override
  public String evaluateToJson(String expression, Object... variables) {
    throw new UnsupportedOperationException(
        "An evaluation result is not expression source; there is nothing to evaluate to JSON");
  }
}
