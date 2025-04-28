/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.adhoctoolsschema.feel;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;
import org.camunda.feel.syntaxtree.FunctionInvocation;
import org.camunda.feel.syntaxtree.ParsedExpression;
import scala.Product;
import scala.collection.JavaConverters;

class FeelFunctionInvocationExtractor {
  private final Predicate<FunctionInvocation> functionPredicate;

  public FeelFunctionInvocationExtractor(Predicate<FunctionInvocation> functionPredicate) {
    this.functionPredicate = functionPredicate;
  }

  public static FeelFunctionInvocationExtractor forFunctionName(String functionName) {
    return new FeelFunctionInvocationExtractor(
        functionInvocation -> functionInvocation.function().equals(functionName));
  }

  public Set<FunctionInvocation> findMatchingFunctionInvocations(
      ParsedExpression parsedExpression) {
    final var results =
        findMatchingFunctionInvocations(parsedExpression.expression(), new LinkedHashSet<>());
    return Collections.unmodifiableSet(results);
  }

  private Set<FunctionInvocation> findMatchingFunctionInvocations(
      Object object, Set<FunctionInvocation> functions) {
    if (object instanceof FunctionInvocation functionInvocation
        && isMatchingFunctionInvocation(functionInvocation)) {
      functions.add(functionInvocation);
      return functions;
    }

    if (!(object instanceof Product product)) {
      return functions;
    }

    JavaConverters.asJava(product.productIterator())
        .forEachRemaining(
            obj -> {
              if (obj instanceof FunctionInvocation functionInvocation
                  && isMatchingFunctionInvocation(functionInvocation)) {
                functions.add(functionInvocation);
              } else {
                findMatchingFunctionInvocations(obj, functions);
              }
            });

    return functions;
  }

  private boolean isMatchingFunctionInvocation(FunctionInvocation functionInvocation) {
    return functionPredicate.test(functionInvocation);
  }
}
