/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.springframework.lang.Nullable;

/**
 * Per-model capability descriptor materialised from the capability matrix YAML (or a connector
 * config override). Drives runtime decisions like tool-result strategy selection, reasoning
 * negotiation, and cache-marker placement. The vocabulary for {@link Modality} is fixed; modality
 * lists per location are symmetric so every location has an explicit answer.
 *
 * <p>Part of the ADR-005 Phase 1 SPI scaffolding. Wired by ChatClientImpl, dispatched via
 * ChatModelApiRegistry.
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

  public enum Modality {
    @JsonProperty("text")
    TEXT,
    @JsonProperty("image")
    IMAGE,
    @JsonProperty("pdf")
    PDF,
    @JsonProperty("audio")
    AUDIO,
    @JsonProperty("video")
    VIDEO
  }
}
