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

  public static Builder builder() {
    return new Builder();
  }

  /**
   * Hand-written builder (not Jackson-bound; {@link ModelCapabilities} is a plain SPI value
   * produced either by the resolver's YAML round-trip or directly by a {@link
   * io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApi} implementation). Defaults
   * mirror the conservative baseline: empty modality lists, all capability flags {@code false}, no
   * pinned token budgets.
   */
  public static final class Builder {
    private List<Modality> userMessageModalities = List.of();
    private List<Modality> toolResultModalities = List.of();
    private List<Modality> assistantMessageModalities = List.of();
    private boolean supportsReasoning = false;
    private boolean supportsReasoningSignatureRoundtrip = false;
    private boolean supportsPromptCaching = false;
    private boolean supportsParallelToolCalls = false;
    private @Nullable Integer contextWindow;
    private @Nullable Integer maxOutputTokens;

    private Builder() {}

    public Builder userMessageModalities(List<Modality> userMessageModalities) {
      this.userMessageModalities = userMessageModalities;
      return this;
    }

    public Builder toolResultModalities(List<Modality> toolResultModalities) {
      this.toolResultModalities = toolResultModalities;
      return this;
    }

    public Builder assistantMessageModalities(List<Modality> assistantMessageModalities) {
      this.assistantMessageModalities = assistantMessageModalities;
      return this;
    }

    public Builder supportsReasoning(boolean supportsReasoning) {
      this.supportsReasoning = supportsReasoning;
      return this;
    }

    public Builder supportsReasoningSignatureRoundtrip(
        boolean supportsReasoningSignatureRoundtrip) {
      this.supportsReasoningSignatureRoundtrip = supportsReasoningSignatureRoundtrip;
      return this;
    }

    public Builder supportsPromptCaching(boolean supportsPromptCaching) {
      this.supportsPromptCaching = supportsPromptCaching;
      return this;
    }

    public Builder supportsParallelToolCalls(boolean supportsParallelToolCalls) {
      this.supportsParallelToolCalls = supportsParallelToolCalls;
      return this;
    }

    public Builder contextWindow(@Nullable Integer contextWindow) {
      this.contextWindow = contextWindow;
      return this;
    }

    public Builder maxOutputTokens(@Nullable Integer maxOutputTokens) {
      this.maxOutputTokens = maxOutputTokens;
      return this;
    }

    public ModelCapabilities build() {
      return new ModelCapabilities(
          userMessageModalities,
          toolResultModalities,
          assistantMessageModalities,
          supportsReasoning,
          supportsReasoningSignatureRoundtrip,
          supportsPromptCaching,
          supportsParallelToolCalls,
          contextWindow,
          maxOutputTokens);
    }
  }

  /**
   * Modality vocabulary shared across user-message, tool-result and assistant-message locations.
   * Keeps the lowercase {@link JsonProperty} annotations so the resolver's {@code JsonNode} ->
   * sparse DTO round-trip (used to project the merged capability matrix layers) can deserialise the
   * lowercase modality values used throughout the bundled YAML.
   *
   * <p>A {@code Modality} is the CONTENT-TYPE CLASS a provider natively ingests at a given
   * location, derived from a content's MIME type (the MIME -&gt; {@code Modality} mapping itself
   * lands in a later chunk, alongside the multimodal content converters). It is a resolver/SPI
   * concept, distinct from the Camunda {@code Document} abstraction: a Camunda {@code Document} can
   * carry any MIME type, and its MIME type decides which {@code Modality} it maps to; a {@code
   * Modality} is not a document, it's the bucket a document's (or any content's) MIME type falls
   * into.
   *
   * <ul>
   *   <li>{@link #TEXT}: text payloads, including text-family files distinguished from binary
   *       documents only by MIME (e.g. {@code text/csv}, {@code application/json} -> {@code TEXT},
   *       not {@code DOCUMENT}).
   *   <li>{@link #IMAGE}: {@code image/*} content the model accepts inline.
   *   <li>{@link #DOCUMENT}: a non-text file the model ingests as a document — today primarily
   *       {@code application/pdf}.
   *   <li>{@link #AUDIO} / {@link #VIDEO}: native audio/video input modalities. Kept distinct from
   *       {@code DOCUMENT} because providers expose them via separate input channels (dedicated
   *       audio/video content blocks), not as document uploads.
   * </ul>
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
