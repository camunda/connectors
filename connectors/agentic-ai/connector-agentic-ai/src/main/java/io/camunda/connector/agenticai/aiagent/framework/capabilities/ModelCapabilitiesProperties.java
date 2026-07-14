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
 * camel/kebab-case binding) via {@link AgenticAiFrameworkProperties}, and serialised by Jackson to
 * a {@link com.fasterxml.jackson.databind.JsonNode} tree (snake_case via {@link JsonNaming}) by
 * {@link CapabilityMatrixFactory} so {@link ModelCapabilitiesResolver} can deep-merge it against
 * the matched provider's sparse materialisation DTO (e.g. {@code AnthropicModelCapabilitiesData}).
 *
 * <p>Stays in this package (rather than moving alongside a single provider's DTO) because Spring
 * Boot's relaxed binder needs one concrete, typed shape to rebuild modality lists from indexed
 * property keys for every api family bound under {@link AgenticAiFrameworkProperties}, regardless
 * of which provider ultimately materialises the merged tree; every bundled family currently shares
 * this flat shape.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.ALWAYS) // keep null fields visible to deep-merge
record ModelCapabilitiesProperties(
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
}
