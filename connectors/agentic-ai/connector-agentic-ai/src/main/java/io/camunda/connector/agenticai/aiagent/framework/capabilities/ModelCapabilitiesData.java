/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.capabilities;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilities.Modality;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Sparse capability block — bound by Spring Boot from {@code application.yml} (relaxed
 * camel/kebab-case binding) and serialised by Jackson to/from a {@link
 * com.fasterxml.jackson.databind.JsonNode} tree (snake_case via {@link JsonNaming}, nulls preserved
 * so the resolver's deep-merge can fall through to the base layer).
 *
 * <p>Each field is nullable: a missing field means "inherit from the lower layer" (api-family
 * {@code defaults} -&gt; conservative defaults). The fully-merged result is projected onto the flat
 * {@link ModelCapabilities} SPI shape via {@link #toModelCapabilities()}.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.ALWAYS) // keep null fields visible to deep-merge
record ModelCapabilitiesData(
    @Nullable InputModalities inputModalities,
    @Nullable OutputModalities outputModalities,
    @Nullable Boolean supportsReasoning,
    @Nullable Boolean supportsReasoningSignatureRoundtrip,
    @Nullable Boolean supportsPromptCaching,
    @Nullable Boolean supportsParallelToolCalls,
    @Nullable Integer contextWindow,
    @Nullable Integer maxOutputTokens) {

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  @JsonInclude(JsonInclude.Include.ALWAYS)
  record InputModalities(
      @Nullable List<Modality> userMessage, @Nullable List<Modality> toolResult) {}

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  @JsonInclude(JsonInclude.Include.ALWAYS)
  record OutputModalities(@Nullable List<Modality> assistantMessage) {}

  ModelCapabilities toModelCapabilities() {
    return new ModelCapabilities(
        userMessageModalities(),
        toolResultModalities(),
        assistantMessageModalities(),
        Boolean.TRUE.equals(supportsReasoning),
        Boolean.TRUE.equals(supportsReasoningSignatureRoundtrip),
        Boolean.TRUE.equals(supportsPromptCaching),
        Boolean.TRUE.equals(supportsParallelToolCalls),
        contextWindow,
        maxOutputTokens);
  }

  private List<Modality> userMessageModalities() {
    return inputModalities != null && inputModalities.userMessage() != null
        ? inputModalities.userMessage()
        : List.of(Modality.TEXT);
  }

  private List<Modality> toolResultModalities() {
    return inputModalities != null && inputModalities.toolResult() != null
        ? inputModalities.toolResult()
        : List.of(Modality.TEXT);
  }

  private List<Modality> assistantMessageModalities() {
    return outputModalities != null && outputModalities.assistantMessage() != null
        ? outputModalities.assistantMessage()
        : List.of(Modality.TEXT);
  }
}
