/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.feel;

import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolElementParameter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.camunda.feel.api.EvaluationResult;
import org.camunda.feel.api.FeelEngineApi;
import org.camunda.feel.api.FeelEngineBuilder;
import org.camunda.feel.api.ParseResult;
import org.camunda.feel.syntaxtree.ConstString;
import org.camunda.feel.syntaxtree.Exp;
import org.camunda.feel.syntaxtree.FunctionInvocation;
import org.camunda.feel.syntaxtree.NamedFunctionParameters;
import org.camunda.feel.syntaxtree.ParsedExpression;
import org.camunda.feel.syntaxtree.PositionalFunctionParameters;
import org.camunda.feel.syntaxtree.Ref;
import scala.jdk.javaapi.CollectionConverters;

public class FeelExpressionParameterExtractorImpl implements FeelExpressionParameterExtractor {

  private final FeelEngineApi feelEngineApi;

  private final FeelFunctionInvocationExtractor extractor =
      FeelFunctionInvocationExtractor.forFunctionName("fromAi");

  public FeelExpressionParameterExtractorImpl() {
    this(FeelEngineBuilder.forJava().build());
  }

  public FeelExpressionParameterExtractorImpl(FeelEngineApi feelEngineApi) {
    this.feelEngineApi = feelEngineApi;
  }

  @Override
  public List<AdHocToolElementParameter> extractParameters(String expression) {
    ParseResult parseResult = feelEngineApi.parseExpression(expression);
    if (parseResult.isFailure()) {
      throw new FeelExpressionParameterExtractionException(
          "Failed to parse FEEL expression: " + parseResult.failure().message());
    }

    Set<FunctionInvocation> functionInvocations =
        extractor.findMatchingFunctionInvocations(parseResult.parsedExpression());

    return functionInvocations.stream().map(this::mapToParameter).toList();
  }

  private AdHocToolElementParameter mapToParameter(FunctionInvocation functionInvocation) {
    return switch (functionInvocation.params()) {
      case PositionalFunctionParameters positionalFunctionParameters ->
          fromPositionalFunctionInvocationParams(
              CollectionConverters.asJava(positionalFunctionParameters.params()));

      case NamedFunctionParameters namedFunctionParameters ->
          fromNamedFunctionInvocationParams(
              CollectionConverters.asJava(namedFunctionParameters.params()));

      default ->
          throw new FeelExpressionParameterExtractionException(
              "Unsupported function invocation: " + functionInvocation.params());
    };
  }

  private AdHocToolElementParameter fromPositionalFunctionInvocationParams(List<Exp> params) {
    final Function<Integer, Exp> getParam =
        index -> (params.size() > index ? params.get(index) : null);

    return fromFunctionInvocationParams(
        getParam.apply(0),
        getParam.apply(1),
        getParam.apply(2),
        getParam.apply(3),
        getParam.apply(4));
  }

  private AdHocToolElementParameter fromNamedFunctionInvocationParams(Map<String, Exp> params) {
    return fromFunctionInvocationParams(
        params.get("value"),
        params.get("description"),
        params.get("type"),
        params.get("schema"),
        params.get("options"));
  }

  private AdHocToolElementParameter fromFunctionInvocationParams(
      Exp name, Exp description, Exp type, Exp schema, Exp options) {

    final var parameterName = parameterName(name);
    final var descriptionStr = evaluateToString(description, "description");
    final var typeStr = evaluateToString(type, "type");
    final var schemaMap = evaluateToMap(schema, "schema");
    final var optionsMap = evaluateToMap(options, "options");

    return new AdHocToolElementParameter(
        parameterName, descriptionStr, typeStr, schemaMap, optionsMap);
  }

  private String parameterName(Exp value) {
    if (!(value instanceof Ref valueRef)) {
      throw new FeelExpressionParameterExtractionException(
          "Expected parameter 'value' to be a reference (e.g. 'toolCall.customParameter'), but received %s."
              .formatted(
                  switch (value) {
                    case ConstString stringResult -> "string '%s'".formatted(stringResult.value());
                    default -> value;
                  }));
    }

    if (valueRef.names() == null || valueRef.names().isEmpty()) {
      // e.g. toolCall.parameter
      throw new FeelExpressionParameterExtractionException(
          "Expected parameter 'value' to be a reference with at least one segment, but received '%s'."
              .formatted(valueRef));
    }

    return valueRef.names().last();
  }

  private String evaluateToString(Exp exp, String parameterName) {
    if (exp == null) {
      return null;
    }

    Object result = evaluate(exp, parameterName);
    if (!(result instanceof String resultString)) {
      throw new FeelExpressionParameterExtractionException(
          "Expected parameter '%s' to be a string, but received '%s'."
              .formatted(parameterName, result));
    }

    return resultString;
  }

  private Map<String, Object> evaluateToMap(Exp exp, String parameterName) {
    if (exp == null) {
      return null;
    }

    Object result = evaluate(exp, parameterName);
    if (!(result instanceof Map<?, ?> resultMap)) {
      throw new FeelExpressionParameterExtractionException(
          "Expected parameter '%s' to be a map, but received '%s'."
              .formatted(parameterName, result));
    }

    return resultMap.entrySet().stream()
        .collect(
            Collectors.toMap(
                entry -> entry.getKey().toString(),
                entry -> (Object) entry.getValue(),
                (v1, v2) -> v2,
                LinkedHashMap::new));
  }

  private Object evaluate(Exp exp, String parameterName) {
    EvaluationResult result =
        feelEngineApi.evaluate(new ParsedExpression(exp, ""), Collections.emptyMap());
    if (result.isFailure()) {
      throw new FeelExpressionParameterExtractionException(
          "Failed to evaluate expression for parameter '%s': %s"
              .formatted(parameterName, result.failure().message()));
    }

    return result.result();
  }
}
