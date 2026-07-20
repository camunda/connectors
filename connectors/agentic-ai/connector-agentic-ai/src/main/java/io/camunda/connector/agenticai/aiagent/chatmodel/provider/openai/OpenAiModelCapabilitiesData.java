/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.camunda.connector.agenticai.aiagent.capabilities.CoreModelCapabilities;
import io.camunda.connector.agenticai.aiagent.capabilities.ModelCapabilities.Modality;
import io.camunda.connector.agenticai.aiagent.capabilities.ModelCapabilitiesData;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Sparse OpenAI capability block — the shape of one merged {@code openai} capability matrix row.
 * Each field is nullable so the resolver's deep-merge can fall through to a lower layer; the
 * fully-merged tree is projected onto {@link OpenAiModelCapabilities} via {@link
 * #toModelCapabilities()}. {@code provider} is the typed interpretation of the opaque {@code
 * provider} capability bag (an untyped {@code Map<String, Object>} in the Spring-bound {@code
 * ModelCapabilitiesProperties} shape); today it only carries the {@code reasoning} descriptor.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.ALWAYS)
public record OpenAiModelCapabilitiesData(
    @Nullable InputModalities inputModalities,
    @Nullable OutputModalities outputModalities,
    @Nullable Integer contextWindow,
    @Nullable Integer maxOutputTokens,
    @Nullable OpenAiProviderCapabilities provider)
    implements ModelCapabilitiesData<OpenAiModelCapabilities> {

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record InputModalities(
      @Nullable List<Modality> userMessage, @Nullable List<Modality> toolResult) {}

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record OutputModalities(@Nullable List<Modality> assistantMessage) {}

  @Override
  public OpenAiModelCapabilities toModelCapabilities() {
    return new OpenAiModelCapabilities(
        new CoreModelCapabilities(
            userMessageModalities(),
            toolResultModalities(),
            assistantMessageModalities(),
            contextWindow,
            maxOutputTokens),
        provider == null ? null : provider.reasoning());
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
