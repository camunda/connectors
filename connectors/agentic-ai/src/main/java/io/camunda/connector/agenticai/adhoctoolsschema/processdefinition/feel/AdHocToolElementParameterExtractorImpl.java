/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.feel;

import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolElementParameter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.camunda.feel.api.FeelEngineApi;
import org.camunda.feel.api.FeelEngineBuilder;
import org.camunda.feel.api.ParseResult;
import org.camunda.feel.syntaxtree.FunctionInvocation;
import org.camunda.feel.syntaxtree.ParsedExpression;
import scala.Product;
import scala.jdk.javaapi.CollectionConverters;

/**
 * Generic parameter extractor delegating actual function call handling to individual
 * FunctionCallParameterExtractors.
 */
public class AdHocToolElementParameterExtractorImpl implements AdHocToolElementParameterExtractor {

  private final FeelEngineApi feelEngineApi;

  private final Map<String, FeelFunctionCallParameterExtractor> parameterExtractors =
      new LinkedHashMap<>();

  public AdHocToolElementParameterExtractorImpl() {
    this(FeelEngineBuilder.forJava().build());
  }

  public AdHocToolElementParameterExtractorImpl(FeelEngineApi feelEngineApi) {
    this.feelEngineApi = feelEngineApi;
    registerParameterExtractor(new FromAiFeelFunctionCallParameterExtractor());
  }

  private void registerParameterExtractor(final FeelFunctionCallParameterExtractor extractor) {
    parameterExtractors.put(extractor.functionName(), extractor);
  }

  @Override
  public List<AdHocToolElementParameter> extractParameters(final String expression) {
    ParseResult parseResult = feelEngineApi.parseExpression(expression);
    if (parseResult.isFailure()) {
      throw new AdHocToolElementParameterExtractionException(
          "Failed to parse FEEL expression: " + parseResult.failure().message());
    }

    return extractParameters(parseResult.parsedExpression());
  }

  public List<AdHocToolElementParameter> extractParameters(
      final ParsedExpression parsedExpression) {
    final var extracted = extractParameters(parsedExpression.expression(), new ArrayList<>());
    return Collections.unmodifiableList(extracted);
  }

  private List<AdHocToolElementParameter> extractParameters(
      final Object object, final List<AdHocToolElementParameter> extracted) {
    if (isSupportedFunctionInvocation(object)) {
      processFunctionInvocation((FunctionInvocation) object, extracted);
      return extracted;
    }

    if (!(object instanceof final Product product)) {
      return extracted;
    }

    CollectionConverters.asJava(product.productIterator())
        .forEachRemaining(
            obj -> {
              if (isSupportedFunctionInvocation(obj)) {
                processFunctionInvocation((FunctionInvocation) obj, extracted);
              } else {
                extractParameters(obj, extracted);
              }
            });

    return extracted;
  }

  private boolean isSupportedFunctionInvocation(final Object object) {
    return object instanceof final FunctionInvocation functionInvocation
        && parameterExtractors.containsKey(functionInvocation.function());
  }

  private void processFunctionInvocation(
      final FunctionInvocation functionInvocation,
      final List<AdHocToolElementParameter> extracted) {
    extracted.add(
        parameterExtractors.get(functionInvocation.function()).mapToParameter(functionInvocation));
  }
}
