/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.anthropic;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.CoreModelCapabilities;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilities.Modality;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilitiesData;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Sparse Anthropic capability block — the shape of one merged {@code anthropic-messages} capability
 * matrix row. Each field is nullable so the resolver's deep-merge can fall through to a lower
 * layer; the fully-merged tree is projected onto {@link AnthropicModelCapabilities} via {@link
 * #toModelCapabilities()}. (Ported verbatim from the former provider-neutral {@code
 * ModelCapabilitiesData} record, now Anthropic-owned so R1 can add a typed {@code reasoning}
 * descriptor with Anthropic-specific keys.)
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AnthropicModelCapabilitiesData(
    @Nullable InputModalities inputModalities,
    @Nullable OutputModalities outputModalities,
    @Nullable Boolean supportsReasoning,
    @Nullable Boolean supportsReasoningSignatureRoundtrip,
    @Nullable Boolean supportsPromptCaching,
    @Nullable Boolean supportsParallelToolCalls,
    @Nullable Integer contextWindow,
    @Nullable Integer maxOutputTokens)
    implements ModelCapabilitiesData<AnthropicModelCapabilities> {

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record InputModalities(
      @Nullable List<Modality> userMessage, @Nullable List<Modality> toolResult) {}

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record OutputModalities(@Nullable List<Modality> assistantMessage) {}

  @Override
  public AnthropicModelCapabilities toModelCapabilities() {
    return new AnthropicModelCapabilities(
        new CoreModelCapabilities(
            userMessageModalities(),
            toolResultModalities(),
            assistantMessageModalities(),
            contextWindow,
            maxOutputTokens),
        Boolean.TRUE.equals(supportsReasoning),
        Boolean.TRUE.equals(supportsReasoningSignatureRoundtrip),
        Boolean.TRUE.equals(supportsPromptCaching),
        Boolean.TRUE.equals(supportsParallelToolCalls));
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
