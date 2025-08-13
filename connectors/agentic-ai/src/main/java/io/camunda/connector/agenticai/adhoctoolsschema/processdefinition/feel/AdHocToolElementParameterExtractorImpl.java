/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.feel;

import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolElementParameter;
import io.camunda.zeebe.feel.tagged.impl.TaggedParameter;
import io.camunda.zeebe.feel.tagged.impl.TaggedParameterExtractor;
import java.util.List;
import org.camunda.feel.api.FeelEngineApi;
import org.camunda.feel.api.FeelEngineBuilder;
import org.camunda.feel.api.ParseResult;

public class AdHocToolElementParameterExtractorImpl implements AdHocToolElementParameterExtractor {

  private final FeelEngineApi feelEngineApi;
  private final TaggedParameterExtractor taggedParameterExtractor;

  public AdHocToolElementParameterExtractorImpl(TaggedParameterExtractor taggedParameterExtractor) {
    this(FeelEngineBuilder.forJava().build(), taggedParameterExtractor);
  }

  public AdHocToolElementParameterExtractorImpl(
      FeelEngineApi feelEngineApi, TaggedParameterExtractor taggedParameterExtractor) {
    this.feelEngineApi = feelEngineApi;
    this.taggedParameterExtractor = taggedParameterExtractor;
  }

  @Override
  public List<AdHocToolElementParameter> extractParameters(final String expression) {
    ParseResult parseResult = feelEngineApi.parseExpression(expression);
    if (parseResult.isFailure()) {
      throw new IllegalArgumentException(
          "Failed to parse FEEL expression: " + parseResult.failure().message());
    }

    return taggedParameterExtractor.extractParameters(parseResult.parsedExpression()).stream()
        .map(this::asToolElementParameter)
        .toList();
  }

  private AdHocToolElementParameter asToolElementParameter(final TaggedParameter parameter) {
    return AdHocToolElementParameter.builder()
        .name(parameter.name())
        .description(parameter.description())
        .type(parameter.type())
        .schema(parameter.schema())
        .options(parameter.options())
        .build();
  }
}
