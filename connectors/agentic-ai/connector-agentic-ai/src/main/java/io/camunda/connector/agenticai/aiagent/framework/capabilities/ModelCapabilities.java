/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.capabilities;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Per-model capability descriptor materialised from the capability matrix (bundled YAML plus
 * library-consumer overrides) or a connector config override. Drives runtime decisions like
 * tool-result strategy selection, reasoning negotiation, and cache-marker placement. The vocabulary
 * for {@link Modality} is fixed; modality lists per location are symmetric so every location has an
 * explicit answer.
 */
public record ModelCapabilities(
    List<Modality> userMessageModalities,
    List<Modality> toolResultModalities,
    List<Modality> assistantMessageModalities,
    boolean supportsReasoning,
    boolean supportsReasoningSignatureRoundtrip,
    boolean supportsPromptCaching,
    boolean supportsParallelToolCalls,
    @Nullable Integer contextWindow,
    @Nullable Integer maxOutputTokens) {

  /**
   * Modality vocabulary shared across user-message, tool-result and assistant-message locations.
   * Keeps the lowercase {@link JsonProperty} annotations so the resolver's {@code JsonNode} ->
   * sparse DTO round-trip (used to project the merged capability matrix layers) can deserialise the
   * lowercase modality values used throughout the bundled YAML.
   */
  public enum Modality {
    @JsonProperty("text")
    TEXT,
    @JsonProperty("image")
    IMAGE,
    @JsonProperty("document")
    DOCUMENT,
    @JsonProperty("audio")
    AUDIO,
    @JsonProperty("video")
    VIDEO
  }
}
