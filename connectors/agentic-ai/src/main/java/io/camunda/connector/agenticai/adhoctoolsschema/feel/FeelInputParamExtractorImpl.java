/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.adhoctoolsschema.feel;

import static io.camunda.connector.agenticai.util.JacksonExceptionMessageExtractor.humanReadableJsonProcessingExceptionMessage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.scala.DefaultScalaModule;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
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

public class FeelInputParamExtractorImpl implements FeelInputParamExtractor {

  private final FeelEngineApi feelEngineApi;

  private final ObjectMapper objectMapper;
  private final ObjectMapper scalaObjectMapper;

  private final FeelFunctionInvocationExtractor extractor =
      FeelFunctionInvocationExtractor.forFunctionName("fromAi");

  public FeelInputParamExtractorImpl() {
    this(new ObjectMapper());
  }

  public FeelInputParamExtractorImpl(ObjectMapper objectMapper) {
    this(FeelEngineBuilder.create().build(), objectMapper);
  }

  public FeelInputParamExtractorImpl(FeelEngineApi feelEngineApi, ObjectMapper objectMapper) {
    this.feelEngineApi = feelEngineApi;
    this.objectMapper = objectMapper;
    this.scalaObjectMapper = objectMapper.copy().registerModule(new DefaultScalaModule());
  }

  @Override
  public List<FeelInputParam> extractInputParams(String expression) {
    ParseResult parseResult = feelEngineApi.parseExpression(expression);
    if (parseResult.isFailure()) {
      throw new FeelInputParamExtractionException(
          "Failed to parse FEEL expression: " + parseResult.failure().message());
    }

    Set<FunctionInvocation> functionInvocations =
        extractor.findMatchingFunctionInvocations(parseResult.parsedExpression());

    return functionInvocations.stream().map(this::mapToInputParameter).toList();
  }

  private FeelInputParam mapToInputParameter(FunctionInvocation functionInvocation) {
    return switch (functionInvocation.params()) {
      case PositionalFunctionParameters positionalFunctionParameters ->
          fromPositionalFunctionInvocationParams(
              CollectionConverters.asJava(positionalFunctionParameters.params()));

      case NamedFunctionParameters namedFunctionParameters ->
          fromNamedFunctionInvocationParams(
              CollectionConverters.asJava(namedFunctionParameters.params()));

      default ->
          throw new FeelInputParamExtractionException(
              "Unsupported function invocation: " + functionInvocation.params());
    };
  }

  private FeelInputParam fromPositionalFunctionInvocationParams(List<Exp> params) {
    final Function<Integer, Exp> getParam =
        index -> (params.size() > index ? params.get(index) : null);

    return fromFunctionInvocationParams(
        getParam.apply(0),
        getParam.apply(1),
        getParam.apply(2),
        getParam.apply(3),
        getParam.apply(4));
  }

  private FeelInputParam fromNamedFunctionInvocationParams(Map<String, Exp> params) {
    return fromFunctionInvocationParams(
        params.get("value"),
        params.get("description"),
        params.get("type"),
        params.get("schema"),
        params.get("options"));
  }

  private FeelInputParam fromFunctionInvocationParams(
      Exp name, Exp description, Exp type, Exp schema, Exp options) {

    final var parameterName = parameterName(name);
    final var descriptionStr = evaluateToString(description, "description");
    final var typeStr = evaluateToString(type, "type");
    final var schemaMap = evaluateToMap(schema, "schema");
    final var optionsMap = evaluateToMap(options, "options");

    return new FeelInputParam(parameterName, descriptionStr, typeStr, schemaMap, optionsMap);
  }

  private String parameterName(Exp value) {
    if (!(value instanceof Ref valueRef)) {
      throw new FeelInputParamExtractionException(
          "Expected parameter 'value' to be a reference (e.g. 'toolCall.customParameter'), but received %s."
              .formatted(
                  switch (value) {
                    case ConstString stringResult -> "string '%s'".formatted(stringResult.value());
                    default -> value;
                  }));
    }

    if (valueRef.names() == null || valueRef.names().isEmpty()) {
      // e.g. toolCall.parameter
      throw new FeelInputParamExtractionException(
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
      throw new FeelInputParamExtractionException(
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
    if (!(result instanceof scala.collection.Map<?, ?> resultMap)) {
      throw new FeelInputParamExtractionException(
          "Expected parameter '%s' to be a map, but received '%s'."
              .formatted(parameterName, result));
    }

    try {
      final var jsonSchemaString = scalaObjectMapper.writeValueAsString(resultMap);
      return objectMapper.readValue(jsonSchemaString, new TypeReference<>() {});
    } catch (JsonProcessingException jpe) {
      throw new FeelInputParamExtractionException(
          "Failed to evaluate parameter '%s': %s"
              .formatted(parameterName, humanReadableJsonProcessingExceptionMessage(jpe)));
    } catch (Throwable e) {
      throw new FeelInputParamExtractionException(
          "Failed to evaluate parameter '%s': %s".formatted(parameterName, e.getMessage()));
    }
  }

  private Object evaluate(Exp exp, String parameterName) {
    EvaluationResult result =
        feelEngineApi.evaluate(new ParsedExpression(exp, ""), Collections.emptyMap());
    if (result.isFailure()) {
      throw new FeelInputParamExtractionException(
          "Failed to evaluate expression for parameter '%s': %s"
              .formatted(parameterName, result.failure().message()));
    }

    return result.result();
  }
}
