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
package io.camunda.connector.runtime.core.feel;

import fastparse.Parsed;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.camunda.feel.impl.parser.FeelParser;
import org.camunda.feel.syntaxtree.ArithmeticNegation;
import org.camunda.feel.syntaxtree.ConstBool;
import org.camunda.feel.syntaxtree.ConstContext;
import org.camunda.feel.syntaxtree.ConstDate;
import org.camunda.feel.syntaxtree.ConstDateTime;
import org.camunda.feel.syntaxtree.ConstList;
import org.camunda.feel.syntaxtree.ConstLocalDateTime;
import org.camunda.feel.syntaxtree.ConstNull;
import org.camunda.feel.syntaxtree.ConstNumber;
import org.camunda.feel.syntaxtree.ConstString;
import org.camunda.feel.syntaxtree.Exp;
import scala.Tuple2;
import scala.collection.immutable.List;
import scala.math.BigDecimal;

public class FeelParserWrapper {

  @SuppressWarnings("unchecked")
  public static Object parseIfIsFeelExpressionOrGetOriginal(final Object value) {
    if (value instanceof Map) {
      return ((Map<Object, Object>) value)
          .entrySet().stream()
              .collect(
                  Collectors.toMap(
                      Map.Entry::getKey,
                      entry -> parseIfIsFeelExpressionOrGetOriginal(entry.getValue())));
    }
    if (value instanceof String && isFeelExpression((String) value)) {
      return parseExpression((String) value);
    } else {
      return value;
    }
  }

  private static boolean isFeelExpression(String value) {
    return value.startsWith("=");
  }

  private static Object parseExpression(String expressionStr) {
    String feelExpression = expressionStr.substring(1);
    Parsed<Exp> parsedExp = FeelParser.parseExpression(feelExpression);
    if (!parsedExp.isSuccess()) {
      throw new RuntimeException(parsedExp.toString());
    }
    Exp expression = parsedExp.get().value();
    return parseExpression(expression);
  }

  private static Object parseExpression(final Exp expression) {
    if (ConstNull.canEqual(expression)) {
      return null;
    } else if (expression instanceof ConstContext constContext) {
      Map<Object, Object> map = new HashMap<>();
      for (int i = 0; i < constContext.entries().size(); i++) {
        Tuple2<String, Exp> apply = constContext.entries().apply(i);
        String s = apply._1;
        Exp exp = apply._2;
        map.put(s, parseExpression(exp));
      }
      return map;
    } else if (expression instanceof ConstList value1) {
      List<Exp> items = value1.items();
      java.util.List<Object> result = new java.util.ArrayList<>();
      for (int i = 0; i < items.size(); i++) {
        result.add(parseExpression(items.apply(i)));
      }
      return result;
    } else if (expression instanceof ArithmeticNegation) {
      Object parsedValue = parseExpression(((ArithmeticNegation) expression).x());
      return parsedValue != null ? ((BigDecimal) parsedValue).bigDecimal().negate() : null;
    } else if (expression instanceof ConstString) {
      return ((ConstString) expression).value();
    } else if (expression instanceof ConstBool) {
      return ((ConstBool) expression).value();
    } else if (expression instanceof ConstNumber) {
      return ((ConstNumber) expression).value();
    } else if (expression instanceof ConstDate) {
      return ((ConstDate) expression).value();
    } else if (expression instanceof ConstDateTime) {
      return ((ConstDateTime) expression).value();
    } else if (expression instanceof ConstLocalDateTime) {
      return ((ConstLocalDateTime) expression).value();
    } else {
      throw new RuntimeException("Failed to parse expression " + expression.toString());
    }
  }
}
