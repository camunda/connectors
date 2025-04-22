/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.adhoctoolsschema.feel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.scala.DefaultScalaModule;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import scala.collection.JavaConverters;

public class FeelInputParamExtractor {

  private final FeelEngineApi feelEngineApi;

  private final ObjectMapper objectMapper;
  private final ObjectMapper scalaObjectMapper;

  private final FeelFunctionInvocationExtractor extractor =
      FeelFunctionInvocationExtractor.forFunctionName("fromAi");

  public FeelInputParamExtractor() {
    this(new ObjectMapper());
  }

  public FeelInputParamExtractor(ObjectMapper objectMapper) {
    this(FeelEngineBuilder.create().build(), objectMapper);
  }

  public FeelInputParamExtractor(FeelEngineApi feelEngineApi, ObjectMapper objectMapper) {
    this.feelEngineApi = feelEngineApi;
    this.objectMapper = objectMapper;
    this.scalaObjectMapper = objectMapper.copy().registerModule(new DefaultScalaModule());
  }

  public List<FeelInputParam> extractInputParams(String expression) {
    ParseResult parseResult = feelEngineApi.parseExpression(expression);
    if (parseResult.isFailure()) {
      throw new RuntimeException(
          "Failed to parse FEEL expression: " + parseResult.failure().message());
    }

    Set<FunctionInvocation> functionInvocations =
        extractor.findMatchingFunctionInvocations(parseResult.parsedExpression());

    List<FeelInputParam> inputParams =
        functionInvocations.stream().map(this::mapToInputParameter).toList();

    return inputParams;
  }

  private FeelInputParam mapToInputParameter(FunctionInvocation functionInvocation) {
    return switch (functionInvocation.params()) {
      case PositionalFunctionParameters positionalFunctionParameters ->
          fromPositionalFunctionInvocationParams(
              JavaConverters.asJava(positionalFunctionParameters.params()));

      case NamedFunctionParameters namedFunctionParameters ->
          fromNamedFunctionInvocationParams(
              JavaConverters.asJava(namedFunctionParameters.params()));

      default ->
          throw new RuntimeException(
              "Unsupported function invocation: " + functionInvocation.params());
    };
  }

  private FeelInputParam fromPositionalFunctionInvocationParams(List<Exp> params) {
    return fromFunctionInvocationParams(
        params.size() > 0 ? params.get(0) : null,
        params.size() > 1 ? params.get(1) : null,
        params.size() > 2 ? params.get(2) : null,
        params.size() > 3 ? params.get(3) : null);
  }

  private FeelInputParam fromNamedFunctionInvocationParams(Map<String, Exp> params) {
    return fromFunctionInvocationParams(
        params.get("value"), params.get("description"), params.get("type"), params.get("schema"));
  }

  private FeelInputParam fromFunctionInvocationParams(
      Exp name, Exp description, Exp type, Exp schema) {

    final var parameterName = parameterName(name);
    final var descriptionStr = constantStringValue(description, "description");
    final var typeStr = constantStringValue(type, "type");
    final var schemaMap = evaluatedMapValue(schema, "schema");

    return new FeelInputParam(parameterName, descriptionStr, typeStr, schemaMap);
  }

  private String parameterName(Exp value) {
    // TODO check for reserved names (e.g. _meta)
    if (!(value instanceof Ref valueRef)) {
      throw new FeelInputParamExtractionException(
          "Expected parameter 'value' to be a reference, but got '%s'"
              .formatted(value != null ? value.getClass().getName() : null));
    }

    if (valueRef.names() == null || valueRef.names().size() < 2) {
      throw new FeelInputParamExtractionException(
          "Expected parameter 'value' to be a reference with at least two segments (e.g. toolCall.parameter), but got: %s"
              .formatted(valueRef.names()));
    }

    return valueRef.names().last();
  }

  private String constantStringValue(Exp exp, String parameterName) {
    if (exp == null) {
      return null;
    }

    if (!(exp instanceof ConstString expString)) {
      throw new FeelInputParamExtractionException(
          "Expected parameter '%s' to be a string, but got '%s'"
              .formatted(parameterName, exp.getClass().getName()));
    }

    return expString.value();
  }

  private Map<String, Object> evaluatedMapValue(Exp exp, String parameterName) {
    if (exp == null) {
      return null;
    }

    try {
      Object result = evaluate(exp);
      if (!(result instanceof scala.collection.Map<?, ?> resultMap)) {
        throw new FeelInputParamExtractionException(
            "Expected parameter %s to be a map, but got: %s"
                .formatted(parameterName, result.getClass().getName()));
      }

      final var jsonSchemaString = scalaObjectMapper.writeValueAsString(resultMap);
      return objectMapper.readValue(jsonSchemaString, new TypeReference<>() {});
    } catch (Throwable e) {
      throw new FeelInputParamExtractionException(
          "Failed to evaluate parameter %s: %s".formatted(parameterName, e.getMessage()));
    }
  }

  private Object evaluate(Exp exp) {
    EvaluationResult result =
        feelEngineApi.evaluate(new ParsedExpression(exp, ""), Collections.emptyMap());
    if (result.isFailure()) {
      throw new FeelInputParamExtractionException(
          "Failed to evaluate expression: " + result.failure().message());
    }

    return result.result();
  }

  public static class FeelInputParamExtractionException extends RuntimeException {

    public FeelInputParamExtractionException(String message) {
      super(message);
    }
  }

  public record FeelInputParam(
      String name, String description, String type, Map<String, Object> schema) {}
}
