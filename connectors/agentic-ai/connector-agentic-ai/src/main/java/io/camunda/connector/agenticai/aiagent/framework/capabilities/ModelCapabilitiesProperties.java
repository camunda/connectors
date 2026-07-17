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
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Sparse capability block — bound by Spring Boot from {@code application.yml} (relaxed
 * camel/kebab-case binding) via {@link AgenticAiCapabilitiesProperties}, and serialised by Jackson
 * to a {@link com.fasterxml.jackson.databind.JsonNode} tree (snake_case via {@link JsonNaming}) by
 * {@link CapabilityMatrixFactory} so {@link ModelCapabilitiesResolver} can deep-merge it against
 * the matched provider's sparse materialisation DTO (e.g. {@code AnthropicModelCapabilitiesData}).
 *
 * <p>Stays in this package (rather than moving alongside a single provider's DTO) because Spring
 * Boot's relaxed binder needs one concrete, typed shape to rebuild modality lists from indexed
 * property keys for every api family bound under {@link AgenticAiCapabilitiesProperties},
 * regardless of which provider ultimately materialises the merged tree; every bundled family
 * currently shares this flat shape.
 *
 * <p>{@code provider} is an opaque, provider-specific capability bag (e.g. Anthropic's {@code
 * reasoning} descriptor) bound as a raw {@code Map<String, Object>} so this provider-agnostic
 * package never needs to know a concrete provider's shape; Spring Boot's relaxed binder still
 * rebuilds nested maps/lists recursively underneath it, and {@link CapabilityMatrixFactory}'s
 * {@code valueToTree} serialises the map verbatim (with its literal kebab-case keys) into the
 * merged {@code JsonNode} tree, where a provider's own typed DTO (e.g. {@code
 * AnthropicProviderCapabilities}) reads it back out.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.ALWAYS) // keep null fields visible to deep-merge
record ModelCapabilitiesProperties(
    @Nullable InputModalities inputModalities,
    @Nullable OutputModalities outputModalities,
    @Nullable Integer contextWindow,
    @Nullable Integer maxOutputTokens,
    @Nullable Map<String, Object> provider) {

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  @JsonInclude(JsonInclude.Include.ALWAYS)
  record InputModalities(
      @Nullable List<Modality> userMessage, @Nullable List<Modality> toolResult) {}

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  @JsonInclude(JsonInclude.Include.ALWAYS)
  record OutputModalities(@Nullable List<Modality> assistantMessage) {}
}
